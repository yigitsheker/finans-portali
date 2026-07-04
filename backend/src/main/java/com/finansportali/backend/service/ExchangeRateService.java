package com.finansportali.backend.service;

import com.finansportali.backend.entity.ExchangeRate;
import com.finansportali.backend.repository.ExchangeRateRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides foreign-exchange rates sourced from TCMB. Periodically scrapes the
 * TCMB daily XML feed, persists per-currency rows, and exposes cached read
 * accessors (latest rates, by-source, history) for the rest of the application.
 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final String SRC_TCMB = "TCMB";
    // Tek bir <Currency ...> ... </Currency> bloğunu yakalar: group(1)=öznitelikler,
    // group(2)=içerik. Öznitelik dizisi possessive ([^>]*+) ile eşleşir; bu, eski
    // desendeki iç içe [^>]* ... [^>]* geri-izleme (ReDoS) riskini ortadan kaldırır
    // (Sonar S5852). CurrencyCode ayrıca CURRENCY_CODE_ATTR ile çıkarılır.
    private static final Pattern CURRENCY_PATTERN = Pattern.compile(
            "<Currency\\b([^>]*+)>(.*?)</Currency>", Pattern.DOTALL);
    private static final Pattern CURRENCY_CODE_ATTR = Pattern.compile(
            "CurrencyCode=\"([^\"]++)\"");

    private final ExchangeRateRepository repository;
    private final WebClient webClient;

    // Business metrics — mirrors BondDataRefreshService convention.
    private final Counter refreshSuccessCounter;
    private final Counter refreshFailureCounter;
    private final Counter currenciesFetchedCounter;
    private final Timer refreshDurationTimer;

    /**
     * Builds a timeout-bounded WebClient for the TCMB feed and registers the
     * FX refresh business metrics on the supplied registry.
     */
    public ExchangeRateService(ExchangeRateRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(10))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10))
                        .addHandlerLast(new WriteTimeoutHandler(10)));
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; FinansPortali/1.0)")
                .build();

        this.refreshSuccessCounter = Counter.builder("fx_refresh_success_total")
                .description("Total successful TCMB FX rate refreshes")
                .register(meterRegistry);
        this.refreshFailureCounter = Counter.builder("fx_refresh_failure_total")
                .description("Total failed TCMB FX rate refreshes")
                .register(meterRegistry);
        this.currenciesFetchedCounter = Counter.builder("fx_currencies_fetched_total")
                .description("Total currency rows persisted across all refreshes")
                .register(meterRegistry);
        this.refreshDurationTimer = Timer.builder("fx_refresh_duration_seconds")
                .description("Duration of a TCMB FX refresh cycle")
                .register(meterRegistry);
    }

    /** Returns the most recent rate row per source. */
    @Cacheable("exchange-rates")
    public List<ExchangeRate> getLatestRates() {
        return repository.findLatestRatesBySource();
    }

    /** Returns today's rates for the given source. */
    @Cacheable("exchange-rates-by-source")
    public List<ExchangeRate> getRatesBySource(String source) {
        LocalDate today = LocalDate.now();
        return repository.findBySourceAndRateDate(source, today);
    }

    /** Lists the distinct rate sources stored in the database. */
    public List<String> getSources() {
        return repository.findDistinctSources();
    }

    /** Returns the full rate history for a currency, newest first. */
    public List<ExchangeRate> getCurrencyHistory(String currencyCode) {
        return repository.findByCurrencyCodeOrderByRateDateDesc(currencyCode);
    }

    /** Scheduled TCMB refresh — runs every 4 hours, timed via the duration timer. */
    @Scheduled(initialDelay = 10_000, fixedDelay = 4 * 60 * 60 * 1000L) // Every 4 hours
    public void fetchTcmbRates() {
        refreshDurationTimer.record(this::doFetchTcmbRates);
    }

    private void doFetchTcmbRates() {
        log.info("Fetching TCMB exchange rates...");
        try {
            LocalDate today = LocalDate.now();
            String xml = webClient.get()
                    .uri("https://www.tcmb.gov.tr/kurlar/today.xml")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (xml != null && !xml.isBlank()) {
                parseTcmbXml(xml, today);
                refreshSuccessCounter.increment();
            } else {
                refreshFailureCounter.increment();
            }
        } catch (Exception e) {
            log.error("Failed to fetch TCMB rates: {}", e.getMessage());
            refreshFailureCounter.increment();
        }
    }

    private void parseTcmbXml(String xml, LocalDate date) {
        // Hoist the existing-rate lookup out of the loop — same date, same source
        // for every currency, so the query was being run N times per fetch.
        List<ExchangeRate> existing = repository.findBySourceAndRateDate(SRC_TCMB, date);
        Matcher matcher = CURRENCY_PATTERN.matcher(xml);

        int saved = 0;
        while (matcher.find()) {
            Matcher codeMatcher = CURRENCY_CODE_ATTR.matcher(matcher.group(1));
            if (!codeMatcher.find()) continue;
            if (saveCurrencyFromXml(codeMatcher.group(1), matcher.group(2), date, existing)) {
                saved++;
                currenciesFetchedCounter.increment();
            }
        }
        log.info("Saved {} TCMB exchange rates for {}", saved, date);
    }

    /**
     * Parse one <Currency> block and persist it if not already cached.
     * Returns true if a new rate was written.
     */
    private boolean saveCurrencyFromXml(String currencyCode, String content,
                                        LocalDate date, List<ExchangeRate> existing) {
        try {
            String buyingStr = extractValue(content, "BanknoteBuying");
            String sellingStr = extractValue(content, "BanknoteSelling");
            if (buyingStr == null || sellingStr == null) return false;
            if (existing.stream().anyMatch(r -> r.getCurrencyCode().equals(currencyCode))) {
                return false;
            }

            BigDecimal buying = new BigDecimal(buyingStr);
            BigDecimal selling = new BigDecimal(sellingStr);
            String effBuyStr = extractValue(content, "ForexBuying");
            String effSellStr = extractValue(content, "ForexSelling");
            BigDecimal effectiveBuying  = effBuyStr  != null ? new BigDecimal(effBuyStr)  : buying;
            BigDecimal effectiveSelling = effSellStr != null ? new BigDecimal(effSellStr) : selling;
            String currencyName = extractValue(content, "CurrencyName");

            repository.save(new ExchangeRate(
                    currencyCode,
                    currencyName != null ? currencyName : currencyCode,
                    buying, selling, effectiveBuying, effectiveSelling,
                    date, SRC_TCMB));
            return true;
        } catch (RuntimeException e) {
            // BigDecimal(String) throws NumberFormatException for malformed
            // TCMB rates and JPA save can blow up on constraint hits — both
            // are RuntimeExceptions. Skip the bad currency and continue.
            log.warn("Failed to parse currency data for {}: {}", currencyCode, e.getMessage());
            return false;
        }
    }

    private String extractValue(String xml, String tagName) {
        Pattern pattern = Pattern.compile("<" + tagName + ">(.*?)</" + tagName + ">");
        Matcher matcher = pattern.matcher(xml);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /** Seeds a few sample USD/EUR/GBP rows when the table is empty (dev bootstrap). */
    public void seedIfEmpty() {
        if (repository.count() > 0) return;

        LocalDate today = LocalDate.now();
        
        // Seed some sample data
        repository.save(new ExchangeRate("USD", "US Dollar", 
                new BigDecimal("34.25"), new BigDecimal("34.35"), 
                new BigDecimal("34.20"), new BigDecimal("34.40"), 
                today, SRC_TCMB));
        
        repository.save(new ExchangeRate("EUR", "Euro", 
                new BigDecimal("37.15"), new BigDecimal("37.25"), 
                new BigDecimal("37.10"), new BigDecimal("37.30"), 
                today, SRC_TCMB));
        
        repository.save(new ExchangeRate("GBP", "British Pound", 
                new BigDecimal("43.50"), new BigDecimal("43.65"), 
                new BigDecimal("43.45"), new BigDecimal("43.70"), 
                today, SRC_TCMB));

        log.info("Seeded sample exchange rate data");
    }
}
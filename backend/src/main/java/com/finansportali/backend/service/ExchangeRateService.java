package com.finansportali.backend.service;

import com.finansportali.backend.entity.ExchangeRate;
import com.finansportali.backend.repository.ExchangeRateRepository;
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

@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final String SRC_TCMB = "TCMB";
    private static final Pattern CURRENCY_PATTERN = Pattern.compile(
            "<Currency[^>]*CrossOrder=\"(\\d+)\"[^>]*CurrencyCode=\"([^\"]+)\"[^>]*>(.*?)</Currency>",
            Pattern.DOTALL);

    private final ExchangeRateRepository repository;
    private final WebClient webClient;

    public ExchangeRateService(ExchangeRateRepository repository) {
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
    }

    @Cacheable("exchange-rates")
    public List<ExchangeRate> getLatestRates() {
        return repository.findLatestRatesBySource();
    }

    @Cacheable("exchange-rates-by-source")
    public List<ExchangeRate> getRatesBySource(String source) {
        LocalDate today = LocalDate.now();
        return repository.findBySourceAndRateDate(source, today);
    }

    public List<String> getSources() {
        return repository.findDistinctSources();
    }

    public List<ExchangeRate> getCurrencyHistory(String currencyCode) {
        return repository.findByCurrencyCodeOrderByRateDateDesc(currencyCode);
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 4 * 60 * 60 * 1000L) // Every 4 hours
    public void fetchTcmbRates() {
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
            }
        } catch (Exception e) {
            log.error("Failed to fetch TCMB rates: {}", e.getMessage());
        }
    }

    private void parseTcmbXml(String xml, LocalDate date) {
        // Hoist the existing-rate lookup out of the loop — same date, same source
        // for every currency, so the query was being run N times per fetch.
        List<ExchangeRate> existing = repository.findBySourceAndRateDate(SRC_TCMB, date);
        Matcher matcher = CURRENCY_PATTERN.matcher(xml);

        int saved = 0;
        while (matcher.find()) {
            if (saveCurrencyFromXml(matcher.group(2), matcher.group(3), date, existing)) {
                saved++;
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
        } catch (Exception e) {
            log.warn("Failed to parse currency data for {}: {}", currencyCode, e.getMessage());
            return false;
        }
    }

    private String extractValue(String xml, String tagName) {
        Pattern pattern = Pattern.compile("<" + tagName + ">(.*?)</" + tagName + ">");
        Matcher matcher = pattern.matcher(xml);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

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
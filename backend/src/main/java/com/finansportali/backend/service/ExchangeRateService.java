package com.finansportali.backend.service;

import com.finansportali.backend.entity.ExchangeRate;
import com.finansportali.backend.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ExchangeRateRepository repository;
    private final WebClient webClient;

    public ExchangeRateService(ExchangeRateRepository repository) {
        this.repository = repository;
        this.webClient = WebClient.builder()
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
            String dateStr = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            
            String url = "https://www.tcmb.gov.tr/kurlar/today.xml";
            String xml = webClient.get()
                    .uri(url)
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
        Pattern currencyPattern = Pattern.compile("<Currency[^>]*CrossOrder=\"(\\d+)\"[^>]*CurrencyCode=\"([^\"]+)\"[^>]*>(.*?)</Currency>", Pattern.DOTALL);
        Matcher matcher = currencyPattern.matcher(xml);

        int saved = 0;
        while (matcher.find()) {
            try {
                String currencyCode = matcher.group(2);
                String content = matcher.group(3);

                String currencyName = extractValue(content, "CurrencyName");
                String buyingStr = extractValue(content, "BanknoteBuying");
                String sellingStr = extractValue(content, "BanknoteSelling");
                String effectiveBuyingStr = extractValue(content, "ForexBuying");
                String effectiveSellingStr = extractValue(content, "ForexSelling");

                if (buyingStr != null && sellingStr != null) {
                    BigDecimal buying = new BigDecimal(buyingStr);
                    BigDecimal selling = new BigDecimal(sellingStr);
                    BigDecimal effectiveBuying = effectiveBuyingStr != null ? new BigDecimal(effectiveBuyingStr) : buying;
                    BigDecimal effectiveSelling = effectiveSellingStr != null ? new BigDecimal(effectiveSellingStr) : selling;

                    // Check if rate already exists for today
                    List<ExchangeRate> existing = repository.findBySourceAndRateDate("TCMB", date);
                    boolean exists = existing.stream().anyMatch(r -> r.getCurrencyCode().equals(currencyCode));

                    if (!exists) {
                        ExchangeRate rate = new ExchangeRate(
                                currencyCode,
                                currencyName != null ? currencyName : currencyCode,
                                buying,
                                selling,
                                effectiveBuying,
                                effectiveSelling,
                                date,
                                "TCMB"
                        );
                        repository.save(rate);
                        saved++;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse currency data: {}", e.getMessage());
            }
        }
        log.info("Saved {} TCMB exchange rates for {}", saved, date);
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
                today, "TCMB"));
        
        repository.save(new ExchangeRate("EUR", "Euro", 
                new BigDecimal("37.15"), new BigDecimal("37.25"), 
                new BigDecimal("37.10"), new BigDecimal("37.30"), 
                today, "TCMB"));
        
        repository.save(new ExchangeRate("GBP", "British Pound", 
                new BigDecimal("43.50"), new BigDecimal("43.65"), 
                new BigDecimal("43.45"), new BigDecimal("43.70"), 
                today, "TCMB"));

        log.info("Seeded sample exchange rate data");
    }
}
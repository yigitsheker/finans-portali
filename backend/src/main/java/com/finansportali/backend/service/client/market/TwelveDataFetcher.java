package com.finansportali.backend.service.client.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Twelve Data API istemcisi (https://twelvedata.com).
 *
 * Ücretsiz tier: 800 istek/gün, 8 istek/dakika.
 * Günlük 1 kez güncelleme ile 22 sembol × 2 istek = 44 istek — yeterli.
 *
 * Sembol formatları:
 *   FX        : "USD/TRY", "EUR/TRY"
 *   Crypto    : "BTC/USD", "ETH/USD", "SOL/USD"
 *   US Stocks : "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA", "META"
 *   BIST      : "THYAO:BIST", "GARAN:BIST" (EOD/gecikmeli)
 *   Commodity : "XAU/USD"
 *   Index     : "XU100:BIST"
 */
/**
 * @deprecated Yahoo Finance'e geçildi. Bu sınıf artık kullanılmıyor.
 * Silinmeden önce legacy referanslar için tutulmaktadır.
 */
@Deprecated
@Component
public class TwelveDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataFetcher.class);
    private static final String BASE = "https://api.twelvedata.com";

    private final WebClient client;
    private final String apiKey;

    public TwelveDataFetcher(@Value("${twelvedata.api-key:disabled}") String apiKey) {
        this.apiKey = apiKey;
        this.client = WebClient.builder().baseUrl(BASE).build();
    }

    /**
     * Anlık fiyat, değişim ve yüzde değişim çeker.
     * Başarısız olursa Optional.empty() döner — sistem çökmez.
     */
    @SuppressWarnings("unchecked")
    public Optional<TwelveQuote> fetchQuote(String providerSymbol) {
        try {
            Map<String, Object> resp = client.get()
                    .uri(u -> u.path("/quote")
                            .queryParam("symbol", providerSymbol)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) {
                log.warn("[TwelveData] Null response for {}", providerSymbol);
                return Optional.empty();
            }

            // Hata kodu varsa (örn. {"code":400,"message":"..."})
            if (resp.containsKey("code")) {
                log.warn("[TwelveData] API error for {}: {}", providerSymbol, resp.get("message"));
                return Optional.empty();
            }

            double close         = toDouble(resp.get("close"));
            double change        = toDouble(resp.get("change"));
            double pct           = toDouble(resp.get("percent_change"));
            double previousClose = toDouble(resp.get("previous_close"));

            if (close == 0.0) {
                log.debug("[TwelveData] Zero price for {}, skipping", providerSymbol);
                return Optional.empty();
            }

            return Optional.of(new TwelveQuote(
                    BigDecimal.valueOf(close),
                    previousClose > 0 ? BigDecimal.valueOf(previousClose) : null,
                    BigDecimal.valueOf(change),
                    BigDecimal.valueOf(pct)
            ));
        } catch (Exception e) {
            log.warn("[TwelveData] fetchQuote failed for {}: {}", providerSymbol, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Günlük zaman serisi çeker (tarihsel grafik için).
     * outputSize: kaç gün geriye gidileceği (max 5000 ücretsiz planda).
     */
    @SuppressWarnings("unchecked")
    public List<DayClose> fetchDailyTimeSeries(String providerSymbol, int outputSize) {
        try {
            Map<String, Object> resp = client.get()
                    .uri(u -> u.path("/time_series")
                            .queryParam("symbol", providerSymbol)
                            .queryParam("interval", "1day")
                            .queryParam("outputsize", outputSize)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null || resp.containsKey("code")) {
                log.warn("[TwelveData] time_series error for {}: {}", providerSymbol,
                        resp != null ? resp.get("message") : "null");
                return List.of();
            }

            List<Map<String, Object>> values = (List<Map<String, Object>>) resp.get("values");
            if (values == null || values.isEmpty()) return List.of();

            List<DayClose> result = new ArrayList<>(values.size());
            for (Map<String, Object> v : values) {
                String dateStr = (String) v.get("datetime");
                double close   = toDouble(v.get("close"));
                if (dateStr != null && close > 0) {
                    result.add(new DayClose(
                            LocalDate.parse(dateStr.substring(0, 10)),
                            BigDecimal.valueOf(close)
                    ));
                }
            }

            // API en yeni → en eski sırasıyla döner, biz artan sıra istiyoruz
            Collections.reverse(result);
            return result;
        } catch (Exception e) {
            log.warn("[TwelveData] fetchDailyTimeSeries failed for {}: {}", providerSymbol, e.getMessage());
            return List.of();
        }
    }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0.0; }
    }

    public record TwelveQuote(
            BigDecimal last,
            BigDecimal previousClose,  // null olabilir
            BigDecimal changeAbs,
            BigDecimal changePct
    ) {}

    public record DayClose(LocalDate day, BigDecimal close) {}
}

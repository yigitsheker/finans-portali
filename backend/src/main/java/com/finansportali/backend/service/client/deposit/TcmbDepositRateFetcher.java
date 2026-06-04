package com.finansportali.backend.service.client.deposit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finansportali.backend.entity.DepositRatePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TCMB EVDS3 bank deposit rate fetcher.
 *
 * <p>Series: {@code TP.{currency}.MT{01..06}} — monthly weighted-average deposit rates
 * across all banks. Six maturity buckets per currency, three currencies (TRY/USD/EUR).
 *
 * <p>Maturity mapping (verified empirically against live EVDS3):
 * <ul>
 *   <li>{@code MT01} — up to 1 month</li>
 *   <li>{@code MT02} — 1-3 months</li>
 *   <li>{@code MT03} — 3-6 months</li>
 *   <li>{@code MT04} — 6-12 months</li>
 *   <li>{@code MT05} — over 12 months</li>
 *   <li>{@code MT06} — weighted average across all maturities</li>
 * </ul>
 *
 * <p>Same auth scheme as {@code TcmbInflationFetcher}: anonymous session cookies set
 * via env vars; quota grows once a logged-in cookie pair is provided.
 */
@Service
public class TcmbDepositRateFetcher {

    private static final Logger log = LoggerFactory.getLogger(TcmbDepositRateFetcher.class);

    private static final String EVDS3_DATA_URL = "https://evds3.tcmb.gov.tr/igmevdsms-dis/fe";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    private static final DateTimeFormatter EVDS_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public static final List<String> CURRENCIES = List.of("TRY", "USD", "EUR");
    public static final List<String> MATURITIES = List.of("MT01", "MT02", "MT03", "MT04", "MT05", "MT06");

    @Value("${app.evds.api-key:}")
    private String apiKey;

    @Value("${app.bonds.tcmb.evds3-jsessionid:}")
    private String jsessionId;

    @Value("${app.bonds.tcmb.evds3-ts-cookie:}")
    private String tsCookie;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Builds the WebClient with a browser User-Agent and an enlarged in-memory buffer for EVDS3 payloads. */
    public TcmbDepositRateFetcher() {
        this.webClient = WebClient.builder()
                .defaultHeader("User-Agent", USER_AGENT)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    /**
     * Fetch monthly deposit rate rows between {@code from} and {@code to} (inclusive),
     * one row per (period, currency) with all 6 maturity buckets populated.
     */
    public List<DepositRatePoint> fetchDepositRates(LocalDate from, LocalDate to) {
        // Proceed if EITHER auth method is available — the persistent api-key
        // (preferred) OR session cookies. The old guard required cookies even
        // when a valid api-key was set, dead-ending the api-key path and leaving
        // deposit rates empty. Mirrors EvdsBondYieldFetcher's OR logic.
        boolean haveKey = apiKey != null && !apiKey.isBlank();
        boolean haveCookies = jsessionId != null && !jsessionId.isBlank()
                && tsCookie != null && !tsCookie.isBlank();
        if (!haveKey && !haveCookies) {
            log.warn("[DepositRates] no EVDS auth (api-key or session cookies) configured; returning empty");
            return List.of();
        }

        log.info("[DepositRates] Fetching deposit rates from EVDS3: {} → {}", from, to);

        // (currency, periodDate) → DepositRatePoint
        Map<String, DepositRatePoint> merged = new LinkedHashMap<>();

        int seriesOk = 0, seriesFail = 0;
        for (String currency : CURRENCIES) {
            for (String maturity : MATURITIES) {
                String code = "TP." + currency + "." + maturity;
                Map<LocalDate, BigDecimal> seriesData = fetchSeries(code, from, to);
                if (seriesData.isEmpty()) { seriesFail++; continue; }
                seriesOk++;

                for (Map.Entry<LocalDate, BigDecimal> e : seriesData.entrySet()) {
                    String key = currency + "|" + e.getKey();
                    DepositRatePoint point = merged.computeIfAbsent(key,
                            k -> new DepositRatePoint(e.getKey(), currency));
                    assignRate(point, maturity, e.getValue());
                }
            }
        }

        log.info("[DepositRates] Fetched {} (currency,month) rows. series ok={}, fail={}",
                merged.size(), seriesOk, seriesFail);
        return new ArrayList<>(merged.values());
    }

    private static void assignRate(DepositRatePoint point, String maturity, BigDecimal value) {
        switch (maturity) {
            case "MT01" -> point.setRate1m(value);
            case "MT02" -> point.setRate3m(value);
            case "MT03" -> point.setRate6m(value);
            case "MT04" -> point.setRate12m(value);
            case "MT05" -> point.setRateOver12m(value);
            case "MT06" -> point.setRateAvg(value);
            default -> { /* unknown bucket — ignore */ }
        }
    }

    /** Fetch a single EVDS3 series. Returns {@code period (1st of month) → numeric value}. */
    private Map<LocalDate, BigDecimal> fetchSeries(String seriesCode, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        try {
            String body = String.format(
                    "{\"type\":\"json\",\"series\":\"%s\",\"aggregationTypes\":\"avg\",\"formulas\":\"0\","
                            + "\"dateFormat\":\"0\",\"decimal\":\"4\",\"decimalSeperator\":\".\","
                            + "\"startDate\":\"%s\",\"endDate\":\"%s\",\"frequency\":\"5\","
                            + "\"groupSeperator\":true,\"isRaporSayfasi\":false,\"lang\":\"tr\","
                            + "\"ozelFormuller\":[],\"sira\":\"0\",\"yon\":\"0\"}",
                    seriesCode,
                    from.format(EVDS_DATE),
                    to.format(EVDS_DATE));

            var spec = webClient.post()
                    .uri(EVDS3_DATA_URL)
                    .header("Content-Type", "application/json")
                    .header("Origin", "https://evds3.tcmb.gov.tr")
                    .header("Referer", "https://evds3.tcmb.gov.tr/");
            if (apiKey != null && !apiKey.isBlank()) {
                spec = spec.header("key", apiKey);
            } else {
                spec = spec.header("Cookie",
                        "JSESSIONID=" + jsessionId + "; TS017d0b0b=" + tsCookie);
            }
            String response = spec
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));

            if (response == null || response.isBlank() || response.startsWith("<")) {
                log.debug("[DepositRates] Series {} non-JSON response", seriesCode);
                return result;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) return result;

            String column = seriesCode.replace('.', '_');
            for (JsonNode item : items) {
                JsonNode dateNode = item.get("Tarih");
                JsonNode val = item.get(column);
                if (dateNode == null || val == null || val.isNull()) continue;
                String dateStr = dateNode.asText("").trim();
                String valStr = val.asText("").trim();
                if (dateStr.isEmpty() || valStr.isEmpty() || "null".equalsIgnoreCase(valStr)) continue;

                LocalDate periodDate = parsePeriod(dateStr);
                if (periodDate == null) continue;

                try {
                    // TCMB sends Anglo-Saxon format (comma = thousands, dot = decimal).
                    // Strip commas; do not convert them.
                    BigDecimal numeric = new BigDecimal(valStr.replace(",", ""));
                    result.put(periodDate, numeric);
                } catch (NumberFormatException nfe) {
                    log.debug("[DepositRates] Unparseable value '{}' for {} on {}", valStr, seriesCode, dateStr);
                }
            }
        } catch (Exception e) {
            log.debug("[DepositRates] Failed to fetch series {}: {}", seriesCode, e.getMessage());
        }
        return result;
    }

    private static LocalDate parsePeriod(String yearMonth) {
        try {
            String[] parts = yearMonth.split("-");
            if (parts.length != 2) return null;
            return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1);
        } catch (Exception e) {
            return null;
        }
    }
}

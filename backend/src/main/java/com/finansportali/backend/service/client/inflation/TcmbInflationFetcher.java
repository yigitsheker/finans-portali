package com.finansportali.backend.service.client.inflation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finansportali.backend.entity.InflationDataPoint;
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
 * TCMB EVDS3 inflation data fetcher. Pulls monthly CPI (TÜFE) and PPI (Yİ-ÜFE)
 * series via the same session-cookie scheme used by {@link
 * com.finansportali.backend.service.client.bond.TcmbBondDataProvider}.
 *
 * <p>Series used:
 * <ul>
 *   <li>{@code TP.FG.J0}     - TÜFE genel endeks (CPI level, 2003=100)</li>
 *   <li>{@code TP.FE.OKTG01} - TÜFE yıllık % değişim</li>
 *   <li>{@code TP.FE.OKTGY01} - TÜFE aylık % değişim</li>
 *   <li>{@code TP.UFE.S01}   - Yurt İçi Üretici Fiyat Endeksi (PPI level)</li>
 *   <li>{@code TP.UFE.YEYI}  - Yİ-ÜFE yıllık % değişim</li>
 * </ul>
 *
 * Returns an empty list when EVDS3 cookies expire — caller falls back to whatever
 * is already persisted in {@code inflation_data_points}.
 */
@Service
public class TcmbInflationFetcher {

    private static final Logger log = LoggerFactory.getLogger(TcmbInflationFetcher.class);

    private static final String EVDS3_DATA_URL = "https://evds3.tcmb.gov.tr/igmevdsms-dis/fe";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    private static final DateTimeFormatter EVDS_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // We rely on a single verified series — TP.FG.J0 (TÜFE Genel Endeks).
    // Yearly and monthly percentage changes are derived in-memory from the index.
    // Other series probed (return 500 / mismatched semantics):
    //   TP.FE.OKTG01, TP.FE.OKTGY01, TP.UFE.S01, TP.UFE.YEYI — disabled.
    private static final String SERIES_CPI_INDEX = "TP.FG.J0";

    @Value("${app.evds.api-key:}")
    private String apiKey;

    @Value("${app.bonds.tcmb.evds3-jsessionid:}")
    private String jsessionId;

    @Value("${app.bonds.tcmb.evds3-ts-cookie:}")
    private String tsCookie;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Builds the WebClient with a browser User-Agent and an enlarged in-memory buffer for EVDS3 payloads. */
    public TcmbInflationFetcher() {
        this.webClient = WebClient.builder()
                .defaultHeader("User-Agent", USER_AGENT)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    /**
     * Fetch monthly inflation rows between {@code from} and {@code to} (inclusive).
     * Merges multiple series into one row per month.
     */
    public List<InflationDataPoint> fetchInflationHistory(LocalDate from, LocalDate to) {
        // Proceed if EITHER auth method is available — the persistent api-key
        // (preferred) OR session cookies. The old guard required cookies even
        // when a valid api-key was set, dead-ending the api-key path and leaving
        // TR inflation empty (Analysis page showed only US). Mirrors the bond fetcher.
        boolean haveKey = apiKey != null && !apiKey.isBlank();
        boolean haveCookies = jsessionId != null && !jsessionId.isBlank()
                && tsCookie != null && !tsCookie.isBlank();
        if (!haveKey && !haveCookies) {
            log.warn("[Inflation] no EVDS auth (api-key or session cookies) configured; returning empty");
            return List.of();
        }

        log.info("[Inflation] Fetching TÜFE & Yİ-ÜFE from EVDS3: {} → {}", from, to);

        Map<LocalDate, BigDecimal> cpiIndex = fetchSeries(SERIES_CPI_INDEX, from, to);

        Map<LocalDate, InflationDataPoint> merged = new LinkedHashMap<>();
        cpiIndex.forEach((d, v) -> merged.computeIfAbsent(d, InflationDataPoint::new).setCpiIndex(v));

        List<InflationDataPoint> out = new ArrayList<>(merged.values());
        out.sort((a, b) -> a.getPeriodDate().compareTo(b.getPeriodDate()));

        // Derive monthly and yearly % change from the CPI index level.
        // monthly:  cpi[i]  / cpi[i-1]  - 1
        // yearly :  cpi[i]  / cpi[i-12] - 1
        for (int i = 0; i < out.size(); i++) {
            BigDecimal cur = out.get(i).getCpiIndex();
            if (cur == null || cur.signum() <= 0) continue;

            if (i >= 1) {
                BigDecimal prev = out.get(i - 1).getCpiIndex();
                if (prev != null && prev.signum() > 0) {
                    out.get(i).setCpiMonthlyChange(pctChange(cur, prev));
                }
            }
            if (i >= 12) {
                BigDecimal prevYear = out.get(i - 12).getCpiIndex();
                if (prevYear != null && prevYear.signum() > 0) {
                    out.get(i).setCpiYearlyChange(pctChange(cur, prevYear));
                }
            }
        }

        log.info("[Inflation] Got {} monthly rows (cpiIndex coverage={})", out.size(), cpiIndex.size());
        return out;
    }

    private static BigDecimal pctChange(BigDecimal cur, BigDecimal prev) {
        return cur.divide(prev, 8, java.math.RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Fetch a single EVDS3 series. Returns {@code period (1st of month) → value}.
     */
    private Map<LocalDate, BigDecimal> fetchSeries(String seriesCode, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        try {
            String body = String.format(
                    "{\"type\":\"json\",\"series\":\"%s\",\"aggregationTypes\":\"avg\",\"formulas\":\"0\"," +
                    "\"dateFormat\":\"0\",\"decimal\":\"4\",\"decimalSeperator\":\".\"," +
                    "\"startDate\":\"%s\",\"endDate\":\"%s\",\"frequency\":\"5\"," +
                    "\"groupSeperator\":true,\"isRaporSayfasi\":false,\"lang\":\"tr\"," +
                    "\"ozelFormuller\":[],\"sira\":\"0\",\"yon\":\"0\"}",
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
                log.warn("[Inflation] EVDS3 non-JSON response for series {} (session expired?)", seriesCode);
                return result;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                log.warn("[Inflation] EVDS3 returned no items for series {}", seriesCode);
                return result;
            }

            String column = seriesCode.replace('.', '_');
            for (JsonNode item : items) {
                JsonNode dateNode = item.get("Tarih");
                JsonNode val = item.get(column);
                if (dateNode == null || val == null || val.isNull()) continue;

                String dateStr = dateNode.asText("").trim();   // "2025-12"
                String valStr = val.asText("").trim();
                if (dateStr.isEmpty() || valStr.isEmpty() || "null".equalsIgnoreCase(valStr)) continue;

                LocalDate periodDate = parsePeriod(dateStr);
                if (periodDate == null) continue;

                try {
                    // We requested decimalSeperator='.', so TCMB sends Anglo-Saxon format
                    // like "3,683.8300" — comma is the thousands separator, dot is decimal.
                    // Strip commas; do NOT translate them to dots (would yield "3.683.8300").
                    String cleaned = valStr.replace(",", "");
                    BigDecimal numeric = new BigDecimal(cleaned);
                    result.put(periodDate, numeric);
                } catch (NumberFormatException nfe) {
                    log.debug("[Inflation] Unparseable value '{}' for {} on {}", valStr, seriesCode, dateStr);
                }
            }
        } catch (Exception e) {
            log.warn("[Inflation] Failed to fetch series {}: {}", seriesCode, e.getMessage());
        }
        return result;
    }

    /** Parse "2025-12" (EVDS3 monthly format) → first of that month. */
    private LocalDate parsePeriod(String yearMonth) {
        try {
            String[] parts = yearMonth.split("-");
            if (parts.length != 2) return null;
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            return LocalDate.of(year, month, 1);
        } catch (Exception e) {
            return null;
        }
    }
}

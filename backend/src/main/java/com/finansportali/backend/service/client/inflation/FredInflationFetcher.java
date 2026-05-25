package com.finansportali.backend.service.client.inflation;

import com.fasterxml.jackson.databind.JsonNode;
import com.finansportali.backend.entity.InflationDataPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * US CPI fetcher backed by the FRED (Federal Reserve Economic Data) API.
 *
 * <p>Series used:
 * <ul>
 *   <li>{@code CPIAUCSL} — Consumer Price Index for All Urban Consumers,
 *   seasonally adjusted, monthly (1982-1984 = 100).</li>
 * </ul>
 *
 * <p>YoY % change and MoM % change are derived in-memory from the level
 * series so we only call FRED once per refresh. Returns an empty list when
 * the API key is missing or the call fails — caller falls back to whatever
 * is already persisted for country=US.
 *
 * <p>Docs: <a href="https://fred.stlouisfed.org/docs/api/fred/series_observations.html">
 * fred/series/observations</a>
 */
@Service
public class FredInflationFetcher {

    private static final Logger log = LoggerFactory.getLogger(FredInflationFetcher.class);

    private static final String FRED_BASE_URL = "https://api.stlouisfed.org";
    private static final String SERIES_OBSERVATIONS_PATH = "/fred/series/observations";
    private static final String CPI_SERIES_ID = "CPIAUCSL";

    @Value("${app.fred.api-key:}")
    private String apiKey;

    private final WebClient webClient = WebClient.builder()
            .baseUrl(FRED_BASE_URL)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();

    /**
     * Pull monthly CPIAUCSL observations between {@code from} and {@code to}
     * (inclusive of both endpoints), and derive yearly / monthly % change
     * from the resulting level series.
     */
    public List<InflationDataPoint> fetchInflationHistory(LocalDate from, LocalDate to) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[Inflation/US] FRED API key not configured — skipping US refresh");
            return List.of();
        }

        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE;
        JsonNode body;
        try {
            body = webClient.get()
                    .uri(uri -> uri.path(SERIES_OBSERVATIONS_PATH)
                            .queryParam("series_id", CPI_SERIES_ID)
                            .queryParam("api_key", apiKey)
                            .queryParam("file_type", "json")
                            .queryParam("observation_start", iso.format(from))
                            .queryParam("observation_end", iso.format(to))
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();
        } catch (RuntimeException e) {
            log.warn("[Inflation/US] FRED fetch failed: {}", e.getMessage());
            return List.of();
        }

        if (body == null || !body.hasNonNull("observations")) {
            log.warn("[Inflation/US] FRED response missing observations");
            return List.of();
        }

        // Build a date→level map first so the YoY/MoM math has a complete picture.
        TreeMap<LocalDate, BigDecimal> levels = new TreeMap<>();
        for (JsonNode obs : body.get("observations")) {
            String date = obs.path("date").asText(null);
            String value = obs.path("value").asText(null);
            if (date == null || value == null || ".".equals(value)) continue;
            try {
                LocalDate periodDate = LocalDate.parse(date).withDayOfMonth(1);
                levels.put(periodDate, new BigDecimal(value));
            } catch (NumberFormatException | java.time.format.DateTimeParseException ex) {
                // Bad row — skip, keep going.
            }
        }

        List<InflationDataPoint> points = new ArrayList<>(levels.size());
        BigDecimal prevMonthLevel = null;
        for (var entry : levels.entrySet()) {
            LocalDate period = entry.getKey();
            BigDecimal level = entry.getValue();
            InflationDataPoint p = new InflationDataPoint(period, "US");
            p.setCpiIndex(level);
            p.setSource("FRED_CPIAUCSL");

            BigDecimal yearAgo = levels.get(period.minusYears(1));
            if (yearAgo != null && yearAgo.signum() > 0) {
                p.setCpiYearlyChange(pctChange(level, yearAgo));
            }
            if (prevMonthLevel != null && prevMonthLevel.signum() > 0) {
                p.setCpiMonthlyChange(pctChange(level, prevMonthLevel));
            }
            points.add(p);
            prevMonthLevel = level;
        }
        log.info("[Inflation/US] FRED returned {} monthly CPI rows", points.size());
        return points;
    }

    private static BigDecimal pctChange(BigDecimal current, BigDecimal base) {
        return current.divide(base, 8, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);
    }
}

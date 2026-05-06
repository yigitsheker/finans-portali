package com.finansportali.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Yahoo Finance v8/finance/chart fetcher.
 *
 * Symbol formats (verified):
 *   FX        : USDTRY=X, EURTRY=X
 *   Crypto    : BTC-USD, ETH-USD, SOL-USD
 *   US Stocks : AAPL, MSFT, GOOGL, AMZN, NVDA, TSLA, META
 *   BIST      : THYAO.IS, GARAN.IS, ASELS.IS, KCHOL.IS, etc.
 *   Index     : XU100.IS
 *   Commodity : GC=F (gold futures USD/oz)
 */
@Component
public class YahooPriceFetcher {

    private static final Logger log = LoggerFactory.getLogger(YahooPriceFetcher.class);
    private static final String BASE = "https://query1.finance.yahoo.com";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final WebClient client;

    public YahooPriceFetcher() {
        this.client = WebClient.builder()
                .baseUrl(BASE)
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    // ── Quote ────────────────────────────────────────────────────────────────

    public Optional<YahooQuote> fetchQuote(String yahooSymbol) {
        try {
            Map<?, ?> resp = client.get()
                    .uri(u -> u.path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", "1d")
                            .queryParam("range", "2d")
                            .build(yahooSymbol))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return parseQuote(yahooSymbol, resp);

        } catch (WebClientResponseException e) {
            log.warn("[Yahoo] HTTP {} for symbol '{}': {}", e.getStatusCode(), yahooSymbol, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[Yahoo] fetchQuote failed for '{}': {}", yahooSymbol, e.getMessage());
            return Optional.empty();
        }
    }

    // ── History ──────────────────────────────────────────────────────────────

    /**
     * Fetch historical candles with explicit range and interval.
     *
     * Recommended mappings:
     *   UI "1D"  -> range="1d",  interval="5m"  (intraday)
     *   UI "5D"  -> range="5d",  interval="1h"
     *   UI "1A"  -> range="1mo", interval="1d"
     *   UI "1Y"  -> range="1y",  interval="1d"
     */
    public List<DayClose> fetchHistory(String yahooSymbol, String range, String interval) {
        try {
            log.info("[Yahoo] fetchHistory symbol='{}' range='{}' interval='{}'",
                    yahooSymbol, range, interval);

            Map<?, ?> resp = client.get()
                    .uri(u -> u.path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", interval)
                            .queryParam("range", range)
                            .build(yahooSymbol))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<DayClose> result = parseHistory(yahooSymbol, resp, interval);
            log.info("[Yahoo] fetchHistory symbol='{}' range='{}' interval='{}' -> {} candles",
                    yahooSymbol, range, interval, result.size());

            if (!result.isEmpty()) {
                DayClose first = result.get(0);
                DayClose last  = result.get(result.size() - 1);
                log.info("[Yahoo] First candle: {} close={} | Last candle: {} close={}",
                        first.timestamp(), first.close(), last.timestamp(), last.close());
            }

            return result;

        } catch (WebClientResponseException e) {
            log.warn("[Yahoo] HTTP {} history for '{}': {}", e.getStatusCode(), yahooSymbol, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("[Yahoo] fetchHistory failed for '{}': {}", yahooSymbol, e.getMessage());
            return List.of();
        }
    }

    /** Backward-compat: daily history */
    public List<DayClose> fetchDailyHistory(String yahooSymbol, String range) {
        return fetchHistory(yahooSymbol, range, "1d");
    }

    // ── Parse ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Optional<YahooQuote> parseQuote(String symbol, Map<?, ?> resp) {
        if (resp == null) {
            log.debug("[Yahoo] Null response for '{}'", symbol);
            return Optional.empty();
        }
        try {
            Map<String, Object> chart = (Map<String, Object>) resp.get("chart");
            if (chart == null) return empty(symbol, "no 'chart' key");

            Map<?, ?> error = (Map<?, ?>) chart.get("error");
            if (error != null) {
                log.warn("[Yahoo] API error for '{}': {}", symbol, error.get("description"));
                return Optional.empty();
            }

            List<Map<String, Object>> results = (List<Map<String, Object>>) chart.get("result");
            if (results == null || results.isEmpty()) return empty(symbol, "empty result");

            Map<String, Object> meta = (Map<String, Object>) results.get(0).get("meta");
            if (meta == null) return empty(symbol, "null meta");

            double currentPrice = toDouble(meta.get("regularMarketPrice"));
            if (currentPrice <= 0) return empty(symbol, "regularMarketPrice=0");

            double prevClose = toDouble(meta.get("chartPreviousClose"));
            if (prevClose <= 0) prevClose = toDouble(meta.get("previousClose"));

            BigDecimal last   = bd(currentPrice);
            BigDecimal prev   = prevClose > 0 ? bd(prevClose) : null;
            BigDecimal chgAbs = BigDecimal.ZERO;
            BigDecimal chgPct = BigDecimal.ZERO;

            if (prev != null && prev.compareTo(BigDecimal.ZERO) != 0) {
                chgAbs = last.subtract(prev);
                chgPct = chgAbs.divide(prev, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            String currency = str(meta.get("currency"));
            String exchange  = str(meta.get("exchangeName"));

            log.debug("[Yahoo] OK '{}' -> price={} prev={} chg={}%",
                    symbol, currentPrice, prevClose, chgPct.setScale(2, RoundingMode.HALF_UP));

            return Optional.of(new YahooQuote(last, prev, chgAbs, chgPct, currency, exchange));

        } catch (Exception e) {
            log.warn("[Yahoo] Parse error for '{}': {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<DayClose> parseHistory(String symbol, Map<?, ?> resp, String interval) {
        if (resp == null) return List.of();
        try {
            Map<String, Object> chart = (Map<String, Object>) resp.get("chart");
            if (chart == null) return List.of();

            Map<?, ?> error = (Map<?, ?>) chart.get("error");
            if (error != null) {
                log.warn("[Yahoo] History API error for '{}': {}", symbol, error.get("description"));
                return List.of();
            }

            List<Map<String, Object>> results = (List<Map<String, Object>>) chart.get("result");
            if (results == null || results.isEmpty()) return List.of();

            Map<String, Object> result = results.get(0);
            List<?> timestampList = (List<?>) result.get("timestamp");
            Map<String, Object> indicators = (Map<String, Object>) result.get("indicators");

            if (timestampList == null || indicators == null) return List.of();

            List<Map<String, Object>> quoteList =
                    (List<Map<String, Object>>) indicators.get("quote");
            if (quoteList == null || quoteList.isEmpty()) return List.of();

            List<Double> closes = (List<Double>) quoteList.get(0).get("close");
            if (closes == null) return List.of();

            boolean isIntraday = isIntradayInterval(interval);

            List<DayClose> out = new ArrayList<>();
            Set<Long> seenTs = new LinkedHashSet<>();
            int nullCount = 0;
            int zeroCount = 0;
            int dupCount  = 0;

            for (int i = 0; i < timestampList.size(); i++) {
                // Handle both Integer and Long timestamps from Yahoo API
                Long ts = null;
                Object timestampObj = timestampList.get(i);
                if (timestampObj instanceof Number) {
                    ts = ((Number) timestampObj).longValue();
                }
                
                if (ts == null) { nullCount++; continue; }

                // Deduplicate by timestamp
                if (!seenTs.add(ts)) { dupCount++; continue; }

                if (i >= closes.size() || closes.get(i) == null) { nullCount++; continue; }

                double close = closes.get(i);
                if (close <= 0 || Double.isNaN(close) || Double.isInfinite(close)) {
                    zeroCount++;
                    continue;
                }

                // Convert Unix timestamp to LocalDate (UTC)
                LocalDate day = Instant.ofEpochSecond(ts).atZone(ZoneOffset.UTC).toLocalDate();
                // For intraday, also store the full datetime for label
                String label;
                if (isIntraday) {
                    LocalDateTime dt = Instant.ofEpochSecond(ts).atZone(ZoneOffset.UTC).toLocalDateTime();
                    // Include date prefix for multi-day intraday (5d range) to avoid duplicate labels
                    // Single-day (1d): "HH:mm", Multi-day (5d): "MM-dd HH:mm"
                    label = String.format("%02d-%02d %02d:%02d",
                            dt.getMonthValue(), dt.getDayOfMonth(),
                            dt.getHour(), dt.getMinute());
                } else {
                    label = day.toString();
                }

                out.add(new DayClose(day, bd(close), ts, label));
            }

            // Sort by timestamp ascending (critical — Yahoo sometimes returns unsorted)
            out.sort(Comparator.comparingLong(DayClose::timestamp));

            // Outlier detection: log suspicious spikes (>40% change between consecutive candles)
            for (int i = 1; i < out.size(); i++) {
                double prev = out.get(i - 1).close().doubleValue();
                double curr = out.get(i).close().doubleValue();
                if (prev > 0) {
                    double changePct = Math.abs((curr - prev) / prev) * 100;
                    if (changePct > 40) {
                        log.warn("[Yahoo] Suspicious spike in '{}' at {}: prev={} curr={} change={}%",
                                symbol, out.get(i).day(), prev, curr,
                                String.format("%.1f", changePct));
                    }
                }
            }

            if (nullCount > 0)  log.debug("[Yahoo] '{}': {} null/invalid closes removed", symbol, nullCount);
            if (zeroCount > 0)  log.debug("[Yahoo] '{}': {} zero/negative closes removed", symbol, zeroCount);
            if (dupCount > 0)   log.warn("[Yahoo] '{}': {} duplicate timestamps removed", symbol, dupCount);

            log.debug("[Yahoo] History '{}' -> {} clean candles (interval={})", symbol, out.size(), interval);
            return out;

        } catch (Exception e) {
            log.warn("[Yahoo] History parse error for '{}': {}", symbol, e.getMessage());
            return List.of();
        }
    }

    private static boolean isIntradayInterval(String interval) {
        if (interval == null) return false;
        return interval.endsWith("m") || interval.endsWith("h");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Optional<YahooQuote> empty(String symbol, String reason) {
        log.debug("[Yahoo] Skip '{}': {}", symbol, reason);
        return Optional.empty();
    }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        try { return ((Number) o).doubleValue(); } catch (Exception e) { return 0.0; }
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
    
    private static BigDecimal bd(Object o) {
        if (o == null) return null;
        if (o instanceof Number) {
            return BigDecimal.valueOf(((Number) o).doubleValue());
        }
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Long num(Object o) {
        if (o == null) return null;
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record YahooQuote(
            BigDecimal last,
            BigDecimal previousClose,
            BigDecimal changeAbs,
            BigDecimal changePct,
            String currency,
            String exchange
    ) {}

    /**
     * A single historical candle point.
     * timestamp: Unix epoch seconds (for sorting)
     * label: display string for chart axis (HH:mm for intraday, yyyy-MM-dd for daily)
     */
    public record DayClose(
            LocalDate day,
            BigDecimal close,
            long timestamp,
            String label
    ) {
        /** Backward-compat constructor for daily candles */
        public DayClose(LocalDate day, BigDecimal close) {
            this(day, close, day.toEpochDay() * 86400L, day.toString());
        }
    }
    
    /**
     * Intraday price point with datetime
     */
    public record IntradayPoint(
            LocalDateTime datetime,
            BigDecimal price
    ) {}
    
    /**
     * Fetch intraday prices for today (1D chart)
     * @param yahooSymbol e.g. "ASELS.IS", "AAPL", "BTC-USD"
     * @param interval e.g. "5m", "15m", "1h"
     * @return list of intraday points
     */
    public List<IntradayPoint> fetchIntraday(String yahooSymbol, String interval) {
        try {
            log.info("Fetching intraday data for symbol={}, interval={}", yahooSymbol, interval);
            
            Map<?, ?> resp = client.get()
                    .uri(u -> u.path("/v8/finance/chart/{symbol}")
                            .queryParam("range", "1d")
                            .queryParam("interval", interval)
                            .build(yahooSymbol))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (resp == null) {
                log.warn("No response from Yahoo for intraday {}", yahooSymbol);
                return List.of();
            }
            
            Map<?, ?> chart = (Map<?, ?>) resp.get("chart");
            if (chart == null) return List.of();
            
            List<?> results = (List<?>) chart.get("result");
            if (results == null || results.isEmpty()) return List.of();
            
            Map<?, ?> result = (Map<?, ?>) results.get(0);
            List<?> timestamps = (List<?>) result.get("timestamp");
            Map<?, ?> indicators = (Map<?, ?>) result.get("indicators");
            
            if (timestamps == null || indicators == null) return List.of();
            
            List<?> quotes = (List<?>) indicators.get("quote");
            if (quotes == null || quotes.isEmpty()) return List.of();
            
            Map<?, ?> quote = (Map<?, ?>) quotes.get(0);
            List<?> closes = (List<?>) quote.get("close");
            
            if (closes == null) return List.of();
            
            List<IntradayPoint> points = new ArrayList<>();
            for (int i = 0; i < timestamps.size() && i < closes.size(); i++) {
                Object closeObj = closes.get(i);
                if (closeObj == null) continue;
                
                BigDecimal price = bd(closeObj);
                if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) continue;
                
                Long ts = num(timestamps.get(i));
                if (ts == null) continue;
                
                LocalDateTime datetime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(ts), 
                        ZoneOffset.UTC);
                
                points.add(new IntradayPoint(datetime, price));
            }
            
            log.info("Fetched {} intraday points for {}", points.size(), yahooSymbol);
            return points;
            
        } catch (WebClientResponseException e) {
            log.error("Yahoo intraday fetch failed for {}: {} - {}", 
                    yahooSymbol, e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.error("Error fetching intraday data for {}: {}", yahooSymbol, e.getMessage(), e);
            return List.of();
        }
    }
}
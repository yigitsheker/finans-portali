package com.finansportali.backend.service.market;

import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.dto.response.market.MarketHistoryPoint;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.service.client.market.YahooPriceFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service responsible for market historical data.
 * Handles historical price charts and time series data.
 */
@Service
public class MarketHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MarketHistoryService.class);

    private final MarketInstrumentRepository instrumentRepo;
    private final MarketCandleRepository candleRepo;
    private final YahooPriceFetcher yahooPriceFetcher;
    private final MarketInstrumentService instrumentService;

    public MarketHistoryService(MarketInstrumentRepository instrumentRepo,
                                MarketCandleRepository candleRepo,
                                YahooPriceFetcher yahooPriceFetcher,
                                MarketInstrumentService instrumentService) {
        this.instrumentRepo = instrumentRepo;
        this.candleRepo = candleRepo;
        this.yahooPriceFetcher = yahooPriceFetcher;
        this.instrumentService = instrumentService;
    }

    /**
     * Get historical price data for charting.
     * Cached to improve performance.
     */
    @Cacheable(cacheNames = "marketHistory", key = "#symbol + ':' + #period")
    public List<MarketHistoryPoint> getHistory(String symbol, String period) {
        MarketInstrument inst = instrumentRepo.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + symbol));

        if (usesDatabaseCandles(inst.getInstrumentType())) {
            log.info("Using database candles for {} (type: {})", symbol, inst.getInstrumentType());
            return getDatabaseHistory(inst, period);
        }

        List<MarketHistoryPoint> yahooHistory = fetchYahooHistory(inst, period);
        return yahooHistory.isEmpty() ? getDatabaseHistory(inst, period) : yahooHistory;
    }

    private static boolean usesDatabaseCandles(InstrumentType type) {
        return type == InstrumentType.FUND || type == InstrumentType.BOND || type == InstrumentType.VIOP;
    }

    /**
     * Fetch + clean Yahoo Finance history. Returns an empty list if Yahoo
     * has nothing for this ticker (404'd BIST symbols like KOZAA / SODA) or
     * if the upstream call blows up — the caller falls back to DB candles.
     */
    private List<MarketHistoryPoint> fetchYahooHistory(MarketInstrument inst, String period) {
        String symbol = inst.getSymbol();
        String yahooSymbol = instrumentService.normalizeSymbolForYahoo(symbol, inst.getInstrumentType());
        YahooRangeConfig config = mapPeriodToYahooRange(period);

        log.info("Fetching chart data: symbol={} yahooSymbol={} period={} range={} interval={}",
                symbol, yahooSymbol, period, config.range(), config.interval());

        List<YahooPriceFetcher.DayClose> yahooData;
        try {
            yahooData = yahooPriceFetcher.fetchHistory(yahooSymbol, config.range(), config.interval());
        } catch (RuntimeException e) {
            // WebClient + reactor wrap upstream HTTP / parsing failures in
            // various RuntimeException subclasses (WebClientResponseException,
            // DecodingException, IllegalStateException for block-timeout etc.).
            // Catching the base RuntimeException lets us fall back to DB
            // candles without coupling to every WebClient internal type.
            log.error("Failed to fetch Yahoo chart data for symbol={} yahooSymbol={}: {}",
                    symbol, yahooSymbol, e.getMessage(), e);
            return List.of();
        }

        if (yahooData.isEmpty()) {
            log.warn("No Yahoo data for symbol={} yahooSymbol={} range={} interval={} — falling back to DB candles",
                    symbol, yahooSymbol, config.range(), config.interval());
            return List.of();
        }

        List<MarketHistoryPoint> result = cleanHistoricalData(yahooData, symbol).stream()
                .map(d -> new MarketHistoryPoint(d.day(), d.close(), d.label(), d.timestamp()))
                .toList();

        logChartSummary(symbol, yahooSymbol, period, result);
        return result;
    }

    private static void logChartSummary(String symbol, String yahooSymbol, String period,
                                        List<MarketHistoryPoint> result) {
        log.info("Chart data processed: symbol={} yahooSymbol={} period={} -> {} clean points",
                symbol, yahooSymbol, period, result.size());
        if (result.isEmpty()) {
            return;
        }
        MarketHistoryPoint first = result.get(0);
        MarketHistoryPoint last = result.get(result.size() - 1);
        log.info("Chart range: {} (close={}) to {} (close={})",
                first.label(), first.close(), last.label(), last.close());
    }

    /**
     * Map UI period to Yahoo Finance range and interval parameters.
     */
    private YahooRangeConfig mapPeriodToYahooRange(String period) {
        return switch ((period == null ? "30D" : period).toUpperCase(Locale.ROOT)) {
            case "1D", "1G" -> new YahooRangeConfig("1d", "5m");   // Intraday 5-minute
            case "5D", "5G" -> new YahooRangeConfig("5d", "1h");   // 5 days, hourly
            case "30D", "1A" -> new YahooRangeConfig("1mo", "1d"); // 1 month, daily
            case "1Y" -> new YahooRangeConfig("1y", "1d");         // 1 year, daily
            default -> new YahooRangeConfig("1mo", "1d");          // Default to 1 month
        };
    }

    /**
     * Clean and validate historical data from Yahoo Finance.
     */
    private List<YahooPriceFetcher.DayClose> cleanHistoricalData(
            List<YahooPriceFetcher.DayClose> rawData, String symbol) {

        List<YahooPriceFetcher.DayClose> cleaned = new ArrayList<>();
        int nullCount = 0;
        int zeroCount = 0;
        int outlierCount = 0;

        for (int i = 0; i < rawData.size(); i++) {
            YahooPriceFetcher.DayClose candle = rawData.get(i);

            // Skip null candles
            if (candle == null || candle.close() == null) {
                nullCount++;
                continue;
            }

            // Skip zero or negative prices
            if (candle.close().compareTo(BigDecimal.ZERO) <= 0) {
                zeroCount++;
                continue;
            }

            // Outlier detection: check for suspicious spikes
            if (!cleaned.isEmpty()) {
                YahooPriceFetcher.DayClose prev = cleaned.get(cleaned.size() - 1);
                double prevPrice = prev.close().doubleValue();
                double currPrice = candle.close().doubleValue();

                if (prevPrice > 0) {
                    double changePct = Math.abs((currPrice - prevPrice) / prevPrice) * 100;
                    if (changePct > 40) {
                        log.warn("Suspicious price spike detected for {}: {} -> {} ({}% change) at {}",
                                symbol, prevPrice, currPrice, String.format("%.1f", changePct), candle.label());
                        outlierCount++;
                        // Still include the point but log it for investigation
                    }
                }
            }

            cleaned.add(candle);
        }

        if (nullCount > 0) log.debug("Removed {} null candles for {}", nullCount, symbol);
        if (zeroCount > 0) log.debug("Removed {} zero/negative candles for {}", zeroCount, symbol);
        if (outlierCount > 0) log.warn("Found {} potential outliers for {}", outlierCount, symbol);

        log.debug("Data cleaning for {}: {} raw -> {} clean candles", symbol, rawData.size(), cleaned.size());
        return cleaned;
    }

    /**
     * Fallback to database candles when Yahoo Finance fails.
     */
    private List<MarketHistoryPoint> getDatabaseHistory(MarketInstrument inst, String period) {
        int days = switch ((period == null ? "30D" : period).toUpperCase(Locale.ROOT)) {
            case "1D", "1G" -> 2;
            case "5D", "5G" -> 5;
            case "30D", "1A" -> 30;
            case "1Y" -> 365;
            default -> 30;
        };

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);

        return candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(inst, start, end)
                .stream()
                .map(c -> new MarketHistoryPoint(c.getDay(), c.getClose()))
                .toList();
    }

    /**
     * Configuration record for Yahoo Finance range and interval parameters.
     */
    private record YahooRangeConfig(String range, String interval) {
    }
}

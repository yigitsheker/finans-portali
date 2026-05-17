package com.finansportali.backend.service.portfolio;

import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.PortfolioPosition;
import com.finansportali.backend.dto.response.portfolio.PortfolioPerformancePoint;
import com.finansportali.backend.dto.response.portfolio.PortfolioPerformanceResponse;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.PortfolioPositionRepository;
import com.finansportali.backend.service.HistoricalPriceService;
import com.finansportali.backend.service.MarketService;
import com.finansportali.backend.service.client.market.YahooPriceFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service responsible for calculating portfolio performance over time.
 * Handles historical performance calculations using real market data.
 */
@Service
public class PortfolioPerformanceService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioPerformanceService.class);

    private final PortfolioPositionRepository positionRepo;
    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;
    private final MarketService marketService;
    private final HistoricalPriceService historicalPriceService;
    private final YahooPriceFetcher yahooPriceFetcher;
    private final PortfolioCurrencyService currencyService;

    public PortfolioPerformanceService(PortfolioPositionRepository positionRepo,
                                       MarketInstrumentRepository instrumentRepo,
                                       MarketQuoteRepository quoteRepo,
                                       MarketService marketService,
                                       HistoricalPriceService historicalPriceService,
                                       YahooPriceFetcher yahooPriceFetcher,
                                       PortfolioCurrencyService currencyService) {
        this.positionRepo = positionRepo;
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.marketService = marketService;
        this.historicalPriceService = historicalPriceService;
        this.yahooPriceFetcher = yahooPriceFetcher;
        this.currencyService = currencyService;
    }

    /**
     * Resolve the per-position TRY conversion multiplier. USD-denominated instruments
     * (international stocks, crypto) get multiplied by the current USDTRY rate so the
     * chart value is in the same units as the "Toplam Portföy Değeri" card.
     */
    private BigDecimal toTryMultiplier(PortfolioPosition pos, BigDecimal usdTryRate) {
        MarketInstrument inst = instrumentRepo.findBySymbol(pos.getSymbol()).orElse(null);
        InstrumentType type = inst != null ? inst.getInstrumentType() : null;
        String currency = currencyService.getInstrumentCurrency(pos.getSymbol(), type);
        return "USD".equals(currency) ? usdTryRate : BigDecimal.ONE;
    }

    /**
     * Calculate portfolio performance over time using real historical prices.
     * For 1D range, uses intraday data if available, otherwise falls back to buy/current prices.
     */
    public PortfolioPerformanceResponse calculatePortfolioPerformance(String userId, String range) {
        log.info("Calculating portfolio performance for userId={}, range={}", userId, range);

        List<PortfolioPosition> positions = positionRepo.findByUserId(userId);

        if (positions.isEmpty()) {
            log.info("No positions found, returning empty performance");
            return new PortfolioPerformanceResponse(range, LocalDate.now(), LocalDate.now(),
                    "DAILY", "EMPTY", List.of());
        }

        // Find earliest buy date
        LocalDate earliestBuyDate = positions.stream()
                .map(p -> p.getPurchaseDate() != null ? p.getPurchaseDate() : LocalDate.now())
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        log.info("Earliest buy date: {}", earliestBuyDate);

        LocalDate today = LocalDate.now();

        // Special handling for 1D range - use intraday data
        if ("1D".equalsIgnoreCase(range)) {
            return calculateIntradayPerformance(userId, positions, earliestBuyDate, today);
        }

        // For other ranges, use daily historical data
        // If earliest buy date is today, always fall back to intraday/buy-current
        if (earliestBuyDate.equals(today)) {
            log.info("All positions bought today, using intraday fallback for range={}", range);
            return calculateIntradayPerformance(userId, positions, earliestBuyDate, today);
        }

        LocalDate startDate = calculateStartDate(earliestBuyDate, range);
        LocalDate endDate = today;

        log.info("Performance period: {} to {}", startDate, endDate);

        // Ensure historical prices for the range are populated in the DB cache for every
        // position. Without this, getClosePriceOnOrBefore would keep returning the same
        // stale price for every date and the chart would render as a flat line.
        Set<String> uniqueSymbols = new HashSet<>();
        for (PortfolioPosition pos : positions) {
            LocalDate posBuyDate = pos.getPurchaseDate() != null ? pos.getPurchaseDate() : today;
            if (posBuyDate.isAfter(endDate)) continue;
            uniqueSymbols.add(pos.getSymbol());
        }
        LocalDate fetchFrom = startDate.minusDays(7); // pad to cover non-trading days before startDate
        for (String symbol : uniqueSymbols) {
            try {
                historicalPriceService.getHistoricalPrices(symbol, fetchFrom, endDate);
            } catch (Exception e) {
                log.warn("Failed to ensure historical prices for symbol={}: {}", symbol, e.getMessage());
            }
        }

        // Generate date points based on range
        List<LocalDate> datePoints = generateDatePoints(startDate, endDate, range);
        log.info("Generated {} date points", datePoints.size());

        // Pre-compute USD→TRY multiplier per position so foreign-currency holdings
        // (e.g. AAPL priced in USD) are summed in the same currency as the
        // "Toplam Portföy Değeri" card.
        BigDecimal usdTryRate = currencyService.getUsdTryRate();
        Map<Long, BigDecimal> tryMultipliers = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            tryMultipliers.put(pos.getId(), toTryMultiplier(pos, usdTryRate));
        }

        // Calculate portfolio value for each date
        List<PortfolioPerformancePoint> points = new ArrayList<>();
        boolean hasHistoricalData = false;

        for (LocalDate date : datePoints) {
            BigDecimal portfolioValue = BigDecimal.ZERO;

            for (PortfolioPosition pos : positions) {
                LocalDate positionBuyDate = pos.getPurchaseDate() != null ?
                        pos.getPurchaseDate() : LocalDate.now();

                // Only include positions that were bought on or before this date
                if (!positionBuyDate.isAfter(date)) {
                    // Get historical price for this date
                    Optional<BigDecimal> historicalPrice = historicalPriceService
                            .getClosePriceOnOrBefore(pos.getSymbol(), date);

                    if (historicalPrice.isPresent()) {
                        BigDecimal positionValue = historicalPrice.get()
                                .multiply(pos.getQuantity())
                                .multiply(tryMultipliers.get(pos.getId()));
                        portfolioValue = portfolioValue.add(positionValue);
                        hasHistoricalData = true;

                        log.debug("Date {}, Symbol {}: price={}, quantity={}, value={}",
                                date, pos.getSymbol(), historicalPrice.get(),
                                pos.getQuantity(), positionValue);
                    } else {
                        log.warn("No historical price found for symbol={} on date={}",
                                pos.getSymbol(), date);
                    }
                }
            }

            if (portfolioValue.compareTo(BigDecimal.ZERO) > 0) {
                points.add(new PortfolioPerformancePoint(date, portfolioValue));
                log.debug("Portfolio value on {}: {}", date, portfolioValue);
            }
        }

        log.info("Calculated {} performance points, hasHistoricalData={}", points.size(), hasHistoricalData);

        // If no historical data, use buy/current fallback
        if (!hasHistoricalData || points.isEmpty()) {
            log.info("No historical data found, using buy/current fallback");
            return createBuyCurrentFallback(positions, startDate, endDate, range);
        }

        if (!points.isEmpty()) {
            log.info("First point: date={}, value={}", points.get(0).date(), points.get(0).value());
            log.info("Last point: date={}, value={}",
                    points.get(points.size() - 1).date(), points.get(points.size() - 1).value());
        }

        return new PortfolioPerformanceResponse(range, startDate, endDate,
                "DAILY", "YAHOO_DAILY", points);
    }

    /**
     * Calculate intraday performance for 1D range
     */
    private PortfolioPerformanceResponse calculateIntradayPerformance(
            String userId, List<PortfolioPosition> positions,
            LocalDate earliestBuyDate, LocalDate today) {

        log.info("Calculating intraday performance for userId={}, earliestBuyDate={}", userId, earliestBuyDate);

        // Try to fetch intraday data for each position bought on or before today
        Map<String, List<YahooPriceFetcher.IntradayPoint>> intradayDataMap = new HashMap<>();
        boolean hasIntradayData = false;

        for (PortfolioPosition pos : positions) {
            // Only include positions bought on or before today
            LocalDate buyDate = pos.getPurchaseDate() != null ? pos.getPurchaseDate() : today;
            if (buyDate.isAfter(today)) continue;

            MarketInstrument inst = instrumentRepo.findBySymbol(pos.getSymbol()).orElse(null);
            if (inst == null) continue;

            String yahooSymbol = marketService.normalizeSymbolForYahoo(
                    inst.getSymbol(), inst.getInstrumentType());

            log.info("Fetching intraday data for symbol={}, yahooSymbol={}", pos.getSymbol(), yahooSymbol);

            List<YahooPriceFetcher.IntradayPoint> intradayPoints =
                    yahooPriceFetcher.fetchIntraday(yahooSymbol, "15m");

            if (!intradayPoints.isEmpty()) {
                intradayDataMap.put(pos.getSymbol(), intradayPoints);
                hasIntradayData = true;
                log.info("Found {} intraday points for {}", intradayPoints.size(), pos.getSymbol());
            } else {
                log.warn("No intraday data for symbol={}", pos.getSymbol());
            }
        }

        if (!hasIntradayData) {
            log.info("No intraday data available, using buy/current fallback");
            return createBuyCurrentFallback(positions, today, today, "1D");
        }

        // Merge intraday data from all positions
        Set<LocalDateTime> allDatetimes = new TreeSet<>();
        for (List<YahooPriceFetcher.IntradayPoint> points : intradayDataMap.values()) {
            for (YahooPriceFetcher.IntradayPoint point : points) {
                allDatetimes.add(point.datetime());
            }
        }

        // Pre-compute USD→TRY multiplier per position.
        BigDecimal usdTryRate = currencyService.getUsdTryRate();
        Map<Long, BigDecimal> tryMultipliers = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            tryMultipliers.put(pos.getId(), toTryMultiplier(pos, usdTryRate));
        }

        // Pre-resolve fallback prices for every position so that markets which haven't
        // opened yet (e.g. NYSE before 16:30 TR time for AAPL) don't drop the position
        // out of the sum and create a fake vertical jump when the first tick arrives.
        // Priority: latest DB quote → cost basis.
        Map<Long, BigDecimal> fallbackPrices = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            BigDecimal fallback = pos.getAvgCost() != null ? pos.getAvgCost() : BigDecimal.ZERO;
            MarketInstrument inst = instrumentRepo.findBySymbol(pos.getSymbol()).orElse(null);
            if (inst != null) {
                fallback = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst)
                        .map(q -> q.getLast())
                        .orElse(fallback);
            }
            fallbackPrices.put(pos.getId(), fallback);
        }

        List<PortfolioPerformancePoint> points = new ArrayList<>();

        for (LocalDateTime datetime : allDatetimes) {
            BigDecimal portfolioValue = BigDecimal.ZERO;

            for (PortfolioPosition pos : positions) {
                LocalDate buyDate = pos.getPurchaseDate() != null ? pos.getPurchaseDate() : today;
                // Exclude positions that hadn't been purchased yet at this datetime.
                // Using today instead of datetime.toLocalDate() would cause a fake vertical
                // jump when a position bought today suddenly "appears" mid-chart.
                if (buyDate.isAfter(datetime.toLocalDate())) continue;

                BigDecimal multiplier = tryMultipliers.get(pos.getId());
                List<YahooPriceFetcher.IntradayPoint> posIntradayData = intradayDataMap.get(pos.getSymbol());

                BigDecimal priceAtMoment = null;
                if (posIntradayData != null) {
                    priceAtMoment = posIntradayData.stream()
                            .filter(p -> !p.datetime().isAfter(datetime))
                            .max(Comparator.comparing(YahooPriceFetcher.IntradayPoint::datetime))
                            .map(YahooPriceFetcher.IntradayPoint::price)
                            .orElse(null);
                }

                // Fallback when no intraday tick has been published yet for this position
                // (closed market, missing data). Without this the chart "jumps" the moment
                // a late-opening market produces its first tick.
                if (priceAtMoment == null) {
                    priceAtMoment = fallbackPrices.get(pos.getId());
                }

                if (priceAtMoment != null) {
                    portfolioValue = portfolioValue.add(
                            priceAtMoment.multiply(pos.getQuantity()).multiply(multiplier));
                }
            }

            if (portfolioValue.compareTo(BigDecimal.ZERO) > 0) {
                points.add(new PortfolioPerformancePoint(datetime, portfolioValue));
            }
        }

        log.info("Calculated {} intraday points", points.size());

        if (points.isEmpty()) {
            log.info("No intraday points calculated, using buy/current fallback");
            return createBuyCurrentFallback(positions, today, today, "1D");
        }

        LocalDate firstDate = points.get(0).datetime().toLocalDate();
        LocalDate lastDate = points.get(points.size() - 1).datetime().toLocalDate();
        return new PortfolioPerformanceResponse("1D", firstDate, lastDate,
                "INTRADAY", "YAHOO_INTRADAY", points);
    }

    /**
     * Create fallback performance data using buy price and current price
     */
    private PortfolioPerformanceResponse createBuyCurrentFallback(
            List<PortfolioPosition> positions, LocalDate startDate, LocalDate endDate, String range) {

        log.info("Creating buy/current fallback for {} positions", positions.size());

        BigDecimal usdTryRate = currencyService.getUsdTryRate();
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalCurrent = BigDecimal.ZERO;

        for (PortfolioPosition pos : positions) {
            BigDecimal buyPrice = pos.getAvgCost() != null ? pos.getAvgCost() : BigDecimal.ZERO;
            BigDecimal quantity = pos.getQuantity();
            BigDecimal multiplier = toTryMultiplier(pos, usdTryRate);

            totalInvested = totalInvested.add(buyPrice.multiply(quantity).multiply(multiplier));

            // Get current price
            MarketInstrument inst = instrumentRepo.findBySymbol(pos.getSymbol()).orElse(null);
            if (inst != null) {
                BigDecimal currentPrice = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst)
                        .map(q -> q.getLast())
                        .orElse(buyPrice);

                totalCurrent = totalCurrent.add(currentPrice.multiply(quantity).multiply(multiplier));
            }
        }

        List<PortfolioPerformancePoint> points = new ArrayList<>();

        if ("1D".equalsIgnoreCase(range) && startDate.equals(endDate)) {
            // For 1D, use datetime points
            LocalDateTime marketOpen = startDate.atTime(9, 30);
            LocalDateTime now = LocalDateTime.now();

            points.add(new PortfolioPerformancePoint(marketOpen, totalInvested));
            points.add(new PortfolioPerformancePoint(now, totalCurrent));

            log.info("Fallback 1D: {} at {} -> {} at {}",
                    totalInvested, marketOpen, totalCurrent, now);
        } else {
            // For longer ranges, use date points
            points.add(new PortfolioPerformancePoint(startDate, totalInvested));
            points.add(new PortfolioPerformancePoint(endDate, totalCurrent));

            log.info("Fallback {}: {} at {} -> {} at {}",
                    range, totalInvested, startDate, totalCurrent, endDate);
        }

        String granularity = "1D".equalsIgnoreCase(range) ? "INTRADAY" : "DAILY";

        return new PortfolioPerformanceResponse(range, startDate, endDate,
                granularity, "BUY_CURRENT_FALLBACK", points);
    }

    private LocalDate calculateStartDate(LocalDate earliestBuyDate, String range) {
        LocalDate today = LocalDate.now();
        LocalDate calculatedStart;

        switch (range.toUpperCase()) {
            case "1D":
                calculatedStart = today.minusDays(1);
                break;
            case "5D":
                calculatedStart = today.minusDays(5);
                break;
            case "1M":
                calculatedStart = today.minusMonths(1);
                break;
            case "3M":
                calculatedStart = today.minusMonths(3);
                break;
            case "1Y":
                calculatedStart = today.minusYears(1);
                break;
            case "ALL":
                return earliestBuyDate;
            default:
                calculatedStart = today.minusMonths(1);
        }

        // Never go before earliest buy date
        return calculatedStart.isBefore(earliestBuyDate) ? earliestBuyDate : calculatedStart;
    }

    private List<LocalDate> generateDatePoints(LocalDate startDate, LocalDate endDate, String range) {
        // Derive step from the actual span, not the user-selected range. This keeps the chart
        // dense when a recently opened position is viewed under a long range (e.g. 1Y on a
        // 5-day-old position would otherwise yield only 1-2 points).
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        int stepDays;
        if (totalDays <= 31) stepDays = 1;
        else if (totalDays <= 92) stepDays = 3;
        else if (totalDays <= 366) stepDays = 7;
        else stepDays = 14;

        List<LocalDate> points = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            points.add(current);
            current = current.plusDays(stepDays);
        }

        // Always include end date
        if (!points.contains(endDate)) {
            points.add(endDate);
        }

        return points;
    }
}

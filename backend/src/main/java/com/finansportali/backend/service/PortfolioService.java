package com.finansportali.backend.service;

import com.finansportali.backend.domain.MarketInstrument;
import com.finansportali.backend.domain.PortfolioPosition;
import com.finansportali.backend.dto.*;
import com.finansportali.backend.repo.MarketInstrumentRepository;
import com.finansportali.backend.repo.MarketQuoteRepository;
import com.finansportali.backend.repo.PortfolioPositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final PortfolioPositionRepository positionRepo;
    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;
    private final MarketService marketService;
    private final HistoricalPriceService historicalPriceService;
    private final YahooPriceFetcher yahooPriceFetcher;

    public PortfolioService(PortfolioPositionRepository positionRepo,
                            MarketInstrumentRepository instrumentRepo,
                            MarketQuoteRepository quoteRepo,
                            MarketService marketService,
                            HistoricalPriceService historicalPriceService,
                            YahooPriceFetcher yahooPriceFetcher) {
        this.positionRepo = positionRepo;
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.marketService = marketService;
        this.historicalPriceService = historicalPriceService;
        this.yahooPriceFetcher = yahooPriceFetcher;
    }

    public void upsert(String userId, UpsertPositionRequest req) {
        if (req.symbol() == null || req.symbol().isBlank()) throw new IllegalArgumentException("symbol is required");
        if (req.quantity() == null) throw new IllegalArgumentException("quantity is required");

        log.info("Portfolio operation started - Action: UPSERT - Symbol: {} - Quantity: {} - UserId: {}",
                req.symbol(), req.quantity(), userId);

        marketService.seedIfEmpty();

        instrumentRepo.findBySymbol(req.symbol())
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + req.symbol()));

        PortfolioPosition pos = positionRepo.findByUserIdAndSymbol(userId, req.symbol())
                .orElseGet(() -> new PortfolioPosition(userId, req.symbol(), BigDecimal.ZERO, null));

        // İlk kez ekleniyor mu kontrol et
        boolean isNewPosition = pos.getId() == null;

        // Mevcut miktara ekle (replace değil)
        BigDecimal newQuantity = pos.getQuantity().add(req.quantity());
        pos.setQuantity(newQuantity);

        // Ortalama maliyet hesapla (ağırlıklı ortalama)
        if (req.avgCost() != null && req.avgCost().compareTo(BigDecimal.ZERO) > 0) {
            if (pos.getAvgCost() == null || pos.getAvgCost().compareTo(BigDecimal.ZERO) == 0) {
                pos.setAvgCost(req.avgCost());
            } else {
                // Ağırlıklı ortalama: (eskiMiktar * eskiFiyat + yeniMiktar * yeniFiyat) / toplamMiktar
                BigDecimal oldValue = pos.getAvgCost().multiply(pos.getQuantity().subtract(req.quantity()));
                BigDecimal newValue = req.avgCost().multiply(req.quantity());
                BigDecimal weightedAvg = oldValue.add(newValue).divide(newQuantity, 6, java.math.RoundingMode.HALF_UP);
                pos.setAvgCost(weightedAvg);
            }
        }

        // İlk kez ekleniyorsa bugünün tarihini kaydet
        if (isNewPosition && pos.getPurchaseDate() == null) {
            pos.setPurchaseDate(java.time.LocalDate.now());
        }

        positionRepo.save(pos);
        
        log.info("Portfolio operation completed - Action: UPSERT - Symbol: {} - NewQuantity: {} - AvgCost: {} - UserId: {}",
                req.symbol(), pos.getQuantity(), pos.getAvgCost(), userId);
    }

    public List<PortfolioPosition> list(String userId) {
        return positionRepo.findByUserId(userId);
    }

    public PortfolioSummary summary(String userId) {
        marketService.seedIfEmpty();

        List<PositionView> views = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (PortfolioPosition p : positionRepo.findByUserId(userId)) {
            MarketInstrument inst = instrumentRepo.findBySymbol(p.getSymbol()).orElse(null);

            BigDecimal last = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst)
                    .map(q -> q.getLast())
                    .orElse(BigDecimal.ZERO);

            BigDecimal marketValue = last.multiply(p.getQuantity());
            total = total.add(marketValue);

            BigDecimal pnlAbs = null, pnlPct = null;
            if (p.getAvgCost() != null) {
                BigDecimal costValue = p.getAvgCost().multiply(p.getQuantity());
                pnlAbs = marketValue.subtract(costValue);
                pnlPct = costValue.compareTo(BigDecimal.ZERO) != 0
                        ? pnlAbs.divide(costValue, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;
            }

            views.add(new PositionView(p.getSymbol(), p.getQuantity(), p.getAvgCost(), last, marketValue, pnlAbs, pnlPct));
        }

        return new PortfolioSummary(total, views);
    }

    public List<AllocationItem> allocation(String userId) {
        PortfolioSummary s = summary(userId);
        BigDecimal total = s.totalValue();
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return List.of();

        return s.positions().stream()
                .map(p -> {
                    BigDecimal mv = p.marketValue() == null ? BigDecimal.ZERO : p.marketValue();
                    BigDecimal pct = mv.divide(total, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                    return new AllocationItem(p.symbol(), mv, pct);
                })
                .sorted(Comparator.comparing(AllocationItem::marketValue).reversed())
                .toList();
    }

    public List<AllocationByTypeItem> allocationByType(String userId) {
        PortfolioSummary s = summary(userId);
        BigDecimal total = s.totalValue();
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return List.of();

        Map<String, BigDecimal> sums = new HashMap<>();
        for (PositionView p : s.positions()) {
            String type = instrumentRepo.findBySymbol(p.symbol()).map(MarketInstrument::getType).orElse("UNKNOWN");
            BigDecimal mv = p.marketValue() == null ? BigDecimal.ZERO : p.marketValue();
            sums.put(type, sums.getOrDefault(type, BigDecimal.ZERO).add(mv));
        }

        return sums.entrySet().stream()
                .map(e -> {
                    BigDecimal pct = e.getValue().divide(total, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                    return new AllocationByTypeItem(e.getKey(), e.getValue(), pct);
                })
                .sorted((a, b) -> b.marketValue().compareTo(a.marketValue()))
                .toList();
    }

    @Transactional
    public void deleteBySymbol(String userId, String symbol) {
        log.info("Portfolio operation started - Action: DELETE - Symbol: {} - UserId: {}", symbol, userId);
        
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        long deleted = positionRepo.deleteByUserIdAndSymbol(userId, symbol);
        if (deleted == 0) {
            log.warn("Portfolio operation failed - Action: DELETE - Symbol: {} - Reason: Position not found - UserId: {}",
                    symbol, userId);
            throw new IllegalArgumentException("Position not found: " + symbol);
        }
        
        log.info("Portfolio operation completed - Action: DELETE - Symbol: {} - UserId: {}", symbol, userId);
    }

    /**
     * Kısmi satış: belirtilen miktarı pozisyondan düşer.
     * Kalan miktar 0 veya altına inerse pozisyon tamamen silinir.
     * Satıştan elde edilen tutar = quantity × güncel fiyat olarak döner.
     */
    @Transactional
    public BigDecimal sell(String userId, SellPositionRequest req) {
        log.info("Portfolio operation started - Action: SELL - Symbol: {} - Quantity: {} - UserId: {}",
                req.symbol(), req.quantity(), userId);
        
        if (req.symbol() == null || req.symbol().isBlank()) throw new IllegalArgumentException("symbol is required");
        if (req.quantity() == null || req.quantity().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("quantity must be > 0");

        PortfolioPosition pos = positionRepo.findByUserIdAndSymbol(userId, req.symbol())
                .orElseThrow(() -> new IllegalArgumentException("Position not found: " + req.symbol()));

        if (req.quantity().compareTo(pos.getQuantity()) > 0)
            throw new IllegalArgumentException("Satmak istediğiniz miktar (" + req.quantity()
                    + ") mevcut pozisyondan (" + pos.getQuantity() + ") fazla");

        // Güncel fiyatı al
        MarketInstrument inst = instrumentRepo.findBySymbol(req.symbol()).orElse(null);
        BigDecimal currentPrice = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst)
                .map(q -> q.getLast())
                .orElse(pos.getAvgCost() != null ? pos.getAvgCost() : BigDecimal.ZERO);

        BigDecimal proceeds = currentPrice.multiply(req.quantity());

        BigDecimal remaining = pos.getQuantity().subtract(req.quantity());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            positionRepo.deleteByUserIdAndSymbol(userId, req.symbol());
            log.info("Portfolio operation completed - Action: SELL - Symbol: {} - Quantity: {} - Proceeds: {} - RemainingQuantity: 0 (position closed) - UserId: {}",
                    req.symbol(), req.quantity(), proceeds, userId);
        } else {
            pos.setQuantity(remaining);
            positionRepo.save(pos);
            log.info("Portfolio operation completed - Action: SELL - Symbol: {} - Quantity: {} - Proceeds: {} - RemainingQuantity: {} - UserId: {}",
                    req.symbol(), req.quantity(), proceeds, remaining, userId);
        }

        return proceeds;
    }

    @Transactional
    public void clear(String userId) {
        positionRepo.deleteByUserId(userId);
    }

    /**
     * Calculate detailed portfolio summary with real invested amounts and changes.
     */
    public PortfolioSummaryDetail calculatePortfolioSummaryDetail(String userId) {
        log.info("Calculating portfolio summary detail for userId={}", userId);
        
        marketService.seedIfEmpty();
        
        List<PortfolioPosition> positions = positionRepo.findByUserId(userId);
        log.info("Found {} positions for user", positions.size());
        
        if (positions.isEmpty()) {
            return new PortfolioSummaryDetail(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    List.of()
            );
        }
        
        List<PortfolioPositionDetail> positionDetails = new ArrayList<>();
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        
        for (PortfolioPosition pos : positions) {
            try {
                MarketInstrument inst = instrumentRepo.findBySymbol(pos.getSymbol()).orElse(null);
                if (inst == null) {
                    log.warn("Instrument not found for symbol={}", pos.getSymbol());
                    continue;
                }
                
                // Get current price
                BigDecimal currentPrice = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst)
                        .map(q -> q.getLast())
                        .orElse(BigDecimal.ZERO);
                
                if (currentPrice.compareTo(BigDecimal.ZERO) == 0) {
                    log.warn("No current price available for symbol={}", pos.getSymbol());
                    continue;
                }
                
                // Calculate values
                BigDecimal buyPrice = pos.getAvgCost() != null ? pos.getAvgCost() : BigDecimal.ZERO;
                BigDecimal quantity = pos.getQuantity();
                BigDecimal investedAmount = buyPrice.multiply(quantity);
                BigDecimal currentValue = currentPrice.multiply(quantity);
                BigDecimal totalChangeValue = currentValue.subtract(investedAmount);
                BigDecimal totalChangePercent = investedAmount.compareTo(BigDecimal.ZERO) > 0
                        ? totalChangeValue.divide(investedAmount, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;
                
                // Get daily change from quote
                BigDecimal dailyChangePercent = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst)
                        .map(q -> q.getChangePct())
                        .orElse(BigDecimal.ZERO);
                
                BigDecimal dailyChangeValue = currentValue.multiply(dailyChangePercent)
                        .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                
                LocalDate buyDate = pos.getPurchaseDate() != null ? pos.getPurchaseDate() : LocalDate.now();
                
                PortfolioPositionDetail detail = new PortfolioPositionDetail(
                        pos.getSymbol(),
                        inst.getName(),
                        quantity,
                        buyDate,
                        buyPrice,
                        currentPrice,
                        investedAmount,
                        currentValue,
                        totalChangeValue,
                        totalChangePercent,
                        dailyChangePercent,
                        dailyChangeValue
                );
                
                positionDetails.add(detail);
                totalInvested = totalInvested.add(investedAmount);
                totalCurrentValue = totalCurrentValue.add(currentValue);
                
                log.info("Position {}: invested={}, current={}, change={}%", 
                        pos.getSymbol(), investedAmount, currentValue, totalChangePercent);
                
            } catch (Exception e) {
                log.error("Error calculating position detail for symbol={}: {}", 
                        pos.getSymbol(), e.getMessage(), e);
            }
        }
        
        BigDecimal totalChangeValue = totalCurrentValue.subtract(totalInvested);
        BigDecimal totalChangePercent = totalInvested.compareTo(BigDecimal.ZERO) > 0
                ? totalChangeValue.divide(totalInvested, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        
        log.info("Portfolio summary: totalInvested={}, totalCurrentValue={}, totalChangePercent={}%",
                totalInvested, totalCurrentValue, totalChangePercent);
        
        return new PortfolioSummaryDetail(
                totalInvested,
                totalCurrentValue,
                totalChangeValue,
                totalChangePercent,
                positionDetails
        );
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
        
        // Generate date points based on range
        List<LocalDate> datePoints = generateDatePoints(startDate, endDate, range);
        log.info("Generated {} date points", datePoints.size());
        
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
                        BigDecimal positionValue = historicalPrice.get().multiply(pos.getQuantity());
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
                    points.get(points.size()-1).date(), points.get(points.size()-1).value());
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
        
        List<PortfolioPerformancePoint> points = new ArrayList<>();
        
        for (LocalDateTime datetime : allDatetimes) {
            BigDecimal portfolioValue = BigDecimal.ZERO;
            
            for (PortfolioPosition pos : positions) {
                LocalDate buyDate = pos.getPurchaseDate() != null ? pos.getPurchaseDate() : today;
                if (buyDate.isAfter(today)) continue;
                
                List<YahooPriceFetcher.IntradayPoint> posIntradayData = intradayDataMap.get(pos.getSymbol());
                
                if (posIntradayData != null) {
                    // Find closest price at or before this datetime
                    Optional<BigDecimal> price = posIntradayData.stream()
                            .filter(p -> !p.datetime().isAfter(datetime))
                            .max(Comparator.comparing(YahooPriceFetcher.IntradayPoint::datetime))
                            .map(YahooPriceFetcher.IntradayPoint::price);
                    
                    if (price.isPresent()) {
                        portfolioValue = portfolioValue.add(price.get().multiply(pos.getQuantity()));
                    }
                } else {
                    // No intraday data: use current price from DB
                    MarketInstrument inst = instrumentRepo.findBySymbol(pos.getSymbol()).orElse(null);
                    if (inst != null) {
                        BigDecimal currentPrice = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst)
                                .map(q -> q.getLast())
                                .orElse(pos.getAvgCost() != null ? pos.getAvgCost() : BigDecimal.ZERO);
                        portfolioValue = portfolioValue.add(currentPrice.multiply(pos.getQuantity()));
                    }
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
        
        return new PortfolioPerformanceResponse("1D", today, today, 
                "INTRADAY", "YAHOO_INTRADAY", points);
    }
    
    /**
     * Create fallback performance data using buy price and current price
     */
    private PortfolioPerformanceResponse createBuyCurrentFallback(
            List<PortfolioPosition> positions, LocalDate startDate, LocalDate endDate, String range) {
        
        log.info("Creating buy/current fallback for {} positions", positions.size());
        
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalCurrent = BigDecimal.ZERO;
        
        for (PortfolioPosition pos : positions) {
            BigDecimal buyPrice = pos.getAvgCost() != null ? pos.getAvgCost() : BigDecimal.ZERO;
            BigDecimal quantity = pos.getQuantity();
            
            totalInvested = totalInvested.add(buyPrice.multiply(quantity));
            
            // Get current price
            MarketInstrument inst = instrumentRepo.findBySymbol(pos.getSymbol()).orElse(null);
            if (inst != null) {
                BigDecimal currentPrice = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst)
                        .map(q -> q.getLast())
                        .orElse(buyPrice);
                
                totalCurrent = totalCurrent.add(currentPrice.multiply(quantity));
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
        List<LocalDate> points = new ArrayList<>();
        
        // Determine step size based on range
        int stepDays;
        switch (range.toUpperCase()) {
            case "1D":
            case "5D":
                stepDays = 1; // Daily points
                break;
            case "1M":
                stepDays = 1; // Daily points for 1 month
                break;
            case "3M":
                stepDays = 3; // Every 3 days
                break;
            case "1Y":
                stepDays = 7; // Weekly points
                break;
            case "ALL":
                long totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
                if (totalDays <= 30) stepDays = 1;
                else if (totalDays <= 90) stepDays = 3;
                else if (totalDays <= 365) stepDays = 7;
                else stepDays = 14; // Bi-weekly for longer periods
                break;
            default:
                stepDays = 1;
        }
        
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

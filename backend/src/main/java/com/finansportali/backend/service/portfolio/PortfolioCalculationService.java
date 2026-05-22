package com.finansportali.backend.service.portfolio;

import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.PortfolioPosition;
import com.finansportali.backend.dto.response.portfolio.*;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.PortfolioPositionRepository;
import com.finansportali.backend.service.MarketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for portfolio calculations and summaries.
 * Handles portfolio value, allocation, and position detail calculations.
 */
@Service
public class PortfolioCalculationService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioCalculationService.class);

    private final PortfolioPositionRepository positionRepo;
    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;
    private final MarketService marketService;
    private final PortfolioCurrencyService currencyService;

    public PortfolioCalculationService(PortfolioPositionRepository positionRepo,
                                       MarketInstrumentRepository instrumentRepo,
                                       MarketQuoteRepository quoteRepo,
                                       MarketService marketService,
                                       PortfolioCurrencyService currencyService) {
        this.positionRepo = positionRepo;
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.marketService = marketService;
        this.currencyService = currencyService;
    }

    /**
     * Calculate basic portfolio summary with positions and total value.
     */
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

    /**
     * Calculate portfolio allocation by symbol.
     */
    public List<AllocationItem> allocation(String userId) {
        PortfolioSummary s = summary(userId);
        BigDecimal total = s.totalValue();
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        return s.positions().stream()
                .map(p -> {
                    BigDecimal mv = p.marketValue() == null ? BigDecimal.ZERO : p.marketValue();
                    BigDecimal pct = mv.divide(total, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                    return new AllocationItem(p.symbol(), mv, pct);
                })
                .sorted(Comparator.comparing(AllocationItem::marketValue).reversed())
                .toList();
    }

    /**
     * Calculate portfolio allocation by instrument type.
     */
    public List<AllocationByTypeItem> allocationByType(String userId) {
        PortfolioSummary s = summary(userId);
        BigDecimal total = s.totalValue();
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        Map<String, BigDecimal> sums = new HashMap<>();
        for (PositionView p : s.positions()) {
            String type = instrumentRepo.findBySymbol(p.symbol())
                    .map(MarketInstrument::getType)
                    .orElse("UNKNOWN");
            BigDecimal mv = p.marketValue() == null ? BigDecimal.ZERO : p.marketValue();
            sums.put(type, sums.getOrDefault(type, BigDecimal.ZERO).add(mv));
        }

        return sums.entrySet().stream()
                .map(e -> {
                    BigDecimal pct = e.getValue().divide(total, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    return new AllocationByTypeItem(e.getKey(), e.getValue(), pct);
                })
                .sorted((a, b) -> b.marketValue().compareTo(a.marketValue()))
                .toList();
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
                    List.of(),
                    null,
                    List.of()
            );
        }

        List<PortfolioPositionDetail> positionDetails = new ArrayList<>();
        // Surfaced to the UI so the user knows which symbols silently dropped
        // out of their summary (rather than just seeing a smaller total).
        List<String> warnings = new ArrayList<>();
        // Freshest quote timestamp seen — shown as the "last updated" hint.
        java.time.Instant freshestAsOf = null;
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;

        for (PortfolioPosition pos : positions) {
            try {
                MarketInstrument inst = instrumentRepo.findBySymbol(pos.getSymbol()).orElse(null);
                if (inst == null) {
                    log.warn("Instrument not found for symbol={}", pos.getSymbol());
                    warnings.add(pos.getSymbol() + ": enstrüman katalogda bulunamadı, "
                            + "pozisyon özetten dışlandı.");
                    continue;
                }

                var latestQuoteOpt = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst);
                BigDecimal currentPrice = latestQuoteOpt
                        .map(q -> q.getLast())
                        .orElse(BigDecimal.ZERO);

                if (currentPrice.compareTo(BigDecimal.ZERO) == 0) {
                    log.warn("No current price available for symbol={}", pos.getSymbol());
                    warnings.add(pos.getSymbol() + ": güncel fiyat bulunamadı, "
                            + "pozisyon özetten dışlandı.");
                    continue;
                }

                // Track freshness across all included positions.
                if (latestQuoteOpt.isPresent() && latestQuoteOpt.get().getAsOf() != null) {
                    java.time.Instant ts = latestQuoteOpt.get().getAsOf();
                    if (freshestAsOf == null || ts.isAfter(freshestAsOf)) {
                        freshestAsOf = ts;
                    }
                }

                String instrumentType = inst.getInstrumentType() != null ? inst.getInstrumentType().name() : "STOCK";

                // Determine currency for this instrument
                String currency = currencyService.getInstrumentCurrency(pos.getSymbol(), inst.getInstrumentType());
                log.info("Symbol: {}, Type: {}, Currency: {}", pos.getSymbol(), instrumentType, currency);

                // Keep prices in original currency (USD for international stocks, TRY for BIST)
                BigDecimal buyPrice = pos.getAvgCost() != null ? pos.getAvgCost() : BigDecimal.ZERO;
                BigDecimal quantity = pos.getQuantity();
                BigDecimal investedAmount = buyPrice.multiply(quantity);
                BigDecimal currentValue = currentPrice.multiply(quantity);
                BigDecimal totalChangeValue = currentValue.subtract(investedAmount);
                BigDecimal totalChangePercent = investedAmount.compareTo(BigDecimal.ZERO) > 0
                        ? totalChangeValue.divide(investedAmount, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;

                // Daily change pulled from the latest quote we already fetched
                // above. Returning `null` when the quote has no changePct lets
                // the frontend render "—" instead of a misleading "0.00%" that
                // looks like "no movement".
                BigDecimal dailyChangePercent = latestQuoteOpt
                        .map(q -> q.getChangePct())
                        .orElse(null);

                BigDecimal dailyChangeValue = dailyChangePercent == null
                        ? null
                        : currentValue.multiply(dailyChangePercent)
                                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

                java.time.LocalDate buyDate = pos.getPurchaseDate() != null ? 
                        pos.getPurchaseDate() : java.time.LocalDate.now();

                PortfolioPositionDetail detail = new PortfolioPositionDetail(
                        pos.getSymbol(),
                        inst.getName(),
                        instrumentType,
                        currency,
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

                log.info("Position {}: type={}, invested={}, current={}, change={}%",
                        pos.getSymbol(), instrumentType, investedAmount, currentValue, totalChangePercent);

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
                positionDetails,
                freshestAsOf,
                warnings
        );
    }
}

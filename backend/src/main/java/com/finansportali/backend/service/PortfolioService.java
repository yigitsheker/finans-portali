package com.finansportali.backend.service;

import com.finansportali.backend.domain.MarketInstrument;
import com.finansportali.backend.domain.PortfolioPosition;
import com.finansportali.backend.dto.PortfolioSummary;
import com.finansportali.backend.dto.PositionView;
import com.finansportali.backend.dto.UpsertPositionRequest;
import com.finansportali.backend.repo.MarketInstrumentRepository;
import com.finansportali.backend.repo.MarketQuoteRepository;
import com.finansportali.backend.repo.PortfolioPositionRepository;
import org.springframework.stereotype.Service;
import com.finansportali.backend.dto.AllocationItem;
import java.util.Comparator;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioPositionRepository positionRepo;
    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;
    private final MarketService marketService; // seed garanti

    public PortfolioService(PortfolioPositionRepository positionRepo,
                            MarketInstrumentRepository instrumentRepo,
                            MarketQuoteRepository quoteRepo,
                            MarketService marketService) {
        this.positionRepo = positionRepo;
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.marketService = marketService;
    }

    public void upsert(UpsertPositionRequest req) {
        if (req.symbol() == null || req.symbol().isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        if (req.quantity() == null) {
            throw new IllegalArgumentException("quantity is required");
        }

        // market seed (enstrümanlar/quote'lar hazır olsun)
        marketService.seedIfEmpty();

        // symbol valid mi?
        instrumentRepo.findBySymbol(req.symbol())
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + req.symbol()));

        PortfolioPosition pos = positionRepo.findBySymbol(req.symbol())
                .orElseGet(() -> new PortfolioPosition(req.symbol(), BigDecimal.ZERO, null));

        pos.setQuantity(req.quantity());
        pos.setAvgCost(req.avgCost());

        positionRepo.save(pos);
    }

    public List<PortfolioPosition> list() {
        return positionRepo.findAll();
    }

    public PortfolioSummary summary() {
        marketService.seedIfEmpty();

        List<PositionView> views = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (PortfolioPosition p : positionRepo.findAll()) {
            MarketInstrument inst = instrumentRepo.findBySymbol(p.getSymbol())
                    .orElse(null);

            BigDecimal last = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst)
                    .map(q -> q.getLast())
                    .orElse(BigDecimal.ZERO);

            BigDecimal marketValue = last.multiply(p.getQuantity());
            total = total.add(marketValue);

            BigDecimal pnlAbs = null;
            BigDecimal pnlPct = null;

            if (p.getAvgCost() != null) {
                BigDecimal costValue = p.getAvgCost().multiply(p.getQuantity());
                pnlAbs = marketValue.subtract(costValue);

                if (costValue.compareTo(BigDecimal.ZERO) != 0) {
                    pnlPct = pnlAbs.divide(costValue, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                } else {
                    pnlPct = BigDecimal.ZERO;
                }
            }

            views.add(new PositionView(
                    p.getSymbol(),
                    p.getQuantity(),
                    p.getAvgCost(),
                    last,
                    marketValue,
                    pnlAbs,
                    pnlPct
            ));
        }

        return new PortfolioSummary(total, views);
    }
    public List<AllocationItem> allocation() {
        marketService.seedIfEmpty();

        PortfolioSummary s = summary();
        BigDecimal total = s.totalValue();
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return List.of();

        return s.positions().stream()
                .map(p -> {
                    BigDecimal mv = p.marketValue() == null ? BigDecimal.ZERO : p.marketValue();
                    BigDecimal pct = mv.divide(total, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    return new AllocationItem(p.symbol(), mv, pct);
                })
                .sorted(Comparator.comparing(AllocationItem::marketValue).reversed())
                .toList();
    }


}

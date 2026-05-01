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
import com.finansportali.backend.dto.AllocationByTypeItem;
import com.finansportali.backend.dto.SellPositionRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class PortfolioService {

    private final PortfolioPositionRepository positionRepo;
    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;
    private final MarketService marketService;

    public PortfolioService(PortfolioPositionRepository positionRepo,
                            MarketInstrumentRepository instrumentRepo,
                            MarketQuoteRepository quoteRepo,
                            MarketService marketService) {
        this.positionRepo = positionRepo;
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.marketService = marketService;
    }

    public void upsert(String userId, UpsertPositionRequest req) {
        if (req.symbol() == null || req.symbol().isBlank()) throw new IllegalArgumentException("symbol is required");
        if (req.quantity() == null) throw new IllegalArgumentException("quantity is required");

        marketService.seedIfEmpty();

        instrumentRepo.findBySymbol(req.symbol())
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + req.symbol()));

        PortfolioPosition pos = positionRepo.findByUserIdAndSymbol(userId, req.symbol())
                .orElseGet(() -> new PortfolioPosition(userId, req.symbol(), BigDecimal.ZERO, null));

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

        positionRepo.save(pos);
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
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        long deleted = positionRepo.deleteByUserIdAndSymbol(userId, symbol);
        if (deleted == 0) throw new IllegalArgumentException("Position not found: " + symbol);
    }

    /**
     * Kısmi satış: belirtilen miktarı pozisyondan düşer.
     * Kalan miktar 0 veya altına inerse pozisyon tamamen silinir.
     * Satıştan elde edilen tutar = quantity × güncel fiyat olarak döner.
     */
    @Transactional
    public BigDecimal sell(String userId, SellPositionRequest req) {
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
        } else {
            pos.setQuantity(remaining);
            positionRepo.save(pos);
        }

        return proceeds;
    }

    @Transactional
    public void clear(String userId) {
        positionRepo.deleteByUserId(userId);
    }
}

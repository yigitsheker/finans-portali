package com.finansportali.backend.service.portfolio;

import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.PortfolioPosition;
import com.finansportali.backend.entity.PortfolioTransaction;
import com.finansportali.backend.dto.request.SellPositionRequest;
import com.finansportali.backend.dto.request.UpsertPositionRequest;
import com.finansportali.backend.dto.response.PortfolioTransactionView;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.PortfolioPositionRepository;
import com.finansportali.backend.repository.PortfolioTransactionRepository;
import com.finansportali.backend.service.MarketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Service responsible for portfolio position CRUD operations.
 * Handles adding, updating, selling, and deleting positions.
 */
@Service
public class PortfolioPositionService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioPositionService.class);

    private final PortfolioPositionRepository positionRepo;
    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;
    private final MarketService marketService;
    private final PortfolioTransactionRepository txRepo;

    public PortfolioPositionService(PortfolioPositionRepository positionRepo,
                                    MarketInstrumentRepository instrumentRepo,
                                    MarketQuoteRepository quoteRepo,
                                    MarketService marketService,
                                    PortfolioTransactionRepository txRepo) {
        this.positionRepo = positionRepo;
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.marketService = marketService;
        this.txRepo = txRepo;
    }

    /** Append a movement to the observable ledger. Never throws — a ledger
     *  failure must not block the actual buy/sell. */
    private void record(String userId, String symbol, String name, PortfolioTransaction.Type type,
                        BigDecimal qty, BigDecimal price, BigDecimal realizedPnl) {
        try {
            PortfolioTransaction t = new PortfolioTransaction();
            t.setUserId(userId);
            t.setSymbol(symbol);
            t.setName(name);
            t.setType(type);
            t.setQuantity(qty);
            t.setPrice(price);
            t.setAmount(price != null && qty != null ? qty.multiply(price).setScale(2, RoundingMode.HALF_UP) : null);
            t.setRealizedPnl(realizedPnl == null ? null : realizedPnl.setScale(2, RoundingMode.HALF_UP));
            t.setExecutedAt(Instant.now());
            txRepo.save(t);
        } catch (RuntimeException e) {
            log.warn("Failed to record portfolio transaction {} {} for {}: {}", type, symbol, userId, e.getMessage());
        }
    }

    /** Movement history (newest first) for the observability view + closed-P&L chart. */
    @Transactional
    public List<PortfolioTransactionView> transactions(String userId) {
        backfillFromPositionsIfEmpty(userId);
        return txRepo.findByUserIdOrderByExecutedAtDesc(userId).stream()
                .map(PortfolioTransactionView::from).toList();
    }

    /**
     * One-time lazy migration: the ledger was introduced after positions already
     * existed, so a user with holdings but an empty ledger would see no history.
     * Seed a BUY movement per current position (from its avgCost + purchaseDate)
     * so the history isn't empty on first view. Runs once — after the first
     * real buy/sell the ledger is non-empty and this never fires again.
     */
    private void backfillFromPositionsIfEmpty(String userId) {
        if (!txRepo.findByUserIdOrderByExecutedAtDesc(userId).isEmpty()) return;
        for (PortfolioPosition pos : positionRepo.findByUserId(userId)) {
            if (pos.getQuantity() == null || pos.getQuantity().signum() <= 0) continue;
            String name = instrumentRepo.findBySymbol(pos.getSymbol())
                    .map(MarketInstrument::getName).orElse(pos.getSymbol());
            PortfolioTransaction t = new PortfolioTransaction();
            t.setUserId(userId);
            t.setSymbol(pos.getSymbol());
            t.setName(name);
            t.setType(PortfolioTransaction.Type.BUY);
            t.setQuantity(pos.getQuantity());
            t.setPrice(pos.getAvgCost());
            t.setAmount(pos.getAvgCost() != null
                    ? pos.getQuantity().multiply(pos.getAvgCost()).setScale(2, RoundingMode.HALF_UP) : null);
            t.setExecutedAt(pos.getPurchaseDate() != null
                    ? pos.getPurchaseDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : Instant.now());
            txRepo.save(t);
        }
    }

    /**
     * Add or update a portfolio position.
     * If position exists, adds to existing quantity and recalculates weighted average cost.
     */
    public void upsert(String userId, UpsertPositionRequest req) {
        if (req.symbol() == null || req.symbol().isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        if (req.quantity() == null) {
            throw new IllegalArgumentException("quantity is required");
        }

        log.info("Portfolio operation started - Action: UPSERT - Symbol: {} - Quantity: {} - UserId: {}",
                req.symbol(), req.quantity(), userId);

        marketService.seedIfEmpty();

        MarketInstrument inst = instrumentRepo.findBySymbol(req.symbol())
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + req.symbol()));

        PortfolioPosition pos = positionRepo.findByUserIdAndSymbol(userId, req.symbol())
                .orElseGet(() -> new PortfolioPosition(userId, req.symbol(), BigDecimal.ZERO, null));

        boolean isNewPosition = pos.getId() == null;

        // Add to existing quantity (not replace)
        BigDecimal newQuantity = pos.getQuantity().add(req.quantity());
        pos.setQuantity(newQuantity);

        // Calculate weighted average cost
        if (req.avgCost() != null && req.avgCost().compareTo(BigDecimal.ZERO) > 0) {
            if (pos.getAvgCost() == null || pos.getAvgCost().compareTo(BigDecimal.ZERO) == 0) {
                pos.setAvgCost(req.avgCost());
            } else {
                // Weighted average: (oldQty * oldPrice + newQty * newPrice) / totalQty
                BigDecimal oldValue = pos.getAvgCost().multiply(pos.getQuantity().subtract(req.quantity()));
                BigDecimal newValue = req.avgCost().multiply(req.quantity());
                BigDecimal weightedAvg = oldValue.add(newValue)
                        .divide(newQuantity, 6, java.math.RoundingMode.HALF_UP);
                pos.setAvgCost(weightedAvg);
            }
        }

        // Set purchase date for new positions
        if (isNewPosition && pos.getPurchaseDate() == null) {
            pos.setPurchaseDate(LocalDate.now());
        }

        positionRepo.save(pos);

        // Ledger: record this buy movement so history + closed-P&L survive.
        record(userId, req.symbol(), inst.getName(), PortfolioTransaction.Type.BUY,
                req.quantity(), req.avgCost(), null);

        log.info("Portfolio operation completed - Action: UPSERT - Symbol: {} - NewQuantity: {} - AvgCost: {} - UserId: {}",
                req.symbol(), pos.getQuantity(), pos.getAvgCost(), userId);
    }

    /**
     * Get all positions for a user.
     */
    public List<PortfolioPosition> list(String userId) {
        return positionRepo.findByUserId(userId);
    }

    /**
     * Delete a position by symbol.
     */
    @Transactional
    public void deleteBySymbol(String userId, String symbol) {
        log.info("Portfolio operation started - Action: DELETE - Symbol: {} - UserId: {}", symbol, userId);

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }

        long deleted = positionRepo.deleteByUserIdAndSymbol(userId, symbol);
        if (deleted == 0) {
            log.warn("Portfolio operation failed - Action: DELETE - Symbol: {} - Reason: Position not found - UserId: {}",
                    symbol, userId);
            throw new IllegalArgumentException("Position not found: " + symbol);
        }

        log.info("Portfolio operation completed - Action: DELETE - Symbol: {} - UserId: {}", symbol, userId);
    }

    /**
     * Sell a partial or full position.
     * Reduces quantity by the specified amount. If remaining quantity is zero or negative, deletes the position.
     * Returns the proceeds from the sale (quantity × current price).
     */
    @Transactional
    public BigDecimal sell(String userId, SellPositionRequest req) {
        log.info("Portfolio operation started - Action: SELL - Symbol: {} - Quantity: {} - UserId: {}",
                req.symbol(), req.quantity(), userId);

        if (req.symbol() == null || req.symbol().isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        if (req.quantity() == null || req.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }

        PortfolioPosition pos = positionRepo.findByUserIdAndSymbol(userId, req.symbol())
                .orElseThrow(() -> new IllegalArgumentException("Position not found: " + req.symbol()));

        if (req.quantity().compareTo(pos.getQuantity()) > 0) {
            throw new IllegalArgumentException("Satmak istediğiniz miktar (" + req.quantity()
                    + ") mevcut pozisyondan (" + pos.getQuantity() + ") fazla");
        }

        // Get current price
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

        // Ledger: realized P&L = (sellPrice − avgCost) × soldQty. Survives even
        // when the position is fully closed and its row deleted above.
        BigDecimal realized = pos.getAvgCost() != null
                ? currentPrice.subtract(pos.getAvgCost()).multiply(req.quantity())
                : null;
        record(userId, req.symbol(), inst != null ? inst.getName() : req.symbol(),
                PortfolioTransaction.Type.SELL, req.quantity(), currentPrice, realized);

        return proceeds;
    }

    /**
     * Clear all positions for a user.
     */
    @Transactional
    public void clear(String userId) {
        positionRepo.deleteByUserId(userId);
    }
}

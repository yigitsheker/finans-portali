package com.finansportali.backend.service.portfolio;

import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.PortfolioPosition;
import com.finansportali.backend.dto.request.SellPositionRequest;
import com.finansportali.backend.dto.request.UpsertPositionRequest;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.PortfolioPositionRepository;
import com.finansportali.backend.service.MarketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    public PortfolioPositionService(PortfolioPositionRepository positionRepo,
                                    MarketInstrumentRepository instrumentRepo,
                                    MarketQuoteRepository quoteRepo,
                                    MarketService marketService) {
        this.positionRepo = positionRepo;
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.marketService = marketService;
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

        instrumentRepo.findBySymbol(req.symbol())
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

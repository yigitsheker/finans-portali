package com.finansportali.backend.service.market;

import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.MarketQuote;
import com.finansportali.backend.dto.response.market.MarketSummaryItem;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for market price operations.
 * Handles current prices, quotes, and market summaries.
 */
@Service
public class MarketPriceService {

    private static final Logger log = LoggerFactory.getLogger(MarketPriceService.class);

    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;

    public MarketPriceService(MarketInstrumentRepository instrumentRepo,
                              MarketQuoteRepository quoteRepo) {
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
    }

    /**
     * Get current price for a symbol (used by price alerts).
     */
    public BigDecimal getCurrentPrice(String symbol) {
        return quoteRepo.findTop1ByInstrument_SymbolOrderByAsOfDesc(symbol)
                .map(MarketQuote::getLast)
                .orElse(null);
    }

    /**
     * Get all instruments with their current prices (used by price alerts dropdown).
     */
    public List<MarketSummaryItem> getAllInstrumentsWithPrices() {
        List<MarketInstrument> instruments = instrumentRepo.findAll();
        return instruments.stream()
                .map(inst -> {
                    MarketQuote quote = quoteRepo.findTop1ByInstrument_SymbolOrderByAsOfDesc(inst.getSymbol()).orElse(null);
                    return new MarketSummaryItem(
                            inst.getSymbol(),
                            inst.getName(),
                            inst.getInstrumentType() != null ? inst.getInstrumentType().name() : "UNKNOWN",
                            quote != null ? quote.getLast() : BigDecimal.ZERO,
                            quote != null ? quote.getChangeAbs() : BigDecimal.ZERO,
                            quote != null ? quote.getChangePct() : BigDecimal.ZERO,
                            quote != null ? quote.getAsOf() : Instant.now(),
                            inst.isDelayed(),
                            inst.isDelayed() ? "Gecikmeli" : null
                    );
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get latest price for a single symbol (for portfolio modal).
     */
    public Map<String, Object> getLatestPrice(String symbol) {
        MarketInstrument inst = instrumentRepo.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + symbol));

        BigDecimal last = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst)
                .map(MarketQuote::getLast)
                .orElse(BigDecimal.ZERO);

        return Map.of(
                "symbol", inst.getSymbol(),
                "price", last,
                "name", inst.getName(),
                "type", inst.getInstrumentType() != null ? inst.getInstrumentType().name() : "UNKNOWN",
                "delayed", inst.isDelayed()
        );
    }

    /**
     * Get market summary for all instruments with current prices.
     * Cached to improve performance.
     */
    @Cacheable(cacheNames = "marketSummary")
    public List<MarketSummaryItem> getMarketSummary() {
        List<MarketSummaryItem> out = new ArrayList<>();
        for (MarketInstrument inst : instrumentRepo.findAll()) {
            // Skip instruments without type (legacy data)
            if (inst.getInstrumentType() == null) continue;

            quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst).ifPresent(q ->
                    out.add(new MarketSummaryItem(
                            inst.getSymbol(),
                            inst.getName(),
                            inst.getInstrumentType().name(),
                            q.getLast(),
                            q.getChangeAbs(),
                            q.getChangePct(),
                            q.getAsOf(),
                            inst.isDelayed(),
                            inst.isDelayed() ? "Gecikmeli" : null
                    ))
            );
        }
        return out;
    }
}

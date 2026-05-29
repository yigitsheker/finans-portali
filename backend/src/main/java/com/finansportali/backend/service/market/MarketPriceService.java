package com.finansportali.backend.service.market;

import com.finansportali.backend.entity.MarketCandle;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.MarketQuote;
import com.finansportali.backend.dto.response.market.MarketSummaryItem;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final MarketCandleRepository candleRepo;

    public MarketPriceService(MarketInstrumentRepository instrumentRepo,
                              MarketQuoteRepository quoteRepo,
                              MarketCandleRepository candleRepo) {
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.candleRepo = candleRepo;
    }

    /**
     * Daily change derived from the two newest candles. Used as a fallback
     * when the cached quote still holds seed values (changePct=0, prev=null)
     * — the scheduler runs once a day, but candles fill in continuously
     * via on-demand history fetches.
     *
     * Returns {changeAbs, changePct} or null if fewer than two candles exist
     * or yesterday's close is zero.
     */
    private BigDecimal[] deriveChangeFromCandles(MarketInstrument inst, BigDecimal currentLast) {
        List<MarketCandle> recent = candleRepo.findTop2ByInstrumentOrderByDayDesc(inst);
        if (recent.size() < 2) return null;
        BigDecimal prev = recent.get(1).getClose();
        if (prev == null || prev.signum() == 0) return null;
        BigDecimal abs = currentLast.subtract(prev);
        BigDecimal pct = abs.divide(prev, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal[] { abs, pct };
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
                            inst.isDelayed() ? "Gecikmeli" : null,
                            quote != null ? quote.getVolume() : null
                    );
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get latest price for a single symbol (for portfolio modal).
     */
    public Map<String, Object> getLatestPrice(String symbol) {
        MarketInstrument inst = instrumentRepo.findBySymbol(symbol.toUpperCase(Locale.ROOT))
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

            quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst).ifPresent(q -> {
                BigDecimal changeAbs = q.getChangeAbs();
                BigDecimal changePct = q.getChangePct();
                // Yahoo's BIST endpoint returns flat quotes out of market hours
                // (previousClose == last → changePct = 0). The candle history is
                // authoritative for day-over-day moves, so prefer it whenever the
                // cached quote shows no change.
                boolean staleChange = (changePct == null || changePct.signum() == 0);
                if (staleChange && q.getLast() != null) {
                    BigDecimal[] derived = deriveChangeFromCandles(inst, q.getLast());
                    if (derived != null) {
                        changeAbs = derived[0];
                        changePct = derived[1];
                    }
                }
                out.add(new MarketSummaryItem(
                        inst.getSymbol(),
                        inst.getName(),
                        inst.getInstrumentType().name(),
                        q.getLast(),
                        changeAbs,
                        changePct,
                        q.getAsOf(),
                        inst.isDelayed(),
                        inst.isDelayed() ? "Gecikmeli" : null,
                        q.getVolume()
                ));
            });
        }
        return out;
    }
}

package com.finansportali.backend.service.portfolio;

import com.finansportali.backend.entity.ExchangeRate;
import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.repository.ExchangeRateRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service responsible for currency-related operations in portfolio calculations.
 * Handles currency detection and exchange rate retrieval.
 */
@Service
public class PortfolioCurrencyService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioCurrencyService.class);

    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;
    private final ExchangeRateRepository exchangeRateRepo;

    /**
     * Last-resort USDTRY rate when neither MarketQuote nor TCMB ExchangeRate
     * is available. Configurable so we can bump the static fallback as the
     * lira moves rather than shipping a code change. Picked from current spot
     * (≈35 TRY/USD as of 2026Q1); a stale-but-close fallback is dramatically
     * better than the prior `1.0`, which made every USD position appear ×35
     * smaller than reality.
     */
    @Value("${app.currency.usdtry-fallback:35.0}")
    private BigDecimal usdtryFallback;

    public PortfolioCurrencyService(MarketInstrumentRepository instrumentRepo,
                                    MarketQuoteRepository quoteRepo,
                                    ExchangeRateRepository exchangeRateRepo) {
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.exchangeRateRepo = exchangeRateRepo;
    }

    /**
     * Determine the currency of an instrument based on its symbol and type.
     * Returns "TRY" for BIST stocks, "USD" for international stocks and crypto.
     */
    public String getInstrumentCurrency(String symbol, InstrumentType type) {
        // BIST stocks: symbols ending with .IS or type is BIST
        if (symbol.endsWith(".IS") || (type != null && type.name().equals("BIST"))) {
            return "TRY";
        }

        // FX pairs containing TRY
        if (symbol.contains("TRY") && !symbol.equals("USDTRY")) {
            return "TRY";
        }

        // If type is STOCK (not BIST), it's international = USD
        if (type != null && type.name().equals("STOCK")) {
            return "USD";
        }

        // Crypto = USD
        if (type != null && type.name().equals("CRYPTO")) {
            return "USD";
        }

        // Check if it's a Turkish symbol pattern (3-5 uppercase letters, no dots)
        // This is for BIST stocks without .IS suffix
        if (symbol.matches("^[A-Z]{3,5}$") &&
            !symbol.contains("USD") &&
            !symbol.contains("BTC") &&
            !symbol.contains("ETH")) {
            return "TRY";
        }

        // Everything else = USD
        return "USD";
    }

    /**
     * Get the current USD/TRY exchange rate, with a three-tier fallback so
     * USD-denominated positions are always valued sensibly:
     *
     *   1. Live MarketQuote for the USDTRY instrument (Yahoo, refreshed daily)
     *   2. Latest TCMB ExchangeRate row (refreshed every 4h)
     *   3. Configurable static default (app.currency.usdtry-fallback)
     *
     * Returning `1.0` like the old code did silently undervalued USD positions
     * by ~35× whenever Yahoo's USDTRY quote was missing — a real bug we hit on
     * fresh databases before the first price-refresh scheduler tick.
     */
    public BigDecimal getUsdTryRate() {
        // 1) Live market quote
        try {
            MarketInstrument usdTry = instrumentRepo.findBySymbol("USDTRY")
                    .or(() -> instrumentRepo.findBySymbol("USD/TRY"))
                    .orElse(null);
            if (usdTry != null) {
                BigDecimal rate = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(usdTry)
                        .map(q -> q.getLast())
                        .orElse(null);
                if (rate != null && rate.signum() > 0) {
                    log.debug("USDTRY from MarketQuote: {}", rate);
                    return rate;
                }
            }
        } catch (Exception e) {
            log.warn("USDTRY MarketQuote lookup failed: {}", e.getMessage());
        }

        // 2) Latest TCMB exchange-rate row — the TCMB scheduler stores buying +
        // selling rates as separate fields; we average them for a single mid.
        try {
            List<ExchangeRate> usdHistory = exchangeRateRepo
                    .findByCurrencyCodeOrderByRateDateDesc("USD");
            if (!usdHistory.isEmpty()) {
                ExchangeRate latest = usdHistory.get(0);
                BigDecimal buying = latest.getBuyingRate();
                BigDecimal selling = latest.getSellingRate();
                if (buying != null && selling != null && buying.signum() > 0 && selling.signum() > 0) {
                    BigDecimal mid = buying.add(selling).divide(BigDecimal.valueOf(2),
                            6, java.math.RoundingMode.HALF_UP);
                    log.info("USDTRY from TCMB ExchangeRate (date={}): {}", latest.getRateDate(), mid);
                    return mid;
                }
                // Some sources store only one side — take whichever is present
                BigDecimal single = buying != null && buying.signum() > 0 ? buying : selling;
                if (single != null && single.signum() > 0) {
                    log.info("USDTRY from TCMB ExchangeRate (single-side, date={}): {}", latest.getRateDate(), single);
                    return single;
                }
            }
        } catch (Exception e) {
            log.warn("USDTRY ExchangeRate lookup failed: {}", e.getMessage());
        }

        // 3) Static fallback — last resort
        log.error("USDTRY unavailable from MarketQuote and TCMB tables; using static fallback {}",
                usdtryFallback);
        return usdtryFallback;
    }
}

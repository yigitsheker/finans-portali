package com.finansportali.backend.service.portfolio;

import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Service responsible for currency-related operations in portfolio calculations.
 * Handles currency detection and exchange rate retrieval.
 */
@Service
public class PortfolioCurrencyService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioCurrencyService.class);

    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;

    public PortfolioCurrencyService(MarketInstrumentRepository instrumentRepo,
                                    MarketQuoteRepository quoteRepo) {
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
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
     * Get current USD/TRY exchange rate from market data.
     * Returns 1.0 if rate cannot be fetched.
     */
    public BigDecimal getUsdTryRate() {
        try {
            log.info("Attempting to fetch USD/TRY rate...");

            // Try both "USDTRY" and "USD/TRY" symbols
            MarketInstrument usdTry = instrumentRepo.findBySymbol("USDTRY")
                    .or(() -> instrumentRepo.findBySymbol("USD/TRY"))
                    .orElse(null);

            if (usdTry != null) {
                log.info("Found USD/TRY instrument: id={}, symbol={}, type={}",
                        usdTry.getId(), usdTry.getSymbol(), usdTry.getInstrumentType());

                BigDecimal rate = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(usdTry)
                        .map(q -> {
                            log.info("Found quote for USDTRY: last={}, asOf={}", q.getLast(), q.getAsOf());
                            return q.getLast();
                        })
                        .orElseGet(() -> {
                            log.warn("No quote found for USDTRY instrument");
                            return BigDecimal.ONE;
                        });

                log.info("Returning USD/TRY rate: {}", rate);
                return rate;
            } else {
                log.warn("USD/TRY instrument not found in database - checked symbols: USDTRY, USD/TRY");
            }
        } catch (Exception e) {
            log.error("Failed to get USD/TRY rate: {}", e.getMessage(), e);
        }
        log.warn("Returning default USD/TRY rate: 1.0");
        return BigDecimal.ONE;
    }
}

package com.finansportali.backend.service.market;

import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Service responsible for market instrument management.
 * Handles instrument search, retrieval, and symbol normalization.
 */
@Service
public class MarketInstrumentService {

    private static final Logger log = LoggerFactory.getLogger(MarketInstrumentService.class);

    private final MarketInstrumentRepository instrumentRepo;

    public MarketInstrumentService(MarketInstrumentRepository instrumentRepo) {
        this.instrumentRepo = instrumentRepo;
    }

    /**
     * Get all instruments.
     */
    public List<MarketInstrument> getAllInstruments() {
        return instrumentRepo.findAll();
    }

    /**
     * Get instrument by symbol.
     */
    public Optional<MarketInstrument> getInstrumentBySymbol(String symbol) {
        return instrumentRepo.findBySymbol(symbol);
    }

    /**
     * Search instruments by symbol or name.
     * Returns up to 10 results for autocomplete.
     */
    public List<MarketInstrument> searchInstruments(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        String searchTerm = query.trim().toUpperCase(Locale.ROOT);
        return instrumentRepo.findAll().stream()
                .filter(inst -> inst.getSymbol().toUpperCase(Locale.ROOT).contains(searchTerm)
                        || inst.getName().toUpperCase(Locale.ROOT).contains(searchTerm))
                .limit(10)
                .toList();
    }

    /**
     * Central symbol normalization function for Yahoo Finance calls.
     * Converts internal symbols to Yahoo Finance format based on instrument type.
     */
    public String normalizeSymbolForYahoo(String symbol, InstrumentType type) {
        if (symbol == null || type == null) {
            log.warn("Cannot normalize null symbol or type: symbol={}, type={}", symbol, type);
            return symbol;
        }

        String normalized = switch (type) {
            case FX -> {
                // FX pairs: USDTRY → USDTRY=X, EURTRY → EURTRY=X
                yield symbol.endsWith("=X") ? symbol : symbol + "=X";
            }
            case CRYPTO -> {
                // Crypto: BTCUSD → BTC-USD, ETHUSD → ETH-USD
                if (symbol.contains("-")) {
                    yield symbol; // Already in Yahoo format
                }
                // Explicit overrides for tickers whose internal short-code doesn't
                // line up with Yahoo's ticker. The naive "first 3 chars + USD" split
                // produces dead pages (DOG-USD, MAT-USD…) or — worse — collides with
                // an unrelated low-volume token (UNI-USD ≠ Uniswap, that's UNI7083-USD).
                String override = switch (symbol) {
                    case "DOGUSD"  -> "DOGE-USD";   // Dogecoin
                    case "MATUSD"  -> "MATIC-USD";  // Polygon (legacy ticker)
                    case "AVXUSD"  -> "AVAX-USD";   // Avalanche
                    case "LNKUSD"  -> "LINK-USD";   // Chainlink
                    case "UNIUSD"  -> "UNI7083-USD";// Uniswap — bare UNI-USD is a different token
                    case "ALGOUSD" -> "ALGO-USD";   // Algorand
                    case "ATOMUSD" -> "ATOM-USD";   // Cosmos
                    default -> null;
                };
                if (override != null) yield override;
                // Convert BTCUSD to BTC-USD
                if (symbol.length() >= 6) {
                    String base = symbol.substring(0, 3);
                    String quote = symbol.substring(3);
                    yield base + "-" + quote;
                }
                yield symbol;
            }
            case STOCK -> {
                // US stocks: AAPL → AAPL (no transformation)
                yield symbol;
            }
            case BIST -> {
                // BIST stocks: THYAO → THYAO.IS, GARAN → GARAN.IS
                yield symbol.endsWith(".IS") ? symbol : symbol + ".IS";
            }
            case INDEX -> {
                // Indices: XU100 → XU100.IS
                yield symbol.endsWith(".IS") ? symbol : symbol + ".IS";
            }
            case COMMODITY -> {
                // Commodities: map internal symbols to Yahoo futures tickers
                yield switch (symbol) {
                    case "XAUUSD" -> "GC=F";    // Gold
                    case "XAGUSD" -> "SI=F";    // Silver
                    case "WTI"    -> "CL=F";    // Crude Oil (WTI)
                    case "NGAS"   -> "NG=F";    // Natural Gas (Henry Hub)
                    case "XCUUSD" -> "HG=F";    // Copper
                    case "XPTUSD" -> "PL=F";    // Platinum
                    default       -> symbol;
                };
            }
            case VIOP -> {
                // VIOP futures: No Yahoo data, use symbol as-is for manual data
                yield symbol;
            }
            case BOND -> {
                // Bonds: No Yahoo data, use symbol as-is for manual data
                yield symbol;
            }
            case FUND -> {
                // Funds: No Yahoo data, use symbol as-is for manual data
                yield symbol;
            }
        };

        log.debug("Symbol normalization: {} ({}) → {}", symbol, type, normalized);
        return normalized;
    }
}

package com.finansportali.backend.service;

import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.dto.response.market.MarketHistoryPoint;
import com.finansportali.backend.dto.response.market.MarketSummaryItem;
import com.finansportali.backend.service.market.MarketDataSeedService;
import com.finansportali.backend.service.market.MarketHistoryService;
import com.finansportali.backend.service.market.MarketInstrumentService;
import com.finansportali.backend.service.market.MarketPriceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Coordinates market operations while delegating each responsibility to focused services.
 */
@Service
public class MarketService {

    private final MarketInstrumentService instrumentService;
    private final MarketPriceService priceService;
    private final MarketHistoryService historyService;
    private final MarketDataSeedService seedService;

    public MarketService(MarketInstrumentService instrumentService,
                         MarketPriceService priceService,
                         MarketHistoryService historyService,
                         MarketDataSeedService seedService) {
        this.instrumentService = instrumentService;
        this.priceService = priceService;
        this.historyService = historyService;
        this.seedService = seedService;
    }

    /** Latest price for a symbol in its native currency. */
    public BigDecimal getCurrentPrice(String symbol) {
        return priceService.getCurrentPrice(symbol);
    }

    /** Look up an instrument's metadata by its application symbol. */
    public java.util.Optional<MarketInstrument> getInstrumentBySymbol(String symbol) {
        return instrumentService.getInstrumentBySymbol(symbol);
    }

    /** All instruments with their current prices, as summary items. */
    public List<MarketSummaryItem> getAllInstruments() {
        return priceService.getAllInstrumentsWithPrices();
    }

    /** Translate an application symbol into the ticker form expected by Yahoo Finance. */
    public String normalizeSymbolForYahoo(String symbol, InstrumentType type) {
        return instrumentService.normalizeSymbolForYahoo(symbol, type);
    }

    /** Seed the instrument catalog from defaults if the table is empty (idempotent). */
    public void seedIfEmpty() {
        seedService.seedIfEmpty();
    }

    /** All instruments, seeding the catalog first if needed. */
    public List<MarketInstrument> instruments() {
        seedIfEmpty();
        return instrumentService.getAllInstruments();
    }

    /** Search the instrument catalog by name or symbol. */
    public List<MarketInstrument> searchInstruments(String query) {
        seedIfEmpty();
        return instrumentService.searchInstruments(query);
    }

    /** Latest price snapshot for a symbol as a JSON-friendly map. */
    public Map<String, Object> latestPrice(String symbol) {
        seedIfEmpty();
        return priceService.getLatestPrice(symbol);
    }

    /** Market overview list across all instruments. */
    public List<MarketSummaryItem> summary() {
        seedIfEmpty();
        return priceService.getMarketSummary();
    }

    /** Historical price points for a symbol over the given period (e.g. "1mo", "1y"). */
    public List<MarketHistoryPoint> history(String symbol, String period) {
        seedIfEmpty();
        return historyService.getHistory(symbol, period);
    }

    /** OHLC candles for the native chart over the given period. */
    public List<com.finansportali.backend.dto.response.market.MarketCandleDto> candles(String symbol, String period) {
        seedIfEmpty();
        return historyService.getCandles(symbol, period);
    }

    /** Historical FX rate points for a currency code over the given period. */
    public List<MarketHistoryPoint> historyForFx(String currencyCode, String period) {
        return historyService.getFxHistory(currencyCode, period);
    }
}

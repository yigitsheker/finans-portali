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

    public BigDecimal getCurrentPrice(String symbol) {
        return priceService.getCurrentPrice(symbol);
    }

    public java.util.Optional<MarketInstrument> getInstrumentBySymbol(String symbol) {
        return instrumentService.getInstrumentBySymbol(symbol);
    }

    public List<MarketSummaryItem> getAllInstruments() {
        return priceService.getAllInstrumentsWithPrices();
    }

    public String normalizeSymbolForYahoo(String symbol, InstrumentType type) {
        return instrumentService.normalizeSymbolForYahoo(symbol, type);
    }

    public void seedIfEmpty() {
        seedService.seedIfEmpty();
    }

    public List<MarketInstrument> instruments() {
        seedIfEmpty();
        return instrumentService.getAllInstruments();
    }

    public List<MarketInstrument> searchInstruments(String query) {
        seedIfEmpty();
        return instrumentService.searchInstruments(query);
    }

    public Map<String, Object> latestPrice(String symbol) {
        seedIfEmpty();
        return priceService.getLatestPrice(symbol);
    }

    public List<MarketSummaryItem> summary() {
        seedIfEmpty();
        return priceService.getMarketSummary();
    }

    public List<MarketHistoryPoint> history(String symbol, String period) {
        seedIfEmpty();
        return historyService.getHistory(symbol, period);
    }

    public List<MarketHistoryPoint> historyForFx(String currencyCode, String period) {
        return historyService.getFxHistory(currencyCode, period);
    }
}

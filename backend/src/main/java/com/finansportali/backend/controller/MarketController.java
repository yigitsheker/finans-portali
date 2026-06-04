package com.finansportali.backend.controller;

import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.dto.response.market.MarketCandleDto;
import com.finansportali.backend.dto.response.market.MarketHistoryPoint;
import com.finansportali.backend.dto.response.market.MarketSummaryItem;
import com.finansportali.backend.service.MarketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for market data: instrument summaries and listings, spot FX,
 * latest prices, and (single/FX/batch) price history for charts and sparklines.
 */
@RestController
@RequestMapping("/api/v1/market")
public class MarketController {

    private final MarketService service;

    public MarketController(MarketService service) {
        this.service = service;
    }

    /** Cached cross-asset summary (last price + change) for every instrument. */
    @GetMapping("/summary")
    public List<MarketSummaryItem> summary() {
        return service.summary();
    }

    /**
     * Lightweight spot FX rates (USDTRY, EURTRY) for the currency toggle.
     * The frontend polls this every 60s purely to convert prices client-side;
     * it previously hit {@code /summary} and downloaded all ~250 instruments
     * each time. This filters the already-cached summary down to the FX rows,
     * so it's a cache hit (no extra DB work) returning a tiny payload.
     */
    @GetMapping("/spot-rates")
    public List<MarketSummaryItem> spotRates() {
        return service.summary().stream()
                .filter(i -> "FX".equals(i.type()))
                .toList();
    }

    /** All tradable instruments known to the platform. */
    @GetMapping("/instruments")
    public List<MarketInstrument> instruments() {
        return service.instruments();
    }

    /** Search instruments by symbol or name. */
    @GetMapping("/search")
    public List<MarketInstrument> search(@RequestParam String query) {
        return service.searchInstruments(query);
    }

    /** Latest price snapshot for a single symbol. */
    @GetMapping("/price")
    public java.util.Map<String, Object> price(@RequestParam String symbol) {
        return service.latestPrice(symbol);
    }

    /** Price history for one symbol over the given period (default 30D). */
    @GetMapping("/history")
    public List<MarketHistoryPoint> history(
            @RequestParam String symbol,
            @RequestParam(required = false, defaultValue = "30D") String period
    ) {
        return service.history(symbol, period);
    }

    /** OHLC candles for the native chart over the given period (default 30D). */
    @GetMapping("/candles")
    public List<MarketCandleDto> candles(
            @RequestParam String symbol,
            @RequestParam(required = false, defaultValue = "30D") String period
    ) {
        return service.candles(symbol, period);
    }

    /**
     * FX-specific history endpoint. The general {@code /history} call looks
     * the symbol up in {@code market_instruments} and 404's anything not
     * there — exchange-rate rows live in their own {@code exchange_rates}
     * table, so this variant bypasses the lookup and hits Yahoo directly
     * with the {@code <CODE>TRY=X} synthetic ticker (e.g. {@code USDTRY=X}).
     */
    @GetMapping("/history/fx")
    public List<MarketHistoryPoint> historyFx(
            @RequestParam String code,
            @RequestParam(required = false, defaultValue = "30D") String period
    ) {
        return service.historyForFx(code, period);
    }

    /**
     * Batch variant — fetches history for up to 100 symbols in one round trip.
     * Each symbol still goes through the cached {@code history()} call, but
     * collapsing N sparkline lookups from N HTTP requests into one cuts the
     * page-load wall time dramatically.
     *
     * <p>Symbols that throw or return no data are present in the response with
     * an empty list — clients don't have to special-case missing keys.
     *
     * <p>Example: {@code /api/v1/market/history/batch?symbols=THYAO,GARAN,AKBNK&period=1M}
     */
    @GetMapping("/history/batch")
    public java.util.Map<String, List<MarketHistoryPoint>> historyBatch(
            @RequestParam String symbols,
            @RequestParam(required = false, defaultValue = "30D") String period
    ) {
        java.util.Map<String, List<MarketHistoryPoint>> out = new java.util.LinkedHashMap<>();
        String[] parts = symbols.split(",");
        int cap = Math.min(parts.length, 100);
        for (int i = 0; i < cap; i++) {
            String s = parts[i].trim();
            if (s.isEmpty()) continue;
            try { out.put(s, service.history(s, period)); }
            catch (Exception e) { out.put(s, List.of()); }
        }
        return out;
    }
}

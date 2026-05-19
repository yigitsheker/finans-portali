package com.finansportali.backend.controller;

import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.dto.response.market.MarketHistoryPoint;
import com.finansportali.backend.dto.response.market.MarketSummaryItem;
import com.finansportali.backend.service.MarketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market")
public class MarketController {

    private final MarketService service;

    public MarketController(MarketService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public List<MarketSummaryItem> summary() {
        return service.summary();
    }

    @GetMapping("/instruments")
    public List<MarketInstrument> instruments() {
        return service.instruments();
    }

    @GetMapping("/search")
    public List<MarketInstrument> search(@RequestParam String query) {
        return service.searchInstruments(query);
    }

    @GetMapping("/price")
    public java.util.Map<String, Object> price(@RequestParam String symbol) {
        return service.latestPrice(symbol);
    }

    @GetMapping("/history")
    public List<MarketHistoryPoint> history(
            @RequestParam String symbol,
            @RequestParam(required = false, defaultValue = "30D") String period
    ) {
        return service.history(symbol, period);
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

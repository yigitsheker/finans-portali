package com.finansportali.backend.api;

import com.finansportali.backend.dto.MarketHistoryPoint;
import com.finansportali.backend.dto.MarketSummaryItem;
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

    @GetMapping("/history")
    public List<MarketHistoryPoint> history(
            @RequestParam String symbol,
            @RequestParam(required = false, defaultValue = "30D") String period
    ) {
        return service.history(symbol, period);
    }
}

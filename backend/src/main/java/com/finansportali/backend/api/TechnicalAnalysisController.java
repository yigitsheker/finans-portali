package com.finansportali.backend.api;

import com.finansportali.backend.service.TechnicalAnalysisService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/technical-analysis")
public class TechnicalAnalysisController {

    private final TechnicalAnalysisService service;

    public TechnicalAnalysisController(TechnicalAnalysisService service) {
        this.service = service;
    }

    @GetMapping("/{symbol}/moving-averages")
    public Map<String, Object> getMovingAverages(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "20") int period) {
        return service.calculateMovingAverages(symbol, period);
    }

    @GetMapping("/{symbol}/trend")
    public Map<String, Object> getTrendAnalysis(@PathVariable String symbol) {
        return service.analyzeTrend(symbol);
    }

    @GetMapping("/{symbol}/support-resistance")
    public Map<String, Object> getSupportResistance(@PathVariable String symbol) {
        return service.calculateSupportResistance(symbol);
    }

    @GetMapping("/{symbol}/momentum")
    public Map<String, Object> getMomentum(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "14") int period) {
        return service.calculateMomentum(symbol, period);
    }
}
package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.TechnicalAnalysisResponse;
import com.finansportali.backend.service.TechnicalAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * REST endpoints exposing technical analysis (moving averages, trend,
 * support/resistance, momentum) for a given symbol. The primary endpoint
 * returns a consolidated response; the others remain for backward compatibility.
 */
@RestController
@RequestMapping("/api/v1/technical-analysis")
public class TechnicalAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(TechnicalAnalysisController.class);
    
    private final TechnicalAnalysisService service;

    public TechnicalAnalysisController(TechnicalAnalysisService service) {
        this.service = service;
    }

    /**
     * Get comprehensive technical analysis for a symbol
     * GET /api/v1/technical-analysis/{symbol}?from=2026-04-01&to=2026-05-01
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<TechnicalAnalysisResponse> getTechnicalAnalysis(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        
        log.info("[TechnicalAnalysisController] GET /{} from={} to={}", symbol, from, to);
        
        // Default date range if not provided
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = to.minusMonths(3); // Default to 3 months
        }
        
        // Validate date range
        if (from.isAfter(to)) {
            log.warn("[TechnicalAnalysisController] Invalid date range: from={} to={}", from, to);
            return ResponseEntity.badRequest().build();
        }
        
        TechnicalAnalysisResponse response = service.getTechnicalAnalysis(symbol, from, to);
        return ResponseEntity.ok(response);
    }

    // Legacy endpoints for backward compatibility

    /** Legacy: returns the moving average for the symbol over the given period. */
    @GetMapping("/{symbol}/moving-averages")
    public Map<String, Object> getMovingAverages(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "20") int period) {
        return service.calculateMovingAverages(symbol, period);
    }

    /** Legacy: returns trend-direction analysis for the symbol. */
    @GetMapping("/{symbol}/trend")
    public Map<String, Object> getTrendAnalysis(@PathVariable String symbol) {
        return service.analyzeTrend(symbol);
    }

    /** Legacy: returns computed support and resistance levels for the symbol. */
    @GetMapping("/{symbol}/support-resistance")
    public Map<String, Object> getSupportResistance(@PathVariable String symbol) {
        return service.calculateSupportResistance(symbol);
    }

    /** Legacy: returns a momentum indicator for the symbol over the given period. */
    @GetMapping("/{symbol}/momentum")
    public Map<String, Object> getMomentum(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "14") int period) {
        return service.calculateMomentum(symbol, period);
    }
}
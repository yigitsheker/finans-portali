package com.finansportali.backend.controller;

import com.finansportali.backend.entity.ExchangeRate;
import com.finansportali.backend.service.ExchangeRateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for foreign-exchange rates: latest quotes, available data
 * sources, and per-currency history.
 */
@RestController
@RequestMapping("/api/v1/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateService service;

    public ExchangeRateController(ExchangeRateService service) {
        this.service = service;
    }

    /** Latest rate per currency across all sources. */
    @GetMapping
    public List<ExchangeRate> getLatestRates() {
        return service.getLatestRates();
    }

    /** Distinct names of the configured exchange-rate data sources. */
    @GetMapping("/sources")
    public List<String> getSources() {
        return service.getSources();
    }

    /** Rates published by a single named source. */
    @GetMapping("/source/{source}")
    public List<ExchangeRate> getRatesBySource(@PathVariable String source) {
        return service.getRatesBySource(source);
    }

    /** Historical rate series for one currency code (e.g. USD). */
    @GetMapping("/currency/{currencyCode}/history")
    public List<ExchangeRate> getCurrencyHistory(@PathVariable String currencyCode) {
        return service.getCurrencyHistory(currencyCode);
    }
}
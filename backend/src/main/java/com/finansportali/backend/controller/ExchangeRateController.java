package com.finansportali.backend.controller;

import com.finansportali.backend.entity.ExchangeRate;
import com.finansportali.backend.service.ExchangeRateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateService service;

    public ExchangeRateController(ExchangeRateService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExchangeRate> getLatestRates() {
        return service.getLatestRates();
    }

    @GetMapping("/sources")
    public List<String> getSources() {
        return service.getSources();
    }

    @GetMapping("/source/{source}")
    public List<ExchangeRate> getRatesBySource(@PathVariable String source) {
        return service.getRatesBySource(source);
    }

    @GetMapping("/currency/{currencyCode}/history")
    public List<ExchangeRate> getCurrencyHistory(@PathVariable String currencyCode) {
        return service.getCurrencyHistory(currencyCode);
    }
}
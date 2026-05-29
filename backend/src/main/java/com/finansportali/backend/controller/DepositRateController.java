package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.deposit.DepositRateDto;
import com.finansportali.backend.service.DepositRateService;
import com.finansportali.backend.service.client.deposit.TcmbDepositRateFetcher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/deposit-rates")
public class DepositRateController {

    private final DepositRateService service;

    public DepositRateController(DepositRateService service) {
        this.service = service;
    }

    /** All monthly rows for a single currency (TRY/USD/EUR), ascending. */
    @GetMapping
    public List<DepositRateDto> getAll(@RequestParam(defaultValue = "TRY") String currency) {
        return service.getAllForCurrency(currency.toUpperCase(Locale.ROOT)).stream()
                .map(DepositRateDto::from)
                .toList();
    }

    /** Latest available month per currency — convenient for the Bonds page summary card. */
    @GetMapping("/latest")
    public Map<String, DepositRateDto> getLatest() {
        Map<String, DepositRateDto> out = new LinkedHashMap<>();
        for (String currency : TcmbDepositRateFetcher.CURRENCIES) {
            service.getLatest(currency).ifPresent(p -> out.put(currency, DepositRateDto.from(p)));
        }
        return out;
    }

    @GetMapping("/latest/{currency}")
    public ResponseEntity<DepositRateDto> getLatestForCurrency(@PathVariable String currency) {
        return service.getLatest(currency.toUpperCase(Locale.ROOT))
                .map(DepositRateDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/refresh")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> refresh() {
        int n = service.refresh();
        return Map.of("rowsUpserted", n);
    }
}

package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.inflation.InflationCompareDto;
import com.finansportali.backend.dto.response.inflation.InflationPointDto;
import com.finansportali.backend.service.InflationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inflation")
public class InflationController {

    private final InflationService service;

    public InflationController(InflationService service) {
        this.service = service;
    }

    /** Full monthly history (ascending by period). */
    @GetMapping
    public List<InflationPointDto> getAll() {
        return service.getAllAscending().stream()
                .map(InflationPointDto::from)
                .toList();
    }

    /** Most recent published month. */
    @GetMapping("/latest")
    public ResponseEntity<InflationPointDto> getLatest() {
        return service.getLatest()
                .map(InflationPointDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Compute cumulative inflation between two dates and (optionally) the real return
     * given a nominal return for the same window.
     *
     * Used by the historical comparison page to show "after inflation, you actually
     * gained/lost X%".
     */
    @GetMapping("/compare")
    public ResponseEntity<InflationCompareDto> compare(
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to,
            @RequestParam(value = "nominalPct", required = false) BigDecimal nominalPct) {
        return service.compare(from, to, nominalPct)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Admin manual refresh trigger (also runs on startup + daily 09:00). */
    @PostMapping("/refresh")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> refresh() {
        int n = service.refresh();
        return Map.of("rowsUpserted", n);
    }
}

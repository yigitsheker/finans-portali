package com.finansportali.backend.controller;

import com.finansportali.backend.entity.ViopContract;
import com.finansportali.backend.service.ViopService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * REST endpoint exposing VİOP (Turkish derivatives) contracts, optionally
 * filtered by category. Read-only listing for the trading UI.
 */
@RestController
@RequestMapping("/api/v1/viop")
public class ViopController {

    private final ViopService service;

    public ViopController(ViopService service) {
        this.service = service;
    }

    /**
     * Lists VİOP contracts. When {@code category} is blank all contracts are
     * returned; an unrecognised category yields an empty list rather than an error.
     */
    @GetMapping
    public List<ViopContract> list(@RequestParam(required = false) String category) {
        if (category == null || category.isBlank()) {
            return service.findAll();
        }
        try {
            // Locale.ROOT — without it, a Turkish-locale JVM upper-cases
            // "index" → "İNDEX" (dotted-I), which then fails Category.valueOf
            // and silently falls through to an empty list. This bit production
            // routing too, not just the test.
            return service.findByCategory(
                    ViopContract.Category.valueOf(category.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
}

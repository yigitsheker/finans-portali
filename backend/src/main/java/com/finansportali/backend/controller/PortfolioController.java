package com.finansportali.backend.controller;

import com.finansportali.backend.entity.PortfolioPosition;
import com.finansportali.backend.dto.request.UpsertPositionRequest;
import com.finansportali.backend.dto.request.SellPositionRequest;
import com.finansportali.backend.dto.response.portfolio.*;
import com.finansportali.backend.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for the authenticated user's portfolio: positions, summary,
 * allocation breakdowns, buy/sell operations and performance history. The user
 * is always resolved from the JWT so a caller can only touch their own holdings.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    private String userId(Jwt jwt) {
        return jwt.getSubject(); // Keycloak sub claim
    }

    /** Lists all open positions held by the current user. */
    @GetMapping("/positions")
    public List<PortfolioPosition> positions(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt));
    }

    /** Observable buy/sell movement history (newest first) for this user. */
    @GetMapping("/transactions")
    public List<com.finansportali.backend.dto.response.PortfolioTransactionView> transactions(@AuthenticationPrincipal Jwt jwt) {
        return service.transactions(userId(jwt));
    }

    /** Returns aggregate totals (cost, market value, P/L) for the portfolio. */
    @GetMapping("/summary")
    public PortfolioSummary summary(@AuthenticationPrincipal Jwt jwt) {
        return service.summary(userId(jwt));
    }

    /** Returns the portfolio allocation broken down per symbol. */
    @GetMapping("/allocation")
    public List<AllocationItem> allocation(@AuthenticationPrincipal Jwt jwt) {
        return service.allocation(userId(jwt));
    }

    /** Returns the portfolio allocation grouped by asset type. */
    @GetMapping("/allocation/by-type")
    public List<AllocationByTypeItem> allocationByType(@AuthenticationPrincipal Jwt jwt) {
        return service.allocationByType(userId(jwt));
    }

    /** Creates a new position or buys into an existing one (averaging the cost). */
    @PostMapping("/positions")
    public ResponseEntity<Void> upsert(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody UpsertPositionRequest req) {
        service.upsert(userId(jwt), req);
        return ResponseEntity.ok().build();
    }

    /**
     * Sells a quantity of a held symbol and returns the symbol, sold quantity
     * and realised sale proceeds.
     */
    @PostMapping("/positions/sell")
    public ResponseEntity<java.util.Map<String, Object>> sell(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SellPositionRequest req) {
        java.math.BigDecimal proceeds = service.sell(userId(jwt), req);
        return ResponseEntity.ok(java.util.Map.of(
                "symbol", req.symbol(),
                "soldQuantity", req.quantity(),
                "proceeds", proceeds
        ));
    }

    /** Removes the entire position for a single symbol. */
    @DeleteMapping("/positions/{symbol}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable String symbol) {
        service.deleteBySymbol(userId(jwt), symbol);
        return ResponseEntity.noContent().build();
    }

    /** Removes all positions for the current user. */
    @DeleteMapping("/positions")
    public ResponseEntity<Void> clear(@AuthenticationPrincipal Jwt jwt) {
        service.clear(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    /** Returns a detailed summary with per-position breakdown and computed metrics. */
    @GetMapping("/summary-detail")
    public PortfolioSummaryDetail summaryDetail(@AuthenticationPrincipal Jwt jwt) {
        return service.calculatePortfolioSummaryDetail(userId(jwt));
    }

    /**
     * Returns the portfolio's value/return history over the given time range
     * (e.g. {@code ALL}, defaulting to the full history).
     */
    @GetMapping("/performance")
    public PortfolioPerformanceResponse performance(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "ALL") String range) {
        return service.calculatePortfolioPerformance(userId(jwt), range);
    }
}

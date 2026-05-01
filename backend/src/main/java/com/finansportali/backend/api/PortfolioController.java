package com.finansportali.backend.api;

import com.finansportali.backend.domain.PortfolioPosition;
import com.finansportali.backend.dto.*;
import com.finansportali.backend.service.PortfolioService;import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/positions")
    public List<PortfolioPosition> positions(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt));
    }

    @GetMapping("/summary")
    public PortfolioSummary summary(@AuthenticationPrincipal Jwt jwt) {
        return service.summary(userId(jwt));
    }

    @GetMapping("/allocation")
    public List<AllocationItem> allocation(@AuthenticationPrincipal Jwt jwt) {
        return service.allocation(userId(jwt));
    }

    @GetMapping("/allocation/by-type")
    public List<AllocationByTypeItem> allocationByType(@AuthenticationPrincipal Jwt jwt) {
        return service.allocationByType(userId(jwt));
    }

    @PostMapping("/positions")
    public ResponseEntity<Void> upsert(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody UpsertPositionRequest req) {
        service.upsert(userId(jwt), req);
        return ResponseEntity.ok().build();
    }

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

    @DeleteMapping("/positions/{symbol}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable String symbol) {
        service.deleteBySymbol(userId(jwt), symbol);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/positions")
    public ResponseEntity<Void> clear(@AuthenticationPrincipal Jwt jwt) {
        service.clear(userId(jwt));
        return ResponseEntity.noContent().build();
    }
}

package com.finansportali.backend.controller;

import com.finansportali.backend.dto.request.bond.BuyBondRequest;
import com.finansportali.backend.dto.request.bond.SellBondRequest;
import com.finansportali.backend.dto.response.bond.*;
import com.finansportali.backend.service.bond.BondPositionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Bond/bill trading — SIMULATION ONLY. Nominal-based virtual positions for
 * portfolio tracking; no real order is sent. Authenticated; user from JWT.
 */
@RestController
@RequestMapping("/api/v1/portfolio/bonds")
public class BondTradeController {

    private final BondPositionService service;

    public BondTradeController(BondPositionService service) {
        this.service = service;
    }

    private String userId(Jwt jwt) {
        return jwt.getSubject();
    }

    @PostMapping("/buy")
    public ResponseEntity<BondTradeResult> buy(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody BuyBondRequest req) {
        return ResponseEntity.ok(service.buy(userId(jwt), req.identifier(), req.nominal(),
                req.cleanPrice(), req.accruedInterest(), req.dirtyPrice()));
    }

    @PostMapping("/sell")
    public ResponseEntity<BondTradeResult> sell(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody SellBondRequest req) {
        return ResponseEntity.ok(service.sell(userId(jwt), req.identifier(), req.nominal(),
                req.cleanPrice(), req.accruedInterest(), req.dirtyPrice()));
    }

    @GetMapping("/positions")
    public List<BondPositionView> positions(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt));
    }

    @GetMapping("/transactions")
    public List<BondPositionTransactionView> transactions(@AuthenticationPrincipal Jwt jwt) {
        return service.transactions(userId(jwt));
    }

    @GetMapping("/summary")
    public BondPortfolioSummary summary(@AuthenticationPrincipal Jwt jwt) {
        return service.summary(userId(jwt));
    }

    @PostMapping("/preview/buy")
    public ResponseEntity<BondTradePreview> previewBuy(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody BuyBondRequest req) {
        return ResponseEntity.ok(service.previewBuy(req.identifier(), req.nominal(),
                req.cleanPrice(), req.accruedInterest(), req.dirtyPrice()));
    }

    @PostMapping("/preview/sell")
    public ResponseEntity<BondTradePreview> previewSell(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody SellBondRequest req) {
        return ResponseEntity.ok(service.previewSell(userId(jwt), req.identifier(), req.nominal(),
                req.cleanPrice(), req.accruedInterest(), req.dirtyPrice()));
    }
}

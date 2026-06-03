package com.finansportali.backend.controller;

import com.finansportali.backend.dto.request.viop.CloseViopRequest;
import com.finansportali.backend.dto.request.viop.OpenViopRequest;
import com.finansportali.backend.dto.response.viop.*;
import com.finansportali.backend.service.viop.ViopPositionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * VİOP (futures) trading — SIMULATION ONLY. No real order is sent; every action
 * is a virtual position/transaction record for portfolio tracking. All endpoints
 * are authenticated (under /api/v1/portfolio/**); the user is taken from the JWT
 * and can only touch their own positions.
 */
@RestController
@RequestMapping("/api/v1/portfolio/viop")
public class ViopTradeController {

    private final ViopPositionService service;

    public ViopTradeController(ViopPositionService service) {
        this.service = service;
    }

    private String userId(Jwt jwt) {
        return jwt.getSubject();
    }

    @PostMapping("/positions/open")
    public ResponseEntity<ViopTradeResult> open(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody OpenViopRequest req) {
        return ResponseEntity.ok(service.open(userId(jwt), req.contractSymbol(),
                req.direction(), req.quantity(), req.price()));
    }

    @PostMapping("/positions/close")
    public ResponseEntity<ViopTradeResult> close(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody CloseViopRequest req) {
        return ResponseEntity.ok(service.close(userId(jwt), req.contractSymbol(),
                req.quantity(), req.price()));
    }

    @GetMapping("/positions")
    public List<ViopPositionView> positions(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt));
    }

    @GetMapping("/transactions")
    public List<ViopTransactionView> transactions(@AuthenticationPrincipal Jwt jwt) {
        return service.transactions(userId(jwt));
    }

    @GetMapping("/summary")
    public ViopSummary summary(@AuthenticationPrincipal Jwt jwt) {
        return service.summary(userId(jwt));
    }

    @PostMapping("/preview")
    public ResponseEntity<ViopPreviewResult> preview(@AuthenticationPrincipal Jwt jwt,
                                                     @Valid @RequestBody OpenViopRequest req) {
        return ResponseEntity.ok(service.preview(userId(jwt), req.contractSymbol(),
                req.direction(), req.quantity(), req.price()));
    }
}

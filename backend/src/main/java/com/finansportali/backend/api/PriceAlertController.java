package com.finansportali.backend.api;

import com.finansportali.backend.dto.AlertView;
import com.finansportali.backend.dto.CreateAlertRequest;
import com.finansportali.backend.service.PriceAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
public class PriceAlertController {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertController.class);
    private final PriceAlertService alertService;

    public PriceAlertController(PriceAlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    public ResponseEntity<AlertView> createAlert(
            Authentication auth,
            @RequestBody CreateAlertRequest request) {
        
        String userId = auth.getName(); // Keycloak subject
        log.info("[AlertController] Creating alert for user: {} with request: {}", userId, request);
        
        AlertView alert = alertService.createAlert(userId, request);
        log.info("[AlertController] Alert created successfully: {}", alert);
        
        return ResponseEntity.ok(alert);
    }

    @GetMapping
    public ResponseEntity<List<AlertView>> getUserAlerts(
            Authentication auth,
            @RequestParam(defaultValue = "false") boolean includeTriggered) {
        
        String userId = auth.getName();
        log.info("[AlertController] Getting alerts for user: {} includeTriggered: {}", userId, includeTriggered);
        
        List<AlertView> alerts = alertService.getUserAlerts(userId, !includeTriggered);
        log.info("[AlertController] Found {} alerts for user: {}", alerts.size(), userId);
        
        return ResponseEntity.ok(alerts);
    }

    @DeleteMapping("/{alertId}")
    public ResponseEntity<Void> deleteAlert(
            Authentication auth,
            @PathVariable Long alertId) {
        
        String userId = auth.getName();
        alertService.deleteAlert(userId, alertId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{alertId}/toggle")
    public ResponseEntity<Void> toggleAlert(
            Authentication auth,
            @PathVariable Long alertId) {
        
        String userId = auth.getName();
        alertService.toggleAlert(userId, alertId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAlertStats() {
        Map<String, Object> stats = alertService.getAlertStats();
        return ResponseEntity.ok(stats);
    }

    // Test endpoint - sadece development için
    @PostMapping("/test-check")
    public ResponseEntity<String> testAlertCheck() {
        alertService.checkAllAlerts();
        return ResponseEntity.ok("Alert check completed");
    }

    // Manuel tetikleme endpoint'i - test için
    @PostMapping("/{alertId}/trigger-test")
    public ResponseEntity<Map<String, Object>> triggerAlertManually(
            Authentication auth,
            @PathVariable Long alertId) {
        
        String userId = auth.getName();
        log.info("[AlertController] Manually triggering alert {} for user {}", alertId, userId);
        
        try {
            alertService.triggerAlertManually(userId, alertId, auth);
            
            // Email JWT token'dan alınacak
            String message = "Alarm tetiklendi ve email gönderildi!";
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message
            ));
        } catch (Exception e) {
            log.error("[AlertController] Failed to trigger alert manually: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Alarm tetiklenemedi: " + e.getMessage()
            ));
        }
    }
}
package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.alert.AlertView;
import com.finansportali.backend.dto.request.CreateAlertRequest;
import com.finansportali.backend.service.PriceAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for user-defined price alerts: creating, listing, deleting and
 * manually testing alerts. Each endpoint maps service-layer exceptions to the
 * appropriate HTTP status with a localised (Turkish) error message.
 */
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Slf4j
public class PriceAlertController {

    private final PriceAlertService alertService;

    /** Creates a new price alert for the authenticated user. */
    @PostMapping
    public ResponseEntity<?> createAlert(
            @RequestBody CreateAlertRequest request,
            Authentication authentication) {
        try {
            AlertView alert = alertService.createAlert(request, authentication);
            return ResponseEntity.ok(alert);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid alert request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating alert", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Alarm oluşturulamadı"));
        }
    }

    /** Returns all alerts belonging to the authenticated user. */
    @GetMapping
    public ResponseEntity<?> getUserAlerts(Authentication authentication) {
        try {
            List<AlertView> alerts = alertService.getUserAlerts(authentication);
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            log.error("Error fetching alerts", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Alarmlar getirilemedi"));
        }
    }

    /** Deletes an alert owned by the user; returns 403 if it belongs to someone else. */
    @DeleteMapping("/{alertId}")
    public ResponseEntity<?> deleteAlert(
            @PathVariable Long alertId,
            Authentication authentication) {
        try {
            alertService.deleteAlert(alertId, authentication);
            return ResponseEntity.ok(Map.of("message", "Alarm silindi"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Yetkisiz işlem"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting alert", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Alarm silinemedi"));
        }
    }

    /** Manually triggers an alert to verify delivery (sends the notification email). */
    @PostMapping("/{alertId}/test")
    public ResponseEntity<?> testAlert(
            @PathVariable Long alertId,
            Authentication authentication) {
        try {
            AlertView alert = alertService.testAlert(alertId, authentication);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Alarm tetiklendi ve email gönderildi",
                    "alert", alert
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "message", "Yetkisiz işlem"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Alarm bulunamadı"
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error testing alert", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Alarm test edilemedi: " + e.getMessage()
            ));
        }
    }
}

package com.finansportali.backend.service;

import com.finansportali.backend.entity.AlertType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.MarketQuote;
import com.finansportali.backend.entity.PriceAlert;
import com.finansportali.backend.dto.response.alert.AlertView;
import com.finansportali.backend.dto.request.CreateAlertRequest;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.PriceAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceAlertService {

    private final PriceAlertRepository alertRepository;
    private final MarketInstrumentRepository instrumentRepository;
    private final MarketQuoteRepository quoteRepository;
    private final NotificationService notificationService;
    private final KeycloakUserService keycloakUserService;

    @Transactional
    public AlertView createAlert(CreateAlertRequest request, Authentication authentication) {
        String userId = keycloakUserService.getUserId(authentication);
        String userEmail = keycloakUserService.getUserEmail(authentication);

        // Find instrument
        MarketInstrument instrument = instrumentRepository.findBySymbol(request.symbol())
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + request.symbol()));

        // Get current price
        BigDecimal currentPrice = getCurrentPrice(request.symbol());

        PriceAlert alert = PriceAlert.builder()
                .userId(userId)
                .userEmail(userEmail)            // captured for the scheduled trigger
                .instrument(instrument)
                .symbol(request.symbol())
                .alertType(request.alertType())
                .targetPrice(request.targetPrice())
                .creationPrice(currentPrice)
                .note(request.note())
                .active(true)
                .build();

        PriceAlert saved = alertRepository.save(alert);
        log.info("Created price alert {} for user {} on symbol {} (email={})",
                saved.getId(), userId, request.symbol(), userEmail);

        return AlertView.fromAlert(saved, currentPrice);
    }

    @Transactional(readOnly = true)
    public List<AlertView> getUserAlerts(Authentication authentication) {
        String userId = keycloakUserService.getUserId(authentication);
        List<PriceAlert> alerts = alertRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return alerts.stream()
                .map(alert -> {
                    BigDecimal currentPrice = getCurrentPrice(alert.getSymbol());
                    return AlertView.fromAlert(alert, currentPrice);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAlert(Long alertId, Authentication authentication) {
        String userId = keycloakUserService.getUserId(authentication);
        PriceAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        if (!alert.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized");
        }

        alertRepository.delete(alert);
        log.info("Deleted alert {} by user {}", alertId, userId);
    }

    @Transactional
    public AlertView testAlert(Long alertId, Authentication authentication) {
        String userId = keycloakUserService.getUserId(authentication);
        PriceAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));

        if (!alert.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized");
        }

        BigDecimal currentPrice = getCurrentPrice(alert.getSymbol());
        if (currentPrice == null) {
            throw new IllegalStateException("Mevcut fiyat alınamadı");
        }

        // Trigger the alert
        alert.setActive(false);
        alert.setTriggeredAt(Instant.now());
        alert.setTriggeredPrice(currentPrice);
        alertRepository.save(alert);

        // Send notification
        notificationService.sendPriceAlert(alert, currentPrice, authentication);

        log.info("Test triggered alert {} for user {}", alertId, userId);
        return AlertView.fromAlert(alert, currentPrice);
    }

    @Transactional
    public void checkAllAlerts() {
        List<PriceAlert> activeAlerts = alertRepository.findByActiveTrue();
        log.info("Checking {} active alerts", activeAlerts.size());

        int triggeredCount = 0;
        for (PriceAlert alert : activeAlerts) {
            try {
                if (checkAndTriggerAlert(alert)) {
                    triggeredCount++;
                }
            } catch (Exception e) {
                log.error("Error checking alert {}: {}", alert.getId(), e.getMessage(), e);
            }
        }

        if (triggeredCount > 0) {
            log.info("Triggered {} alerts", triggeredCount);
        }
    }

    private boolean checkAndTriggerAlert(PriceAlert alert) {
        BigDecimal currentPrice = getCurrentPrice(alert.getSymbol());
        if (currentPrice == null) {
            log.warn("Could not get current price for {}", alert.getSymbol());
            return false;
        }

        boolean shouldTrigger = false;

        switch (alert.getAlertType()) {
            case PRICE_ABOVE:
                shouldTrigger = currentPrice.compareTo(alert.getTargetPrice()) >= 0;
                break;
            case PRICE_BELOW:
                shouldTrigger = currentPrice.compareTo(alert.getTargetPrice()) <= 0;
                break;
            case PERCENT_GAIN:
                if (alert.getCreationPrice() != null) {
                    BigDecimal change = currentPrice.subtract(alert.getCreationPrice());
                    BigDecimal changePercent = change.divide(alert.getCreationPrice(), 4, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    shouldTrigger = changePercent.compareTo(alert.getTargetPrice()) >= 0;
                }
                break;
            case PERCENT_LOSS:
                if (alert.getCreationPrice() != null) {
                    BigDecimal change = alert.getCreationPrice().subtract(currentPrice);
                    BigDecimal changePercent = change.divide(alert.getCreationPrice(), 4, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    shouldTrigger = changePercent.compareTo(alert.getTargetPrice()) >= 0;
                }
                break;
        }

        if (shouldTrigger) {
            alert.setActive(false);
            alert.setTriggeredAt(Instant.now());
            alert.setTriggeredPrice(currentPrice);
            alertRepository.save(alert);

            log.info("🔔 Alert {} triggered for symbol {} at price {}", alert.getId(), alert.getSymbol(), currentPrice);

            // Fire both email (using email captured at creation) and in-app notification.
            // NotificationService swallows individual errors so neither path blocks the
            // other; the alert row is already saved as triggered.
            try {
                notificationService.sendPriceAlertSystem(alert, currentPrice);
            } catch (Exception e) {
                log.error("Notification dispatch failed for alert {}: {}", alert.getId(), e.getMessage(), e);
            }

            return true;
        }

        return false;
    }

    private BigDecimal getCurrentPrice(String symbol) {
        return quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc(symbol)
                .map(MarketQuote::getLast)
                .orElse(null);
    }
}

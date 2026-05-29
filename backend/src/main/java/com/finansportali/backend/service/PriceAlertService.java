package com.finansportali.backend.service;

import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.MarketQuote;
import com.finansportali.backend.entity.PriceAlert;
import com.finansportali.backend.dto.response.alert.AlertView;
import com.finansportali.backend.dto.request.CreateAlertRequest;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.PriceAlertRepository;
import com.finansportali.backend.service.portfolio.PortfolioCurrencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
    private final PortfolioCurrencyService currencyService;

    @Transactional
    public AlertView createAlert(CreateAlertRequest request, Authentication authentication) {
        String userId = keycloakUserService.getUserId(authentication);
        String userEmail = keycloakUserService.getUserEmail(authentication);
        String language = resolveLanguage();

        MarketInstrument instrument = instrumentRepository.findBySymbol(request.symbol())
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + request.symbol()));

        // Resolve currencies: the alert is denominated in the user's UI currency
        // when provided, otherwise the instrument's native currency.
        String nativeCurrency = currencyService.getInstrumentCurrency(
                request.symbol(), instrument.getInstrumentType());
        String alertCurrency = normalizeCurrency(request.currency(), nativeCurrency);

        // Capture the creation-time price IN THE ALERT CURRENCY so creation,
        // current and triggered prices are all comparable downstream.
        BigDecimal nativeCurrent = getCurrentPriceNative(request.symbol());
        BigDecimal creationInAlertCurrency = convertPrice(nativeCurrent, nativeCurrency, alertCurrency);

        PriceAlert alert = PriceAlert.builder()
                .userId(userId)
                .userEmail(userEmail)
                .language(language)
                .currency(alertCurrency)
                .instrument(instrument)
                .symbol(request.symbol())
                .alertType(request.alertType())
                .targetPrice(request.targetPrice())
                .creationPrice(creationInAlertCurrency)
                .note(request.note())
                .active(true)
                .build();

        PriceAlert saved = alertRepository.save(alert);
        log.info("Created price alert {} for user {} on symbol {} (email={}, lang={}, currency={})",
                saved.getId(), userId, request.symbol(), userEmail, language, alertCurrency);

        return AlertView.fromAlert(saved, creationInAlertCurrency);
    }

    /**
     * Pick the alert language from the current request's Accept-Language header
     * (forwarded by the frontend from the UI's `i18n-lang` localStorage entry).
     * Anything other than "en" is treated as Turkish.
     */
    private String resolveLanguage() {
        Locale locale = LocaleContextHolder.getLocale();
        String lang = locale != null && locale.getLanguage() != null
                ? locale.getLanguage().toLowerCase(Locale.ROOT)
                : "tr";
        return "en".equals(lang) ? "en" : "tr";
    }

    /** Coerce arbitrary input into {"TRY", "USD"}; fall back to the instrument's native currency. */
    private static String normalizeCurrency(String requested, String fallback) {
        if (requested == null || requested.isBlank()) return safe(fallback);
        String upper = requested.trim().toUpperCase(Locale.ROOT);
        return "USD".equals(upper) || "TRY".equals(upper) ? upper : safe(fallback);
    }

    private static String safe(String fallback) {
        return ("USD".equals(fallback)) ? "USD" : "TRY";
    }

    /**
     * Convert between TRY and USD using the live USDTRY rate. Returns the input
     * unchanged when the currencies match or when the rate is unavailable.
     */
    private BigDecimal convertPrice(BigDecimal nativePrice, String from, String to) {
        if (nativePrice == null) return null;
        if (from == null || to == null || from.equals(to)) return nativePrice;
        BigDecimal usdTry = currencyService.getUsdTryRate();
        if (usdTry == null || usdTry.signum() <= 0) return nativePrice;
        if ("TRY".equals(from) && "USD".equals(to)) {
            return nativePrice.divide(usdTry, 6, RoundingMode.HALF_UP);
        }
        if ("USD".equals(from) && "TRY".equals(to)) {
            return nativePrice.multiply(usdTry);
        }
        return nativePrice;
    }

    /** Read the latest quote in the instrument's native currency. */
    private BigDecimal getCurrentPriceNative(String symbol) {
        return quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc(symbol)
                .map(MarketQuote::getLast)
                .orElse(null);
    }

    /**
     * Read the latest quote and convert into the alert's currency, so the
     * comparison and the email both work in the user's chosen unit.
     */
    private BigDecimal getCurrentPriceInAlertCurrency(PriceAlert alert) {
        BigDecimal nativePrice = getCurrentPriceNative(alert.getSymbol());
        if (nativePrice == null) return null;
        String nativeCurrency = currencyService.getInstrumentCurrency(
                alert.getSymbol(),
                alert.getInstrument() != null ? alert.getInstrument().getInstrumentType() : null);
        return convertPrice(nativePrice, nativeCurrency, alert.getCurrency());
    }

    @Transactional(readOnly = true)
    public List<AlertView> getUserAlerts(Authentication authentication) {
        String userId = keycloakUserService.getUserId(authentication);
        List<PriceAlert> alerts = alertRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return alerts.stream()
                .map(alert -> AlertView.fromAlert(alert, getCurrentPriceInAlertCurrency(alert)))
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

        BigDecimal currentPrice = getCurrentPriceInAlertCurrency(alert);
        if (currentPrice == null) {
            throw new IllegalStateException("Mevcut fiyat alınamadı");
        }

        // Trigger the alert
        alert.setActive(false);
        alert.setTriggeredAt(Instant.now());
        alert.setTriggeredPrice(currentPrice);
        alertRepository.save(alert);

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
        // Compare in the alert's chosen currency. This keeps the user's intent
        // intact even when the native quote is in another currency (e.g. a USD
        // alarm on a TRY-quoted stock — common when viewing the site in USD mode).
        BigDecimal currentPrice = getCurrentPriceInAlertCurrency(alert);
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
                if (alert.getCreationPrice() != null
                        && alert.getCreationPrice().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal change = currentPrice.subtract(alert.getCreationPrice());
                    BigDecimal changePercent = change.divide(alert.getCreationPrice(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    shouldTrigger = changePercent.compareTo(alert.getTargetPrice()) >= 0;
                }
                break;
            case PERCENT_LOSS:
                if (alert.getCreationPrice() != null
                        && alert.getCreationPrice().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal change = alert.getCreationPrice().subtract(currentPrice);
                    BigDecimal changePercent = change.divide(alert.getCreationPrice(), 4, RoundingMode.HALF_UP)
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

            log.info("🔔 Alert {} triggered for symbol {} at price {} {}",
                    alert.getId(), alert.getSymbol(), currentPrice, alert.getCurrency());

            try {
                notificationService.sendPriceAlertSystem(alert, currentPrice);
            } catch (Exception e) {
                log.error("Notification dispatch failed for alert {}: {}", alert.getId(), e.getMessage(), e);
            }

            return true;
        }

        return false;
    }
}

package com.finansportali.backend.service;

import com.finansportali.backend.domain.*;
import com.finansportali.backend.dto.AlertView;
import com.finansportali.backend.dto.CreateAlertRequest;
import com.finansportali.backend.repo.MarketInstrumentRepository;
import com.finansportali.backend.repo.MarketQuoteRepository;
import com.finansportali.backend.repo.PriceAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PriceAlertService {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertService.class);
    private static final int MAX_ALERTS_PER_USER = 50;

    private final PriceAlertRepository alertRepo;
    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;
    private final NotificationService notificationService;

    public PriceAlertService(PriceAlertRepository alertRepo,
                           MarketInstrumentRepository instrumentRepo,
                           MarketQuoteRepository quoteRepo,
                           NotificationService notificationService) {
        this.alertRepo = alertRepo;
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.notificationService = notificationService;
    }

    // ── CRUD Operations ──────────────────────────────────────────────────────

    @Transactional
    public AlertView createAlert(String userId, CreateAlertRequest request) {
        // Kullanıcının aktif alarm sayısını kontrol et
        long activeCount = alertRepo.countByUserIdAndActive(userId, true);
        if (activeCount >= MAX_ALERTS_PER_USER) {
            throw new IllegalStateException("Maksimum " + MAX_ALERTS_PER_USER + " aktif alarm oluşturabilirsiniz");
        }

        // Enstrümanı bul
        MarketInstrument instrument = instrumentRepo.findBySymbol(request.symbol())
                .orElseThrow(() -> new IllegalArgumentException("Bilinmeyen sembol: " + request.symbol()));

        // Mevcut fiyatı al
        BigDecimal currentPrice = getCurrentPrice(instrument);

        // Alarm oluştur
        PriceAlert alert = new PriceAlert(
                userId, instrument, request.alertType(), 
                request.targetPrice(), currentPrice, request.note()
        );

        // Alarm mantığını doğrula
        validateAlert(alert, currentPrice);

        alert = alertRepo.save(alert);
        
        log.info("Yeni alarm oluşturuldu: userId={} symbol={} type={} target={} current={}", 
                userId, request.symbol(), request.alertType(), request.targetPrice(), currentPrice);

        return AlertView.fromAlert(alert, currentPrice);
    }

    public List<AlertView> getUserAlerts(String userId, boolean activeOnly) {
        List<PriceAlert> alerts = activeOnly 
            ? alertRepo.findByUserIdAndActiveOrderByCreatedAtDesc(userId, true)
            : alertRepo.findByUserIdOrderByCreatedAtDesc(userId);

        log.info("Found {} alerts for user {} (activeOnly={})", alerts.size(), userId, activeOnly);

        // Mevcut fiyatları toplu olarak al
        Map<String, BigDecimal> currentPrices = getCurrentPrices(
            alerts.stream().map(PriceAlert::getSymbol).distinct().toList()
        );

        return alerts.stream()
                .map(alert -> {
                    try {
                        // Instrument'i eager load et
                        MarketInstrument instrument = alert.getInstrument();
                        String instrumentName = instrument != null ? instrument.getName() : alert.getSymbol();
                        BigDecimal currentPrice = currentPrices.get(alert.getSymbol());
                        
                        log.debug("Processing alert: id={} symbol={} instrumentName={} currentPrice={}", 
                                alert.getId(), alert.getSymbol(), instrumentName, currentPrice);
                        
                        return AlertView.fromAlert(alert, currentPrice);
                    } catch (Exception e) {
                        log.error("Error processing alert {}: {}", alert.getId(), e.getMessage(), e);
                        // Fallback: instrument name olarak symbol kullan
                        return new AlertView(
                                alert.getId(),
                                alert.getSymbol(),
                                alert.getSymbol(), // instrumentName fallback
                                alert.getAlertType(),
                                alert.getTargetPrice(),
                                alert.getCreationPrice(),
                                currentPrices.get(alert.getSymbol()),
                                alert.getActive(),
                                alert.getCreatedAt(),
                                alert.getTriggeredAt(),
                                alert.getTriggeredPrice(),
                                alert.getNote(),
                                alert.getActive() ? "Aktif" : "Tetiklendi",
                                0.0 // progressPercent fallback
                        );
                    }
                })
                .toList();
    }

    @Transactional
    public void deleteAlert(String userId, Long alertId) {
        PriceAlert alert = alertRepo.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alarm bulunamadı: " + alertId));

        if (!alert.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Bu alarm size ait değil");
        }

        alertRepo.delete(alert);
        log.info("Alarm silindi: userId={} alertId={} symbol={}", userId, alertId, alert.getSymbol());
    }

    @Transactional
    public void toggleAlert(String userId, Long alertId) {
        PriceAlert alert = alertRepo.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alarm bulunamadı: " + alertId));

        if (!alert.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Bu alarm size ait değil");
        }

        alert.setActive(!alert.getActive());
        alertRepo.save(alert);
        
        log.info("Alarm durumu değiştirildi: userId={} alertId={} active={}", 
                userId, alertId, alert.getActive());
    }

    // ── Price Monitoring ─────────────────────────────────────────────────────

    @Transactional
    public void checkAllAlerts() {
        List<PriceAlert> activeAlerts = alertRepo.findByActiveOrderBySymbol(true);
        if (activeAlerts.isEmpty()) {
            return;
        }

        log.debug("Checking {} active alerts", activeAlerts.size());

        // Sembollere göre grupla
        Map<String, List<PriceAlert>> alertsBySymbol = activeAlerts.stream()
                .collect(Collectors.groupingBy(PriceAlert::getSymbol));

        // Her sembol için fiyat kontrolü yap
        for (Map.Entry<String, List<PriceAlert>> entry : alertsBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<PriceAlert> symbolAlerts = entry.getValue();
            
            try {
                checkAlertsForSymbol(symbol, symbolAlerts);
            } catch (Exception e) {
                log.error("Error checking alerts for symbol {}: {}", symbol, e.getMessage());
            }
        }
    }

    private void checkAlertsForSymbol(String symbol, List<PriceAlert> alerts) {
        BigDecimal currentPrice = getCurrentPrice(alerts.get(0).getInstrument());
        if (currentPrice == null) {
            log.warn("No current price available for symbol: {}", symbol);
            return;
        }

        for (PriceAlert alert : alerts) {
            if (shouldTriggerAlert(alert, currentPrice)) {
                triggerAlert(alert, currentPrice);
            }
        }
    }

    private boolean shouldTriggerAlert(PriceAlert alert, BigDecimal currentPrice) {
        return switch (alert.getAlertType()) {
            case PRICE_ABOVE -> currentPrice.compareTo(alert.getTargetPrice()) >= 0;
            case PRICE_BELOW -> currentPrice.compareTo(alert.getTargetPrice()) <= 0;
            case PERCENT_GAIN -> {
                if (alert.getCreationPrice() == null) yield false;
                BigDecimal gainPercent = currentPrice.subtract(alert.getCreationPrice())
                        .divide(alert.getCreationPrice(), 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                yield gainPercent.compareTo(alert.getTargetPrice()) >= 0;
            }
            case PERCENT_LOSS -> {
                if (alert.getCreationPrice() == null) yield false;
                BigDecimal lossPercent = alert.getCreationPrice().subtract(currentPrice)
                        .divide(alert.getCreationPrice(), 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                yield lossPercent.compareTo(alert.getTargetPrice()) >= 0;
            }
        };
    }

    @Transactional
    public void triggerAlert(PriceAlert alert, BigDecimal currentPrice) {
        alert.trigger(currentPrice);
        alertRepo.save(alert);

        // Bildirim gönder
        notificationService.sendPriceAlert(alert, currentPrice);

        log.info("Alarm tetiklendi: userId={} symbol={} type={} target={} current={}", 
                alert.getUserId(), alert.getSymbol(), alert.getAlertType(), 
                alert.getTargetPrice(), currentPrice);
    }

    // ── Helper Methods ───────────────────────────────────────────────────────

    private void validateAlert(PriceAlert alert, BigDecimal currentPrice) {
        if (currentPrice == null) {
            throw new IllegalArgumentException("Mevcut fiyat alınamadı: " + alert.getSymbol());
        }

        switch (alert.getAlertType()) {
            case PRICE_ABOVE -> {
                if (alert.getTargetPrice().compareTo(currentPrice) <= 0) {
                    throw new IllegalArgumentException("Hedef fiyat mevcut fiyattan yüksek olmalı");
                }
            }
            case PRICE_BELOW -> {
                if (alert.getTargetPrice().compareTo(currentPrice) >= 0) {
                    throw new IllegalArgumentException("Hedef fiyat mevcut fiyattan düşük olmalı");
                }
            }
            case PERCENT_GAIN, PERCENT_LOSS -> {
                if (alert.getTargetPrice().compareTo(BigDecimal.ZERO) <= 0 || 
                    alert.getTargetPrice().compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new IllegalArgumentException("Yüzde değeri 0-100 arasında olmalı");
                }
            }
        }
    }

    private BigDecimal getCurrentPrice(MarketInstrument instrument) {
        return quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(instrument)
                .map(MarketQuote::getLast)
                .orElse(null);
    }

    private Map<String, BigDecimal> getCurrentPrices(List<String> symbols) {
        return symbols.stream()
                .collect(Collectors.toMap(
                    symbol -> symbol,
                    symbol -> instrumentRepo.findBySymbol(symbol)
                            .map(this::getCurrentPrice)
                            .orElse(BigDecimal.ZERO)
                ));
    }

    // ── Statistics ───────────────────────────────────────────────────────────

    public Map<String, Object> getAlertStats() {
        long totalActive = alertRepo.countActiveAlerts();
        List<PriceAlert> recentTriggered = alertRepo.findRecentlyTriggeredAlerts()
                .stream().limit(10).toList();

        return Map.of(
                "totalActiveAlerts", totalActive,
                "recentTriggeredCount", recentTriggered.size(),
                "maxAlertsPerUser", MAX_ALERTS_PER_USER
        );
    }
}
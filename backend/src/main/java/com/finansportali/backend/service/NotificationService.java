package com.finansportali.backend.service;

import com.finansportali.backend.domain.PriceAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void sendPriceAlert(PriceAlert alert, BigDecimal currentPrice) {
        String message = formatAlertMessage(alert, currentPrice);
        
        // Şimdilik sadece log'a yazdırıyoruz
        // Gelecekte email, push notification, SMS vs. eklenebilir
        log.info("🔔 PRICE ALERT: {}", message);
        
        // TODO: Gerçek bildirim gönderme implementasyonu
        // - Email notification
        // - Push notification (WebSocket)
        // - SMS notification
        // - Slack/Discord webhook
    }

    private String formatAlertMessage(PriceAlert alert, BigDecimal currentPrice) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("tr-TR"));
        formatter.setMaximumFractionDigits(4);
        
        String symbol = alert.getSymbol();
        String instrumentName = alert.getInstrument().getName();
        String current = formatter.format(currentPrice);
        String target = formatter.format(alert.getTargetPrice());
        
        return switch (alert.getAlertType()) {
            case PRICE_ABOVE -> String.format(
                "%s (%s) fiyatı %s seviyesini aştı! Mevcut: %s, Hedef: %s", 
                symbol, instrumentName, target, current, target
            );
            case PRICE_BELOW -> String.format(
                "%s (%s) fiyatı %s seviyesinin altına düştü! Mevcut: %s, Hedef: %s", 
                symbol, instrumentName, target, current, target
            );
            case PERCENT_GAIN -> String.format(
                "%s (%s) %%%s kazanç hedefine ulaştı! Mevcut fiyat: %s", 
                symbol, instrumentName, target, current
            );
            case PERCENT_LOSS -> String.format(
                "%s (%s) %%%s kayıp seviyesine ulaştı! Mevcut fiyat: %s", 
                symbol, instrumentName, target, current
            );
        };
    }

    // Gelecekte eklenebilecek notification türleri:
    
    public void sendEmailNotification(String email, String subject, String message) {
        // Email gönderme implementasyonu
        log.info("Email notification sent to {}: {}", email, subject);
    }
    
    public void sendPushNotification(String userId, String title, String message) {
        // Push notification implementasyonu
        log.info("Push notification sent to {}: {}", userId, title);
    }
    
    public void sendWebSocketNotification(String userId, Object payload) {
        // WebSocket notification implementasyonu
        log.info("WebSocket notification sent to {}: {}", userId, payload);
    }
}
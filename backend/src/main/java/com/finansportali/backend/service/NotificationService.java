package com.finansportali.backend.service;

import com.finansportali.backend.domain.PriceAlert;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final KeycloakUserService keycloakUserService;
    
    @Value("${spring.mail.username:noreply@finansportali.com}")
    private String fromEmail;

    public NotificationService(JavaMailSender mailSender, KeycloakUserService keycloakUserService) {
        this.mailSender = mailSender;
        this.keycloakUserService = keycloakUserService;
    }

    public void sendPriceAlert(PriceAlert alert, BigDecimal currentPrice, Authentication authentication) {
        String message = formatAlertMessage(alert, currentPrice);
        
        // Log the alert
        log.info("🔔 PRICE ALERT: {}", message);
        
        // Send email notification
        try {
            String userEmail = keycloakUserService.getUserEmail(authentication);
            if (userEmail != null && !userEmail.isEmpty()) {
                String username = keycloakUserService.getUsername(authentication);
                sendAlertEmail(userEmail, username, alert, currentPrice, message);
                log.info("✅ Email notification sent to {} for alert {}", userEmail, alert.getId());
            } else {
                log.warn("⚠️ No email found in JWT token for user {}, skipping email notification", alert.getUserId());
            }
        } catch (Exception e) {
            log.error("❌ Failed to send email notification for alert {}: {}", alert.getId(), e.getMessage(), e);
            // Don't throw exception - alert should still be triggered even if email fails
        }
    }

    private void sendAlertEmail(String toEmail, String username, PriceAlert alert, BigDecimal currentPrice, String message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("🔔 Fiyat Alarmı Tetiklendi: " + alert.getSymbol());
            
            String htmlContent = buildAlertEmailHtml(username, alert, currentPrice, message);
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Email gönderilemedi", e);
        }
    }

    private String buildAlertEmailHtml(String username, PriceAlert alert, BigDecimal currentPrice, String message) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("tr-TR"));
        formatter.setMaximumFractionDigits(4);
        
        String symbol = alert.getSymbol();
        String instrumentName = alert.getInstrument().getName();
        String current = formatter.format(currentPrice);
        String target = formatter.format(alert.getTargetPrice());
        String alertTypeLabel = getAlertTypeLabel(alert.getAlertType().name());
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }");
        html.append(".container { max-width: 600px; margin: 0 auto; padding: 20px; }");
        html.append(".header { background: linear-gradient(135deg, #22c55e 0%, #16a34a 100%); color: white; padding: 30px; border-radius: 10px 10px 0 0; text-align: center; }");
        html.append(".header h1 { margin: 0; font-size: 24px; }");
        html.append(".content { background: #f9fafb; padding: 30px; border-radius: 0 0 10px 10px; }");
        html.append(".alert-box { background: white; border-left: 4px solid #22c55e; padding: 20px; margin: 20px 0; border-radius: 5px; }");
        html.append(".info-row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #e5e7eb; }");
        html.append(".info-label { font-weight: bold; color: #6b7280; }");
        html.append(".info-value { color: #111827; }");
        html.append(".note-box { background: #fef3c7; border-left: 4px solid #f59e0b; padding: 15px; margin: 20px 0; border-radius: 5px; }");
        html.append(".footer { text-align: center; color: #6b7280; font-size: 12px; margin-top: 20px; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class='container'>");
        
        // Header
        html.append("<div class='header'>");
        html.append("<h1>🔔 Fiyat Alarmı Tetiklendi</h1>");
        if (username != null && !username.isEmpty()) {
            html.append("<p style='margin: 10px 0 0 0; font-size: 14px; opacity: 0.9;'>Merhaba ").append(username).append(",</p>");
        }
        html.append("</div>");
        
        // Content
        html.append("<div class='content'>");
        html.append("<div class='alert-box'>");
        html.append("<h2 style='margin-top: 0; color: #22c55e;'>").append(symbol).append("</h2>");
        html.append("<p style='color: #6b7280; margin: 5px 0;'>").append(instrumentName).append("</p>");
        html.append("<p style='font-size: 16px; margin: 15px 0;'>").append(message).append("</p>");
        html.append("</div>");
        
        // Details
        html.append("<div style='background: white; padding: 20px; border-radius: 5px;'>");
        html.append("<h3 style='margin-top: 0;'>Alarm Detayları</h3>");
        
        html.append("<div class='info-row'>");
        html.append("<span class='info-label'>Alarm Tipi:</span>");
        html.append("<span class='info-value'>").append(alertTypeLabel).append("</span>");
        html.append("</div>");
        
        html.append("<div class='info-row'>");
        html.append("<span class='info-label'>Hedef Fiyat:</span>");
        html.append("<span class='info-value'>").append(target).append("</span>");
        html.append("</div>");
        
        html.append("<div class='info-row'>");
        html.append("<span class='info-label'>Mevcut Fiyat:</span>");
        html.append("<span class='info-value' style='color: #22c55e; font-weight: bold;'>").append(current).append("</span>");
        html.append("</div>");
        
        if (alert.getCreationPrice() != null) {
            String creationPrice = formatter.format(alert.getCreationPrice());
            html.append("<div class='info-row'>");
            html.append("<span class='info-label'>Oluşturulma Fiyatı:</span>");
            html.append("<span class='info-value'>").append(creationPrice).append("</span>");
            html.append("</div>");
        }
        
        html.append("</div>");
        
        // User note if exists
        if (alert.getNote() != null && !alert.getNote().trim().isEmpty()) {
            html.append("<div class='note-box'>");
            html.append("<strong>📝 Notunuz:</strong><br>");
            html.append(alert.getNote());
            html.append("</div>");
        }
        
        html.append("<p style='margin-top: 20px; color: #6b7280;'>");
        html.append("Bu alarm otomatik olarak devre dışı bırakıldı. Yeni bir alarm oluşturmak için Finans Portalı'nı ziyaret edin.");
        html.append("</p>");
        
        html.append("</div>");
        
        // Footer
        html.append("<div class='footer'>");
        html.append("<p>Bu e-posta Finans Portalı tarafından otomatik olarak gönderilmiştir.</p>");
        html.append("<p>© 2026 Finans Portalı. Tüm hakları saklıdır.</p>");
        html.append("</div>");
        
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
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

    private String getAlertTypeLabel(String alertType) {
        return switch (alertType) {
            case "PRICE_ABOVE" -> "Fiyat Üstü";
            case "PRICE_BELOW" -> "Fiyat Altı";
            case "PERCENT_GAIN" -> "% Kazanç";
            case "PERCENT_LOSS" -> "% Kayıp";
            default -> alertType;
        };
    }

    // Gelecekte eklenebilecek notification türleri:
    
    public void sendPushNotification(String userId, String title, String message) {
        // Push notification implementasyonu
        log.info("Push notification sent to {}: {}", userId, title);
    }
    
    public void sendWebSocketNotification(String userId, Object payload) {
        // WebSocket notification implementasyonu
        log.info("WebSocket notification sent to {}: {}", userId, payload);
    }
}
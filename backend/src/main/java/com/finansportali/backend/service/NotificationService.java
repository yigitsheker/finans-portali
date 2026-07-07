package com.finansportali.backend.service;

import com.finansportali.backend.entity.Notification;
import com.finansportali.backend.entity.PriceAlert;
import com.finansportali.backend.repository.NotificationRepository;
import com.finansportali.backend.util.LogSanitizer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Dispatches user notifications for triggered price alerts. Records an in-app
 * notification row and, when mail is configured, sends a localized HTML email,
 * resolving the recipient address from the JWT or live from Keycloak.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final KeycloakUserService keycloakUserService;
    private final KeycloakAdminService keycloakAdminService;
    private final NotificationRepository notificationRepository;

    // Some relays (e.g. Brevo) require the auth user (spring.mail.username) to be
    // different from the human-readable From: address (the verified sender).
    // app.mail.from lets us set the From: explicitly; falls back to mail.username,
    // and finally to a literal noreply address if neither is provided.
    @Value("${app.mail.from:${spring.mail.username:noreply@finansportali.com}}")
    private String fromEmail;

    // Master switch for outbound email. Defaults to true so configured
    // deployments keep working; set app.mail.enabled=false (e.g. local dev with
    // no SMTP creds) to skip sends cleanly instead of throwing SMTP errors on
    // every alert. As a safety net we ALSO skip when no real sender is
    // configured (fromEmail still on the literal fallback), so an empty
    // spring.mail.username never produces noisy auth failures.
    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    private boolean isMailConfigured() {
        return mailEnabled
                && fromEmail != null
                && !fromEmail.isBlank()
                && !"noreply@finansportali.com".equals(fromEmail);
    }

    public NotificationService(JavaMailSender mailSender,
                               KeycloakUserService keycloakUserService,
                               KeycloakAdminService keycloakAdminService,
                               NotificationRepository notificationRepository) {
        this.mailSender = mailSender;
        this.keycloakUserService = keycloakUserService;
        this.keycloakAdminService = keycloakAdminService;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Authenticated-path: user is online and just triggered the alert manually
     * (e.g. the "Test" button). Pulls email from the JWT.
     */
    public void sendPriceAlert(PriceAlert alert, BigDecimal currentPrice, Authentication authentication) {
        String email = keycloakUserService.getUserEmail(authentication);
        String username = keycloakUserService.getUsername(authentication);
        sendPriceAlertInternal(alert, currentPrice, email, username);
    }

    /** Returns "en" for the alert's English variant, "tr" otherwise. */
    private static String langOf(PriceAlert alert) {
        return "en".equalsIgnoreCase(alert.getLanguage()) ? "en" : "tr";
    }

    /** "$" for USD alerts, "₺" otherwise. */
    private static String currencySymbol(PriceAlert alert) {
        return "USD".equalsIgnoreCase(alert.getCurrency()) ? "$" : "₺";
    }

    /**
     * Scheduled-path: a background job tripped the alert. No Authentication
     * context, so we look the email up live from Keycloak using the stored userId.
     * This way a user can change their email in Keycloak and the very next alert
     * goes to the new address — no need to recreate alerts.
     *
     * The email persisted on the PriceAlert row is used only as a last-resort
     * fallback if Keycloak is unreachable.
     */
    public void sendPriceAlertSystem(PriceAlert alert, BigDecimal currentPrice) {
        String liveEmail = keycloakAdminService.getUserEmailById(alert.getUserId());
        String email = (liveEmail != null && !liveEmail.isBlank()) ? liveEmail : alert.getUserEmail();
        if (liveEmail == null) {
            // Email comes from a JWT claim / user-edited profile; sanitize
            // before logging to defuse CRLF injection (S5145).
            log.warn("Falling back to stored email {} for alert {} (Keycloak lookup failed)",
                    LogSanitizer.sanitize(email), alert.getId());
        } else if (alert.getUserEmail() != null && !liveEmail.equalsIgnoreCase(alert.getUserEmail())) {
            log.info("Email for alert {} updated in Keycloak: stored={} → live={}",
                    alert.getId(),
                    LogSanitizer.sanitize(alert.getUserEmail()),
                    LogSanitizer.sanitize(liveEmail));
        }
        sendPriceAlertInternal(alert, currentPrice, email, null);
    }

    private void sendPriceAlertInternal(PriceAlert alert, BigDecimal currentPrice,
                                        String email, String username) {
        String lang = langOf(alert);
        String message = formatAlertMessage(alert, currentPrice, lang);
        log.info("PRICE ALERT: {}", message);

        // 1) Persist as in-app notification (visible in the bell dropdown).
        try {
            String title = "en".equals(lang)
                    ? alert.getSymbol() + " price alert triggered"
                    : alert.getSymbol() + " fiyat alarmı tetiklendi";
            recordNotification(alert.getUserId(), "PRICE_ALERT",
                    title,
                    message,
                    String.valueOf(alert.getId()));
        } catch (Exception e) {
            log.warn("Failed to persist in-app notification for alert {}: {}", alert.getId(), e.getMessage());
        }

        // 2) Email — best-effort. Failure does not prevent the in-app record above.
        if (!isMailConfigured()) {
            log.info("Email disabled (app.mail.enabled=false or no sender configured); "
                    + "in-app notification recorded for alert {} but no email sent", alert.getId());
            return;
        }
        if (email == null || email.isBlank()) {
            log.warn("No email stored/available for alert {} (user {}); skipping email", alert.getId(), alert.getUserId());
            return;
        }
        try {
            sendAlertEmail(email, username, alert, currentPrice, message, lang);
            log.info("Email notification sent to {} for alert {} (lang={})",
                    LogSanitizer.sanitize(email), alert.getId(), lang);
        } catch (Exception e) {
            log.error("Failed to send email to {} for alert {}: {}",
                    LogSanitizer.sanitize(email), alert.getId(), LogSanitizer.sanitize(e.getMessage()), e);
        }
    }

    /** Persist a generic in-app notification row. Used by both the price-alert
     *  flow and any future system messages. */
    @Transactional
    public Notification recordNotification(String userId, String type, String title,
                                           String message, String referenceId) {
        Notification n = new Notification(userId, type, title, message, referenceId);
        return notificationRepository.save(n);
    }

    private void sendAlertEmail(String toEmail, String username, PriceAlert alert,
                                BigDecimal currentPrice, String message, String lang) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            // SPRING_MAIL_USERNAME can be intentionally empty for local Mailpit (no auth);
            // fall back to a literal sender so MimeMessageHelper.setFrom("") doesn't blow up.
            String from = (fromEmail != null && !fromEmail.isBlank())
                    ? fromEmail
                    : "noreply@finansportali.com";
            helper.setFrom(from);
            helper.setTo(toEmail);
            String subject = "en".equals(lang)
                    ? "Price Alert Triggered: " + alert.getSymbol()
                    : "Fiyat Alarmı Tetiklendi: " + alert.getSymbol();
            helper.setSubject(subject);

            String htmlContent = buildAlertEmailHtml(username, alert, currentPrice, message, lang);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException(
                    "en".equals(lang) ? "Email could not be sent" : "Email gönderilemedi", e);
        }
    }

    private String buildAlertEmailHtml(String username, PriceAlert alert, BigDecimal currentPrice,
                                       String message, String lang) {
        boolean en = "en".equals(lang);
        // tr-TR uses dot grouping ("1.234,5678"); en-US uses comma grouping ("1,234.5678")
        NumberFormat formatter = NumberFormat.getNumberInstance(
                Locale.forLanguageTag(en ? "en-US" : "tr-TR"));
        formatter.setMaximumFractionDigits(4);

        // PERCENT alerts: targetPrice is a percentage, so we render it as "%X"
        // instead of "₺X". The current price snapshot stays in the alert's
        // currency for context.
        boolean percentAlert = alert.getAlertType() != null
                && alert.getAlertType().name().startsWith("PERCENT_");
        String priceSym = currencySymbol(alert);

        String symbol = alert.getSymbol();
        String instrumentName = alert.getInstrument().getName();
        String current = priceSym + formatter.format(currentPrice);
        String target = percentAlert
                ? "%" + formatter.format(alert.getTargetPrice())
                : priceSym + formatter.format(alert.getTargetPrice());
        String alertTypeLabel = getAlertTypeLabel(alert.getAlertType().name(), lang);

        // Localized labels
        String headerTitle    = en ? "Price Alert Triggered"             : "Fiyat Alarmı Tetiklendi";
        String greeting       = en ? "Hi "                                  : "Merhaba ";
        String detailsHeader  = en ? "Alert Details"                        : "Alarm Detayları";
        String labelType      = en ? "Alert Type:"                          : "Alarm Tipi:";
        String labelTarget    = en ? "Target Price:"                        : "Hedef Fiyat:";
        String labelCurrent   = en ? "Current Price:"                       : "Mevcut Fiyat:";
        String labelCreation  = en ? "Creation Price:"                      : "Oluşturulma Fiyatı:";
        String labelNote      = en ? "Your note:"                        : "Notunuz:";
        String autoDisabled   = en
                ? "This alert was automatically deactivated. Visit Finans Portalı to create a new one."
                : "Bu alarm otomatik olarak devre dışı bırakıldı. Yeni bir alarm oluşturmak için Finans Portalı'nı ziyaret edin.";
        String footerLine1    = en
                ? "This email was sent automatically by Finans Portalı."
                : "Bu e-posta Finans Portalı tarafından otomatik olarak gönderilmiştir.";
        String footerLine2    = en
                ? "© 2026 Finans Portalı. All rights reserved."
                : "© 2026 Finans Portalı. Tüm hakları saklıdır.";
        
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
        html.append("<h1>").append(headerTitle).append("</h1>");
        if (username != null && !username.isEmpty()) {
            html.append("<p style='margin: 10px 0 0 0; font-size: 14px; opacity: 0.9;'>")
                .append(greeting).append(username).append(",</p>");
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
        html.append("<h3 style='margin-top: 0;'>").append(detailsHeader).append("</h3>");

        html.append("<div class='info-row'>");
        html.append("<span class='info-label'>").append(labelType).append("</span>");
        html.append("<span class='info-value'>").append(alertTypeLabel).append("</span>");
        html.append("</div>");

        html.append("<div class='info-row'>");
        html.append("<span class='info-label'>").append(labelTarget).append("</span>");
        html.append("<span class='info-value'>").append(target).append("</span>");
        html.append("</div>");

        html.append("<div class='info-row'>");
        html.append("<span class='info-label'>").append(labelCurrent).append("</span>");
        html.append("<span class='info-value' style='color: #22c55e; font-weight: bold;'>").append(current).append("</span>");
        html.append("</div>");

        if (alert.getCreationPrice() != null) {
            String creationPrice = priceSym + formatter.format(alert.getCreationPrice());
            html.append("<div class='info-row'>");
            html.append("<span class='info-label'>").append(labelCreation).append("</span>");
            html.append("<span class='info-value'>").append(creationPrice).append("</span>");
            html.append("</div>");
        }

        html.append("</div>");

        // User note if exists
        if (alert.getNote() != null && !alert.getNote().trim().isEmpty()) {
            html.append("<div class='note-box'>");
            html.append("<strong>").append(labelNote).append("</strong><br>");
            html.append(alert.getNote());
            html.append("</div>");
        }

        html.append("<p style='margin-top: 20px; color: #6b7280;'>");
        html.append(autoDisabled);
        html.append("</p>");

        html.append("</div>");

        // Footer
        html.append("<div class='footer'>");
        html.append("<p>").append(footerLine1).append("</p>");
        html.append("<p>").append(footerLine2).append("</p>");
        html.append("</div>");
        
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
    }

    private String formatAlertMessage(PriceAlert alert, BigDecimal currentPrice, String lang) {
        boolean en = "en".equals(lang);
        NumberFormat formatter = NumberFormat.getNumberInstance(
                Locale.forLanguageTag(en ? "en-US" : "tr-TR"));
        formatter.setMaximumFractionDigits(4);

        String priceSym = currencySymbol(alert);
        String symbol = alert.getSymbol();
        String instrumentName = alert.getInstrument().getName();
        // PRICE_*: render as currency. PERCENT_*: targetPrice is a %, currentPrice still a money value.
        String current = priceSym + formatter.format(currentPrice);
        String priceTarget = priceSym + formatter.format(alert.getTargetPrice());
        String pctTarget = formatter.format(alert.getTargetPrice());

        if (en) {
            return switch (alert.getAlertType()) {
                case PRICE_ABOVE -> String.format(
                    "%s (%s) price exceeded the level of %s! Current: %s, Target: %s",
                    symbol, instrumentName, priceTarget, current, priceTarget);
                case PRICE_BELOW -> String.format(
                    "%s (%s) price dropped below %s! Current: %s, Target: %s",
                    symbol, instrumentName, priceTarget, current, priceTarget);
                case PERCENT_GAIN -> String.format(
                    "%s (%s) reached the %%%s gain target! Current price: %s",
                    symbol, instrumentName, pctTarget, current);
                case PERCENT_LOSS -> String.format(
                    "%s (%s) reached the %%%s loss level! Current price: %s",
                    symbol, instrumentName, pctTarget, current);
            };
        }
        return switch (alert.getAlertType()) {
            case PRICE_ABOVE -> String.format(
                "%s (%s) fiyatı %s seviyesini aştı! Mevcut: %s, Hedef: %s",
                symbol, instrumentName, priceTarget, current, priceTarget);
            case PRICE_BELOW -> String.format(
                "%s (%s) fiyatı %s seviyesinin altına düştü! Mevcut: %s, Hedef: %s",
                symbol, instrumentName, priceTarget, current, priceTarget);
            case PERCENT_GAIN -> String.format(
                "%s (%s) %%%s kazanç hedefine ulaştı! Mevcut fiyat: %s",
                symbol, instrumentName, pctTarget, current);
            case PERCENT_LOSS -> String.format(
                "%s (%s) %%%s kayıp seviyesine ulaştı! Mevcut fiyat: %s",
                symbol, instrumentName, pctTarget, current);
        };
    }

    private String getAlertTypeLabel(String alertType, String lang) {
        if ("en".equals(lang)) {
            return switch (alertType) {
                case "PRICE_ABOVE" -> "Price Above";
                case "PRICE_BELOW" -> "Price Below";
                case "PERCENT_GAIN" -> "% Gain";
                case "PERCENT_LOSS" -> "% Loss";
                default -> alertType;
            };
        }
        return switch (alertType) {
            case "PRICE_ABOVE" -> "Fiyat Üstü";
            case "PRICE_BELOW" -> "Fiyat Altı";
            case "PERCENT_GAIN" -> "% Kazanç";
            case "PERCENT_LOSS" -> "% Kayıp";
            default -> alertType;
        };
    }

    // Gelecekte eklenebilecek notification türleri:
    
    /** Placeholder for future push-notification delivery; currently logs only. */
    public void sendPushNotification(String userId, String title, String message) {
        // Push notification implementasyonu
        log.info("Push notification sent to {}: {}", userId, title);
    }

    /** Placeholder for future WebSocket delivery; currently logs only. */
    public void sendWebSocketNotification(String userId, Object payload) {
        // WebSocket notification implementasyonu
        log.info("WebSocket notification sent to {}: {}", userId, payload);
    }
}
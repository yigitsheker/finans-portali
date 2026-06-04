package com.finansportali.backend.service;

import com.finansportali.backend.entity.AlertType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.Notification;
import com.finansportali.backend.entity.PriceAlert;
import com.finansportali.backend.repository.NotificationRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private KeycloakUserService keycloakUserService;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private Authentication auth;

    @InjectMocks private NotificationService service;

    @BeforeEach
    void setup() {
        // isMailConfigured() needs the master switch ON and a REAL sender (not
        // blank, not the literal noreply fallback) before it will attempt an
        // SMTP send. Plain Mockito doesn't inject @Value fields, so set both
        // here; individual tests override fromEmail to exercise the skip path.
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "fromEmail", "alerts@finansportali.com");
        // Default: mail sender returns a real MimeMessage so MimeMessageHelper can fill it in.
        MimeMessage real = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(real);
    }

    private static MarketInstrument inst(String symbol, String name) {
        MarketInstrument i = new MarketInstrument();
        i.setSymbol(symbol);
        i.setName(name);
        return i;
    }

    private static PriceAlert alert(AlertType type, BigDecimal target, BigDecimal creation, String note) {
        return alert(type, target, creation, note, "tr");
    }

    /**
     * MimeMessageHelper with multipart=true wraps the body in a MimeMultipart;
     * descend into the first body part to recover the rendered HTML string.
     */
    private static String extractHtmlBody(jakarta.mail.internet.MimeMessage msg) throws Exception {
        Object content = msg.getContent();
        if (content instanceof String s) return s;
        if (content instanceof jakarta.mail.Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                Object inner = mp.getBodyPart(i).getContent();
                if (inner instanceof String s) return s;
                if (inner instanceof jakarta.mail.Multipart inner_mp) {
                    for (int j = 0; j < inner_mp.getCount(); j++) {
                        Object leaf = inner_mp.getBodyPart(j).getContent();
                        if (leaf instanceof String s2) return s2;
                    }
                }
            }
        }
        return String.valueOf(content);
    }

    private static PriceAlert alert(AlertType type, BigDecimal target, BigDecimal creation,
                                    String note, String language) {
        return alert(type, target, creation, note, language, "TRY");
    }

    private static PriceAlert alert(AlertType type, BigDecimal target, BigDecimal creation,
                                    String note, String language, String currency) {
        return PriceAlert.builder()
                .id(1L)
                .userId("u-1")
                .userEmail("stored@x")
                .language(language)
                .currency(currency)
                .instrument(inst("THYAO", "Türk Hava Yolları"))
                .symbol("THYAO")
                .alertType(type)
                .targetPrice(target)
                .creationPrice(creation)
                .note(note)
                .active(true)
                .build();
    }

    // ---------- recordNotification ----------

    @Test
    void recordNotification_persists_via_repository() {
        Notification n = new Notification("u", "T", "Title", "Body", "ref");
        when(notificationRepository.save(any(Notification.class))).thenReturn(n);
        Notification result = service.recordNotification("u", "T", "Title", "Body", "ref");
        assertThat(result).isEqualTo(n);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo("u");
        assertThat(cap.getValue().getType()).isEqualTo("T");
    }

    // ---------- sendPriceAlert (authenticated path) ----------

    @Test
    void sendPriceAlert_persists_inapp_notification_and_sends_email() {
        when(keycloakUserService.getUserEmail(auth)).thenReturn("alice@x");
        when(keycloakUserService.getUsername(auth)).thenReturn("alice");

        PriceAlert a = alert(AlertType.PRICE_ABOVE, new BigDecimal("310"),
                new BigDecimal("290"), "test note");

        service.sendPriceAlert(a, new BigDecimal("315"), auth);

        verify(notificationRepository).save(any(Notification.class));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPriceAlert_skips_email_when_user_has_no_email() {
        when(keycloakUserService.getUserEmail(auth)).thenReturn(null);
        when(keycloakUserService.getUsername(auth)).thenReturn("alice");

        service.sendPriceAlert(
                alert(AlertType.PRICE_ABOVE, new BigDecimal("310"), null, ""),
                new BigDecimal("315"),
                auth);

        verify(notificationRepository).save(any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendPriceAlert_swallows_email_send_failure() {
        when(keycloakUserService.getUserEmail(auth)).thenReturn("alice@x");
        when(keycloakUserService.getUsername(auth)).thenReturn("alice");
        doThrow(new MailSendException("smtp dead")).when(mailSender).send(any(MimeMessage.class));

        service.sendPriceAlert(
                alert(AlertType.PRICE_ABOVE, new BigDecimal("310"), new BigDecimal("290"), null),
                new BigDecimal("315"),
                auth);
        // No exception, in-app notification still persisted.
        verify(notificationRepository).save(any());
    }

    @Test
    void sendPriceAlert_swallows_in_app_persist_failure() {
        when(keycloakUserService.getUserEmail(auth)).thenReturn("alice@x");
        when(keycloakUserService.getUsername(auth)).thenReturn("alice");
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("db down"));

        service.sendPriceAlert(
                alert(AlertType.PRICE_ABOVE, new BigDecimal("310"), new BigDecimal("290"), null),
                new BigDecimal("315"),
                auth);
        // Still tries to send the email regardless.
        verify(mailSender).send(any(MimeMessage.class));
    }

    // ---------- sendPriceAlertSystem (scheduled path) ----------

    @Test
    void sendPriceAlertSystem_uses_live_email_from_keycloak() {
        when(keycloakAdminService.getUserEmailById("u-1")).thenReturn("live@x");

        service.sendPriceAlertSystem(
                alert(AlertType.PRICE_BELOW, new BigDecimal("280"), new BigDecimal("295"), null),
                new BigDecimal("275"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPriceAlertSystem_falls_back_to_stored_email_when_keycloak_returns_null() {
        when(keycloakAdminService.getUserEmailById("u-1")).thenReturn(null);

        service.sendPriceAlertSystem(
                alert(AlertType.PRICE_BELOW, new BigDecimal("280"), new BigDecimal("295"), null),
                new BigDecimal("275"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPriceAlertSystem_logs_when_live_email_differs_from_stored() {
        // Live email differs from stored — service logs the change but still uses live.
        when(keycloakAdminService.getUserEmailById("u-1")).thenReturn("different@x");

        service.sendPriceAlertSystem(
                alert(AlertType.PRICE_ABOVE, new BigDecimal("310"), new BigDecimal("290"), null),
                new BigDecimal("315"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    // ---------- fromEmail fallback ----------

    @Test
    void blank_fromEmail_skips_email_as_safety_net() {
        // When app.mail.from resolves to blank (or only the literal noreply
        // fallback), isMailConfigured() returns false, so the SMTP send is
        // skipped to avoid noisy auth failures with a bogus sender. The in-app
        // notification is still recorded.
        ReflectionTestUtils.setField(service, "fromEmail", "");
        when(keycloakUserService.getUserEmail(auth)).thenReturn("alice@x");
        when(keycloakUserService.getUsername(auth)).thenReturn("alice");

        service.sendPriceAlert(
                alert(AlertType.PRICE_ABOVE, new BigDecimal("310"), new BigDecimal("290"), null),
                new BigDecimal("315"),
                auth);

        verify(notificationRepository).save(any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ---------- formatAlertMessage variants ----------

    @Test
    void all_four_alert_types_produce_distinct_messages() {
        when(keycloakUserService.getUserEmail(auth)).thenReturn(null);   // skip email
        when(keycloakUserService.getUsername(auth)).thenReturn("alice");

        service.sendPriceAlert(
                alert(AlertType.PRICE_ABOVE, new BigDecimal("310"), new BigDecimal("290"), null),
                new BigDecimal("315"),
                auth);
        service.sendPriceAlert(
                alert(AlertType.PRICE_BELOW, new BigDecimal("280"), new BigDecimal("295"), null),
                new BigDecimal("275"),
                auth);
        service.sendPriceAlert(
                alert(AlertType.PERCENT_GAIN, new BigDecimal("10"), new BigDecimal("100"), null),
                new BigDecimal("115"),
                auth);
        service.sendPriceAlert(
                alert(AlertType.PERCENT_LOSS, new BigDecimal("10"), new BigDecimal("100"), null),
                new BigDecimal("85"),
                auth);

        // 4 in-app rows persisted, no emails sent (email was null).
        verify(notificationRepository, org.mockito.Mockito.times(4)).save(any());
    }

    // ---------- localized email content ----------

    @Test
    void english_alert_uses_english_subject_and_html_body() throws Exception {
        when(keycloakUserService.getUserEmail(auth)).thenReturn("alice@x");
        when(keycloakUserService.getUsername(auth)).thenReturn("alice");

        PriceAlert a = alert(AlertType.PRICE_ABOVE, new BigDecimal("310"),
                new BigDecimal("290"), "watching this one", "en");

        service.sendPriceAlert(a, new BigDecimal("315"), auth);

        ArgumentCaptor<jakarta.mail.internet.MimeMessage> cap =
                ArgumentCaptor.forClass(jakarta.mail.internet.MimeMessage.class);
        verify(mailSender).send(cap.capture());
        jakarta.mail.internet.MimeMessage sent = cap.getValue();
        assertThat(sent.getSubject()).contains("Price Alert Triggered").contains("THYAO");
        String body = extractHtmlBody(sent);
        assertThat(body)
                .contains("Price Alert Triggered")
                .contains("Hi alice")
                .contains("Alert Details")
                .contains("Target Price:")
                .contains("Current Price:")
                .contains("Price Above")
                .contains("Your note:")
                .doesNotContain("Merhaba")
                .doesNotContain("Fiyat Alarmı");

        // In-app notification row also in English
        ArgumentCaptor<Notification> notif = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notif.capture());
        assertThat(notif.getValue().getTitle()).contains("price alert triggered");
        assertThat(notif.getValue().getMessage()).contains("exceeded the level");
    }

    @Test
    void turkish_alert_uses_turkish_subject_and_html_body() throws Exception {
        when(keycloakUserService.getUserEmail(auth)).thenReturn("alice@x");
        when(keycloakUserService.getUsername(auth)).thenReturn("alice");

        PriceAlert a = alert(AlertType.PRICE_BELOW, new BigDecimal("280"),
                new BigDecimal("295"), null, "tr");

        service.sendPriceAlert(a, new BigDecimal("275"), auth);

        ArgumentCaptor<jakarta.mail.internet.MimeMessage> cap =
                ArgumentCaptor.forClass(jakarta.mail.internet.MimeMessage.class);
        verify(mailSender).send(cap.capture());
        jakarta.mail.internet.MimeMessage sent = cap.getValue();
        assertThat(sent.getSubject()).contains("Fiyat Alarmı Tetiklendi").contains("THYAO");
        String body = extractHtmlBody(sent);
        assertThat(body)
                .contains("Fiyat Alarmı Tetiklendi")
                .contains("Merhaba alice")
                .contains("Alarm Detayları")
                .contains("Fiyat Altı")
                .doesNotContain("Price Alert Triggered")
                .doesNotContain("Alert Details");
    }

    // ---------- localized currency symbol ----------

    @Test
    void usd_alert_renders_prices_with_dollar_prefix_not_lira() throws Exception {
        when(keycloakUserService.getUserEmail(auth)).thenReturn("alice@x");
        when(keycloakUserService.getUsername(auth)).thenReturn("alice");

        // USD-denominated alert created in EN; expect "$" everywhere a price
        // shows up (subject would still localize but body proves the symbol).
        PriceAlert a = alert(AlertType.PRICE_ABOVE,
                new BigDecimal("10"), new BigDecimal("8.20"), null, "en", "USD");

        service.sendPriceAlert(a, new BigDecimal("10.33"), auth);

        ArgumentCaptor<jakarta.mail.internet.MimeMessage> cap =
                ArgumentCaptor.forClass(jakarta.mail.internet.MimeMessage.class);
        verify(mailSender).send(cap.capture());
        String body = extractHtmlBody(cap.getValue());

        // Prices carry "$" prefix
        assertThat(body).contains("$10.33").contains("$10").contains("$8.2");
        // Lira sign nowhere on a USD alert
        assertThat(body).doesNotContain("₺");
    }

    @Test
    void try_alert_renders_prices_with_lira_prefix() throws Exception {
        when(keycloakUserService.getUserEmail(auth)).thenReturn("alice@x");
        when(keycloakUserService.getUsername(auth)).thenReturn("alice");

        // Default fixture is TRY, en language → en formatter (1,234.5)
        PriceAlert a = alert(AlertType.PRICE_ABOVE,
                new BigDecimal("310"), new BigDecimal("290"), null, "en", "TRY");

        service.sendPriceAlert(a, new BigDecimal("315.50"), auth);

        ArgumentCaptor<jakarta.mail.internet.MimeMessage> cap =
                ArgumentCaptor.forClass(jakarta.mail.internet.MimeMessage.class);
        verify(mailSender).send(cap.capture());
        String body = extractHtmlBody(cap.getValue());

        assertThat(body).contains("₺315.5").contains("₺310").contains("₺290");
        assertThat(body).doesNotContain("$");
    }

    @Test
    void percent_alert_uses_percent_sign_on_target_but_currency_on_current() throws Exception {
        when(keycloakUserService.getUserEmail(auth)).thenReturn("alice@x");
        when(keycloakUserService.getUsername(auth)).thenReturn("alice");

        // 10% gain target; current price is a money value
        PriceAlert a = alert(AlertType.PERCENT_GAIN,
                new BigDecimal("10"), new BigDecimal("100"), null, "tr", "USD");

        service.sendPriceAlert(a, new BigDecimal("115"), auth);

        ArgumentCaptor<jakarta.mail.internet.MimeMessage> cap =
                ArgumentCaptor.forClass(jakarta.mail.internet.MimeMessage.class);
        verify(mailSender).send(cap.capture());
        String body = extractHtmlBody(cap.getValue());

        // The target is "%10" (the user's % goal), the current price is "$115"
        assertThat(body).contains("%10").contains("$115");
    }

    // ---------- helper notifications ----------

    @Test
    void sendPushNotification_does_not_throw() {
        service.sendPushNotification("u", "t", "m");
    }

    @Test
    void sendWebSocketNotification_does_not_throw() {
        service.sendWebSocketNotification("u", "payload");
    }
}

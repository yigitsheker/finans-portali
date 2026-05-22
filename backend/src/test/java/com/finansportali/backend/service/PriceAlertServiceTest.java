package com.finansportali.backend.service;

import com.finansportali.backend.dto.request.CreateAlertRequest;
import com.finansportali.backend.dto.response.alert.AlertView;
import com.finansportali.backend.entity.AlertType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.MarketQuote;
import com.finansportali.backend.entity.PriceAlert;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.PriceAlertRepository;
import com.finansportali.backend.service.portfolio.PortfolioCurrencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PriceAlertServiceTest {

    @Mock private PriceAlertRepository alertRepository;
    @Mock private MarketInstrumentRepository instrumentRepository;
    @Mock private MarketQuoteRepository quoteRepository;
    @Mock private NotificationService notificationService;
    @Mock private KeycloakUserService keycloakUserService;
    @Mock private PortfolioCurrencyService currencyService;
    @InjectMocks private PriceAlertService service;

    @Mock private Authentication auth;

    @BeforeEach
    void defaultStubs() {
        // Default to TRY-quoted instruments and 1:1 conversion. Individual
        // tests can override per-symbol when they need USD/TRY conversion.
        when(currencyService.getInstrumentCurrency(anyString(), any())).thenReturn("TRY");
        when(currencyService.getUsdTryRate()).thenReturn(BigDecimal.ONE);
    }

    private static MarketInstrument inst(String symbol) {
        MarketInstrument i = new MarketInstrument();
        i.setSymbol(symbol);
        return i;
    }

    private static MarketQuote quote(BigDecimal last) {
        MarketQuote q = new MarketQuote();
        q.setLast(last);
        return q;
    }

    private static PriceAlert alert(Long id, String userId, String symbol,
                                    AlertType type, BigDecimal target, BigDecimal creation,
                                    boolean active) {
        return PriceAlert.builder()
                .id(id)
                .userId(userId)
                .symbol(symbol)
                .alertType(type)
                .targetPrice(target)
                .creationPrice(creation)
                .active(active)
                .instrument(inst(symbol))
                .build();
    }

    // ---------- createAlert ----------

    @Test
    void createAlert_throws_when_symbol_not_found() {
        when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
        when(keycloakUserService.getUserEmail(auth)).thenReturn("a@x");
        when(instrumentRepository.findBySymbol("XXX")).thenReturn(Optional.empty());

        CreateAlertRequest req = new CreateAlertRequest("XXX", AlertType.PRICE_ABOVE, new BigDecimal("100"), null);

        assertThatThrownBy(() -> service.createAlert(req, auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void createAlert_persists_and_returns_view() {
        when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
        when(keycloakUserService.getUserEmail(auth)).thenReturn("a@x");
        when(instrumentRepository.findBySymbol("THYAO")).thenReturn(Optional.of(inst("THYAO")));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.of(quote(new BigDecimal("290"))));

        // Save returns argument with generated id.
        when(alertRepository.save(any(PriceAlert.class))).thenAnswer(inv -> {
            PriceAlert a = inv.getArgument(0);
            a.setId(5L);
            return a;
        });

        CreateAlertRequest req = new CreateAlertRequest("THYAO", AlertType.PRICE_ABOVE,
                new BigDecimal("310"), "test");

        AlertView view = service.createAlert(req, auth);

        assertThat(view.symbol()).isEqualTo("THYAO");
        assertThat(view.id()).isEqualTo(5L);
        ArgumentCaptor<PriceAlert> cap = ArgumentCaptor.forClass(PriceAlert.class);
        verify(alertRepository).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo("u-1");
        assertThat(cap.getValue().getUserEmail()).isEqualTo("a@x");
        assertThat(cap.getValue().getCreationPrice()).isEqualByComparingTo("290");
    }

    @Test
    void createAlert_snapshots_english_when_locale_is_en() {
        java.util.Locale prev = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        try {
            org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.ENGLISH);

            when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
            when(keycloakUserService.getUserEmail(auth)).thenReturn("a@x");
            when(instrumentRepository.findBySymbol("THYAO"))
                    .thenReturn(Optional.of(inst("THYAO")));
            when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                    .thenReturn(Optional.of(quote(new BigDecimal("290"))));
            when(alertRepository.save(any(PriceAlert.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateAlertRequest req = new CreateAlertRequest(
                    "THYAO", AlertType.PRICE_ABOVE, new BigDecimal("310"), null);
            service.createAlert(req, auth);

            ArgumentCaptor<PriceAlert> cap = ArgumentCaptor.forClass(PriceAlert.class);
            verify(alertRepository).save(cap.capture());
            assertThat(cap.getValue().getLanguage()).isEqualTo("en");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.setLocale(prev);
        }
    }

    @Test
    void createAlert_defaults_language_to_tr_for_non_english_locales() {
        java.util.Locale prev = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        try {
            // Anything that isn't "en" (Turkish, German, no header at all) → "tr"
            org.springframework.context.i18n.LocaleContextHolder.setLocale(
                    java.util.Locale.forLanguageTag("de-DE"));

            when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
            when(keycloakUserService.getUserEmail(auth)).thenReturn("a@x");
            when(instrumentRepository.findBySymbol("THYAO"))
                    .thenReturn(Optional.of(inst("THYAO")));
            when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                    .thenReturn(Optional.empty());
            when(alertRepository.save(any(PriceAlert.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateAlertRequest req = new CreateAlertRequest(
                    "THYAO", AlertType.PRICE_ABOVE, new BigDecimal("310"), null);
            service.createAlert(req, auth);

            ArgumentCaptor<PriceAlert> cap = ArgumentCaptor.forClass(PriceAlert.class);
            verify(alertRepository).save(cap.capture());
            assertThat(cap.getValue().getLanguage()).isEqualTo("tr");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.setLocale(prev);
        }
    }

    // ---------- currency snapshotting + conversion ----------

    @Test
    void createAlert_with_USD_currency_converts_native_TRY_creation_price_to_USD() {
        when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
        when(keycloakUserService.getUserEmail(auth)).thenReturn("a@x");
        when(instrumentRepository.findBySymbol("THYAO")).thenReturn(Optional.of(inst("THYAO")));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.of(quote(new BigDecimal("300"))));
        // THYAO is natively TRY; USDTRY = 30
        when(currencyService.getInstrumentCurrency("THYAO", null)).thenReturn("TRY");
        when(currencyService.getUsdTryRate()).thenReturn(new BigDecimal("30"));
        when(alertRepository.save(any(PriceAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        // User on USD-mode site enters "I want $11 alarm on THYAO"
        CreateAlertRequest req = new CreateAlertRequest(
                "THYAO", AlertType.PRICE_ABOVE, new BigDecimal("11"), null, "USD");

        service.createAlert(req, auth);

        ArgumentCaptor<PriceAlert> cap = ArgumentCaptor.forClass(PriceAlert.class);
        verify(alertRepository).save(cap.capture());
        PriceAlert saved = cap.getValue();
        assertThat(saved.getCurrency()).isEqualTo("USD");
        // 300 TRY / 30 = $10 — snapshot stored in USD
        assertThat(saved.getCreationPrice()).isEqualByComparingTo("10");
        // target stays as user entered ($11)
        assertThat(saved.getTargetPrice()).isEqualByComparingTo("11");
    }

    @Test
    void createAlert_defaults_currency_to_native_when_request_omits_it() {
        when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
        when(keycloakUserService.getUserEmail(auth)).thenReturn("a@x");
        when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.of(inst("AAPL")));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("AAPL"))
                .thenReturn(Optional.of(quote(new BigDecimal("200"))));
        when(currencyService.getInstrumentCurrency("AAPL", null)).thenReturn("USD");
        when(alertRepository.save(any(PriceAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateAlertRequest req = new CreateAlertRequest(
                "AAPL", AlertType.PRICE_ABOVE, new BigDecimal("250"), null, null);
        service.createAlert(req, auth);

        ArgumentCaptor<PriceAlert> cap = ArgumentCaptor.forClass(PriceAlert.class);
        verify(alertRepository).save(cap.capture());
        // AAPL native = USD → alert.currency = USD
        assertThat(cap.getValue().getCurrency()).isEqualTo("USD");
    }

    @Test
    void checkAllAlerts_triggers_USD_alert_when_converted_TRY_quote_crosses_target() {
        // USD alarm "above $10" on THYAO (natively quoted in TRY).
        PriceAlert a = PriceAlert.builder()
                .id(5L).userId("u-1").symbol("THYAO")
                .alertType(AlertType.PRICE_ABOVE)
                .targetPrice(new BigDecimal("10"))
                .creationPrice(new BigDecimal("8.20"))
                .currency("USD")
                .language("tr")
                .active(true)
                .instrument(inst("THYAO"))
                .build();
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        // Native quote ₺310; USDTRY = 30 → $10.33 ≥ $10 → should trigger
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.of(quote(new BigDecimal("310"))));
        when(currencyService.getInstrumentCurrency("THYAO", null)).thenReturn("TRY");
        when(currencyService.getUsdTryRate()).thenReturn(new BigDecimal("30"));

        service.checkAllAlerts();

        assertThat(a.getActive()).isFalse();
        // triggeredPrice persisted in USD (310/30 ≈ 10.333333)
        assertThat(a.getTriggeredPrice()).isEqualByComparingTo("10.333333");
        verify(notificationService).sendPriceAlertSystem(eq(a), any(BigDecimal.class));
    }

    @Test
    void checkAllAlerts_does_not_trigger_USD_alert_when_converted_price_below_target() {
        PriceAlert a = PriceAlert.builder()
                .id(5L).userId("u-1").symbol("THYAO")
                .alertType(AlertType.PRICE_ABOVE)
                .targetPrice(new BigDecimal("11"))
                .creationPrice(new BigDecimal("8.20"))
                .currency("USD")
                .language("tr")
                .active(true)
                .instrument(inst("THYAO"))
                .build();
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        // ₺310 / 30 = $10.33 < $11 → no trigger
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.of(quote(new BigDecimal("310"))));
        when(currencyService.getInstrumentCurrency("THYAO", null)).thenReturn("TRY");
        when(currencyService.getUsdTryRate()).thenReturn(new BigDecimal("30"));

        service.checkAllAlerts();

        assertThat(a.getActive()).isTrue();
        verify(alertRepository, never()).save(any());
    }

    // ---------- getUserAlerts ----------

    @Test
    void getUserAlerts_returns_one_view_per_alert_with_current_price() {
        when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
        PriceAlert a1 = alert(1L, "u-1", "THYAO", AlertType.PRICE_ABOVE,
                new BigDecimal("310"), new BigDecimal("290"), true);
        when(alertRepository.findByUserIdOrderByCreatedAtDesc("u-1")).thenReturn(List.of(a1));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.of(quote(new BigDecimal("300"))));

        List<AlertView> views = service.getUserAlerts(auth);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).currentPrice()).isEqualByComparingTo("300");
    }

    // ---------- deleteAlert ----------

    @Test
    void deleteAlert_throws_when_not_found() {
        when(alertRepository.findById(99L)).thenReturn(Optional.empty());
        when(keycloakUserService.getUserId(auth)).thenReturn("u-1");

        assertThatThrownBy(() -> service.deleteAlert(99L, auth))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteAlert_throws_security_when_owner_mismatch() {
        when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
        PriceAlert a = alert(5L, "OTHER", "THYAO", AlertType.PRICE_ABOVE,
                new BigDecimal("310"), new BigDecimal("290"), true);
        when(alertRepository.findById(5L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.deleteAlert(5L, auth))
                .isInstanceOf(SecurityException.class);
        verify(alertRepository, never()).delete(any());
    }

    @Test
    void deleteAlert_deletes_when_owner_matches() {
        when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
        PriceAlert a = alert(5L, "u-1", "THYAO", AlertType.PRICE_ABOVE,
                new BigDecimal("310"), new BigDecimal("290"), true);
        when(alertRepository.findById(5L)).thenReturn(Optional.of(a));

        service.deleteAlert(5L, auth);
        verify(alertRepository).delete(a);
    }

    // ---------- testAlert ----------

    @Test
    void testAlert_throws_when_not_found() {
        when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
        when(alertRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.testAlert(99L, auth))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAlert_throws_security_when_owner_mismatch() {
        when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
        PriceAlert a = alert(5L, "OTHER", "THYAO", AlertType.PRICE_ABOVE,
                new BigDecimal("310"), new BigDecimal("290"), true);
        when(alertRepository.findById(5L)).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.testAlert(5L, auth))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void testAlert_throws_when_no_current_price() {
        when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
        PriceAlert a = alert(5L, "u-1", "THYAO", AlertType.PRICE_ABOVE,
                new BigDecimal("310"), new BigDecimal("290"), true);
        when(alertRepository.findById(5L)).thenReturn(Optional.of(a));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.testAlert(5L, auth))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testAlert_triggers_persists_and_notifies() {
        when(keycloakUserService.getUserId(auth)).thenReturn("u-1");
        PriceAlert a = alert(5L, "u-1", "THYAO", AlertType.PRICE_ABOVE,
                new BigDecimal("310"), new BigDecimal("290"), true);
        when(alertRepository.findById(5L)).thenReturn(Optional.of(a));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.of(quote(new BigDecimal("315"))));

        AlertView view = service.testAlert(5L, auth);

        assertThat(view.symbol()).isEqualTo("THYAO");
        assertThat(a.getActive()).isFalse();
        assertThat(a.getTriggeredAt()).isNotNull();
        assertThat(a.getTriggeredPrice()).isEqualByComparingTo("315");
        verify(alertRepository).save(a);
        verify(notificationService).sendPriceAlert(eq(a), any(BigDecimal.class), eq(auth));
    }

    // ---------- checkAllAlerts ----------

    @Test
    void checkAllAlerts_skips_alerts_without_quote() {
        PriceAlert a = alert(5L, "u-1", "THYAO", AlertType.PRICE_ABOVE,
                new BigDecimal("310"), new BigDecimal("290"), true);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.empty());

        service.checkAllAlerts();
        verify(alertRepository, never()).save(any());
        verify(notificationService, never()).sendPriceAlertSystem(any(), any());
    }

    @Test
    void checkAllAlerts_triggers_PRICE_ABOVE_when_current_at_or_above_target() {
        PriceAlert a = alert(5L, "u-1", "THYAO", AlertType.PRICE_ABOVE,
                new BigDecimal("310"), new BigDecimal("290"), true);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.of(quote(new BigDecimal("310"))));

        service.checkAllAlerts();
        assertThat(a.getActive()).isFalse();
        verify(alertRepository).save(a);
        verify(notificationService).sendPriceAlertSystem(eq(a), any(BigDecimal.class));
    }

    @Test
    void checkAllAlerts_triggers_PRICE_BELOW_when_current_at_or_below_target() {
        PriceAlert a = alert(5L, "u-1", "THYAO", AlertType.PRICE_BELOW,
                new BigDecimal("290"), new BigDecimal("300"), true);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.of(quote(new BigDecimal("289"))));

        service.checkAllAlerts();
        assertThat(a.getActive()).isFalse();
    }

    @Test
    void checkAllAlerts_triggers_PERCENT_GAIN() {
        PriceAlert a = alert(5L, "u-1", "THYAO", AlertType.PERCENT_GAIN,
                new BigDecimal("10"), new BigDecimal("100"), true);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.of(quote(new BigDecimal("115"))));   // +15% > 10%

        service.checkAllAlerts();
        assertThat(a.getActive()).isFalse();
    }

    @Test
    void checkAllAlerts_triggers_PERCENT_LOSS() {
        PriceAlert a = alert(5L, "u-1", "THYAO", AlertType.PERCENT_LOSS,
                new BigDecimal("10"), new BigDecimal("100"), true);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.of(quote(new BigDecimal("85"))));    // -15% < -10%

        service.checkAllAlerts();
        assertThat(a.getActive()).isFalse();
    }

    @Test
    void checkAllAlerts_PERCENT_alerts_silent_when_creationPrice_null() {
        PriceAlert a = alert(5L, "u-1", "THYAO", AlertType.PERCENT_GAIN,
                new BigDecimal("10"), null, true);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.of(quote(new BigDecimal("999"))));

        service.checkAllAlerts();
        assertThat(a.getActive()).isTrue();
        verify(alertRepository, never()).save(any());
    }

    @Test
    void checkAllAlerts_swallows_notification_exception() {
        PriceAlert a = alert(5L, "u-1", "THYAO", AlertType.PRICE_ABOVE,
                new BigDecimal("310"), new BigDecimal("290"), true);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(quoteRepository.findTop1ByInstrument_SymbolOrderByAsOfDesc("THYAO"))
                .thenReturn(Optional.of(quote(new BigDecimal("320"))));
        doThrow(new RuntimeException("smtp dead"))
                .when(notificationService).sendPriceAlertSystem(any(), any());

        service.checkAllAlerts();          // no exception
        verify(alertRepository).save(a);   // still persisted as triggered
    }
}

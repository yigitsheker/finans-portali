package com.finansportali.backend.service;

import com.finansportali.backend.entity.ExchangeRate;
import com.finansportali.backend.repository.ExchangeRateRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock private ExchangeRateRepository repository;
    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeRateService(repository, new SimpleMeterRegistry());
    }

    @Test
    void getLatestRates_delegates() {
        List<ExchangeRate> rows = List.of();
        when(repository.findLatestRatesBySource()).thenReturn(rows);
        assertThat(service.getLatestRates()).isEqualTo(rows);
    }

    @Test
    void getRatesBySource_passes_today_to_repository() {
        when(repository.findBySourceAndRateDate(eq("TCMB"), any(LocalDate.class)))
                .thenReturn(List.of());
        assertThat(service.getRatesBySource("TCMB")).isEmpty();
        verify(repository).findBySourceAndRateDate(eq("TCMB"), any(LocalDate.class));
    }

    @Test
    void getSources_delegates() {
        when(repository.findDistinctSources()).thenReturn(List.of("TCMB"));
        assertThat(service.getSources()).containsExactly("TCMB");
    }

    @Test
    void getCurrencyHistory_delegates() {
        when(repository.findByCurrencyCodeOrderByRateDateDesc("USD")).thenReturn(List.of());
        assertThat(service.getCurrencyHistory("USD")).isEmpty();
    }

    @Test
    void seedIfEmpty_skips_when_count_positive() {
        when(repository.count()).thenReturn(3L);
        service.seedIfEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void seedIfEmpty_inserts_three_rows_when_db_empty() {
        when(repository.count()).thenReturn(0L);
        service.seedIfEmpty();
        verify(repository, times(3)).save(any(ExchangeRate.class));
    }

    @Test
    void fetchTcmbRates_does_not_throw_when_network_fails() {
        // No mocks configured for the embedded WebClient; the call will fail and the
        // outer try/catch swallows the exception. The service should remain healthy.
        service.fetchTcmbRates();
    }

    // ── parseTcmbXml / saveCurrencyFromXml helpers ──────────────────────
    // Reached via reflection so we don't have to stand up a mocked WebClient
    // just to drive the XML parser. Each test pins one branch of the
    // currency-row handler so coverage on the new helpers stays high.

    @Test
    void parseTcmbXml_saves_each_new_currency() {
        String xml =
                "<Currency CrossOrder=\"1\" CurrencyCode=\"USD\">"
                + "  <CurrencyName>US Dollar</CurrencyName>"
                + "  <BanknoteBuying>34.10</BanknoteBuying>"
                + "  <BanknoteSelling>34.30</BanknoteSelling>"
                + "  <ForexBuying>34.05</ForexBuying>"
                + "  <ForexSelling>34.35</ForexSelling>"
                + "</Currency>";
        org.mockito.Mockito.when(
                        repository.findBySourceAndRateDate(org.mockito.ArgumentMatchers.eq("TCMB"),
                                org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(java.util.List.of());

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "parseTcmbXml", xml, LocalDate.now());

        verify(repository).save(any(ExchangeRate.class));
    }

    @Test
    void parseTcmbXml_skips_already_existing_currency() {
        String xml =
                "<Currency CrossOrder=\"1\" CurrencyCode=\"USD\">"
                + "  <CurrencyName>US Dollar</CurrencyName>"
                + "  <BanknoteBuying>34.10</BanknoteBuying>"
                + "  <BanknoteSelling>34.30</BanknoteSelling>"
                + "</Currency>";
        ExchangeRate existing = new ExchangeRate("USD", "US Dollar",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                LocalDate.now(), "TCMB");
        org.mockito.Mockito.when(
                        repository.findBySourceAndRateDate(org.mockito.ArgumentMatchers.eq("TCMB"),
                                org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(java.util.List.of(existing));

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "parseTcmbXml", xml, LocalDate.now());

        verify(repository, never()).save(any(ExchangeRate.class));
    }

    @Test
    void parseTcmbXml_skips_currency_with_missing_banknote_buying() {
        // BanknoteBuying tag absent → saveCurrencyFromXml short-circuits to false.
        String xml =
                "<Currency CrossOrder=\"1\" CurrencyCode=\"USD\">"
                + "  <CurrencyName>US Dollar</CurrencyName>"
                + "  <BanknoteSelling>34.30</BanknoteSelling>"
                + "</Currency>";
        org.mockito.Mockito.when(
                        repository.findBySourceAndRateDate(org.mockito.ArgumentMatchers.eq("TCMB"),
                                org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(java.util.List.of());

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "parseTcmbXml", xml, LocalDate.now());

        verify(repository, never()).save(any(ExchangeRate.class));
    }

    @Test
    void parseTcmbXml_falls_back_to_buying_when_forex_rate_missing() {
        // No ForexBuying / ForexSelling → effectiveBuying/Selling fall back
        // to banknote values. The save still happens.
        String xml =
                "<Currency CrossOrder=\"1\" CurrencyCode=\"USD\">"
                + "  <CurrencyName>US Dollar</CurrencyName>"
                + "  <BanknoteBuying>34.10</BanknoteBuying>"
                + "  <BanknoteSelling>34.30</BanknoteSelling>"
                + "</Currency>";
        org.mockito.Mockito.when(
                        repository.findBySourceAndRateDate(org.mockito.ArgumentMatchers.eq("TCMB"),
                                org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(java.util.List.of());

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "parseTcmbXml", xml, LocalDate.now());

        verify(repository).save(any(ExchangeRate.class));
    }

    @Test
    void parseTcmbXml_swallows_invalid_number_format_and_continues() {
        // Bad decimal in BanknoteBuying triggers the saveCurrencyFromXml
        // RuntimeException catch — service should not bubble it out and the
        // second valid currency should still be saved.
        String xml =
                "<Currency CrossOrder=\"1\" CurrencyCode=\"USD\">"
                + "  <BanknoteBuying>not-a-number</BanknoteBuying>"
                + "  <BanknoteSelling>34.30</BanknoteSelling>"
                + "</Currency>"
                + "<Currency CrossOrder=\"2\" CurrencyCode=\"EUR\">"
                + "  <BanknoteBuying>37.10</BanknoteBuying>"
                + "  <BanknoteSelling>37.30</BanknoteSelling>"
                + "</Currency>";
        org.mockito.Mockito.when(
                        repository.findBySourceAndRateDate(org.mockito.ArgumentMatchers.eq("TCMB"),
                                org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(java.util.List.of());

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "parseTcmbXml", xml, LocalDate.now());

        verify(repository, times(1)).save(any(ExchangeRate.class));
    }
}

package com.finansportali.backend.service;

import com.finansportali.backend.entity.ExchangeRate;
import com.finansportali.backend.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    @InjectMocks private ExchangeRateService service;

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
}

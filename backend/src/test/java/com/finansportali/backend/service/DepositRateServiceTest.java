package com.finansportali.backend.service;

import com.finansportali.backend.entity.DepositRatePoint;
import com.finansportali.backend.repository.DepositRatePointRepository;
import com.finansportali.backend.service.client.deposit.TcmbDepositRateFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositRateServiceTest {

    @Mock private DepositRatePointRepository repo;
    @Mock private TcmbDepositRateFetcher fetcher;
    @InjectMocks private DepositRateService service;

    private static DepositRatePoint point(LocalDate d, String currency, String rate12m) {
        DepositRatePoint p = new DepositRatePoint(d, currency);
        p.setRate12m(new BigDecimal(rate12m));
        return p;
    }

    @Test
    void refresh_returns_zero_when_fetcher_empty() {
        when(fetcher.fetchDepositRates(any(), any())).thenReturn(List.of());
        assertThat(service.refresh()).isZero();
        verify(repo, never()).save(any());
    }

    @Test
    void refresh_inserts_new_rows_with_source_and_updatedAt() {
        DepositRatePoint fresh = point(LocalDate.of(2026, 1, 1), "TRY", "50");
        when(fetcher.fetchDepositRates(any(), any())).thenReturn(List.of(fresh));
        when(repo.findByPeriodDateAndCurrency(LocalDate.of(2026, 1, 1), "TRY"))
                .thenReturn(Optional.empty());

        int n = service.refresh();
        assertThat(n).isEqualTo(1);

        ArgumentCaptor<DepositRatePoint> cap = ArgumentCaptor.forClass(DepositRatePoint.class);
        verify(repo).save(cap.capture());
        DepositRatePoint saved = cap.getValue();
        assertThat(saved.getRate12m()).isEqualByComparingTo("50");
        assertThat(saved.getSource()).isEqualTo("TCMB_EVDS3");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void refresh_updates_existing_row_when_present() {
        DepositRatePoint fresh = point(LocalDate.of(2026, 1, 1), "TRY", "55");
        when(fetcher.fetchDepositRates(any(), any())).thenReturn(List.of(fresh));

        DepositRatePoint existing = point(LocalDate.of(2026, 1, 1), "TRY", "40");
        when(repo.findByPeriodDateAndCurrency(LocalDate.of(2026, 1, 1), "TRY"))
                .thenReturn(Optional.of(existing));

        service.refresh();
        assertThat(existing.getRate12m()).isEqualByComparingTo("55");
        verify(repo).save(existing);
    }

    @Test
    void scheduledRefresh_swallows_exceptions() {
        when(fetcher.fetchDepositRates(any(), any()))
                .thenThrow(new RuntimeException("boom"));
        service.scheduledRefresh();
    }

    @Test
    void getAllForCurrency_returns_repo_results() {
        List<DepositRatePoint> rows = List.of();
        when(repo.findByCurrencyOrderByPeriodDateAsc("TRY")).thenReturn(rows);
        assertThat(service.getAllForCurrency("TRY")).isEqualTo(rows);
    }

    @Test
    void getLatest_returns_repo_result() {
        when(repo.findLatestOnOrBefore(org.mockito.ArgumentMatchers.eq("TRY"), any()))
                .thenReturn(Optional.empty());
        assertThat(service.getLatest("TRY")).isEmpty();
    }
}

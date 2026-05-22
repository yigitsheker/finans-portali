package com.finansportali.backend.service;

import com.finansportali.backend.dto.response.inflation.InflationCompareDto;
import com.finansportali.backend.entity.InflationDataPoint;
import com.finansportali.backend.repository.InflationDataPointRepository;
import com.finansportali.backend.service.client.inflation.TcmbInflationFetcher;
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
class InflationServiceTest {

    @Mock private InflationDataPointRepository repo;
    @Mock private TcmbInflationFetcher fetcher;
    @InjectMocks private InflationService service;

    private static InflationDataPoint point(LocalDate d, String cpi) {
        InflationDataPoint p = new InflationDataPoint(d);
        p.setCpiIndex(new BigDecimal(cpi));
        return p;
    }

    @Test
    void refresh_returns_zero_when_fetcher_empty_and_does_not_save() {
        when(fetcher.fetchInflationHistory(any(), any())).thenReturn(List.of());
        assertThat(service.refresh()).isZero();
        verify(repo, never()).save(any());
    }

    @Test
    void refresh_upserts_new_rows() {
        InflationDataPoint p = point(LocalDate.of(2026, 1, 1), "150");
        p.setCpiYearlyChange(new BigDecimal("65"));
        p.setCpiMonthlyChange(new BigDecimal("3"));
        when(fetcher.fetchInflationHistory(any(), any())).thenReturn(List.of(p));
        when(repo.findByPeriodDate(LocalDate.of(2026, 1, 1))).thenReturn(Optional.empty());

        int n = service.refresh();
        assertThat(n).isEqualTo(1);

        ArgumentCaptor<InflationDataPoint> cap = ArgumentCaptor.forClass(InflationDataPoint.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getCpiIndex()).isEqualByComparingTo("150");
        assertThat(cap.getValue().getSource()).isEqualTo("TCMB_EVDS3");
    }

    @Test
    void refresh_updates_existing_row_in_place() {
        InflationDataPoint fresh = point(LocalDate.of(2026, 1, 1), "200");
        when(fetcher.fetchInflationHistory(any(), any())).thenReturn(List.of(fresh));

        InflationDataPoint existing = point(LocalDate.of(2026, 1, 1), "100");
        when(repo.findByPeriodDate(LocalDate.of(2026, 1, 1))).thenReturn(Optional.of(existing));

        service.refresh();
        // Update in-place — same instance, with new CPI value.
        assertThat(existing.getCpiIndex()).isEqualByComparingTo("200");
    }

    @Test
    void scheduledRefresh_swallows_exceptions() {
        when(fetcher.fetchInflationHistory(any(), any()))
                .thenThrow(new RuntimeException("boom"));
        service.scheduledRefresh();   // does not throw
    }

    @Test
    void getAllAscending_returns_repo_results() {
        when(repo.findAllByOrderByPeriodDateAsc()).thenReturn(List.of());
        assertThat(service.getAllAscending()).isEmpty();
    }

    @Test
    void getLatest_returns_repo_result() {
        when(repo.findLatestOnOrBefore(any())).thenReturn(Optional.empty());
        assertThat(service.getLatest()).isEmpty();
    }

    @Test
    void getOnOrBefore_returns_repo_result() {
        InflationDataPoint p = point(LocalDate.of(2026, 1, 1), "100");
        when(repo.findLatestOnOrBefore(LocalDate.of(2026, 1, 5))).thenReturn(Optional.of(p));
        assertThat(service.getOnOrBefore(LocalDate.of(2026, 1, 5))).isPresent();
    }

    @Test
    void compare_returns_empty_when_no_from_point() {
        when(repo.findLatestOnOrBefore(LocalDate.of(2026, 1, 1))).thenReturn(Optional.empty());
        when(repo.findLatestOnOrBefore(LocalDate.of(2026, 2, 1)))
                .thenReturn(Optional.of(point(LocalDate.of(2026, 2, 1), "100")));
        assertThat(service.compare(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), null))
                .isEmpty();
    }

    @Test
    void compare_returns_empty_when_cpiFrom_is_null() {
        InflationDataPoint from = new InflationDataPoint(LocalDate.of(2026, 1, 1));
        when(repo.findLatestOnOrBefore(LocalDate.of(2026, 1, 1))).thenReturn(Optional.of(from));
        when(repo.findLatestOnOrBefore(LocalDate.of(2026, 2, 1)))
                .thenReturn(Optional.of(point(LocalDate.of(2026, 2, 1), "100")));
        assertThat(service.compare(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), null))
                .isEmpty();
    }

    @Test
    void compare_returns_cumulative_inflation_without_nominal() {
        when(repo.findLatestOnOrBefore(LocalDate.of(2026, 1, 1)))
                .thenReturn(Optional.of(point(LocalDate.of(2026, 1, 1), "100")));
        when(repo.findLatestOnOrBefore(LocalDate.of(2027, 1, 1)))
                .thenReturn(Optional.of(point(LocalDate.of(2027, 1, 1), "150")));

        Optional<InflationCompareDto> dto = service.compare(LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), null);
        assertThat(dto).isPresent();
        // (150/100 - 1) * 100 = 50
        assertThat(dto.get().cumulativeInflationPct()).isEqualByComparingTo("50");
        assertThat(dto.get().realReturnPct()).isNull();
    }

    @Test
    void compare_computes_real_return_with_nominal() {
        when(repo.findLatestOnOrBefore(LocalDate.of(2026, 1, 1)))
                .thenReturn(Optional.of(point(LocalDate.of(2026, 1, 1), "100")));
        when(repo.findLatestOnOrBefore(LocalDate.of(2027, 1, 1)))
                .thenReturn(Optional.of(point(LocalDate.of(2027, 1, 1), "120")));

        // Nominal +50%, inflation +20% → real = (1.5/1.2 - 1)*100 = 25%
        Optional<InflationCompareDto> dto = service.compare(LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), new BigDecimal("50"));
        assertThat(dto).isPresent();
        assertThat(dto.get().cumulativeInflationPct()).isEqualByComparingTo("20");
        assertThat(dto.get().realReturnPct())
                .isEqualByComparingTo(new BigDecimal("25.0000"));
    }
}

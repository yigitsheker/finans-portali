package com.finansportali.backend.service;

import com.finansportali.backend.dto.response.SeriesPointDto;
import com.finansportali.backend.dto.response.TechnicalAnalysisResponse;
import com.finansportali.backend.entity.HistoricalPrice;
import com.finansportali.backend.repository.HistoricalPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pure-math unit tests for {@link TechnicalAnalysisService}.
 *
 * The service is straightforward to unit-test because nearly all the work
 * happens inside private indicator helpers that we exercise through
 * {@link TechnicalAnalysisService#getTechnicalAnalysis} — the only
 * collaborator is {@link HistoricalPriceService}, which we mock.
 *
 * The series we feed in is short but carefully crafted: monotonic rises,
 * monotonic drops, alternating moves — each set targets a specific
 * indicator behaviour so a regression in the math fails a named test.
 */
@ExtendWith(MockitoExtension.class)
class TechnicalAnalysisServiceTest {

    @Mock private HistoricalPriceRepository historicalPriceRepo;
    @Mock private HistoricalPriceService historicalPriceService;
    @InjectMocks private TechnicalAnalysisService service;

    private LocalDate from;
    private LocalDate to;

    @BeforeEach
    void setUp() {
        // Pick a fixed window so the service's warmup-buffer math is
        // deterministic and we can compare against hand-computed values.
        from = LocalDate.of(2026, 4, 1);
        to = LocalDate.of(2026, 5, 1);
    }

    /** Build a HistoricalPrice with sequential dates starting at `from`. */
    private List<HistoricalPrice> series(double... closes) {
        List<HistoricalPrice> out = new ArrayList<>(closes.length);
        LocalDate d = from;
        for (double c : closes) {
            HistoricalPrice hp = new HistoricalPrice("TEST", d, BigDecimal.valueOf(c));
            out.add(hp);
            d = d.plusDays(1);
        }
        return out;
    }

    private void stubFetch(List<HistoricalPrice> rows) {
        // Service fetches `from - 60 days` to give indicators warmup. We
        // don't care about the exact range; return our crafted rows for
        // any fetch.
        when(historicalPriceService.getHistoricalPrices(anyString(), any(), any())).thenReturn(rows);
    }

    @Test
    void empty_history_returns_insufficient_data() {
        stubFetch(List.of());
        TechnicalAnalysisResponse r = service.getTechnicalAnalysis("TEST", from, to);
        assertThat(r.getTrend().getDirection()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(r.getSeries()).isEmpty();
        assertThat(r.getSummary().getLatestClose()).isZero();
    }

    @Test
    void strictly_rising_series_classifies_as_upward() {
        double[] closes = new double[40];
        for (int i = 0; i < closes.length; i++) closes[i] = 100.0 + i * 2.0;
        stubFetch(series(closes));

        TechnicalAnalysisResponse r = service.getTechnicalAnalysis("TEST", from, to);

        assertThat(r.getTrend().getDirection()).isEqualTo("UPWARD");
        // change% = (last-first)/first × 100. Caveat: trend math runs on
        // the *visible* window (rows whose date >= from), not the entire
        // synthetic series. We at least know it must be positive.
        assertThat(r.getTrend().getChangePercent()).isPositive();
        assertThat(r.getSummary().getHighestClose()).isGreaterThan(r.getSummary().getLowestClose());
    }

    @Test
    void strictly_falling_series_classifies_as_downward() {
        double[] closes = new double[40];
        for (int i = 0; i < closes.length; i++) closes[i] = 200.0 - i * 2.0;
        stubFetch(series(closes));

        TechnicalAnalysisResponse r = service.getTechnicalAnalysis("TEST", from, to);

        assertThat(r.getTrend().getDirection()).isEqualTo("DOWNWARD");
        assertThat(r.getTrend().getChangePercent()).isNegative();
    }

    @Test
    void flat_series_classifies_as_sideways() {
        double[] closes = new double[40];
        for (int i = 0; i < closes.length; i++) closes[i] = 100.0;
        stubFetch(series(closes));

        TechnicalAnalysisResponse r = service.getTechnicalAnalysis("TEST", from, to);

        // Flat → slope ≈ 0, must NOT round into UPWARD/DOWNWARD.
        assertThat(r.getTrend().getDirection()).isEqualTo("SIDEWAYS");
        // Annualised volatility of a flat series is zero.
        assertThat(r.getSummary().getVolatilityPercent()).isEqualTo(0.0);
    }

    @Test
    void sma7_value_matches_arithmetic_mean() {
        // 14 closes, ascending integers — easy to verify by hand.
        double[] closes = new double[14];
        for (int i = 0; i < closes.length; i++) closes[i] = i + 1; // 1..14
        stubFetch(series(closes));

        TechnicalAnalysisResponse r = service.getTechnicalAnalysis("TEST", from, to);
        // SMA 7 at the last point = mean of last 7 values = (8+9+...+14)/7 = 11.
        List<SeriesPointDto> s = r.getSeries();
        SeriesPointDto last = s.get(s.size() - 1);
        assertThat(last.getSma7()).isCloseTo(11.0, offset(1e-9));
        // SMA 20 hasn't accumulated enough points → null.
        assertThat(last.getSma20()).isNull();
    }

    @Test
    void rsi_saturates_at_100_for_purely_rising_window() {
        // 30 strictly-rising closes — every diff is positive → avg-loss = 0
        // → Wilder RSI returns 100 by convention.
        double[] closes = new double[30];
        for (int i = 0; i < closes.length; i++) closes[i] = 100.0 + i;
        stubFetch(series(closes));

        TechnicalAnalysisResponse r = service.getTechnicalAnalysis("TEST", from, to);
        assertThat(r.getSummary().getRsi14Latest()).isCloseTo(100.0, offset(1e-6));
    }

    @Test
    void rsi_is_zero_for_purely_falling_window() {
        // Mirror image: every diff negative → avg-gain = 0 → RS = 0 →
        // RSI = 100 - 100/(1+0) = 0.
        double[] closes = new double[30];
        for (int i = 0; i < closes.length; i++) closes[i] = 200.0 - i;
        stubFetch(series(closes));

        TechnicalAnalysisResponse r = service.getTechnicalAnalysis("TEST", from, to);
        assertThat(r.getSummary().getRsi14Latest()).isCloseTo(0.0, offset(1e-6));
    }

    @Test
    void bollinger_bands_envelope_the_mid_line() {
        // Random-ish series with both ups and downs so the bands aren't
        // degenerate. Bands at any point must satisfy lower ≤ mid ≤ upper.
        double[] closes = {100, 102, 101, 105, 103, 108, 110, 107, 109, 112,
                           110, 115, 113, 118, 116, 120, 117, 119, 122, 120,
                           125, 123, 128, 126, 130, 128, 132, 130, 135, 133};
        stubFetch(series(closes));

        TechnicalAnalysisResponse r = service.getTechnicalAnalysis("TEST", from, to);
        for (SeriesPointDto p : r.getSeries()) {
            if (p.getBbMid() == null) continue; // not enough warmup yet
            assertThat(p.getBbUpper()).isGreaterThanOrEqualTo(p.getBbMid());
            assertThat(p.getBbLower()).isLessThanOrEqualTo(p.getBbMid());
        }
    }

    @Test
    void macd_histogram_equals_macd_minus_signal() {
        // 60-point random walk so both EMAs and the signal are populated.
        double[] closes = new double[60];
        double v = 100;
        for (int i = 0; i < closes.length; i++) {
            // Deterministic pseudo-random walk so the test is repeatable.
            v += Math.sin(i * 0.3) * 1.5 + (i % 3 == 0 ? 0.5 : -0.2);
            closes[i] = v;
        }
        stubFetch(series(closes));

        TechnicalAnalysisResponse r = service.getTechnicalAnalysis("TEST", from, to);
        int populated = 0;
        for (SeriesPointDto p : r.getSeries()) {
            if (p.getMacd() == null || p.getMacdSignal() == null) continue;
            populated++;
            assertThat(p.getMacdHist())
                    .isNotNull()
                    .isCloseTo(p.getMacd() - p.getMacdSignal(), offset(1e-9));
        }
        // Sanity: we expect AT LEAST one point with both MACD and signal
        // populated, otherwise the assertion above is vacuous.
        assertThat(populated).isGreaterThan(0);
    }

    @Test
    void summary_latest_close_is_last_visible_close() {
        double[] closes = {100, 110, 120};
        stubFetch(series(closes));

        TechnicalAnalysisResponse r = service.getTechnicalAnalysis("TEST", from, to);
        assertThat(r.getSummary().getLatestClose()).isEqualTo(120.0);
        assertThat(r.getSummary().getHighestClose()).isEqualTo(120.0);
        assertThat(r.getSummary().getLowestClose()).isEqualTo(100.0);
        assertThat(r.getSummary().getAverageClose()).isCloseTo(110.0, offset(1e-9));
    }

    @Test
    void series_dates_match_input() {
        double[] closes = {100, 105, 110};
        List<HistoricalPrice> rows = series(closes);
        stubFetch(rows);

        TechnicalAnalysisResponse r = service.getTechnicalAnalysis("TEST", from, to);
        assertThat(r.getSeries()).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(r.getSeries().get(i).getDate())
                    .isEqualTo(rows.get(i).getPriceDate().toString());
        }
    }

    @Test
    void adjusted_close_takes_precedence_over_raw_close() {
        // When adjustedClosePrice is set, the service must use it (so
        // dividend-adjusted historical comparisons behave correctly).
        HistoricalPrice hp = new HistoricalPrice("TEST", from, BigDecimal.valueOf(100.0));
        hp.setAdjustedClosePrice(BigDecimal.valueOf(95.0));
        HistoricalPrice hp2 = new HistoricalPrice("TEST", from.plusDays(1), BigDecimal.valueOf(110.0));
        hp2.setAdjustedClosePrice(BigDecimal.valueOf(105.0));
        stubFetch(List.of(hp, hp2));

        TechnicalAnalysisResponse r = service.getTechnicalAnalysis("TEST", from, to);
        assertThat(r.getSummary().getLatestClose()).isEqualTo(105.0);
        assertThat(r.getSummary().getLowestClose()).isEqualTo(95.0);
    }
}

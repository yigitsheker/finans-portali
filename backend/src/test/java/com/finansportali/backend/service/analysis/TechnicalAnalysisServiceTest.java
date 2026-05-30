package com.finansportali.backend.service.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-math tests for the analysis-package {@link TechnicalAnalysisService}
 * (signals + trend + volatility + moving-average comparison). This sibling
 * class is distinct from the chart-data TA service in
 * {@code com.finansportali.backend.service.TechnicalAnalysisService}; they
 * share a name only because Spring already disambiguates via an explicit
 * {@code @Service} bean name.
 */
class TechnicalAnalysisServiceTest {

    private TechnicalAnalysisService ta;

    @BeforeEach
    void setUp() {
        ta = new TechnicalAnalysisService();
    }

    // ── shortTermSignal / longTermSignal ────────────────────────────────

    @Test
    void shortTermSignal_returns_NEUTRAL_when_both_null() {
        assertThat(ta.shortTermSignal(null, null)).isEqualTo(TechnicalAnalysisService.NEUTRAL);
    }

    @Test
    void shortTermSignal_returns_BUY_on_strong_weekly_uptick() {
        assertThat(ta.shortTermSignal(new BigDecimal("5"), new BigDecimal("2")))
                .isEqualTo(TechnicalAnalysisService.BUY);
    }

    @Test
    void shortTermSignal_returns_SELL_on_strong_weekly_downtick() {
        assertThat(ta.shortTermSignal(new BigDecimal("-5"), new BigDecimal("-2")))
                .isEqualTo(TechnicalAnalysisService.SELL);
    }

    @Test
    void shortTermSignal_returns_HOLD_for_flat_moves() {
        assertThat(ta.shortTermSignal(new BigDecimal("1"), new BigDecimal("1")))
                .isEqualTo(TechnicalAnalysisService.HOLD);
    }

    @Test
    void longTermSignal_returns_NEUTRAL_when_both_null() {
        assertThat(ta.longTermSignal(null, null)).isEqualTo(TechnicalAnalysisService.NEUTRAL);
    }

    @Test
    void longTermSignal_returns_BUY_on_strong_yearly_with_stable_monthly() {
        assertThat(ta.longTermSignal(new BigDecimal("0"), new BigDecimal("30")))
                .isEqualTo(TechnicalAnalysisService.BUY);
    }

    @Test
    void longTermSignal_returns_SELL_on_steep_yearly_decline() {
        assertThat(ta.longTermSignal(new BigDecimal("0"), new BigDecimal("-30")))
                .isEqualTo(TechnicalAnalysisService.SELL);
    }

    // ── trend(weekly, monthly) ──────────────────────────────────────────

    @Test
    void trend_returns_UP_when_both_weekly_and_monthly_positive_above_thresholds() {
        // Implementation: w > 1 && m > 0 → "UP".
        assertThat(ta.trend(new BigDecimal("2"), new BigDecimal("1"))).isEqualTo("UP");
    }

    @Test
    void trend_returns_DOWN_when_both_weekly_and_monthly_negative_above_thresholds() {
        // Implementation: w < -1 && m < 0 → "DOWN".
        assertThat(ta.trend(new BigDecimal("-2"), new BigDecimal("-1"))).isEqualTo("DOWN");
    }

    @Test
    void trend_returns_SIDEWAYS_for_mixed_or_small_moves() {
        // Either condition not satisfied → "SIDEWAYS".
        assertThat(ta.trend(new BigDecimal("0.5"), new BigDecimal("1"))).isEqualTo("SIDEWAYS");
        assertThat(ta.trend(new BigDecimal("-2"), new BigDecimal("3"))).isEqualTo("SIDEWAYS");
    }

    @Test
    void trend_returns_SIDEWAYS_when_both_inputs_null() {
        assertThat(ta.trend(null, null)).isEqualTo("SIDEWAYS");
    }

    // ── priceAboveMovingAverage(series, window) ─────────────────────────

    @Test
    void priceAboveMovingAverage_returns_null_for_null_series() {
        assertThat(ta.priceAboveMovingAverage(null, 5)).isNull();
    }

    @Test
    void priceAboveMovingAverage_returns_null_for_too_short_series() {
        // Need at least window+1 entries; 3 < 5+1.
        List<BigDecimal> shortSeries = List.of(
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"));
        assertThat(ta.priceAboveMovingAverage(shortSeries, 5)).isNull();
    }

    @Test
    void priceAboveMovingAverage_returns_true_when_latest_above_mean() {
        // Last close 100 vs mean of prior 4 (10 each) → 100 > 10 → true.
        List<BigDecimal> series = List.of(
                new BigDecimal("10"),
                new BigDecimal("10"),
                new BigDecimal("10"),
                new BigDecimal("10"),
                new BigDecimal("100"));
        assertThat(ta.priceAboveMovingAverage(series, 4)).isTrue();
    }

    @Test
    void priceAboveMovingAverage_returns_false_when_latest_below_mean() {
        // Last close 1 vs mean of prior 4 (10 each) → 1 < 10 → false.
        List<BigDecimal> series = List.of(
                new BigDecimal("10"),
                new BigDecimal("10"),
                new BigDecimal("10"),
                new BigDecimal("10"),
                new BigDecimal("1"));
        assertThat(ta.priceAboveMovingAverage(series, 4)).isFalse();
    }

    // ── volatility(monthly, yearly) ─────────────────────────────────────

    @Test
    void volatility_classifies_HIGH_when_thresholds_breached() {
        assertThat(ta.volatility(new BigDecimal("20"), null)).isEqualTo("HIGH");
        assertThat(ta.volatility(null, new BigDecimal("90"))).isEqualTo("HIGH");
    }

    @Test
    void volatility_classifies_MEDIUM_between_thresholds() {
        assertThat(ta.volatility(new BigDecimal("10"), new BigDecimal("40"))).isEqualTo("MEDIUM");
    }

    @Test
    void volatility_classifies_LOW_for_calm_moves() {
        assertThat(ta.volatility(new BigDecimal("2"), new BigDecimal("10"))).isEqualTo("LOW");
        assertThat(ta.volatility(null, null)).isEqualTo("LOW");
    }
}

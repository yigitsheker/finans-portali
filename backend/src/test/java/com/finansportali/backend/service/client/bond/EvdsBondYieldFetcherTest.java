package com.finansportali.backend.service.client.bond;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Unit tests for {@link EvdsBondYieldFetcher#computeYtm}.
 *
 * The simple-approximation YTM formula:
 *   YTM ≈ (C + (F − P) / n) / ((F + P) / 2) × 100
 * with F = 100. Each test pins one branch of the math (premium bond,
 * discount bond, par bond, expired bond, zero-coupon).
 */
class EvdsBondYieldFetcherTest {

    private final LocalDate today = LocalDate.of(2026, 5, 19);

    @Test
    void discount_bond_yields_above_coupon() {
        // Price 95 (below par), 1-year maturity, 10% coupon → both the
        // coupon income AND the price gain (5%) contribute to yield, so
        // YTM should be >10%.
        LocalDate maturity = today.plusYears(1);
        BigDecimal ytm = EvdsBondYieldFetcher.computeYtm(
                new BigDecimal("95"),
                new BigDecimal("10"),
                maturity,
                today);
        assertThat(ytm).isNotNull();
        assertThat(ytm.doubleValue()).isGreaterThan(10.0);
        // Order of magnitude sanity: (10 + 5/1) / 97.5 × 100 ≈ 15.38
        assertThat(ytm.doubleValue()).isCloseTo(15.38, offset(0.5));
    }

    @Test
    void premium_bond_yields_below_coupon() {
        // Price 110, coupon 10%, 1-year → price loses 10/110 over the
        // year so the realised yield is below the coupon.
        LocalDate maturity = today.plusYears(1);
        BigDecimal ytm = EvdsBondYieldFetcher.computeYtm(
                new BigDecimal("110"),
                new BigDecimal("10"),
                maturity,
                today);
        assertThat(ytm).isNotNull();
        assertThat(ytm.doubleValue()).isLessThan(10.0);
    }

    @Test
    void par_bond_ytm_equals_coupon() {
        // Price == face value → YTM ≈ coupon (small float wiggle).
        LocalDate maturity = today.plusYears(1);
        BigDecimal ytm = EvdsBondYieldFetcher.computeYtm(
                new BigDecimal("100"),
                new BigDecimal("12"),
                maturity,
                today);
        assertThat(ytm).isNotNull();
        assertThat(ytm.doubleValue()).isCloseTo(12.0, offset(0.01));
    }

    @Test
    void zero_coupon_bond_yields_from_price_gain_alone() {
        // No coupon, price 80, 2 years → (0 + (100-80)/2) / ((100+80)/2) × 100
        // = 10 / 90 × 100 = 11.11
        LocalDate maturity = today.plusYears(2);
        BigDecimal ytm = EvdsBondYieldFetcher.computeYtm(
                new BigDecimal("80"),
                BigDecimal.ZERO,
                maturity,
                today);
        assertThat(ytm.doubleValue()).isCloseTo(11.11, offset(0.05));
    }

    @Test
    void null_coupon_treated_as_zero() {
        LocalDate maturity = today.plusYears(2);
        BigDecimal ytm1 = EvdsBondYieldFetcher.computeYtm(
                new BigDecimal("80"), null, maturity, today);
        BigDecimal ytm2 = EvdsBondYieldFetcher.computeYtm(
                new BigDecimal("80"), BigDecimal.ZERO, maturity, today);
        assertThat(ytm1.doubleValue()).isCloseTo(ytm2.doubleValue(), offset(1e-9));
    }

    @Test
    void matured_bond_returns_null() {
        // Maturity in the past — formula would divide by negative years
        // and produce nonsense, so the service short-circuits.
        BigDecimal ytm = EvdsBondYieldFetcher.computeYtm(
                new BigDecimal("100"),
                new BigDecimal("10"),
                today.minusDays(1),
                today);
        assertThat(ytm).isNull();
    }

    @Test
    void null_price_returns_null() {
        BigDecimal ytm = EvdsBondYieldFetcher.computeYtm(
                null, new BigDecimal("10"), today.plusYears(1), today);
        assertThat(ytm).isNull();
    }

    @Test
    void zero_price_returns_null() {
        BigDecimal ytm = EvdsBondYieldFetcher.computeYtm(
                BigDecimal.ZERO, new BigDecimal("10"), today.plusYears(1), today);
        assertThat(ytm).isNull();
    }

    @Test
    void result_is_rounded_to_two_decimals() {
        BigDecimal ytm = EvdsBondYieldFetcher.computeYtm(
                new BigDecimal("95"),
                new BigDecimal("10"),
                today.plusYears(1),
                today);
        // The service rounds with HALF_UP to 2 decimals, so the BigDecimal
        // scale must be exactly 2.
        assertThat(ytm.scale()).isEqualTo(2);
    }

    // ── Proper (clean-price, cash-flow) YTM ────────────────────────────────

    private final LocalDate issue = LocalDate.of(2020, 1, 15);
    private final LocalDate maturity = LocalDate.of(2030, 1, 15);

    @Test
    void proper_par_bond_on_coupon_date_yields_near_coupon() {
        // today is exactly a semi-annual coupon date → accrued ≈ 0, so the
        // dirty price IS the clean price. A par bond should yield ≈ coupon.
        LocalDate onCoupon = LocalDate.of(2026, 1, 15);
        var r = EvdsBondYieldFetcher.computeProperYtm(
                new BigDecimal("100"), new BigDecimal("10"), issue, maturity, onCoupon);
        assertThat(r).isNotNull();
        assertThat(r.cleanPrice().doubleValue()).isCloseTo(100.0, offset(0.5));
        assertThat(r.accrued().doubleValue()).isCloseTo(0.0, offset(0.2));
        assertThat(r.ytm().doubleValue()).isBetween(9.0, 11.0);
    }

    @Test
    void proper_discount_bond_yields_above_coupon() {
        LocalDate onCoupon = LocalDate.of(2026, 1, 15);
        var r = EvdsBondYieldFetcher.computeProperYtm(
                new BigDecimal("85"), new BigDecimal("10"), issue, maturity, onCoupon);
        assertThat(r).isNotNull();
        assertThat(r.ytm().doubleValue()).isGreaterThan(10.0);
    }

    @Test
    void proper_premium_bond_yields_below_coupon() {
        LocalDate onCoupon = LocalDate.of(2026, 1, 15);
        var r = EvdsBondYieldFetcher.computeProperYtm(
                new BigDecimal("120"), new BigDecimal("10"), issue, maturity, onCoupon);
        assertThat(r).isNotNull();
        assertThat(r.ytm().doubleValue()).isLessThan(10.0);
    }

    @Test
    void proper_accrued_is_stripped_mid_period() {
        // Halfway between two coupons (~3 months in) accrued should be ~1/4 of
        // the semi-annual coupon (10%/2 = 5 → ~2.5 accrued at the midpoint),
        // so clean price is meaningfully below the dirty price.
        LocalDate midPeriod = LocalDate.of(2026, 4, 15); // 3 months after 2026-01-15
        var r = EvdsBondYieldFetcher.computeProperYtm(
                new BigDecimal("105"), new BigDecimal("10"), issue, maturity, midPeriod);
        assertThat(r).isNotNull();
        assertThat(r.accrued().doubleValue()).isBetween(2.0, 3.0);
        assertThat(r.cleanPrice().doubleValue()).isLessThan(105.0);
    }

    @Test
    void proper_matured_bond_returns_null() {
        var r = EvdsBondYieldFetcher.computeProperYtm(
                new BigDecimal("100"), new BigDecimal("10"), issue, today.minusDays(1), today);
        assertThat(r).isNull();
    }

    @Test
    void proper_null_or_zero_price_returns_null() {
        assertThat(EvdsBondYieldFetcher.computeProperYtm(
                null, new BigDecimal("10"), issue, maturity, today)).isNull();
        assertThat(EvdsBondYieldFetcher.computeProperYtm(
                BigDecimal.ZERO, new BigDecimal("10"), issue, maturity, today)).isNull();
    }
}

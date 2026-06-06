package com.finansportali.backend.service.viop;

import com.finansportali.backend.entity.ViopContract;
import com.finansportali.backend.entity.ViopDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supplemental branch-coverage tests for {@link ViopCalculationService}.
 *
 * <p>This is a pure calculation service with no injected collaborators, so no
 * Mockito mocks are required; the service is instantiated directly (mirroring
 * the sibling {@code ViopCalculationServiceTest}). These tests target the
 * guard / switch / direction branches the spec-pinning test leaves uncovered:
 * the null category guard and the FX_USD / METAL_TRY / METAL contract-size
 * arms, every {@code currencyFor} arm, all null-guard early returns, the
 * initialMargin zero/negative/null fallbacks in requiredMargin, the leverage
 * null/zero/negative-margin returns, and the LONG vs SHORT unrealized P/L sides.
 */
class ViopCalculationServiceCoverageTest {

    private final ViopCalculationService calc = new ViopCalculationService();

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    // ---------------------------------------------------------------------
    // contractSizeFor: null guard + the arms not exercised by the base test
    // ---------------------------------------------------------------------

    @Test
    void contractSize_null_category_defaults_to_one() {
        assertThat(ViopCalculationService.contractSizeFor(null)).isEqualTo(1);
    }

    @Test
    void contractSize_fx_usd_is_1000() {
        assertThat(ViopCalculationService.contractSizeFor(ViopContract.Category.FX_USD)).isEqualTo(1000);
    }

    @Test
    void contractSize_metal_try_is_one() {
        assertThat(ViopCalculationService.contractSizeFor(ViopContract.Category.METAL_TRY)).isEqualTo(1);
    }

    @Test
    void contractSize_metal_is_one() {
        assertThat(ViopCalculationService.contractSizeFor(ViopContract.Category.METAL)).isEqualTo(1);
    }

    // ---------------------------------------------------------------------
    // currencyFor: BIST VİOP is TRY-settled, so EVERY category → TRY
    // ---------------------------------------------------------------------

    @Test
    void currency_fx_usd_is_try() {
        assertThat(ViopCalculationService.currencyFor(ViopContract.Category.FX_USD)).isEqualTo("TRY");
    }

    @Test
    void currency_metal_usd_is_try() {
        assertThat(ViopCalculationService.currencyFor(ViopContract.Category.METAL_USD)).isEqualTo("TRY");
    }

    @Test
    void currency_try_quoted_categories_default_to_try() {
        assertThat(ViopCalculationService.currencyFor(ViopContract.Category.STOCK)).isEqualTo("TRY");
        assertThat(ViopCalculationService.currencyFor(ViopContract.Category.INDEX)).isEqualTo("TRY");
        assertThat(ViopCalculationService.currencyFor(ViopContract.Category.FX_TRY)).isEqualTo("TRY");
        assertThat(ViopCalculationService.currencyFor(ViopContract.Category.METAL_TRY)).isEqualTo("TRY");
        assertThat(ViopCalculationService.currencyFor(ViopContract.Category.METAL)).isEqualTo("TRY");
    }

    @Test
    void currency_null_category_defaults_to_try() {
        assertThat(ViopCalculationService.currencyFor(null)).isEqualTo("TRY");
    }

    // ---------------------------------------------------------------------
    // positionSize: null-guard early return
    // ---------------------------------------------------------------------

    @Test
    void positionSize_null_entryPrice_returns_zero() {
        assertThat(calc.positionSize(null, bd("10"), bd("2"))).isEqualByComparingTo("0");
    }

    @Test
    void positionSize_null_contractSize_returns_zero() {
        assertThat(calc.positionSize(bd("12000"), null, bd("2"))).isEqualByComparingTo("0");
    }

    @Test
    void positionSize_null_qty_returns_zero() {
        assertThat(calc.positionSize(bd("12000"), bd("10"), null)).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------------
    // requiredMargin: initialMargin present-positive vs zero/negative/null
    // qty-null fallback, and the rate-path null guard.
    // ---------------------------------------------------------------------

    @Test
    void requiredMargin_zero_initialMargin_falls_back_to_rate() {
        // initialMargin signum == 0 -> rate path: 240.000 × 0.10 = 24.000
        assertThat(calc.requiredMargin(bd("240000"), bd("0.10"), BigDecimal.ZERO, bd("2")))
                .isEqualByComparingTo("24000");
    }

    @Test
    void requiredMargin_negative_initialMargin_falls_back_to_rate() {
        // initialMargin signum < 0 -> rate path
        assertThat(calc.requiredMargin(bd("240000"), bd("0.10"), bd("-5000"), bd("2")))
                .isEqualByComparingTo("24000");
    }

    @Test
    void requiredMargin_positive_initialMargin_but_null_qty_falls_back_to_rate() {
        // initialMargin > 0 but qty == null -> rate path (not initial-margin path)
        assertThat(calc.requiredMargin(bd("240000"), bd("0.10"), bd("5000"), null))
                .isEqualByComparingTo("24000");
    }

    @Test
    void requiredMargin_null_positionSize_in_rate_path_returns_zero() {
        assertThat(calc.requiredMargin(null, bd("0.10"), null, bd("2")))
                .isEqualByComparingTo("0");
    }

    @Test
    void requiredMargin_null_marginRate_in_rate_path_returns_zero() {
        assertThat(calc.requiredMargin(bd("240000"), null, null, bd("2")))
                .isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------------
    // leverage: null inputs and margin <= 0 all yield null
    // ---------------------------------------------------------------------

    @Test
    void leverage_null_positionSize_returns_null() {
        assertThat(calc.leverage(null, bd("24000"))).isNull();
    }

    @Test
    void leverage_null_requiredMargin_returns_null() {
        assertThat(calc.leverage(bd("240000"), null)).isNull();
    }

    @Test
    void leverage_zero_requiredMargin_returns_null() {
        assertThat(calc.leverage(bd("240000"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void leverage_negative_requiredMargin_returns_null() {
        assertThat(calc.leverage(bd("240000"), bd("-1"))).isNull();
    }

    // ---------------------------------------------------------------------
    // unrealizedPnl: every null-guard arm + SHORT direction branch + loss
    // ---------------------------------------------------------------------

    @Test
    void unrealizedPnl_null_direction_returns_zero() {
        assertThat(calc.unrealizedPnl(null, bd("12000"), bd("12300"), bd("10"), bd("2")))
                .isEqualByComparingTo("0");
    }

    @Test
    void unrealizedPnl_null_entryPrice_returns_zero() {
        assertThat(calc.unrealizedPnl(ViopDirection.LONG, null, bd("12300"), bd("10"), bd("2")))
                .isEqualByComparingTo("0");
    }

    @Test
    void unrealizedPnl_null_currentPrice_returns_zero() {
        assertThat(calc.unrealizedPnl(ViopDirection.LONG, bd("12000"), null, bd("10"), bd("2")))
                .isEqualByComparingTo("0");
    }

    @Test
    void unrealizedPnl_null_contractSize_returns_zero() {
        assertThat(calc.unrealizedPnl(ViopDirection.LONG, bd("12000"), bd("12300"), null, bd("2")))
                .isEqualByComparingTo("0");
    }

    @Test
    void unrealizedPnl_null_qty_returns_zero() {
        assertThat(calc.unrealizedPnl(ViopDirection.LONG, bd("12000"), bd("12300"), bd("10"), null))
                .isEqualByComparingTo("0");
    }

    @Test
    void unrealizedPnl_short_profit_uses_entry_minus_current() {
        // SHORT: (12.000 − 11.700) × 10 × 2 = 6.000
        assertThat(calc.unrealizedPnl(ViopDirection.SHORT, bd("12000"), bd("11700"), bd("10"), bd("2")))
                .isEqualByComparingTo("6000");
    }

    @Test
    void unrealizedPnl_long_loss_uses_current_minus_entry() {
        // LONG: (11.700 − 12.000) × 10 × 2 = −6.000
        assertThat(calc.unrealizedPnl(ViopDirection.LONG, bd("12000"), bd("11700"), bd("10"), bd("2")))
                .isEqualByComparingTo("-6000");
    }

    // ---------------------------------------------------------------------
    // realizedPnl: delegate also exercises the SHORT side & a null-guard arm
    // ---------------------------------------------------------------------

    @Test
    void realizedPnl_null_direction_returns_zero() {
        assertThat(calc.realizedPnl(null, bd("12000"), bd("12300"), bd("10"), bd("2")))
                .isEqualByComparingTo("0");
    }

    @Test
    void realizedPnl_long_loss() {
        // LONG: (11.700 − 12.000) × 10 × 2 = −6.000
        assertThat(calc.realizedPnl(ViopDirection.LONG, bd("12000"), bd("11700"), bd("10"), bd("2")))
                .isEqualByComparingTo("-6000");
    }
}

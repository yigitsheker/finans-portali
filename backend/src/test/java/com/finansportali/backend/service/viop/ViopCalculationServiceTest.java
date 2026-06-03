package com.finansportali.backend.service.viop;

import com.finansportali.backend.entity.ViopContract;
import com.finansportali.backend.entity.ViopDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the VİOP math against the numeric examples in the product spec.
 */
class ViopCalculationServiceTest {

    private final ViopCalculationService calc = new ViopCalculationService();

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test
    void positionSize_matches_spec_example() {
        // 12.000 × 10 × 2 = 240.000
        assertThat(calc.positionSize(bd("12000"), bd("10"), bd("2")))
                .isEqualByComparingTo("240000");
    }

    @Test
    void long_realized_pnl_profit() {
        // (12.300 − 12.000) × 10 × 2 = 6.000
        assertThat(calc.realizedPnl(ViopDirection.LONG, bd("12000"), bd("12300"), bd("10"), bd("2")))
                .isEqualByComparingTo("6000");
    }

    @Test
    void short_realized_pnl_profit() {
        // (12.000 − 11.700) × 10 × 1 = 3.000
        assertThat(calc.realizedPnl(ViopDirection.SHORT, bd("12000"), bd("11700"), bd("10"), bd("1")))
                .isEqualByComparingTo("3000");
    }

    @Test
    void short_realized_pnl_loss() {
        // (12.000 − 12.300) × 10 × 1 = −3.000
        assertThat(calc.realizedPnl(ViopDirection.SHORT, bd("12000"), bd("12300"), bd("10"), bd("1")))
                .isEqualByComparingTo("-3000");
    }

    @Test
    void long_unrealized_pnl() {
        assertThat(calc.unrealizedPnl(ViopDirection.LONG, bd("12000"), bd("12300"), bd("10"), bd("2")))
                .isEqualByComparingTo("6000");
    }

    @Test
    void required_margin_by_rate_and_leverage() {
        BigDecimal posSize = calc.positionSize(bd("12000"), bd("10"), bd("2")); // 240.000
        BigDecimal margin = calc.requiredMargin(posSize, bd("0.10"), null, bd("2")); // 24.000
        assertThat(margin).isEqualByComparingTo("24000");
        assertThat(calc.leverage(posSize, margin)).isEqualByComparingTo("10"); // 240000/24000
    }

    @Test
    void required_margin_prefers_initial_margin_when_known() {
        // initialMargin 5.000 × 3 contracts = 15.000 (overrides the rate path)
        assertThat(calc.requiredMargin(bd("999999"), bd("0.10"), bd("5000"), bd("3")))
                .isEqualByComparingTo("15000");
    }

    @Test
    void contract_size_map_matches_categories() {
        assertThat(ViopCalculationService.contractSizeFor(ViopContract.Category.STOCK)).isEqualTo(100);
        assertThat(ViopCalculationService.contractSizeFor(ViopContract.Category.INDEX)).isEqualTo(10);
        assertThat(ViopCalculationService.contractSizeFor(ViopContract.Category.FX_TRY)).isEqualTo(1000);
        assertThat(ViopCalculationService.contractSizeFor(ViopContract.Category.METAL_USD)).isEqualTo(10);
    }
}

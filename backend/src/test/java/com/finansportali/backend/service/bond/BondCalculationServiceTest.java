package com.finansportali.backend.service.bond;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the bond/bill math against the numeric examples in the product spec.
 */
class BondCalculationServiceTest {

    private final BondCalculationService calc = new BondCalculationService();

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test
    void buy_cost_uses_dirty_price() {
        // nominal 100.000, dirty 97,70 → 100.000 × 97,70/100 = 97.700
        BigDecimal dirty = calc.dirtyFrom(bd("96.50"), bd("1.20")); // 97.70
        assertThat(dirty).isEqualByComparingTo("97.70");
        assertThat(calc.amountAtPrice(bd("100000"), dirty)).isEqualByComparingTo("97700.00");
    }

    @Test
    void weighted_average_price_after_second_buy() {
        // (97.700 + 49.250) / 150.000 × 100 = 97,9667
        assertThat(calc.weightedAvgPrice(bd("146950"), bd("150000")))
                .isEqualByComparingTo("97.9667");
    }

    @Test
    void partial_sell_profit() {
        // proportional cost = 97.700 × 40.000/100.000 = 39.080
        BigDecimal propCost = calc.proportionalCost(bd("97700"), bd("40000"), bd("100000"));
        assertThat(propCost).isEqualByComparingTo("39080.00");
        // proceeds = 40.000 × 99,00/100 = 39.600 → kar = 520
        BigDecimal proceeds = calc.amountAtPrice(bd("40000"), bd("99.00"));
        assertThat(proceeds).isEqualByComparingTo("39600.00");
        assertThat(proceeds.subtract(propCost)).isEqualByComparingTo("520.00");
    }

    @Test
    void unrealized_pnl_on_remaining_nominal() {
        // kalan 60.000: maliyet 60.000×97,70/100=58.620, değer 60.000×99,20/100=59.520 → +900
        BigDecimal cost = calc.amountAtPrice(bd("60000"), bd("97.70"));
        BigDecimal value = calc.amountAtPrice(bd("60000"), bd("99.20"));
        assertThat(cost).isEqualByComparingTo("58620.00");
        assertThat(value).isEqualByComparingTo("59520.00");
        assertThat(value.subtract(cost)).isEqualByComparingTo("900.00");
    }

    @Test
    void coupon_payment_semiannual() {
        // nominal 100.000, yıllık %20, frekans 2 → 100.000 × 0,20 / 2 = 10.000
        assertThat(calc.couponPayment(bd("100000"), bd("20"), 2))
                .isEqualByComparingTo("10000.00");
    }

    @Test
    void zero_frequency_yields_no_coupon() {
        assertThat(calc.couponPayment(bd("100000"), bd("20"), 0))
                .isEqualByComparingTo("0");
    }
}

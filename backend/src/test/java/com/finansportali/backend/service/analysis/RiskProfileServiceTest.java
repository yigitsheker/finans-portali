package com.finansportali.backend.service.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-function unit tests for {@link RiskProfileService}. Covers every
 * category branch, the null-handling paths, and the volatility bump-up rule.
 */
class RiskProfileServiceTest {

    private final RiskProfileService service = new RiskProfileService();

    @Test
    void null_category_defaults_to_medium() {
        assertThat(service.classify(null, null)).isEqualTo(RiskProfileService.MEDIUM);
        assertThat(service.classify(null, new BigDecimal("999"))).isEqualTo(RiskProfileService.MEDIUM);
    }

    @Test
    void inflation_tr_is_low_risk() {
        assertThat(service.classify("INFLATION_TR", null)).isEqualTo(RiskProfileService.LOW);
    }

    @Test
    void inflation_us_is_low_risk() {
        assertThat(service.classify("INFLATION_US", null)).isEqualTo(RiskProfileService.LOW);
    }

    @Test
    void bond_is_low_risk() {
        assertThat(service.classify("BOND", null)).isEqualTo(RiskProfileService.LOW);
    }

    @Test
    void fund_fx_commodity_stock_default_to_medium() {
        assertThat(service.classify("FUND", null)).isEqualTo(RiskProfileService.MEDIUM);
        assertThat(service.classify("FX", null)).isEqualTo(RiskProfileService.MEDIUM);
        assertThat(service.classify("COMMODITY", null)).isEqualTo(RiskProfileService.MEDIUM);
        assertThat(service.classify("STOCK", null)).isEqualTo(RiskProfileService.MEDIUM);
    }

    @Test
    void crypto_is_high_risk() {
        assertThat(service.classify("CRYPTO", null)).isEqualTo(RiskProfileService.HIGH);
    }

    @Test
    void unknown_category_defaults_to_medium() {
        assertThat(service.classify("FOOBAR", null)).isEqualTo(RiskProfileService.MEDIUM);
    }

    @Test
    void low_bumps_to_medium_when_volatility_above_60_percent() {
        // BOND is base LOW; 70% yearly move bumps it up.
        assertThat(service.classify("BOND", new BigDecimal("70"))).isEqualTo(RiskProfileService.MEDIUM);
        assertThat(service.classify("BOND", new BigDecimal("-70"))).isEqualTo(RiskProfileService.MEDIUM);
    }

    @Test
    void medium_bumps_to_high_when_volatility_above_60_percent() {
        assertThat(service.classify("STOCK", new BigDecimal("80"))).isEqualTo(RiskProfileService.HIGH);
        assertThat(service.classify("FUND", new BigDecimal("-65"))).isEqualTo(RiskProfileService.HIGH);
    }

    @Test
    void high_crypto_stays_high_under_high_volatility() {
        // Boundary: CRYPTO is already HIGH so the bump rule doesn't apply.
        assertThat(service.classify("CRYPTO", new BigDecimal("200"))).isEqualTo(RiskProfileService.HIGH);
    }

    @Test
    void volatility_at_or_below_60_does_not_bump() {
        // 60% exactly is NOT > 60, so base risk stands.
        assertThat(service.classify("STOCK", new BigDecimal("60"))).isEqualTo(RiskProfileService.MEDIUM);
        assertThat(service.classify("BOND", new BigDecimal("60"))).isEqualTo(RiskProfileService.LOW);
        assertThat(service.classify("STOCK", new BigDecimal("10"))).isEqualTo(RiskProfileService.MEDIUM);
    }

    @Test
    void null_change_uses_base_risk() {
        assertThat(service.classify("BOND", null)).isEqualTo(RiskProfileService.LOW);
        assertThat(service.classify("FX", null)).isEqualTo(RiskProfileService.MEDIUM);
        assertThat(service.classify("CRYPTO", null)).isEqualTo(RiskProfileService.HIGH);
    }
}

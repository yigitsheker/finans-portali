package com.finansportali.backend.service.bond;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure bond/bill math — nominal + clean/dirty price + coupon, no persistence.
 * Unit-tested against the spec examples. All prices are "per 100 nominal".
 */
@Service
public class BondCalculationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** Dirty price = clean + accrued (accrued may be null → clean). */
    public BigDecimal dirtyFrom(BigDecimal cleanPrice, BigDecimal accruedInterest) {
        if (cleanPrice == null) return null;
        return accruedInterest == null ? cleanPrice : cleanPrice.add(accruedInterest);
    }

    /** Cost/value of {@code nominal} at a price per 100. */
    public BigDecimal amountAtPrice(BigDecimal nominal, BigDecimal pricePer100) {
        if (nominal == null || pricePer100 == null) return BigDecimal.ZERO;
        return nominal.multiply(pricePer100).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Weighted-average price per 100 = totalCost / totalNominal × 100. */
    public BigDecimal weightedAvgPrice(BigDecimal totalCost, BigDecimal totalNominal) {
        if (totalCost == null || totalNominal == null || totalNominal.signum() == 0) return BigDecimal.ZERO;
        return totalCost.multiply(HUNDRED).divide(totalNominal, 4, RoundingMode.HALF_UP);
    }

    /** Cost attributable to the sold slice = totalCost × soldNominal / totalNominal. */
    public BigDecimal proportionalCost(BigDecimal totalCost, BigDecimal soldNominal, BigDecimal totalNominal) {
        if (totalCost == null || soldNominal == null || totalNominal == null || totalNominal.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return totalCost.multiply(soldNominal).divide(totalNominal, 2, RoundingMode.HALF_UP);
    }

    /** Periodic coupon = nominal × (annualCouponRate% / 100) / frequency. */
    public BigDecimal couponPayment(BigDecimal nominal, BigDecimal annualCouponRatePct, int frequency) {
        if (nominal == null || annualCouponRatePct == null || frequency <= 0) return BigDecimal.ZERO;
        return nominal.multiply(annualCouponRatePct).divide(HUNDRED, 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(frequency), 2, RoundingMode.HALF_UP);
    }
}

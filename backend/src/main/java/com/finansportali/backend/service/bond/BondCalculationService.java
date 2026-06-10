package com.finansportali.backend.service.bond;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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

    /**
     * Accrued interest <b>per 100 nominal</b> (so it adds straight onto the clean
     * price to give the dirty price), using the ACT/ACT day-count over the current
     * coupon period [prevCouponDate, nextCouponDate):
     * <pre>accrued/100 = (couponRate% / frequency) × elapsedDays / periodDays</pre>
     * Returns 0 for a zero-coupon bill or any missing/degenerate input. Elapsed is
     * clamped to the period so a settlement on/after the next coupon never exceeds
     * one full period's coupon.
     */
    public BigDecimal accruedInterest(BigDecimal annualCouponRatePct, int frequency,
                                      LocalDate prevCouponDate, LocalDate nextCouponDate, LocalDate settlement) {
        if (annualCouponRatePct == null || annualCouponRatePct.signum() <= 0 || frequency <= 0
                || prevCouponDate == null || nextCouponDate == null || settlement == null) {
            return BigDecimal.ZERO;
        }
        long periodDays = ChronoUnit.DAYS.between(prevCouponDate, nextCouponDate);
        if (periodDays <= 0) return BigDecimal.ZERO;
        long elapsed = ChronoUnit.DAYS.between(prevCouponDate, settlement);
        if (elapsed <= 0) return BigDecimal.ZERO;
        if (elapsed > periodDays) elapsed = periodDays;
        // coupon per 100 nominal for the full period = couponRate% / frequency
        return annualCouponRatePct
                .divide(BigDecimal.valueOf(frequency), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(elapsed))
                .divide(BigDecimal.valueOf(periodDays), 6, RoundingMode.HALF_UP);
    }
}

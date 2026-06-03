package com.finansportali.backend.dto.response.bond;

import java.math.BigDecimal;

/** Aggregate bond/bill portfolio metrics for a user. */
public record BondPortfolioSummary(
        int positionCount,
        BigDecimal totalNominal,
        BigDecimal currentMarketValue,
        int upcomingMaturities,
        BigDecimal expectedNextCoupons,
        BigDecimal realizedCouponIncome,
        BigDecimal totalUnrealizedPnl,
        BigDecimal totalRealizedPnl
) {}

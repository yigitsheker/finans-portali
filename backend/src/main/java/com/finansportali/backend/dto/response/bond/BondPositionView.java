package com.finansportali.backend.dto.response.bond;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read model for a bond/bill position with backend-computed valuation. */
public record BondPositionView(
        Long id,
        String isin,
        String symbol,
        String name,
        String type,
        String issuer,
        String currency,
        BigDecimal remainingNominal,
        BigDecimal avgCostPrice,
        BigDecimal remainingCost,
        BigDecimal currentCleanPrice,
        BigDecimal currentDirtyPrice,
        BigDecimal currentYield,
        BigDecimal couponRate,
        Integer couponFrequency,
        LocalDate maturityDate,
        Long daysToMaturity,
        LocalDate purchaseDate,
        BigDecimal currentValue,
        BigDecimal unrealizedPnl,
        BigDecimal realizedPnl,
        BigDecimal couponIncome,
        String status,
        boolean priceIsStale
) {}

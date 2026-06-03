package com.finansportali.backend.dto.response.bond;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read model for one bond position transaction (buy/sell/coupon/redemption).
 * Named distinctly from the market-data Bond* DTOs to avoid collision.
 */
public record BondPositionTransactionView(
        Long id,
        String isin,
        String type,
        BigDecimal nominal,
        BigDecimal cleanPrice,
        BigDecimal accruedInterest,
        BigDecimal dirtyPrice,
        BigDecimal grossAmount,
        BigDecimal realizedPnl,
        BigDecimal grossCoupon,
        BigDecimal taxAmount,
        BigDecimal netCoupon,
        Instant executedAt,
        String note
) {}

package com.finansportali.backend.dto.response.bond;

import java.math.BigDecimal;

/** Backend-computed preview of a bond buy/sell (no persistence). */
public record BondTradePreview(
        String side,
        String isin,
        BigDecimal nominal,
        BigDecimal cleanPrice,
        BigDecimal accruedInterest,
        BigDecimal dirtyPrice,
        BigDecimal totalAmount,
        BigDecimal proportionalCost,
        BigDecimal estimatedRealizedPnl,
        String note
) {}

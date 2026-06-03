package com.finansportali.backend.dto.response.viop;

import java.math.BigDecimal;

/** Aggregate VİOP metrics across a user's open positions. */
public record ViopSummary(
        int openPositionCount,
        BigDecimal totalOpenPositionSize,
        BigDecimal totalRequiredMargin,
        BigDecimal totalUnrealizedPnl,
        BigDecimal totalRealizedPnl
) {}

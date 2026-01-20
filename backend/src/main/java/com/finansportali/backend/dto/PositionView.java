package com.finansportali.backend.dto;

import java.math.BigDecimal;

public record PositionView(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgCost,
        BigDecimal lastPrice,
        BigDecimal marketValue,
        BigDecimal pnlAbs,
        BigDecimal pnlPct
) {}

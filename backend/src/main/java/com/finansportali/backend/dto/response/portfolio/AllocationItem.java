package com.finansportali.backend.dto.response.portfolio;

import java.math.BigDecimal;

public record AllocationItem(
        String symbol,
        BigDecimal marketValue,
        BigDecimal weightPct
) {}

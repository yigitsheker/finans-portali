package com.finansportali.backend.dto;

import java.math.BigDecimal;

public record AllocationItem(
        String symbol,
        BigDecimal marketValue,
        BigDecimal weightPct
) {}

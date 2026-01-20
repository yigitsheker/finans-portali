package com.finansportali.backend.dto;

import java.math.BigDecimal;

public record AllocationByTypeItem(
        String type,
        BigDecimal marketValue,
        BigDecimal weightPct
) {}

package com.finansportali.backend.dto.response.portfolio;

import java.math.BigDecimal;

public record AllocationByTypeItem(
        String type,
        BigDecimal marketValue,
        BigDecimal weightPct
) {}

package com.finansportali.backend.dto;

import java.math.BigDecimal;

public record UpsertPositionRequest(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgCost
) {}

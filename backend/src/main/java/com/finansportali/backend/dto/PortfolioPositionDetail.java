package com.finansportali.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioPositionDetail(
        String symbol,
        String name,
        BigDecimal quantity,
        LocalDate buyDate,
        BigDecimal buyPrice,
        BigDecimal currentPrice,
        BigDecimal investedAmount,
        BigDecimal currentValue,
        BigDecimal totalChangeValue,
        BigDecimal totalChangePercent,
        BigDecimal dailyChangePercent,
        BigDecimal dailyChangeValue
) {}

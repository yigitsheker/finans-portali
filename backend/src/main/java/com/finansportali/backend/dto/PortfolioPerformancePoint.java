package com.finansportali.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PortfolioPerformancePoint(
        LocalDate date,
        LocalDateTime datetime,
        BigDecimal value
) {
    // Constructor for daily data
    public PortfolioPerformancePoint(LocalDate date, BigDecimal value) {
        this(date, null, value);
    }
    
    // Constructor for intraday data
    public PortfolioPerformancePoint(LocalDateTime datetime, BigDecimal value) {
        this(null, datetime, value);
    }
}

package com.finansportali.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketHistoryPoint(
        LocalDate day,
        BigDecimal close,
        String label,       // human-readable label for chart axis (date or datetime)
        Long timestamp      // Unix timestamp in seconds for Lightweight Charts
) {
    // Backward-compat constructor for daily candles
    public MarketHistoryPoint(LocalDate day, BigDecimal close) {
        this(day, close, day.toString(),
             day.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC));
    }

    // Constructor with label but no explicit timestamp (derives from day)
    public MarketHistoryPoint(LocalDate day, BigDecimal close, String label) {
        this(day, close, label,
             day.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC));
    }
}

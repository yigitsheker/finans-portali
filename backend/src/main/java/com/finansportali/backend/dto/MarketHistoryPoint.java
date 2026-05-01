package com.finansportali.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketHistoryPoint(
        LocalDate day,
        BigDecimal close,
        String label   // human-readable label for chart axis (date or datetime)
) {
    // Backward-compat constructor for daily candles
    public MarketHistoryPoint(LocalDate day, BigDecimal close) {
        this(day, close, day.toString());
    }
}

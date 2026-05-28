package com.finansportali.backend.dto.response.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Single sparkline point for the analysis detail card. */
public class PricePointDto {
    private LocalDate date;
    private BigDecimal value;

    public PricePointDto() {}
    public PricePointDto(LocalDate date, BigDecimal value) {
        this.date = date;
        this.value = value;
    }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
}

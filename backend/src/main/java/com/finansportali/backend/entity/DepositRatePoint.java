package com.finansportali.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Monthly bank deposit interest rate point per currency.
 * One row per (period_date, currency). Sourced from TCMB EVDS3
 * via the TP.{TRY|USD|EUR}.MT{01..06} series family.
 */
@Entity
@Table(name = "deposit_rate_points",
       uniqueConstraints = @UniqueConstraint(name = "uk_deposit_rate_period_currency",
                                             columnNames = {"period_date", "currency"}))
public class DepositRatePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "rate_1m", precision = 8, scale = 4)
    private BigDecimal rate1m;

    @Column(name = "rate_3m", precision = 8, scale = 4)
    private BigDecimal rate3m;

    @Column(name = "rate_6m", precision = 8, scale = 4)
    private BigDecimal rate6m;

    @Column(name = "rate_12m", precision = 8, scale = 4)
    private BigDecimal rate12m;

    @Column(name = "rate_over_12m", precision = 8, scale = 4)
    private BigDecimal rateOver12m;

    @Column(name = "rate_avg", precision = 8, scale = 4)
    private BigDecimal rateAvg;

    @Column(name = "source", nullable = false, length = 40)
    private String source = "TCMB_EVDS3";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public DepositRatePoint() {}

    public DepositRatePoint(LocalDate periodDate, String currency) {
        this.periodDate = periodDate;
        this.currency = currency;
    }

    public Long getId() { return id; }
    public LocalDate getPeriodDate() { return periodDate; }
    public String getCurrency() { return currency; }
    public BigDecimal getRate1m() { return rate1m; }
    public BigDecimal getRate3m() { return rate3m; }
    public BigDecimal getRate6m() { return rate6m; }
    public BigDecimal getRate12m() { return rate12m; }
    public BigDecimal getRateOver12m() { return rateOver12m; }
    public BigDecimal getRateAvg() { return rateAvg; }
    public String getSource() { return source; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setPeriodDate(LocalDate periodDate) { this.periodDate = periodDate; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setRate1m(BigDecimal v) { this.rate1m = v; }
    public void setRate3m(BigDecimal v) { this.rate3m = v; }
    public void setRate6m(BigDecimal v) { this.rate6m = v; }
    public void setRate12m(BigDecimal v) { this.rate12m = v; }
    public void setRateOver12m(BigDecimal v) { this.rateOver12m = v; }
    public void setRateAvg(BigDecimal v) { this.rateAvg = v; }
    public void setSource(String source) { this.source = source; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

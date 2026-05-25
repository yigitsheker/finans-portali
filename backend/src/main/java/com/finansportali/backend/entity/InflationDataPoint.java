package com.finansportali.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Monthly Turkey inflation data point. Sourced from TCMB EVDS3
 * (TÜFE: TP.FE.OKTG01 / TP.FG.J0, Yİ-ÜFE: TP.UFE.S01).
 *
 * One row per calendar month; {@code periodDate} is normalised to the first of that month.
 */
@Entity
@Table(name = "inflation_data_points")
public class InflationDataPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    /**
     * ISO 3166-1 alpha-2 country code. {@code "TR"} for TCMB-sourced
     * Turkish CPI, {@code "US"} for FRED CPIAUCSL. The natural key is
     * the (period_date, country) pair — see V20 migration.
     */
    @Column(name = "country", nullable = false, length = 2)
    private String country = "TR";

    @Column(name = "cpi_index", precision = 15, scale = 4)
    private BigDecimal cpiIndex;

    @Column(name = "cpi_yearly_change", precision = 8, scale = 4)
    private BigDecimal cpiYearlyChange;

    @Column(name = "cpi_monthly_change", precision = 8, scale = 4)
    private BigDecimal cpiMonthlyChange;

    @Column(name = "ppi_index", precision = 15, scale = 4)
    private BigDecimal ppiIndex;

    @Column(name = "ppi_yearly_change", precision = 8, scale = 4)
    private BigDecimal ppiYearlyChange;

    @Column(name = "source", nullable = false, length = 40)
    private String source = "TCMB_EVDS3";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public InflationDataPoint() {}

    public InflationDataPoint(LocalDate periodDate) {
        this.periodDate = periodDate;
    }

    public InflationDataPoint(LocalDate periodDate, String country) {
        this.periodDate = periodDate;
        this.country = country;
    }

    public Long getId() { return id; }
    public LocalDate getPeriodDate() { return periodDate; }
    public String getCountry() { return country; }
    public BigDecimal getCpiIndex() { return cpiIndex; }
    public BigDecimal getCpiYearlyChange() { return cpiYearlyChange; }
    public BigDecimal getCpiMonthlyChange() { return cpiMonthlyChange; }
    public BigDecimal getPpiIndex() { return ppiIndex; }
    public BigDecimal getPpiYearlyChange() { return ppiYearlyChange; }
    public String getSource() { return source; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setPeriodDate(LocalDate periodDate) { this.periodDate = periodDate; }
    public void setCountry(String country) { this.country = country; }
    public void setCpiIndex(BigDecimal cpiIndex) { this.cpiIndex = cpiIndex; }
    public void setCpiYearlyChange(BigDecimal cpiYearlyChange) { this.cpiYearlyChange = cpiYearlyChange; }
    public void setCpiMonthlyChange(BigDecimal cpiMonthlyChange) { this.cpiMonthlyChange = cpiMonthlyChange; }
    public void setPpiIndex(BigDecimal ppiIndex) { this.ppiIndex = ppiIndex; }
    public void setPpiYearlyChange(BigDecimal ppiYearlyChange) { this.ppiYearlyChange = ppiYearlyChange; }
    public void setSource(String source) { this.source = source; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

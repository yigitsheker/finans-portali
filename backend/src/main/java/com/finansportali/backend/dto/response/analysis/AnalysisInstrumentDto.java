package com.finansportali.backend.dto.response.analysis;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cross-asset row for the /api/v1/analysis/instruments grid. The page mixes
 * stocks, crypto, FX, bonds, funds, inflation series and commodities, so the
 * shape is intentionally generic: {@code category} carries the asset class
 * label, currency-agnostic {@code value} carries the latest price/level, and
 * the percentage-change fields are all optional (inflation series have
 * monthly/yearly but no weekly, for example).
 */
public class AnalysisInstrumentDto {
    private String symbol;
    private String name;
    private String category;     // "STOCK", "CRYPTO", "FX", "COMMODITY", "FUND", "BOND", "INFLATION_TR", "INFLATION_US"
    private BigDecimal value;
    private String currency;
    private BigDecimal changeDaily;
    private BigDecimal changeWeekly;
    private BigDecimal changeMonthly;
    private BigDecimal changeYearly;
    // Inflation-adjusted yearly return: (1 + nominal) / (1 + inflation) - 1.
    // The inflation reference depends on the instrument's currency — TRY
    // uses TR TÜFE, anything else (USD, EUR, etc.) uses US CPI. Null when
    // either the nominal or the reference CPI is missing.
    private BigDecimal realChangeYearly;
    // Convenience flag for the "beats inflation" UX filter — true iff
    // realChangeYearly is positive. Null when we couldn't compute it.
    private Boolean beatsInflation;
    private String riskLevel;    // "LOW" | "MEDIUM" | "HIGH"
    private String shortTermSignal; // "BUY" | "HOLD" | "SELL" | "NEUTRAL"
    private String longTermSignal;
    // How strongly the composite factors agreed on the signal: "LOW" |
    // "MEDIUM" | "HIGH". Null for NEUTRAL rows (no data to score).
    private String shortTermConfidence;
    private String longTermConfidence;
    private Instant updatedAt;

    public AnalysisInstrumentDto() {
        // Default constructor required for Jackson deserialization.
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getChangeDaily() { return changeDaily; }
    public void setChangeDaily(BigDecimal changeDaily) { this.changeDaily = changeDaily; }
    public BigDecimal getChangeWeekly() { return changeWeekly; }
    public void setChangeWeekly(BigDecimal changeWeekly) { this.changeWeekly = changeWeekly; }
    public BigDecimal getChangeMonthly() { return changeMonthly; }
    public void setChangeMonthly(BigDecimal changeMonthly) { this.changeMonthly = changeMonthly; }
    public BigDecimal getChangeYearly() { return changeYearly; }
    public void setChangeYearly(BigDecimal changeYearly) { this.changeYearly = changeYearly; }
    public BigDecimal getRealChangeYearly() { return realChangeYearly; }
    public void setRealChangeYearly(BigDecimal realChangeYearly) { this.realChangeYearly = realChangeYearly; }
    public Boolean getBeatsInflation() { return beatsInflation; }
    public void setBeatsInflation(Boolean beatsInflation) { this.beatsInflation = beatsInflation; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getShortTermSignal() { return shortTermSignal; }
    public void setShortTermSignal(String shortTermSignal) { this.shortTermSignal = shortTermSignal; }
    public String getLongTermSignal() { return longTermSignal; }
    public void setLongTermSignal(String longTermSignal) { this.longTermSignal = longTermSignal; }
    public String getShortTermConfidence() { return shortTermConfidence; }
    public void setShortTermConfidence(String shortTermConfidence) { this.shortTermConfidence = shortTermConfidence; }
    public String getLongTermConfidence() { return longTermConfidence; }
    public void setLongTermConfidence(String longTermConfidence) { this.longTermConfidence = longTermConfidence; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

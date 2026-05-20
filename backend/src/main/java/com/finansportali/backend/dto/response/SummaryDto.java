package com.finansportali.backend.dto.response;

/**
 * Headline metrics shown in the summary cards.
 *
 * volatilityPercent — historical (annualised) standard deviation of daily
 *   log-returns, expressed as a %. This is the standard finance definition
 *   (σ_daily × √252 × 100), NOT the coefficient of variation the older
 *   version returned.
 *
 * rsi14Latest — latest 14-period Wilder RSI, 0..100. Null when there's
 *   <15 data points.
 */
public class SummaryDto {

    private Double latestClose;
    private Double highestClose;
    private Double lowestClose;
    private Double averageClose;
    private Double volatilityPercent;   // annualised σ of log-returns, in %
    private Double rsi14Latest;

    public SummaryDto() {}

    public SummaryDto(Double latestClose, Double highestClose, Double lowestClose,
                      Double averageClose, Double volatilityPercent, Double rsi14Latest) {
        this.latestClose = latestClose;
        this.highestClose = highestClose;
        this.lowestClose = lowestClose;
        this.averageClose = averageClose;
        this.volatilityPercent = volatilityPercent;
        this.rsi14Latest = rsi14Latest;
    }

    // Back-compat ctor for callers that don't yet pass RSI.
    public SummaryDto(Double latestClose, Double highestClose, Double lowestClose,
                      Double averageClose, Double volatilityPercent) {
        this(latestClose, highestClose, lowestClose, averageClose, volatilityPercent, null);
    }

    public Double getLatestClose() { return latestClose; }
    public void setLatestClose(Double latestClose) { this.latestClose = latestClose; }
    public Double getHighestClose() { return highestClose; }
    public void setHighestClose(Double highestClose) { this.highestClose = highestClose; }
    public Double getLowestClose() { return lowestClose; }
    public void setLowestClose(Double lowestClose) { this.lowestClose = lowestClose; }
    public Double getAverageClose() { return averageClose; }
    public void setAverageClose(Double averageClose) { this.averageClose = averageClose; }
    public Double getVolatilityPercent() { return volatilityPercent; }
    public void setVolatilityPercent(Double volatilityPercent) { this.volatilityPercent = volatilityPercent; }
    public Double getRsi14Latest() { return rsi14Latest; }
    public void setRsi14Latest(Double rsi14Latest) { this.rsi14Latest = rsi14Latest; }
}

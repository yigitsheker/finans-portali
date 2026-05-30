package com.finansportali.backend.dto.response.analysis;

import java.util.List;

/**
 * Wider payload served by GET /api/v1/analysis/instruments/{symbol} when a
 * row is clicked. Embeds the basic summary row plus narrative analysis
 * (short/long-term commentary, risk note, trend description) and an
 * optional sparkline-style series of recent price points.
 */
public class AnalysisDetailDto {
    private AnalysisInstrumentDto summary;
    private String trend;            // "UP" | "DOWN" | "SIDEWAYS"
    private String volatility;       // "LOW" | "MEDIUM" | "HIGH"
    private String shortTermNote;
    private String longTermNote;
    private String riskNote;
    private List<PricePointDto> series;

    public AnalysisDetailDto() {
        // Default constructor required for Jackson deserialization.
    }

    public AnalysisInstrumentDto getSummary() { return summary; }
    public void setSummary(AnalysisInstrumentDto summary) { this.summary = summary; }
    public String getTrend() { return trend; }
    public void setTrend(String trend) { this.trend = trend; }
    public String getVolatility() { return volatility; }
    public void setVolatility(String volatility) { this.volatility = volatility; }
    public String getShortTermNote() { return shortTermNote; }
    public void setShortTermNote(String shortTermNote) { this.shortTermNote = shortTermNote; }
    public String getLongTermNote() { return longTermNote; }
    public void setLongTermNote(String longTermNote) { this.longTermNote = longTermNote; }
    public String getRiskNote() { return riskNote; }
    public void setRiskNote(String riskNote) { this.riskNote = riskNote; }
    public List<PricePointDto> getSeries() { return series; }
    public void setSeries(List<PricePointDto> series) { this.series = series; }
}

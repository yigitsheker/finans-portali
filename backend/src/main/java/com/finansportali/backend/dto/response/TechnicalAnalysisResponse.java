package com.finansportali.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public class TechnicalAnalysisResponse {
    
    private String symbol;
    private String from;
    private String to;
    private TrendDto trend;
    private SummaryDto summary;
    private List<SeriesPointDto> series;
    
    public TechnicalAnalysisResponse() {}
    
    public TechnicalAnalysisResponse(String symbol, String from, String to, TrendDto trend, 
                                    SummaryDto summary, List<SeriesPointDto> series) {
        this.symbol = symbol;
        this.from = from;
        this.to = to;
        this.trend = trend;
        this.summary = summary;
        this.series = series;
    }
    
    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    
    public TrendDto getTrend() { return trend; }
    public void setTrend(TrendDto trend) { this.trend = trend; }
    
    public SummaryDto getSummary() { return summary; }
    public void setSummary(SummaryDto summary) { this.summary = summary; }
    
    public List<SeriesPointDto> getSeries() { return series; }
    public void setSeries(List<SeriesPointDto> series) { this.series = series; }
}

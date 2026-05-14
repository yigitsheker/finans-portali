package com.finansportali.backend.dto.response;

public class SummaryDto {
    
    private Double latestClose;
    private Double highestClose;
    private Double lowestClose;
    private Double averageClose;
    private Double volatilityPercent;
    
    public SummaryDto() {}
    
    public SummaryDto(Double latestClose, Double highestClose, Double lowestClose, 
                     Double averageClose, Double volatilityPercent) {
        this.latestClose = latestClose;
        this.highestClose = highestClose;
        this.lowestClose = lowestClose;
        this.averageClose = averageClose;
        this.volatilityPercent = volatilityPercent;
    }
    
    // Getters and Setters
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
}

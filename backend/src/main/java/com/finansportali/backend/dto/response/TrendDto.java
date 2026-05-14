package com.finansportali.backend.dto.response;

public class TrendDto {
    
    private String direction; // UPWARD, DOWNWARD, SIDEWAYS, INSUFFICIENT_DATA
    private Double slope;
    private Double changePercent;
    private String description;
    
    public TrendDto() {}
    
    public TrendDto(String direction, Double slope, Double changePercent, String description) {
        this.direction = direction;
        this.slope = slope;
        this.changePercent = changePercent;
        this.description = description;
    }
    
    // Getters and Setters
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    
    public Double getSlope() { return slope; }
    public void setSlope(Double slope) { this.slope = slope; }
    
    public Double getChangePercent() { return changePercent; }
    public void setChangePercent(Double changePercent) { this.changePercent = changePercent; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

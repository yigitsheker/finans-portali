package com.finansportali.backend.dto.response;

public class SeriesPointDto {
    
    private String date;
    private Double close;
    private Double sma7;
    private Double sma20;
    private Double sma50;
    
    public SeriesPointDto() {}
    
    public SeriesPointDto(String date, Double close, Double sma7, Double sma20, Double sma50) {
        this.date = date;
        this.close = close;
        this.sma7 = sma7;
        this.sma20 = sma20;
        this.sma50 = sma50;
    }
    
    // Getters and Setters
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    
    public Double getClose() { return close; }
    public void setClose(Double close) { this.close = close; }
    
    public Double getSma7() { return sma7; }
    public void setSma7(Double sma7) { this.sma7 = sma7; }
    
    public Double getSma20() { return sma20; }
    public void setSma20(Double sma20) { this.sma20 = sma20; }
    
    public Double getSma50() { return sma50; }
    public void setSma50(Double sma50) { this.sma50 = sma50; }
}

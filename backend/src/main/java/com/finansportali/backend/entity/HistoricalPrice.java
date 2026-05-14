package com.finansportali.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "historical_prices", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "price_date"}),
       indexes = {
           @Index(name = "idx_historical_symbol_date", columnList = "symbol,price_date"),
           @Index(name = "idx_historical_date", columnList = "price_date")
       })
public class HistoricalPrice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 30)
    private String symbol;
    
    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;
    
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal closePrice;
    
    @Column(precision = 19, scale = 6)
    private BigDecimal adjustedClosePrice;
    
    @Column(precision = 19, scale = 6)
    private BigDecimal openPrice;
    
    @Column(precision = 19, scale = 6)
    private BigDecimal highPrice;
    
    @Column(precision = 19, scale = 6)
    private BigDecimal lowPrice;
    
    @Column(precision = 19, scale = 0)
    private Long volume;
    
    public HistoricalPrice() {}
    
    public HistoricalPrice(String symbol, LocalDate priceDate, BigDecimal closePrice) {
        this.symbol = symbol;
        this.priceDate = priceDate;
        this.closePrice = closePrice;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public LocalDate getPriceDate() { return priceDate; }
    public void setPriceDate(LocalDate priceDate) { this.priceDate = priceDate; }
    
    public BigDecimal getClosePrice() { return closePrice; }
    public void setClosePrice(BigDecimal closePrice) { this.closePrice = closePrice; }
    
    public BigDecimal getAdjustedClosePrice() { return adjustedClosePrice; }
    public void setAdjustedClosePrice(BigDecimal adjustedClosePrice) { this.adjustedClosePrice = adjustedClosePrice; }
    
    public BigDecimal getOpenPrice() { return openPrice; }
    public void setOpenPrice(BigDecimal openPrice) { this.openPrice = openPrice; }
    
    public BigDecimal getHighPrice() { return highPrice; }
    public void setHighPrice(BigDecimal highPrice) { this.highPrice = highPrice; }
    
    public BigDecimal getLowPrice() { return lowPrice; }
    public void setLowPrice(BigDecimal lowPrice) { this.lowPrice = lowPrice; }
    
    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }
}

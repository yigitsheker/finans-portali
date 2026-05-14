package com.finansportali.backend.dto.response.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PortfolioPositionDetail {
    private String symbol;
    private String name;
    private String type;
    private String currency;
    private BigDecimal quantity;
    private LocalDate buyDate;
    private BigDecimal buyPrice;
    private BigDecimal currentPrice;
    private BigDecimal investedAmount;
    private BigDecimal currentValue;
    private BigDecimal totalChangeValue;
    private BigDecimal totalChangePercent;
    private BigDecimal dailyChangePercent;
    private BigDecimal dailyChangeValue;

    public PortfolioPositionDetail(String symbol, String name, String type, String currency,
                                    BigDecimal quantity, LocalDate buyDate, BigDecimal buyPrice,
                                    BigDecimal currentPrice, BigDecimal investedAmount, BigDecimal currentValue,
                                    BigDecimal totalChangeValue, BigDecimal totalChangePercent,
                                    BigDecimal dailyChangePercent, BigDecimal dailyChangeValue) {
        this.symbol = symbol;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.quantity = quantity;
        this.buyDate = buyDate;
        this.buyPrice = buyPrice;
        this.currentPrice = currentPrice;
        this.investedAmount = investedAmount;
        this.currentValue = currentValue;
        this.totalChangeValue = totalChangeValue;
        this.totalChangePercent = totalChangePercent;
        this.dailyChangePercent = dailyChangePercent;
        this.dailyChangeValue = dailyChangeValue;
    }

    // Getters
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getCurrency() { return currency; }
    public BigDecimal getQuantity() { return quantity; }
    public LocalDate getBuyDate() { return buyDate; }
    public BigDecimal getBuyPrice() { return buyPrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getInvestedAmount() { return investedAmount; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public BigDecimal getTotalChangeValue() { return totalChangeValue; }
    public BigDecimal getTotalChangePercent() { return totalChangePercent; }
    public BigDecimal getDailyChangePercent() { return dailyChangePercent; }
    public BigDecimal getDailyChangeValue() { return dailyChangeValue; }
}

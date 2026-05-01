package com.finansportali.backend.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String currencyCode; // USD, EUR, GBP, etc.

    @Column(nullable = false, length = 50)
    private String currencyName; // US Dollar, Euro, etc.

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal buyingRate;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal sellingRate;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal effectiveBuyingRate;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal effectiveSellingRate;

    @Column(nullable = false)
    private LocalDate rateDate;

    @Column(nullable = false, length = 50)
    private String source; // TCMB, Garanti, İş Bankası, etc.

    public ExchangeRate() {
    }

    public ExchangeRate(String currencyCode, String currencyName, BigDecimal buyingRate, 
                       BigDecimal sellingRate, BigDecimal effectiveBuyingRate, 
                       BigDecimal effectiveSellingRate, LocalDate rateDate, String source) {
        this.currencyCode = currencyCode;
        this.currencyName = currencyName;
        this.buyingRate = buyingRate;
        this.sellingRate = sellingRate;
        this.effectiveBuyingRate = effectiveBuyingRate;
        this.effectiveSellingRate = effectiveSellingRate;
        this.rateDate = rateDate;
        this.source = source;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getCurrencyCode() { return currencyCode; }
    public String getCurrencyName() { return currencyName; }
    public BigDecimal getBuyingRate() { return buyingRate; }
    public BigDecimal getSellingRate() { return sellingRate; }
    public BigDecimal getEffectiveBuyingRate() { return effectiveBuyingRate; }
    public BigDecimal getEffectiveSellingRate() { return effectiveSellingRate; }
    public LocalDate getRateDate() { return rateDate; }
    public String getSource() { return source; }

    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setCurrencyName(String currencyName) { this.currencyName = currencyName; }
    public void setBuyingRate(BigDecimal buyingRate) { this.buyingRate = buyingRate; }
    public void setSellingRate(BigDecimal sellingRate) { this.sellingRate = sellingRate; }
    public void setEffectiveBuyingRate(BigDecimal effectiveBuyingRate) { this.effectiveBuyingRate = effectiveBuyingRate; }
    public void setEffectiveSellingRate(BigDecimal effectiveSellingRate) { this.effectiveSellingRate = effectiveSellingRate; }
    public void setRateDate(LocalDate rateDate) { this.rateDate = rateDate; }
    public void setSource(String source) { this.source = source; }
}
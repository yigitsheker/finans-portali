package com.finansportali.backend.dto.response.bond;

import com.finansportali.backend.entity.DebtInstrumentType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tahvil/bono verisi DTO.
 * Veri sağlayıcılardan gelen ham veriyi temsil eder.
 */
public class BondQuoteDto {
    private String symbol;
    private String isin;
    private String name;
    private DebtInstrumentType type;
    private String issuer;
    private String currency;
    private LocalDate maturityDate;
    private BigDecimal couponRate;
    private String couponType;
    
    // Quote data
    private LocalDate quoteDate;
    private BigDecimal price;
    private BigDecimal yieldRate;
    private BigDecimal cleanPrice;
    private BigDecimal dirtyPrice;
    private BigDecimal volume;
    private BigDecimal changeRate;
    private String source;

    // Constructors
    public BondQuoteDto() {}

    public BondQuoteDto(String symbol, String name, DebtInstrumentType type) {
        this.symbol = symbol;
        this.name = name;
        this.type = type;
    }

    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public DebtInstrumentType getType() { return type; }
    public void setType(DebtInstrumentType type) { this.type = type; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }

    public BigDecimal getCouponRate() { return couponRate; }
    public void setCouponRate(BigDecimal couponRate) { this.couponRate = couponRate; }

    public String getCouponType() { return couponType; }
    public void setCouponType(String couponType) { this.couponType = couponType; }

    public LocalDate getQuoteDate() { return quoteDate; }
    public void setQuoteDate(LocalDate quoteDate) { this.quoteDate = quoteDate; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getYieldRate() { return yieldRate; }
    public void setYieldRate(BigDecimal yieldRate) { this.yieldRate = yieldRate; }

    public BigDecimal getCleanPrice() { return cleanPrice; }
    public void setCleanPrice(BigDecimal cleanPrice) { this.cleanPrice = cleanPrice; }

    public BigDecimal getDirtyPrice() { return dirtyPrice; }
    public void setDirtyPrice(BigDecimal dirtyPrice) { this.dirtyPrice = dirtyPrice; }

    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }

    public BigDecimal getChangeRate() { return changeRate; }
    public void setChangeRate(BigDecimal changeRate) { this.changeRate = changeRate; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}

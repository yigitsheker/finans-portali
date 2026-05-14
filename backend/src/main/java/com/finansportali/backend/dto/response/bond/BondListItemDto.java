package com.finansportali.backend.dto.response.bond;

import com.finansportali.backend.entity.DebtInstrumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tahvil/bono liste öğesi DTO.
 */
public class BondListItemDto {
    private Long id;
    private String symbol;
    private String isin;
    private String name;
    private DebtInstrumentType type;
    private String currency;
    private LocalDate maturityDate;
    private BigDecimal couponRate;
    private BigDecimal latestPrice;
    private BigDecimal latestYieldRate;
    private BigDecimal changeRate;
    private String source;
    private LocalDateTime lastUpdatedAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public DebtInstrumentType getType() { return type; }
    public void setType(DebtInstrumentType type) { this.type = type; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }

    public BigDecimal getCouponRate() { return couponRate; }
    public void setCouponRate(BigDecimal couponRate) { this.couponRate = couponRate; }

    public BigDecimal getLatestPrice() { return latestPrice; }
    public void setLatestPrice(BigDecimal latestPrice) { this.latestPrice = latestPrice; }

    public BigDecimal getLatestYieldRate() { return latestYieldRate; }
    public void setLatestYieldRate(BigDecimal latestYieldRate) { this.latestYieldRate = latestYieldRate; }

    public BigDecimal getChangeRate() { return changeRate; }
    public void setChangeRate(BigDecimal changeRate) { this.changeRate = changeRate; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
}

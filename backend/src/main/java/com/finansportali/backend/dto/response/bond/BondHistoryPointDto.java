package com.finansportali.backend.dto.response.bond;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tahvil/bono tarihsel veri noktası DTO.
 */
public class BondHistoryPointDto {
    private LocalDate date;
    private BigDecimal price;
    private BigDecimal yieldRate;
    private BigDecimal volume;

    // Getters and Setters
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getYieldRate() { return yieldRate; }
    public void setYieldRate(BigDecimal yieldRate) { this.yieldRate = yieldRate; }

    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
}

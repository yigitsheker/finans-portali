package com.finansportali.backend.dto.response.bond;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tahvil/bono özet istatistikleri DTO.
 */
public class BondSummaryDto {
    private Integer totalInstruments;
    private BigDecimal averageYield;
    private BigDecimal highestYield;
    private LocalDate nearestMaturity;
    private LocalDate farthestMaturity;
    private LocalDate lastUpdateDate;

    // Getters and Setters
    public Integer getTotalInstruments() { return totalInstruments; }
    public void setTotalInstruments(Integer totalInstruments) { this.totalInstruments = totalInstruments; }

    public BigDecimal getAverageYield() { return averageYield; }
    public void setAverageYield(BigDecimal averageYield) { this.averageYield = averageYield; }

    public BigDecimal getHighestYield() { return highestYield; }
    public void setHighestYield(BigDecimal highestYield) { this.highestYield = highestYield; }

    public LocalDate getNearestMaturity() { return nearestMaturity; }
    public void setNearestMaturity(LocalDate nearestMaturity) { this.nearestMaturity = nearestMaturity; }

    public LocalDate getFarthestMaturity() { return farthestMaturity; }
    public void setFarthestMaturity(LocalDate farthestMaturity) { this.farthestMaturity = farthestMaturity; }

    public LocalDate getLastUpdateDate() { return lastUpdateDate; }
    public void setLastUpdateDate(LocalDate lastUpdateDate) { this.lastUpdateDate = lastUpdateDate; }
}

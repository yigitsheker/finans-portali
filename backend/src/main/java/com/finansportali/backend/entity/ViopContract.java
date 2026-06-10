package com.finansportali.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * VIOP (Vadeli İşlem ve Opsiyon Piyasası) futures contract snapshot.
 *
 * Rows are upserted from a scheduled scrape of İş Yatırım's public VIOP page.
 * One row per active contract symbol (e.g. F_AKBNK0526 for the AKBNK
 * single-stock future maturing May 2026).
 */
@Entity
@Table(name = "viop_contracts")
public class ViopContract {

    public enum Category {
        STOCK,        // Pay (single-stock) futures
        INDEX,        // Endeks (BIST 30, BIST 100) futures
        FX_TRY,       // TRY-quoted currency futures (USDTRY, EURTRY)
        FX_USD,       // USD-cross currency futures (EURUSD, GBPUSD)
        METAL_TRY,    // TRY-quoted precious metals (XAUTRY, XAGTRY)
        METAL_USD,    // USD-quoted precious metals (XAUUSD, XPTUSD)
        METAL         // Industrial metals (XCUUSD)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String symbol;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String underlying;

    @Column(name = "maturity_month", nullable = false)
    private Integer maturityMonth;

    @Column(name = "maturity_year", nullable = false)
    private Integer maturityYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Category category;

    @Column(name = "last_price", precision = 19, scale = 6)
    private BigDecimal lastPrice;

    @Column(name = "change_pct", precision = 8, scale = 3)
    private BigDecimal changePct;

    @Column(name = "change_abs", precision = 19, scale = 6)
    private BigDecimal changeAbs;

    @Column(name = "volume_tl", precision = 19, scale = 2)
    private BigDecimal volumeTl;

    @Column(name = "volume_lots")
    private Long volumeLots;

    // Per-contract initial / maintenance margin (teminat). Populated from a
    // verified exchange source (Takasbank VİOP margin parameters) when available;
    // when null the category-based margin rate is used instead. When set,
    // requiredMargin uses initialMargin × qty — authoritative and time-varying.
    @Column(name = "initial_margin", precision = 19, scale = 2)
    private BigDecimal initialMargin;

    @Column(name = "maintenance_margin", precision = 19, scale = 2)
    private BigDecimal maintenanceMargin;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public ViopContract() {}

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public String getUnderlying() { return underlying; }
    public Integer getMaturityMonth() { return maturityMonth; }
    public Integer getMaturityYear() { return maturityYear; }
    public Category getCategory() { return category; }
    public BigDecimal getLastPrice() { return lastPrice; }
    public BigDecimal getChangePct() { return changePct; }
    public BigDecimal getChangeAbs() { return changeAbs; }
    public BigDecimal getVolumeTl() { return volumeTl; }
    public Long getVolumeLots() { return volumeLots; }
    public BigDecimal getInitialMargin() { return initialMargin; }
    public BigDecimal getMaintenanceMargin() { return maintenanceMargin; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setName(String name) { this.name = name; }
    public void setUnderlying(String underlying) { this.underlying = underlying; }
    public void setMaturityMonth(Integer maturityMonth) { this.maturityMonth = maturityMonth; }
    public void setMaturityYear(Integer maturityYear) { this.maturityYear = maturityYear; }
    public void setCategory(Category category) { this.category = category; }
    public void setLastPrice(BigDecimal lastPrice) { this.lastPrice = lastPrice; }
    public void setChangePct(BigDecimal changePct) { this.changePct = changePct; }
    public void setChangeAbs(BigDecimal changeAbs) { this.changeAbs = changeAbs; }
    public void setVolumeTl(BigDecimal volumeTl) { this.volumeTl = volumeTl; }
    public void setVolumeLots(Long volumeLots) { this.volumeLots = volumeLots; }
    public void setInitialMargin(BigDecimal initialMargin) { this.initialMargin = initialMargin; }
    public void setMaintenanceMargin(BigDecimal maintenanceMargin) { this.maintenanceMargin = maintenanceMargin; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

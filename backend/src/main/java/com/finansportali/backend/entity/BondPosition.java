package com.finansportali.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A user's bond/bill position — SIMULATION ONLY. Modeled by NOMINAL amount (not
 * qty×price): same ISIN buys are merged into one row with a weighted-average
 * cost. Cost basis uses the DIRTY price (clean + accrued). One row per
 * (userId, isin); transaction history lives in {@link BondTransaction}.
 */
@Entity
@Table(name = "bond_positions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_bond_user_isin", columnNames = {"user_id", "isin"})
})
public class BondPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, length = 12)
    private String isin;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DebtInstrumentType type;

    @Column(length = 100)
    private String issuer;

    @Column(nullable = false, length = 3)
    private String currency = "TRY";

    /** Outstanding face/nominal still held. */
    @Column(name = "remaining_nominal", nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingNominal = BigDecimal.ZERO;

    /** Remaining cost basis (dirty) for the outstanding nominal. */
    @Column(name = "total_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    /** Weighted-average dirty cost price per 100 nominal. */
    @Column(name = "avg_cost_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal avgCostPrice = BigDecimal.ZERO;

    @Column(name = "coupon_rate", precision = 10, scale = 4)
    private BigDecimal couponRate;

    /** Coupon payments per year (e.g. 2 = semi-annual; 0 for zero-coupon/bill). */
    @Column(name = "coupon_frequency")
    private Integer couponFrequency;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    /** Last coupon date already paid into this position (for the scheduler). */
    @Column(name = "last_coupon_date")
    private LocalDate lastCouponDate;

    @Column(name = "realized_pnl", nullable = false, precision = 19, scale = 2)
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Column(name = "coupon_income", nullable = false, precision = 19, scale = 2)
    private BigDecimal couponIncome = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BondPositionStatus status = BondPositionStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public BondPosition() {}

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getIsin() { return isin; }
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public DebtInstrumentType getType() { return type; }
    public String getIssuer() { return issuer; }
    public String getCurrency() { return currency; }
    public BigDecimal getRemainingNominal() { return remainingNominal; }
    public BigDecimal getTotalCost() { return totalCost; }
    public BigDecimal getAvgCostPrice() { return avgCostPrice; }
    public BigDecimal getCouponRate() { return couponRate; }
    public Integer getCouponFrequency() { return couponFrequency; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public LocalDate getLastCouponDate() { return lastCouponDate; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public BigDecimal getCouponIncome() { return couponIncome; }
    public BondPositionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setIsin(String isin) { this.isin = isin; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setName(String name) { this.name = name; }
    public void setType(DebtInstrumentType type) { this.type = type; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setRemainingNominal(BigDecimal remainingNominal) { this.remainingNominal = remainingNominal; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public void setAvgCostPrice(BigDecimal avgCostPrice) { this.avgCostPrice = avgCostPrice; }
    public void setCouponRate(BigDecimal couponRate) { this.couponRate = couponRate; }
    public void setCouponFrequency(Integer couponFrequency) { this.couponFrequency = couponFrequency; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public void setLastCouponDate(LocalDate lastCouponDate) { this.lastCouponDate = lastCouponDate; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
    public void setCouponIncome(BigDecimal couponIncome) { this.couponIncome = couponIncome; }
    public void setStatus(BondPositionStatus status) { this.status = status; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

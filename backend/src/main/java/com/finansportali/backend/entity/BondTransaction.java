package com.finansportali.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable bond/bill transaction record (simulation). Covers buys, sells,
 * coupon payments, and redemption/maturity. Coupon legs carry gross/tax/net.
 */
@Entity
@Table(name = "bond_transactions")
public class BondTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, length = 12)
    private String isin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BondTransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal nominal;

    @Column(name = "clean_price", precision = 12, scale = 4)
    private BigDecimal cleanPrice;

    @Column(name = "accrued_interest", precision = 12, scale = 4)
    private BigDecimal accruedInterest;

    @Column(name = "dirty_price", precision = 12, scale = 4)
    private BigDecimal dirtyPrice;

    /** Cash flow: buy cost (negative-sense), sell proceeds, coupon, or redemption. */
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "realized_pnl", nullable = false, precision = 19, scale = 2)
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Column(name = "gross_coupon", precision = 19, scale = 2)
    private BigDecimal grossCoupon;

    @Column(name = "tax_amount", precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "net_coupon", precision = 19, scale = 2)
    private BigDecimal netCoupon;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt = Instant.now();

    @Column(length = 200)
    private String note;

    public BondTransaction() {}

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getIsin() { return isin; }
    public BondTransactionType getType() { return type; }
    public BigDecimal getNominal() { return nominal; }
    public BigDecimal getCleanPrice() { return cleanPrice; }
    public BigDecimal getAccruedInterest() { return accruedInterest; }
    public BigDecimal getDirtyPrice() { return dirtyPrice; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public BigDecimal getGrossCoupon() { return grossCoupon; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getNetCoupon() { return netCoupon; }
    public Instant getExecutedAt() { return executedAt; }
    public String getNote() { return note; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setIsin(String isin) { this.isin = isin; }
    public void setType(BondTransactionType type) { this.type = type; }
    public void setNominal(BigDecimal nominal) { this.nominal = nominal; }
    public void setCleanPrice(BigDecimal cleanPrice) { this.cleanPrice = cleanPrice; }
    public void setAccruedInterest(BigDecimal accruedInterest) { this.accruedInterest = accruedInterest; }
    public void setDirtyPrice(BigDecimal dirtyPrice) { this.dirtyPrice = dirtyPrice; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
    public void setGrossCoupon(BigDecimal grossCoupon) { this.grossCoupon = grossCoupon; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public void setNetCoupon(BigDecimal netCoupon) { this.netCoupon = netCoupon; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public void setNote(String note) { this.note = note; }
}

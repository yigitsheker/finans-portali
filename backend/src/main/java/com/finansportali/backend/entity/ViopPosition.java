package com.finansportali.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A user's NET VİOP (futures) position on a single contract — simulation only,
 * no real order is ever sent.
 *
 * <p>One row per (userId, contractSymbol). Net-position logic guarantees a user
 * never holds LONG and SHORT simultaneously on the same contract: opening the
 * opposite side first closes the existing side (recording CLOSE legs), and only
 * the remainder flips this row's direction. When fully closed the row stays
 * (status=CLOSED, quantity=0) so {@code realizedPnl} accumulates across the
 * contract's life; the full leg-by-leg history lives in {@link ViopTransaction}.
 */
@Entity
@Table(name = "viop_positions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_viop_user_contract", columnNames = {"user_id", "contract_symbol"})
})
public class ViopPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Keycloak subject (sub claim) — owner. */
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "contract_symbol", nullable = false, length = 40)
    private String contractSymbol;

    @Column(nullable = false, length = 20)
    private String underlying;

    /** ViopContract.Category name (STOCK, INDEX, FX_TRY, ...). */
    @Column(name = "contract_type", nullable = false, length = 40)
    private String contractType;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ViopDirection direction;

    /** Open contract count (net). Zero when CLOSED/EXPIRED. */
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity = BigDecimal.ZERO;

    /** Weighted-average entry price of the open side. */
    @Column(name = "entry_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal entryPrice = BigDecimal.ZERO;

    /** Contract size (kontrat büyüklüğü) snapshot at open time. */
    @Column(name = "contract_size", nullable = false, precision = 19, scale = 6)
    private BigDecimal contractSize = BigDecimal.ONE;

    @Column(nullable = false, length = 3)
    private String currency = "TRY";

    @Column(name = "margin_rate", precision = 9, scale = 6)
    private BigDecimal marginRate;

    @Column(name = "required_margin", precision = 19, scale = 2)
    private BigDecimal requiredMargin;

    @Column(name = "initial_margin", precision = 19, scale = 2)
    private BigDecimal initialMargin;

    @Column(name = "maintenance_margin", precision = 19, scale = 2)
    private BigDecimal maintenanceMargin;

    /** Accumulated realized P/L across all closes on this contract. */
    @Column(name = "realized_pnl", nullable = false, precision = 19, scale = 2)
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ViopPositionStatus status = ViopPositionStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    public ViopPosition() {}

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getContractSymbol() { return contractSymbol; }
    public String getUnderlying() { return underlying; }
    public String getContractType() { return contractType; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public ViopDirection getDirection() { return direction; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public BigDecimal getContractSize() { return contractSize; }
    public String getCurrency() { return currency; }
    public BigDecimal getMarginRate() { return marginRate; }
    public BigDecimal getRequiredMargin() { return requiredMargin; }
    public BigDecimal getInitialMargin() { return initialMargin; }
    public BigDecimal getMaintenanceMargin() { return maintenanceMargin; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public ViopPositionStatus getStatus() { return status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getClosedAt() { return closedAt; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setContractSymbol(String contractSymbol) { this.contractSymbol = contractSymbol; }
    public void setUnderlying(String underlying) { this.underlying = underlying; }
    public void setContractType(String contractType) { this.contractType = contractType; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public void setDirection(ViopDirection direction) { this.direction = direction; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }
    public void setContractSize(BigDecimal contractSize) { this.contractSize = contractSize; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setMarginRate(BigDecimal marginRate) { this.marginRate = marginRate; }
    public void setRequiredMargin(BigDecimal requiredMargin) { this.requiredMargin = requiredMargin; }
    public void setInitialMargin(BigDecimal initialMargin) { this.initialMargin = initialMargin; }
    public void setMaintenanceMargin(BigDecimal maintenanceMargin) { this.maintenanceMargin = maintenanceMargin; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
    public void setStatus(ViopPositionStatus status) { this.status = status; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
}

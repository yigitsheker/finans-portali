package com.finansportali.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable audit record of a single VİOP transaction leg (simulation only).
 * Every open/close/partial-close/expire is recorded so the user has a full
 * history and realized P/L is traceable.
 */
@Entity
@Table(name = "viop_transactions")
public class ViopTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "contract_symbol", nullable = false, length = 40)
    private String contractSymbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ViopTransactionType type;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal price;

    @Column(name = "contract_size", nullable = false, precision = 19, scale = 6)
    private BigDecimal contractSize;

    /** entryPrice × contractSize × quantity at the time of the leg. */
    @Column(name = "position_size", nullable = false, precision = 19, scale = 2)
    private BigDecimal positionSize;

    /** Realized P/L for close/expire legs; zero for opens. */
    @Column(name = "realized_pnl", nullable = false, precision = 19, scale = 2)
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt = Instant.now();

    @Column(length = 200)
    private String note;

    public ViopTransaction() {}

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getContractSymbol() { return contractSymbol; }
    public ViopTransactionType getType() { return type; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getContractSize() { return contractSize; }
    public BigDecimal getPositionSize() { return positionSize; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public Instant getExecutedAt() { return executedAt; }
    public String getNote() { return note; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setContractSymbol(String contractSymbol) { this.contractSymbol = contractSymbol; }
    public void setType(ViopTransactionType type) { this.type = type; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setContractSize(BigDecimal contractSize) { this.contractSize = contractSize; }
    public void setPositionSize(BigDecimal positionSize) { this.positionSize = positionSize; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public void setNote(String note) { this.note = note; }
}

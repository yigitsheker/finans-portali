package com.finansportali.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An observable record of a portfolio movement (buy / sell) for the main
 * stock/crypto/fund portfolio. Unlike {@link PortfolioPosition} (which only
 * keeps the live net holding), this is an append-only ledger so the user can
 * review their history and so realized P&L on sells (closed positions) survives
 * even after the position itself is fully sold and removed.
 */
@Entity
@Table(name = "portfolio_transactions", indexes = {
        @Index(name = "idx_portfolio_tx_user", columnList = "user_id, executed_at"),
        @Index(name = "idx_portfolio_tx_user_symbol", columnList = "user_id, symbol")
})
public class PortfolioTransaction {

    public enum Type { BUY, SELL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    /** Per-unit price of the movement (buy cost / sell price). */
    @Column(precision = 19, scale = 6)
    private BigDecimal price;

    /** quantity × price (cost for BUY, proceeds for SELL). */
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    /** Realized profit/loss — SELL only: (sellPrice − avgCost) × quantity. */
    @Column(name = "realized_pnl", precision = 19, scale = 2)
    private BigDecimal realizedPnl;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt = Instant.now();

    public PortfolioTransaction() {}

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public Instant getExecutedAt() { return executedAt; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setName(String name) { this.name = name; }
    public void setType(Type type) { this.type = type; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}

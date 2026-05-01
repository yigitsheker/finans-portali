package com.finansportali.backend.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "portfolio_positions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_portfolio_user_symbol", columnNames = {"userId", "symbol"})
})
public class PortfolioPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Keycloak subject (sub claim) - identifies the owner */
    @Column(nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, length = 30)
    private String symbol; // USDTRY, BTCUSD...

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    // Ortalama maliyet (opsiyonel ama kâr/zarar için lazım)
    @Column(nullable = true, precision = 19, scale = 6)
    private BigDecimal avgCost;

    public PortfolioPosition() {}

    public PortfolioPosition(String userId, String symbol, BigDecimal quantity, BigDecimal avgCost) {
        this.userId = userId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgCost = avgCost;
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAvgCost() { return avgCost; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
}

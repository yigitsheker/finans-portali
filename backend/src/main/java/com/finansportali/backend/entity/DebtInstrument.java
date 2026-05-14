package com.finansportali.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Borçlanma aracı (tahvil, bono, DİBS) entity.
 * Türkiye finans piyasalarındaki devlet ve özel sektör borçlanma araçlarını temsil eder.
 */
@Entity
@Table(name = "debt_instruments", indexes = {
    @Index(name = "idx_debt_symbol", columnList = "symbol"),
    @Index(name = "idx_debt_isin", columnList = "isin"),
    @Index(name = "idx_debt_type", columnList = "type"),
    @Index(name = "idx_debt_maturity", columnList = "maturity_date")
})
public class DebtInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Uygulama içi sembol (örn: TR2YT, TR10YT) */
    @Column(nullable = false, unique = true, length = 30)
    private String symbol;

    /** ISIN kodu (örn: TR240914TVA) */
    @Column(length = 12)
    private String isin;

    /** Görünen ad */
    @Column(nullable = false, length = 200)
    private String name;

    /** Borçlanma aracı türü */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DebtInstrumentType type;

    /** İhraççı (örn: Hazine ve Maliye Bakanlığı) */
    @Column(length = 100)
    private String issuer;

    /** Para birimi (TRY, USD, EUR) */
    @Column(length = 3)
    private String currency;

    /** Vade tarihi */
    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    /** Kupon oranı (yıllık %) */
    @Column(name = "coupon_rate", precision = 10, scale = 4)
    private BigDecimal couponRate;

    /** Kupon tipi (Sabit, Değişken, Sıfır Kuponlu) */
    @Column(name = "coupon_type", length = 30)
    private String couponType;

    /** Aktif mi? */
    @Column(nullable = false)
    private Boolean active = true;

    /** Oluşturulma zamanı */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Güncellenme zamanı */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public DebtInstrument() {}

    public DebtInstrument(String symbol, String name, DebtInstrumentType type) {
        this.symbol = symbol;
        this.name = name;
        this.type = type;
    }

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

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }

    public BigDecimal getCouponRate() { return couponRate; }
    public void setCouponRate(BigDecimal couponRate) { this.couponRate = couponRate; }

    public String getCouponType() { return couponType; }
    public void setCouponType(String couponType) { this.couponType = couponType; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

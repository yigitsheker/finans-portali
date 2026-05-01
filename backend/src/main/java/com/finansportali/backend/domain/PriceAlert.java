package com.finansportali.backend.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_alerts", indexes = {
        @Index(name = "idx_alert_user_symbol", columnList = "userId,symbol"),
        @Index(name = "idx_alert_active", columnList = "active"),
        @Index(name = "idx_alert_instrument", columnList = "instrument_id")
})
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kullanıcı ID'si (Keycloak subject) */
    @Column(nullable = false, length = 100)
    private String userId;

    /** Takip edilen enstrüman */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrument instrument;

    /** Enstrüman sembolü (hızlı erişim için) */
    @Column(nullable = false, length = 30)
    private String symbol;

    /** Alarm tipi */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertType alertType;

    /** Hedef fiyat */
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal targetPrice;

    /** Alarm oluşturulduğu andaki fiyat */
    @Column(nullable = true, precision = 19, scale = 6)
    private BigDecimal creationPrice;

    /** Alarm aktif mi? */
    @Column(nullable = false)
    private Boolean active = true;

    /** Alarm tetiklendiği zaman */
    @Column(nullable = true)
    private Instant triggeredAt;

    /** Alarm tetiklendiğindeki fiyat */
    @Column(nullable = true, precision = 19, scale = 6)
    private BigDecimal triggeredPrice;

    /** Alarm oluşturulma zamanı */
    @Column(nullable = false)
    private Instant createdAt;

    /** Kullanıcı notu (opsiyonel) */
    @Column(nullable = true, length = 200)
    private String note;

    public PriceAlert() {}

    public PriceAlert(String userId, MarketInstrument instrument, AlertType alertType, 
                      BigDecimal targetPrice, BigDecimal creationPrice, String note) {
        this.userId = userId;
        this.instrument = instrument;
        this.symbol = instrument.getSymbol();
        this.alertType = alertType;
        this.targetPrice = targetPrice;
        this.creationPrice = creationPrice;
        this.note = note;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public void trigger(BigDecimal currentPrice) {
        this.active = false;
        this.triggeredAt = Instant.now();
        this.triggeredPrice = currentPrice;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public MarketInstrument getInstrument() { return instrument; }
    public String getSymbol() { return symbol; }
    public AlertType getAlertType() { return alertType; }
    public BigDecimal getTargetPrice() { return targetPrice; }
    public BigDecimal getCreationPrice() { return creationPrice; }
    public Boolean getActive() { return active; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public BigDecimal getTriggeredPrice() { return triggeredPrice; }
    public Instant getCreatedAt() { return createdAt; }
    public String getNote() { return note; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setInstrument(MarketInstrument instrument) { this.instrument = instrument; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setAlertType(AlertType alertType) { this.alertType = alertType; }
    public void setTargetPrice(BigDecimal targetPrice) { this.targetPrice = targetPrice; }
    public void setCreationPrice(BigDecimal creationPrice) { this.creationPrice = creationPrice; }
    public void setActive(Boolean active) { this.active = active; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }
    public void setTriggeredPrice(BigDecimal triggeredPrice) { this.triggeredPrice = triggeredPrice; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setNote(String note) { this.note = note; }
}
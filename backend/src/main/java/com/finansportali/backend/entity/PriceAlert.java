package com.finansportali.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrument instrument;

    @Column(name = "symbol", nullable = false, length = 30)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 20)
    private AlertType alertType;

    @Column(name = "target_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal targetPrice;

    @Column(name = "creation_price", precision = 19, scale = 6)
    private BigDecimal creationPrice;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "triggered_price", precision = 19, scale = 6)
    private BigDecimal triggeredPrice;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "note", length = 200)
    private String note;

    /** Email captured at creation time. Used by the scheduled trigger which
     *  has no Authentication context to look it up from. */
    @Column(name = "user_email", length = 200)
    private String userEmail;

    /** UI language captured at creation time ('tr' or 'en'). The scheduled
     *  trigger has no UI session, so we snapshot the user's choice here.
     *  Defaults to 'tr' for legacy rows. */
    @Column(name = "language", nullable = false, length = 2)
    @Builder.Default
    private String language = "tr";

    /** Currency the user picked on the site when creating the alarm
     *  ('TRY' or 'USD'). targetPrice, creationPrice and triggeredPrice are
     *  all interpreted in this currency. The scheduled checker converts the
     *  native quote to this currency before comparing. Defaults to 'TRY'
     *  for legacy rows. */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "TRY";

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getter methods for compatibility with existing code
    public Boolean getActive() {
        return active;
    }

    public MarketInstrument getInstrument() {
        return instrument;
    }

    public BigDecimal getCreationPrice() {
        return creationPrice;
    }

    public String getNote() {
        return note;
    }

    public BigDecimal getTriggeredPrice() {
        return triggeredPrice;
    }
}

package com.finansportali.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Borçlanma aracı fiyat/getiri verisi.
 * Her gün için bir enstrümanın fiyat, getiri ve işlem hacmi bilgilerini tutar.
 */
@Entity
@Table(name = "debt_instrument_quotes", 
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_debt_quote", 
            columnNames = {"instrument_id", "quote_date", "source"})
    },
    indexes = {
        @Index(name = "idx_debt_quote_instrument", columnList = "instrument_id"),
        @Index(name = "idx_debt_quote_date", columnList = "quote_date"),
        @Index(name = "idx_debt_quote_source", columnList = "source")
    }
)
public class DebtInstrumentQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** İlişkili borçlanma aracı */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private DebtInstrument instrument;

    /** Fiyat tarihi */
    @Column(name = "quote_date", nullable = false)
    private LocalDate quoteDate;

    /** Fiyat (nominal değere göre %) */
    @Column(precision = 12, scale = 4)
    private BigDecimal price;

    /** Getiri oranı (%) */
    @Column(name = "yield_rate", precision = 10, scale = 4)
    private BigDecimal yieldRate;

    /** Temiz fiyat (clean price) */
    @Column(name = "clean_price", precision = 12, scale = 4)
    private BigDecimal cleanPrice;

    /** Kirli fiyat (dirty price - tahakkuk eden faiz dahil) */
    @Column(name = "dirty_price", precision = 12, scale = 4)
    private BigDecimal dirtyPrice;

    /** İşlem hacmi (nominal değer) */
    @Column(precision = 18, scale = 2)
    private BigDecimal volume;

    /** Değişim oranı (%) */
    @Column(name = "change_rate", precision = 10, scale = 4)
    private BigDecimal changeRate;

    /** Veri kaynağı (TCMB, BIST, DEMO) */
    @Column(nullable = false, length = 20)
    private String source;

    /** Oluşturulma zamanı */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Constructors
    public DebtInstrumentQuote() {}

    public DebtInstrumentQuote(DebtInstrument instrument, LocalDate quoteDate, String source) {
        this.instrument = instrument;
        this.quoteDate = quoteDate;
        this.source = source;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DebtInstrument getInstrument() { return instrument; }
    public void setInstrument(DebtInstrument instrument) { this.instrument = instrument; }

    public LocalDate getQuoteDate() { return quoteDate; }
    public void setQuoteDate(LocalDate quoteDate) { this.quoteDate = quoteDate; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getYieldRate() { return yieldRate; }
    public void setYieldRate(BigDecimal yieldRate) { this.yieldRate = yieldRate; }

    public BigDecimal getCleanPrice() { return cleanPrice; }
    public void setCleanPrice(BigDecimal cleanPrice) { this.cleanPrice = cleanPrice; }

    public BigDecimal getDirtyPrice() { return dirtyPrice; }
    public void setDirtyPrice(BigDecimal dirtyPrice) { this.dirtyPrice = dirtyPrice; }

    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }

    public BigDecimal getChangeRate() { return changeRate; }
    public void setChangeRate(BigDecimal changeRate) { this.changeRate = changeRate; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

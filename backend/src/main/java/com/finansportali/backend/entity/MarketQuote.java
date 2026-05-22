package com.finansportali.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "market_quotes", indexes = {
        @Index(name = "idx_quote_instrument_time", columnList = "instrument_id,asOf")
})
public class MarketQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrument instrument;

    /** Son fiyat */
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal last;

    /** Önceki kapanış — change hesabı için */
    @Column(nullable = true, precision = 19, scale = 6)
    private BigDecimal previousClose;

    /** Mutlak değişim (provider'dan veya hesaplanmış) */
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal changeAbs;

    /** Yüzde değişim (provider'dan veya hesaplanmış) */
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal changePct;

    /** Verinin çekildiği zaman */
    @Column(nullable = false)
    private Instant asOf;

    /** Hangi provider'dan geldi */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    private MarketDataProvider provider;

    /** Son trading bar'ında işlem gören lot sayısı. Yahoo "chart" endpoint'i
     *  indicators.quote[0].volume[] altında veriyor; biz son bar'ı alıyoruz.
     *  null kalabilir (legacy satırlar + provider'ın volume vermediği
     *  enstrümanlar — örn. bazı FX cross'ları). */
    @Column(nullable = true)
    private Long volume;

    public MarketQuote() {}

    public MarketQuote(MarketInstrument instrument, BigDecimal last,
                       BigDecimal previousClose, BigDecimal changeAbs, BigDecimal changePct,
                       Instant asOf, MarketDataProvider provider) {
        this.instrument = instrument;
        this.last = last;
        this.previousClose = previousClose;
        this.changeAbs = changeAbs;
        this.changePct = changePct;
        this.asOf = asOf;
        this.provider = provider;
    }

    /**
     * Convenience: previousClose yoksa changeAbs/changePct provider'dan gelir.
     */
    public MarketQuote(MarketInstrument instrument, BigDecimal last,
                       BigDecimal changeAbs, BigDecimal changePct,
                       Instant asOf) {
        this(instrument, last, null, changeAbs, changePct, asOf, null);
    }

    /**
     * previousClose üzerinden changeAbs ve changePct hesapla.
     */
    public static MarketQuote fromPreviousClose(MarketInstrument instrument,
                                                BigDecimal last, BigDecimal previousClose,
                                                Instant asOf, MarketDataProvider provider) {
        BigDecimal changeAbs = BigDecimal.ZERO;
        BigDecimal changePct = BigDecimal.ZERO;
        if (previousClose != null && previousClose.compareTo(BigDecimal.ZERO) != 0) {
            changeAbs = last.subtract(previousClose);
            changePct = changeAbs.divide(previousClose, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return new MarketQuote(instrument, last, previousClose, changeAbs, changePct, asOf, provider);
    }

    // Getters
    public Long getId() { return id; }
    public MarketInstrument getInstrument() { return instrument; }
    public BigDecimal getLast() { return last; }
    public BigDecimal getPreviousClose() { return previousClose; }
    public BigDecimal getChangeAbs() { return changeAbs; }
    public BigDecimal getChangePct() { return changePct; }
    public Instant getAsOf() { return asOf; }
    public MarketDataProvider getProvider() { return provider; }
    public Long getVolume() { return volume; }

    // Setters
    public void setInstrument(MarketInstrument instrument) { this.instrument = instrument; }
    public void setLast(BigDecimal last) { this.last = last; }
    public void setPreviousClose(BigDecimal previousClose) { this.previousClose = previousClose; }
    public void setChangeAbs(BigDecimal changeAbs) { this.changeAbs = changeAbs; }
    public void setChangePct(BigDecimal changePct) { this.changePct = changePct; }
    public void setAsOf(Instant asOf) { this.asOf = asOf; }
    public void setProvider(MarketDataProvider provider) { this.provider = provider; }
    public void setVolume(Long volume) { this.volume = volume; }
}

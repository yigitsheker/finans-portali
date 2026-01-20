package com.finansportali.backend.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
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

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal last;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal changeAbs;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal changePct;

    @Column(nullable = false)
    private Instant asOf;

    public MarketQuote() {}

    public MarketQuote(MarketInstrument instrument, BigDecimal last, BigDecimal changeAbs, BigDecimal changePct, Instant asOf) {
        this.instrument = instrument;
        this.last = last;
        this.changeAbs = changeAbs;
        this.changePct = changePct;
        this.asOf = asOf;
    }

    public Long getId() { return id; }
    public MarketInstrument getInstrument() { return instrument; }
    public BigDecimal getLast() { return last; }
    public BigDecimal getChangeAbs() { return changeAbs; }
    public BigDecimal getChangePct() { return changePct; }
    public Instant getAsOf() { return asOf; }

    public void setInstrument(MarketInstrument instrument) { this.instrument = instrument; }
    public void setLast(BigDecimal last) { this.last = last; }
    public void setChangeAbs(BigDecimal changeAbs) { this.changeAbs = changeAbs; }
    public void setChangePct(BigDecimal changePct) { this.changePct = changePct; }
    public void setAsOf(Instant asOf) { this.asOf = asOf; }
}

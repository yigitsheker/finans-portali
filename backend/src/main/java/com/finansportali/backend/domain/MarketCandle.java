package com.finansportali.backend.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "market_candles", indexes = {
        @Index(name = "idx_candle_instrument_date", columnList = "instrument_id,day")
})
public class MarketCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrument instrument;

    @Column(nullable = false)
    private LocalDate day;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal close;

    public MarketCandle() {}

    public MarketCandle(MarketInstrument instrument, LocalDate day, BigDecimal close) {
        this.instrument = instrument;
        this.day = day;
        this.close = close;
    }

    public Long getId() { return id; }
    public MarketInstrument getInstrument() { return instrument; }
    public LocalDate getDay() { return day; }
    public BigDecimal getClose() { return close; }

    public void setInstrument(MarketInstrument instrument) { this.instrument = instrument; }
    public void setDay(LocalDate day) { this.day = day; }
    public void setClose(BigDecimal close) { this.close = close; }
}

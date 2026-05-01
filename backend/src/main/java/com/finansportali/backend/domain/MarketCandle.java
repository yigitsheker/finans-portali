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

    @Column(name = "open_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal open;

    @Column(name = "high_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal high;

    @Column(name = "low_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal low;

    @Column(name = "close_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal close;

    @Column(nullable = false)
    private Long volume = 0L;

    public MarketCandle() {}

    public MarketCandle(MarketInstrument instrument, LocalDate day, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, Long volume) {
        this.instrument = instrument;
        this.day = day;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    // Simplified constructor for seed data (uses close as all prices)
    public MarketCandle(MarketInstrument instrument, LocalDate day, BigDecimal close) {
        this.instrument = instrument;
        this.day = day;
        this.open = close;
        this.high = close.multiply(BigDecimal.valueOf(1.01));
        this.low = close.multiply(BigDecimal.valueOf(0.99));
        this.close = close;
        this.volume = 0L;
    }

    public Long getId() { return id; }
    public MarketInstrument getInstrument() { return instrument; }
    public LocalDate getDay() { return day; }
    public BigDecimal getOpen() { return open; }
    public BigDecimal getHigh() { return high; }
    public BigDecimal getLow() { return low; }
    public BigDecimal getClose() { return close; }
    public Long getVolume() { return volume; }

    public void setInstrument(MarketInstrument instrument) { this.instrument = instrument; }
    public void setDay(LocalDate day) { this.day = day; }
    public void setOpen(BigDecimal open) { this.open = open; }
    public void setHigh(BigDecimal high) { this.high = high; }
    public void setLow(BigDecimal low) { this.low = low; }
    public void setClose(BigDecimal close) { this.close = close; }
    public void setVolume(Long volume) { this.volume = volume; }
}

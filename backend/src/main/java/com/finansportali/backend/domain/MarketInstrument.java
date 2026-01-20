package com.finansportali.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "market_instruments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_instrument_symbol", columnNames = "symbol")
})
public class MarketInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol; // e.g. USDTRY, EURTRY, XU100, XAUUSD, BTCUSD

    @Column(nullable = false, length = 120)
    private String name;   // e.g. "USD/TRY", "BIST 100"

    @Column(nullable = false, length = 30)
    private String type;   // FX, INDEX, COMMODITY, CRYPTO

    public MarketInstrument() {}

    public MarketInstrument(String symbol, String name, String type) {
        this.symbol = symbol;
        this.name = name;
        this.type = type;
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public String getType() { return type; }

    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
}

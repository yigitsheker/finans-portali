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

    /** Uygulama içi sembol (örn. USDTRY, BTCUSD, THYAO) */
    @Column(nullable = false, length = 30)
    private String symbol;

    /** Görünen ad */
    @Column(nullable = false, length = 120)
    private String name;

    /** Enstrüman tipi */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = true, length = 20)
    private InstrumentType instrumentType;

    /** Veri sağlayıcı */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = true, length = 20)
    private MarketDataProvider provider;

    /** Provider'ın beklediği sembol formatı */
    @Column(name = "provider_symbol", nullable = true, length = 60)
    private String providerSymbol;

    /**
     * BIST hisseleri gibi gecikmeli/EOD veri içeren enstrümanlar için true.
     * Frontend'de "Gecikmeli" etiketi gösterilir.
     */
    @Column(nullable = true)
    private Boolean delayed = false;

    public MarketInstrument() {}

    public MarketInstrument(String symbol, String name, InstrumentType instrumentType,
                            MarketDataProvider provider, String providerSymbol, boolean delayed) {
        this.symbol = symbol;
        this.name = name;
        this.instrumentType = instrumentType;
        this.provider = provider;
        this.providerSymbol = providerSymbol;
        this.delayed = delayed;
    }

    // Getters
    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public InstrumentType getInstrumentType() { return instrumentType; }
    public MarketDataProvider getProvider() { return provider; }
    public String getProviderSymbol() { return providerSymbol; }
    public Boolean getDelayed() { return delayed != null && delayed; }
    public boolean isDelayed() { return delayed != null && delayed; }

    // Setters
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setName(String name) { this.name = name; }
    public void setInstrumentType(InstrumentType instrumentType) { this.instrumentType = instrumentType; }
    public void setProvider(MarketDataProvider provider) { this.provider = provider; }
    public void setProviderSymbol(String providerSymbol) { this.providerSymbol = providerSymbol; }
    public void setDelayed(Boolean delayed) { this.delayed = delayed; }

    /** Backward compat — eski kod için */
    public String getType() {
        return instrumentType != null ? instrumentType.name() : null;
    }
    /** Backward compat */
    public String getFinnhubSymbol() { return providerSymbol; }
}

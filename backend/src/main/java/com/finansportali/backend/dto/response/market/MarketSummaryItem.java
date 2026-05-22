package com.finansportali.backend.dto.response.market;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketSummaryItem(
        String symbol,
        String name,
        String type,          // InstrumentType.name()
        BigDecimal last,
        BigDecimal changeAbs,
        BigDecimal changePct,
        Instant asOf,
        boolean delayed,      // BIST gibi gecikmeli veri
        String delayLabel,    // "Gecikmeli" veya null
        Long volume           // Latest trading bar volume; null when provider didn't supply it
) {
    /** Legacy 9-arg ctor for call sites that pre-date the volume column. */
    public MarketSummaryItem(String symbol, String name, String type,
                             BigDecimal last, BigDecimal changeAbs, BigDecimal changePct,
                             Instant asOf, boolean delayed, String delayLabel) {
        this(symbol, name, type, last, changeAbs, changePct, asOf, delayed, delayLabel, null);
    }
}

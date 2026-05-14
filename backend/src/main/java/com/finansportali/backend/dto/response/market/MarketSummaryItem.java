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
        String delayLabel     // "Gecikmeli" veya null
) {}

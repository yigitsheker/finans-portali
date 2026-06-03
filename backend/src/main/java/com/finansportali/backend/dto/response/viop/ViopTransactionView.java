package com.finansportali.backend.dto.response.viop;

import java.math.BigDecimal;
import java.time.Instant;

/** Read model for one VİOP transaction leg. */
public record ViopTransactionView(
        Long id,
        String contractSymbol,
        String type,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal contractSize,
        BigDecimal positionSize,
        BigDecimal realizedPnl,
        Instant executedAt,
        String note
) {}

package com.finansportali.backend.dto.response.viop;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Read model for a VİOP position, with backend-computed valuation figures. */
public record ViopPositionView(
        Long id,
        String contractSymbol,
        String underlying,
        String contractType,
        LocalDate maturityDate,
        String direction,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal currentPrice,
        BigDecimal contractSize,
        String currency,
        BigDecimal positionSize,
        BigDecimal requiredMargin,
        BigDecimal marginRate,
        BigDecimal initialMargin,
        BigDecimal maintenanceMargin,
        BigDecimal leverage,
        BigDecimal unrealizedPnl,
        BigDecimal realizedPnl,
        String status,
        Instant openedAt
) {}

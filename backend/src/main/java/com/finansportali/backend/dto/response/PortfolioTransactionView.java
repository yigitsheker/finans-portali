package com.finansportali.backend.dto.response;

import com.finansportali.backend.entity.PortfolioTransaction;

import java.math.BigDecimal;
import java.time.Instant;

/** One portfolio movement (buy/sell) for the history view + closed-P&L chart. */
public record PortfolioTransactionView(
        Long id,
        String symbol,
        String name,
        String type,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal amount,
        BigDecimal realizedPnl,
        Instant executedAt
) {
    public static PortfolioTransactionView from(PortfolioTransaction t) {
        return new PortfolioTransactionView(
                t.getId(), t.getSymbol(), t.getName(),
                t.getType() == null ? null : t.getType().name(),
                t.getQuantity(), t.getPrice(), t.getAmount(), t.getRealizedPnl(), t.getExecutedAt());
    }
}

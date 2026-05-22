package com.finansportali.backend.dto.response.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Top-level shape served by /api/v1/portfolio/summary-detail.
 *
 * The two trailing fields surface data-quality context to the UI:
 *
 *   • asOf      — timestamp of the freshest quote across all included
 *                 positions, so the UI can show "Güncelleme: HH:mm".
 *                 Null when no position has a usable quote.
 *
 *   • warnings  — human-readable advisories for the user (e.g. an
 *                 instrument was deleted from the catalog while the
 *                 position still exists, or a quote was missing so the
 *                 position was valued at its cost basis). Each entry is
 *                 already localised on the backend side. May be empty.
 *
 * Legacy compact constructor preserves the older 5-arg call sites used
 * in tests / facade paths that don't care about these fields.
 */
public record PortfolioSummaryDetail(
        BigDecimal totalInvested,
        BigDecimal totalCurrentValue,
        BigDecimal totalChangeValue,
        BigDecimal totalChangePercent,
        List<PortfolioPositionDetail> positions,
        Instant asOf,
        List<String> warnings
) {
    public PortfolioSummaryDetail(BigDecimal totalInvested,
                                  BigDecimal totalCurrentValue,
                                  BigDecimal totalChangeValue,
                                  BigDecimal totalChangePercent,
                                  List<PortfolioPositionDetail> positions) {
        this(totalInvested, totalCurrentValue, totalChangeValue, totalChangePercent,
                positions, null, List.of());
    }
}

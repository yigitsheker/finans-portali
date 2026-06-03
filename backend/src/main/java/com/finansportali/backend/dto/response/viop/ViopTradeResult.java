package com.finansportali.backend.dto.response.viop;

import java.util.List;

/**
 * Result of an open/close action: the resulting position (null if fully
 * closed) plus the executed transaction legs (e.g. a flip yields several).
 */
public record ViopTradeResult(
        ViopPositionView position,
        List<ViopTransactionView> legs,
        String message
) {}

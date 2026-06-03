package com.finansportali.backend.dto.response.viop;

import java.math.BigDecimal;

/**
 * Backend-computed preview for an intended VİOP open (no persistence). The
 * frontend renders these so all math stays server-side.
 */
public record ViopPreviewResult(
        String contractSymbol,
        String direction,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal contractSize,
        String currency,
        BigDecimal positionSize,
        BigDecimal requiredMargin,
        BigDecimal leverage,
        boolean willCloseOpposite,
        String note
) {}

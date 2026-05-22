package com.finansportali.backend.dto.request;

import com.finansportali.backend.entity.AlertType;
import java.math.BigDecimal;

public record CreateAlertRequest(
        String symbol,
        AlertType alertType,
        BigDecimal targetPrice,
        String note,
        /** "TRY" or "USD" — currency the user picked on the site. Optional;
         *  the service falls back to the instrument's native currency when null. */
        String currency
) {
    public CreateAlertRequest {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (alertType == null) {
            throw new IllegalArgumentException("Alert type cannot be null");
        }
        if (targetPrice == null || targetPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Target price must be positive");
        }
    }

    /** Legacy 4-arg constructor for callers/tests that pre-date the currency field. */
    public CreateAlertRequest(String symbol, AlertType alertType, BigDecimal targetPrice, String note) {
        this(symbol, alertType, targetPrice, note, null);
    }
}
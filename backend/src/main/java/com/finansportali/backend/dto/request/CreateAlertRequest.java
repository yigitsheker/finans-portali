package com.finansportali.backend.dto.request;

import com.finansportali.backend.entity.AlertType;
import java.math.BigDecimal;

public record CreateAlertRequest(
        String symbol,
        AlertType alertType,
        BigDecimal targetPrice,
        String note
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
}
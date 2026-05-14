package com.finansportali.backend.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpsertPositionRequest(
        @NotBlank String symbol,

        @NotNull
        @DecimalMin(value = "0.000001", inclusive = true, message = "quantity must be > 0")
        BigDecimal quantity,

        @DecimalMin(value = "0", inclusive = true, message = "avgCost must be >= 0")
        BigDecimal avgCost
) {}

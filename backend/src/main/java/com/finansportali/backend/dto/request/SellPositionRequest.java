package com.finansportali.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SellPositionRequest(
        @NotBlank String symbol,

        @NotNull
        @DecimalMin(value = "0.000001", inclusive = true, message = "quantity must be > 0")
        BigDecimal quantity
) {}

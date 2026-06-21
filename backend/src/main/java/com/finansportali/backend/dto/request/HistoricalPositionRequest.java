package com.finansportali.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalPositionRequest(
    @NotBlank(message = "Symbol is required")
    String symbol,

    @NotBlank(message = "Name is required")
    String name,

    @NotNull(message = "Buy date is required")
    LocalDate buyDate,

    @NotNull(message = "Buy price is required")
    @Positive(message = "Buy price must be positive")
    BigDecimal buyPrice,

    @NotNull(message = "Lots is required")
    @Positive(message = "Lots must be positive")
    BigDecimal lots,

    @NotBlank(message = "Currency is required")
    String currency
) {}

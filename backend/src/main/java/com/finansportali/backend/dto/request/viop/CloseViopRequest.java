package com.finansportali.backend.dto.request.viop;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Close (or partially close) an open VİOP position. {@code quantity} must not
 * exceed the open contract count. {@code price} optional → live contract price.
 */
public record CloseViopRequest(
        @NotBlank String contractSymbol,
        @NotNull @DecimalMin(value = "0.000001", message = "quantity must be > 0") BigDecimal quantity,
        @DecimalMin(value = "0", message = "price must be >= 0") BigDecimal price
) {}

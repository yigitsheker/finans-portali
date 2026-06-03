package com.finansportali.backend.dto.request.bond;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Buy a bond/bill by NOMINAL amount. {@code identifier} is the ISIN (or symbol).
 * Cost is computed from the dirty price: dirtyPrice if given, else
 * cleanPrice + accruedInterest, else cleanPrice.
 */
public record BuyBondRequest(
        @NotBlank String identifier,
        @NotNull @DecimalMin(value = "0.000001", message = "nominal must be > 0") BigDecimal nominal,
        @NotNull @DecimalMin(value = "0.000001", message = "cleanPrice must be > 0") BigDecimal cleanPrice,
        @DecimalMin(value = "0", message = "accruedInterest must be >= 0") BigDecimal accruedInterest,
        @DecimalMin(value = "0", message = "dirtyPrice must be >= 0") BigDecimal dirtyPrice
) {}

package com.finansportali.backend.dto.request.viop;

import com.finansportali.backend.entity.ViopDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Open a VİOP position (Long Aç / Short Aç). Net-position logic may first close
 * an opposing open position on the same contract. {@code price} is optional —
 * when null the backend uses the live contract price (the user can't dictate
 * fills; this is simulation).
 */
public record OpenViopRequest(
        @NotBlank String contractSymbol,
        @NotNull ViopDirection direction,
        @NotNull @DecimalMin(value = "0.000001", message = "quantity must be > 0") BigDecimal quantity,
        @DecimalMin(value = "0", message = "price must be >= 0") BigDecimal price
) {}

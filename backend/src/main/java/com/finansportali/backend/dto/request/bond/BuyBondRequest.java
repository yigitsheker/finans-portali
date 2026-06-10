package com.finansportali.backend.dto.request.bond;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Buy a bond/bill by NOMINAL amount. {@code identifier} is the ISIN (or symbol).
 * Cost is computed from the dirty price: dirtyPrice if given, else
 * cleanPrice + accruedInterest; when accruedInterest is omitted it is derived
 * automatically from the coupon schedule, so the user can enter only cleanPrice.
 * {@code couponFrequency} (1=yıllık, 2=yarı-yıllık, 4=üç-aylık) overrides the
 * instrument's default coupon frequency; omit to use the default.
 */
public record BuyBondRequest(
        @NotBlank String identifier,
        @NotNull @DecimalMin(value = "0.000001", message = "nominal must be > 0") BigDecimal nominal,
        @NotNull @DecimalMin(value = "0.000001", message = "cleanPrice must be > 0") BigDecimal cleanPrice,
        @DecimalMin(value = "0", message = "accruedInterest must be >= 0") BigDecimal accruedInterest,
        @DecimalMin(value = "0", message = "dirtyPrice must be >= 0") BigDecimal dirtyPrice,
        @Min(value = 0, message = "couponFrequency must be >= 0") @Max(value = 12, message = "couponFrequency must be <= 12") Integer couponFrequency
) {}

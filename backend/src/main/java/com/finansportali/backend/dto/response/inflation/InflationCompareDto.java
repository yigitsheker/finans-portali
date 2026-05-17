package com.finansportali.backend.dto.response.inflation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Compare a nominal price change between two dates against cumulative CPI inflation
 * over the same window. Lets the UI show whether an asset beat or lost to inflation
 * in real terms.
 */
public record InflationCompareDto(
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal cpiFrom,                  // CPI level at "from" month (or closest earlier)
        BigDecimal cpiTo,                    // CPI level at "to" month (or closest earlier)
        BigDecimal cumulativeInflationPct,   // (cpiTo / cpiFrom - 1) * 100
        BigDecimal nominalReturnPct,         // user's input — for context
        BigDecimal realReturnPct             // ((1 + nominal/100) / (1 + infl/100) - 1) * 100
) {}

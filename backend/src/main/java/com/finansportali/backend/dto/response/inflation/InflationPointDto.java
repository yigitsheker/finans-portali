package com.finansportali.backend.dto.response.inflation;

import com.finansportali.backend.entity.InflationDataPoint;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InflationPointDto(
        LocalDate periodDate,
        BigDecimal cpiIndex,
        BigDecimal cpiYearlyChange,
        BigDecimal cpiMonthlyChange,
        BigDecimal ppiIndex,
        BigDecimal ppiYearlyChange,
        String source
) {
    public static InflationPointDto from(InflationDataPoint e) {
        return new InflationPointDto(
                e.getPeriodDate(),
                e.getCpiIndex(),
                e.getCpiYearlyChange(),
                e.getCpiMonthlyChange(),
                e.getPpiIndex(),
                e.getPpiYearlyChange(),
                e.getSource()
        );
    }
}

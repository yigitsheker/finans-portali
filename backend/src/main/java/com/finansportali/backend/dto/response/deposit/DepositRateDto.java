package com.finansportali.backend.dto.response.deposit;

import com.finansportali.backend.entity.DepositRatePoint;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DepositRateDto(
        LocalDate periodDate,
        String currency,
        BigDecimal rate1m,
        BigDecimal rate3m,
        BigDecimal rate6m,
        BigDecimal rate12m,
        BigDecimal rateOver12m,
        BigDecimal rateAvg
) {
    public static DepositRateDto from(DepositRatePoint e) {
        return new DepositRateDto(
                e.getPeriodDate(),
                e.getCurrency(),
                e.getRate1m(),
                e.getRate3m(),
                e.getRate6m(),
                e.getRate12m(),
                e.getRateOver12m(),
                e.getRateAvg()
        );
    }
}

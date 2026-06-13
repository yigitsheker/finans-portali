package com.finansportali.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalPositionResponse(
    Long id,
    String symbol,
    String name,
    LocalDate buyDate,
    BigDecimal buyPrice,
    Integer lots,
    String currency
) {}

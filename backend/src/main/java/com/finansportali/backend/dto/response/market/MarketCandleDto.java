package com.finansportali.backend.dto.response.market;

import java.math.BigDecimal;

/**
 * One OHLC candle for the native chart. {@code time} is Unix epoch SECONDS
 * (UTC), which is exactly the {@code UTCTimestamp} format lightweight-charts
 * expects on the time axis.
 */
public record MarketCandleDto(
        long time,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {}

package com.finansportali.backend.service.analysis;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Toy technical-analysis helper. Derives short-/long-term BUY/HOLD/SELL
 * signals from recent percentage changes and a simple moving-average
 * comparison. Intentionally crude — meant as a starting point for the
 * Analysis page UI, not a real trading model.
 *
 * Sign convention:
 *   BUY   — meaningful positive momentum
 *   SELL  — meaningful negative momentum
 *   HOLD  — small moves either way
 *   NEUTRAL — insufficient data
 */
// Explicit bean name — the project already has a
// com.finansportali.backend.service.TechnicalAnalysisService (charting),
// and Spring's default name generator would collide on
// "technicalAnalysisService" when both classes get scanned.
@Service("analysisTechnicalAnalysisService")
public class TechnicalAnalysisService {

    public static final String BUY = "BUY";
    public static final String SELL = "SELL";
    public static final String HOLD = "HOLD";
    public static final String NEUTRAL = "NEUTRAL";

    /**
     * Short-term BUY/HOLD/SELL signal from weekly + monthly momentum.
     * Returns NEUTRAL when both inputs are null.
     */
    public String shortTermSignal(BigDecimal weeklyPct, BigDecimal monthlyPct) {
        if (weeklyPct == null && monthlyPct == null) return NEUTRAL;
        double w = weeklyPct != null ? weeklyPct.doubleValue() : 0;
        double m = monthlyPct != null ? monthlyPct.doubleValue() : 0;
        // Strong weekly trend dominates; otherwise fall back to monthly.
        if (w > 3 || (m > 5 && w > 0)) return BUY;
        if (w < -3 || (m < -5 && w < 0)) return SELL;
        return HOLD;
    }

    /**
     * Long-term BUY/HOLD/SELL signal from monthly + yearly momentum.
     * Returns NEUTRAL when both inputs are null.
     */
    public String longTermSignal(BigDecimal monthlyPct, BigDecimal yearlyPct) {
        if (monthlyPct == null && yearlyPct == null) return NEUTRAL;
        double m = monthlyPct != null ? monthlyPct.doubleValue() : 0;
        double y = yearlyPct != null ? yearlyPct.doubleValue() : 0;
        if (y > 15 && m > -5) return BUY;
        if (y < -15 && m < 5) return SELL;
        return HOLD;
    }

    /**
     * Coarse trend label: UP / DOWN / SIDEWAYS from weekly + monthly change.
     */
    public String trend(BigDecimal weeklyPct, BigDecimal monthlyPct) {
        if (weeklyPct == null && monthlyPct == null) return "SIDEWAYS";
        double w = weeklyPct != null ? weeklyPct.doubleValue() : 0;
        double m = monthlyPct != null ? monthlyPct.doubleValue() : 0;
        if (w > 1 && m > 0) return "UP";
        if (w < -1 && m < 0) return "DOWN";
        return "SIDEWAYS";
    }

    /**
     * Volatility bucket (LOW / MEDIUM / HIGH) from the absolute size of the
     * monthly and yearly moves.
     */
    public String volatility(BigDecimal monthlyPct, BigDecimal yearlyPct) {
        double absMonthly = monthlyPct == null ? 0 : Math.abs(monthlyPct.doubleValue());
        double absYearly  = yearlyPct  == null ? 0 : Math.abs(yearlyPct.doubleValue());
        if (absMonthly > 15 || absYearly > 80) return "HIGH";
        if (absMonthly > 5 || absYearly > 30) return "MEDIUM";
        return "LOW";
    }

    /**
     * Simple moving-average comparison. If the latest price sits above the
     * mean of the prior {@code window} prices, the trend is "above MA"
     * (bullish bias). Returns null when the series is too short to compute.
     */
    public Boolean priceAboveMovingAverage(List<BigDecimal> series, int window) {
        if (series == null || series.size() < window + 1) return null;
        List<BigDecimal> tail = series.subList(series.size() - window - 1, series.size() - 1);
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : tail) sum = sum.add(v);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(tail.size()), 6, RoundingMode.HALF_UP);
        return series.get(series.size() - 1).compareTo(avg) > 0;
    }
}

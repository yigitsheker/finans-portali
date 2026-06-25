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
// Distinct class name from the charting-side
// com.finansportali.backend.service.TechnicalAnalysisService — this one
// derives BUY/HOLD/SELL signals for the Analiz page, so it carries the
// default bean name "technicalSignalService" with no qualifier needed.
@Service
public class TechnicalSignalService {

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

    // ── Composite signal engine ─────────────────────────────────────────
    //
    // The single-threshold momentum methods above are kept for backward
    // compatibility, but the Analysis grid now uses the composite engine:
    // each signal blends several independent factors (price-vs-moving-average
    // trend, volatility-normalised momentum, RSI overbought/oversold, MACD
    // histogram) into one weighted score, then reports both the BUY/HOLD/SELL
    // verdict and a confidence bucket reflecting how strongly the factors
    // agreed. Factors with no data are skipped and the remaining weights are
    // renormalised, so funds (returns but no candle history) and data-less FX
    // rows degrade gracefully instead of forcing a misleading verdict.

    public static final String CONF_LOW = "LOW";
    public static final String CONF_MEDIUM = "MEDIUM";
    public static final String CONF_HIGH = "HIGH";

    /** A signal plus how strongly the contributing factors agreed on it. */
    public record SignalResult(String signal, String confidence) {}

    private static final SignalResult NEUTRAL_RESULT = new SignalResult(NEUTRAL, null);

    /**
     * Short-term composite (≈ days–weeks horizon). Uses SMA10/SMA20 trend,
     * weekly momentum normalised by realised volatility, RSI(14) and the
     * MACD(12,26,9) histogram when a price series is available; falls back to
     * weekly+monthly momentum when it isn't, and to NEUTRAL when nothing is.
     */
    public SignalResult shortTermComposite(List<BigDecimal> closes, BigDecimal weeklyPct, BigDecimal monthlyPct) {
        double[] x = toArray(closes);
        Accumulator acc = new Accumulator();
        if (x.length >= 12) {
            double price = x[x.length - 1];
            acc.add(0.30, trendScore(price, smaLast(x, 10), smaLast(x, 20)));
            Double rsi = rsiLast(x, 14);
            acc.add(0.20, rsi == null ? null : rsiScore(rsi));
            Double hist = macdHistLast(x, 12, 26, 9);
            acc.add(0.20, hist == null ? null : Math.signum(hist));
            acc.add(0.30, momentumScore(weeklyPct, dailyVol(x, 30), 5, 6));
        } else {
            acc.add(0.60, momentumScore(weeklyPct, null, 5, 6));
            acc.add(0.40, scaleScore(monthlyPct, 12));
        }
        return acc.result();
    }

    /**
     * Long-term composite (≈ months–year horizon). Uses SMA50 vs SMA200/SMA100
     * trend, yearly + monthly momentum and a light RSI(14) input; falls back to
     * yearly+monthly momentum without a series.
     */
    public SignalResult longTermComposite(List<BigDecimal> closes, BigDecimal monthlyPct, BigDecimal yearlyPct) {
        double[] x = toArray(closes);
        Accumulator acc = new Accumulator();
        if (x.length >= 60) {
            double price = x[x.length - 1];
            Double smaSlow = x.length >= 200 ? smaLast(x, 200) : smaLast(x, 100);
            acc.add(0.40, trendScore(price, smaLast(x, 50), smaSlow));
            acc.add(0.40, scaleScore(yearlyPct, 40));
            acc.add(0.10, scaleScore(monthlyPct, 12));
            Double rsi = rsiLast(x, 14);
            acc.add(0.10, rsi == null ? null : rsiScore(rsi));
        } else {
            acc.add(0.65, scaleScore(yearlyPct, 40));
            acc.add(0.35, scaleScore(monthlyPct, 12));
        }
        return acc.result();
    }

    /** Weighted average of the non-null factor scores, mapped to a verdict. */
    private static final class Accumulator {
        private double weighted = 0;
        private double weightSum = 0;
        void add(double weight, Double score) {
            if (score == null) return;
            weighted += weight * Math.max(-1, Math.min(1, score));
            weightSum += weight;
        }
        SignalResult result() {
            if (weightSum == 0) return NEUTRAL_RESULT;
            double s = weighted / weightSum;                       // [-1, 1]
            String signal = s >= 0.20 ? BUY : (s <= -0.20 ? SELL : HOLD);
            double a = Math.abs(s);
            String conf = a >= 0.45 ? CONF_HIGH : (a >= 0.20 ? CONF_MEDIUM : CONF_LOW);
            return new SignalResult(signal, conf);
        }
    }

    // ── Factor scores (each maps to [-1, 1]) ────────────────────────────

    /** +1 above both MAs, −1 below both, fractional/partial otherwise. */
    private static Double trendScore(double price, Double maFast, Double maSlow) {
        double s = 0; int n = 0;
        if (maFast != null) { s += price > maFast ? 0.5 : -0.5; n++; }
        if (maSlow != null) { s += price > maSlow ? 0.5 : -0.5; n++; }
        if (n == 0) return null;
        // With a single MA the magnitude caps at 0.5; lift it to ±1 so a
        // one-MA verdict isn't artificially diluted.
        return n == 1 ? s * 2 : s;
    }

    /** RSI as momentum in the mid-band, flipped to mean-reversion at extremes. */
    private static double rsiScore(double rsi) {
        if (rsi >= 72) return -0.4;   // overbought → pullback risk
        if (rsi <= 28) return 0.4;    // oversold → bounce potential
        return Math.max(-1, Math.min(1, (rsi - 50) / 20.0));
    }

    /** Momentum normalised by realised volatility; linear fallback otherwise. */
    private static Double momentumScore(BigDecimal pct, Double dailyVol, int horizonDays, double fallbackSat) {
        if (pct == null) return null;
        double r = pct.doubleValue() / 100.0;
        if (dailyVol != null && dailyVol > 0) {
            double expected = dailyVol * Math.sqrt(horizonDays);
            return Math.tanh(r / (expected * 1.5));
        }
        return scaleScore(pct, fallbackSat);
    }

    /** Linear score saturating at ±sat percent. */
    private static Double scaleScore(BigDecimal pct, double sat) {
        if (pct == null) return null;
        double v = pct.doubleValue() / sat;
        return v > 1 ? 1 : (v < -1 ? -1 : v);
    }

    // ── Indicator math (operate on chronological-ascending closes) ──────

    private static double[] toArray(List<BigDecimal> closes) {
        if (closes == null || closes.isEmpty()) return new double[0];
        double[] x = new double[closes.size()];
        int n = 0;
        for (BigDecimal c : closes) if (c != null) x[n++] = c.doubleValue();
        return n == x.length ? x : java.util.Arrays.copyOf(x, n);
    }

    static Double smaLast(double[] x, int period) {
        if (x.length < period) return null;
        double s = 0;
        for (int i = x.length - period; i < x.length; i++) s += x[i];
        return s / period;
    }

    /** Wilder's RSI latest value (0-100); null if the series is too short. */
    static Double rsiLast(double[] x, int period) {
        if (x.length < period + 1) return null;
        double gain = 0, loss = 0;
        for (int i = 1; i <= period; i++) {
            double ch = x[i] - x[i - 1];
            if (ch >= 0) gain += ch; else loss -= ch;
        }
        double avgGain = gain / period, avgLoss = loss / period;
        for (int i = period + 1; i < x.length; i++) {
            double ch = x[i] - x[i - 1];
            avgGain = (avgGain * (period - 1) + Math.max(ch, 0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-ch, 0)) / period;
        }
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100 - 100 / (1 + rs);
    }

    private static double[] emaSeries(double[] x, int period) {
        double[] out = new double[x.length];
        double k = 2.0 / (period + 1);
        out[0] = x[0];
        for (int i = 1; i < x.length; i++) out[i] = x[i] * k + out[i - 1] * (1 - k);
        return out;
    }

    /** MACD histogram (macd − signal) latest value; null if too short. */
    static Double macdHistLast(double[] x, int fast, int slow, int signal) {
        if (x.length < slow + signal) return null;
        double[] emaF = emaSeries(x, fast);
        double[] emaS = emaSeries(x, slow);
        double[] macd = new double[x.length];
        for (int i = 0; i < x.length; i++) macd[i] = emaF[i] - emaS[i];
        double[] sig = emaSeries(macd, signal);
        return macd[x.length - 1] - sig[x.length - 1];
    }

    /** Realised daily-return volatility (stdev of pct returns) over a window. */
    private static Double dailyVol(double[] x, int lookback) {
        int n = Math.min(lookback, x.length - 1);
        if (n < 5) return null;
        int start = x.length - n;
        double[] r = new double[n];
        double mean = 0;
        for (int i = 0; i < n; i++) {
            double prev = x[start + i - 1];
            r[i] = prev != 0 ? (x[start + i] - prev) / prev : 0;
            mean += r[i];
        }
        mean /= n;
        double var = 0;
        for (double v : r) var += (v - mean) * (v - mean);
        return Math.sqrt(var / n);
    }
}

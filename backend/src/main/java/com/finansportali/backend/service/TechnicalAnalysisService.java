package com.finansportali.backend.service;

import com.finansportali.backend.entity.HistoricalPrice;
import com.finansportali.backend.dto.response.TechnicalAnalysisResponse;
import com.finansportali.backend.dto.response.TrendDto;
import com.finansportali.backend.dto.response.SeriesPointDto;
import com.finansportali.backend.dto.response.SummaryDto;
import com.finansportali.backend.repository.HistoricalPriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes the classic technical-analysis bundle from a daily close series:
 *
 *   SMA 7 / SMA 20 / SMA 50  — trend overlays
 *   RSI 14 (Wilder)          — momentum oscillator, 0-100
 *   Bollinger Bands (20, 2σ) — volatility channels
 *   MACD (12, 26, 9)         — convergence/divergence histogram
 *   Annualised volatility    — σ of daily log-returns × √252, in %
 *   Linear-regression trend  — slope normalised against mean price so the
 *                              UP/DOWN/SIDEWAYS classification is scale-free
 *
 * Caveat: only daily closes are stored, so anything that needs intraday
 * granularity (true range, intraday volatility) is out of scope here.
 */
@Service
public class TechnicalAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(TechnicalAnalysisService.class);

    // Indicator periods — standard textbook defaults.
    private static final int RSI_PERIOD = 14;
    private static final int BB_PERIOD = 20;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int MACD_SIGNAL = 9;
    private static final double BB_K = 2.0;
    private static final double TRADING_DAYS_PER_YEAR = 252.0;

    private final HistoricalPriceRepository historicalPriceRepo;
    private final HistoricalPriceService historicalPriceService;

    public TechnicalAnalysisService(HistoricalPriceRepository historicalPriceRepo,
                                    HistoricalPriceService historicalPriceService) {
        this.historicalPriceRepo = historicalPriceRepo;
        this.historicalPriceService = historicalPriceService;
    }

    /**
     * Warmup buffer. MACD needs MACD_SLOW + MACD_SIGNAL − 1 = 34 closes
     * before its signal line starts producing values; SMA 50 needs 50.
     * 60 is comfortable for everything we compute today.
     */
    private static final int WARMUP_DAYS = 60;

    /**
     * Cached for 5 minutes — the same period+symbol pair will be requested
     * repeatedly as the user toggles indicators on/off, and the underlying
     * historical-price service is the slow path. Cache name is reused with
     * the existing CaffeineCacheManager; we declared "technicalAnalysis"
     * there to match.
     */
    @Cacheable(cacheNames = "technicalAnalysis",
               key = "#symbol + ':' + #fromDate + ':' + #toDate")
    public TechnicalAnalysisResponse getTechnicalAnalysis(String symbol, LocalDate fromDate, LocalDate toDate) {
        log.info("[TechnicalAnalysis] symbol={} from={} to={}", symbol, fromDate, toDate);

        // Fetch a wider window than requested so the indicators have proper
        // warmup. Without this, picking "1A" leaves MACD's signal line
        // (which needs ~34 closes) completely empty.
        LocalDate fetchFrom = fromDate.minusDays(WARMUP_DAYS);
        List<HistoricalPrice> prices = historicalPriceService.getHistoricalPrices(symbol, fetchFrom, toDate);
        if (prices.isEmpty()) {
            log.warn("[TechnicalAnalysis] no historical data for symbol={}", symbol);
            return insufficient(symbol, fromDate, toDate);
        }

        double[] closes = new double[prices.size()];
        for (int i = 0; i < prices.size(); i++) {
            closes[i] = getClosePrice(prices.get(i)).doubleValue();
        }

        Double[] sma7 = sma(closes, 7);
        Double[] sma20 = sma(closes, 20);
        Double[] sma50 = sma(closes, 50);
        Double[] rsi = rsiWilder(closes, RSI_PERIOD);
        Double[][] bb = bollingerBands(closes, BB_PERIOD, BB_K);
        Double[][] macdArr = macd(closes, MACD_FAST, MACD_SLOW, MACD_SIGNAL);

        // Find the first index whose date is on/after the user-requested
        // start. Indicators stay computed over the full window (so MACD
        // signal is already populated by then); we just clip the returned
        // series so the chart doesn't show the warmup tail.
        int startIdx = 0;
        for (int i = 0; i < prices.size(); i++) {
            if (!prices.get(i).getPriceDate().isBefore(fromDate)) { startIdx = i; break; }
        }

        List<SeriesPointDto> series = new ArrayList<>(prices.size() - startIdx);
        double[] visibleCloses = new double[prices.size() - startIdx];
        Double[] visibleRsi = new Double[prices.size() - startIdx];
        for (int i = startIdx; i < prices.size(); i++) {
            series.add(new SeriesPointDto(
                    prices.get(i).getPriceDate().toString(),
                    closes[i],
                    sma7[i], sma20[i], sma50[i],
                    rsi[i],
                    bb[0][i], bb[1][i], bb[2][i],
                    macdArr[0][i], macdArr[1][i], macdArr[2][i]
            ));
            visibleCloses[i - startIdx] = closes[i];
            visibleRsi[i - startIdx] = rsi[i];
        }

        // Trend + summary operate on the visible (requested) window only —
        // we don't want warmup days bleeding into "highest/lowest in period".
        TrendDto trend = trend(visibleCloses);
        SummaryDto summary = summary(visibleCloses, visibleRsi);
        return new TechnicalAnalysisResponse(symbol, fromDate.toString(), toDate.toString(),
                trend, summary, series);
    }

    // ── Indicators ─────────────────────────────────────────────────────

    /** Simple moving average. Returns null until {@code period} closes are seen. */
    private Double[] sma(double[] x, int period) {
        Double[] out = new Double[x.length];
        if (x.length < period) return out;
        double sum = 0;
        for (int i = 0; i < x.length; i++) {
            sum += x[i];
            if (i >= period) sum -= x[i - period];
            if (i >= period - 1) out[i] = sum / period;
        }
        return out;
    }

    /**
     * Wilder's RSI: seeds with simple averages over the first {@code period}
     * up/down moves, then smooths with α = 1/period thereafter. This is the
     * canonical RSI most charting platforms use.
     */
    private Double[] rsiWilder(double[] x, int period) {
        Double[] out = new Double[x.length];
        if (x.length <= period) return out;
        double gainSum = 0, lossSum = 0;
        for (int i = 1; i <= period; i++) {
            double diff = x[i] - x[i - 1];
            if (diff >= 0) gainSum += diff; else lossSum -= diff;
        }
        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;
        out[period] = rsiFromAverages(avgGain, avgLoss);
        for (int i = period + 1; i < x.length; i++) {
            double diff = x[i] - x[i - 1];
            double gain = diff > 0 ? diff : 0;
            double loss = diff < 0 ? -diff : 0;
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
            out[i] = rsiFromAverages(avgGain, avgLoss);
        }
        return out;
    }

    private double rsiFromAverages(double avgGain, double avgLoss) {
        if (avgLoss == 0) return 100.0; // pure uptrend in window → RSI saturates
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    /** Returns {upper[], mid[], lower[]} of period-N, k-σ Bollinger Bands. */
    private Double[][] bollingerBands(double[] x, int period, double k) {
        Double[] mid = sma(x, period);
        Double[] upper = new Double[x.length];
        Double[] lower = new Double[x.length];
        for (int i = period - 1; i < x.length; i++) {
            double mean = mid[i];
            double sumSq = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double d = x[j] - mean;
                sumSq += d * d;
            }
            double sd = Math.sqrt(sumSq / period);
            upper[i] = mean + k * sd;
            lower[i] = mean - k * sd;
        }
        return new Double[][] { upper, mid, lower };
    }

    /**
     * Exponential moving average. The standard convention is to seed with
     * an SMA of the first {@code period} values; everything before that
     * stays null.
     */
    private Double[] ema(double[] x, int period) {
        Double[] out = new Double[x.length];
        if (x.length < period) return out;
        double k = 2.0 / (period + 1.0);
        double seed = 0;
        for (int i = 0; i < period; i++) seed += x[i];
        out[period - 1] = seed / period;
        for (int i = period; i < x.length; i++) {
            out[i] = x[i] * k + out[i - 1] * (1 - k);
        }
        return out;
    }

    /** Same as ema but operating on an already-nullable Double[] input. */
    private Double[] emaOf(Double[] x, int period) {
        Double[] out = new Double[x.length];
        // Find the first index where x has a value; that's our seed window start.
        int firstIdx = -1;
        for (int i = 0; i < x.length; i++) { if (x[i] != null) { firstIdx = i; break; } }
        if (firstIdx < 0 || firstIdx + period > x.length) return out;
        double k = 2.0 / (period + 1.0);
        double seed = 0;
        for (int i = firstIdx; i < firstIdx + period; i++) seed += x[i];
        out[firstIdx + period - 1] = seed / period;
        for (int i = firstIdx + period; i < x.length; i++) {
            out[i] = x[i] * k + out[i - 1] * (1 - k);
        }
        return out;
    }

    /** Returns {macdLine[], signalLine[], histogram[]}. */
    private Double[][] macd(double[] x, int fast, int slow, int signal) {
        Double[] emaFast = ema(x, fast);
        Double[] emaSlow = ema(x, slow);
        Double[] macdLine = new Double[x.length];
        for (int i = 0; i < x.length; i++) {
            if (emaFast[i] != null && emaSlow[i] != null) {
                macdLine[i] = emaFast[i] - emaSlow[i];
            }
        }
        Double[] signalLine = emaOf(macdLine, signal);
        Double[] hist = new Double[x.length];
        for (int i = 0; i < x.length; i++) {
            if (macdLine[i] != null && signalLine[i] != null) {
                hist[i] = macdLine[i] - signalLine[i];
            }
        }
        return new Double[][] { macdLine, signalLine, hist };
    }

    // ── Trend / summary ────────────────────────────────────────────────

    /**
     * Linear regression on closes. Slope is normalised against the mean
     * price so the threshold is scale-free: at any price level, > 0.1%
     * per day counts as a directional trend.
     */
    private TrendDto trend(double[] closes) {
        int n = closes.length;
        if (n < 2) {
            return new TrendDto("INSUFFICIENT_DATA", 0.0, 0.0,
                    "Trend analizi için yeterli veri yok.");
        }
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += closes[i];
            sumXY += i * closes[i];
            sumX2 += i * (double) i;
        }
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double mean = sumY / n;
        // Slope as percent of mean price, per day. 0.1% per day ≈ 25% over
        // a 250-trading-day year — comfortably above noise.
        double slopePctPerDay = mean == 0 ? 0 : (slope / mean) * 100.0;
        double changePercent = ((closes[n - 1] - closes[0]) / closes[0]) * 100.0;
        String direction;
        String description;
        if (slopePctPerDay > 0.1) {
            direction = "UPWARD";
            description = "Seçilen aralıkta yükselen trend gözlemleniyor.";
        } else if (slopePctPerDay < -0.1) {
            direction = "DOWNWARD";
            description = "Seçilen aralıkta düşen trend gözlemleniyor.";
        } else {
            direction = "SIDEWAYS";
            description = "Seçilen aralıkta yatay bir hareket gözlemleniyor.";
        }
        log.info("[TechnicalAnalysis] trend={} slopePctPerDay={} changePercent={}",
                direction, slopePctPerDay, changePercent);
        return new TrendDto(direction, slope, changePercent, description);
    }

    /**
     * Volatility is computed as the standard deviation of daily log-returns,
     * annualised by √252. This is the standard finance definition and is
     * directly comparable between assets and across timeframes.
     */
    private SummaryDto summary(double[] closes, Double[] rsi) {
        int n = closes.length;
        double sum = 0, hi = Double.NEGATIVE_INFINITY, lo = Double.POSITIVE_INFINITY;
        for (double c : closes) {
            sum += c;
            if (c > hi) hi = c;
            if (c < lo) lo = c;
        }
        double avg = sum / n;
        double latest = closes[n - 1];

        double volPctAnnualised = 0;
        if (n >= 2) {
            double[] logReturns = new double[n - 1];
            double rSum = 0;
            for (int i = 1; i < n; i++) {
                logReturns[i - 1] = Math.log(closes[i] / closes[i - 1]);
                rSum += logReturns[i - 1];
            }
            double rMean = rSum / logReturns.length;
            double sqSum = 0;
            for (double r : logReturns) {
                double d = r - rMean;
                sqSum += d * d;
            }
            // Sample variance (unbiased, divide by n-1) when possible.
            double variance = logReturns.length > 1 ? sqSum / (logReturns.length - 1) : sqSum;
            double dailyStdev = Math.sqrt(variance);
            volPctAnnualised = dailyStdev * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100.0;
        }

        Double latestRsi = rsi.length > 0 ? rsi[rsi.length - 1] : null;
        log.info("[TechnicalAnalysis] latest={} hi={} lo={} avg={} annualisedVol={}% rsi={}",
                latest, hi, lo, avg, volPctAnnualised, latestRsi);
        return new SummaryDto(latest, hi, lo, avg, volPctAnnualised, latestRsi);
    }

    private BigDecimal getClosePrice(HistoricalPrice p) {
        return p.getAdjustedClosePrice() != null ? p.getAdjustedClosePrice() : p.getClosePrice();
    }

    private TechnicalAnalysisResponse insufficient(String symbol, LocalDate from, LocalDate to) {
        TrendDto trend = new TrendDto("INSUFFICIENT_DATA", 0.0, 0.0,
                "Bu enstrüman için teknik analiz oluşturacak yeterli tarihsel veri bulunamadı.");
        SummaryDto summary = new SummaryDto(0.0, 0.0, 0.0, 0.0, 0.0, null);
        return new TechnicalAnalysisResponse(symbol, from.toString(), to.toString(),
                trend, summary, new ArrayList<>());
    }

    // ── Legacy passthrough used by older callers ───────────────────────

    /** Legacy wrapper: returns the full 3-month analysis bundle under a "data" key. */
    public Map<String, Object> calculateMovingAverages(String symbol, int period) {
        LocalDate to = LocalDate.now();
        return Map.of("data", getTechnicalAnalysis(symbol, to.minusMonths(3), to));
    }

    /** Legacy wrapper: returns just the trend classification over the last 3 months. */
    public Map<String, Object> analyzeTrend(String symbol) {
        LocalDate to = LocalDate.now();
        return Map.of("trend", getTechnicalAnalysis(symbol, to.minusMonths(3), to).getTrend());
    }

    /** Legacy wrapper: approximates support/resistance as the 3-month low/high close. */
    public Map<String, Object> calculateSupportResistance(String symbol) {
        LocalDate to = LocalDate.now();
        TechnicalAnalysisResponse r = getTechnicalAnalysis(symbol, to.minusMonths(3), to);
        return Map.of("support", r.getSummary().getLowestClose(),
                "resistance", r.getSummary().getHighestClose());
    }

    /** Legacy wrapper: returns the 3-month percentage change as a momentum proxy. */
    public Map<String, Object> calculateMomentum(String symbol, int period) {
        LocalDate to = LocalDate.now();
        return Map.of("momentum", getTechnicalAnalysis(symbol, to.minusMonths(3), to)
                .getTrend().getChangePercent());
    }
}

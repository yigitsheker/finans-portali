// Technical-indicator math for the native chart. Inputs are candle bars
// ({ time, open, high, low, close, volume }); outputs are lightweight-charts
// line/histogram point arrays ({ time, value[, color] }). All client-side so
// the chart stays in sync with whatever candles the backend returned.

function round(v) {
    return Math.round(v * 10000) / 10000;
}

/** Simple Moving Average of close over `period`. */
export function sma(bars, period) {
    const out = [];
    let sum = 0;
    for (let i = 0; i < bars.length; i++) {
        sum += bars[i].close;
        if (i >= period) sum -= bars[i - period].close;
        if (i >= period - 1) out.push({ time: bars[i].time, value: round(sum / period) });
    }
    return out;
}

/** EMA over a numeric array (nulls preserved/skipped); seeds with an SMA. */
function ema(values, period) {
    const k = 2 / (period + 1);
    const out = new Array(values.length).fill(null);
    let prev = null, sum = 0, count = 0;
    for (let i = 0; i < values.length; i++) {
        const v = values[i];
        if (v == null) continue;
        if (prev === null) {
            sum += v; count += 1;
            if (count === period) { prev = sum / period; out[i] = prev; }
        } else {
            prev = v * k + prev * (1 - k);
            out[i] = prev;
        }
    }
    return out;
}

function rsiVal(gain, loss) {
    if (loss === 0) return 100;
    const rs = gain / loss;
    return round(100 - 100 / (1 + rs));
}

/** Wilder's RSI over `period` (default 14). */
export function rsi(bars, period = 14) {
    const out = [];
    if (bars.length <= period) return out;
    let avgGain = 0, avgLoss = 0;
    for (let i = 1; i <= period; i++) {
        const ch = bars[i].close - bars[i - 1].close;
        avgGain += Math.max(ch, 0);
        avgLoss += Math.max(-ch, 0);
    }
    avgGain /= period; avgLoss /= period;
    out.push({ time: bars[period].time, value: rsiVal(avgGain, avgLoss) });
    for (let i = period + 1; i < bars.length; i++) {
        const ch = bars[i].close - bars[i - 1].close;
        avgGain = (avgGain * (period - 1) + Math.max(ch, 0)) / period;
        avgLoss = (avgLoss * (period - 1) + Math.max(-ch, 0)) / period;
        out.push({ time: bars[i].time, value: rsiVal(avgGain, avgLoss) });
    }
    return out;
}

/** MACD(12,26,9): returns { macdLine, signalLine, hist }. */
export function macd(bars, fast = 12, slow = 26, signalP = 9, up = "#16a34a", down = "#dc2626") {
    const closes = bars.map((b) => b.close);
    const emaFast = ema(closes, fast);
    const emaSlow = ema(closes, slow);
    const macdArr = closes.map((_, i) =>
        (emaFast[i] != null && emaSlow[i] != null) ? emaFast[i] - emaSlow[i] : null);
    const signalArr = ema(macdArr, signalP);

    const macdLine = [], signalLine = [], hist = [];
    for (let i = 0; i < bars.length; i++) {
        const t = bars[i].time;
        if (macdArr[i] != null) macdLine.push({ time: t, value: round(macdArr[i]) });
        if (signalArr[i] != null) signalLine.push({ time: t, value: round(signalArr[i]) });
        if (macdArr[i] != null && signalArr[i] != null) {
            const h = macdArr[i] - signalArr[i];
            hist.push({ time: t, value: round(h), color: h >= 0 ? up : down });
        }
    }
    return { macdLine, signalLine, hist };
}

/** Volume points coloured by candle direction. */
export function volume(bars, up = "rgba(22,163,74,0.5)", down = "rgba(220,38,38,0.5)") {
    return bars.map((b) => ({
        time: b.time,
        value: b.volume == null ? 0 : b.volume,
        color: b.close >= b.open ? up : down,
    }));
}

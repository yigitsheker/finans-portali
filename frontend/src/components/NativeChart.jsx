import { useCallback, useEffect, useRef, useState } from "react";
import PropTypes from "prop-types";
import {
    createChart, ColorType, CrosshairMode, LineStyle,
    CandlestickSeries, HistogramSeries, LineSeries,
} from "lightweight-charts";
import { getCandles } from "../api/marketChartApi";
import { sma, rsi, macd, volume as volumePoints } from "../utils/indicators";
import { useI18n } from "../contexts/I18nContext";

const PERIODS = ["1G", "5G", "1A", "1Y"];
const UP = "#16a34a";
const DOWN = "#dc2626";
const THEME = { bg: "#0d1117", text: "#9ba7b4", grid: "rgba(255,255,255,0.06)", border: "#222a33" };

/**
 * Native candlestick chart powered by the app's OWN data
 * (/api/v1/market/candles) via lightweight-charts v5. Works for every symbol —
 * including BIST — unlike the TradingView free embed which gated BIST data.
 * Candles + volume + MA(9), plus RSI(14) and MACD(12,26,9) as built-in panes
 * (shared time axis, no sync code), timeframe switching, a crosshair OHLC
 * legend, and a horizontal-line drawing tool persisted per-symbol.
 */
export default function NativeChart({ symbol }) {
    const { t } = useI18n();
    const [period, setPeriod] = useState("1A");
    const [bars, setBars] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [showMA, setShowMA] = useState(true);
    const [showRSI, setShowRSI] = useState(true);
    const [showMACD, setShowMACD] = useState(true);
    const [drawMode, setDrawMode] = useState(false);

    const mainRef = useRef(null);
    const legendRef = useRef(null);
    const candleSeriesRef = useRef(null);
    const priceLinesRef = useRef([]);
    const drawModeRef = useRef(false);

    const hlKey = `chart-hlines-${symbol}`;
    const [hlines, setHlines] = useState([]);

    useEffect(() => { drawModeRef.current = drawMode; }, [drawMode]);

    useEffect(() => {
        try { setHlines(JSON.parse(localStorage.getItem(hlKey)) || []); }
        catch { setHlines([]); }
    }, [hlKey]);

    useEffect(() => {
        let cancelled = false;
        setLoading(true); setError(null);
        getCandles(symbol, period)
            .then((data) => { if (!cancelled) { setBars(data); setLoading(false); } })
            .catch(() => { if (!cancelled) { setError(t("nativeChart.loadError")); setLoading(false); } });
        return () => { cancelled = true; };
    }, [symbol, period, t]);

    const applyHlines = useCallback((prices) => {
        const cs = candleSeriesRef.current;
        if (!cs) return;
        priceLinesRef.current.forEach((pl) => { try { cs.removePriceLine(pl); } catch { /* gone */ } });
        priceLinesRef.current = prices.map((price) => cs.createPriceLine({
            price, color: "#eab308", lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true, title: "",
        }));
    }, []);

    // Build / rebuild the chart when data or visible panes change.
    useEffect(() => {
        if (loading || error || bars.length === 0 || !mainRef.current) return;

        const chart = createChart(mainRef.current, {
            layout: { background: { type: ColorType.Solid, color: THEME.bg }, textColor: THEME.text },
            grid: { vertLines: { color: THEME.grid }, horzLines: { color: THEME.grid } },
            rightPriceScale: { borderColor: THEME.border },
            timeScale: {
                borderColor: THEME.border,
                timeVisible: period === "1G" || period === "5G",
                secondsVisible: false,
            },
            crosshair: { mode: CrosshairMode.Normal },
            autoSize: true,
        });

        const candle = chart.addSeries(CandlestickSeries, {
            upColor: UP, downColor: DOWN, borderUpColor: UP, borderDownColor: DOWN,
            wickUpColor: UP, wickDownColor: DOWN,
        });
        candle.setData(bars.map((b) => ({ time: b.time, open: b.open, high: b.high, low: b.low, close: b.close })));
        candleSeriesRef.current = candle;

        const vol = chart.addSeries(HistogramSeries, { priceScaleId: "vol", priceFormat: { type: "volume" } });
        chart.priceScale("vol").applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });
        vol.setData(volumePoints(bars));

        if (showMA && bars.length > 9) {
            const ma = chart.addSeries(LineSeries, {
                color: "#3b82f6", lineWidth: 2, priceLineVisible: false,
                lastValueVisible: false, crosshairMarkerVisible: false,
            });
            ma.setData(sma(bars, 9));
        }

        // RSI / MACD as additional panes (shared time axis).
        let pane = 1;
        if (showRSI && bars.length > 15) {
            const rsiSeries = chart.addSeries(LineSeries, { color: "#a855f7", lineWidth: 1, priceLineVisible: false }, pane);
            rsiSeries.setData(rsi(bars, 14));
            rsiSeries.createPriceLine({ price: 70, color: "rgba(220,38,38,0.5)", lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true });
            rsiSeries.createPriceLine({ price: 30, color: "rgba(22,163,74,0.5)", lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true });
            pane += 1;
        }
        if (showMACD && bars.length > 26) {
            const { macdLine, signalLine, hist } = macd(bars);
            chart.addSeries(HistogramSeries, { priceLineVisible: false }, pane).setData(hist);
            chart.addSeries(LineSeries, { color: "#3b82f6", lineWidth: 1, priceLineVisible: false, lastValueVisible: false }, pane).setData(macdLine);
            chart.addSeries(LineSeries, { color: "#f59e0b", lineWidth: 1, priceLineVisible: false, lastValueVisible: false }, pane).setData(signalLine);
            pane += 1;
        }

        // Give the price pane most of the height; sub-panes share the rest.
        try {
            const panes = chart.panes();
            if (panes[0]) panes[0].setStretchFactor(3);
            for (let i = 1; i < panes.length; i++) panes[i].setStretchFactor(1);
        } catch { /* setStretchFactor unavailable — default distribution is fine */ }

        chart.timeScale().fitContent();

        chart.subscribeCrosshairMove((param) => {
            const el = legendRef.current;
            if (!el) return;
            const d = param.seriesData?.get(candle);
            if (!d) { el.textContent = ""; return; }
            el.textContent = `A ${fmt(d.open)}  Y ${fmt(d.high)}  D ${fmt(d.low)}  K ${fmt(d.close)}`;
            el.style.color = d.close >= d.open ? UP : DOWN;
        });

        chart.subscribeClick((param) => {
            if (!drawModeRef.current || !param.point) return;
            const price = candle.coordinateToPrice(param.point.y);
            if (price == null) return;
            const rounded = Math.round(price * 10000) / 10000;
            setHlines((prev) => {
                const next = [...prev, rounded];
                try { localStorage.setItem(hlKey, JSON.stringify(next)); } catch { /* quota */ }
                return next;
            });
        });

        applyHlines(hlines);

        return () => {
            try { chart.remove(); } catch { /* already gone */ }
            candleSeriesRef.current = null;
            priceLinesRef.current = [];
        };
        // hlines applied imperatively via applyHlines — excluded to avoid full rebuild on add
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [bars, loading, error, showMA, showRSI, showMACD, period, hlKey, applyHlines]);

    useEffect(() => { applyHlines(hlines); }, [hlines, applyHlines]);

    const clearHlines = () => {
        setHlines([]);
        try { localStorage.removeItem(hlKey); } catch { /* ignore */ }
    };

    return (
        <div style={s.wrap}>
            <div style={s.toolbar}>
                <div style={s.grp}>
                    {PERIODS.map((p) => (
                        <button key={p} type="button"
                            style={{ ...s.btn, ...(period === p ? s.btnActive : {}) }}
                            onClick={() => setPeriod(p)}>{p}</button>
                    ))}
                </div>
                <div style={s.grp}>
                    <button type="button" style={{ ...s.btn, ...(showMA ? s.btnActive : {}) }} onClick={() => setShowMA((v) => !v)}>MA</button>
                    <button type="button" style={{ ...s.btn, ...(showRSI ? s.btnActive : {}) }} onClick={() => setShowRSI((v) => !v)}>RSI</button>
                    <button type="button" style={{ ...s.btn, ...(showMACD ? s.btnActive : {}) }} onClick={() => setShowMACD((v) => !v)}>MACD</button>
                </div>
                <div style={s.grp}>
                    <button type="button" style={{ ...s.btn, ...(drawMode ? s.btnActive : {}) }}
                        onClick={() => setDrawMode((v) => !v)} title={t("nativeChart.hLineHint")}>╾ {t("nativeChart.hLine")}</button>
                    <button type="button" style={s.btn} onClick={clearHlines}>{t("nativeChart.clear")}</button>
                </div>
                <div ref={legendRef} style={s.legend} />
            </div>

            {loading ? (
                <div style={s.state}>{t("nativeChart.loading")}</div>
            ) : error ? (
                <div style={{ ...s.state, color: DOWN }}>{error}</div>
            ) : bars.length === 0 ? (
                <div style={s.state}>{t("nativeChart.empty")}</div>
            ) : (
                <div ref={mainRef} style={s.chart} />
            )}
        </div>
    );
}

NativeChart.propTypes = { symbol: PropTypes.string.isRequired };

function fmt(v) {
    if (v == null) return "—";
    return Number(v).toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const s = {
    wrap: { display: "flex", flexDirection: "column", height: "100%", gap: 8 },
    toolbar: { display: "flex", flexWrap: "wrap", alignItems: "center", gap: 10 },
    grp: { display: "inline-flex", gap: 4, background: "#161b22", border: "1px solid #222a33", borderRadius: 8, padding: 3 },
    btn: { padding: "5px 10px", borderRadius: 6, border: "none", background: "transparent", color: "#9ba7b4", fontSize: 12, fontWeight: 600, cursor: "pointer" },
    btnActive: { background: "rgba(34,197,94,0.15)", color: "#22c55e" },
    legend: { marginLeft: "auto", fontSize: 12, fontVariantNumeric: "tabular-nums", minHeight: 16, color: "#9ba7b4" },
    chart: { flex: 1, minHeight: 0, width: "100%", borderRadius: 8, overflow: "hidden", background: THEME.bg },
    state: { flex: 1, display: "grid", placeItems: "center", color: "#9ba7b4", fontSize: 14 },
};

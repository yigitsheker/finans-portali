import { useEffect, useRef, useState } from "react";
import PropTypes from "prop-types";
import { init, dispose } from "klinecharts";
import { getCandles, searchInstruments } from "../api/marketChartApi";
import { useI18n } from "../contexts/I18nContext";

const PERIODS = ["1G", "5G", "1A", "1Y"];

// Built-in klinecharts drawing overlays — the real "trader toolset".
const TOOLS = [
    { name: "horizontalStraightLine", key: "chartTools.hline" },
    { name: "verticalStraightLine", key: "chartTools.vline" },
    { name: "segment", key: "chartTools.trend" },
    { name: "rayLine", key: "chartTools.ray" },
    { name: "straightLine", key: "chartTools.line" },
    { name: "priceLine", key: "chartTools.priceLine" },
    { name: "parallelStraightLine", key: "chartTools.parallel" },
    { name: "priceChannelLine", key: "chartTools.channel" },
    { name: "fibonacciLine", key: "chartTools.fib" },
];

const AXIS = "#222a33";
const TXT = "#9ba7b4";
const UP = "#16a34a";
const DOWN = "#dc2626";

const DARK_STYLES = {
    grid: { horizontal: { color: "#1b232c" }, vertical: { color: "#1b232c" } },
    candle: {
        bar: {
            upColor: UP, downColor: DOWN, noChangeColor: "#888",
            upBorderColor: UP, downBorderColor: DOWN, noChangeBorderColor: "#888",
            upWickColor: UP, downWickColor: DOWN, noChangeWickColor: "#888",
        },
        priceMark: {
            high: { color: TXT }, low: { color: TXT },
            last: { text: { color: "#fff" } },
        },
        tooltip: { text: { color: TXT } },
    },
    indicator: { tooltip: { text: { color: TXT } } },
    xAxis: { axisLine: { color: AXIS }, tickLine: { color: AXIS }, tickText: { color: TXT } },
    yAxis: { axisLine: { color: AXIS }, tickLine: { color: AXIS }, tickText: { color: TXT } },
    separator: { color: AXIS },
    crosshair: {
        horizontal: { line: { color: "#6b7280" }, text: { backgroundColor: "#33415c" } },
        vertical: { line: { color: "#6b7280" }, text: { backgroundColor: "#33415c" } },
    },
};

const toBars = (data) => data.map((c) => ({
    timestamp: c.time * 1000,
    open: c.open, high: c.high, low: c.low, close: c.close, volume: c.volume,
}));

// One-way view sync: copy the main chart's zoom (bar space) and right-edge
// offset onto the comparison chart so the two candlestick charts stay aligned
// by date. The comparison chart has scroll/zoom disabled, so this never loops.
const mirrorView = (src, dst) => {
    if (!src || !dst) return;
    try {
        dst.setBarSpace(src.getBarSpace());
        dst.setOffsetRightDistance(src.getOffsetRightDistance());
    } catch { /* charts not ready */ }
};

/**
 * Detailed chart backed by the app's OWN OHLC data (/api/v1/market/candles),
 * rendered with klinecharts — which ships a full trader drawing-tool suite
 * (trend/ray/line, horizontal & vertical, price line, parallel & channel,
 * Fibonacci) plus MA/VOL/RSI/MACD indicators. Works for every symbol incl.
 * BIST. Drawn overlays persist per-symbol in localStorage.
 *
 * Comparison shows the second symbol as its OWN candlestick chart stacked
 * below the main one (a second klinecharts instance), with the two views
 * kept in sync — instead of a single normalized % line.
 */
export default function KLineChart({ symbol }) {
    const { t } = useI18n();
    const elRef = useRef(null);
    const chartRef = useRef(null);
    const cmpElRef = useRef(null);
    const cmpChartRef = useRef(null);
    const panesRef = useRef({}); // indicator name -> paneId
    const [period, setPeriod] = useState("1A");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [cmpLoading, setCmpLoading] = useState(false);
    // MA + VOL on by default; RSI/MACD are opt-in to keep the default view
    // uncluttered (no surprise extra panes at the bottom).
    const [ind, setInd] = useState({ MA: true, VOL: true, RSI: false, MACD: false });
    const [activeTool, setActiveTool] = useState(null);
    const [compareSymbol, setCompareSymbol] = useState(null);
    const [compareInput, setCompareInput] = useState("");
    const [suggestions, setSuggestions] = useState([]);
    const [showSug, setShowSug] = useState(false);

    const ovKey = `chart-overlays-${symbol}`;

    // ── init main chart once ─────────────────────────────────────────────────
    useEffect(() => {
        const chart = init(elRef.current);
        chart.setStyles(DARK_STYLES);
        chartRef.current = chart;
        // default indicators (MA on the candle pane, VOL in its own pane)
        panesRef.current.MA = chart.createIndicator("MA", true, { id: "candle_pane" });
        panesRef.current.VOL = chart.createIndicator("VOL");
        // keep the comparison chart aligned to the main chart's pan/zoom
        chart.subscribeAction("onScroll", () => mirrorView(chart, cmpChartRef.current));
        chart.subscribeAction("onZoom", () => mirrorView(chart, cmpChartRef.current));

        const ro = new ResizeObserver(() => { try { chart.resize(); } catch { /* disposed */ } });
        ro.observe(elRef.current);
        return () => {
            ro.disconnect();
            try { dispose(elRef.current); } catch { /* already disposed */ }
            chartRef.current = null;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // ── load candles on symbol/period change ─────────────────────────────────
    useEffect(() => {
        const chart = chartRef.current;
        if (!chart) return;
        let cancelled = false;
        setLoading(true); setError(null);
        getCandles(symbol, period)
            .then((data) => {
                if (cancelled || !chartRef.current) return;
                chart.applyNewData(toBars(data));
                restoreOverlays();
                mirrorView(chart, cmpChartRef.current);
                setLoading(false);
            })
            .catch(() => { if (!cancelled) { setError(t("nativeChart.loadError")); setLoading(false); } });
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [symbol, period]);

    // ── comparison: second candlestick chart for the compare symbol ──────────
    // Created when a compare symbol is chosen, reloaded on period change, and
    // disposed when cleared. Scroll/zoom disabled — it follows the main chart.
    useEffect(() => {
        if (!compareSymbol) return;
        const el = cmpElRef.current;
        if (!el) return;
        const chart = init(el);
        chart.setStyles(DARK_STYLES);
        chart.setScrollEnabled(false);
        chart.setZoomEnabled(false);
        chart.createIndicator("MA", true, { id: "candle_pane" });
        cmpChartRef.current = chart;

        const ro = new ResizeObserver(() => { try { chart.resize(); } catch { /* disposed */ } });
        ro.observe(el);

        let cancelled = false;
        setCmpLoading(true);
        getCandles(compareSymbol, period)
            .then((data) => {
                if (cancelled) return;
                chart.applyNewData(toBars(data));
                mirrorView(chartRef.current, chart);
                setCmpLoading(false);
            })
            .catch(() => { if (!cancelled) setCmpLoading(false); });

        return () => {
            cancelled = true;
            ro.disconnect();
            try { dispose(chart); } catch { /* already disposed */ }
            cmpChartRef.current = null;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [compareSymbol, period]);

    // ── comparison autocomplete (debounced instrument search) ────────────────
    useEffect(() => {
        if (compareSymbol) { setSuggestions([]); setShowSug(false); return; }
        const q = compareInput.trim();
        if (q.length < 2) { setSuggestions([]); setShowSug(false); return; }
        let cancelled = false;
        const id = setTimeout(() => {
            searchInstruments(q).then((r) => {
                if (cancelled) return;
                const list = r.filter((x) => (x.symbol || "").toUpperCase() !== symbol).slice(0, 8);
                setSuggestions(list);
                setShowSug(list.length > 0);
            });
        }, 250);
        return () => { cancelled = true; clearTimeout(id); };
    }, [compareInput, compareSymbol, symbol]);

    // ── overlay persistence ──────────────────────────────────────────────────
    function saveOverlays() {
        const chart = chartRef.current;
        if (!chart?.getOverlays) return;
        try {
            const list = (chart.getOverlays() || []).map((o) => ({
                name: o.name,
                points: (o.points || []).map((p) => ({ timestamp: p.timestamp, value: p.value })),
            })).filter((o) => o.points.length > 0);
            localStorage.setItem(ovKey, JSON.stringify(list));
        } catch { /* quota / api shape */ }
    }

    function restoreOverlays() {
        const chart = chartRef.current;
        if (!chart) return;
        try { chart.removeOverlay(); } catch { /* none */ }
        let saved = [];
        try { saved = JSON.parse(localStorage.getItem(ovKey)) || []; } catch { saved = []; }
        saved.forEach((o) => { try { chart.createOverlay({ name: o.name, points: o.points }); } catch { /* skip */ } });
    }

    // ── handlers ─────────────────────────────────────────────────────────────
    const pickTool = (name) => {
        const chart = chartRef.current;
        if (!chart) return;
        setActiveTool(name);
        chart.createOverlay({ name, onDrawEnd: () => { saveOverlays(); setActiveTool(null); return true; } });
    };

    const clearTools = () => {
        const chart = chartRef.current;
        if (!chart) return;
        try { chart.removeOverlay(); } catch { /* none */ }
        try { localStorage.removeItem(ovKey); } catch { /* ignore */ }
        setActiveTool(null);
    };

    const toggleInd = (name) => {
        const chart = chartRef.current;
        if (!chart) return;
        const on = !ind[name];
        try {
            if (on) {
                panesRef.current[name] = name === "MA"
                    ? chart.createIndicator("MA", true, { id: "candle_pane" })
                    : chart.createIndicator(name);
            } else if (name === "MA") {
                chart.removeIndicator("candle_pane", "MA");
            } else if (panesRef.current[name]) {
                chart.removeIndicator(panesRef.current[name], name);
            }
        } catch { /* indicator api */ }
        setInd((prev) => ({ ...prev, [name]: on }));
    };

    const pickCompare = (sym) => {
        const v = (sym || "").toUpperCase();
        if (v && v !== symbol) setCompareSymbol(v);
        setCompareInput(""); setSuggestions([]); setShowSug(false);
    };
    const applyCompare = () => {
        // Enter with an open list picks the first match; otherwise use raw text.
        if (suggestions.length > 0) { pickCompare(suggestions[0].symbol); return; }
        pickCompare(compareInput.trim());
    };
    const clearCompare = () => {
        setCompareSymbol(null); setCompareInput(""); setSuggestions([]); setShowSug(false);
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
                    {["MA", "VOL", "RSI", "MACD"].map((n) => (
                        <button key={n} type="button"
                            style={{ ...s.btn, ...(ind[n] ? s.btnActive : {}) }}
                            onClick={() => toggleInd(n)}>{n}</button>
                    ))}
                </div>
                <div style={s.grp}>
                    {TOOLS.map((tool) => (
                        <button key={tool.name} type="button"
                            style={{ ...s.btn, ...(activeTool === tool.name ? s.btnActive : {}) }}
                            onClick={() => pickTool(tool.name)}>{t(tool.key)}</button>
                    ))}
                    <button type="button" style={s.btn} onClick={clearTools}>{t("nativeChart.clear")}</button>
                </div>
                <div style={s.grp}>
                    {compareSymbol ? (
                        <span style={s.cmpChip}>
                            {t("chartTools.compare")}: {compareSymbol}
                            <button type="button" style={s.cmpX} onClick={clearCompare} aria-label="x">✕</button>
                        </span>
                    ) : (
                        <div style={s.cmpSearchWrap}>
                            <input
                                value={compareInput}
                                onChange={(e) => setCompareInput(e.target.value.toUpperCase())}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") applyCompare();
                                    else if (e.key === "Escape") setShowSug(false);
                                }}
                                onFocus={() => { if (suggestions.length) setShowSug(true); }}
                                onBlur={() => setTimeout(() => setShowSug(false), 150)}
                                placeholder={t("chartTools.comparePh")}
                                style={s.cmpInput}
                            />
                            <button type="button" style={s.btn} onClick={applyCompare}>{t("chartTools.compare")}</button>
                            {showSug && (
                                <div style={s.sugBox}>
                                    {suggestions.map((it) => (
                                        <button key={it.symbol} type="button" style={s.sugItem}
                                            onMouseDown={() => pickCompare(it.symbol)}>
                                            <span style={s.sugSym}>{it.symbol}</span>
                                            <span style={s.sugName}>{it.name}</span>
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </div>

            {error && <div style={s.error}>{error}</div>}
            <div style={s.chartBox}>
                <div style={{ ...s.pane, flex: compareSymbol ? 1.8 : 1 }}>
                    <span style={s.paneLabel}>{symbol}</span>
                    <div ref={elRef} style={s.chart} />
                    {loading && <div style={s.loading}>{t("nativeChart.loading")}</div>}
                </div>
                {compareSymbol && (
                    <div style={{ ...s.pane, flex: 1, borderTop: `2px solid ${AXIS}` }}>
                        <span style={s.paneLabel}>{compareSymbol}</span>
                        <div ref={cmpElRef} style={s.chart} />
                        {cmpLoading && <div style={s.loading}>{t("nativeChart.loading")}</div>}
                    </div>
                )}
            </div>
        </div>
    );
}

KLineChart.propTypes = { symbol: PropTypes.string.isRequired };

const s = {
    wrap: { display: "flex", flexDirection: "column", height: "100%", gap: 8 },
    toolbar: { display: "flex", flexWrap: "wrap", alignItems: "center", gap: 8 },
    grp: { display: "inline-flex", flexWrap: "wrap", gap: 4, background: "#161b22", border: "1px solid #222a33", borderRadius: 8, padding: 3 },
    btn: { padding: "5px 9px", borderRadius: 6, border: "none", background: "transparent", color: "#9ba7b4", fontSize: 12, fontWeight: 600, cursor: "pointer", whiteSpace: "nowrap" },
    btnActive: { background: "rgba(34,197,94,0.15)", color: "#22c55e" },
    cmpSearchWrap: { position: "relative", display: "inline-flex", gap: 4, alignItems: "center" },
    cmpInput: { width: 120, padding: "5px 8px", borderRadius: 6, border: "none", background: "transparent", color: "#e6edf3", fontSize: 12, outline: "none" },
    sugBox: { position: "absolute", top: "calc(100% + 4px)", left: 0, minWidth: 220, maxHeight: 260, overflowY: "auto", background: "#161b22", border: "1px solid #222a33", borderRadius: 8, boxShadow: "0 8px 24px rgba(0,0,0,0.4)", zIndex: 50 },
    sugItem: { display: "flex", flexDirection: "column", alignItems: "flex-start", gap: 1, width: "100%", padding: "7px 10px", border: "none", borderBottom: "1px solid #1b232c", background: "transparent", color: "#e6edf3", cursor: "pointer", textAlign: "left" },
    sugSym: { fontSize: 12.5, fontWeight: 700, color: "#e6edf3" },
    sugName: { fontSize: 11, color: "#9ba7b4" },
    cmpChip: { display: "inline-flex", alignItems: "center", gap: 6, padding: "5px 9px", color: "#22c55e", fontSize: 12, fontWeight: 700 },
    cmpX: { border: "none", background: "transparent", color: "#9ba7b4", cursor: "pointer", fontSize: 12, padding: 0 },
    chartBox: { position: "relative", flex: 1, minHeight: 0, display: "flex", flexDirection: "column" },
    pane: { position: "relative", minHeight: 0 },
    paneLabel: { position: "absolute", top: 6, left: 8, zIndex: 5, padding: "2px 7px", borderRadius: 5, background: "rgba(22,27,34,0.7)", color: "#e6edf3", fontSize: 12, fontWeight: 700, pointerEvents: "none" },
    chart: { width: "100%", height: "100%" },
    loading: { position: "absolute", inset: 0, display: "grid", placeItems: "center", color: "#9ba7b4", fontSize: 14, pointerEvents: "none" },
    error: { padding: "8px 12px", borderRadius: 8, background: "rgba(220,38,38,0.1)", color: "#dc2626", fontSize: 13 },
};

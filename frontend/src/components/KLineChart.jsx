import { useCallback, useEffect, useRef, useState } from "react";
import PropTypes from "prop-types";
import { init, dispose, registerIndicator } from "klinecharts";
import { getCandles, searchInstruments } from "../api/marketChartApi";
import { useI18n } from "../contexts/I18nContext";

// Comparison indicator: plots the MAIN symbol and a comparison symbol both as
// % change from the first bar (base 0) in their own pane, so two instruments
// with very different price scales (e.g. GARAN ₺129 vs AKBNK ₺64) can be
// compared by shape/relative performance. The comparison closes-by-timestamp
// map is injected via the indicator's extendData. Registered once globally.
let compareRegistered = false;
function ensureCompareIndicator() {
    if (compareRegistered) return;
    try {
        registerIndicator({
            name: "COMPARE",
            shortName: "Karşılaştırma %",
            figures: [
                { key: "base", title: "Ana: ", type: "line" },
                { key: "cmp", title: "Karş.: ", type: "line" },
            ],
            calc: (dataList, indicator) => {
                const map = (indicator.extendData && indicator.extendData.map) || {};
                const baseFirst = dataList.length ? dataList[0].close : null;
                let cmpFirst = null;
                for (const d of dataList) {
                    const v = map[d.timestamp];
                    if (v != null) { cmpFirst = v; break; }
                }
                return dataList.map((d) => {
                    const base = baseFirst ? (d.close / baseFirst - 1) * 100 : null;
                    const cv = map[d.timestamp];
                    const cmp = (cmpFirst && cv != null) ? (cv / cmpFirst - 1) * 100 : null;
                    return { base, cmp };
                });
            },
        });
        compareRegistered = true;
    } catch { /* already registered */ }
}

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

/**
 * Detailed chart backed by the app's OWN OHLC data (/api/v1/market/candles),
 * rendered with klinecharts — which ships a full trader drawing-tool suite
 * (trend/ray/line, horizontal & vertical, price line, parallel & channel,
 * Fibonacci) plus MA/VOL/RSI/MACD indicators. Works for every symbol incl.
 * BIST. Drawn overlays persist per-symbol in localStorage.
 */
export default function KLineChart({ symbol }) {
    const { t } = useI18n();
    const elRef = useRef(null);
    const chartRef = useRef(null);
    const panesRef = useRef({}); // indicator name -> paneId
    const [period, setPeriod] = useState("1A");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [ind, setInd] = useState({ MA: true, VOL: true, RSI: true, MACD: true });
    const [activeTool, setActiveTool] = useState(null);
    const [compareSymbol, setCompareSymbol] = useState(null);
    const [compareInput, setCompareInput] = useState("");
    const [suggestions, setSuggestions] = useState([]);
    const [showSug, setShowSug] = useState(false);
    const compareCreatedRef = useRef(false);
    const compareMapRef = useRef(null);

    const ovKey = `chart-overlays-${symbol}`;

    // ── init chart once ──────────────────────────────────────────────────────
    useEffect(() => {
        ensureCompareIndicator();
        const chart = init(elRef.current);
        chart.setStyles(DARK_STYLES);
        chartRef.current = chart;
        // default indicators
        panesRef.current.MA = chart.createIndicator("MA", true, { id: "candle_pane" });
        panesRef.current.VOL = chart.createIndicator("VOL");
        panesRef.current.RSI = chart.createIndicator("RSI");
        panesRef.current.MACD = chart.createIndicator("MACD");

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
                chart.applyNewData(data.map((c) => ({
                    timestamp: c.time * 1000,
                    open: c.open, high: c.high, low: c.low, close: c.close, volume: c.volume,
                })));
                restoreOverlays();
                setLoading(false);
            })
            .catch(() => { if (!cancelled) { setError(t("nativeChart.loadError")); setLoading(false); } });
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [symbol, period]);

    // (Re)build the comparison pane from the cached map. Always removes then
    // recreates so it survives indicator toggles — klinecharts re-lays-out panes
    // when an indicator pane (e.g. VOL) is removed, which otherwise wiped the
    // comparison pane. Idempotent and self-healing.
    const renderCompare = useCallback(() => {
        const chart = chartRef.current;
        if (!chart) return;
        try { chart.removeIndicator("compare_pane", "COMPARE"); } catch { /* none */ }
        compareCreatedRef.current = false;
        if (!compareSymbol || !compareMapRef.current) return;
        try {
            chart.createIndicator({
                name: "COMPARE",
                shortName: `% ${symbol} / ${compareSymbol}`,
                extendData: { map: compareMapRef.current },
                styles: { lines: [{ color: "#3b82f6" }, { color: "#f59e0b" }] },
            }, false, { id: "compare_pane" });
            compareCreatedRef.current = true;
        } catch { /* indicator api */ }
    }, [compareSymbol, symbol]);

    // ── comparison symbol (% change pane) ─────────────────────────────────────
    useEffect(() => {
        const chart = chartRef.current;
        if (!chart) return;
        if (!compareSymbol) { compareMapRef.current = null; renderCompare(); return; }
        let cancelled = false;
        getCandles(compareSymbol, period)
            .then((data) => {
                if (cancelled || !chartRef.current) return;
                const map = {};
                data.forEach((c) => { map[c.time * 1000] = c.close; });
                compareMapRef.current = map;
                renderCompare();
            })
            .catch(() => { /* comparison fetch failed — leave chart as-is */ });
        return () => { cancelled = true; };
    }, [compareSymbol, period, renderCompare]);

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
        // Removing an indicator pane re-lays-out the panes; re-assert the
        // comparison pane so it doesn't get dropped.
        renderCompare();
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
                            % {symbol}/{compareSymbol}
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
                <div ref={elRef} style={s.chart} />
                {loading && <div style={s.loading}>{t("nativeChart.loading")}</div>}
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
    chartBox: { position: "relative", flex: 1, minHeight: 0 },
    chart: { width: "100%", height: "100%" },
    loading: { position: "absolute", inset: 0, display: "grid", placeItems: "center", color: "#9ba7b4", fontSize: 14, pointerEvents: "none" },
    error: { padding: "8px 12px", borderRadius: 8, background: "rgba(220,38,38,0.1)", color: "#dc2626", fontSize: 13 },
};

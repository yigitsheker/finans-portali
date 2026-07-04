import { useCallback, useEffect, useRef, useState, memo } from "react";
import { IconTrash, IconX } from "./common/icons";
import PropTypes from "prop-types";
import { init, dispose } from "klinecharts";
import { getCandles, searchInstruments } from "../api/marketChartApi";
import { useI18n } from "../contexts/I18nContext";

const PERIODS = ["1G", "5G", "1A", "1Y"];
const IND_NAMES = ["MA", "VOL", "RSI", "MACD"];
const MAX_COMPARES = 4;

// Each VOL/RSI/MACD sub-pane gets a FIXED (small) height so the candlestick
// keeps the lion's share of whatever vertical space its CandlePane has. Charts
// SHARE the viewport via flexGrow (flexBasis:0), so a compare chart stays
// visible WITHOUT scrolling — 1–2 charts always fit; only 3+ stacked charts
// overflow into the scroll fallback.
const SUB_PANE_H = 70;     // px per VOL / RSI / MACD sub-pane

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
        // Push the always-on OHLC/MA legend below the per-pane symbol chip
        // (top:6) so the two don't overlap in the top-left corner.
        tooltip: { offsetLeft: 8, offsetTop: 28, text: { color: TXT } },
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

// One-way view sync: copy the main chart's zoom (bar width) and right-edge
// offset onto a comparison chart so both anchor their most-recent bar at the
// same place and scroll/zoom together. Exact date alignment holds when the
// symbols share the same trading calendar (equal bar counts); otherwise the
// charts stay right-edge aligned and drift slightly on the far left. Only the
// main chart drives, so this never loops.
const mirrorView = (src, dst) => {
    if (!src || !dst || src === dst) return;
    try {
        dst.setBarSpace(src.getBarSpace());
        dst.setOffsetRightDistance(src.getOffsetRightDistance());
    } catch { /* charts not ready */ }
};

const overlayKey = (sym) => `chart-overlays-${sym}`;

// klinecharts 9.8 has NO getOverlays() — only getOverlayById(id). So we persist
// from the overlay IDs the parent already tracks (drawn + restored), resolving
// each via getOverlayById. The old getOverlays() call silently saved nothing.
function saveOverlays(chart, sym, ids) {
    if (!chart?.getOverlayById) return;
    try {
        const list = (ids || []).map((id) => {
            const o = chart.getOverlayById(id);
            if (!o) return null;
            return {
                name: o.name,
                points: (o.points || []).map((p) => ({ timestamp: p.timestamp, value: p.value })),
            };
        }).filter((o) => o && o.points.length > 0);
        localStorage.setItem(overlayKey(sym), JSON.stringify(list));
    } catch { /* quota / api shape */ }
}

// Standard overlay event wiring so the parent can track selection, removal and
// draw-order (for "delete selected" / "undo") across every chart. `cbs` is the
// stable callback bag from the parent.
function overlayEvents(chart, cbs) {
    return {
        onSelected: (e) => { cbs.onSelected(chart, e.overlay.id); return false; },
        onDeselected: (e) => { cbs.onDeselected(chart, e.overlay.id); return false; },
        onRemoved: (e) => { cbs.onRemoved(chart, e.overlay.id); return false; },
    };
}

function restoreOverlays(chart, sym, cbs) {
    if (!chart) return;
    try { chart.removeOverlay(); } catch { /* none */ }
    let saved = [];
    try { saved = JSON.parse(localStorage.getItem(overlayKey(sym))) || []; } catch { saved = []; }
    saved.forEach((o) => {
        try {
            const id = chart.createOverlay({ name: o.name, points: o.points, ...overlayEvents(chart, cbs) });
            if (typeof id === "string") cbs.onAdded(chart, id);
        } catch { /* skip */ }
    });
}

// Reconcile a chart's indicators against the shared on/off selection. MA is an
// overlay on the candle pane; VOL/RSI/MACD each get their own sub-pane. Applied
// identically to every chart so the toolbar selection drives all of them.
function applyIndicators(chart, paneIds, prev, next) {
    IND_NAMES.forEach((name) => {
        const was = !!prev[name];
        const now = !!next[name];
        if (was === now) return;
        try {
            if (now) {
                paneIds[name] = name === "MA"
                    ? chart.createIndicator("MA", true, { id: "candle_pane" })
                    : chart.createIndicator(name, false, { height: SUB_PANE_H });
            } else if (name === "MA") {
                chart.removeIndicator("candle_pane", "MA");
            } else if (paneIds[name]) {
                chart.removeIndicator(paneIds[name], name);
            }
        } catch { /* indicator api */ }
    });
}

/**
 * A single candlestick chart (one klinecharts instance) for one symbol. Loads
 * its own OHLC, applies the shared indicator selection, arms the shared drawing
 * tool on itself, restores its own drawn overlays (persisted per-symbol), and
 * registers with the parent so the parent can drive cross-chart sync.
 */
const CandlePane = memo(function CandlePane({
    paneKey, symbol, period, indicators, activeTool, isMain, grow, basis, borderTop,
    register, getMainChart, onMainView, onDrawn, overlayCbs,
}) {
    const { t } = useI18n();
    const elRef = useRef(null);
    const chartRef = useRef(null);
    const paneIdsRef = useRef({});
    const prevIndRef = useRef({});
    const restoredSymRef = useRef(null); // last symbol whose saved drawings were restored
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    // ── init this pane's chart once ──────────────────────────────────────────
    useEffect(() => {
        const chart = init(elRef.current);
        chart.setStyles(DARK_STYLES);
        chartRef.current = chart;
        applyIndicators(chart, paneIdsRef.current, {}, indicators);
        prevIndRef.current = { ...indicators };
        // Only the main chart drives the shared view; compares follow it.
        if (isMain) {
            chart.subscribeAction("onScroll", () => onMainView(chart));
            chart.subscribeAction("onZoom", () => onMainView(chart));
        }
        register(paneKey, chart, isMain, symbol);
        // ResizeObserver feedback guard: calling chart.resize() synchronously
        // in the callback makes klinecharts repaint, which nudges the container
        // by a sub-pixel and re-fires the observer → an endless resize loop that
        // reads on screen as flicker. Defer the resize to the next frame and
        // skip it unless the integer size actually changed, so it converges.
        let rafId = 0;
        let lastW = 0;
        let lastH = 0;
        const ro = new ResizeObserver((entries) => {
            const cr = entries[0]?.contentRect;
            if (!cr) return;
            const w = Math.round(cr.width);
            const h = Math.round(cr.height);
            if (w === lastW && h === lastH) return;
            lastW = w;
            lastH = h;
            cancelAnimationFrame(rafId);
            rafId = requestAnimationFrame(() => { try { chart.resize(); } catch { /* disposed */ } });
        });
        ro.observe(elRef.current);
        return () => {
            cancelAnimationFrame(rafId);
            ro.disconnect();
            register(paneKey, null, isMain, symbol);
            overlayCbs.onChartGone(chart);
            try { dispose(chart); } catch { /* already disposed */ }
            chartRef.current = null;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // ── load candles on symbol/period change ─────────────────────────────────
    useEffect(() => {
        const chart = chartRef.current;
        if (!chart) return;
        let cancelled = false;
        setLoading(true); setError(false);
        getCandles(symbol, period)
            .then((data) => {
                if (cancelled || !chartRef.current) return;
                chart.applyNewData(toBars(data));
                // Restore drawings only when the SYMBOL changes — not on a mere
                // period switch. Overlays are positioned by timestamp and survive
                // applyNewData, so a timeframe change keeps them (incl. unsaved
                // ones) instead of wiping them via restore's clear-all.
                if (restoredSymRef.current !== symbol) {
                    restoreOverlays(chart, symbol, overlayCbs);
                    restoredSymRef.current = symbol;
                }
                if (isMain) onMainView(chart);          // re-align compares to new data
                else mirrorView(getMainChart(), chart); // align this new compare to main
                setLoading(false);
            })
            .catch(() => { if (!cancelled) { setError(true); setLoading(false); } });
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [symbol, period]);

    // ── apply shared indicator selection ─────────────────────────────────────
    useEffect(() => {
        const chart = chartRef.current;
        if (!chart) return;
        applyIndicators(chart, paneIdsRef.current, prevIndRef.current, indicators);
        prevIndRef.current = { ...indicators };
    }, [indicators]);

    // ── arm the shared drawing tool on THIS pane ─────────────────────────────
    // Reactive: every pane (incl. ones added after a tool is picked) arms the
    // active tool. Drawing on any one pane finishes it (onDrawn clears the
    // selection); the cleanup then disarms the still-pending overlays on the
    // other panes. The `done` guard keeps the completed drawing from being
    // removed by its own cleanup. Drawings are NOT auto-saved — the toolbar's
    // "Kaydet" button persists them explicitly.
    useEffect(() => {
        const chart = chartRef.current;
        if (!chart || !activeTool) return;
        let overlayId = null;
        let done = false;
        try {
            overlayId = chart.createOverlay({
                name: activeTool,
                onDrawEnd: (e) => { done = true; overlayCbs.onAdded(chart, e.overlay.id); onDrawn(); return true; },
                ...overlayEvents(chart, overlayCbs),
            });
        } catch { /* overlay api */ }
        return () => {
            if (!done && typeof overlayId === "string") {
                try { chart.removeOverlay(overlayId); } catch { /* gone */ }
            }
        };
    }, [activeTool, symbol, onDrawn, overlayCbs]);

    return (
        <div style={{ ...s.pane, flexGrow: grow, flexBasis: basis, ...(borderTop ? { borderTop: `2px solid ${AXIS}` } : {}) }}>
            <span style={s.paneLabel}>{symbol}</span>
            <div ref={elRef} style={s.chart} />
            {loading && <div style={s.loading}>{t("nativeChart.loading")}</div>}
            {error && <div style={s.paneError}>{t("nativeChart.loadError")}</div>}
        </div>
    );
});

CandlePane.propTypes = {
    paneKey: PropTypes.string.isRequired,
    symbol: PropTypes.string.isRequired,
    period: PropTypes.string.isRequired,
    indicators: PropTypes.object.isRequired,
    activeTool: PropTypes.string,
    isMain: PropTypes.bool.isRequired,
    grow: PropTypes.number.isRequired,
    basis: PropTypes.number.isRequired,
    borderTop: PropTypes.bool,
    register: PropTypes.func.isRequired,
    getMainChart: PropTypes.func.isRequired,
    onMainView: PropTypes.func.isRequired,
    onDrawn: PropTypes.func.isRequired,
    overlayCbs: PropTypes.object.isRequired,
};

/**
 * Detailed chart backed by the app's OWN OHLC data (/api/v1/market/candles),
 * rendered with klinecharts — a full trader drawing-tool suite (trend/ray/line,
 * horizontal & vertical, price line, parallel & channel, Fibonacci) plus
 * MA/VOL/RSI/MACD. Works for every symbol incl. BIST.
 *
 * Comparison: up to MAX_COMPARES extra symbols, each shown as its OWN
 * candlestick chart stacked below the main one. The indicator selection AND the
 * drawing tools apply to EVERY chart, and the compares follow the main chart's
 * pan/zoom. Drawn overlays persist per-symbol in localStorage.
 */
export default function KLineChart({ symbol }) {
    const { t } = useI18n();
    const mainChartRef = useRef(null);
    const chartBoxRef = useRef(null);      // scroll container for the stacked charts
    const comparesRef = useRef(new Map()); // paneKey -> { chart, symbol }
    const selectedRef = useRef(null);      // { chart, id } of the selected overlay
    const historyRef = useRef([]);         // [{ chart, id }] in draw order (for undo)
    const flashRef = useRef(null);         // "Kaydedildi ✓" flash timer
    const [period, setPeriod] = useState("1A");
    // MA + VOL on by default; RSI/MACD opt-in to keep the view uncluttered.
    const [ind, setInd] = useState({ MA: true, VOL: true, RSI: false, MACD: false });
    const [activeTool, setActiveTool] = useState(null);
    const [hasSelection, setHasSelection] = useState(false);
    const [justSaved, setJustSaved] = useState(false);
    const [compareSymbols, setCompareSymbols] = useState([]);
    const [compareInput, setCompareInput] = useState("");
    const [suggestions, setSuggestions] = useState([]);
    const [showSug, setShowSug] = useState(false);

    // Stable overlay-event bag shared by every chart: tracks draw-order (undo),
    // the currently-selected overlay (delete-selected) and removals.
    const overlayCbs = useRef({
        onAdded: (chart, id) => { historyRef.current.push({ chart, id }); },
        onSelected: (chart, id) => { selectedRef.current = { chart, id }; setHasSelection(true); },
        onDeselected: (chart, id) => {
            const sel = selectedRef.current;
            if (sel && sel.chart === chart && sel.id === id) { selectedRef.current = null; setHasSelection(false); }
        },
        onRemoved: (chart, id) => {
            historyRef.current = historyRef.current.filter((h) => !(h.chart === chart && h.id === id));
            const sel = selectedRef.current;
            if (sel && sel.chart === chart && sel.id === id) { selectedRef.current = null; setHasSelection(false); }
        },
        // A pane was disposed (compare removed): drop its overlays from the undo
        // history and clear selection if it pointed there — dispose() does NOT
        // fire onRemoved, so without this they'd become phantom undo steps.
        onChartGone: (chart) => {
            historyRef.current = historyRef.current.filter((h) => h.chart !== chart);
            const sel = selectedRef.current;
            if (sel && sel.chart === chart) { selectedRef.current = null; setHasSelection(false); }
        },
    }).current;

    // ── chart registry (stable callbacks; CandlePane registers on mount) ──────
    const register = useCallback((key, chart, isMain, sym) => {
        if (isMain) { mainChartRef.current = chart; return; }
        if (chart) comparesRef.current.set(key, { chart, symbol: sym });
        else comparesRef.current.delete(key);
    }, []);
    const getMainChart = useCallback(() => mainChartRef.current, []);
    const onMainView = useCallback((mainChart) => {
        comparesRef.current.forEach(({ chart }) => mirrorView(mainChart, chart));
    }, []);
    const onDrawn = useCallback(() => setActiveTool(null), []);

    // Every live chart paired with the symbol its drawings persist under. Main
    // uses the current `symbol` prop; compares use their (stable) registry key.
    const allEntries = () => {
        const list = [];
        if (mainChartRef.current) list.push({ chart: mainChartRef.current, symbol });
        comparesRef.current.forEach(({ chart, symbol: sym }) => list.push({ chart, symbol: sym }));
        return list;
    };

    // ── handlers ─────────────────────────────────────────────────────────────
    const toggleInd = (name) => setInd((prev) => ({ ...prev, [name]: !prev[name] }));

    // Pick a tool: just flip the shared selection. Each pane arms itself (see
    // CandlePane's arming effect), so newly-added panes become drawable too.
    const pickTool = (name) => setActiveTool((prev) => (prev === name ? null : name));

    // Undo: remove the most recently drawn overlay (across all charts). On
    // success onRemoved prunes history; on a genuine removal failure, drop that
    // specific entry from the live ref so the next undo advances.
    const undo = () => {
        const h = historyRef.current;
        if (!h.length) return;
        const last = h[h.length - 1];
        try { last.chart.removeOverlay(last.id); }
        catch { historyRef.current = historyRef.current.filter((x) => x !== last); }
    };

    // Delete only the overlay the user has selected (clicked) — not everything.
    const deleteSelected = () => {
        const sel = selectedRef.current;
        if (!sel) return;
        try { sel.chart.removeOverlay(sel.id); } catch { /* gone */ }
    };

    // Explicit save: persist the current drawings of every chart (per symbol).
    const saveDrawings = () => {
        allEntries().forEach(({ chart, symbol: sym }) => {
            const ids = historyRef.current.filter((h) => h.chart === chart).map((h) => h.id);
            saveOverlays(chart, sym, ids);
        });
        setJustSaved(true);
        clearTimeout(flashRef.current);
        flashRef.current = setTimeout(() => setJustSaved(false), 1500);
    };

    const addCompare = (raw) => {
        const v = (raw || "").toUpperCase().trim();
        setCompareInput(""); setSuggestions([]); setShowSug(false);
        if (!v || v === symbol) return;
        setCompareSymbols((prev) =>
            (prev.includes(v) || prev.length >= MAX_COMPARES) ? prev : [...prev, v]);
    };
    const applyCompare = () => addCompare(suggestions.length ? suggestions[0].symbol : compareInput);
    const removeCompare = (sym) => setCompareSymbols((prev) => prev.filter((s) => s !== sym));

    // Changing the symbol or period reloads every pane (which clears in-progress
    // overlays); drop any armed tool so the toolbar state stays truthful.
    useEffect(() => { setActiveTool(null); }, [period, symbol]);

    // Clear the save-flash timer on unmount.
    useEffect(() => () => clearTimeout(flashRef.current), []);

    // Wheel over stacked charts: klinecharts swallows the wheel for zoom, which
    // makes the lower (compare) charts unreachable once the panes overflow the
    // viewport. A capture-phase native listener scrolls the container first and
    // stops klinecharts from zooming — EXCEPT at the very top/bottom edge, where
    // the wheel falls through to zoom the chart under the cursor. When nothing
    // overflows (single chart, few indicators) the chart zooms as usual.
    useEffect(() => {
        const el = chartBoxRef.current;
        if (!el) return;
        const onWheel = (e) => {
            if (el.scrollHeight <= el.clientHeight + 1) return;      // fits → let chart zoom
            const atTop = el.scrollTop <= 0;
            const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 1;
            if ((e.deltaY < 0 && atTop) || (e.deltaY > 0 && atBottom)) return; // edges → zoom
            el.scrollTop += e.deltaY;
            e.preventDefault();
            e.stopPropagation();
        };
        el.addEventListener("wheel", onWheel, { capture: true, passive: false });
        return () => el.removeEventListener("wheel", onWheel, { capture: true });
    }, []);

    // ── comparison autocomplete (debounced instrument search) ────────────────
    useEffect(() => {
        const q = compareInput.trim();
        if (q.length < 2 || compareSymbols.length >= MAX_COMPARES) {
            setSuggestions([]); setShowSug(false); return;
        }
        let cancelled = false;
        const id = setTimeout(() => {
            searchInstruments(q).then((r) => {
                if (cancelled) return;
                const taken = new Set([symbol, ...compareSymbols]);
                const list = r.filter((x) => !taken.has((x.symbol || "").toUpperCase())).slice(0, 8);
                setSuggestions(list);
                setShowSug(list.length > 0);
            });
        }, 250);
        return () => { cancelled = true; clearTimeout(id); };
    }, [compareInput, compareSymbols, symbol]);

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
                    {IND_NAMES.map((n) => (
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
                </div>
                <div style={s.grp}>
                    <button type="button" style={s.btn} onClick={undo}
                        title={t("chartTools.undo")}>↶ {t("chartTools.undo")}</button>
                    <button type="button"
                        style={{ ...s.btn, ...(hasSelection ? {} : s.btnDisabled) }}
                        onClick={deleteSelected} disabled={!hasSelection}
                        title={t("chartTools.delSelected")}><IconTrash size={13} style={{ verticalAlign: "-2px", marginRight: 4 }} />{t("chartTools.delSelected")}</button>
                    <button type="button"
                        style={{ ...s.btn, ...(justSaved ? s.btnActive : {}) }}
                        onClick={saveDrawings}>{justSaved ? t("chartTools.saved") : t("chartTools.save")}</button>
                </div>
                <div style={s.grp}>
                    {compareSymbols.map((sym) => (
                        <span key={sym} style={s.cmpChip}>
                            {sym}
                            <button type="button" style={s.cmpX} onClick={() => removeCompare(sym)} aria-label="remove"><IconX size={12} /></button>
                        </span>
                    ))}
                    {compareSymbols.length < MAX_COMPARES && (
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
                                            onMouseDown={() => addCompare(it.symbol)}>
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

            <div ref={chartBoxRef} style={s.chartBox}>
                <CandlePane
                    key="main" paneKey="main" symbol={symbol} period={period} indicators={ind}
                    activeTool={activeTool} isMain grow={1.25} basis={0}
                    register={register} getMainChart={getMainChart} onMainView={onMainView}
                    onDrawn={onDrawn} overlayCbs={overlayCbs}
                />
                {compareSymbols.map((sym) => (
                    <CandlePane
                        key={sym} paneKey={sym} symbol={sym} period={period} indicators={ind}
                        activeTool={activeTool} isMain={false} grow={1} basis={0} borderTop
                        register={register} getMainChart={getMainChart} onMainView={onMainView}
                        onDrawn={onDrawn} overlayCbs={overlayCbs}
                    />
                ))}
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
    btnDisabled: { opacity: 0.4, cursor: "not-allowed" },
    cmpSearchWrap: { position: "relative", display: "inline-flex", gap: 4, alignItems: "center" },
    cmpInput: { width: 120, padding: "5px 8px", borderRadius: 6, border: "none", background: "transparent", color: "#e6edf3", fontSize: 12, outline: "none" },
    sugBox: { position: "absolute", top: "calc(100% + 4px)", left: 0, minWidth: 220, maxHeight: 260, overflowY: "auto", background: "#161b22", border: "1px solid #222a33", borderRadius: 8, boxShadow: "0 8px 24px rgba(0,0,0,0.4)", zIndex: 50 },
    sugItem: { display: "flex", flexDirection: "column", alignItems: "flex-start", gap: 1, width: "100%", padding: "7px 10px", border: "none", borderBottom: "1px solid #1b232c", background: "transparent", color: "#e6edf3", cursor: "pointer", textAlign: "left" },
    sugSym: { fontSize: 12.5, fontWeight: 700, color: "#e6edf3" },
    sugName: { fontSize: 11, color: "#9ba7b4" },
    cmpChip: { display: "inline-flex", alignItems: "center", gap: 6, padding: "5px 9px", color: "#22c55e", fontSize: 12, fontWeight: 700 },
    cmpX: { border: "none", background: "transparent", color: "#9ba7b4", cursor: "pointer", fontSize: 12, padding: 0 },
    // scrollbarGutter:stable reserves the scrollbar's width so a pane that
    // briefly overflows can't toggle the scrollbar on/off and oscillate the
    // chart width (another flicker source feeding the resize loop).
    chartBox: { position: "relative", flex: 1, minHeight: 0, display: "flex", flexDirection: "column", overflowY: "auto", scrollbarGutter: "stable" },
    pane: { position: "relative", flexShrink: 0, minHeight: 280 },
    paneLabel: { position: "absolute", top: 6, left: 8, zIndex: 5, padding: "2px 7px", borderRadius: 5, background: "rgba(22,27,34,0.7)", color: "#e6edf3", fontSize: 12, fontWeight: 700, pointerEvents: "none" },
    chart: { width: "100%", height: "100%" },
    loading: { position: "absolute", inset: 0, display: "grid", placeItems: "center", color: "#9ba7b4", fontSize: 14, pointerEvents: "none" },
    paneError: { position: "absolute", inset: 0, display: "grid", placeItems: "center", color: "#dc2626", fontSize: 13, pointerEvents: "none" },
};

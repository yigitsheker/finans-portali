import { useEffect, useRef, useState } from "react";
import { createChart, ColorType, LineSeries, HistogramSeries, LineStyle } from "lightweight-charts";
import { IconBarChart, IconTrendingUp, IconTrendingDown, IconArrowRight, IconAlertTriangle } from "./common/icons";
import { getTechnicalAnalysis } from "../api/portfolioApi";

// Maps the backend's analysis series to lightweight-charts rows ('YYYY-MM-DD'
// daily times, de-duplicated and ascending). Replaces the recharts dependency
// with the chart engine the rest of the app already ships.
function buildLwRows(series) {
    const out = [];
    const seen = new Set();
    for (const p of series || []) {
        const d = new Date(p.date);
        if (Number.isNaN(d.getTime())) continue;
        const time = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
        if (seen.has(time)) continue;
        seen.add(time);
        out.push({
            time,
            close: p.close,
            sma7: p.sma7, sma20: p.sma20, sma50: p.sma50,
            bbUpper: p.bbUpper, bbLower: p.bbLower,
            rsi: p.rsi14,
            macd: p.macd, macdSignal: p.macdSignal, macdHist: p.macdHist,
        });
    }
    return out;
}

/**
 * Generic single-pane chart on lightweight-charts. `seriesDefs` is an ordered
 * list of {field, color, lineWidth, lineStyle, type:'line'|'histogram',
 * colorFn, fixed:[min,max]}; `priceLines` are horizontal reference levels
 * attached to the first series. Recreated whenever `redrawKey` changes (data,
 * toggles or theme), which keeps the render path simple.
 */
function IndicatorChart({ rows, seriesDefs, priceLines = [], height, redrawKey }) {
    const containerRef = useRef(null);
    useEffect(() => {
        const el = containerRef.current;
        if (!el || !rows || rows.length === 0) return;
        const isDark = document.documentElement.getAttribute("data-theme") !== "light";
        const gridC = isDark ? "rgba(48,54,61,0.5)" : "rgba(0,0,0,0.06)";
        const textC = isDark ? "#8b949e" : "#6b7280";
        const borderC = isDark ? "rgba(48,54,61,0.6)" : "rgba(0,0,0,0.12)";

        const chart = createChart(el, {
            layout: {
                background: { type: ColorType.Solid, color: "transparent" },
                textColor: textC,
                fontSize: 11,
                fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
                attributionLogo: false,
            },
            grid: { vertLines: { color: gridC }, horzLines: { color: gridC } },
            rightPriceScale: { borderColor: borderC },
            timeScale: { borderColor: borderC },
            width: el.clientWidth,
            height,
        });

        let firstSeries = null;
        for (const def of seriesDefs) {
            let series;
            if (def.type === "histogram") {
                series = chart.addSeries(HistogramSeries, { priceLineVisible: false, lastValueVisible: false });
                series.setData(rows.filter((r) => r[def.field] != null)
                    .map((r) => ({ time: r.time, value: r[def.field], color: def.colorFn ? def.colorFn(r[def.field]) : def.color })));
            } else {
                const opts = {
                    color: def.color,
                    lineWidth: def.lineWidth || 2,
                    lineStyle: def.lineStyle || 0,
                    priceLineVisible: false,
                    lastValueVisible: false,
                };
                if (def.fixed) opts.autoscaleInfoProvider = () => ({ priceRange: { minValue: def.fixed[0], maxValue: def.fixed[1] } });
                series = chart.addSeries(LineSeries, opts);
                series.setData(rows.filter((r) => r[def.field] != null).map((r) => ({ time: r.time, value: r[def.field] })));
            }
            if (!firstSeries) firstSeries = series;
        }
        if (firstSeries) {
            for (const pl of priceLines) {
                firstSeries.createPriceLine({ price: pl.price, color: pl.color, lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true });
            }
        }
        chart.timeScale().fitContent();

        const ro = new ResizeObserver((entries) => {
            const w = entries[0].contentRect.width;
            if (w > 0) chart.applyOptions({ width: w });
        });
        ro.observe(el);
        return () => { ro.disconnect(); chart.remove(); };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [redrawKey, height]);

    return <div ref={containerRef} style={{ width: "100%", height }} />;
}

// localStorage cache for the technical-analysis response. Reads paint
// instantly on reload and survive a backend / network outage on stale
// data; fresh entries also short-circuit the network roundtrip.
const TA_TTL_MS = 10 * 60 * 1000;        // 10 minutes fresh
const TA_STALE_TTL_MS = 24 * 60 * 60 * 1000; // 1 day usable as offline fallback
const TA_KEY = (symbol, period) => `ta-cache:${symbol}:${period}`;

function readTaCache(symbol, period) {
    try {
        const raw = localStorage.getItem(TA_KEY(symbol, period));
        if (!raw) return null;
        const parsed = JSON.parse(raw);
        const age = Date.now() - (parsed.ts || 0);
        if (age > TA_STALE_TTL_MS) return null;
        return { data: parsed.data, fresh: age < TA_TTL_MS };
    } catch {
        return null;
    }
}

function writeTaCache(symbol, period, data) {
    try {
        localStorage.setItem(TA_KEY(symbol, period), JSON.stringify({ ts: Date.now(), data }));
    } catch { /* quota — fail open */ }
}

export default function TechnicalAnalysisPanel({ symbol, period }) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    // Indicator toggles
    const [showSMA7, setShowSMA7] = useState(true);
    const [showSMA20, setShowSMA20] = useState(true);
    const [showSMA50, setShowSMA50] = useState(false);
    const [showBB, setShowBB] = useState(false);
    // RSI + MACD live in separate subplots that the user can collapse.
    const [showRsi, setShowRsi] = useState(true);
    const [showMacd, setShowMacd] = useState(true);

    useEffect(() => {
        if (!symbol) return;
        let cancelled = false;

        // Synchronously paint from cache (even stale) so the panel never
        // shows a spinner if we've seen this symbol+period before.
        const cached = readTaCache(symbol, period);
        if (cached) {
            setData(cached.data);
            setError(null);
            setLoading(false);
            if (cached.fresh) return; // skip network entirely
        } else {
            setLoading(true);
            setError(null);
        }

        // Calculate date range based on period
        const to = new Date();
        let from = new Date();
        switch (period) {
            case "1D":  from.setDate(to.getDate() - 1); break;
            case "5D":  from.setDate(to.getDate() - 5); break;
            case "30D": from.setMonth(to.getMonth() - 1); break;
            case "1Y":  from.setFullYear(to.getFullYear() - 1); break;
            default:    from.setMonth(to.getMonth() - 3);
        }
        const fromStr = from.toISOString().split('T')[0];
        const toStr = to.toISOString().split('T')[0];

        getTechnicalAnalysis(symbol, fromStr, toStr)
            .then((fresh) => {
                if (cancelled) return;
                setData(fresh);
                writeTaCache(symbol, period, fresh);
            })
            .catch((err) => {
                if (cancelled) return;
                console.error("Failed to load technical analysis:", err);
                // Don't overwrite a cached painted view with an error — if we
                // had stale data, the user keeps seeing it. Otherwise surface
                // the error.
                if (!cached) setError("Teknik analiz verileri yüklenemedi");
            })
            .finally(() => { if (!cancelled) setLoading(false); });

        return () => { cancelled = true; };
    }, [symbol, period]);

    if (loading) {
        return (
            <div style={s.loading}>
                <div style={s.spinner}>⏳</div>
                <div>Teknik analiz hesaplanıyor...</div>
            </div>
        );
    }

    if (error || !data) {
        return (
            <div style={s.error}>
                {error || "Veri yüklenemedi"}
            </div>
        );
    }

    if (data.trend.direction === "INSUFFICIENT_DATA") {
        return (
            <div style={s.insufficientData}>
                <div style={{ marginBottom: 8 }}><IconBarChart size={32} /></div>
                <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Yetersiz Veri</div>
                <div style={{ fontSize: 12, color: "var(--text-muted)" }}>
                    {data.trend.description}
                </div>
            </div>
        );
    }

    // lightweight-charts rows (replaces recharts). Toggles gate which series
    // definitions get drawn; redrawKeys recreate each pane on change.
    const rows = buildLwRows(data.series);
    const isDark = document.documentElement.getAttribute("data-theme") !== "light";

    const mainDefs = [
        ...(showBB ? [
            { field: "bbUpper", color: "#94a3b8", lineWidth: 1, lineStyle: LineStyle.Dashed },
            { field: "bbLower", color: "#94a3b8", lineWidth: 1, lineStyle: LineStyle.Dashed },
        ] : []),
        { field: "close", color: "#10b981", lineWidth: 2 },
        ...(showSMA7 ? [{ field: "sma7", color: "#3b82f6", lineWidth: 1.5, lineStyle: LineStyle.Dashed }] : []),
        ...(showSMA20 ? [{ field: "sma20", color: "#f59e0b", lineWidth: 1.5, lineStyle: LineStyle.Dashed }] : []),
        ...(showSMA50 ? [{ field: "sma50", color: "#8b5cf6", lineWidth: 1.5, lineStyle: LineStyle.Dashed }] : []),
    ];
    const mainKey = `m:${symbol}:${period}:${rows.length}:${showSMA7 ? 1 : 0}${showSMA20 ? 1 : 0}${showSMA50 ? 1 : 0}${showBB ? 1 : 0}:${isDark ? 1 : 0}`;

    const rsiDefs = [{ field: "rsi", color: "#a855f7", lineWidth: 1.8, fixed: [0, 100] }];
    const rsiPriceLines = [{ price: 70, color: "#ef4444" }, { price: 30, color: "#10b981" }];
    const rsiKey = `r:${symbol}:${period}:${rows.length}:${isDark ? 1 : 0}`;

    const macdDefs = [
        { field: "macdHist", type: "histogram", colorFn: (v) => (v >= 0 ? "rgba(16,185,129,0.6)" : "rgba(239,68,68,0.6)") },
        { field: "macd", color: "#06b6d4", lineWidth: 1.8 },
        { field: "macdSignal", color: "#f59e0b", lineWidth: 1.5, lineStyle: LineStyle.Dashed },
    ];
    const macdPriceLines = [{ price: 0, color: "#8b949e" }];
    const macdKey = `d:${symbol}:${period}:${rows.length}:${isDark ? 1 : 0}`;

    const trendColor =
        data.trend.direction === "UPWARD" ? "#10b981" :
        data.trend.direction === "DOWNWARD" ? "#ef4444" : "#6b7280";

    const trendIcon =
        data.trend.direction === "UPWARD" ? <IconTrendingUp size={20} style={{ verticalAlign: "-3px" }} /> :
        data.trend.direction === "DOWNWARD" ? <IconTrendingDown size={20} style={{ verticalAlign: "-3px" }} /> : <IconArrowRight size={20} style={{ verticalAlign: "-3px" }} />;

    return (
        <div style={s.root}>
            {/* Disclaimer */}
            <div style={s.disclaimer}>
                <IconAlertTriangle size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />Bu bölüm yalnızca temel teknik analiz göstergelerini sunar. Yatırım tavsiyesi değildir.
            </div>

            {/* Summary Cards */}
            <div style={s.summaryGrid}>
                <div style={s.card}>
                    <div style={s.cardLabel}>Trend</div>
                    <div style={{ ...s.cardValue, color: trendColor }}>
                        {trendIcon} {data.trend.direction === "UPWARD" ? "Yükselen" :
                                    data.trend.direction === "DOWNWARD" ? "Düşen" : "Yatay"}
                    </div>
                    <div style={s.cardSub}>
                        {data.trend.changePercent >= 0 ? "+" : ""}{data.trend.changePercent.toFixed(2)}% (dönem)
                    </div>
                </div>

                <div style={s.card}>
                    <div style={s.cardLabel}>Son Fiyat</div>
                    <div style={s.cardValue}>{data.summary.latestClose.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}</div>
                    <div style={s.cardSub}>Ortalama: {data.summary.averageClose.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}</div>
                </div>

                <div style={s.card}>
                    <div style={s.cardLabel}>En Yüksek</div>
                    <div style={{ ...s.cardValue, color: "#10b981" }}>
                        {data.summary.highestClose.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}
                    </div>
                    <div style={s.cardSub}>Direnç (dönem zirvesi)</div>
                </div>

                <div style={s.card}>
                    <div style={s.cardLabel}>En Düşük</div>
                    <div style={{ ...s.cardValue, color: "#ef4444" }}>
                        {data.summary.lowestClose.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}
                    </div>
                    <div style={s.cardSub}>Destek (dönem dibi)</div>
                </div>

                <div style={s.card}>
                    <div style={s.cardLabel}>Volatilite</div>
                    <div style={s.cardValue}>{data.summary.volatilityPercent.toFixed(2)}%</div>
                    <div style={s.cardSub}>Yıllıklandırılmış (σ × √252)</div>
                </div>

                {/* RSI summary — colour-coded per the classic 70/30 thresholds. */}
                {data.summary.rsi14Latest != null && (
                    <div style={s.card}>
                        <div style={s.cardLabel}>RSI (14)</div>
                        <div style={{
                            ...s.cardValue,
                            color: data.summary.rsi14Latest >= 70 ? "#ef4444"
                                 : data.summary.rsi14Latest <= 30 ? "#10b981"
                                 : "var(--text-primary)",
                        }}>
                            {data.summary.rsi14Latest.toFixed(1)}
                        </div>
                        <div style={s.cardSub}>
                            {data.summary.rsi14Latest >= 70 ? "Aşırı alım"
                             : data.summary.rsi14Latest <= 30 ? "Aşırı satım"
                             : "Nötr bölge"}
                        </div>
                    </div>
                )}
            </div>

            {/* Indicator Toggles */}
            <div style={s.toggleRow}>
                <div style={s.toggleLabel}>Göstergeler:</div>
                <label style={s.toggle}>
                    <input type="checkbox" checked={showSMA7} onChange={(e) => setShowSMA7(e.target.checked)} style={s.checkbox} />
                    <span style={{ color: "#3b82f6" }}>SMA 7</span>
                </label>
                <label style={s.toggle}>
                    <input type="checkbox" checked={showSMA20} onChange={(e) => setShowSMA20(e.target.checked)} style={s.checkbox} />
                    <span style={{ color: "#f59e0b" }}>SMA 20</span>
                </label>
                <label style={s.toggle}>
                    <input type="checkbox" checked={showSMA50} onChange={(e) => setShowSMA50(e.target.checked)} style={s.checkbox} />
                    <span style={{ color: "#8b5cf6" }}>SMA 50</span>
                </label>
                <label style={s.toggle}>
                    <input type="checkbox" checked={showBB} onChange={(e) => setShowBB(e.target.checked)} style={s.checkbox} />
                    <span style={{ color: "#94a3b8" }}>Bollinger (20, 2σ)</span>
                </label>
                <label style={s.toggle}>
                    <input type="checkbox" checked={showRsi} onChange={(e) => setShowRsi(e.target.checked)} style={s.checkbox} />
                    <span style={{ color: "#a855f7" }}>RSI alt grafik</span>
                </label>
                <label style={s.toggle}>
                    <input type="checkbox" checked={showMacd} onChange={(e) => setShowMacd(e.target.checked)} style={s.checkbox} />
                    <span style={{ color: "#06b6d4" }}>MACD alt grafik</span>
                </label>
            </div>

            {/* Main price chart — price + optional SMA overlays + Bollinger. */}
            <div style={s.chartWrap}>
                <IndicatorChart rows={rows} seriesDefs={mainDefs} height={350} redrawKey={mainKey} />
            </div>

            {/* RSI subplot — fixed 0-100 with the canonical 70/30 reference
                lines so overbought/oversold zones are immediately readable. */}
            {showRsi && (
                <div style={s.chartWrap}>
                    <div style={s.subplotTitle}>RSI (14)</div>
                    <IndicatorChart rows={rows} seriesDefs={rsiDefs} priceLines={rsiPriceLines} height={140} redrawKey={rsiKey} />
                </div>
            )}

            {/* MACD subplot — histogram (sign-coloured) + MACD & signal lines. */}
            {showMacd && (
                <div style={s.chartWrap}>
                    <div style={s.subplotTitle}>MACD (12, 26, 9)</div>
                    <IndicatorChart rows={rows} seriesDefs={macdDefs} priceLines={macdPriceLines} height={160} redrawKey={macdKey} />
                </div>
            )}

            {/* Trend Description */}
            <div style={s.trendDescription}>
                <div style={s.trendIcon}>{trendIcon}</div>
                <div>
                    <div style={{ fontWeight: 600, marginBottom: 4 }}>Trend Analizi</div>
                    <div style={{ fontSize: 13, color: "var(--text-muted)" }}>
                        {data.trend.description}
                    </div>
                </div>
            </div>
        </div>
    );
}

const s = {
    root: {
        display: "flex",
        flexDirection: "column",
        gap: 16,
    },
    disclaimer: {
        padding: "10px 14px",
        background: "rgba(245, 158, 11, 0.1)",
        border: "1px solid rgba(245, 158, 11, 0.3)",
        borderRadius: 8,
        fontSize: 12,
        color: "#f59e0b",
        textAlign: "center",
    },
    summaryGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))",
        gap: 10,
    },
    card: {
        padding: "12px 14px",
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
    },
    cardLabel: {
        fontSize: 11,
        color: "var(--text-muted)",
        marginBottom: 6,
    },
    cardValue: {
        fontSize: 18,
        fontWeight: 700,
        color: "var(--text-primary)",
        marginBottom: 2,
    },
    cardSub: {
        fontSize: 10,
        color: "var(--text-muted)",
    },
    toggleRow: {
        display: "flex",
        alignItems: "center",
        gap: 16,
        padding: "10px 14px",
        background: "var(--bg-panel)",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
    },
    toggleLabel: {
        fontSize: 12,
        fontWeight: 600,
        color: "var(--text-muted)",
    },
    toggle: {
        display: "flex",
        alignItems: "center",
        gap: 6,
        fontSize: 12,
        fontWeight: 500,
        cursor: "pointer",
        color: "var(--text-primary)",
    },
    checkbox: {
        cursor: "pointer",
    },
    chartWrap: {
        padding: 16,
        background: "var(--bg-panel2)",
        border: "1px solid var(--border)",
        borderRadius: 10,
    },
    subplotTitle: {
        fontSize: 12,
        fontWeight: 600,
        color: "var(--text-muted)",
        marginBottom: 6,
        letterSpacing: 0.4,
        textTransform: "uppercase",
    },
    trendDescription: {
        display: "flex",
        gap: 12,
        padding: "12px 14px",
        background: "var(--bg-panel)",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
    },
    trendIcon: {
        fontSize: 24,
    },
    loading: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "48px 20px",
        gap: 12,
        color: "var(--text-muted)",
        fontSize: 13,
    },
    spinner: {
        fontSize: 32,
        animation: "spin 2s linear infinite",
    },
    error: {
        padding: "24px",
        textAlign: "center",
        color: "var(--danger-text)",
        background: "var(--danger-bg)",
        border: "1px solid var(--danger-border)",
        borderRadius: 8,
        fontSize: 13,
    },
    insufficientData: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "48px 20px",
        textAlign: "center",
    },
};

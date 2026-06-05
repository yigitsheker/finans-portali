import { useEffect, useMemo, useState, useRef, useCallback } from "react";
import Modal from "./Modal";
import { getMarketHistory, getMarketSummary } from "../api/portfolioApi";
import { getInflationHistory } from "../api/inflationApi";
import { clickable } from "../utils/clickable";
import { useI18n } from "../contexts/I18nContext";

// Instrument types whose prices are denominated in TRY. When the user picks
// "USD Bazlı" view we need to divide these by the historical USDTRY rate of
// each respective day — using today's rate would distort the past.
const TRY_DENOMINATED_TYPES = new Set(["BIST", "INDEX", "BOND", "FUND", "VIOP"]);
const isTryDenominated = (inst) => inst && TRY_DENOMINATED_TYPES.has(inst.type);

// Sentinel "instrument" representing CPI (TÜFE). Treated specially in the data
// fetcher and series builder; never shown in stock search results.
const INFLATION_INSTRUMENT = {
  symbol: "TÜFE",
  name: "Enflasyon (TCMB)",
  isInflation: true,
};

/**
 * Fetch raw monthly CPI rows. The series builder will interpolate them onto the
 * stock's daily date grid so the two lines share the same x-axis. CPI is monthly,
 * so intraday periods can't carry an inflation overlay.
 */
async function fetchInflationAsHistory(period) {
  if (period === "1D" || period === "5D") return [];
  const rows = await getInflationHistory();
  if (!rows || rows.length < 2) return [];
  return rows.map((r) => ({
    label: r.periodDate,
    day: r.periodDate,
    close: Number(r.cpiIndex),
    timestamp: new Date(r.periodDate).getTime() / 1000,
  }));
}

/**
 * Project monthly CPI values onto an arbitrary list of daily target dates.
 *
 *  • Inside the published CPI range → linear interpolation between adjacent months.
 *  • Before the first known month   → clamp to the earliest value.
 *  • After the latest published month → compound the last 3-month average monthly
 *    inflation rate forward. Each emitted point carries an {@code estimated} flag
 *    so the chart can render it as a dashed line, making clear that this segment
 *    is a projection rather than actual data.
 */
function interpolateCpiOntoDates(cpiRows, targetDates) {
  if (!cpiRows || cpiRows.length < 2 || targetDates.length === 0) return [];
  const cpiPts = cpiRows
    .map((r) => ({ t: new Date(r.label).getTime(), v: Number(r.close) }))
    .filter((p) => Number.isFinite(p.t) && Number.isFinite(p.v))
    .sort((a, b) => a.t - b.t);
  if (cpiPts.length < 2) return [];

  const lastT = cpiPts[cpiPts.length - 1].t;
  const lastV = cpiPts[cpiPts.length - 1].v;

  // Recent monthly inflation factor — derived from the last 3 months when available,
  // otherwise from the last month. Damps single-month spikes for a more honest tail.
  let monthlyFactor = 1.0;
  if (cpiPts.length >= 4) {
    const ref = cpiPts[cpiPts.length - 4].v;   // 3 months ago
    monthlyFactor = Math.pow(lastV / ref, 1 / 3);
  } else {
    const prev = cpiPts[cpiPts.length - 2].v;
    monthlyFactor = lastV / prev;
  }
  const MS_PER_MONTH = 1000 * 60 * 60 * 24 * 30.4375;

  const result = [];
  for (const label of targetDates) {
    const t = new Date(label).getTime();
    let v;
    let estimated = false;

    if (t <= cpiPts[0].t) {
      v = cpiPts[0].v;
    } else if (t > lastT) {
      // Compound the recent monthly rate forward continuously.
      const monthsPast = (t - lastT) / MS_PER_MONTH;
      v = lastV * Math.pow(monthlyFactor, monthsPast);
      estimated = true;
    } else {
      for (let j = 0; j < cpiPts.length - 1; j++) {
        if (t >= cpiPts[j].t && t <= cpiPts[j + 1].t) {
          const ratio = (t - cpiPts[j].t) / (cpiPts[j + 1].t - cpiPts[j].t);
          v = cpiPts[j].v + ratio * (cpiPts[j + 1].v - cpiPts[j].v);
          break;
        }
      }
    }
    if (v !== undefined) result.push({ label, value: v, estimated });
  }
  return result;
}

const PERIODS = [
    { labelKey: "compare.p1G", value: "1D" },
    { labelKey: "compare.p5G", value: "5D" },
    { labelKey: "compare.p1A", value: "30D" },
    { labelKey: "compare.p1Y", value: "1Y" },
];

// Visually distinct, high-contrast palette for dark backgrounds.
// First slot stays green to match the app accent (covers the base instrument).
const COLORS = ["#22c55e", "#3b82f6", "#f97316", "#ec4899", "#a855f7"];

// ── Pure SVG multi-line chart ──────────────────────────────────────────────

function SVGChart({ series, xLabels, yLabel, mode, independentX = false }) {
    const { t } = useI18n();
    const [tooltip, setTooltip] = useState(null);
    // zoomRange = null → full extent; otherwise {startIdx, endIdx} inclusive over xLabels.
    const [zoomRange, setZoomRange] = useState(null);
    const svgRef = useRef(null);

    const W = 780, H = 340;
    const PAD = { top: 20, right: 20, bottom: 40, left: 70 };
    const chartW = W - PAD.left - PAD.right;
    const chartH = H - PAD.top - PAD.bottom;

    // Capture the FULL extent before any shadowing — wheel math needs absolute totals.
    const fullXLabels = xLabels;
    const fullLength = fullXLabels.length;

    // Reset zoom whenever the underlying data shape changes — otherwise switching
    // period/mode could leave the chart stuck at a stale window.
    useEffect(() => {
        setZoomRange(null);
    }, [fullLength, mode, independentX]);

    // Visible (zoomed) slice of the x axis. Series points are filtered to the same
    // label set, and inflation's `estimatedFromIdx` recomputed against the new array.
    const visibleXLabels = useMemo(() => {
        if (!zoomRange || independentX) return fullXLabels;
        return fullXLabels.slice(zoomRange.startIdx, zoomRange.endIdx + 1);
    }, [fullXLabels, zoomRange, independentX]);

    const visibleSeries = useMemo(() => {
        if (!zoomRange || independentX) return series;
        const allowed = new Set(visibleXLabels);
        return series.map((s) => {
            const filteredPoints = s.points.filter((p) => allowed.has(p.label));
            const firstEst = filteredPoints.findIndex((p) => p.estimated);
            return {
                ...s,
                points: filteredPoints,
                estimatedFromIdx: firstEst >= 0 ? firstEst : null,
            };
        });
    }, [series, zoomRange, visibleXLabels, independentX]);

    // Shadow the originals so the existing render code below stays unchanged.
    series = visibleSeries;
    xLabels = visibleXLabels;

    // Collect all values to compute y scale
    const allValues = series.flatMap(s => s.points.map(p => p.value));
    const minV = allValues.length ? Math.min(...allValues) : 0;
    const maxV = allValues.length ? Math.max(...allValues) : 1;
    const vRange = maxV - minV || 1;
    const vPad = vRange * 0.08;

    const yMin = minV - vPad;
    const yMax = maxV + vPad;

    const toX = (i) => PAD.left + (i / Math.max(xLabels.length - 1, 1)) * chartW;
    const toY = (v) => PAD.top + chartH - ((v - yMin) / (yMax - yMin)) * chartH;

    // For independentX (1D cross-market), each series spans the full chart width
    // by its own index rather than via xLabels lookup.
    const seriesXAt = (s, i) =>
        PAD.left + (i / Math.max(s.points.length - 1, 1)) * chartW;

    // Y axis ticks
    const yTicks = useMemo(() => {
        const count = 6;
        return Array.from({ length: count }, (_, i) => yMin + (i / (count - 1)) * (yMax - yMin));
    }, [yMin, yMax]);

    // X axis ticks — show ~6 evenly spaced labels.
    // Avoid pushing the final label if it would land right next to the previous tick;
    // otherwise the two labels overlap and render as visual garbage.
    const xTickIndices = useMemo(() => {
        if (xLabels.length <= 6) return xLabels.map((_, i) => i);
        const step = Math.floor(xLabels.length / 5);
        const indices = [];
        for (let i = 0; i < xLabels.length; i += step) indices.push(i);
        const lastIdx = xLabels.length - 1;
        const prev = indices[indices.length - 1];
        // Only add the trailing tick if it's meaningfully spaced from the last one.
        // Threshold is half a step — closer than that means overlap.
        if (lastIdx - prev > step / 2) {
            indices.push(lastIdx);
        } else if (prev !== lastIdx) {
            // Replace the last tick with the actual end so users see a real end date
            indices[indices.length - 1] = lastIdx;
        }
        return indices;
    }, [xLabels]);

    // For independentX, x axis is session-progress (0..100%) instead of a real time line.
    const independentTicks = useMemo(() => {
        if (!independentX) return [];
        return [0, 0.25, 0.5, 0.75, 1].map(r => ({
            ratio: r,
            label: `%${Math.round(r * 100)}`,
        }));
    }, [independentX]);

    const formatValue = (v) => {
        if (mode === "percentage") return `${v.toFixed(2)}%`;
        if (mode === "usd") return `$${v.toFixed(2)}`;
        return `₺${v.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    };

    const handleMouseMove = useCallback((e) => {
        if (!svgRef.current) return;
        const rect = svgRef.current.getBoundingClientRect();
        const mouseX = (e.clientX - rect.left) * (W / rect.width) - PAD.left;

        if (independentX) {
            // Cross-market intraday: align each series by its own progress (0..1).
            const ratio = Math.max(0, Math.min(1, mouseX / chartW));
            const perSeries = series
                .map(s => {
                    if (s.points.length === 0) return null;
                    const idx = Math.round(ratio * (s.points.length - 1));
                    const pt = s.points[idx];
                    return { symbol: s.symbol, color: s.color, value: pt.value, label: pt.label };
                })
                .filter(Boolean);
            if (perSeries.length > 0) {
                setTooltip({
                    x: PAD.left + ratio * chartW,
                    y: e.clientY - rect.top,
                    label: perSeries.map(v => `${v.symbol} • ${v.label}`).join("  |  "),
                    values: perSeries.map(({ symbol, color, value }) => ({ symbol, color, value })),
                });
            }
            return;
        }

        if (xLabels.length === 0) return;
        const idx = Math.round((mouseX / chartW) * (xLabels.length - 1));
        const clampedIdx = Math.max(0, Math.min(xLabels.length - 1, idx));

        const values = series
            .map(s => {
                const pt = s.points.find(p => p.label === xLabels[clampedIdx]);
                return pt ? { symbol: s.symbol, color: s.color, value: pt.value } : null;
            })
            .filter(Boolean);

        if (values.length > 0) {
            setTooltip({
                x: toX(clampedIdx),
                y: e.clientY - rect.top,
                label: xLabels[clampedIdx],
                values,
            });
        }
    }, [series, xLabels, chartW, independentX]);

    // Wheel zoom: keep the point under the cursor anchored as the visible window
    // shrinks (zoom in) or grows (zoom out). Disabled in cross-market intraday mode
    // where each series owns its own x-axis.
    const handleWheel = useCallback((e) => {
        if (independentX) return;
        if (fullLength < 5) return;
        e.preventDefault();

        const rect = svgRef.current?.getBoundingClientRect();
        if (!rect) return;

        // Cursor position as a fraction inside the plot area (0..1).
        const xInChart = (e.clientX - rect.left) * (W / rect.width) - PAD.left;
        const frac = Math.max(0, Math.min(1, xInChart / chartW));

        setZoomRange((cur) => {
            const absStart = cur ? cur.startIdx : 0;
            const absEnd = cur ? cur.endIdx : fullLength - 1;
            const rangeSize = absEnd - absStart + 1;
            const pivotAbs = absStart + Math.round(frac * (rangeSize - 1));

            const factor = e.deltaY < 0 ? 0.7 : 1.42;
            const newSize = Math.max(5, Math.min(fullLength, Math.round(rangeSize * factor)));
            if (newSize >= fullLength) return null;     // back to full extent

            let newStart = Math.round(pivotAbs - frac * (newSize - 1));
            let newEnd = newStart + newSize - 1;
            if (newStart < 0) { newEnd -= newStart; newStart = 0; }
            if (newEnd > fullLength - 1) { newStart -= (newEnd - (fullLength - 1)); newEnd = fullLength - 1; }
            newStart = Math.max(0, newStart);
            newEnd = Math.min(fullLength - 1, newEnd);
            return { startIdx: newStart, endIdx: newEnd };
        });
    }, [independentX, fullLength, chartW]);

    return (
        <div style={{ position: "relative", width: "100%" }}>
            {/* Zoom reset button — visible only while zoomed in */}
            {zoomRange && !independentX && (
                <button
                    type="button"
                    onClick={() => setZoomRange(null)}
                    style={{
                        position: "absolute",
                        top: 8,
                        right: 8,
                        zIndex: 11,
                        padding: "4px 10px",
                        fontSize: 11,
                        fontWeight: 600,
                        borderRadius: 6,
                        border: "1px solid var(--border-card)",
                        background: "var(--bg-card)",
                        color: "var(--text-primary)",
                        cursor: "pointer",
                    }}
                    title={t("compare.resetZoomTooltip")}
                >
                    {t("compare.resetZoom")}
                </button>
            )}
            <svg
                ref={svgRef}
                viewBox={`0 0 ${W} ${H}`}
                style={{ width: "100%", height: "auto", display: "block", cursor: independentX ? "default" : "ns-resize" }}
                onMouseMove={handleMouseMove}
                onMouseLeave={() => setTooltip(null)}
                onWheel={handleWheel}
                onDoubleClick={() => setZoomRange(null)}
            >
                {/* Grid lines */}
                {yTicks.map((v, i) => (
                    <line
                        key={i}
                        x1={PAD.left} y1={toY(v)}
                        x2={PAD.left + chartW} y2={toY(v)}
                        stroke="var(--border-soft)"
                        strokeWidth={0.8}
                        strokeDasharray="4 4"
                    />
                ))}

                {/* Y axis labels */}
                {yTicks.map((v, i) => (
                    <text
                        key={i}
                        x={PAD.left - 8}
                        y={toY(v) + 4}
                        textAnchor="end"
                        fontSize={10}
                        fill="var(--text-muted)"
                    >
                        {mode === "percentage" ? `${v.toFixed(1)}%` : `$${v.toFixed(2)}`}
                    </text>
                ))}

                {/* Y axis label */}
                <text
                    x={14}
                    y={PAD.top + chartH / 2}
                    textAnchor="middle"
                    fontSize={10}
                    fill="var(--text-muted)"
                    transform={`rotate(-90, 14, ${PAD.top + chartH / 2})`}
                >
                    {yLabel}
                </text>

                {/* X axis labels — session-progress in independentX mode, real labels otherwise */}
                {independentX
                    ? independentTicks.map((t, i) => (
                        <text
                            key={i}
                            x={PAD.left + t.ratio * chartW}
                            y={H - 8}
                            textAnchor="middle"
                            fontSize={10}
                            fill="var(--text-muted)"
                        >
                            {t.label}
                        </text>
                    ))
                    : xTickIndices.map(i => (
                        <text
                            key={i}
                            x={toX(i)}
                            y={H - 8}
                            textAnchor="middle"
                            fontSize={10}
                            fill="var(--text-muted)"
                        >
                            {xLabels[i]}
                        </text>
                    ))}

                {/* Zero line for percentage mode */}
                {mode === "percentage" && yMin < 0 && yMax > 0 && (
                    <line
                        x1={PAD.left} y1={toY(0)}
                        x2={PAD.left + chartW} y2={toY(0)}
                        stroke="var(--text-muted)"
                        strokeWidth={1}
                        opacity={0.4}
                    />
                )}

                {/* Series lines — connect every available point. Earlier code broke the
                    line whenever a series skipped a label (e.g. BIST holidays vs Nasdaq),
                    leaving visible gaps. For intraday cross-market comparison (independentX)
                    each series spans the full chart width by its own index.
                    Series with estimatedFromIdx render two overlapping paths so the projected
                    tail appears dashed while the actual history stays solid. */}
                {series.flatMap(s => {
                    const allCoords = s.points
                        .map((pt, i) => {
                            if (independentX) {
                                return { x: seriesXAt(s, i), y: toY(pt.value) };
                            }
                            const labelIdx = xLabels.indexOf(pt.label);
                            if (labelIdx === -1) return null;
                            return { x: toX(labelIdx), y: toY(pt.value) };
                        })
                        .filter((p) => p !== null);

                    if (allCoords.length < 2) return [];

                    const cut = (typeof s.estimatedFromIdx === "number" && s.estimatedFromIdx > 0)
                        ? Math.min(s.estimatedFromIdx, allCoords.length)
                        : allCoords.length;

                    const solidCoords = allCoords.slice(0, cut);
                    // Start dashed segment at the last solid point so the two paths meet visually.
                    const dashedCoords = cut < allCoords.length ? allCoords.slice(cut - 1) : [];

                    const pathOf = (coords) =>
                        coords.map((p, i) => `${i === 0 ? "M" : "L"} ${p.x} ${p.y}`).join(" ");

                    const paths = [];
                    if (solidCoords.length >= 2) {
                        paths.push(
                            <path
                                key={s.symbol + "-solid"}
                                d={pathOf(solidCoords)}
                                fill="none"
                                stroke={s.color}
                                strokeWidth={2}
                                strokeLinecap="round"
                                strokeLinejoin="round"
                            />
                        );
                    }
                    if (dashedCoords.length >= 2) {
                        paths.push(
                            <path
                                key={s.symbol + "-dashed"}
                                d={pathOf(dashedCoords)}
                                fill="none"
                                stroke={s.color}
                                strokeWidth={2}
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeDasharray="6 4"
                                opacity={0.85}
                            />
                        );
                    }
                    return paths;
                })}

                {/* Crosshair vertical line */}
                {tooltip && (
                    <line
                        x1={tooltip.x} y1={PAD.top}
                        x2={tooltip.x} y2={PAD.top + chartH}
                        stroke="var(--text-muted)"
                        strokeWidth={1}
                        strokeDasharray="4 4"
                        opacity={0.6}
                    />
                )}

                {/* Crosshair dots */}
                {tooltip && series.map(s => {
                    if (independentX) {
                        if (s.points.length === 0) return null;
                        const ratio = (tooltip.x - PAD.left) / chartW;
                        const idx = Math.max(0, Math.min(s.points.length - 1, Math.round(ratio * (s.points.length - 1))));
                        const pt = s.points[idx];
                        return (
                            <circle
                                key={s.symbol}
                                cx={seriesXAt(s, idx)}
                                cy={toY(pt.value)}
                                r={4}
                                fill={s.color}
                                stroke="var(--bg-panel)"
                                strokeWidth={2}
                            />
                        );
                    }
                    const pt = s.points.find(p => p.label === tooltip.label);
                    if (!pt) return null;
                    return (
                        <circle
                            key={s.symbol}
                            cx={toX(xLabels.indexOf(pt.label))}
                            cy={toY(pt.value)}
                            r={4}
                            fill={s.color}
                            stroke="var(--bg-panel)"
                            strokeWidth={2}
                        />
                    );
                })}
            </svg>

            {/* Tooltip */}
            {tooltip && (
                <div style={{
                    position: "absolute",
                    top: Math.max(0, tooltip.y - 10),
                    left: tooltip.x / W * 100 > 60 ? "auto" : `calc(${(tooltip.x / W) * 100}% + 12px)`,
                    right: tooltip.x / W * 100 > 60 ? `calc(${100 - (tooltip.x / W) * 100}% + 12px)` : "auto",
                    background: "var(--bg-card)",
                    border: "1px solid var(--border-card)",
                    borderRadius: 8,
                    padding: "8px 12px",
                    pointerEvents: "none",
                    zIndex: 10,
                    minWidth: 140,
                    boxShadow: "var(--shadow)",
                }}>
                    <div style={{ fontSize: 11, color: "var(--text-muted)", marginBottom: 6 }}>{tooltip.label}</div>
                    {tooltip.values.map(v => (
                        <div key={v.symbol} style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 3 }}>
                            <div style={{ width: 8, height: 8, borderRadius: "50%", background: v.color, flexShrink: 0 }} />
                            <span style={{ fontSize: 12, color: "var(--text-muted)", flex: 1 }}>{v.symbol}</span>
                            <span style={{ fontSize: 12, fontWeight: 600, color: "var(--text-primary)" }}>{formatValue(v.value)}</span>
                        </div>
                    ))}
                </div>
            )}

            {/* Legend — in independentX mode also annotate each series's actual session range
                since the X axis no longer carries real timestamps. */}
            <div style={{ display: "flex", gap: 16, justifyContent: "center", marginTop: 8, flexWrap: "wrap" }}>
                {series.map(s => {
                    const range = independentX && s.points.length > 0
                        ? `${s.points[0].label} → ${s.points[s.points.length - 1].label}`
                        : null;
                    return (
                        <div key={s.symbol} style={{ display: "flex", alignItems: "center", gap: 6 }}>
                            <div style={{ width: 20, height: 2, background: s.color, borderRadius: 1 }} />
                            <span style={{ fontSize: 12, color: "var(--text-muted)" }}>{s.symbol}</span>
                            {range && (
                                <span style={{ fontSize: 10, color: "var(--text-muted)", opacity: 0.7 }}>({range})</span>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

// ── Main component ─────────────────────────────────────────────────────────
export default function CompareInstrumentsModal({ baseInstrument, onClose }) {
    const { t } = useI18n();
    const [period, setPeriod] = useState("30D");
    const [mode, setMode] = useState("percentage");
    const [loading, setLoading] = useState(false);
    const [selectedInstruments, setSelectedInstruments] = useState([]);
    const [availableInstruments, setAvailableInstruments] = useState([]);
    const [searchTerm, setSearchTerm] = useState("");
    const [usdRate, setUsdRate] = useState(44.75);
    const [rawData, setRawData] = useState({});

    useEffect(() => {
        getMarketSummary()
            .then((data) => {
                setAvailableInstruments(data.filter(i => i.symbol !== baseInstrument?.symbol));
                const usdtry = data.find(i => i.symbol === "USDTRY");
                if (usdtry) setUsdRate(usdtry.last);
            })
            .catch(console.error);
    }, [baseInstrument]);

    useEffect(() => {
        if (baseInstrument) setSelectedInstruments([baseInstrument]);
    }, [baseInstrument]);

    // Fetch data for all selected instruments. Also fetch USDTRY history whenever
    // a TRY-denominated instrument is in the basket, so the "USD Bazlı" view can
    // convert each historical TRY close at *its own day's* rate.
    useEffect(() => {
        if (selectedInstruments.length === 0) return;
        setLoading(true);

        const fetch = async () => {
            const result = {};
            for (const inst of selectedInstruments) {
                try {
                    if (inst.isInflation) {
                        result[inst.symbol] = await fetchInflationAsHistory(period);
                    } else {
                        result[inst.symbol] = await getMarketHistory(inst.symbol, period);
                    }
                } catch {
                    result[inst.symbol] = [];
                }
            }
            // USDTRY history is required to express TRY prices in USD across the timeline.
            const needsUsdRate = selectedInstruments.some(isTryDenominated);
            if (needsUsdRate && !result["USDTRY"]) {
                try {
                    result["USDTRY"] = await getMarketHistory("USDTRY", period);
                } catch {
                    result["USDTRY"] = [];
                }
            }
            setRawData(result);
            setLoading(false);
        };
        fetch();
    }, [selectedInstruments, period]);

    // Build series for SVG Chart
    const { series, xLabels } = useMemo(() => {
        if (selectedInstruments.length === 0) return { series: [], xLabels: [] };

        // Use only stock instruments to define the x-axis grid. CPI is monthly
        // and would otherwise stretch the axis to its own range, squeezing the
        // stock window into one corner of the chart.
        const stockInsts = selectedInstruments.filter((i) => !i.isInflation);
        const allLabels = new Set();
        stockInsts.forEach((inst) => {
            const data = rawData[inst.symbol] || [];
            data.forEach((p) => allLabels.add(p.label || p.day.split("T")[0]));
        });
        // Labels are ISO date strings — explicit string compare avoids the
        // implicit toString sort that Sonar S2871 flags.
        const sortedLabels = Array.from(allLabels).sort((a, b) => a.localeCompare(b));

        // Build series. Inflation rows have a very different magnitude (CPI index ~3500)
        // and only make sense as a % change overlay — drop them in price/usd modes.
        // Keep the *original* index for color so chips stay consistent.
        const seriesData = selectedInstruments
            .map((inst, idx) => ({ inst, idx }))
            .filter(({ inst }) => !inst.isInflation || mode === "percentage")
            .map(({ inst, idx }) => {
                const data = rawData[inst.symbol] || [];

                // Inflation: linear-interpolate monthly CPI onto the stock's daily labels
                // so both lines share the same x-axis grid and start anchored at 0%.
                if (inst.isInflation) {
                    if (sortedLabels.length < 2 || data.length < 2) {
                        return { symbol: inst.symbol, color: COLORS[idx % COLORS.length], points: [] };
                    }
                    const interpolated = interpolateCpiOntoDates(data, sortedLabels);
                    const anchor = interpolated[0]?.value;
                    const points = interpolated.map(({ label, value, estimated }, i) => {
                        const pct = anchor && anchor > 0 ? ((value - anchor) / anchor) * 100 : 0;
                        return { x: i, y: pct, label, value: pct, estimated };
                    });
                    // Index of the first projected point — caller will draw a dashed
                    // overlay from one before it (so solid and dashed segments meet).
                    const firstEstIdx = points.findIndex((p) => p.estimated);
                    return {
                        symbol: inst.symbol,
                        color: COLORS[idx % COLORS.length],
                        points,
                        estimatedFromIdx: firstEstIdx >= 0 ? firstEstIdx : null,
                    };
                }

                const sortedData = [...data].sort((a, b) => a.timestamp - b.timestamp);

                // Build a {date → USDTRY close} map for USD-mode conversion of TRY instruments.
                // Falls back to the latest known rate for any missing day.
                let usdRateByDate = null;
                if (mode === "usd" && isTryDenominated(inst)) {
                    const usdtryHist = [...(rawData["USDTRY"] || [])].sort((a, b) => a.timestamp - b.timestamp);
                    if (usdtryHist.length > 0) {
                        usdRateByDate = new Map();
                        let lastRate = usdtryHist[0].close;
                        for (const r of usdtryHist) {
                            const k = r.label || r.day?.split("T")[0];
                            if (k && r.close > 0) {
                                usdRateByDate.set(k, r.close);
                                lastRate = r.close;
                            }
                        }
                        usdRateByDate.__fallback = lastRate;
                    }
                }
                const resolveUsdRate = (dateLabel) => {
                    if (!usdRateByDate) return usdRate;  // non-TRY instrument: use today's spot
                    return usdRateByDate.get(dateLabel) ?? usdRateByDate.__fallback ?? usdRate;
                };

                // For percentage mode, the anchor in USD mode must be computed from the
                // converted-USD price of the first day — otherwise mixing TRY/USD instruments
                // breaks the comparison. We just precompute the first converted price.
                const firstClose = sortedData[0]?.close;
                const firstLabel = sortedData[0]?.label || sortedData[0]?.day?.split("T")[0];

                const points = sortedData.map((p, i) => {
                    const lbl = p.label || p.day.split("T")[0];
                    let value;
                    if (mode === "percentage") {
                        value = firstClose && firstClose > 0
                            ? ((p.close - firstClose) / firstClose) * 100
                            : 0;
                    } else {
                        // mode === "usd"
                        if (isTryDenominated(inst)) {
                            const rate = resolveUsdRate(lbl);
                            value = rate > 0 ? p.close / rate : 0;
                        } else {
                            value = p.close;  // already USD
                        }
                    }
                    return {
                        x: i,
                        y: value,
                        label: lbl,
                        value,
                    };
                });

                return {
                    symbol: inst.symbol,
                    color: COLORS[idx % COLORS.length],
                    points,
                };
            });

        return { series: seriesData, xLabels: sortedLabels };
    }, [rawData, selectedInstruments, mode, usdRate]);

    const addInstrument = (instrument) => {
        if (selectedInstruments.length >= 5) return;
        if (!selectedInstruments.find(i => i.symbol === instrument.symbol)) {
            setSelectedInstruments(prev => [...prev, instrument]);
        }
        setSearchTerm("");
    };

    const removeInstrument = (symbol) => {
        setSelectedInstruments(prev => prev.filter(i => i.symbol !== symbol));
    };

    const filteredInstruments = availableInstruments.filter(i =>
        i.symbol.toLowerCase().includes(searchTerm.toLowerCase()) ||
        i.name.toLowerCase().includes(searchTerm.toLowerCase())
    );

    // Human-readable name of the latest published CPI month, for the no-data hint.
    const latestCpiLabel = useMemo(() => {
        const rows = rawData["TÜFE"];
        if (!rows || rows.length === 0) return null;
        const last = rows[rows.length - 1];
        if (!last?.label) return null;
        const [y, m] = String(last.label).split("-");
        const mi = Number(m) - 1;
        if (!Number.isInteger(mi) || mi < 0 || mi > 11) return null;
        // Localized month name (TR/EN) instead of a hardcoded Turkish array.
        const locale = (() => { try { return localStorage.getItem("i18n-lang") === "en" ? "en-US" : "tr-TR"; } catch { return "tr-TR"; } })();
        const monthName = new Intl.DateTimeFormat(locale, { month: "long" }).format(new Date(Number(y), mi, 1));
        return `${monthName} ${y}`;
    }, [rawData]);

    const getYAxisLabel = () => {
        if (mode === "percentage") return t("compare.yAxisChange");
        return t("compare.yAxisPrice");
    };

    if (!baseInstrument) return null;

    return (
        <Modal
            open={!!baseInstrument}
            title={t("compare.title", { symbol: baseInstrument.symbol })}
            onClose={onClose}
            maxWidth={860}
        >
            <div style={s.root}>
                {/* Controls */}
                <div style={s.controls}>
                    <div style={s.periodRow}>
                        {PERIODS.map((p) => (
                            <button
                                key={p.value}
                                style={{ ...s.periodBtn, ...(period === p.value ? s.periodActive : {}) }}
                                onClick={() => setPeriod(p.value)}
                            >
                                {t(p.labelKey)}
                            </button>
                        ))}
                    </div>
                    <div style={s.modeRow}>
                        {["percentage", "usd"].map((m) => (
                            <button
                                key={m}
                                style={{ ...s.modeBtn, ...(mode === m ? s.modeActive : {}) }}
                                onClick={() => setMode(m)}
                            >
                                {m === "percentage" ? t("compare.modePercent") : t("compare.modeUsd")}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Selected chips */}
                <div style={s.selectedRow}>
                    {selectedInstruments.map((inst, idx) => (
                        <div key={inst.symbol} style={s.selectedChip}>
                            <div style={{ ...s.chipColor, background: COLORS[idx % COLORS.length] }} />
                            <span style={s.chipText}>{inst.symbol}</span>
                            {selectedInstruments.length > 1 && (
                                <button style={s.chipRemove} onClick={() => removeInstrument(inst.symbol)}>×</button>
                            )}
                        </div>
                    ))}
                </div>

                {/* Chart */}
                <div style={s.chartWrap}>
                    {loading ? (
                        <div style={s.loading}>
                            <div style={s.spinner} />
                        </div>
                    ) : series.length === 0 || xLabels.length === 0 ? (
                        <div style={s.loading}>{t("compare.noData")}</div>
                    ) : (
                        <SVGChart
                            series={series}
                            xLabels={xLabels}
                            yLabel={getYAxisLabel()}
                            mode={mode}
                            independentX={period === "1D"}
                        />
                    )}
                </div>

                {/* Inflation overlay button — only meaningful in % mode and ≥1M period */}
                {!selectedInstruments.find((i) => i.isInflation) &&
                  selectedInstruments.length < 5 &&
                  mode === "percentage" &&
                  (period === "30D" || period === "1Y") && (
                    <button
                        type="button"
                        style={s.inflationBtn}
                        onClick={() => addInstrument(INFLATION_INSTRUMENT)}
                        title={t("compare.addInflationTooltip")}
                    >
                        {t("compare.addInflation")}
                    </button>
                )}

                {/* Hint when inflation chip is present but current mode/period can't render it */}
                {selectedInstruments.find((i) => i.isInflation) &&
                  (mode !== "percentage" || period === "1D" || period === "5D") && (
                    <div
                        style={s.inflationHint}
                        dangerouslySetInnerHTML={{ __html: t("compare.inflationModeHint") }}
                    />
                )}

                {/* When inflation is selected and the window extends past the latest published
                    CPI month, surface what the dashed line means so users don't mistake it for
                    real data. */}
                {selectedInstruments.find((i) => i.isInflation) &&
                  mode === "percentage" &&
                  (period === "30D" || period === "1Y") &&
                  series.find((s) => s.symbol === "TÜFE" && typeof s.estimatedFromIdx === "number") && (
                    <div style={s.inflationHint}>
                        💡 TCMB en güncel TÜFE'yi <b>{latestCpiLabel ?? t("compare.cpiFallbackMonth")}</b> olarak yayınladı.
                        Sonraki günler için TÜFE eğrisi <b>son 3 aylık ortalama enflasyon hızıyla</b> tahmin
                        ediliyor ve <span style={{ borderBottom: "2px dashed currentColor" }}>kesik çizgi</span> ile gösterilir.
                    </div>
                )}

                {/* Search */}
                {selectedInstruments.length < 5 && (
                    <div style={s.addSection}>
                        <input
                            type="text"
                            placeholder="Hisse ara... (karşılaştırmak için ekle)"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            style={s.searchInput}
                        />
                        {searchTerm && (
                            <div style={s.searchResults}>
                                {filteredInstruments.slice(0, 8).map((inst) => (
                                    <div key={inst.symbol} style={s.searchItem} {...clickable(() => addInstrument(inst))}>
                                        <div>
                                            <div style={s.searchSymbol}>{inst.symbol}</div>
                                            <div style={s.searchName}>{inst.name}</div>
                                        </div>
                                        <div style={s.searchAdd}>+</div>
                                    </div>
                                ))}
                                {filteredInstruments.length === 0 && (
                                    <div style={{ padding: "12px 14px", color: "var(--text-muted)", fontSize: 13 }}>
                                        Sonuç bulunamadı
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                )}
            </div>
        </Modal>
    );
}

const s = {
    root: { display: "flex", flexDirection: "column", gap: 16, paddingBottom: 8 },
    controls: { display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 10 },
    periodRow: { display: "flex", gap: 6 },
    periodBtn: {
        padding: "7px 14px", borderRadius: 6,
        border: "1px solid var(--border-card)", background: "var(--input-bg)",
        color: "var(--text-muted)", cursor: "pointer", fontSize: 13, fontWeight: 500, transition: "all 0.2s",
    },
    periodActive: {
        border: "1px solid var(--accent-solid)", background: "var(--accent)", color: "var(--accent-solid)",
    },
    modeRow: { display: "flex", gap: 6 },
    modeBtn: {
        padding: "7px 14px", borderRadius: 6,
        border: "1px solid var(--border-card)", background: "var(--input-bg)",
        color: "var(--text-muted)", cursor: "pointer", fontSize: 13, fontWeight: 500, transition: "all 0.2s",
    },
    modeActive: {
        border: "1px solid var(--accent-solid)", background: "var(--accent)", color: "var(--accent-solid)",
    },
    selectedRow: { display: "flex", gap: 8, flexWrap: "wrap" },
    selectedChip: {
        display: "flex", alignItems: "center", gap: 8, padding: "5px 12px",
        background: "var(--bg-panel)", border: "1px solid var(--border-card)", borderRadius: 20,
    },
    chipColor: { width: 10, height: 10, borderRadius: "50%" },
    chipText: { fontSize: 13, fontWeight: 600, color: "var(--text-primary)" },
    chipRemove: {
        background: "transparent", border: "none", color: "var(--text-muted)",
        cursor: "pointer", fontSize: 18, lineHeight: 1, padding: 0, width: 20, height: 20,
    },
    chartWrap: { minHeight: 300 },
    loading: {
        display: "flex", alignItems: "center", justifyContent: "center",
        height: 300, color: "var(--text-muted)", fontSize: 14,
    },
    spinner: {
        width: 28, height: 28,
        border: "3px solid rgba(34,197,94,0.2)",
        borderTop: "3px solid #22c55e",
        borderRadius: "50%",
        animation: "spin 0.8s linear infinite",
    },
    inflationBtn: {
        alignSelf: "flex-start",
        padding: "8px 14px",
        borderRadius: 8,
        border: "1px dashed var(--accent-solid, #3b82f6)",
        background: "transparent",
        color: "var(--accent-solid, #3b82f6)",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
    },
    inflationHint: {
        padding: "8px 12px",
        background: "var(--bg-panel)",
        border: "1px solid var(--border-soft)",
        borderRadius: 8,
        color: "var(--text-muted)",
        fontSize: 12,
    },
    addSection: { display: "flex", flexDirection: "column", gap: 0 },
    searchInput: {
        width: "100%", padding: "10px 14px",
        background: "var(--input-bg)", border: "1px solid var(--input-border)",
        borderRadius: 8, color: "var(--text-primary)", fontSize: 14, outline: "none", boxSizing: "border-box",
    },
    searchResults: {
        marginTop: 4, background: "var(--bg-card)", border: "1px solid var(--border-card)",
        borderRadius: 8, maxHeight: 220, overflowY: "auto",
    },
    searchItem: {
        display: "flex", justifyContent: "space-between", alignItems: "center",
        padding: "10px 14px", cursor: "pointer", borderBottom: "1px solid var(--border-card)", transition: "background 0.2s",
    },
    searchSymbol: { fontSize: 13, fontWeight: 600, color: "var(--text-primary)" },
    searchName: { fontSize: 11, color: "var(--text-muted)", marginTop: 2 },
    searchAdd: {
        width: 24, height: 24, borderRadius: "50%", background: "var(--accent-solid)",
        color: "#000", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 18, fontWeight: 600,
    },
};

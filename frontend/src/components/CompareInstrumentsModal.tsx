import { useEffect, useRef, useState, useCallback, useMemo } from "react";
import Modal from "./Modal";
import { getMarketHistory, getMarketSummary, type MarketHistoryPoint, type MarketSummaryItem } from "../api/portfolioApi";

type Period = "1D" | "5D" | "30D" | "1Y";
type ComparisonMode = "percentage" | "price" | "usd";

type Props = {
    baseInstrument: MarketSummaryItem | null;
    onClose: () => void;
};

const PERIODS: { label: string; value: Period }[] = [
    { label: "1G", value: "1D" },
    { label: "5G", value: "5D" },
    { label: "1A", value: "30D" },
    { label: "1Y", value: "1Y" },
];

const COLORS = ["#3b82f6", "#ef4444", "#22c55e", "#f59e0b", "#8b5cf6"];

// ── Pure SVG multi-line chart ──────────────────────────────────────────────
interface SeriesData {
    symbol: string;
    color: string;
    points: { x: number; y: number; label: string; value: number }[];
}

interface SVGChartProps {
    series: SeriesData[];
    xLabels: string[];
    yLabel: string;
    mode: ComparisonMode;
}

function SVGChart({ series, xLabels, yLabel, mode }: SVGChartProps) {
    const [tooltip, setTooltip] = useState<{ x: number; y: number; label: string; values: { symbol: string; color: string; value: number }[] } | null>(null);
    const svgRef = useRef<SVGSVGElement>(null);

    const W = 780, H = 340;
    const PAD = { top: 20, right: 20, bottom: 40, left: 70 };
    const chartW = W - PAD.left - PAD.right;
    const chartH = H - PAD.top - PAD.bottom;

    // Collect all values to compute y scale
    const allValues = series.flatMap(s => s.points.map(p => p.value));
    const minV = allValues.length ? Math.min(...allValues) : 0;
    const maxV = allValues.length ? Math.max(...allValues) : 1;
    const vRange = maxV - minV || 1;
    const vPad = vRange * 0.08;

    const yMin = minV - vPad;
    const yMax = maxV + vPad;

    const toX = (i: number) => PAD.left + (i / Math.max(xLabels.length - 1, 1)) * chartW;
    const toY = (v: number) => PAD.top + chartH - ((v - yMin) / (yMax - yMin)) * chartH;

    // Y axis ticks
    const yTicks = useMemo(() => {
        const count = 6;
        return Array.from({ length: count }, (_, i) => yMin + (i / (count - 1)) * (yMax - yMin));
    }, [yMin, yMax]);

    // X axis ticks — show ~6 evenly spaced labels
    const xTickIndices = useMemo(() => {
        if (xLabels.length <= 6) return xLabels.map((_, i) => i);
        const step = Math.floor(xLabels.length / 5);
        const indices = [];
        for (let i = 0; i < xLabels.length; i += step) indices.push(i);
        if (indices[indices.length - 1] !== xLabels.length - 1) indices.push(xLabels.length - 1);
        return indices;
    }, [xLabels]);

    const formatValue = (v: number) => {
        if (mode === "percentage") return `${v.toFixed(2)}%`;
        if (mode === "usd") return `$${v.toFixed(2)}`;
        return `₺${v.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    };

    const handleMouseMove = useCallback((e: React.MouseEvent<SVGSVGElement>) => {
        if (!svgRef.current || xLabels.length === 0) return;
        const rect = svgRef.current.getBoundingClientRect();
        const mouseX = (e.clientX - rect.left) * (W / rect.width) - PAD.left;
        const idx = Math.round((mouseX / chartW) * (xLabels.length - 1));
        const clampedIdx = Math.max(0, Math.min(xLabels.length - 1, idx));

        const values = series
            .map(s => {
                const pt = s.points.find(p => p.label === xLabels[clampedIdx]);
                return pt ? { symbol: s.symbol, color: s.color, value: pt.value } : null;
            })
            .filter(Boolean) as { symbol: string; color: string; value: number }[];

        if (values.length > 0) {
            setTooltip({
                x: toX(clampedIdx),
                y: e.clientY - rect.top,
                label: xLabels[clampedIdx],
                values,
            });
        }
    }, [series, xLabels, chartW]);

    return (
        <div style={{ position: "relative", width: "100%" }}>
            <svg
                ref={svgRef}
                viewBox={`0 0 ${W} ${H}`}
                style={{ width: "100%", height: "auto", display: "block" }}
                onMouseMove={handleMouseMove}
                onMouseLeave={() => setTooltip(null)}
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
                        {mode === "percentage" ? `${v.toFixed(1)}%` : v.toFixed(1)}
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

                {/* X axis labels */}
                {xTickIndices.map(i => (
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

                {/* Series lines */}
                {series.map(s => {
                    if (s.points.length < 2) return null;
                    // Map each point to its x position using its label's index in xLabels
                    const d = s.points.reduce((acc, pt, i) => {
                        const labelIdx = xLabels.indexOf(pt.label);
                        if (labelIdx === -1) return acc;
                        const x = toX(labelIdx);
                        const y = toY(pt.value);
                        // Use M (move) for first point or after a gap, L (line) otherwise
                        const prevPt = i > 0 ? s.points[i - 1] : null;
                        const prevIdx = prevPt ? xLabels.indexOf(prevPt.label) : -1;
                        const isGap = prevIdx === -1 || labelIdx !== prevIdx + 1;
                        return acc + (acc === "" || isGap ? `M ${x} ${y}` : ` L ${x} ${y}`);
                    }, "");
                    return (
                        <path
                            key={s.symbol}
                            d={d}
                            fill="none"
                            stroke={s.color}
                            strokeWidth={2}
                            strokeLinecap="round"
                            strokeLinejoin="round"
                        />
                    );
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

            {/* Legend */}
            <div style={{ display: "flex", gap: 16, justifyContent: "center", marginTop: 8, flexWrap: "wrap" }}>
                {series.map(s => (
                    <div key={s.symbol} style={{ display: "flex", alignItems: "center", gap: 6 }}>
                        <div style={{ width: 20, height: 2, background: s.color, borderRadius: 1 }} />
                        <span style={{ fontSize: 12, color: "var(--text-muted)" }}>{s.symbol}</span>
                    </div>
                ))}
            </div>
        </div>
    );
}

// ── Main component ─────────────────────────────────────────────────────────
export default function CompareInstrumentsModal({ baseInstrument, onClose }: Props) {
    const [period, setPeriod] = useState<Period>("30D");
    const [mode, setMode] = useState<ComparisonMode>("percentage");
    const [loading, setLoading] = useState(false);
    const [selectedInstruments, setSelectedInstruments] = useState<MarketSummaryItem[]>([]);
    const [availableInstruments, setAvailableInstruments] = useState<MarketSummaryItem[]>([]);
    const [searchTerm, setSearchTerm] = useState("");
    const [usdRate, setUsdRate] = useState(44.75);
    const [rawData, setRawData] = useState<Record<string, MarketHistoryPoint[]>>({});

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

    // Fetch data for all selected instruments
    useEffect(() => {
        if (selectedInstruments.length === 0) return;
        setLoading(true);

        const fetch = async () => {
            const result: Record<string, MarketHistoryPoint[]> = {};
            for (const inst of selectedInstruments) {
                try {
                    result[inst.symbol] = await getMarketHistory(inst.symbol, period);
                } catch {
                    result[inst.symbol] = [];
                }
            }
            setRawData(result);
            setLoading(false);
        };
        fetch();
    }, [selectedInstruments, period]);

    // Build series for SVG chart
    const { series, xLabels } = useMemo(() => {
        // Collect all unique sorted labels — sort by actual value (works for both HH:mm and yyyy-MM-dd)
        const labelSet = new Set<string>();
        Object.values(rawData).forEach(d => d.forEach(p => labelSet.add(p.label)));
        const xLabels = Array.from(labelSet).sort();

        const series: SeriesData[] = selectedInstruments.map((inst, idx) => {
            const data = rawData[inst.symbol] || [];
            // Sort data by label to ensure correct order
            const sortedData = [...data].sort((a, b) => a.label.localeCompare(b.label));
            // Use the very first data point as baseline for percentage (not per-day)
            const first = sortedData[0];
            const points = sortedData
                .map(p => {
                    let value: number;
                    if (mode === "percentage") {
                        value = first && first.close > 0
                            ? ((p.close - first.close) / first.close) * 100
                            : 0;
                    } else if (mode === "usd") {
                        value = p.close / usdRate;
                    } else {
                        value = p.close;
                    }
                    return { x: 0, y: 0, label: p.label, value };
                });
            return { symbol: inst.symbol, color: COLORS[idx % COLORS.length], points };
        });

        return { series, xLabels };
    }, [rawData, selectedInstruments, mode, usdRate]);

    const addInstrument = (instrument: MarketSummaryItem) => {
        if (selectedInstruments.length >= 5) return;
        if (!selectedInstruments.find(i => i.symbol === instrument.symbol)) {
            setSelectedInstruments(prev => [...prev, instrument]);
        }
        setSearchTerm("");
    };

    const removeInstrument = (symbol: string) => {
        setSelectedInstruments(prev => prev.filter(i => i.symbol !== symbol));
    };

    const filteredInstruments = availableInstruments.filter(i =>
        i.symbol.toLowerCase().includes(searchTerm.toLowerCase()) ||
        i.name.toLowerCase().includes(searchTerm.toLowerCase())
    );

    const getYAxisLabel = () => {
        if (mode === "percentage") return "Değişim (%)";
        if (mode === "usd") return "Fiyat (USD)";
        return "Fiyat (₺)";
    };

    if (!baseInstrument) return null;

    return (
        <Modal
            open={!!baseInstrument}
            title={`Karşılaştır: ${baseInstrument.symbol}`}
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
                                {p.label}
                            </button>
                        ))}
                    </div>
                    <div style={s.modeRow}>
                        {(["percentage", "price", "usd"] as ComparisonMode[]).map((m) => (
                            <button
                                key={m}
                                style={{ ...s.modeBtn, ...(mode === m ? s.modeActive : {}) }}
                                onClick={() => setMode(m)}
                            >
                                {m === "percentage" ? "Yüzde (%)" : m === "price" ? "Fiyat (₺)" : "USD Bazlı"}
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
                        <div style={s.loading}>Veri bulunamadı</div>
                    ) : (
                        <SVGChart
                            series={series}
                            xLabels={xLabels}
                            yLabel={getYAxisLabel()}
                            mode={mode}
                        />
                    )}
                </div>

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
                                    <div key={inst.symbol} style={s.searchItem} onClick={() => addInstrument(inst)}>
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

const s: Record<string, React.CSSProperties> = {
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
        color: "#fff", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 18, fontWeight: 600,
    },
};

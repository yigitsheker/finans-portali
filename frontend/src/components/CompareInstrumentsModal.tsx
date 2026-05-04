import { useEffect, useState } from "react";
import Modal from "./Modal";
import {
    ResponsiveContainer,
    LineChart,
    Line,
    XAxis,
    YAxis,
    Tooltip,
    CartesianGrid,
    Legend,
} from "recharts";
import { getMarketHistory, getMarketSummary, type MarketHistoryPoint, type MarketSummaryItem } from "../api/portfolioApi";

type Period = "1D" | "5D" | "30D" | "1Y";
type ComparisonMode = "percentage" | "price" | "usd";

type Props = {
    baseInstrument: MarketSummaryItem | null;
    onClose: () => void;
};

type CompareData = {
    label: string;
    [key: string]: string | number;
};

const PERIODS: { label: string; value: Period }[] = [
    { label: "1G", value: "1D" },
    { label: "5G", value: "5D" },
    { label: "1A", value: "30D" },
    { label: "1Y", value: "1Y" },
];

const COLORS = ["#3b82f6", "#ef4444", "#10b981", "#f59e0b", "#8b5cf6"];

export default function CompareInstrumentsModal({ baseInstrument, onClose }: Props) {
    const [period, setPeriod] = useState<Period>("30D");
    const [mode, setMode] = useState<ComparisonMode>("percentage");
    const [compareData, setCompareData] = useState<CompareData[]>([]);
    const [loading, setLoading] = useState(false);
    const [selectedInstruments, setSelectedInstruments] = useState<MarketSummaryItem[]>([]);
    const [availableInstruments, setAvailableInstruments] = useState<MarketSummaryItem[]>([]);
    const [searchTerm, setSearchTerm] = useState("");
    const [usdRate, setUsdRate] = useState(44.75); // Default USD/TRY rate

    const isDark = document.documentElement.getAttribute("data-theme") !== "light";
    const axisColor = isDark ? "#7d8590" : "#656d76";
    const gridColor = isDark ? "rgba(48, 54, 61, 0.5)" : "rgba(208, 215, 222, 0.5)";

    useEffect(() => {
        getMarketSummary()
            .then((data) => {
                const filtered = data.filter(
                    (item) => item.type !== "INDEX" && item.symbol !== baseInstrument?.symbol
                );
                setAvailableInstruments(filtered);
                
                // Get USD/TRY rate
                const usdtry = data.find(item => item.symbol === "USDTRY");
                if (usdtry) {
                    setUsdRate(usdtry.last);
                }
            })
            .catch((error) => {
                console.error("Failed to load instruments:", error);
            });
    }, [baseInstrument]);

    useEffect(() => {
        if (baseInstrument) {
            setSelectedInstruments([baseInstrument]);
        }
    }, [baseInstrument]);

    useEffect(() => {
        if (selectedInstruments.length === 0) return;

        setLoading(true);
        setCompareData([]);

        const loadAllData = async () => {
            try {
                const allData: { [symbol: string]: MarketHistoryPoint[] } = {};

                for (const instrument of selectedInstruments) {
                    try {
                        const data = await getMarketHistory(instrument.symbol, period);
                        allData[instrument.symbol] = data;
                    } catch (error) {
                        console.error(`Failed to load data for ${instrument.symbol}:`, error);
                        allData[instrument.symbol] = [];
                    }
                }

                // Find common dates
                const allDates = new Set<string>();
                Object.values(allData).forEach(data => {
                    data.forEach(point => allDates.add(point.label));
                });
                const sortedDates = Array.from(allDates).sort();

                // Normalize data based on mode
                const normalized: CompareData[] = sortedDates.map(date => {
                    const point: CompareData = { label: date };
                    
                    selectedInstruments.forEach(instrument => {
                        const data = allData[instrument.symbol] || [];
                        const dataPoint = data.find(d => d.label === date);
                        
                        if (dataPoint) {
                            const firstPoint = data[0];
                            if (firstPoint && firstPoint.close > 0) {
                                if (mode === "percentage") {
                                    // Percentage change from first value
                                    point[instrument.symbol] = ((dataPoint.close - firstPoint.close) / firstPoint.close) * 100;
                                } else if (mode === "price") {
                                    // Actual price
                                    point[instrument.symbol] = dataPoint.close;
                                } else if (mode === "usd") {
                                    // Price in USD
                                    point[instrument.symbol] = dataPoint.close / usdRate;
                                }
                            }
                        }
                    });
                    
                    return point;
                });

                setCompareData(normalized);
            } catch (error) {
                console.error("Failed to load comparison data:", error);
            } finally {
                setLoading(false);
            }
        };

        loadAllData();
    }, [selectedInstruments, period, mode, usdRate]);

    const addInstrument = (instrument: MarketSummaryItem) => {
        if (selectedInstruments.length >= 5) {
            alert("En fazla 5 enstrüman karşılaştırabilirsiniz");
            return;
        }
        if (!selectedInstruments.find(i => i.symbol === instrument.symbol)) {
            setSelectedInstruments([...selectedInstruments, instrument]);
        }
        setSearchTerm("");
    };

    const removeInstrument = (symbol: string) => {
        setSelectedInstruments(selectedInstruments.filter(i => i.symbol !== symbol));
    };

    const filteredInstruments = availableInstruments.filter(
        (item) =>
            item.symbol.toLowerCase().includes(searchTerm.toLowerCase()) ||
            item.name.toLowerCase().includes(searchTerm.toLowerCase())
    );

    if (!baseInstrument) return null;

    const getYAxisLabel = () => {
        if (mode === "percentage") return "Değişim (%)";
        if (mode === "usd") return "Fiyat (USD)";
        return "Fiyat (₺)";
    };

    return (
        <Modal
            open={!!baseInstrument}
            title={`Karşılaştır: ${baseInstrument.symbol}`}
            onClose={onClose}
        >
            <div style={s.root}>
                {/* Controls */}
                <div style={s.controls}>
                    <div style={s.periodRow}>
                        {PERIODS.map((p) => (
                            <button
                                key={p.value}
                                style={{
                                    ...s.periodBtn,
                                    ...(period === p.value ? s.periodActive : {}),
                                }}
                                onClick={() => setPeriod(p.value)}
                            >
                                {p.label}
                            </button>
                        ))}
                    </div>
                    <div style={s.modeRow}>
                        <button
                            style={{
                                ...s.modeBtn,
                                ...(mode === "percentage" ? s.modeActive : {}),
                            }}
                            onClick={() => setMode("percentage")}
                        >
                            Yüzde (%)
                        </button>
                        <button
                            style={{
                                ...s.modeBtn,
                                ...(mode === "price" ? s.modeActive : {}),
                            }}
                            onClick={() => setMode("price")}
                        >
                            Fiyat (₺)
                        </button>
                        <button
                            style={{
                                ...s.modeBtn,
                                ...(mode === "usd" ? s.modeActive : {}),
                            }}
                            onClick={() => setMode("usd")}
                        >
                            USD Bazlı
                        </button>
                    </div>
                </div>

                {/* Selected Instruments */}
                <div style={s.selectedRow}>
                    {selectedInstruments.map((instrument, index) => (
                        <div key={instrument.symbol} style={s.selectedChip}>
                            <div style={{ ...s.chipColor, background: COLORS[index % COLORS.length] }}></div>
                            <span style={s.chipText}>{instrument.symbol}</span>
                            {selectedInstruments.length > 1 && (
                                <button
                                    style={s.chipRemove}
                                    onClick={() => removeInstrument(instrument.symbol)}
                                >
                                    ×
                                </button>
                            )}
                        </div>
                    ))}
                </div>

                {/* Chart */}
                <div style={s.chartWrap}>
                    {loading ? (
                        <div style={s.loading}>Yükleniyor...</div>
                    ) : compareData.length === 0 ? (
                        <div style={s.loading}>Veri yok</div>
                    ) : (
                        <ResponsiveContainer width="100%" height={400}>
                            <LineChart data={compareData} margin={{ top: 20, right: 30, left: 20, bottom: 20 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
                                <XAxis
                                    dataKey="label"
                                    stroke={axisColor}
                                    tick={{ fill: axisColor, fontSize: 12 }}
                                />
                                <YAxis
                                    stroke={axisColor}
                                    tick={{ fill: axisColor, fontSize: 12 }}
                                    label={{ value: getYAxisLabel(), angle: -90, position: 'insideLeft', fill: axisColor }}
                                />
                                <Tooltip
                                    contentStyle={{
                                        background: "var(--bg-card)",
                                        border: "1px solid var(--border-card)",
                                        borderRadius: 8,
                                        color: "var(--text-primary)",
                                    }}
                                    formatter={(value: any) => {
                                        if (mode === "percentage") {
                                            return `${value.toFixed(2)}%`;
                                        } else if (mode === "usd") {
                                            return `$${value.toFixed(2)}`;
                                        }
                                        return `₺${value.toFixed(2)}`;
                                    }}
                                />
                                <Legend />
                                {selectedInstruments.map((instrument, index) => (
                                    <Line
                                        key={instrument.symbol}
                                        type="monotone"
                                        dataKey={instrument.symbol}
                                        stroke={COLORS[index % COLORS.length]}
                                        strokeWidth={2}
                                        dot={false}
                                        name={instrument.symbol}
                                    />
                                ))}
                            </LineChart>
                        </ResponsiveContainer>
                    )}
                </div>

                {/* Add Instrument */}
                {selectedInstruments.length < 5 && (
                    <div style={s.addSection}>
                        <input
                            type="text"
                            placeholder="Hisse ara..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            style={s.searchInput}
                        />
                        {searchTerm && (
                            <div style={s.searchResults}>
                                {filteredInstruments.slice(0, 10).map((instrument) => (
                                    <div
                                        key={instrument.symbol}
                                        style={s.searchItem}
                                        onClick={() => addInstrument(instrument)}
                                    >
                                        <div>
                                            <div style={s.searchSymbol}>{instrument.symbol}</div>
                                            <div style={s.searchName}>{instrument.name}</div>
                                        </div>
                                        <div style={s.searchAdd}>+</div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}
            </div>
        </Modal>
    );
}

const s: Record<string, React.CSSProperties> = {
    root: { display: "flex", flexDirection: "column", gap: 20 },
    controls: { display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 12 },
    periodRow: { display: "flex", gap: 6 },
    periodBtn: {
        padding: "8px 16px",
        borderRadius: 6,
        border: "1px solid #374151",
        background: "#1f2937",
        color: "#9ca3af",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 500,
        transition: "all 0.2s",
    },
    periodActive: {
        border: "1px solid #3b82f6",
        background: "#1e3a8a",
        color: "#fff",
    },
    modeRow: { display: "flex", gap: 6 },
    modeBtn: {
        padding: "8px 16px",
        borderRadius: 6,
        border: "1px solid #374151",
        background: "#1f2937",
        color: "#9ca3af",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 500,
        transition: "all 0.2s",
    },
    modeActive: {
        border: "1px solid #10b981",
        background: "rgba(16, 185, 129, 0.15)",
        color: "#10b981",
    },
    selectedRow: { display: "flex", gap: 8, flexWrap: "wrap" },
    selectedChip: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        padding: "6px 12px",
        background: "var(--bg-panel)",
        border: "1px solid var(--border-card)",
        borderRadius: 20,
    },
    chipColor: { width: 12, height: 12, borderRadius: "50%" },
    chipText: { fontSize: 13, fontWeight: 600, color: "var(--text-primary)" },
    chipRemove: {
        background: "transparent",
        border: "none",
        color: "var(--text-muted)",
        cursor: "pointer",
        fontSize: 18,
        lineHeight: 1,
        padding: 0,
        width: 20,
        height: 20,
    },
    chartWrap: { minHeight: 400, position: "relative" },
    loading: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        height: 400,
        color: "var(--text-muted)",
        fontSize: 14,
    },
    addSection: { position: "relative" },
    searchInput: {
        width: "100%",
        padding: "10px 14px",
        background: "var(--input-bg)",
        border: "1px solid var(--input-border)",
        borderRadius: 8,
        color: "var(--text-primary)",
        fontSize: 14,
        outline: "none",
    },
    searchResults: {
        position: "absolute",
        top: "100%",
        left: 0,
        right: 0,
        marginTop: 4,
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        maxHeight: 300,
        overflowY: "auto",
        zIndex: 10,
    },
    searchItem: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        padding: "10px 14px",
        cursor: "pointer",
        borderBottom: "1px solid var(--border-card)",
        transition: "background 0.2s",
    },
    searchSymbol: { fontSize: 13, fontWeight: 600, color: "var(--text-primary)" },
    searchName: { fontSize: 11, color: "var(--text-muted)", marginTop: 2 },
    searchAdd: {
        width: 24,
        height: 24,
        borderRadius: "50%",
        background: "var(--accent-solid)",
        color: "#fff",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: 18,
        fontWeight: 600,
    },
};

import { useEffect, useState } from "react";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";
import { getTechnicalAnalysis } from "../api/portfolioApi";

export default function TechnicalAnalysisPanel({ symbol, period }) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    // Indicator toggles
    const [showSMA7, setShowSMA7] = useState(true);
    const [showSMA20, setShowSMA20] = useState(true);
    const [showSMA50, setShowSMA50] = useState(false);

    useEffect(() => {
        if (!symbol) return;

        setLoading(true);
        setError(null);

        // Calculate date range based on period
        const to = new Date();
        let from = new Date();

        switch (period) {
            case "1D":
                from.setDate(to.getDate() - 1);
                break;
            case "5D":
                from.setDate(to.getDate() - 5);
                break;
            case "30D":
                from.setMonth(to.getMonth() - 1);
                break;
            case "1Y":
                from.setFullYear(to.getFullYear() - 1);
                break;
            default:
                from.setMonth(to.getMonth() - 3);
        }

        const fromStr = from.toISOString().split('T')[0];
        const toStr = to.toISOString().split('T')[0];

        getTechnicalAnalysis(symbol, fromStr, toStr)
            .then(setData)
            .catch((err) => {
                console.error("Failed to load technical analysis:", err);
                setError("Teknik analiz verileri yüklenemedi");
            })
            .finally(() => setLoading(false));
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
                <div style={{ fontSize: 32, marginBottom: 8 }}>📊</div>
                <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Yetersiz Veri</div>
                <div style={{ fontSize: 12, color: "var(--text-muted)" }}>
                    {data.trend.description}
                </div>
            </div>
        );
    }

    // Prepare chart data
    const chartData = data.series.map(point => ({
        date: new Date(point.date).toLocaleDateString('tr-TR', { month: 'short', day: 'numeric' }),
        Fiyat: point.close,
        ...(showSMA7 && point.sma7 ? { 'SMA 7': point.sma7 } : {}),
        ...(showSMA20 && point.sma20 ? { 'SMA 20': point.sma20 } : {}),
        ...(showSMA50 && point.sma50 ? { 'SMA 50': point.sma50 } : {}),
    }));

    const trendColor =
        data.trend.direction === "UPWARD" ? "#10b981" :
        data.trend.direction === "DOWNWARD" ? "#ef4444" : "#6b7280";

    const trendIcon =
        data.trend.direction === "UPWARD" ? "📈" :
        data.trend.direction === "DOWNWARD" ? "📉" : "➡️";

    const isDark = document.documentElement.getAttribute("data-theme") !== "light";
    const tooltipBg = isDark ? "#1c2128" : "#ffffff";
    const tooltipBorder = isDark ? "#30363d" : "#d0d7de";
    const tooltipColor = isDark ? "#e6edf3" : "#1f2328";
    const gridColor = isDark ? "#30363d" : "#e5e7eb";
    const axisColor = isDark ? "#8b949e" : "#6b7280";

    return (
        <div style={s.root}>
            {/* Disclaimer */}
            <div style={s.disclaimer}>
                ⚠️ Bu bölüm yalnızca temel teknik analiz göstergelerini sunar. Yatırım tavsiyesi değildir.
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
                        {data.trend.changePercent >= 0 ? "+" : ""}{data.trend.changePercent.toFixed(2)}%
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
                    <div style={s.cardSub}>Direnç seviyesi</div>
                </div>

                <div style={s.card}>
                    <div style={s.cardLabel}>En Düşük</div>
                    <div style={{ ...s.cardValue, color: "#ef4444" }}>
                        {data.summary.lowestClose.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}
                    </div>
                    <div style={s.cardSub}>Destek seviyesi</div>
                </div>

                <div style={s.card}>
                    <div style={s.cardLabel}>Volatilite</div>
                    <div style={s.cardValue}>{data.summary.volatilityPercent.toFixed(2)}%</div>
                    <div style={s.cardSub}>Standart sapma</div>
                </div>
            </div>

            {/* Indicator Toggles */}
            <div style={s.toggleRow}>
                <div style={s.toggleLabel}>Göstergeler:</div>
                <label style={s.toggle}>
                    <input
                        type="checkbox"
                        checked={showSMA7}
                        onChange={(e) => setShowSMA7(e.target.checked)}
                        style={s.checkbox}
                    />
                    <span style={{ color: "#3b82f6" }}>SMA 7</span>
                </label>
                <label style={s.toggle}>
                    <input
                        type="checkbox"
                        checked={showSMA20}
                        onChange={(e) => setShowSMA20(e.target.checked)}
                        style={s.checkbox}
                    />
                    <span style={{ color: "#f59e0b" }}>SMA 20</span>
                </label>
                <label style={s.toggle}>
                    <input
                        type="checkbox"
                        checked={showSMA50}
                        onChange={(e) => setShowSMA50(e.target.checked)}
                        style={s.checkbox}
                    />
                    <span style={{ color: "#8b5cf6" }}>SMA 50</span>
                </label>
            </div>

            {/* Chart */}
            <div style={s.chartWrap}>
                <ResponsiveContainer width="100%" height={350}>
                    <LineChart data={chartData} margin={{ top: 5, right: 20, left: 10, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
                        <XAxis
                            dataKey="date"
                            stroke={axisColor}
                            style={{ fontSize: 11 }}
                        />
                        <YAxis
                            stroke={axisColor}
                            style={{ fontSize: 11 }}
                            domain={['auto', 'auto']}
                        />
                        <Tooltip
                            contentStyle={{
                                background: tooltipBg,
                                border: `1px solid ${tooltipBorder}`,
                                borderRadius: 6,
                                color: tooltipColor,
                                fontSize: 12
                            }}
                        />
                        <Legend
                            wrapperStyle={{ fontSize: 12 }}
                        />
                        <Line
                            type="monotone"
                            dataKey="Fiyat"
                            stroke="#10b981"
                            strokeWidth={2}
                            dot={false}
                        />
                        {showSMA7 && (
                            <Line
                                type="monotone"
                                dataKey="SMA 7"
                                stroke="#3b82f6"
                                strokeWidth={1.5}
                                strokeDasharray="5 5"
                                dot={false}
                            />
                        )}
                        {showSMA20 && (
                            <Line
                                type="monotone"
                                dataKey="SMA 20"
                                stroke="#f59e0b"
                                strokeWidth={1.5}
                                strokeDasharray="5 5"
                                dot={false}
                            />
                        )}
                        {showSMA50 && (
                            <Line
                                type="monotone"
                                dataKey="SMA 50"
                                stroke="#8b5cf6"
                                strokeWidth={1.5}
                                strokeDasharray="5 5"
                                dot={false}
                            />
                        )}
                    </LineChart>
                </ResponsiveContainer>
            </div>

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

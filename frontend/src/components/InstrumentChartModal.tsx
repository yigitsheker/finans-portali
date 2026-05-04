import { useEffect, useState } from "react";
import Modal from "./Modal";
import PriceAlertModal from "./PriceAlertModal";
import {
    ResponsiveContainer,
    AreaChart,
    Area,
    XAxis,
    YAxis,
    Tooltip,
    CartesianGrid,
} from "recharts";
import { 
    getMarketHistory, 
    getTrendAnalysis,
    getSupportResistance,
    getMovingAverages,
    type MarketHistoryPoint, 
    type MarketSummaryItem,
    type TechnicalAnalysis
} from "../api/portfolioApi";
import type Keycloak from "keycloak-js";

type Period = "1D" | "5D" | "30D" | "1Y";

type Props = {
    instrument: MarketSummaryItem | null;
    onClose: () => void;
    keycloak?: Keycloak;
    onAddToPortfolio?: (instrument: MarketSummaryItem) => void;
    onCompare?: (instrument: MarketSummaryItem) => void;
};

const PERIODS: { label: string; value: Period }[] = [
    { label: "1G", value: "1D" },
    { label: "5G", value: "5D" },
    { label: "1A", value: "30D" },
    { label: "1Y", value: "1Y" },
];

export default function InstrumentChartModal({ instrument, onClose, keycloak, onAddToPortfolio, onCompare }: Props) {
    const [period, setPeriod] = useState<Period>("30D");
    const [data, setData] = useState<MarketHistoryPoint[]>([]);
    const [loading, setLoading] = useState(false);
    const [showAlertModal, setShowAlertModal] = useState(false);
    const [activeTab, setActiveTab] = useState<'chart' | 'analysis'>('chart');
    const [technicalData, setTechnicalData] = useState<{
        trend?: TechnicalAnalysis;
        support?: TechnicalAnalysis;
        ma?: TechnicalAnalysis;
    }>({});

    // Add console logging for debugging
    const log = {
        info: (msg: string) => console.log(`[Chart] ${msg}`),
        error: (msg: string, error?: any) => console.error(`[Chart] ${msg}`, error)
    };

    // Tema rengini CSS değişkeninden oku
    const isDark = document.documentElement.getAttribute("data-theme") !== "light";
    const axisColor    = isDark ? "rgba(255,255,255,0.45)" : "rgba(0,0,0,0.45)";
    const gridColor    = isDark ? "rgba(255,255,255,0.06)" : "rgba(0,0,0,0.08)";
    const tooltipBg    = isDark ? "#1e1e2e" : "#ffffff";
    const tooltipBorder = isDark ? "rgba(255,255,255,0.12)" : "rgba(0,0,0,0.12)";
    const tooltipColor = isDark ? "#e5e7eb" : "#1a1f2e";

    useEffect(() => {
        if (!instrument) return;
        
        setLoading(true);
        setData([]); // Clear previous data
        
        log.info(`Fetching chart data for ${instrument.symbol} period ${period}`);
        
        getMarketHistory(instrument.symbol, period)
            .then((newData) => {
                log.info(`Received ${newData.length} data points for ${instrument.symbol} period ${period}`);
                setData(newData);
            })
            .catch((error) => {
                console.error(`Failed to fetch chart data for ${instrument.symbol}:`, error);
                setData([]);
            })
            .finally(() => setLoading(false));
    }, [instrument, period]);

    // Load technical analysis data
    useEffect(() => {
        if (!instrument) return;
        
        const loadTechnicalAnalysis = async () => {
            try {
                const [trendData, supportData, maData] = await Promise.all([
                    getTrendAnalysis(instrument.symbol).catch(() => undefined),
                    getSupportResistance(instrument.symbol).catch(() => undefined),
                    getMovingAverages(instrument.symbol, 20).catch(() => undefined)
                ]);
                
                setTechnicalData({
                    trend: trendData,
                    support: supportData,
                    ma: maData
                });
            } catch (error) {
                console.error('Failed to load technical analysis:', error);
            }
        };

        loadTechnicalAnalysis();
    }, [instrument]);

    if (!instrument) return null;

    const positive = instrument.changePct >= 0;
    const color = positive ? "#4ade80" : "#f87171";

    const minVal = data.length ? Math.min(...data.map((d) => d.close)) * 0.998 : 0;
    const maxVal = data.length ? Math.max(...data.map((d) => d.close)) * 1.002 : 0;

    return (
        <Modal
            open={!!instrument}
            title={`${instrument.symbol} — ${instrument.name}`}
            onClose={onClose}
        >
            {/* Fiyat başlığı */}
            <div style={s.priceRow}>
                <span style={{ fontSize: 22, fontWeight: 800, color: "var(--text-primary)" }}>
                    {instrument.last?.toLocaleString()}
                </span>
                <span style={{ color, fontWeight: 600, fontSize: 14 }}>
                    {positive ? "+" : ""}{instrument.changePct?.toFixed(2)}%
                    &nbsp;({positive ? "+" : ""}{instrument.changeAbs?.toFixed(2)})
                </span>
                <span style={s.typeBadge}>{instrument.type}</span>
                
                {/* Alert Button */}
                {keycloak?.authenticated && (
                    <button
                        onClick={() => setShowAlertModal(true)}
                        style={s.alertButton}
                        title="Fiyat Alarmı Oluştur"
                    >
                        🔔 Alarm
                    </button>
                )}
                
                {/* Compare Button */}
                {onCompare && (
                    <button
                        onClick={() => onCompare(instrument)}
                        style={s.compareButton}
                        title="Başka Hisse ile Karşılaştır"
                    >
                        📊 Karşılaştır
                    </button>
                )}
                
                {/* Add to Portfolio Button */}
                {keycloak?.authenticated && onAddToPortfolio && (
                    <button
                        onClick={() => onAddToPortfolio(instrument)}
                        style={s.addButton}
                        title="Portföye Ekle"
                    >
                        + Portföye Ekle
                    </button>
                )}
            </div>

            {/* Period seçici */}
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
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
                <button
                    style={s.detailedChartBtn}
                    onClick={() => window.open(`/chart?symbol=${instrument.symbol}`, '_blank')}
                >
                    📊 Detaylı Grafik
                </button>
            </div>

            {/* Tab sistemi */}
            <div style={s.tabRow}>
                <button
                    style={{
                        ...s.tabBtn,
                        ...(activeTab === 'chart' ? s.tabActive : {}),
                    }}
                    onClick={() => setActiveTab('chart')}
                >
                    📈 Grafik
                </button>
                <button
                    style={{
                        ...s.tabBtn,
                        ...(activeTab === 'analysis' ? s.tabActive : {}),
                    }}
                    onClick={() => setActiveTab('analysis')}
                >
                    🔍 Teknik Analiz
                </button>
            </div>

            {/* İçerik */}
            {activeTab === 'chart' ? (
                /* Grafik */
                <div style={s.chartWrap}>
                    {loading ? (
                        <div style={s.loading}>Yükleniyor...</div>
                    ) : data.length === 0 ? (
                        <div style={s.loading}>
                            Veri yok - {instrument.symbol} için {period} verisi bulunamadı
                        </div>
                    ) : (
                        <div style={{ width: "100%", height: 300 }}>
                            <ResponsiveContainer width="100%" height="100%">
                                <AreaChart 
                                    data={data} 
                                    margin={{ top: 20, right: 30, left: 20, bottom: 20 }}
                                >
                                    <defs>
                                        <linearGradient id="chartGrad" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="5%"  stopColor={color} stopOpacity={0.25} />
                                            <stop offset="95%" stopColor={color} stopOpacity={0} />
                                        </linearGradient>
                                    </defs>
                                    <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
                                    <XAxis
                                        dataKey="label"
                                        tick={{ fill: axisColor, fontSize: 11 }}
                                        tickLine={false}
                                        axisLine={false}
                                        interval="preserveStartEnd"
                                    />
                                    <YAxis
                                        domain={[minVal, maxVal]}
                                        tick={{ fill: axisColor, fontSize: 11 }}
                                        tickLine={false}
                                        axisLine={false}
                                        width={70}
                                        tickFormatter={(v) => Number(v).toLocaleString()}
                                    />
                                    <Tooltip
                                        contentStyle={{
                                            background: tooltipBg,
                                            border: `1px solid ${tooltipBorder}`,
                                            borderRadius: 8,
                                            color: tooltipColor,
                                            fontSize: 12,
                                            boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                                        }}
                                        formatter={(v: any) => [Number(v).toLocaleString(), "Fiyat"]}
                                        labelFormatter={(label) => `Tarih: ${label}`}
                                        cursor={{ stroke: axisColor, strokeWidth: 1, strokeDasharray: "3 3" }}
                                        position={{ x: undefined, y: undefined }}
                                        allowEscapeViewBox={{ x: false, y: false }}
                                    />
                                    <Area
                                        type="monotone"
                                        dataKey="close"
                                        stroke={color}
                                        strokeWidth={2}
                                        fill="url(#chartGrad)"
                                        dot={false}
                                        activeDot={{ 
                                            r: 5, 
                                            fill: color,
                                            stroke: tooltipBg,
                                            strokeWidth: 2
                                        }}
                                        connectNulls={false}
                                    />
                                </AreaChart>
                            </ResponsiveContainer>
                        </div>
                    )}
                    
                    {/* Debug info */}
                    {data.length > 0 && (
                        <div style={s.debugInfo}>
                            {data.length} veri noktası • 
                            {data[0]?.label} - {data[data.length - 1]?.label} • 
                            Son: {data[data.length - 1]?.close?.toLocaleString()}
                        </div>
                    )}
                </div>
            ) : (
                /* Teknik Analiz */
                <div style={s.analysisWrap}>
                    <div style={s.analysisGrid}>
                        {/* Trend Analizi */}
                        {technicalData.trend && (
                            <div style={s.analysisCard}>
                                <h4 style={s.analysisTitle}>📈 Trend Analizi</h4>
                                <div style={s.analysisContent}>
                                    <div style={s.trendIndicator}>
                                        <span style={{
                                            ...s.trendBadge,
                                            backgroundColor: 
                                                technicalData.trend.trend === 'YUKSELEN' ? '#dcfce7' :
                                                technicalData.trend.trend === 'DUSEN' ? '#fef2f2' : '#f3f4f6',
                                            color:
                                                technicalData.trend.trend === 'YUKSELEN' ? '#166534' :
                                                technicalData.trend.trend === 'DUSEN' ? '#991b1b' : '#374151'
                                        }}>
                                            {technicalData.trend.trend}
                                        </span>
                                    </div>
                                    <div style={s.analysisDetail}>
                                        <span>Değişim: {technicalData.trend.change_percent?.toFixed(2)}%</span>
                                    </div>
                                </div>
                            </div>
                        )}

                        {/* Destek/Direnç */}
                        {technicalData.support && (
                            <div style={s.analysisCard}>
                                <h4 style={s.analysisTitle}>📊 Destek & Direnç</h4>
                                <div style={s.analysisContent}>
                                    <div style={s.supportResistance}>
                                        <div>
                                            <span style={s.label}>Direnç:</span>
                                            <span style={s.value}>{technicalData.support.resistance?.toLocaleString()}</span>
                                        </div>
                                        <div>
                                            <span style={s.label}>Destek:</span>
                                            <span style={s.value}>{technicalData.support.support?.toLocaleString()}</span>
                                        </div>
                                        <div>
                                            <span style={s.label}>Pozisyon:</span>
                                            <span style={s.value}>{technicalData.support.position_in_range?.toFixed(1)}%</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}

                        {/* Hareketli Ortalama */}
                        {technicalData.ma && (
                            <div style={s.analysisCard}>
                                <h4 style={s.analysisTitle}>📉 Hareketli Ortalama (20)</h4>
                                <div style={s.analysisContent}>
                                    <div style={s.maData}>
                                        <div>
                                            <span style={s.label}>SMA:</span>
                                            <span style={s.value}>{technicalData.ma.sma_current?.toLocaleString()}</span>
                                        </div>
                                        <div>
                                            <span style={s.label}>EMA:</span>
                                            <span style={s.value}>{technicalData.ma.ema_current?.toLocaleString()}</span>
                                        </div>
                                        <div>
                                            <span style={s.label}>Güncel:</span>
                                            <span style={s.value}>{technicalData.ma.current_price?.toLocaleString()}</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            )}
            
            {/* Price Alert Modal */}
            {showAlertModal && keycloak && (
                <PriceAlertModal
                    open={showAlertModal}
                    onClose={() => setShowAlertModal(false)}
                    keycloak={keycloak}
                    prefilledSymbol={instrument.symbol}
                    prefilledPrice={instrument.last}
                />
            )}
        </Modal>
    );
}

const s: Record<string, React.CSSProperties> = {
    priceRow: {
        display: "flex",
        alignItems: "center",
        gap: 12,
        marginBottom: 14,
        flexWrap: "wrap",
    },
    typeBadge: {
        marginLeft: "auto",
        padding: "4px 10px",
        borderRadius: 999,
        border: "1px solid var(--border-card)",
        background: "var(--input-bg)",
        color: "var(--text-muted)",
        fontSize: 11,
    },
    periodRow: { display: "flex", gap: 8, marginBottom: 14 },
    
    tabRow: { 
        display: "flex", 
        gap: 8, 
        marginBottom: 14,
        borderBottom: "1px solid var(--border-card)"
    },
    
    tabBtn: {
        padding: "10px 18px",
        border: "none",
        background: "transparent",
        color: "var(--text-muted)",
        cursor: "pointer",
        borderRadius: "6px 6px 0 0",
        fontSize: 14,
        fontWeight: 600,
        transition: "all 0.2s ease",
    },
    
    tabActive: {
        background: "var(--accent-hover-bg)",
        color: "var(--accent-solid)",
        borderBottom: "2px solid var(--accent-solid)",
    },
    periodBtn: {
        padding: "8px 16px",
        borderRadius: 6,
        border: "1px solid var(--border-card)",
        background: "var(--input-bg)",
        color: "var(--text-muted)",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 500,
        transition: "all 0.2s",
    },
    periodActive: {
        border: "1px solid var(--accent-solid)",
        background: "var(--accent)",
        color: "var(--text-primary)",
    },
    chartWrap: {
        borderRadius: 14,
        border: "1px solid var(--border)",
        background: "var(--bg-panel2)",
        padding: 16,
        position: "relative" as const,
        overflow: "hidden",
    },
    loading: {
        height: 300,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        color: "var(--text-muted)",
        fontSize: 13,
    },
    debugInfo: {
        marginTop: 8,
        padding: "4px 8px",
        fontSize: 10,
        color: "var(--text-muted)",
        background: "var(--bg-panel)",
        borderRadius: 4,
        textAlign: "center" as const,
    },
    alertButton: {
        padding: "8px 16px",
        background: "var(--accent)",
        color: "var(--accent-solid)",
        border: "1px solid var(--accent-border)",
        borderRadius: 6,
        fontSize: 13,
        fontWeight: 600,
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        gap: 6,
        transition: "all 0.2s",
    },
    compareButton: {
        padding: "8px 16px",
        background: "transparent",
        color: "#10b981",
        border: "1px solid #10b981",
        borderRadius: 6,
        fontSize: 13,
        fontWeight: 600,
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        gap: 6,
        transition: "all 0.2s",
    },
    addButton: {
        padding: "8px 16px",
        background: "#10b981",
        color: "#000",
        border: "none",
        borderRadius: 6,
        fontSize: 13,
        fontWeight: 600,
        cursor: "pointer",
        transition: "all 0.2s",
    },
    detailedChartBtn: {
        padding: "10px 20px",
        borderRadius: 8,
        border: "none",
        background: "#3b82f6",
        color: "#fff",
        fontSize: 14,
        fontWeight: 600,
        cursor: "pointer",
        transition: "all 0.2s",
        display: "flex",
        alignItems: "center",
        gap: 6,
        boxShadow: "0 2px 8px rgba(59, 130, 246, 0.3)",
    },
    
    // Teknik Analiz Stilleri
    analysisWrap: {
        padding: 16,
        background: "var(--bg-panel2)",
        borderRadius: 14,
        border: "1px solid var(--border)",
    },
    
    analysisGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(250px, 1fr))",
        gap: 16,
    },
    
    analysisCard: {
        background: "var(--bg-primary)",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        padding: 16,
    },
    
    analysisTitle: {
        margin: "0 0 12px 0",
        fontSize: 14,
        fontWeight: 600,
        color: "var(--text-primary)",
    },
    
    analysisContent: {
        display: "flex",
        flexDirection: "column" as const,
        gap: 8,
    },
    
    trendIndicator: {
        display: "flex",
        alignItems: "center",
        gap: 8,
    },
    
    trendBadge: {
        padding: "4px 12px",
        borderRadius: 16,
        fontSize: 12,
        fontWeight: 600,
        textTransform: "uppercase" as const,
    },
    
    analysisDetail: {
        fontSize: 12,
        color: "var(--text-muted)",
    },
    
    supportResistance: {
        display: "flex",
        flexDirection: "column" as const,
        gap: 6,
    },
    
    maData: {
        display: "flex",
        flexDirection: "column" as const,
        gap: 6,
    },
    
    label: {
        fontSize: 11,
        color: "var(--text-muted)",
        marginRight: 8,
        minWidth: 60,
        display: "inline-block",
    },
    
    value: {
        fontSize: 12,
        fontWeight: 600,
        color: "var(--text-primary)",
    },
};

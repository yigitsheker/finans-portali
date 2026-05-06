import { useEffect, useMemo, useRef, useState } from "react";
import Modal from "./Modal";
import PriceAlertModal from "./PriceAlertModal";
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

// ── Pure SVG area chart — no external library, no watermark ───────────────
function SVGAreaChart({ data, color }: { data: MarketHistoryPoint[]; color: string }) {
    const [tooltip, setTooltip] = useState<{ x: number; y: number; label: string; value: number } | null>(null);
    const svgRef = useRef<SVGSVGElement>(null);

    const W = 700, H = 280;
    const PAD = { top: 16, right: 16, bottom: 36, left: 72 };
    const cW = W - PAD.left - PAD.right;
    const cH = H - PAD.top - PAD.bottom;

    const sorted = useMemo(() => [...data].sort((a, b) => a.label.localeCompare(b.label)), [data]);

    const values = sorted.map(d => d.close);
    const minV = Math.min(...values) * 0.998;
    const maxV = Math.max(...values) * 1.002;
    const vRange = maxV - minV || 1;

    const toX = (i: number) => PAD.left + (i / Math.max(sorted.length - 1, 1)) * cW;
    const toY = (v: number) => PAD.top + cH - ((v - minV) / vRange) * cH;

    // Build smooth path
    const linePath = sorted.reduce((acc, d, i) => {
        const x = toX(i), y = toY(d.close);
        if (i === 0) return `M ${x} ${y}`;
        const px = toX(i - 1), py = toY(sorted[i - 1].close);
        const cpX = (px + x) / 2;
        return acc + ` C ${cpX} ${py} ${cpX} ${y} ${x} ${y}`;
    }, "");

    const fillPath = linePath + ` L ${toX(sorted.length - 1)} ${PAD.top + cH} L ${toX(0)} ${PAD.top + cH} Z`;

    // Y ticks
    const yTicks = Array.from({ length: 5 }, (_, i) => minV + (i / 4) * vRange);

    // X ticks — ~5 evenly spaced
    const xTickCount = Math.min(5, sorted.length);
    const xTickIndices = Array.from({ length: xTickCount }, (_, i) =>
        Math.round((i / (xTickCount - 1)) * (sorted.length - 1))
    );

    const handleMouseMove = (e: React.MouseEvent<SVGSVGElement>) => {
        if (!svgRef.current || sorted.length === 0) return;
        const rect = svgRef.current.getBoundingClientRect();
        const mouseX = (e.clientX - rect.left) * (W / rect.width) - PAD.left;
        const idx = Math.max(0, Math.min(sorted.length - 1, Math.round((mouseX / cW) * (sorted.length - 1))));
        const pt = sorted[idx];
        setTooltip({ x: toX(idx), y: toY(pt.close), label: pt.label, value: pt.close });
    };

    const fillId = `icm-fill-${color.replace("#", "")}`;

    return (
        <div style={{ position: "relative", width: "100%" }}>
            <svg
                ref={svgRef}
                viewBox={`0 0 ${W} ${H}`}
                style={{ width: "100%", height: "auto", display: "block" }}
                onMouseMove={handleMouseMove}
                onMouseLeave={() => setTooltip(null)}
            >
                <defs>
                    <linearGradient id={fillId} x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor={color} stopOpacity={0.25} />
                        <stop offset="100%" stopColor={color} stopOpacity={0} />
                    </linearGradient>
                </defs>

                {/* Grid */}
                {yTicks.map((v, i) => (
                    <line key={i} x1={PAD.left} y1={toY(v)} x2={PAD.left + cW} y2={toY(v)}
                        stroke="var(--border-soft)" strokeWidth={0.8} strokeDasharray="4 4" />
                ))}

                {/* Y labels */}
                {yTicks.map((v, i) => (
                    <text key={i} x={PAD.left - 6} y={toY(v) + 4} textAnchor="end" fontSize={10} fill="var(--text-muted)">
                        {v.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
                    </text>
                ))}

                {/* X labels */}
                {xTickIndices.map(i => (
                    <text key={i} x={toX(i)} y={H - 6} textAnchor="middle" fontSize={10} fill="var(--text-muted)">
                        {sorted[i]?.label}
                    </text>
                ))}

                {/* Fill */}
                <path d={fillPath} fill={`url(#${fillId})`} />

                {/* Line */}
                <path d={linePath} fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" />

                {/* Crosshair */}
                {tooltip && (
                    <>
                        <line x1={tooltip.x} y1={PAD.top} x2={tooltip.x} y2={PAD.top + cH}
                            stroke="var(--text-muted)" strokeWidth={1} strokeDasharray="4 4" opacity={0.5} />
                        <circle cx={tooltip.x} cy={tooltip.y} r={4} fill={color}
                            stroke="var(--bg-panel)" strokeWidth={2} />
                    </>
                )}
            </svg>

            {/* Tooltip */}
            {tooltip && (
                <div style={{
                    position: "absolute",
                    top: Math.max(0, (tooltip.y / H) * 100) + "%",
                    left: tooltip.x / W > 0.6 ? "auto" : `calc(${(tooltip.x / W) * 100}% + 10px)`,
                    right: tooltip.x / W > 0.6 ? `calc(${100 - (tooltip.x / W) * 100}% + 10px)` : "auto",
                    background: "var(--bg-card)",
                    border: "1px solid var(--border-card)",
                    borderRadius: 8,
                    padding: "6px 10px",
                    pointerEvents: "none",
                    zIndex: 10,
                    boxShadow: "var(--shadow)",
                    transform: "translateY(-50%)",
                }}>
                    <div style={{ fontSize: 11, color: "var(--text-muted)", marginBottom: 2 }}>{tooltip.label}</div>
                    <div style={{ fontSize: 13, fontWeight: 700, color: "var(--text-primary)" }}>
                        {tooltip.value.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                    </div>
                </div>
            )}

            {/* Debug info */}
            <div style={{ fontSize: 10, color: "var(--text-muted)", textAlign: "center", marginTop: 4 }}>
                {sorted.length} veri noktası
                {sorted.length > 0 && ` • ${sorted[0].label} - ${sorted[sorted.length - 1].label}`}
                {sorted.length > 0 && ` • Son: ${sorted[sorted.length - 1].close.toLocaleString("tr-TR")}`}
            </div>
        </div>
    );
}

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
                        <SVGAreaChart data={data} color={color} />
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

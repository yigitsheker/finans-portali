import { useEffect, useRef, useState } from "react";
import Modal from "./Modal";
import PriceAlertModal from "./PriceAlertModal";
import TechnicalAnalysisPanel from "./TechnicalAnalysisPanel";
import { LWAreaChart } from "./common/LWAreaChart";
import {
    getMarketHistory,
    type MarketHistoryPoint,
    type MarketSummaryItem,
} from "../api/portfolioApi";
import { watchlistApi, type Watchlist } from "../api/watchlistApi";
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

    // Watchlist state
    const [watchlists, setWatchlists] = useState<Watchlist[]>([]);
    const [watchlistsLoaded, setWatchlistsLoaded] = useState(false);
    const [showWatchlistMenu, setShowWatchlistMenu] = useState(false);
    const [creatingList, setCreatingList] = useState(false);
    const [newListName, setNewListName] = useState("");
    const [watchlistBusy, setWatchlistBusy] = useState(false);
    const [watchlistFlash, setWatchlistFlash] = useState<string | null>(null);
    const watchlistMenuRef = useRef<HTMLDivElement>(null);

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

    // Load watchlists lazily — only when the user opens the menu the first time.
    const ensureWatchlistsLoaded = async () => {
        if (watchlistsLoaded || !keycloak?.authenticated) return;
        try {
            const lists = await watchlistApi.getWatchlists(keycloak);
            setWatchlists(lists);
        } catch (error) {
            console.error("Failed to load watchlists:", error);
        } finally {
            setWatchlistsLoaded(true);
        }
    };

    // Close the popover on outside click and when the modal closes.
    useEffect(() => {
        if (!showWatchlistMenu) return;
        const handler = (e: MouseEvent) => {
            if (watchlistMenuRef.current && !watchlistMenuRef.current.contains(e.target as Node)) {
                setShowWatchlistMenu(false);
                setCreatingList(false);
                setNewListName("");
            }
        };
        document.addEventListener("mousedown", handler);
        return () => document.removeEventListener("mousedown", handler);
    }, [showWatchlistMenu]);

    useEffect(() => {
        if (!instrument) {
            setShowWatchlistMenu(false);
            setCreatingList(false);
            setNewListName("");
            setWatchlistFlash(null);
        }
    }, [instrument]);

    const handleAddToList = async (list: Watchlist) => {
        if (!instrument || !keycloak) return;
        if (list.symbols.includes(instrument.symbol)) {
            setWatchlistFlash(`Zaten "${list.name}" listesinde`);
            setTimeout(() => setWatchlistFlash(null), 2000);
            return;
        }
        try {
            setWatchlistBusy(true);
            await watchlistApi.addToWatchlist(keycloak, {
                watchlistId: list.id,
                symbol: instrument.symbol,
            });
            setWatchlists((prev) =>
                prev.map((w) => (w.id === list.id ? { ...w, symbols: [...w.symbols, instrument.symbol] } : w))
            );
            setWatchlistFlash(`"${list.name}" listesine eklendi`);
            setShowWatchlistMenu(false);
            setTimeout(() => setWatchlistFlash(null), 2500);
        } catch (error) {
            console.error("Failed to add to watchlist:", error);
            setWatchlistFlash("Eklenemedi");
            setTimeout(() => setWatchlistFlash(null), 2500);
        } finally {
            setWatchlistBusy(false);
        }
    };

    const handleCreateAndAdd = async () => {
        if (!instrument || !keycloak) return;
        const name = newListName.trim();
        if (!name) return;
        try {
            setWatchlistBusy(true);
            const created = await watchlistApi.createWatchlist(keycloak, { name });
            await watchlistApi.addToWatchlist(keycloak, {
                watchlistId: created.id,
                symbol: instrument.symbol,
            });
            const withSymbol: Watchlist = { ...created, symbols: [instrument.symbol] };
            setWatchlists((prev) => [withSymbol, ...prev]);
            setCreatingList(false);
            setNewListName("");
            setShowWatchlistMenu(false);
            setWatchlistFlash(`"${name}" listesi oluşturuldu`);
            setTimeout(() => setWatchlistFlash(null), 2500);
        } catch (error) {
            console.error("Failed to create watchlist:", error);
            setWatchlistFlash("Liste oluşturulamadı");
            setTimeout(() => setWatchlistFlash(null), 2500);
        } finally {
            setWatchlistBusy(false);
        }
    };

    if (!instrument) return null;

    const positive = instrument.changePct >= 0;
    const color = positive ? "#4ade80" : "#f87171";

    console.log('[InstrumentChartModal] Rendering with instrument:', instrument.symbol, 'data points:', data.length);

    return (
        <Modal
            open={!!instrument}
            title={`${instrument.symbol} — ${instrument.name}`}
            onClose={onClose}
            maxWidth={900}
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

                {/* Add to Watchlist Button */}
                {keycloak?.authenticated && (
                    <div ref={watchlistMenuRef} style={s.watchlistWrap}>
                        <button
                            onClick={() => {
                                setShowWatchlistMenu((v) => !v);
                                ensureWatchlistsLoaded();
                            }}
                            style={s.watchlistButton}
                            title="Favori Listene Ekle"
                        >
                            ⭐ Listeme Ekle
                        </button>
                        {showWatchlistMenu && (
                            <div style={s.watchlistMenu}>
                                {!watchlistsLoaded ? (
                                    <div style={s.watchlistEmpty}>Yükleniyor...</div>
                                ) : watchlists.length === 0 && !creatingList ? (
                                    <div style={s.watchlistEmpty}>Henüz listen yok</div>
                                ) : (
                                    watchlists.map((list) => {
                                        const alreadyIn = list.symbols.includes(instrument.symbol);
                                        return (
                                            <button
                                                key={list.id}
                                                style={{
                                                    ...s.watchlistMenuItem,
                                                    opacity: watchlistBusy ? 0.6 : 1,
                                                }}
                                                disabled={watchlistBusy}
                                                onClick={() => handleAddToList(list)}
                                            >
                                                <span>{list.name}</span>
                                                <span style={s.watchlistMenuCount}>
                                                    {alreadyIn ? "✓" : `${list.symbols.length}`}
                                                </span>
                                            </button>
                                        );
                                    })
                                )}
                                {creatingList ? (
                                    <div style={s.watchlistCreateRow}>
                                        <input
                                            type="text"
                                            autoFocus
                                            placeholder="Liste adı"
                                            value={newListName}
                                            onChange={(e) => setNewListName(e.target.value)}
                                            onKeyDown={(e) => {
                                                if (e.key === "Enter") handleCreateAndAdd();
                                                if (e.key === "Escape") {
                                                    setCreatingList(false);
                                                    setNewListName("");
                                                }
                                            }}
                                            style={s.watchlistCreateInput}
                                        />
                                        <button
                                            onClick={handleCreateAndAdd}
                                            disabled={watchlistBusy || !newListName.trim()}
                                            style={s.watchlistCreateBtn}
                                        >
                                            Ekle
                                        </button>
                                    </div>
                                ) : (
                                    <button
                                        style={s.watchlistNewBtn}
                                        onClick={() => setCreatingList(true)}
                                    >
                                        + Yeni Liste Oluştur
                                    </button>
                                )}
                            </div>
                        )}
                        {watchlistFlash && (
                            <div style={s.watchlistFlash}>{watchlistFlash}</div>
                        )}
                    </div>
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
                    style={{
                        ...s.detailedChartBtn,
                        ...(keycloak?.authenticated ? {} : { opacity: 0.65, cursor: "pointer" }),
                    }}
                    onClick={() => {
                        if (!keycloak?.authenticated) {
                            if (keycloak) keycloak.login({ redirectUri: window.location.href });
                            return;
                        }
                        window.open(`/chart?symbol=${instrument.symbol}`, '_blank');
                    }}
                    title={keycloak?.authenticated ? "Detaylı grafik" : "Detaylı grafik için giriş yapın"}
                >
                    {keycloak?.authenticated ? "📊 Detaylı Grafik" : "🔒 Detaylı Grafik (Giriş gerekli)"}
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
                    onClick={() => {
                        if (!keycloak?.authenticated) {
                            if (keycloak) keycloak.login({ redirectUri: window.location.href });
                            return;
                        }
                        setActiveTab('analysis');
                    }}
                    title={keycloak?.authenticated ? "Teknik analiz göstergeleri" : "Teknik analiz için giriş yapın"}
                >
                    {keycloak?.authenticated ? "🔍 Teknik Analiz" : "🔒 Teknik Analiz"}
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
                        <LWAreaChart
                            data={data.map(d => ({ time: d.timestamp, value: d.close }))}
                            color={color}
                            height={300}
                        />
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
                <TechnicalAnalysisPanel symbol={instrument.symbol} period={period} />
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
        border: "1px solid var(--accent-border)",
        background: "var(--accent)",
        color: "var(--accent-solid)",
        fontSize: 14,
        fontWeight: 600,
        cursor: "pointer",
        transition: "all 0.2s",
        display: "flex",
        alignItems: "center",
        gap: 6,
    },
    watchlistWrap: {
        position: "relative" as const,
    },
    watchlistButton: {
        padding: "8px 16px",
        background: "transparent",
        color: "#facc15",
        border: "1px solid #facc15",
        borderRadius: 6,
        fontSize: 13,
        fontWeight: 600,
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        gap: 6,
        transition: "all 0.2s",
    },
    watchlistMenu: {
        position: "absolute" as const,
        top: "calc(100% + 6px)",
        right: 0,
        minWidth: 240,
        background: "var(--bg-panel)",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        boxShadow: "0 8px 24px rgba(0,0,0,0.4)",
        padding: 6,
        zIndex: 50,
        display: "flex",
        flexDirection: "column" as const,
        gap: 2,
    },
    watchlistEmpty: {
        padding: "10px 12px",
        fontSize: 12,
        color: "var(--text-muted)",
        textAlign: "center" as const,
    },
    watchlistMenuItem: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        padding: "8px 10px",
        background: "transparent",
        border: "none",
        borderRadius: 6,
        color: "var(--text-primary)",
        fontSize: 13,
        cursor: "pointer",
        textAlign: "left" as const,
    },
    watchlistMenuCount: {
        fontSize: 11,
        color: "var(--text-muted)",
        background: "var(--input-bg)",
        padding: "2px 8px",
        borderRadius: 999,
        minWidth: 20,
        textAlign: "center" as const,
    },
    watchlistNewBtn: {
        padding: "8px 10px",
        background: "transparent",
        border: "1px dashed var(--accent-border)",
        borderRadius: 6,
        color: "var(--accent-solid)",
        fontSize: 12,
        fontWeight: 600,
        cursor: "pointer",
        marginTop: 4,
    },
    watchlistCreateRow: {
        display: "flex",
        gap: 6,
        marginTop: 4,
    },
    watchlistCreateInput: {
        flex: 1,
        padding: "6px 8px",
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        border: "1px solid var(--input-border)",
        borderRadius: 6,
        fontSize: 12,
        outline: "none",
    },
    watchlistCreateBtn: {
        padding: "6px 12px",
        background: "var(--accent-solid)",
        color: "#000",
        border: "none",
        borderRadius: 6,
        fontSize: 12,
        fontWeight: 600,
        cursor: "pointer",
    },
    watchlistFlash: {
        position: "absolute" as const,
        top: "calc(100% + 6px)",
        right: 0,
        whiteSpace: "nowrap" as const,
        padding: "6px 10px",
        background: "var(--accent)",
        color: "var(--accent-solid)",
        border: "1px solid var(--accent-border)",
        borderRadius: 6,
        fontSize: 12,
        boxShadow: "0 4px 12px rgba(0,0,0,0.3)",
        zIndex: 49,
    },
};

import { useEffect, useRef, useState } from "react";
import { IconLock, IconTrendingDown, IconRadio } from "./common/icons";
import Modal from "./Modal";
import PriceAlertModal from "./PriceAlertModal";
import TechnicalAnalysisPanel from "./TechnicalAnalysisPanel";
import { LWAreaChart } from "./common/LWAreaChart";
import {
    getMarketHistory,
} from "../api/portfolioApi";
import { watchlistApi } from "../api/watchlistApi";
import { readHistoryCache, writeHistoryCache } from "../utils/historyCache";
import { useI18n } from "../contexts/I18nContext";

// Period değerleri sabit; etiketler render anında t() ile çevrilir.
const PERIODS = [
    { labelKey: "instrumentChart.p1G", value: "1D" },
    { labelKey: "instrumentChart.p5G", value: "5D" },
    { labelKey: "instrumentChart.p1A", value: "30D" },
    { labelKey: "instrumentChart.p1Y", value: "1Y" },
    { labelKey: "instrumentChart.p5Y", value: "5Y" },
];

const BellIcon = () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M18 16V11C18 7.69 15.31 5 12 5C8.69 5 6 7.69 6 11V16L4 18V19H20V18L18 16Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
        <path d="M10 21C10 22.1 10.9 23 12 23C13.1 23 14 22.1 14 21" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
);

const CompareIcon = () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="3" y="13" width="4" height="8" rx="1" stroke="currentColor" strokeWidth="2"/>
        <rect x="10" y="8" width="4" height="13" rx="1" stroke="currentColor" strokeWidth="2"/>
        <rect x="17" y="3" width="4" height="18" rx="1" stroke="currentColor" strokeWidth="2"/>
    </svg>
);

const StarIcon = () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M12 3L14.5 9.5L21 10L16 15L17.5 21.5L12 18L6.5 21.5L8 15L3 10L9.5 9.5L12 3Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
    </svg>
);

const ChartIcon = () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M3 17L9 11L13 15L21 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M16 7H21V12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
);

const AnalysisIcon = () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="11" cy="11" r="7" stroke="currentColor" strokeWidth="2"/>
        <path d="M16 16L21 21" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
);

const LockIcon = () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="5" y="11" width="14" height="10" rx="2" stroke="currentColor" strokeWidth="2"/>
        <path d="M8 11V7C8 4.79 9.79 3 12 3C14.21 3 16 4.79 16 7V11" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
);

export default function InstrumentChartModal({ instrument, onClose, keycloak, onAddToPortfolio, onCompare }) {
    const { t } = useI18n();
    const [period, setPeriod] = useState("30D");
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [showAlertModal, setShowAlertModal] = useState(false);
    const [activeTab, setActiveTab] = useState('chart');
    // "fresh" → fetched this session within the cache TTL
    // "stale" → showing cached data while a refetch is in flight, or backend is unreachable
    const [dataStale, setDataStale] = useState(false);

    // Watchlist state
    const [watchlists, setWatchlists] = useState([]);
    const [watchlistsLoaded, setWatchlistsLoaded] = useState(false);
    const [showWatchlistMenu, setShowWatchlistMenu] = useState(false);
    const [creatingList, setCreatingList] = useState(false);
    const [newListName, setNewListName] = useState("");
    const [watchlistBusy, setWatchlistBusy] = useState(false);
    const [watchlistFlash, setWatchlistFlash] = useState(null);
    const watchlistMenuRef = useRef(null);

    // Tema rengini CSS değişkeninden oku

    // Stale-while-revalidate pattern: cached data paints the chart immediately
    // (so opening the modal feels instant), then a background fetch refreshes it.
    // When the network call fails (offline, backend down), we keep the cached
    // copy visible instead of blanking out to "verisi bulunamadı".
    useEffect(() => {
        if (!instrument) return;
        let cancelled = false;

        const cached = readHistoryCache(instrument.symbol, period);
        if (cached) {
            // Paint instantly — even if it's slightly stale.
            setData(cached.data);
            setDataStale(!cached.fresh);
            // Fresh entry → skip the network round-trip entirely.
            if (cached.fresh) {
                setLoading(false);
                return () => { cancelled = true; };
            }
            // Stale entry → keep showing it while we refetch (no flicker, no
            // empty-state flash).
            setLoading(false);
        } else {
            // No cache → show the spinner, blank the chart.
            setLoading(true);
            setData([]);
            setDataStale(false);
        }

        getMarketHistory(instrument.symbol, period)
            .then((newData) => {
                if (cancelled) return;
                if (Array.isArray(newData) && newData.length > 0) {
                    setData(newData);
                    setDataStale(false);
                    writeHistoryCache(instrument.symbol, period, newData);
                } else if (!cached) {
                    // Empty response and we have nothing to fall back to.
                    setData([]);
                }
                // If response was empty BUT we had a cached copy, just keep it.
            })
            .catch((error) => {
                if (cancelled) return;
                // No user-controlled data in the log line (Sonar S4507).
                console.error("Failed to fetch chart data:", error);
                // Offline / backend down: leave cached data on screen, just
                // flag it as stale. Only blank out if there's no fallback.
                if (!cached) setData([]);
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });

        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
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
        const handler = (e) => {
            if (watchlistMenuRef.current && !watchlistMenuRef.current.contains(e.target)) {
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

    /**
     * Toggle membership: when the symbol is already in the list, clicking
     * the row removes it; otherwise it adds. One row, one click — the user
     * can both add and undo from the same popover instead of needing to
     * leave for the watchlist page to clean up.
     */
    const handleToggleList = async (list) => {
        if (!instrument || !keycloak) return;
        const alreadyIn = list.symbols.includes(instrument.symbol);
        try {
            setWatchlistBusy(true);
            if (alreadyIn) {
                await watchlistApi.removeFromWatchlist(keycloak, list.id, instrument.symbol);
                setWatchlists((prev) =>
                    prev.map((w) => (w.id === list.id
                        ? { ...w, symbols: w.symbols.filter((s) => s !== instrument.symbol) }
                        : w))
                );
                setWatchlistFlash(t("instrumentChart.removedFromList", { name: list.name }));
            } else {
                await watchlistApi.addToWatchlist(keycloak, {
                    watchlistId: list.id,
                    symbol: instrument.symbol,
                });
                setWatchlists((prev) =>
                    prev.map((w) => (w.id === list.id ? { ...w, symbols: [...w.symbols, instrument.symbol] } : w))
                );
                setWatchlistFlash(t("instrumentChart.addedToList", { name: list.name }));
            }
            setShowWatchlistMenu(false);
            setTimeout(() => setWatchlistFlash(null), 2500);
        } catch (error) {
            console.error("Failed to toggle watchlist membership:", error);
            setWatchlistFlash(alreadyIn ? t("instrumentChart.removeListError") : t("instrumentChart.addListError"));
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
            const withSymbol = { ...created, symbols: [instrument.symbol] };
            setWatchlists((prev) => [withSymbol, ...prev]);
            setCreatingList(false);
            setNewListName("");
            setShowWatchlistMenu(false);
            setWatchlistFlash(t("instrumentChart.listCreated", { name }));
            setTimeout(() => setWatchlistFlash(null), 2500);
        } catch (error) {
            console.error("Failed to create watchlist:", error);
            setWatchlistFlash(t("instrumentChart.listCreateError"));
            setTimeout(() => setWatchlistFlash(null), 2500);
        } finally {
            setWatchlistBusy(false);
        }
    };

    if (!instrument) return null;

    const positive = instrument.changePct >= 0;
    const color = positive ? "#4ade80" : "#f87171";

    const priceFmt = instrument.last != null
        ? Number(instrument.last).toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
        : "—";
    const changeAbsFmt = instrument.changeAbs != null
        ? `${positive ? "+" : ""}${Number(instrument.changeAbs).toFixed(2)}`
        : "—";
    const changePctFmt = instrument.changePct != null
        ? `${positive ? "+" : ""}${Number(instrument.changePct).toFixed(2)}%`
        : "—";
    const currencyPrefix = instrument.currency === "USD" ? "$" : instrument.currency === "EUR" ? "€" : "₺";

    return (
        <Modal
            open={!!instrument}
            title={`${instrument.symbol} — ${instrument.name}`}
            onClose={onClose}
            maxWidth={920}
        >
            {/* HERO — fiyat, değişim, tür ve aksiyon butonları */}
            <div style={s.hero}>
                <div style={s.heroLeft}>
                    <div style={s.priceLine}>
                        <span style={s.price}>{currencyPrefix}{priceFmt}</span>
                        <span style={{ ...s.changePill, ...(positive ? s.changePillUp : s.changePillDown) }}>
                            {positive ? "▲" : "▼"} {changePctFmt}
                        </span>
                    </div>
                    <div style={s.subline}>
                        <span style={s.typeBadge}>{instrument.type}</span>
                        <span style={s.changeAbs}>{changeAbsFmt}</span>
                    </div>
                </div>

                <div style={s.heroActions}>
                    {/* Alert */}
                    {keycloak?.authenticated && (
                        <button
                            onClick={() => setShowAlertModal(true)}
                            style={s.iconBtn}
                            title={t("instrumentChart.priceAlert")}
                            aria-label={t("instrumentChart.priceAlert")}
                        >
                            <BellIcon /> <span style={s.btnLabel}>{t("instrumentChart.alarm")}</span>
                        </button>
                    )}

                    {/* Compare */}
                    {onCompare && (
                        <button
                            onClick={() => onCompare(instrument)}
                            style={s.iconBtn}
                            title={t("instrumentChart.compare")}
                            aria-label={t("instrumentChart.compare")}
                        >
                            <CompareIcon /> <span style={s.btnLabel}>{t("instrumentChart.compare")}</span>
                        </button>
                    )}

                    {/* Watchlist */}
                    {keycloak?.authenticated && (
                        <div ref={watchlistMenuRef} style={s.watchlistWrap}>
                            <button
                                onClick={() => {
                                    setShowWatchlistMenu((v) => !v);
                                    ensureWatchlistsLoaded();
                                }}
                                style={s.iconBtn}
                                title={t("instrumentChart.addToList")}
                                aria-label={t("instrumentChart.addToList")}
                            >
                                <StarIcon /> <span style={s.btnLabel}>{t("instrumentChart.list")}</span>
                            </button>
                        {showWatchlistMenu && (
                            <div style={s.watchlistMenu}>
                                {!watchlistsLoaded ? (
                                    <div style={s.watchlistEmpty}>{t("common.loading")}</div>
                                ) : watchlists.length === 0 && !creatingList ? (
                                    <div style={s.watchlistEmpty}>{t("instrumentChart.noLists")}</div>
                                ) : (
                                    watchlists.map((list) => {
                                        const alreadyIn = list.symbols.includes(instrument.symbol);
                                        return (
                                            <button
                                                key={list.id}
                                                style={{
                                                    ...s.watchlistMenuItem,
                                                    ...(alreadyIn ? s.watchlistMenuItemActive : {}),
                                                    opacity: watchlistBusy ? 0.6 : 1,
                                                }}
                                                disabled={watchlistBusy}
                                                onClick={() => handleToggleList(list)}
                                                title={alreadyIn ? t("instrumentChart.removeFromList") : t("instrumentChart.addToListTooltip")}
                                            >
                                                <span>{list.name}</span>
                                                <span
                                                    style={{
                                                        ...s.watchlistMenuCount,
                                                        ...(alreadyIn ? s.watchlistMenuCountActive : {}),
                                                    }}
                                                >
                                                    {alreadyIn ? t("instrumentChart.remove") : `${list.symbols.length}`}
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
                                            placeholder={t("instrumentChart.newListPh")}
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
                                            {t("instrumentChart.add")}
                                        </button>
                                    </div>
                                ) : (
                                    <button
                                        style={s.watchlistNewBtn}
                                        onClick={() => setCreatingList(true)}
                                    >
                                        {t("instrumentChart.newListBtn")}
                                    </button>
                                )}
                            </div>
                        )}
                        {watchlistFlash && (
                            <div style={s.watchlistFlash}>{watchlistFlash}</div>
                        )}
                    </div>
                )}

                    {/* Add to Portfolio (primary action) */}
                    {keycloak?.authenticated && onAddToPortfolio && (
                        <button
                            onClick={() => onAddToPortfolio(instrument)}
                            style={s.primaryBtn}
                            title="Portföye Ekle"
                        >
                            + Portföye Ekle
                        </button>
                    )}
                </div>
            </div>

            {/* CONTROLS — period + detaylı grafik */}
            <div style={s.controls}>
                <div style={s.periodGroup} role="tablist" aria-label="Zaman aralığı">
                    {PERIODS.map((p) => (
                        <button
                            key={p.value}
                            role="tab"
                            aria-selected={period === p.value}
                            style={{
                                ...s.periodBtn,
                                ...(period === p.value ? s.periodActive : {}),
                            }}
                            onClick={() => setPeriod(p.value)}
                        >
                            {t(p.labelKey)}
                        </button>
                    ))}
                </div>
                <button
                    style={s.detailedChartBtn}
                    onClick={() => {
                        if (!keycloak?.authenticated) {
                            if (keycloak) keycloak.login({ redirectUri: window.location.href });
                            return;
                        }
                        window.open(`/chart?symbol=${instrument.symbol}`, '_blank');
                    }}
                    title={keycloak?.authenticated ? "Detaylı grafik" : "Detaylı grafik için giriş yapın"}
                >
                    {keycloak?.authenticated ? "Detaylı Grafik →" : <><IconLock size={13} style={{ verticalAlign: "-2px", marginRight: 4 }} />Detaylı Grafik</>}
                </button>
            </div>

            {/* TABS */}
            <div style={s.tabRow} role="tablist">
                <button
                    role="tab"
                    aria-selected={activeTab === 'chart'}
                    style={{
                        ...s.tabBtn,
                        ...(activeTab === 'chart' ? s.tabActive : {}),
                    }}
                    onClick={() => setActiveTab('chart')}
                >
                    <span style={s.tabIcon}><ChartIcon /></span> Grafik
                </button>
                <button
                    role="tab"
                    aria-selected={activeTab === 'analysis'}
                    style={{
                        ...s.tabBtn,
                        ...(activeTab === 'analysis' ? s.tabActive : {}),
                        ...(!keycloak?.authenticated ? s.tabLocked : {}),
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
                    <span style={s.tabIcon}>{keycloak?.authenticated ? <AnalysisIcon /> : <LockIcon />}</span> Teknik Analiz
                </button>
            </div>

            {/* CONTENT */}
            {activeTab === 'chart' ? (
                <div style={s.chartWrap}>
                    {loading ? (
                        <div style={s.loading}>
                            <div style={s.spinner} />
                            <span>Grafik yükleniyor...</span>
                        </div>
                    ) : data.length === 0 ? (
                        <div style={s.loading}>
                            <span><IconTrendingDown size={20} /></span>
                            <span>{instrument.symbol} için {period} verisi bulunamadı</span>
                        </div>
                    ) : (
                        <LWAreaChart
                            data={data.map(d => ({ time: d.timestamp, value: d.close }))}
                            color={color}
                            height={320}
                        />
                    )}

                    {data.length > 0 && (
                        <div style={s.footer}>
                            <span><strong>{data.length}</strong> veri noktası</span>
                            <span style={s.footerDot}>·</span>
                            <span>{data[0]?.label} → {data[data.length - 1]?.label}</span>
                            <span style={s.footerDot}>·</span>
                            <span>Son: <strong>{currencyPrefix}{Number(data[data.length - 1]?.close ?? 0).toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong></span>
                            {dataStale && (
                                <>
                                    <span style={s.footerDot}>·</span>
                                    <span
                                        style={{
                                            color: "var(--amber)",
                                            fontWeight: 600,
                                        }}
                                        title="Çevrimdışı veri — önbellekteki son görüntü gösteriliyor. Bağlantı geri gelince otomatik yenilenir."
                                    >
                                        <IconRadio size={13} style={{ verticalAlign: "-2px", marginRight: 4 }} />Önbellekten
                                    </span>
                                </>
                            )}
                        </div>
                    )}
                </div>
            ) : (
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

const s = {
    // ─────────── HERO ───────────
    hero: {
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "space-between",
        gap: 16,
        flexWrap: "wrap",
        paddingBottom: 16,
        marginBottom: 16,
        borderBottom: "1px solid var(--border-card)",
    },
    heroLeft: {
        display: "flex",
        flexDirection: "column",
        gap: 6,
        minWidth: 0,
    },
    priceLine: {
        display: "flex",
        alignItems: "baseline",
        gap: 12,
        flexWrap: "wrap",
    },
    price: {
        fontSize: 30,
        fontWeight: 800,
        color: "var(--text-primary)",
        letterSpacing: "-0.02em",
        lineHeight: 1,
        fontVariantNumeric: "tabular-nums",
    },
    changePill: {
        display: "inline-flex",
        alignItems: "center",
        gap: 4,
        padding: "4px 10px",
        borderRadius: 8,
        fontSize: 13,
        fontWeight: 700,
        fontVariantNumeric: "tabular-nums",
    },
    changePillUp: {
        background: "rgba(34, 197, 94, 0.14)",
        color: "var(--green)",
        border: "1px solid rgba(34, 197, 94, 0.30)",
    },
    changePillDown: {
        background: "var(--danger-bg)",
        color: "var(--red)",
        border: "1px solid rgba(239, 68, 68, 0.30)",
    },
    subline: {
        display: "flex",
        alignItems: "center",
        gap: 10,
        fontSize: 12,
    },
    typeBadge: {
        padding: "3px 9px",
        borderRadius: 999,
        border: "1px solid var(--border-card)",
        background: "var(--bg-panel)",
        color: "var(--text-muted)",
        fontSize: 11,
        fontWeight: 700,
        letterSpacing: 0.5,
    },
    changeAbs: {
        color: "var(--text-muted)",
        fontVariantNumeric: "tabular-nums",
    },
    heroActions: {
        display: "flex",
        gap: 8,
        flexWrap: "wrap",
        alignItems: "center",
    },

    // ─────────── ACTION BUTTONS ───────────
    iconBtn: {
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: "8px 12px",
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "var(--bg-panel)",
        color: "var(--text-primary)",
        fontSize: 13,
        fontWeight: 600,
        cursor: "pointer",
        transition: "all 0.15s",
    },
    btnLabel: { color: "var(--text-primary)" },
    primaryBtn: {
        padding: "9px 16px",
        borderRadius: 8,
        border: "1px solid var(--accent-solid)",
        background: "var(--accent-solid)",
        color: "#000",
        fontSize: 13,
        fontWeight: 700,
        cursor: "pointer",
        transition: "all 0.15s",
        whiteSpace: "nowrap",
    },

    // ─────────── CONTROLS (period + detail) ───────────
    controls: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: 12,
        flexWrap: "wrap",
        marginBottom: 12,
    },
    periodGroup: {
        display: "inline-flex",
        background: "var(--bg-panel)",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        padding: 3,
        gap: 2,
    },
    periodBtn: {
        padding: "6px 14px",
        borderRadius: 7,
        border: "1px solid transparent",
        background: "transparent",
        color: "var(--text-muted)",
        cursor: "pointer",
        fontSize: 12,
        fontWeight: 600,
        transition: "all 0.15s",
        fontVariantNumeric: "tabular-nums",
    },
    periodActive: {
        background: "var(--accent-solid)",
        color: "#000",
        border: "1px solid var(--accent-solid)",
    },
    detailedChartBtn: {
        padding: "8px 14px",
        borderRadius: 8,
        border: "1px solid var(--accent-border)",
        background: "var(--accent-hover-bg)",
        color: "var(--accent-solid)",
        fontSize: 12,
        fontWeight: 700,
        cursor: "pointer",
        transition: "all 0.15s",
    },

    // ─────────── TABS ───────────
    tabRow: {
        display: "flex",
        gap: 4,
        marginBottom: 12,
        borderBottom: "1px solid var(--border-card)",
    },
    tabBtn: {
        padding: "10px 16px",
        border: "none",
        borderBottom: "2px solid transparent",
        background: "transparent",
        color: "var(--text-muted)",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
        transition: "all 0.15s",
        marginBottom: -1,
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
    },
    tabIcon: {
        display: "inline-flex",
        alignItems: "center",
    },
    tabActive: {
        color: "var(--accent-solid)",
        borderBottom: "2px solid var(--accent-solid)",
    },
    tabLocked: { opacity: 0.7 },

    // ─────────── CHART + FOOTER ───────────
    chartWrap: {
        borderRadius: 12,
        border: "1px solid var(--border-card)",
        background: "var(--bg-panel)",
        padding: 12,
        position: "relative",
        overflow: "hidden",
    },
    loading: {
        height: 320,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 12,
        color: "var(--text-muted)",
        fontSize: 13,
    },
    spinner: {
        width: 28,
        height: 28,
        border: "3px solid var(--border-card)",
        borderTopColor: "var(--accent-solid)",
        borderRadius: "50%",
        animation: "spin 0.8s linear infinite",
    },
    footer: {
        marginTop: 10,
        padding: "8px 12px",
        fontSize: 11,
        color: "var(--text-muted)",
        background: "var(--bg-panel2, var(--bg-card))",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: 8,
        flexWrap: "wrap",
        fontVariantNumeric: "tabular-nums",
    },
    footerDot: { color: "var(--text-muted)", opacity: 0.5 },

    // ─────────── WATCHLIST POPOVER ───────────
    watchlistWrap: { position: "relative" },
    watchlistMenu: {
        position: "absolute",
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
        flexDirection: "column",
        gap: 2,
    },
    watchlistEmpty: {
        padding: "10px 12px",
        fontSize: 12,
        color: "var(--text-muted)",
        textAlign: "center",
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
        textAlign: "left",
    },
    watchlistMenuItemActive: {
        // Symbol already in list — soft accent fill hints the row is now a
        // remove-toggle rather than an add.
        background: "rgba(34, 197, 94, 0.10)",
    },
    watchlistMenuCount: {
        fontSize: 11,
        color: "var(--text-muted)",
        background: "var(--input-bg)",
        padding: "2px 8px",
        borderRadius: 999,
        minWidth: 20,
        textAlign: "center",
    },
    watchlistMenuCountActive: {
        color: "var(--danger-text, #f87171)",
        background: "rgba(239, 68, 68, 0.14)",
        fontWeight: 600,
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
        position: "absolute",
        top: "calc(100% + 6px)",
        right: 0,
        whiteSpace: "nowrap",
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

import { useEffect, useMemo, useState } from "react";
import { IconCheck, IconBarChart, IconX, IconSearch } from "./common/icons";
import {
    getMarketSummary, upsertPosition,
} from "../api/portfolioApi";
import Modal from "./Modal";
import PriceAlertModal from "./PriceAlertModal";
import InstrumentChartModal from "./InstrumentChartModal";
import CompareInstrumentsModal from "./CompareInstrumentsModal";
import { clickable } from "../utils/clickable";

const TYPE_LABELS = {
    STOCK: "Hisse", BIST: "BIST", CRYPTO: "Kripto",
    FX: "Doviz", COMMODITY: "Emtia", INDEX: "Endeks",
};

// Displayed as type filter labels
const FILTER_LABELS = {
    ALL: "Tumu", BIST: "BIST", COMMODITY: "Emtia",
    CRYPTO: "Kripto", FX: "Doviz", STOCK: "Hisse",
};

// Ticker bar: show these symbols prominently at top
const TICKER_SYMBOLS = ["XU100", "XU050", "XU030"];

// BIST endeks üyelikleri
const BIST30_STOCKS = [
    "THYAO", "GARAN", "AKBNK", "YKBNK", "ISCTR", "SISE", "KCHOL", "EREGL",
    "ASELS", "BIMAS", "SAHOL", "TUPRS", "PETKM", "KOZAL", "TAVHL", "ENKAI",
    "TCELL", "PGSUS", "SODA", "KOZAA", "TTKOM", "HEKTS", "ARCLK", "TOASO",
    "EKGYO", "VESTL", "FROTO", "KRDMD", "OYAKC", "AEFES"
];

const BIST50_STOCKS = [
    ...BIST30_STOCKS,
    "DOHOL", "GUBRF", "ODAS", "SOKM", "MAVI", "LOGO", "KONTR", "ALARK",
    "AGHOL", "BRSAN", "CCOLA", "DOAS", "GOZDE", "ISGYO", "KLMSN", "MGROS",
    "OTKAR", "SELEC", "ULKER", "VAKBN"
];

const BIST100_STOCKS = [
    ...BIST50_STOCKS,
    "AKENR", "AKSA", "AKSEN", "ALGYO", "ANACM", "ANSGR", "ASUZU", "AYDEM",
    "BAGFS", "BERA", "BIOEN", "BIZIM", "BOBET", "BRISA", "BRYAT", "BUCIM",
    "CANTE", "CEMTS", "CIMSA", "CLEBI", "CRFSA", "CVKMD", "DEVA", "DGNMO",
    "EGEEN", "EGGUB", "ENJSA", "ERBOS", "ESEN", "FENER", "GENIL", "GESAN",
    "GLYHO", "GOODY", "GSDHO", "HALKB", "INDES", "IPEKE", "ISMEN", "IZMDC",
    "KARSN", "KARTN", "KENT", "KERVT", "KLRHO", "KMPUR", "KONYA", "KORDS"
];

export default function MarketBrowser({ keycloak, onAdded }) {
    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState("ALL");
    const [indexFilter, setIndexFilter] = useState(null); // BIST30, BIST50, BIST100
    const [search, setSearch] = useState("");
    const [err, setErr] = useState(null);
    const [selected, setSelected] = useState(null);
    const [addTarget, setAddTarget] = useState(null);
    const [addQty, setAddQty] = useState(1);
    const [addSaving, setAddSaving] = useState(false);
    const [addErr, setAddErr] = useState(null);
    const [alertTarget, setAlertTarget] = useState(null);
    const [compareTarget, setCompareTarget] = useState(null);

    useEffect(() => {
        getMarketSummary()
            .then((data) => {
                setItems(data);
            })
            .catch((e) => setErr(e?.message ?? "Fetch error"))
            .finally(() => setLoading(false));
    }, []);

    const tickerItems = useMemo(
        () => TICKER_SYMBOLS.map((sym) => items.find((i) => i.symbol === sym)).filter(Boolean),
        [items]
    );

    const types = useMemo(
        () => ["ALL", ...Array.from(new Set(items.filter((i) => i.type !== "INDEX").map((i) => i.type))).sort((a, b) => a.localeCompare(b))],
        [items]
    );

    const filtered = useMemo(() => {
        let list = items.filter((i) => i.type !== "INDEX");

        // Index filter (BIST30, BIST50, BIST100)
        if (indexFilter === "XU030") {
            list = list.filter((i) => BIST30_STOCKS.includes(i.symbol));
        } else if (indexFilter === "XU050") {
            list = list.filter((i) => BIST50_STOCKS.includes(i.symbol));
        } else if (indexFilter === "XU100") {
            list = list.filter((i) => BIST100_STOCKS.includes(i.symbol));
        }

        // Type filter
        if (filter !== "ALL") list = list.filter((i) => i.type === filter);

        // Search filter
        if (search.trim()) {
            const q = search.trim().toUpperCase();
            list = list.filter((i) => i.symbol.includes(q) || i.name.toUpperCase().includes(q));
        }

        // Shuffle the list randomly
        list = list.sort(() => Math.random() - 0.5);

        return list;
    }, [items, filter, indexFilter, search]);

    const addTotal = useMemo(() => {
        if (!addTarget || !addQty || addQty <= 0) return 0;
        return Number((addTarget.last * addQty).toFixed(4));
    }, [addTarget, addQty]);

    async function onConfirmAdd() {
        if (!addTarget) return;
        if (!addQty || addQty <= 0) return setAddErr("Adet 1 veya daha buyuk olmali");
        try {
            setAddSaving(true); setAddErr(null);
            await upsertPosition(keycloak, { symbol: addTarget.symbol, quantity: addQty, avgCost: addTarget.last });
            setAddTarget(null);
            onAdded();
        } catch (e) { setAddErr(e?.message ?? "Add error"); }
        finally { setAddSaving(false); }
    }

    return (
        <div style={s.root}>
            {/* ── Ticker bar ── */}
            {tickerItems.length > 0 && (
                <div style={s.tickerBar}>
                    {tickerItems.map((idx) => {
                        const pos = idx.changePct >= 0;
                        const clr = pos ? "var(--green)" : "var(--red)";
                        const isActive = indexFilter === idx.symbol;
                        return (
                            <div
                                key={idx.symbol}
                                style={{
                                    ...s.tickerCard,
                                    ...(isActive ? s.tickerCardActive : {})
                                }}
                                {...clickable(() => {
                                    // Toggle index filter
                                    if (indexFilter === idx.symbol) {
                                        setIndexFilter(null);
                                    } else {
                                        setIndexFilter(idx.symbol);
                                        setFilter("BIST"); // Auto-select BIST filter
                                    }
                                })}
                            >
                                <div style={s.tickerLabel}>{idx.symbol}</div>
                                <div style={s.tickerPrice}>{idx.last?.toLocaleString("tr-TR")}</div>
                                <div style={{ color: clr, fontSize: 11, fontWeight: 600, marginTop: 2 }}>
                                    {pos ? "▲" : "▼"} {pos ? "+" : ""}{idx.changePct?.toFixed(2)}%
                                    &nbsp;({pos ? "+" : ""}{idx.changeAbs?.toFixed(2)})
                                </div>
                                {isActive && (
                                    <div style={s.activeIndicator}>
                                        <IconCheck size={12} style={{ verticalAlign: "-2px", marginRight: 4 }} />Filtrelendi
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}

            {err && <div style={s.error}>{err}</div>}

            {/* ── Main content ── */}
            <div style={s.mainContent}>
                {indexFilter && (
                    <div style={s.filterBanner}>
                        <span>
                            <IconBarChart size={13} style={{ verticalAlign: "-2px", marginRight: 6 }} />{indexFilter === "XU030" ? "BIST 30" : indexFilter === "XU050" ? "BIST 50" : "BIST 100"} hisseleri gösteriliyor
                        </span>
                        <button
                            style={s.clearFilterBtn}
                            onClick={() => setIndexFilter(null)}
                        >
                            <IconX size={12} style={{ verticalAlign: "-2px", marginRight: 4 }} />Filtreyi Kaldır
                        </button>
                    </div>
                )}
                <div style={s.searchWrap}>
                    <span style={{ display: "inline-flex", color: "var(--text-muted)" }}><IconSearch size={13} /></span>
                    <input
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder="Hisse ara..."
                        style={s.searchInput}
                    />
                </div>
                <div style={s.filterRow}>
                    {types.map((t) => (
                        <button
                            key={t}
                            style={{ ...s.filterBtn, ...(filter === t ? s.filterActive : {}) }}
                            onClick={() => setFilter(t)}
                        >
                            {FILTER_LABELS[t] ?? t}
                        </button>
                    ))}
                </div>

                {loading ? (
                    <div style={s.empty}>Yukleniyor...</div>
                ) : filtered.length === 0 ? (
                    <div style={s.empty}>Sonuc yok.</div>
                ) : (
                    <div style={s.cardGrid}>
                        {filtered.map((item) => {
                            const pos = item.changePct >= 0;
                            const clr = pos ? "var(--green)" : "var(--red)";
                            return (
                                <div
                                    key={item.symbol}
                                    className="market-card"
                                    style={s.card}
                                    {...clickable(() => setSelected(item))}
                                >
                                    <div style={s.cardTop}>
                                        <div>
                                            <div style={s.cardSymbol}>{item.symbol}</div>
                                            <div style={s.cardName}>{item.name}</div>
                                        </div>
                                        <div style={{ color: clr, fontSize: 11, fontWeight: 600, textAlign: "right" }}>
                                            {pos ? "▲" : "▼"} {pos ? "+" : ""}{item.changePct?.toFixed(2)}%
                                        </div>
                                    </div>
                                    <div style={s.cardPrice}>
                                        {item.last?.toLocaleString("tr-TR")}
                                        <span style={{ color: clr, fontSize: 12, marginLeft: 6, fontWeight: 500 }}>
                                            {pos ? "+" : ""}{item.changeAbs?.toFixed(2)}
                                        </span>
                                    </div>
                                    <MiniSparkline positive={pos} />
                                    <div style={s.cardFooter}>
                                        <span style={s.metaText}>
                                            {item.asOf ? new Date(item.asOf).toLocaleDateString("tr-TR") : ""}
                                        </span>
                                        <span style={s.typePill}>{TYPE_LABELS[item.type] ?? item.type}</span>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>

            {/* Instrument Chart Modal */}
            <InstrumentChartModal
                instrument={selected}
                onClose={() => setSelected(null)}
                keycloak={keycloak}
                onAddToPortfolio={(instrument) => {
                    setAddTarget(instrument);
                    setAddQty(1);
                    setAddErr(null);
                    setSelected(null); // Close chart modal
                }}
                onCompare={(instrument) => {
                    setCompareTarget(instrument);
                    setSelected(null); // Close chart modal
                }}
            />

            {/* Compare Instruments Modal */}
            <CompareInstrumentsModal
                baseInstrument={compareTarget}
                onClose={() => setCompareTarget(null)}
            />

            {/* Add modal */}
            <Modal
                open={!!addTarget}
                title={"Portfolye Ekle - " + (addTarget?.symbol ?? "")}
                onClose={() => setAddTarget(null)}
                footer={
                    <>
                        <button style={s.ghostBtn} onClick={() => setAddTarget(null)} disabled={addSaving}>Vazgec</button>
                        <button style={s.primaryBtn} onClick={onConfirmAdd} disabled={addSaving}>
                            {addSaving ? "Ekleniyor..." : "Portfolye Ekle"}
                        </button>
                    </>
                }
            >
                {addTarget && (
                    <div style={{ display: "grid", gap: 14 }}>
                        <div style={s.infoBox}>
                            <div style={s.infoRow}>
                                <span style={{ color: "var(--text-muted)", fontSize: 13 }}>{addTarget.name}</span>
                                <span style={s.badge}>{TYPE_LABELS[addTarget.type] ?? addTarget.type}</span>
                            </div>
                            <div style={s.infoRow}>
                                <span style={{ color: "var(--text-muted)", fontSize: 13 }}>Guncel Fiyat</span>
                                <span style={{ color: "var(--text-primary)", fontWeight: 700, fontSize: 18 }}>
                                    {addTarget.last?.toLocaleString("tr-TR")}
                                </span>
                            </div>
                        </div>
                        <div style={{ display: "grid", gap: 6 }}>
                            <div style={{ fontSize: 12, color: "var(--text-muted)" }}>Adet (Lot)</div>
                            <input type="number" value={addQty} min={1} onChange={(e) => setAddQty(Number(e.target.value))} style={s.input} autoFocus />
                        </div>
                        <div style={s.infoBox}>
                            <div style={s.infoRow}>
                                <span style={{ color: "var(--text-muted)", fontSize: 13 }}>Toplam Tutar</span>
                                <span style={{ color: "var(--text-primary)", fontWeight: 700, fontSize: 16 }}>
                                    {addTotal > 0 ? addTotal.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}
                                </span>
                            </div>
                        </div>
                        {addErr && <div style={{ color: "var(--danger-text)", fontSize: 13 }}>{addErr}</div>}
                    </div>
                )}
            </Modal>

            {/* Price Alert Modal */}
            {alertTarget && (
                <PriceAlertModal
                    open={!!alertTarget}
                    onClose={() => setAlertTarget(null)}
                    keycloak={keycloak}
                    prefilledSymbol={alertTarget.symbol}
                    prefilledPrice={alertTarget.last}
                />
            )}
        </div>
    );
}

function MiniSparkline({ positive }) {
    const color = positive ? "#3fb950" : "#f85149";
    const pts = positive
        ? "0,28 12,22 25,24 38,18 50,15 62,12 75,14 88,8 100,5"
        : "0,5 12,10 25,8 38,14 50,17 62,20 75,18 88,24 100,27";
    return (
        <svg width="100%" height="30" viewBox="0 0 100 30" preserveAspectRatio="none" style={{ display: "block", marginTop: 8 }}>
            <defs>
                <linearGradient id={"sg" + positive} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={color} stopOpacity="0.25" />
                    <stop offset="100%" stopColor={color} stopOpacity="0" />
                </linearGradient>
            </defs>
            <polyline points={pts} fill="none" stroke={color} strokeWidth="1.5" />
            <polygon points={"0,30 " + pts + " 100,30"} fill={"url(#sg" + positive + ")"} />
        </svg>
    );
}

const s = {
    root: { display: "flex", flexDirection: "column", gap: 16 },

    tickerBar: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fill, minmax(150px, 1fr))",
        gap: 10,
    },
    tickerCard: {
        borderRadius: 10,
        border: "1px solid var(--border-card)",
        background: "var(--bg-card)",
        padding: "12px 14px",
        cursor: "pointer",
        transition: "all 0.2s ease",
        position: "relative",
    },
    tickerCardActive: {
        border: "2px solid #22c55e",
        background: "rgba(34, 197, 94, 0.1)",
        transform: "scale(1.02)",
    },
    activeIndicator: {
        marginTop: 6,
        fontSize: 10,
        color: "#22c55e",
        fontWeight: 600,
        display: "flex",
        alignItems: "center",
        gap: 4,
    },
    tickerLabel: { fontSize: 11, color: "var(--text-muted)", marginBottom: 4, fontWeight: 500 },
    tickerPrice: { fontSize: 17, fontWeight: 700, color: "var(--text-primary)" },

    error: { color: "var(--danger-text)", fontSize: 13 },

    mainContent: { display: "flex", flexDirection: "column", gap: 10 },

    filterBanner: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        padding: "10px 14px",
        borderRadius: 8,
        background: "rgba(34, 197, 94, 0.1)",
        border: "1px solid #22c55e",
        fontSize: 13,
        fontWeight: 500,
        color: "var(--text-primary)",
    },
    clearFilterBtn: {
        padding: "4px 10px",
        borderRadius: 6,
        border: "1px solid #22c55e",
        background: "transparent",
        color: "#22c55e",
        cursor: "pointer",
        fontSize: 12,
        fontWeight: 600,
        transition: "all 0.2s",
    },

    searchWrap: {
        display: "flex", alignItems: "center", gap: 8,
        padding: "8px 12px", borderRadius: 8,
        border: "1px solid var(--input-border)", background: "var(--input-bg)",
    },
    searchInput: {
        flex: 1, background: "transparent", border: "none", outline: "none",
        color: "var(--text-primary)", fontSize: 13,
    },

    filterRow: { display: "flex", gap: 6, flexWrap: "wrap" },
    filterBtn: {
        padding: "5px 12px", borderRadius: 6,
        border: "1px solid var(--border-card)", background: "transparent",
        color: "var(--text-muted)", cursor: "pointer", fontSize: 12, fontWeight: 500,
    },
    filterActive: {
        border: "1px solid var(--accent-solid)",
        background: "rgba(37,99,235,0.15)",
        color: "var(--text-primary)",
    },

    empty: { color: "var(--text-muted)", fontSize: 13, padding: "20px 0", textAlign: "center" },

    cardGrid: { display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: 12 },
    card: {
        borderRadius: 10, border: "1px solid var(--border-card)",
        background: "var(--bg-card)", padding: "12px 14px", cursor: "pointer",
        transition: "all 0.2s ease",
    },
    cardTop: { display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 4, marginBottom: 4 },
    cardSymbol: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    cardName: { fontSize: 10, color: "var(--text-muted)", marginTop: 1 },
    cardPrice: { fontSize: 17, fontWeight: 700, color: "var(--text-primary)" },
    cardFooter: { display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 6 },
    metaText: { fontSize: 10, color: "var(--text-muted)" },
    typePill: {
        fontSize: 10, padding: "2px 6px", borderRadius: 4,
        background: "var(--input-bg)", color: "var(--text-muted)",
        border: "1px solid var(--border-soft)",
    },

    infoBox: {
        borderRadius: 8, border: "1px solid var(--border-card)",
        background: "var(--bg-panel)", padding: "10px 12px", display: "grid", gap: 8,
    },
    infoRow: { display: "flex", justifyContent: "space-between", alignItems: "center" },
    badge: {
        padding: "2px 8px", borderRadius: 4,
        background: "rgba(37,99,235,0.15)", border: "1px solid var(--accent-border)",
        color: "var(--text-primary)", fontSize: 11,
    },
    input: {
        padding: "9px 12px", borderRadius: 8,
        border: "1px solid var(--input-border)", background: "var(--input-bg)",
        color: "var(--text-primary)", outline: "none", width: "100%", boxSizing: "border-box", fontSize: 14,
    },
    primaryBtn: {
        padding: "9px 16px", borderRadius: 8,
        border: "none", background: "var(--accent-solid)",
        color: "#fff", cursor: "pointer", fontWeight: 600,
    },
    ghostBtn: {
        padding: "9px 16px", borderRadius: 8,
        border: "1px solid var(--border-card)", background: "transparent",
        color: "var(--text-primary)", cursor: "pointer",
    },
};

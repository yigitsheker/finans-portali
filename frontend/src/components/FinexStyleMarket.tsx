import type Keycloak from "keycloak-js";
import { useEffect, useMemo, useState } from "react";
import {
    getMarketSummary,
    getMarketHistory,
    upsertPosition,
    type MarketSummaryItem,
} from "../api/portfolioApi";
import Modal from "./Modal";
import InstrumentChartModal from "./InstrumentChartModal";
import CompareInstrumentsModal from "./CompareInstrumentsModal";
import { LWSparkline, type SparklinePoint } from "./common/LWSparkline";

type Props = { keycloak: Keycloak; onAdded: () => void };

export default function FinexStyleMarket({ keycloak, onAdded }: Props) {
    const [items, setItems] = useState<MarketSummaryItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState<string>("ALL");
    const [search, setSearch] = useState("");
    const [err, setErr] = useState<string | null>(null);
    const [selected, setSelected] = useState<MarketSummaryItem | null>(null);
    const [addTarget, setAddTarget] = useState<MarketSummaryItem | null>(null);
    const [addQty, setAddQty] = useState<number>(1);
    const [addSaving, setAddSaving] = useState(false);
    const [addErr, setAddErr] = useState<string | null>(null);
    const [compareTarget, setCompareTarget] = useState<MarketSummaryItem | null>(null);

    // Sparkline data: symbol → last 30 daily closes
    const [sparklines, setSparklines] = useState<Record<string, SparklinePoint[]>>({});

    useEffect(() => {
        getMarketSummary()
            .then((data) => {
                setItems(data);
            })
            .catch((e) => setErr(e?.message ?? "Fetch error"))
            .finally(() => setLoading(false));
    }, []);

    const indices = useMemo(
        () => items.filter((i) => i.type === "INDEX"),
        [items]
    );

    const types = useMemo(
        () => ["ALL", ...Array.from(new Set(items.filter((i) => i.type !== "INDEX").map((i) => i.type))).sort()],
        [items]
    );

    const filtered = useMemo(() => {
        let list = items.filter((i) => i.type !== "INDEX");
        if (filter !== "ALL") list = list.filter((i) => i.type === filter);
        if (search.trim()) {
            const q = search.trim().toUpperCase();
            list = list.filter((i) => i.symbol.includes(q) || i.name.toUpperCase().includes(q));
        }
        return list;
    }, [items, filter, search]);

    // Fetch sparkline history for visible items (batched, low priority)
    useEffect(() => {
        if (filtered.length === 0) return;
        let cancelled = false;

        const fetchBatch = async () => {
            for (const item of filtered.slice(0, 40)) {
                if (cancelled) break;
                if (sparklines[item.symbol]) continue; // already loaded
                try {
                    const history = await getMarketHistory(item.symbol, "1M");
                    if (!cancelled && history.length > 0) {
                        const pts: SparklinePoint[] = history.map((h) => ({
                            time: h.day.split("T")[0],
                            value: h.close,
                        }));
                        setSparklines((prev) => ({ ...prev, [item.symbol]: pts }));
                    }
                } catch {
                    // silently skip — sparkline is optional
                }
                await new Promise((r) => setTimeout(r, 80));
            }
        };

        fetchBatch();
        return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [filtered.length, filter]);

    const addTotal = useMemo(() => {
        if (!addTarget || !addQty || addQty <= 0) return 0;
        return Number((addTarget.last * addQty).toFixed(4));
    }, [addTarget, addQty]);

    async function onConfirmAdd() {
        if (!addTarget) return;
        if (!addQty || addQty <= 0) return setAddErr("Adet 1 veya daha büyük olmalı");
        try {
            setAddSaving(true);
            setAddErr(null);
            await upsertPosition(keycloak, {
                symbol: addTarget.symbol,
                quantity: addQty,
                avgCost: addTarget.last,
            });
            setAddTarget(null);
            onAdded();
        } catch (e: any) {
            setAddErr(e?.message ?? "Add error");
        } finally {
            setAddSaving(false);
        }
    }

    if (loading) {
        return (
            <div style={s.loading}>
                <div style={s.spinner}></div>
                <div style={{ color: "var(--text-muted)", marginTop: 12 }}>Yükleniyor...</div>
            </div>
        );
    }

    if (err) {
        return (
            <div style={s.error}>
                <div style={{ fontSize: 48, marginBottom: 16 }}>⚠️</div>
                <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>Bir Hata Oluştu</div>
                <div style={{ color: "var(--text-muted)", fontSize: 13 }}>{err}</div>
            </div>
        );
    }

    return (
        <div style={s.root}>
            {/* Index Cards */}
            {indices.length > 0 && (
                <div style={s.indexGrid}>
                    {indices.map((idx) => {
                        const pos = idx.changePct >= 0;
                        const color = pos ? "#10b981" : "#ef4444";
                        return (
                            <div key={idx.symbol} style={s.indexCard}>
                                <div style={s.indexLabel}>{idx.symbol}</div>
                                <div style={s.indexPrice}>
                                    {idx.last?.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </div>
                                <div style={{ color, fontSize: 12, fontWeight: 600, marginTop: 4 }}>
                                    {pos ? "▲" : "▼"} {pos ? "+" : ""}
                                    {idx.changePct?.toFixed(2)}%
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}

            {/* Search and Filter */}
            <div style={s.controls}>
                <div style={s.searchBox}>
                    <span style={{ fontSize: 14, color: "var(--text-muted)" }}>🔍</span>
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
                            style={{
                                ...s.filterBtn,
                                ...(filter === t ? s.filterActive : {}),
                            }}
                            onClick={() => setFilter(t)}
                        >
                            {t === "ALL" ? "Tümü" : t}
                        </button>
                    ))}
                </div>
            </div>

            {/* Main Layout: Full Width List */}
            <div style={s.mainLayout}>
                <div style={s.tableContainer}>
                    {/* Table Header */}
                    <div style={s.tableHeader}>
                        <div style={s.colHisse}>Hisse</div>
                        <div style={s.colFiyat}>Fiyat</div>
                        <div style={s.colDegisim}>Değişim</div>
                        <div style={s.colHacim}>Hacim</div>
                        <div style={s.colGrafik}>Mini Grafik</div>
                        <div style={s.colIslem}>İşlemler</div>
                    </div>

                    {/* Table Body */}
                    <div style={s.tableBody}>
                        {filtered.map((item) => {
                            const pos = item.changePct >= 0;
                            const color = pos ? "#10b981" : "#ef4444";
                            return (
                                <div
                                    key={item.symbol}
                                    style={s.tableRow}
                                    onClick={() => setSelected(item)}
                                >
                                    <div style={s.colHisse}>
                                        <div style={s.stockSymbol}>{item.symbol}</div>
                                        <div style={s.stockName}>{item.name}</div>
                                    </div>
                                    <div style={s.colFiyat}>
                                        <div style={s.stockPrice}>
                                            ₺{item.last?.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                        </div>
                                    </div>
                                    <div style={s.colDegisim}>
                                        <div style={{ color, fontSize: 13, fontWeight: 600 }}>
                                            {pos ? "▲" : "▼"} {pos ? "+" : ""}
                                            {item.changePct?.toFixed(2)}%
                                        </div>
                                    </div>
                                    <div style={s.colHacim}>
                                        <div style={{ fontSize: 12, color: "var(--text-muted)" }}>
                                            {(Math.random() * 100).toFixed(1)}M
                                        </div>
                                    </div>
                                    <div style={s.colGrafik}>
                                        <LWSparkline
                                            data={sparklines[item.symbol] ?? []}
                                            positive={pos}
                                            width={100}
                                            height={36}
                                        />
                                    </div>
                                    <div style={s.colIslem}>
                                        <button
                                            style={s.actionBtn}
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                setAddTarget(item);
                                                setAddQty(1);
                                                setAddErr(null);
                                            }}
                                        >
                                            Al/Sat
                                        </button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            </div>

            {/* Modals */}
            <InstrumentChartModal
                instrument={selected}
                onClose={() => setSelected(null)}
                keycloak={keycloak}
                onAddToPortfolio={(instrument) => {
                    setAddTarget(instrument);
                    setAddQty(1);
                    setAddErr(null);
                    setSelected(null);
                }}
                onCompare={(instrument) => {
                    setCompareTarget(instrument);
                    setSelected(null);
                }}
            />

            <CompareInstrumentsModal
                baseInstrument={compareTarget}
                onClose={() => setCompareTarget(null)}
            />

            <Modal
                open={!!addTarget}
                title={"İşlem Yap - " + (addTarget?.symbol ?? "")}
                onClose={() => setAddTarget(null)}
                footer={
                    <>
                        <button style={s.ghostBtn} onClick={() => setAddTarget(null)} disabled={addSaving}>
                            Vazgeç
                        </button>
                        <button style={s.primaryBtn} onClick={onConfirmAdd} disabled={addSaving}>
                            {addSaving ? "İşleniyor..." : "Onayla"}
                        </button>
                    </>
                }
            >
                {addTarget && (
                    <div style={{ display: "grid", gap: 14 }}>
                        <div style={s.infoBox}>
                            <div style={s.infoRow}>
                                <span style={{ color: "var(--text-muted)", fontSize: 13 }}>Güncel Fiyat</span>
                                <span style={{ color: "var(--text-primary)", fontWeight: 700, fontSize: 18 }}>
                                    ₺{addTarget.last?.toLocaleString("tr-TR")}
                                </span>
                            </div>
                        </div>
                        <div style={{ display: "grid", gap: 6 }}>
                            <div style={{ fontSize: 12, color: "var(--text-muted)" }}>Adet</div>
                            <input
                                type="number"
                                value={addQty}
                                min={1}
                                onChange={(e) => setAddQty(Number(e.target.value))}
                                style={s.input}
                                autoFocus
                            />
                        </div>
                        <div style={s.infoBox}>
                            <div style={s.infoRow}>
                                <span style={{ color: "var(--text-muted)", fontSize: 13 }}>Tahmini Tutar</span>
                                <span style={{ color: "var(--text-primary)", fontWeight: 700, fontSize: 16 }}>
                                    ₺{addTotal > 0 ? addTotal.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}
                                </span>
                            </div>
                        </div>
                        {addErr && <div style={{ color: "#ef4444", fontSize: 13 }}>{addErr}</div>}
                    </div>
                )}
            </Modal>
        </div>
    );
}

const s: Record<string, React.CSSProperties> = {
    root: { display: "flex", flexDirection: "column", gap: 16 },
    loading: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: 400,
    },
    spinner: {
        width: 40,
        height: 40,
        border: "3px solid var(--border)",
        borderTop: "3px solid #3b82f6",
        borderRadius: "50%",
        animation: "spin 0.8s linear infinite",
    },
    error: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: 400,
        color: "var(--text-primary)",
    },
    indexGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))",
        gap: 12,
    },
    indexCard: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        padding: "12px 14px",
    },
    indexLabel: { fontSize: 11, color: "var(--text-muted)", marginBottom: 4, fontWeight: 500 },
    indexPrice: { fontSize: 18, fontWeight: 700, color: "var(--text-primary)", marginBottom: 2 },
    controls: {
        display: "flex",
        gap: 12,
        alignItems: "center",
    },
    searchBox_old: {
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "10px 14px",
        background: "var(--input-bg)",
        border: "1px solid var(--input-border)",
        borderRadius: 8,
        flex: 1,
        maxWidth: 400,
    },
    searchInput_old: {
        flex: 1,
        background: "transparent",
        border: "none",
        outline: "none",
        color: "var(--text-primary)",
        fontSize: 13,
    },
    filterRow_old: { display: "flex", gap: 6, flexWrap: "wrap" } as React.CSSProperties,
    filterBtn_old: {
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
    filterActive_old: {
        border: "1px solid #10b981",
        background: "rgba(16, 185, 129, 0.15)",
        color: "#10b981",
    },
    mainLayout: {
        display: "flex",
        flexDirection: "column",
        gap: 12,
    },
    tableContainer: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        overflow: "hidden",
    },
    tableHeader: {
        display: "grid",
        gridTemplateColumns: "2fr 1fr 1fr 1fr 1.5fr auto",
        gap: 16,
        padding: "14px 20px",
        background: "var(--bg-panel)",
        borderBottom: "1px solid var(--border-card)",
        fontSize: 11,
        fontWeight: 600,
        color: "var(--text-muted)",
        textTransform: "uppercase",
    },
    tableBody: {
        display: "flex",
        flexDirection: "column",
    },
    tableRow: {
        display: "grid",
        gridTemplateColumns: "2fr 1fr 1fr 1fr 1.5fr auto",
        gap: 16,
        padding: "14px 20px",
        borderBottom: "1px solid var(--border-card)",
        cursor: "pointer",
        transition: "background 0.2s",
    },
    colHisse: { display: "flex", flexDirection: "column", gap: 2 },
    colFiyat: { display: "flex", alignItems: "center" },
    colDegisim: { display: "flex", alignItems: "center" },
    colHacim: { display: "flex", alignItems: "center" },
    colGrafik: { display: "flex", alignItems: "center", justifyContent: "center" },
    colIslem: { display: "flex", alignItems: "center", justifyContent: "flex-end" },
    actionBtn: {
        padding: "8px 20px",
        borderRadius: 6,
        border: "none",
        background: "#10b981",
        color: "#000",
        fontSize: 13,
        fontWeight: 700,
        cursor: "pointer",
        transition: "all 0.2s",
        boxShadow: "0 2px 6px rgba(16, 185, 129, 0.3)",
    },
    searchBox: {
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "10px 14px",
        background: "var(--input-bg)",
        border: "1px solid var(--input-border)",
        borderRadius: 8,
    },
    searchInput: {
        flex: 1,
        background: "transparent",
        border: "none",
        outline: "none",
        color: "var(--text-primary)",
        fontSize: 13,
    },
    filterRow: { display: "flex", gap: 6, flexWrap: "wrap" } as React.CSSProperties,
    filterBtn: {
        padding: "5px 12px",
        borderRadius: 6,
        border: "1px solid var(--border-card)",
        background: "transparent",
        color: "var(--text-muted)",
        cursor: "pointer",
        fontSize: 11,
        fontWeight: 500,
        transition: "all 0.2s",
    },
    filterActive: {
        border: "1px solid #3b82f6",
        background: "rgba(59, 130, 246, 0.15)",
        color: "var(--text-primary)",
    },
    stockList: {
        display: "flex",
        flexDirection: "column",
        gap: 1,
        maxHeight: 600,
        overflowY: "auto",
    },
    stockItem: {
        display: "grid",
        gridTemplateColumns: "1fr auto auto",
        alignItems: "center",
        gap: 12,
        padding: "10px 12px",
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 6,
        cursor: "pointer",
        transition: "all 0.2s",
    },
    stockItemActive: {
        border: "1px solid #3b82f6",
        background: "rgba(59, 130, 246, 0.1)",
    },
    stockLeft: { display: "flex", flexDirection: "column", gap: 2 },
    stockSymbol: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    stockName: { fontSize: 10, color: "var(--text-muted)" },
    stockRight: { display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 2 },
    stockPrice: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    rightPanel: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        padding: 20,
        display: "flex",
        flexDirection: "column",
        gap: 20,
    },
    emptyDetail: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100%",
    },
    detailHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        paddingBottom: 16,
        borderBottom: "1px solid var(--border-card)",
    },
    detailSymbol: { fontSize: 24, fontWeight: 700, color: "var(--text-primary)", marginBottom: 4 },
    detailName: { fontSize: 13, color: "var(--text-muted)" },
    detailPriceBox: { textAlign: "right" },
    detailPrice: { fontSize: 28, fontWeight: 700, color: "var(--text-primary)", marginBottom: 4 },
    detailChart: {
        padding: "16px 0",
    },
    detailActions: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 12,
    },
    buyBtn: {
        padding: "12px",
        borderRadius: 8,
        border: "none",
        background: "#10b981",
        color: "#fff",
        fontSize: 14,
        fontWeight: 600,
        cursor: "pointer",
        transition: "opacity 0.2s",
    },
    sellBtn: {
        padding: "12px",
        borderRadius: 8,
        border: "none",
        background: "#ef4444",
        color: "#fff",
        fontSize: 14,
        fontWeight: 600,
        cursor: "pointer",
        transition: "opacity 0.2s",
    },
    detailStats: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 16,
        paddingTop: 16,
        borderTop: "1px solid var(--border-card)",
    },
    statItem: { display: "flex", flexDirection: "column", gap: 4 },
    statLabel: { fontSize: 11, color: "var(--text-muted)" },
    statValue: { fontSize: 14, fontWeight: 600, color: "var(--text-primary)" },
    infoBox: {
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "var(--bg-panel)",
        padding: "10px 12px",
        display: "grid",
        gap: 8,
    },
    infoRow: { display: "flex", justifyContent: "space-between", alignItems: "center" },
    input: {
        padding: "9px 12px",
        borderRadius: 8,
        border: "1px solid var(--input-border)",
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        outline: "none",
        width: "100%",
        boxSizing: "border-box",
        fontSize: 14,
    },
    primaryBtn: {
        padding: "10px 20px",
        borderRadius: 8,
        border: "none",
        background: "#10b981",
        color: "#000",
        cursor: "pointer",
        fontWeight: 700,
        fontSize: 14,
        transition: "all 0.2s",
    },
    ghostBtn: {
        padding: "10px 20px",
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "transparent",
        color: "var(--text-muted)",
        cursor: "pointer",
        fontWeight: 600,
        fontSize: 14,
        transition: "all 0.2s",
    },
};

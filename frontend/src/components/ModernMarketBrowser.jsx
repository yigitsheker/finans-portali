import { useEffect, useMemo, useState } from "react";
import {
    getMarketSummary,
    upsertPosition,
} from "../api/portfolioApi";
import Modal from "./Modal";
import InstrumentChartModal from "./InstrumentChartModal";
import CompareInstrumentsModal from "./CompareInstrumentsModal";

const TYPE_LABELS = {
    STOCK: "Hisse",
    BIST: "BIST",
    CRYPTO: "Kripto",
    FX: "Döviz",
    COMMODITY: "Emtia",
    INDEX: "Endeks",
};

export default function ModernMarketBrowser({ keycloak, onAdded }) {
    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(true);
    const [filter, setFilter] = useState("ALL");
    const [search, setSearch] = useState("");
    const [err, setErr] = useState(null);
    const [selected, setSelected] = useState(null);
    const [addTarget, setAddTarget] = useState(null);
    const [addQty, setAddQty] = useState(1);
    const [addSaving, setAddSaving] = useState(false);
    const [addErr, setAddErr] = useState(null);
    const [compareTarget, setCompareTarget] = useState(null);

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
        } catch (e) {
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
                        const color = pos ? "var(--green)" : "var(--red)";
                        return (
                            <div key={idx.symbol} style={s.indexCard}>
                                <div style={s.indexLabel}>{idx.symbol}</div>
                                <div style={s.indexPrice}>{idx.last?.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}</div>
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
                            {t === "ALL" ? "Tümü" : TYPE_LABELS[t] ?? t}
                        </button>
                    ))}
                </div>
            </div>

            {/* Stock List */}
            {filtered.length === 0 ? (
                <div style={s.empty}>Sonuç bulunamadı.</div>
            ) : (
                <div style={s.stockList}>
                    {filtered.map((item) => {
                        const pos = item.changePct >= 0;
                        const color = pos ? "var(--green)" : "var(--red)";
                        return (
                            <div
                                key={item.symbol}
                                style={s.stockRow}
                                onClick={() => setSelected(item)}
                            >
                                <div style={s.stockLeft}>
                                    <div style={s.stockSymbol}>{item.symbol}</div>
                                    <div style={s.stockName}>{item.name}</div>
                                </div>
                                <div style={s.stockCenter}>
                                    <MiniChart positive={pos} />
                                </div>
                                <div style={s.stockRight}>
                                    <div style={s.stockPrice}>
                                        ₺{item.last?.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                    </div>
                                    <div style={{ color, fontSize: 12, fontWeight: 600 }}>
                                        {pos ? "▲" : "▼"} {pos ? "+" : ""}
                                        {item.changePct?.toFixed(2)}%
                                    </div>
                                </div>
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
                        );
                    })}
                </div>
            )}

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
                title={"Portföye Ekle - " + (addTarget?.symbol ?? "")}
                onClose={() => setAddTarget(null)}
                footer={
                    <>
                        <button style={s.ghostBtn} onClick={() => setAddTarget(null)} disabled={addSaving}>
                            Vazgeç
                        </button>
                        <button style={s.primaryBtn} onClick={onConfirmAdd} disabled={addSaving}>
                            {addSaving ? "Ekleniyor..." : "Portföye Ekle"}
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
                                <span style={{ color: "var(--text-muted)", fontSize: 13 }}>Güncel Fiyat</span>
                                <span style={{ color: "var(--text-primary)", fontWeight: 700, fontSize: 18 }}>
                                    ₺{addTarget.last?.toLocaleString("tr-TR")}
                                </span>
                            </div>
                        </div>
                        <div style={{ display: "grid", gap: 6 }}>
                            <div style={{ fontSize: 12, color: "var(--text-muted)" }}>Adet (Lot)</div>
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
                                <span style={{ color: "var(--text-muted)", fontSize: 13 }}>Toplam Tutar</span>
                                <span style={{ color: "var(--text-primary)", fontWeight: 700, fontSize: 16 }}>
                                    ₺{addTotal > 0 ? addTotal.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}
                                </span>
                            </div>
                        </div>
                        {addErr && <div style={{ color: "var(--danger-text)", fontSize: 13 }}>{addErr}</div>}
                    </div>
                )}
            </Modal>
        </div>
    );
}

function MiniChart({ positive }) {
    const color = positive ? "var(--green)" : "var(--red)";
    const pts = positive
        ? "0,20 10,15 20,17 30,12 40,10 50,8"
        : "0,8 10,10 20,12 30,15 40,17 50,20";
    return (
        <svg width="50" height="20" viewBox="0 0 50 20" preserveAspectRatio="none" style={{ display: "block" }}>
            <polyline points={pts} fill="none" stroke={color} strokeWidth="1.5" />
        </svg>
    );
}

const s = {
    root: { display: "flex", flexDirection: "column", gap: 20 },
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
        borderTop: "3px solid var(--accent-solid)",
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
        gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))",
        gap: 12,
    },
    indexCard: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        padding: "14px 16px",
        cursor: "pointer",
        transition: "border-color 0.2s, background 0.2s",
    },
    indexLabel: { fontSize: 11, color: "var(--text-muted)", marginBottom: 6, fontWeight: 500 },
    indexPrice: { fontSize: 20, fontWeight: 700, color: "var(--text-primary)", marginBottom: 2 },
    controls: { display: "flex", flexDirection: "column", gap: 12 },
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
        fontSize: 14,
    },
    filterRow: { display: "flex", gap: 8, flexWrap: "wrap" },
    filterBtn: {
        padding: "6px 14px",
        borderRadius: 6,
        border: "1px solid var(--border-card)",
        background: "transparent",
        color: "var(--text-muted)",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 500,
        transition: "all 0.2s",
    },
    filterActive: {
        border: "1px solid var(--accent-solid)",
        background: "var(--accent)",
        color: "var(--text-primary)",
    },
    empty: {
        textAlign: "center",
        padding: "40px 0",
        color: "var(--text-muted)",
        fontSize: 14,
    },
    stockList: { display: "flex", flexDirection: "column", gap: 1 },
    stockRow: {
        display: "grid",
        gridTemplateColumns: "2fr 1fr 1.5fr auto",
        alignItems: "center",
        gap: 16,
        padding: "14px 16px",
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        cursor: "pointer",
        transition: "all 0.2s",
    },
    stockLeft: { display: "flex", flexDirection: "column", gap: 2 },
    stockSymbol: { fontSize: 14, fontWeight: 700, color: "var(--text-primary)" },
    stockName: { fontSize: 11, color: "var(--text-muted)" },
    stockCenter: { display: "flex", justifyContent: "center" },
    stockRight: { display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 2 },
    stockPrice: { fontSize: 16, fontWeight: 700, color: "var(--text-primary)" },
    actionBtn: {
        padding: "8px 16px",
        borderRadius: 6,
        border: "none",
        background: "var(--accent-solid)",
        color: "#fff",
        fontSize: 12,
        fontWeight: 600,
        cursor: "pointer",
        transition: "opacity 0.2s",
    },
    infoBox: {
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "var(--bg-panel)",
        padding: "10px 12px",
        display: "grid",
        gap: 8,
    },
    infoRow: { display: "flex", justifyContent: "space-between", alignItems: "center" },
    badge: {
        padding: "2px 8px",
        borderRadius: 4,
        background: "var(--accent)",
        border: "1px solid var(--accent-border)",
        color: "var(--text-primary)",
        fontSize: 11,
    },
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
        padding: "9px 16px",
        borderRadius: 8,
        border: "none",
        background: "var(--accent-solid)",
        color: "#fff",
        cursor: "pointer",
        fontWeight: 600,
    },
    ghostBtn: {
        padding: "9px 16px",
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "transparent",
        color: "var(--text-primary)",
        cursor: "pointer",
    },
};

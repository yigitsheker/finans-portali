import { useEffect, useMemo, useState } from "react";
import AddPositionModal from "../components/AddPositionModal";
import { deletePosition, getPositions, type Position } from "../api/portfolioApi";
import type Keycloak from "keycloak-js";

type Props = {
    keycloak: Keycloak;
};

export default function PortfolioPage({ keycloak }: Props) {
    const [items, setItems] = useState<Position[]>([]);
    const [loading, setLoading] = useState(true);
    const [err, setErr] = useState<string | null>(null);
    const [openAdd, setOpenAdd] = useState(false);
    const [deletingId, setDeletingId] = useState<string | null>(null);

    async function refresh() {
        setLoading(true);
        setErr(null);
        try {
            const data = await getPositions(keycloak);
            setItems(data ?? []);
        } catch (e: any) {
            setErr(e?.response?.data?.message ?? e?.message ?? "GET error");
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        refresh();
    }, []);

    const stats = useMemo(() => {
        const total = items.length;
        const totalQty = items.reduce((acc, x) => acc + (Number(x.quantity) || 0), 0);
        return { total, totalQty };
    }, [items]);

    async function onDelete(symbol: string) {
        if (!confirm("Bu pozisyon silinsin mi?")) return;
        setDeletingId(symbol);
        try {
            await deletePosition(keycloak, symbol);
            await refresh();
        } catch (e: any) {
            alert(e?.response?.data?.message ?? e?.message ?? "DELETE error");
        } finally {
            setDeletingId(null);
        }
    }

    return (
        <div>
            <style>{`
        .pos-card { transition: transform .18s ease, border-color .18s ease, background .18s ease; }
        .pos-card:hover {
          transform: translateY(-2px);
          border-color: rgba(99,102,241,0.50);
          background: linear-gradient(180deg, rgba(99,102,241,0.10), rgba(255,255,255,0.02));
        }
        @media (max-width: 980px) {
          .pos-grid { grid-template-columns: 1fr !important; }
        }
      `}</style>

            <div style={s.headerRow}>
                <div>
                    <div style={s.h1}>Portföy</div>
                    <div style={s.sub}>Pozisyonlarını görüntüle, ekle ve sil.</div>
                </div>

                <div style={s.headerRight}>
          <span style={s.pill}>
            Pozisyon <b style={s.pillStrong}>{stats.total}</b>
          </span>
                    <span style={s.pill}>
            Toplam adet <b style={s.pillStrong}>{stats.totalQty}</b>
          </span>

                    <button style={s.primaryBtn} onClick={() => setOpenAdd(true)}>
                        + Pozisyon Ekle
                    </button>
                </div>
            </div>

            {loading && (
                <div style={s.skeletonWrap}>
                    <div style={s.skelRow} />
                    <div style={s.skelRow} />
                    <div style={s.skelRow} />
                </div>
            )}

            {!loading && err && (
                <div style={s.errBox}>
                    <b>Hata:</b> {err}
                    <div style={{ marginTop: 10 }}>
                        <button style={s.secondaryBtn} onClick={refresh}>
                            Tekrar Dene
                        </button>
                    </div>
                </div>
            )}

            {!loading && !err && items.length === 0 && (
                <div style={s.emptyBox}>
                    <div style={{ fontWeight: 900, fontSize: 16 }}>Henüz pozisyon yok</div>
                    <div style={{ opacity: 0.8, marginTop: 6 }}>
                        İlk pozisyonunu ekleyerek portföyünü oluşturmaya başlayabilirsin.
                    </div>
                    <button style={{ ...s.primaryBtn, marginTop: 12 }} onClick={() => setOpenAdd(true)}>
                        + Pozisyon Ekle
                    </button>
                </div>
            )}

            {!loading && !err && items.length > 0 && (
                <div className="pos-grid" style={s.grid}>
                    {items.map((p) => (
                        <div key={p.symbol} className="pos-card" style={s.card}>
                            <div style={s.cardTop}>
                                <div style={s.symbol}>{p.symbol}</div>

                                <button
                                    style={s.dangerBtn}
                                    onClick={() => onDelete(p.symbol)}
                                    disabled={deletingId === p.symbol}
                                >
                                    {deletingId === p.symbol ? "Siliniyor..." : "Sil"}
                                </button>
                            </div>

                            <div style={s.metaRow}>
                <span style={s.kv}>
                  <span style={s.k}>Adet</span>
                  <span style={s.v}>{p.quantity}</span>
                </span>
                                <span style={s.kv}>
                  <span style={s.k}>Ort. Maliyet</span>
                  <span style={s.v}>{p.avgCost || 'N/A'}</span>
                </span>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            <AddPositionModal open={openAdd} onClose={() => setOpenAdd(false)} onCreated={refresh} keycloak={keycloak} />
        </div>
    );
}

const s: Record<string, React.CSSProperties> = {
    headerRow: {
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "space-between",
        gap: 14,
        marginBottom: 16,
        flexWrap: "wrap",
    },
    headerRight: {
        display: "flex",
        gap: 10,
        alignItems: "center",
        flexWrap: "wrap",
        justifyContent: "flex-end",
    },
    h1: { fontSize: 22, fontWeight: 900, letterSpacing: 0.2 },
    sub: { fontSize: 13, opacity: 0.75, marginTop: 4 },

    pill: {
        display: "inline-flex",
        alignItems: "center",
        gap: 8,
        padding: "6px 10px",
        borderRadius: 999,
        border: "1px solid rgba(255,255,255,0.10)",
        background: "rgba(255,255,255,0.03)",
        fontSize: 12,
    },
    pillStrong: { fontWeight: 900 },

    primaryBtn: {
        borderRadius: 12,
        padding: "10px 14px",
        border: "1px solid rgba(99,102,241,0.45)",
        background: "rgba(99,102,241,0.18)",
        color: "inherit",
        cursor: "pointer",
        fontWeight: 900,
    },
    secondaryBtn: {
        borderRadius: 12,
        padding: "10px 14px",
        border: "1px solid rgba(255,255,255,0.10)",
        background: "rgba(255,255,255,0.06)",
        color: "inherit",
        cursor: "pointer",
    },
    dangerBtn: {
        borderRadius: 12,
        padding: "8px 12px",
        border: "1px solid rgba(239,68,68,0.45)",
        background: "rgba(239,68,68,0.10)",
        color: "inherit",
        cursor: "pointer",
        fontWeight: 800,
    },

    grid: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 14,
    },
    card: {
        borderRadius: 14,
        border: "1px solid rgba(255,255,255,0.10)",
        background: "rgba(255,255,255,0.03)",
        padding: 16,
        boxShadow: "0 8px 30px rgba(0,0,0,0.25)",
    },
    cardTop: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 12,
        marginBottom: 12,
    },
    symbol: { fontSize: 18, fontWeight: 950, letterSpacing: 0.4 },
    metaRow: {
        display: "flex",
        gap: 12,
        flexWrap: "wrap",
    },
    kv: {
        display: "inline-flex",
        gap: 8,
        alignItems: "baseline",
        padding: "6px 10px",
        borderRadius: 12,
        border: "1px solid rgba(255,255,255,0.10)",
        background: "rgba(0,0,0,0.18)",
    },
    k: { fontSize: 12, opacity: 0.75 },
    v: { fontSize: 13, fontWeight: 900 },
    note: { marginTop: 12, fontSize: 13, opacity: 0.9 },
    noteEmpty: { marginTop: 12, fontSize: 13, opacity: 0.55, fontStyle: "italic" },

    emptyBox: {
        borderRadius: 14,
        border: "1px dashed rgba(255,255,255,0.20)",
        background: "rgba(255,255,255,0.02)",
        padding: 18,
    },
    errBox: {
        borderRadius: 14,
        border: "1px solid rgba(239,68,68,0.35)",
        background: "rgba(239,68,68,0.10)",
        padding: 18,
    },

    skeletonWrap: {
        display: "grid",
        gap: 10,
    },
    skelRow: {
        height: 70,
        borderRadius: 14,
        border: "1px solid rgba(255,255,255,0.10)",
        background: "rgba(255,255,255,0.04)",
    },
};
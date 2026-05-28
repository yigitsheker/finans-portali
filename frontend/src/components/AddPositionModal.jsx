import { useMemo, useState } from "react";
import Modal from "./Modal";
import { upsertPosition } from "../api/portfolioApi";

export default function AddPositionModal({ open, onClose, onCreated, keycloak, initialSymbol = "", initialPrice = "" }) {
    // Seed the form from the optional props so callers (e.g. Analysis page's
    // chart modal) can pre-fill the symbol/price the user just clicked on.
    // Plain initial-state seeding — the modal is unmounted between opens
    // when `open` flips, so each new `initialSymbol` value lands fresh.
    const [symbol, setSymbol] = useState(initialSymbol || "");
    const [quantity, setQuantity] = useState("1");
    const [avgPrice, setAvgPrice] = useState(initialPrice ? String(initialPrice) : "0");
    const [note, setNote] = useState("");
    const [saving, setSaving] = useState(false);
    const [err, setErr] = useState(null);

    const canSave = useMemo(() => {
        const q = Number(quantity);
        const p = Number(avgPrice);
        return symbol.trim().length >= 2 && Number.isFinite(q) && q > 0 && Number.isFinite(p) && p >= 0;
    }, [symbol, quantity, avgPrice]);

    async function onSubmit() {
        setErr(null);
        if (!canSave) return;

        const payload = {
            symbol: symbol.trim().toUpperCase(),
            quantity: Number(quantity),
            avgCost: Number(avgPrice),
        };

        try {
            setSaving(true);
            await upsertPosition(keycloak, payload);
            onClose();
            onCreated();
            // form reset
            setSymbol("");
            setQuantity("1");
            setAvgPrice("0");
            setNote("");
        } catch (e) {
            setErr(e?.response?.data?.message ?? e?.message ?? "POST error");
        } finally {
            setSaving(false);
        }
    }

    return (
        <Modal open={open} title="Pozisyon Ekle" onClose={onClose}>
            <div style={s.grid}>
                <label style={s.label}>
                    Sembol
                    <input
                        style={s.input}
                        placeholder="THYAO / AAPL / BTC"
                        value={symbol}
                        onChange={(e) => setSymbol(e.target.value)}
                    />
                </label>

                <label style={s.label}>
                    Adet
                    <input
                        style={s.input}
                        type="number"
                        min={0}
                        value={quantity}
                        onChange={(e) => setQuantity(e.target.value)}
                    />
                </label>

                <label style={s.label}>
                    Ortalama Maliyet
                    <input
                        style={s.input}
                        type="number"
                        min={0}
                        step="0.01"
                        value={avgPrice}
                        onChange={(e) => setAvgPrice(e.target.value)}
                    />
                </label>

                <label style={{ ...s.label, gridColumn: "1 / -1" }}>
                    Not (opsiyonel)
                    <input style={s.input} value={note} onChange={(e) => setNote(e.target.value)} />
                </label>
            </div>

            {err && <div style={s.err}>Hata: {err}</div>}

            <div style={s.actions}>
                <button style={s.secondaryBtn} onClick={onClose} disabled={saving}>
                    Vazgeç
                </button>
                <button style={{ ...s.primaryBtn, opacity: canSave ? 1 : 0.6 }} onClick={onSubmit} disabled={!canSave || saving}>
                    {saving ? "Kaydediliyor..." : "Kaydet"}
                </button>
            </div>
        </Modal>
    );
}

const s = {
    grid: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 12,
    },
    label: {
        display: "grid",
        gap: 6,
        fontSize: 12,
        opacity: 0.9,
    },
    input: {
        padding: "10px 12px",
        borderRadius: 12,
        border: "1px solid rgba(255,255,255,0.12)",
        background: "rgba(255,255,255,0.04)",
        color: "inherit",
        outline: "none",
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: 10,
        marginTop: 14,
    },
    primaryBtn: {
        borderRadius: 12,
        padding: "10px 14px",
        border: "1px solid rgba(99,102,241,0.45)",
        background: "rgba(99,102,241,0.18)",
        color: "inherit",
        cursor: "pointer",
        fontWeight: 800,
    },
    secondaryBtn: {
        borderRadius: 12,
        padding: "10px 14px",
        border: "1px solid rgba(255,255,255,0.10)",
        background: "rgba(255,255,255,0.06)",
        color: "inherit",
        cursor: "pointer",
    },
    err: {
        marginTop: 12,
        borderRadius: 12,
        padding: 12,
        border: "1px solid rgba(239,68,68,0.35)",
        background: "rgba(239,68,68,0.10)",
    },
};

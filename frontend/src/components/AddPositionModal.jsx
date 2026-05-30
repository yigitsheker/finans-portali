import { useEffect, useMemo, useState } from "react";
import PropTypes from "prop-types";
import Modal from "./Modal";
import { upsertPosition } from "../api/portfolioApi";

export default function AddPositionModal({
    open,
    onClose,
    onCreated,
    keycloak,
    initialSymbol = "",
    initialPrice = "",
    contractMultiplier = 1,
}) {
    // Seed the form from the optional props so callers (e.g. Analysis page's
    // chart modal, Bonds/Funds/FX/VIOP rows) can pre-fill the symbol/price
    // the user just clicked on.
    const [symbol, setSymbol] = useState(initialSymbol || "");
    const [quantity, setQuantity] = useState("1");
    const [avgPrice, setAvgPrice] = useState(initialPrice ? String(initialPrice) : "0");
    const [saving, setSaving] = useState(false);
    const [err, setErr] = useState(null);

    // AddPositionModal stays mounted across opens (parent toggles `open`,
    // the inner <Modal> returns null when closed but THIS component stays
    // mounted), so useState's initial value only fires once — when buyTarget
    // changes from null → {symbol: "TR2YR", ...} the state still shows the
    // previous (empty) symbol. Re-seed on every open→true transition so the
    // form picks up the latest initialSymbol/initialPrice.
    useEffect(() => {
        if (!open) return;
        setSymbol(initialSymbol || "");
        setAvgPrice(initialPrice ? String(initialPrice) : "0");
        setQuantity("1");
        setErr(null);
    }, [open, initialSymbol, initialPrice]);

    // VIOP contract-multiplier preview. When contractMultiplier > 1 the
    // saved quantity is silently multiplied by it on submit so the portfolio
    // valuation (= qty × current price) matches the contract's real TL
    // exposure (1 lot of USDTRY ≠ 1 USD, it's 1000 USD). The preview row
    // surfaces what the user is actually buying.
    const multiplier = Number(contractMultiplier) || 1;
    const totalUnits = (() => {
        const q = Number(quantity);
        return Number.isFinite(q) ? q * multiplier : 0;
    })();
    const totalCost = (() => {
        const p = Number(avgPrice);
        return Number.isFinite(p) && Number.isFinite(totalUnits) ? totalUnits * p : 0;
    })();

    const canSave = useMemo(() => {
        const q = Number(quantity);
        const p = Number(avgPrice);
        return symbol.trim().length >= 2 && Number.isFinite(q) && q > 0 && Number.isFinite(p) && p >= 0;
    }, [symbol, quantity, avgPrice]);

    async function onSubmit() {
        setErr(null);
        if (!canSave) return;

        // For VIOP-style contracts (multiplier > 1) we save quantity × multiplier
        // so the portfolio's `qty * currentPrice` valuation lines up with the
        // contract's real exposure. avgCost stays per-unit (the price the user
        // sees on the trading screen).
        const payload = {
            symbol: symbol.trim().toUpperCase(),
            quantity: Number(quantity) * multiplier,
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
            </div>

            {multiplier > 1 && (
                <div style={s.viopInfo}>
                    <div style={s.viopRow}>
                        <span style={s.viopLabel}>Sözleşme Çarpanı</span>
                        <strong style={s.viopValue}>{multiplier.toLocaleString("tr-TR")}</strong>
                    </div>
                    <div style={s.viopRow}>
                        <span style={s.viopLabel}>Toplam Pozisyon Büyüklüğü</span>
                        <strong style={s.viopValue}>{totalUnits.toLocaleString("tr-TR")} birim</strong>
                    </div>
                    <div style={s.viopRow}>
                        <span style={s.viopLabel}>Tahmini Toplam Maliyet</span>
                        <strong style={s.viopValue}>
                            {totalCost.toLocaleString("tr-TR", {
                                minimumFractionDigits: 2,
                                maximumFractionDigits: 2,
                            })}
                        </strong>
                    </div>
                </div>
            )}

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

AddPositionModal.propTypes = {
    open: PropTypes.bool.isRequired,
    onClose: PropTypes.func.isRequired,
    onCreated: PropTypes.func,
    keycloak: PropTypes.object,
    initialSymbol: PropTypes.string,
    initialPrice: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    contractMultiplier: PropTypes.number,
};

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
    viopInfo: {
        marginTop: 14,
        padding: "10px 12px",
        borderRadius: 12,
        background: "rgba(99,102,241,0.08)",
        border: "1px solid rgba(99,102,241,0.25)",
        display: "flex",
        flexDirection: "column",
        gap: 6,
    },
    viopRow: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        fontSize: 12,
    },
    viopLabel: { color: "var(--text-muted)" },
    viopValue: { color: "var(--text-primary)", fontVariantNumeric: "tabular-nums" },
};

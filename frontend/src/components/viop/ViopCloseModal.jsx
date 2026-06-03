import { useEffect, useMemo, useState } from "react";
import PropTypes from "prop-types";
import Modal from "../Modal";
import SimulationDisclaimer from "../SimulationDisclaimer";
import { useI18n } from "../../contexts/I18nContext";
import { closeViopPosition } from "../../api/viopTradeApi";

const fmt = (v, d = 2) => {
    if (v == null) return "—";
    const n = Number(v);
    if (!Number.isFinite(n)) return "—";
    return n.toLocaleString("tr-TR", { minimumFractionDigits: d, maximumFractionDigits: d });
};

/**
 * Pozisyon Kapat / Kısmi Kapat. The realized-P/L figure shown is a frontend
 * ESTIMATE for preview; the backend computes the authoritative value on close.
 */
export default function ViopCloseModal({ open, position, keycloak, onClose, onDone }) {
    const { t } = useI18n();
    const [qty, setQty] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (open && position) { setQty(String(position.quantity)); setError(null); }
    }, [open, position]);

    const estPnl = useMemo(() => {
        if (!position) return null;
        const q = Number(qty), entry = Number(position.entryPrice),
            cur = Number(position.currentPrice), size = Number(position.contractSize);
        if (![q, entry, cur, size].every(Number.isFinite) || q <= 0) return null;
        const diff = position.direction === "LONG" ? cur - entry : entry - cur;
        return diff * size * q;
    }, [position, qty]);

    if (!position) return null;
    const max = Number(position.quantity);
    const q = Number(qty);
    const isPartial = Number.isFinite(q) && q > 0 && q < max;
    const title = isPartial ? t("viopTrade.partialClose") : t("viopTrade.closePosition");

    const submit = async () => {
        if (!Number.isFinite(q) || q <= 0) { setError(t("viopTrade.invalidQty")); return; }
        if (q > max) { setError(t("viopTrade.tooManyToClose")); return; }
        setSubmitting(true); setError(null);
        try {
            await closeViopPosition(keycloak, { contractSymbol: position.contractSymbol, quantity: q });
            onDone?.();
            onClose();
        } catch (e) {
            setError(e?.response?.data?.message || e?.message || t("viopTrade.failed"));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Modal open={open} title={`${title} — ${position.contractSymbol}`} onClose={onClose} maxWidth={440}>
            <div style={s.wrap}>
                <SimulationDisclaimer risk="viop" />
                <div style={s.row}><span style={s.k}>{t("viopTrade.openContracts")}</span><span style={s.v}>{fmt(max, 0)}</span></div>
                <div style={s.row}><span style={s.k}>{t("viopTrade.entryPrice")}</span><span style={s.v}>{fmt(position.entryPrice)}</span></div>
                <div style={s.row}><span style={s.k}>{t("viopTrade.currentPrice")}</span><span style={s.v}>{fmt(position.currentPrice)}</span></div>

                <label style={s.label}>{t("viopTrade.contractsToClose")}</label>
                <input type="number" min="1" max={max} step="1" value={qty}
                    onChange={(e) => setQty(e.target.value)} style={s.input} autoFocus />

                {estPnl != null && (
                    <div style={s.row}>
                        <span style={s.k}>{t("viopTrade.estRealizedPnl")}</span>
                        <span style={{ ...s.v, fontWeight: 700, color: estPnl >= 0 ? "#16a34a" : "#dc2626" }}>
                            {fmt(estPnl)} {position.currency}
                        </span>
                    </div>
                )}
                {error && <div style={s.error}>{error}</div>}

                <button type="button" onClick={submit} disabled={submitting}
                    style={{ ...s.submit, opacity: submitting ? 0.6 : 1 }}>
                    {submitting ? t("viopTrade.submitting") : title}
                </button>
            </div>
        </Modal>
    );
}

ViopCloseModal.propTypes = {
    open: PropTypes.bool,
    position: PropTypes.object,
    keycloak: PropTypes.object,
    onClose: PropTypes.func,
    onDone: PropTypes.func,
};

const s = {
    wrap: { display: "flex", flexDirection: "column", gap: 10 },
    row: { display: "flex", justifyContent: "space-between", fontSize: 13 },
    k: { color: "var(--text-muted)" },
    v: { color: "var(--text-primary)", fontWeight: 600 },
    label: { fontSize: 12, fontWeight: 600, color: "var(--text-muted)", marginTop: 4 },
    input: {
        padding: "10px 12px", borderRadius: 8, border: "1px solid var(--border-card)",
        background: "var(--input-bg)", color: "var(--text-primary)", fontSize: 15,
    },
    error: { color: "#ef4444", fontSize: 13 },
    submit: {
        marginTop: 6, padding: "12px", borderRadius: 8, border: "none",
        background: "var(--accent-solid, #22c55e)", color: "#fff", fontWeight: 700,
        fontSize: 14, cursor: "pointer",
    },
};

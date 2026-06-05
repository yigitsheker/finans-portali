import { useEffect, useState } from "react";
import PropTypes from "prop-types";
import Modal from "../Modal";
import SimulationDisclaimer from "../SimulationDisclaimer";
import { useI18n } from "../../contexts/I18nContext";
import { sellBond, previewBondSell } from "../../api/bondTradeApi";

const fmt = (v, d = 2) => {
    if (v == null) return "—";
    const n = Number(v);
    if (!Number.isFinite(n)) return "—";
    return n.toLocaleString("tr-TR", { minimumFractionDigits: d, maximumFractionDigits: d });
};

/** Bond/bill SELL by nominal (≤ remaining). Backend computes proportional cost
 *  and realized P/L; the preview here is backend-computed too. Simulation only. */
export default function BondSellModal({ open, position, keycloak, onClose, onDone }) {
    const { t } = useI18n();
    const [nominal, setNominal] = useState("");
    const [cleanPrice, setCleanPrice] = useState("");
    const [accrued, setAccrued] = useState("0");
    const [preview, setPreview] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);

    const identifier = position?.isin;

    useEffect(() => {
        if (open && position) {
            setNominal(String(position.remainingNominal));
            setCleanPrice(position.currentCleanPrice != null ? String(position.currentCleanPrice) : "");
            setAccrued("0");
            setPreview(null);
            setError(null);
        }
    }, [open, position]);

    useEffect(() => {
        if (!open || !identifier) return;
        const n = Number(nominal), c = Number(cleanPrice);
        if (!Number.isFinite(n) || n <= 0 || !Number.isFinite(c) || c <= 0) { setPreview(null); return; }
        let cancelled = false;
        const id = setTimeout(() => {
            previewBondSell(keycloak, { identifier, nominal: n, cleanPrice: c, accruedInterest: Number(accrued) || 0 })
                .then((p) => { if (!cancelled) setPreview(p); })
                .catch(() => { if (!cancelled) setPreview(null); });
        }, 250);
        return () => { cancelled = true; clearTimeout(id); };
    }, [open, identifier, nominal, cleanPrice, accrued, keycloak]);

    if (!position) return null;
    const max = Number(position.remainingNominal);

    const submit = async () => {
        const n = Number(nominal), c = Number(cleanPrice);
        if (!Number.isFinite(n) || n <= 0 || !Number.isFinite(c) || c <= 0) { setError(t("bondTrade.invalidInput")); return; }
        if (n > max) { setError(t("bondTrade.tooMuchNominal")); return; }
        setSubmitting(true); setError(null);
        try {
            await sellBond(keycloak, { identifier, nominal: n, cleanPrice: c, accruedInterest: Number(accrued) || 0 });
            onDone?.();
            onClose();
        } catch (e) {
            setError(e?.response?.data?.message || e?.message || t("bondTrade.failed"));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Modal open={open} title={`${t("bondTrade.sellTitle")} — ${position.symbol || identifier}`} onClose={onClose} maxWidth={460}>
            <div style={s.wrap}>
                <SimulationDisclaimer risk="bond" />
                <Row k={t("bondTrade.remainingNominal")} v={fmt(max)} />

                <label style={s.label}>{t("bondTrade.sellNominal")}</label>
                <input type="number" min="0" max={max} step="1000" value={nominal} onChange={(e) => setNominal(e.target.value)} style={s.input} autoFocus />

                <div style={s.two}>
                    <div style={{ flex: 1 }}>
                        <label style={s.label}>{t("bondTrade.cleanPrice")}</label>
                        <input type="number" min="0" step="0.01" value={cleanPrice} onChange={(e) => setCleanPrice(e.target.value)} style={s.input} />
                    </div>
                    <div style={{ flex: 1 }}>
                        <label style={s.label}>{t("bondTrade.accrued")}</label>
                        <input type="number" min="0" step="0.01" value={accrued} onChange={(e) => setAccrued(e.target.value)} style={s.input} />
                    </div>
                </div>

                {preview && (
                    <div style={s.preview}>
                        <Row k={t("bondTrade.dirtyPrice")} v={fmt(preview.dirtyPrice, 4)} />
                        <Row k={t("bondTrade.proceeds")} v={`${fmt(preview.totalAmount)} ${position.currency || "TRY"}`} />
                        {preview.proportionalCost != null && <Row k={t("bondTrade.costOfSold")} v={fmt(preview.proportionalCost)} />}
                        {preview.estimatedRealizedPnl != null && (
                            <Row k={t("bondTrade.estRealizedPnl")}
                                v={`${fmt(preview.estimatedRealizedPnl)} ${position.currency || "TRY"}`}
                                color={Number(preview.estimatedRealizedPnl) >= 0 ? "var(--green)" : "var(--red)"} strong />
                        )}
                    </div>
                )}
                {error && <div style={s.error}>{error}</div>}

                <button type="button" onClick={submit} disabled={submitting} style={{ ...s.submit, opacity: submitting ? 0.6 : 1 }}>
                    {submitting ? t("bondTrade.submitting") : t("bondTrade.sellTitle")}
                </button>
            </div>
        </Modal>
    );
}

function Row({ k, v, strong, color }) {
    return (
        <div style={s.row}>
            <span style={s.rowK}>{k}</span>
            <span style={{ ...s.rowV, fontWeight: strong ? 700 : 500, color: color || "var(--text-primary)" }}>{v}</span>
        </div>
    );
}
Row.propTypes = { k: PropTypes.string, v: PropTypes.string, strong: PropTypes.bool, color: PropTypes.string };

BondSellModal.propTypes = {
    open: PropTypes.bool,
    position: PropTypes.object,
    keycloak: PropTypes.object,
    onClose: PropTypes.func,
    onDone: PropTypes.func,
};

const s = {
    wrap: { display: "flex", flexDirection: "column", gap: 12 },
    two: { display: "flex", gap: 12 },
    label: { fontSize: 12, fontWeight: 600, color: "var(--text-muted)" },
    input: {
        width: "100%", boxSizing: "border-box", padding: "10px 12px", borderRadius: 8,
        border: "1px solid var(--border-card)", background: "var(--input-bg)",
        color: "var(--text-primary)", fontSize: 15,
    },
    preview: {
        display: "flex", flexDirection: "column", gap: 6, padding: 12, borderRadius: 8,
        background: "var(--bg-card)", border: "1px solid var(--border-card)",
    },
    row: { display: "flex", justifyContent: "space-between", fontSize: 13 },
    rowK: { color: "var(--text-muted)" },
    rowV: { color: "var(--text-primary)" },
    error: { color: "var(--danger-text)", fontSize: 13 },
    submit: {
        marginTop: 4, padding: "12px", borderRadius: 8, border: "none",
        background: "var(--red)", color: "#fff", fontWeight: 700, fontSize: 14, cursor: "pointer",
    },
};

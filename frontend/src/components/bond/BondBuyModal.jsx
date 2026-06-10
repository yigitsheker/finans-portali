import { useEffect, useState } from "react";
import PropTypes from "prop-types";
import Modal from "../Modal";
import SimulationDisclaimer from "../SimulationDisclaimer";
import { useI18n } from "../../contexts/I18nContext";
import { buyBond, previewBondBuy } from "../../api/bondTradeApi";

const fmt = (v, d = 2) => {
    if (v == null) return "—";
    const n = Number(v);
    if (!Number.isFinite(n)) return "—";
    return n.toLocaleString("tr-TR", { minimumFractionDigits: d, maximumFractionDigits: d });
};

/**
 * Bond/bill BUY by nominal. Backend computes the dirty price (clean + accrued)
 * and the total cost; the frontend only previews. Simulation only.
 */
export default function BondBuyModal({ open, bond, keycloak, onClose, onDone }) {
    const { t } = useI18n();
    const [nominal, setNominal] = useState("100000");
    const [cleanPrice, setCleanPrice] = useState("");
    const [accrued, setAccrued] = useState("0");
    const [autoAccrued, setAutoAccrued] = useState(true);
    const [couponFreq, setCouponFreq] = useState(""); // "" → instrument default
    const [preview, setPreview] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);

    const identifier = bond?.isin || bond?.symbol;

    useEffect(() => {
        if (open && bond) {
            setNominal("100000");
            setCleanPrice(bond.latestPrice != null ? String(bond.latestPrice) : "");
            setAccrued("0");
            setAutoAccrued(true);
            setCouponFreq("");
            setPreview(null);
            setError(null);
        }
    }, [open, bond]);

    useEffect(() => {
        if (!open || !identifier) return;
        const n = Number(nominal), c = Number(cleanPrice);
        if (!Number.isFinite(n) || n <= 0 || !Number.isFinite(c) || c <= 0) { setPreview(null); return; }
        let cancelled = false;
        const body = { identifier, nominal: n, cleanPrice: c };
        if (!autoAccrued) body.accruedInterest = Number(accrued) || 0;
        if (couponFreq) body.couponFrequency = Number(couponFreq);
        const id = setTimeout(() => {
            previewBondBuy(keycloak, body)
                .then((p) => { if (!cancelled) setPreview(p); })
                .catch(() => { if (!cancelled) setPreview(null); });
        }, 250);
        return () => { cancelled = true; clearTimeout(id); };
    }, [open, identifier, nominal, cleanPrice, accrued, autoAccrued, couponFreq, keycloak]);

    if (!bond) return null;

    const submit = async () => {
        const n = Number(nominal), c = Number(cleanPrice);
        if (!Number.isFinite(n) || n <= 0 || !Number.isFinite(c) || c <= 0) { setError(t("bondTrade.invalidInput")); return; }
        setSubmitting(true); setError(null);
        try {
            const body = { identifier, nominal: n, cleanPrice: c };
            if (!autoAccrued) body.accruedInterest = Number(accrued) || 0;
            if (couponFreq) body.couponFrequency = Number(couponFreq);
            await buyBond(keycloak, body);
            onDone?.();
            onClose();
        } catch (e) {
            setError(e?.response?.data?.message || e?.message || t("bondTrade.failed"));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Modal open={open} title={`${t("bondTrade.buyTitle")} — ${bond.symbol || identifier}`} onClose={onClose} maxWidth={460} busy={submitting}>
            <div style={s.wrap}>
                <SimulationDisclaimer risk="bond" />
                <div style={s.meta}>{bond.name}</div>

                <label style={s.label}>{t("bondTrade.nominal")}</label>
                <input type="number" min="0" step="1000" value={nominal} onChange={(e) => setNominal(e.target.value)} style={s.input} autoFocus />

                <div style={s.two}>
                    <div style={{ flex: 1 }}>
                        <label style={s.label}>{t("bondTrade.cleanPrice")}</label>
                        <input type="number" min="0" step="0.01" value={cleanPrice} onChange={(e) => setCleanPrice(e.target.value)} style={s.input} />
                    </div>
                    <div style={{ flex: 1 }}>
                        <label style={s.label}>{t("bondTrade.couponFrequency")}</label>
                        <select value={couponFreq} onChange={(e) => setCouponFreq(e.target.value)} style={s.input}>
                            <option value="">{t("bondTrade.freqAuto")}</option>
                            <option value="1">{t("bondTrade.freqAnnual")}</option>
                            <option value="2">{t("bondTrade.freqSemi")}</option>
                            <option value="4">{t("bondTrade.freqQuarterly")}</option>
                        </select>
                    </div>
                </div>

                <label style={s.check}>
                    <input type="checkbox" checked={autoAccrued} onChange={(e) => setAutoAccrued(e.target.checked)} />
                    {t("bondTrade.autoAccrued")}
                </label>
                {!autoAccrued && (
                    <div>
                        <label style={s.label}>{t("bondTrade.accrued")}</label>
                        <input type="number" min="0" step="0.01" value={accrued} onChange={(e) => setAccrued(e.target.value)} style={s.input} />
                    </div>
                )}

                {preview && (
                    <div style={s.preview}>
                        <Row k={t("bondTrade.accrued")} v={fmt(preview.accruedInterest, 4)} />
                        <Row k={t("bondTrade.dirtyPrice")} v={fmt(preview.dirtyPrice, 4)} />
                        <Row k={t("bondTrade.totalCost")} v={`${fmt(preview.totalAmount)} ${bond.currency || "TRY"}`} strong />
                    </div>
                )}
                {error && <div style={s.error}>{error}</div>}

                <button type="button" onClick={submit} disabled={submitting} style={{ ...s.submit, opacity: submitting ? 0.6 : 1 }}>
                    {submitting ? t("bondTrade.submitting") : t("bondTrade.buyTitle")}
                </button>
            </div>
        </Modal>
    );
}

function Row({ k, v, strong }) {
    return (
        <div style={s.row}>
            <span style={s.rowK}>{k}</span>
            <span style={{ ...s.rowV, fontWeight: strong ? 700 : 500 }}>{v}</span>
        </div>
    );
}
Row.propTypes = { k: PropTypes.string, v: PropTypes.string, strong: PropTypes.bool };

BondBuyModal.propTypes = {
    open: PropTypes.bool,
    bond: PropTypes.object,
    keycloak: PropTypes.object,
    onClose: PropTypes.func,
    onDone: PropTypes.func,
};

const s = {
    wrap: { display: "flex", flexDirection: "column", gap: 12 },
    meta: { fontSize: 13, color: "var(--text-muted)" },
    two: { display: "flex", gap: 12 },
    label: { fontSize: 12, fontWeight: 600, color: "var(--text-muted)" },
    check: { display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "var(--text-primary)", cursor: "pointer" },
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
        background: "var(--accent-solid, #22c55e)", color: "#fff", fontWeight: 700, fontSize: 14, cursor: "pointer",
    },
};

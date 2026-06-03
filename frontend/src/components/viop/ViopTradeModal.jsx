import { useEffect, useState } from "react";
import PropTypes from "prop-types";
import Modal from "../Modal";
import SimulationDisclaimer from "../SimulationDisclaimer";
import { useI18n } from "../../contexts/I18nContext";
import { openViopPosition, previewViopPosition } from "../../api/viopTradeApi";

const fmt = (v, d = 2) => {
    if (v == null) return "—";
    const n = Number(v);
    if (!Number.isFinite(n)) return "—";
    return n.toLocaleString("tr-TR", { minimumFractionDigits: d, maximumFractionDigits: d });
};

/**
 * Long Aç / Short Aç modal. Backend computes the preview (position size,
 * required margin, leverage) and the actual open — the frontend never does the
 * money math. Net-position logic on the backend may first close an opposing side.
 */
export default function ViopTradeModal({ open, contract, direction, keycloak, onClose, onDone }) {
    const { t } = useI18n();
    const [qty, setQty] = useState("1");
    const [preview, setPreview] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (open) { setQty("1"); setPreview(null); setError(null); }
    }, [open, contract, direction]);

    // Debounced backend preview as the quantity changes.
    useEffect(() => {
        if (!open || !contract) return;
        const q = Number(qty);
        if (!Number.isFinite(q) || q <= 0) { setPreview(null); return; }
        let cancelled = false;
        const id = setTimeout(() => {
            previewViopPosition(keycloak, {
                contractSymbol: contract.symbol, direction, quantity: q,
            })
                .then((p) => { if (!cancelled) setPreview(p); })
                .catch(() => { if (!cancelled) setPreview(null); });
        }, 250);
        return () => { cancelled = true; clearTimeout(id); };
    }, [open, contract, direction, qty, keycloak]);

    if (!contract) return null;
    const isLong = direction === "LONG";
    const title = isLong ? t("viopTrade.longOpen") : t("viopTrade.shortOpen");

    const submit = async () => {
        const q = Number(qty);
        if (!Number.isFinite(q) || q <= 0) { setError(t("viopTrade.invalidQty")); return; }
        setSubmitting(true); setError(null);
        try {
            await openViopPosition(keycloak, { contractSymbol: contract.symbol, direction, quantity: q });
            onDone?.();
            onClose();
        } catch (e) {
            setError(e?.response?.data?.message || e?.message || t("viopTrade.failed"));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Modal open={open} title={`${title} — ${contract.symbol}`} onClose={onClose} maxWidth={460}>
            <div style={s.wrap}>
                <SimulationDisclaimer risk="viop" />

                <div style={s.metaRow}>
                    <span style={s.metaLabel}>{t("viopTrade.underlying")}</span>
                    <span style={s.metaVal}>{contract.underlying}</span>
                </div>
                <div style={s.metaRow}>
                    <span style={s.metaLabel}>{t("viopTrade.lastPrice")}</span>
                    <span style={s.metaVal}>{fmt(contract.lastPrice)}</span>
                </div>

                <label style={s.label}>{t("viopTrade.contracts")}</label>
                <input
                    type="number" min="1" step="1" value={qty}
                    onChange={(e) => setQty(e.target.value)} style={s.input} autoFocus
                />

                {preview && (
                    <div style={s.preview}>
                        <Row k={t("viopTrade.positionSize")} v={`${fmt(preview.positionSize)} ${preview.currency}`} />
                        <Row k={t("viopTrade.requiredMargin")} v={`${fmt(preview.requiredMargin)} ${preview.currency}`} strong />
                        <Row k={t("viopTrade.leverage")} v={preview.leverage ? `${fmt(preview.leverage)}x` : "—"} />
                        <Row k={t("viopTrade.contractSize")} v={fmt(preview.contractSize, 0)} />
                        {preview.willCloseOpposite && (
                            <div style={s.flipNote}>↔ {t("viopTrade.willCloseOpposite")}</div>
                        )}
                    </div>
                )}

                {error && <div style={s.error}>{error}</div>}

                <button
                    type="button"
                    onClick={submit}
                    disabled={submitting}
                    style={{ ...s.submit, background: isLong ? "var(--accent-solid, #22c55e)" : "#dc2626", opacity: submitting ? 0.6 : 1 }}
                >
                    {submitting ? t("viopTrade.submitting") : title}
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

ViopTradeModal.propTypes = {
    open: PropTypes.bool,
    contract: PropTypes.object,
    direction: PropTypes.oneOf(["LONG", "SHORT"]),
    keycloak: PropTypes.object,
    onClose: PropTypes.func,
    onDone: PropTypes.func,
};

const s = {
    wrap: { display: "flex", flexDirection: "column", gap: 12 },
    metaRow: { display: "flex", justifyContent: "space-between", fontSize: 13 },
    metaLabel: { color: "var(--text-muted)" },
    metaVal: { color: "var(--text-primary)", fontWeight: 600 },
    label: { fontSize: 12, fontWeight: 600, color: "var(--text-muted)", marginTop: 4 },
    input: {
        padding: "10px 12px", borderRadius: 8, border: "1px solid var(--border-card)",
        background: "var(--input-bg)", color: "var(--text-primary)", fontSize: 15,
    },
    preview: {
        display: "flex", flexDirection: "column", gap: 6, padding: 12,
        borderRadius: 8, background: "var(--bg-card)", border: "1px solid var(--border-card)",
    },
    row: { display: "flex", justifyContent: "space-between", fontSize: 13 },
    rowK: { color: "var(--text-muted)" },
    rowV: { color: "var(--text-primary)" },
    flipNote: { marginTop: 4, fontSize: 12, color: "#d97706", fontWeight: 600 },
    error: { color: "#ef4444", fontSize: 13 },
    submit: {
        marginTop: 4, padding: "12px", borderRadius: 8, border: "none",
        color: "#fff", fontWeight: 700, fontSize: 14, cursor: "pointer",
    },
};

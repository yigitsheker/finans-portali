import { useEffect, useMemo, useState } from "react";
import PropTypes from "prop-types";
import Modal from "./Modal";
import { useI18n } from "../contexts/I18nContext";

const ymd = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
const todayLocalISO = () => ymd(new Date());
const yesterdayLocalISO = () => { const d = new Date(); d.setDate(d.getDate() - 1); return ymd(d); };

/**
 * Review-and-complete step before a bulk import. Shows the parsed rows in an
 * editable grid: missing required cells (lot, and date when requireDate) are
 * flagged red, unknown symbols amber ("will be skipped"), ready rows green.
 * The user may fill in the blanks before confirming. onConfirm receives the
 * (possibly edited) rows; the caller still re-validates and counts skips.
 */
export default function ImportPreviewModal({
    open, initialRows, requireDate, validSymbols, importing, onConfirm, onClose,
}) {
    const { t } = useI18n();
    const [rows, setRows] = useState([]);
    const today = todayLocalISO();
    const maxDate = yesterdayLocalISO(); // buy date must be in the past

    useEffect(() => {
        if (!open) return;
        setRows((initialRows || []).map((r, i) => ({
            key: i,
            symbol: r.symbol || "",
            lot: r.lot != null && !Number.isNaN(r.lot) ? String(r.lot) : "",
            date: r.date || "",
        })));
    }, [open, initialRows]);

    // An absent/empty catalog means we CANNOT validate — treat symbols as
    // unknown (amber) rather than falsely "ready", matching the import step
    // which skips anything not in the catalog.
    const knows = (sym) => !!validSymbols && validSymbols.has(sym);

    const statusOf = (r) => {
        const sym = (r.symbol || "").trim().toUpperCase();
        if (!sym) return { kind: "missing", label: t("import.missingSymbol") };
        if (!knows(sym)) return { kind: "unknown", label: t("import.unknownSymbol") };
        if (!(Number(r.lot) > 0)) return { kind: "missing", label: t("import.missingLot") };
        if (requireDate) {
            if (!r.date) return { kind: "missing", label: t("import.missingDate") };
            if (r.date >= today) return { kind: "missing", label: t("import.futureDate") };
        }
        return { kind: "ok", label: t("import.ready") };
    };

    const okCount = useMemo(
        () => rows.filter((r) => statusOf(r).kind === "ok").length,
        // eslint-disable-next-line react-hooks/exhaustive-deps
        [rows, requireDate, validSymbols],
    );
    const skipCount = rows.length - okCount;

    const setCell = (key, field, value) =>
        setRows((rs) => rs.map((r) => (r.key === key ? { ...r, [field]: value } : r)));

    const confirm = () => onConfirm(rows.map((r) => ({
        symbol: (r.symbol || "").trim().toUpperCase(),
        lot: Number(r.lot),
        date: r.date || null,
    })));

    const badgeStyle = (kind) =>
        kind === "ok" ? st.bOk : kind === "unknown" ? st.bWarn : st.bBad;
    const inputStyle = (bad) => (bad ? { ...st.input, ...st.inputBad } : st.input);

    return (
        <Modal
            open={open}
            title={t("import.previewTitle")}
            onClose={onClose}
            footer={
                <>
                    <button style={st.ghost} onClick={onClose} disabled={importing}>
                        {t("common.cancel")}
                    </button>
                    <button style={st.primary} onClick={confirm} disabled={importing || okCount === 0}>
                        {importing ? t("common.adding") : t("import.confirm", { n: okCount })}
                    </button>
                </>
            }
        >
            <div style={st.intro}>{t("import.intro")}</div>
            <div style={st.summary}>{t("import.summary", { ok: okCount, skip: skipCount })}</div>
            <div style={st.tableWrap}>
                <table style={st.table}>
                    <thead>
                        <tr>
                            <th style={st.th}>{t("import.colSymbol")}</th>
                            <th style={st.th}>{t("import.colLot")}</th>
                            {requireDate && <th style={st.th}>{t("import.colDate")}</th>}
                            <th style={st.th}>{t("import.colStatus")}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {rows.map((r) => {
                            const sym = (r.symbol || "").trim().toUpperCase();
                            const stt = statusOf(r);
                            return (
                                <tr key={r.key}>
                                    <td style={st.td}>
                                        <input
                                            value={r.symbol}
                                            onChange={(e) => setCell(r.key, "symbol", e.target.value.toUpperCase())}
                                            style={inputStyle(!sym || !knows(sym))}
                                        />
                                    </td>
                                    <td style={st.td}>
                                        <input
                                            type="number" min="0" step="any"
                                            value={r.lot}
                                            onChange={(e) => setCell(r.key, "lot", e.target.value)}
                                            style={inputStyle(!(Number(r.lot) > 0))}
                                        />
                                    </td>
                                    {requireDate && (
                                        <td style={st.td}>
                                            <input
                                                type="date" max={maxDate}
                                                value={r.date}
                                                onChange={(e) => setCell(r.key, "date", e.target.value)}
                                                style={inputStyle(!r.date || r.date >= today)}
                                            />
                                        </td>
                                    )}
                                    <td style={st.td}>
                                        <span style={{ ...st.badge, ...badgeStyle(stt.kind) }}>{stt.label}</span>
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </Modal>
    );
}

ImportPreviewModal.propTypes = {
    open: PropTypes.bool,
    initialRows: PropTypes.array,
    requireDate: PropTypes.bool,
    validSymbols: PropTypes.instanceOf(Set),
    importing: PropTypes.bool,
    onConfirm: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired,
};

const st = {
    intro: { fontSize: 13, color: "var(--text-muted)", marginBottom: 8 },
    summary: { fontSize: 12.5, fontWeight: 600, color: "var(--text-primary)", marginBottom: 10 },
    tableWrap: { maxHeight: "48vh", overflowY: "auto", border: "1px solid var(--border-card)", borderRadius: 8 },
    table: { width: "100%", borderCollapse: "collapse" },
    th: { position: "sticky", top: 0, textAlign: "left", padding: "8px 10px", fontSize: 11, fontWeight: 600, color: "var(--text-muted)", background: "var(--bg-card)", borderBottom: "1px solid var(--border)", whiteSpace: "nowrap" },
    td: { padding: "6px 10px", borderBottom: "1px solid var(--border-soft)" },
    input: { padding: "6px 8px", borderRadius: 6, border: "1px solid var(--input-border)", background: "var(--input-bg)", color: "var(--text-primary)", outline: "none", width: "100%", boxSizing: "border-box", fontSize: 13 },
    inputBad: { border: "1px solid var(--danger-border)", background: "var(--danger-bg)" },
    badge: { display: "inline-block", padding: "2px 8px", borderRadius: 999, fontSize: 11, fontWeight: 700, whiteSpace: "nowrap" },
    bOk: { background: "rgba(34,197,94,0.15)", color: "var(--green)" },
    bWarn: { background: "rgba(245,158,11,0.15)", color: "#f59e0b" },
    bBad: { background: "var(--danger-bg)", color: "var(--danger-text)" },
    ghost: { padding: "8px 16px", borderRadius: 8, border: "1px solid var(--border-card)", background: "transparent", color: "var(--text-primary)", cursor: "pointer" },
    primary: { padding: "8px 16px", borderRadius: 8, border: "none", background: "var(--accent-solid)", color: "#fff", cursor: "pointer", fontWeight: 600 },
};

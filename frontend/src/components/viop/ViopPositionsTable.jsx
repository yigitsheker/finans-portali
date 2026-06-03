import { useState } from "react";
import PropTypes from "prop-types";
import ViopCloseModal from "./ViopCloseModal";
import { useI18n } from "../../contexts/I18nContext";

const fmt = (v, d = 2) => {
    if (v == null) return "—";
    const n = Number(v);
    if (!Number.isFinite(n)) return "—";
    return n.toLocaleString("tr-TR", { minimumFractionDigits: d, maximumFractionDigits: d });
};
const pnlColor = (v) => (v == null || Number(v) === 0 ? "var(--text-muted)" : Number(v) > 0 ? "#16a34a" : "#dc2626");

/** Renders a user's OPEN VİOP positions with a Kapat (close/partial) action. */
export default function ViopPositionsTable({ positions, keycloak, onChanged }) {
    const { t } = useI18n();
    const [closeTarget, setCloseTarget] = useState(null);
    const open = (positions || []).filter((p) => p.status === "OPEN" && Number(p.quantity) > 0);

    if (open.length === 0) {
        return <div style={s.empty}>{t("viopTrade.noPositions")}</div>;
    }

    return (
        <div style={s.wrap} className="fp-table-scroll">
            <table style={s.table}>
                <thead>
                    <tr>
                        <th style={s.th}>{t("viopTrade.colContract")}</th>
                        <th style={s.th}>{t("viopTrade.colDirection")}</th>
                        <th style={s.thR}>{t("viopTrade.colQty")}</th>
                        <th style={s.thR}>{t("viopTrade.colEntry")}</th>
                        <th style={s.thR}>{t("viopTrade.colCurrent")}</th>
                        <th style={s.thR}>{t("viopTrade.colPositionSize")}</th>
                        <th style={s.thR}>{t("viopTrade.colMargin")}</th>
                        <th style={s.thR}>{t("viopTrade.colLeverage")}</th>
                        <th style={s.thR}>{t("viopTrade.colUnrealized")}</th>
                        <th style={s.thR}>{t("viopTrade.colAction")}</th>
                    </tr>
                </thead>
                <tbody>
                    {open.map((p) => (
                        <tr key={p.id} style={s.tr}>
                            <td style={s.tdBold}>{p.contractSymbol}</td>
                            <td>
                                <span style={{ ...s.badge, background: p.direction === "LONG" ? "rgba(22,163,74,0.15)" : "rgba(220,38,38,0.15)", color: p.direction === "LONG" ? "#16a34a" : "#dc2626" }}>
                                    {p.direction === "LONG" ? t("viopTrade.long") : t("viopTrade.short")}
                                </span>
                            </td>
                            <td style={s.tdR}>{fmt(p.quantity, 0)}</td>
                            <td style={s.tdR}>{fmt(p.entryPrice)}</td>
                            <td style={s.tdR}>{fmt(p.currentPrice)}</td>
                            <td style={s.tdR}>{fmt(p.positionSize)}</td>
                            <td style={s.tdR}>{fmt(p.requiredMargin)}</td>
                            <td style={s.tdR}>{p.leverage ? `${fmt(p.leverage)}x` : "—"}</td>
                            <td style={{ ...s.tdR, color: pnlColor(p.unrealizedPnl), fontWeight: 600 }}>{fmt(p.unrealizedPnl)}</td>
                            <td style={s.tdR}>
                                <button type="button" style={s.closeBtn} onClick={() => setCloseTarget(p)}>
                                    {t("viopTrade.closeBtn")}
                                </button>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>

            <ViopCloseModal
                open={!!closeTarget}
                position={closeTarget}
                keycloak={keycloak}
                onClose={() => setCloseTarget(null)}
                onDone={onChanged}
            />
        </div>
    );
}

ViopPositionsTable.propTypes = {
    positions: PropTypes.array,
    keycloak: PropTypes.object,
    onChanged: PropTypes.func,
};

const s = {
    wrap: { overflowX: "auto", border: "1px solid var(--border-card)", borderRadius: 8 },
    table: { width: "100%", borderCollapse: "collapse", fontSize: 12.5, minWidth: 880 },
    th: { padding: "10px 12px", textAlign: "left", fontWeight: 600, color: "var(--text-muted)", borderBottom: "1px solid var(--border-card)", whiteSpace: "nowrap", background: "var(--bg-card)" },
    thR: { padding: "10px 12px", textAlign: "right", fontWeight: 600, color: "var(--text-muted)", borderBottom: "1px solid var(--border-card)", whiteSpace: "nowrap", background: "var(--bg-card)" },
    tr: { borderBottom: "1px solid var(--border-card)" },
    tdBold: { padding: "10px 12px", color: "var(--text-primary)", fontWeight: 700, whiteSpace: "nowrap" },
    tdR: { padding: "10px 12px", textAlign: "right", color: "var(--text-primary)", whiteSpace: "nowrap", fontVariantNumeric: "tabular-nums" },
    badge: { padding: "2px 8px", borderRadius: 999, fontSize: 11, fontWeight: 700 },
    closeBtn: { padding: "5px 10px", borderRadius: 6, border: "1px solid var(--border-card)", background: "transparent", color: "var(--text-primary)", cursor: "pointer", fontSize: 12, fontWeight: 600 },
    empty: { padding: 20, textAlign: "center", color: "var(--text-muted)", fontSize: 13, fontStyle: "italic" },
};

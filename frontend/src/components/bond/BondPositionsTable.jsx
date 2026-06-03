import { useState } from "react";
import PropTypes from "prop-types";
import BondSellModal from "./BondSellModal";
import { useI18n } from "../../contexts/I18nContext";

const fmt = (v, d = 2) => {
    if (v == null) return "—";
    const n = Number(v);
    if (!Number.isFinite(n)) return "—";
    return n.toLocaleString("tr-TR", { minimumFractionDigits: d, maximumFractionDigits: d });
};
const pnlColor = (v) => (v == null || Number(v) === 0 ? "var(--text-muted)" : Number(v) > 0 ? "#16a34a" : "#dc2626");

/** A user's ACTIVE bond/bill positions with a Sat (sell) action. */
export default function BondPositionsTable({ positions, keycloak, onChanged }) {
    const { t } = useI18n();
    const [sellTarget, setSellTarget] = useState(null);
    const active = (positions || []).filter((p) => p.status === "ACTIVE" && Number(p.remainingNominal) > 0);

    if (active.length === 0) {
        return <div style={s.empty}>{t("bondTrade.noPositions")}</div>;
    }

    return (
        <div style={s.wrap} className="fp-table-scroll">
            <table style={s.table}>
                <thead>
                    <tr>
                        <th style={s.th}>{t("bondTrade.colIsin")}</th>
                        <th style={s.thR}>{t("bondTrade.colNominal")}</th>
                        <th style={s.thR}>{t("bondTrade.colAvgPrice")}</th>
                        <th style={s.thR}>{t("bondTrade.colDirty")}</th>
                        <th style={s.thR}>{t("bondTrade.colCoupon")}</th>
                        <th style={s.th}>{t("bondTrade.colMaturity")}</th>
                        <th style={s.thR}>{t("bondTrade.colValue")}</th>
                        <th style={s.thR}>{t("bondTrade.colUnrealized")}</th>
                        <th style={s.thR}>{t("bondTrade.colCouponIncome")}</th>
                        <th style={s.thR}>{t("bondTrade.colAction")}</th>
                    </tr>
                </thead>
                <tbody>
                    {active.map((p) => (
                        <tr key={p.id} style={s.tr}>
                            <td style={s.tdBold} title={p.name}>{p.isin}</td>
                            <td style={s.tdR}>{fmt(p.remainingNominal, 0)}</td>
                            <td style={s.tdR}>{fmt(p.avgCostPrice, 4)}</td>
                            <td style={s.tdR}>{fmt(p.currentDirtyPrice, 4)}{p.priceIsStale ? " *" : ""}</td>
                            <td style={s.tdR}>{p.couponRate != null ? `%${fmt(p.couponRate)}` : "—"}</td>
                            <td style={s.tdMuted}>{p.maturityDate || "—"}{p.daysToMaturity != null ? ` (${p.daysToMaturity}g)` : ""}</td>
                            <td style={s.tdR}>{fmt(p.currentValue)}</td>
                            <td style={{ ...s.tdR, color: pnlColor(p.unrealizedPnl), fontWeight: 600 }}>{fmt(p.unrealizedPnl)}</td>
                            <td style={{ ...s.tdR, color: Number(p.couponIncome) > 0 ? "#16a34a" : "var(--text-muted)" }}>{fmt(p.couponIncome)}</td>
                            <td style={s.tdR}>
                                <button type="button" style={s.sellBtn} onClick={() => setSellTarget(p)}>
                                    {t("bondTrade.sellBtn")}
                                </button>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>

            <BondSellModal
                open={!!sellTarget}
                position={sellTarget}
                keycloak={keycloak}
                onClose={() => setSellTarget(null)}
                onDone={onChanged}
            />
        </div>
    );
}

BondPositionsTable.propTypes = {
    positions: PropTypes.array,
    keycloak: PropTypes.object,
    onChanged: PropTypes.func,
};

const s = {
    wrap: { overflowX: "auto", border: "1px solid var(--border-card)", borderRadius: 8 },
    table: { width: "100%", borderCollapse: "collapse", fontSize: 12.5, minWidth: 920 },
    th: { padding: "10px 12px", textAlign: "left", fontWeight: 600, color: "var(--text-muted)", borderBottom: "1px solid var(--border-card)", whiteSpace: "nowrap", background: "var(--bg-card)" },
    thR: { padding: "10px 12px", textAlign: "right", fontWeight: 600, color: "var(--text-muted)", borderBottom: "1px solid var(--border-card)", whiteSpace: "nowrap", background: "var(--bg-card)" },
    tr: { borderBottom: "1px solid var(--border-card)" },
    tdBold: { padding: "10px 12px", color: "var(--text-primary)", fontWeight: 700, whiteSpace: "nowrap" },
    tdR: { padding: "10px 12px", textAlign: "right", color: "var(--text-primary)", whiteSpace: "nowrap", fontVariantNumeric: "tabular-nums" },
    tdMuted: { padding: "10px 12px", color: "var(--text-muted)", whiteSpace: "nowrap", fontSize: 12 },
    sellBtn: { padding: "5px 12px", borderRadius: 6, border: "none", background: "#dc2626", color: "#fff", cursor: "pointer", fontSize: 12, fontWeight: 700 },
    empty: { padding: 20, textAlign: "center", color: "var(--text-muted)", fontSize: 13, fontStyle: "italic" },
};

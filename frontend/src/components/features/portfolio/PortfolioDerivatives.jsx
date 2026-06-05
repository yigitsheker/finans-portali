import { useCallback, useEffect, useState } from "react";
import PropTypes from "prop-types";
import ViopPositionsTable from "../../viop/ViopPositionsTable";
import BondPositionsTable from "../../bond/BondPositionsTable";
import SimulationDisclaimer from "../../SimulationDisclaimer";
import { getViopPositions, getViopSummary } from "../../../api/viopTradeApi";
import { getBondPositions, getBondPortfolioSummary } from "../../../api/bondTradeApi";
import { useI18n } from "../../../contexts/I18nContext";

const fmt = (v, d = 2) => {
    if (v == null) return "—";
    const n = Number(v);
    if (!Number.isFinite(n)) return "—";
    return n.toLocaleString("tr-TR", { minimumFractionDigits: d, maximumFractionDigits: d });
};
const pnlColor = (v) => (v == null || Number(v) === 0 ? "var(--text-muted)" : Number(v) > 0 ? "var(--green)" : "var(--red)");

/**
 * VİOP and bond/bill positions on the portfolio page — shown SEPARATELY from
 * the qty×price holdings (different economics). Each section has its own
 * summary metrics + the mandatory simulation disclaimer. Sections render only
 * when the user actually has positions, to keep the page uncluttered.
 */
export default function PortfolioDerivatives({ keycloak }) {
    const { t } = useI18n();
    const [viop, setViop] = useState([]);
    const [viopSum, setViopSum] = useState(null);
    const [bonds, setBonds] = useState([]);
    const [bondSum, setBondSum] = useState(null);

    const reload = useCallback(() => {
        if (!keycloak?.authenticated) return;
        getViopPositions(keycloak).then(setViop).catch(() => setViop([]));
        getViopSummary(keycloak).then(setViopSum).catch(() => setViopSum(null));
        getBondPositions(keycloak).then(setBonds).catch(() => setBonds([]));
        getBondPortfolioSummary(keycloak).then(setBondSum).catch(() => setBondSum(null));
    }, [keycloak]);
    useEffect(() => { reload(); }, [reload]);

    const hasViop = viop.some((p) => p.status === "OPEN" && Number(p.quantity) > 0);
    const hasBonds = bonds.some((p) => p.status === "ACTIVE" && Number(p.remainingNominal) > 0);
    if (!hasViop && !hasBonds) return null;

    return (
        <div style={s.wrap}>
            {hasViop && (
                <section style={s.section}>
                    <h3 style={s.title}>{t("viopTrade.myPositions")}</h3>
                    {viopSum && (
                        <div style={s.metrics}>
                            <Metric label={t("viopTrade.colPositionSize")} value={fmt(viopSum.totalOpenPositionSize)} />
                            <Metric label={t("viopTrade.requiredMargin")} value={fmt(viopSum.totalRequiredMargin)} />
                            <Metric label={t("viopTrade.colUnrealized")} value={fmt(viopSum.totalUnrealizedPnl)} color={pnlColor(viopSum.totalUnrealizedPnl)} />
                            <Metric label={t("portfolioDeriv.realizedPnl")} value={fmt(viopSum.totalRealizedPnl)} color={pnlColor(viopSum.totalRealizedPnl)} />
                        </div>
                    )}
                    <SimulationDisclaimer risk="viop" style={{ marginBottom: 12 }} />
                    <ViopPositionsTable positions={viop} keycloak={keycloak} onChanged={reload} />
                </section>
            )}

            {hasBonds && (
                <section style={s.section}>
                    <h3 style={s.title}>{t("bondTrade.myPositions")}</h3>
                    {bondSum && (
                        <div style={s.metrics}>
                            <Metric label={t("portfolioDeriv.totalNominal")} value={fmt(bondSum.totalNominal)} />
                            <Metric label={t("portfolioDeriv.marketValue")} value={fmt(bondSum.currentMarketValue)} />
                            <Metric label={t("bondTrade.colUnrealized")} value={fmt(bondSum.totalUnrealizedPnl)} color={pnlColor(bondSum.totalUnrealizedPnl)} />
                            <Metric label={t("bondTrade.colCouponIncome")} value={fmt(bondSum.realizedCouponIncome)} color={Number(bondSum.realizedCouponIncome) > 0 ? "var(--green)" : "var(--text-muted)"} />
                        </div>
                    )}
                    <SimulationDisclaimer risk="bond" style={{ marginBottom: 12 }} />
                    <BondPositionsTable positions={bonds} keycloak={keycloak} onChanged={reload} />
                </section>
            )}
        </div>
    );
}

function Metric({ label, value, color }) {
    return (
        <div style={s.metric}>
            <div style={s.metricLabel}>{label}</div>
            <div style={{ ...s.metricValue, color: color || "var(--text-primary)" }}>{value}</div>
        </div>
    );
}
Metric.propTypes = { label: PropTypes.string, value: PropTypes.string, color: PropTypes.string };

PortfolioDerivatives.propTypes = { keycloak: PropTypes.object };

const s = {
    wrap: { display: "flex", flexDirection: "column", gap: 24, marginTop: 8 },
    section: { display: "flex", flexDirection: "column", gap: 10 },
    title: { fontSize: 18, fontWeight: 700, color: "var(--text-primary)", margin: 0 },
    metrics: { display: "flex", flexWrap: "wrap", gap: 16 },
    metric: { minWidth: 130, padding: "10px 14px", borderRadius: 8, background: "var(--bg-card)", border: "1px solid var(--border-card)" },
    metricLabel: { fontSize: 11, color: "var(--text-muted)" },
    metricValue: { fontSize: 16, fontWeight: 700, marginTop: 2, fontVariantNumeric: "tabular-nums" },
};

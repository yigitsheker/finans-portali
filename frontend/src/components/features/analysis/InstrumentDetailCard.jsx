import PropTypes from "prop-types";
import { useI18n } from "../../../contexts/I18nContext";

/**
 * Map an analysis summary (value/changeDaily/category) onto the shape
 * InstrumentChartModal expects ({ last, changePct, changeAbs, type, currency })
 * so its price header renders instead of showing "$—". The daily absolute
 * change is derived from the current value and the daily % (value − prevClose,
 * where prevClose = value / (1 + pct/100)).
 */
function toChartInstrument(sum) {
    const last = sum?.value != null ? Number(sum.value) : null;
    const pct = sum?.changeDaily != null ? Number(sum.changeDaily) : null;
    const changeAbs = last != null && pct != null ? last - last / (1 + pct / 100) : null;
    // Spread the original summary so downstream modals (Compare, AddPosition —
    // which reads `value`) keep their fields; add the market-shaped aliases the
    // chart modal's header needs (last / changePct / changeAbs / type).
    return {
        ...sum,
        type: sum?.category,
        last,
        changePct: pct,
        changeAbs,
    };
}

/**
 * Compact analysis-detail card shown when a row in InstrumentsTable is
 * clicked. Keeps everything in one column to fit the right-of-table layout;
 * the parent collapses it on mobile and swaps to a stacked layout below
 * the table.
 */
export default function InstrumentDetailCard({ detail, loading, error, onShowChart }) {
    const { t } = useI18n();
    if (loading) return <div style={s.state}>{t("analysis.detailLoading")}</div>;
    if (error) return <div style={{ ...s.state, color: "#dc2626" }}>{error}</div>;
    if (!detail) {
        return <div style={s.state}>{t("analysis.detailEmpty")}</div>;
    }

    const sum = detail.summary;

    // Backend emits raw enums (UP / SIDEWAYS / MEDIUM / SELL …). Map each
    // through t() so the Meta cells read in the active locale instead of
    // showing the wire format.
    const localizeTrend = (v) => {
        if (!v) return "—";
        switch (v) {
            case "UP": return t("analysis.trendUp");
            case "DOWN": return t("analysis.trendDown");
            case "SIDEWAYS": return t("analysis.trendSideways");
            default: return v;
        }
    };
    const localizeVolatility = (v) => {
        if (!v) return "—";
        switch (v) {
            case "LOW": return t("analysis.volLow");
            case "MEDIUM": return t("analysis.volMedium");
            case "HIGH": return t("analysis.volHigh");
            default: return v;
        }
    };
    const localizeRisk = (v) => {
        if (!v) return "—";
        switch (v) {
            case "LOW": return t("analysis.riskLow");
            case "MEDIUM": return t("analysis.riskMedium");
            case "HIGH": return t("analysis.riskHigh");
            default: return v;
        }
    };
    const localizeSignal = (v) => {
        if (!v) return "—";
        switch (v) {
            case "BUY": return t("analysis.signalBuy");
            case "HOLD": return t("analysis.signalHold");
            case "SELL": return t("analysis.signalSell");
            case "NEUTRAL": return t("analysis.signalNeutral");
            default: return v;
        }
    };
    return (
        <div style={s.card}>
            <div style={s.head}>
                <div>
                    <div style={s.name}>{sum?.name ?? "—"}</div>
                    <div style={s.symbol}>{sum?.symbol ?? "—"} · {sum?.category ?? "—"}</div>
                </div>
                {sum?.value != null && (
                    <div style={s.value}>
                        {Number(sum.value).toLocaleString("tr-TR", { maximumFractionDigits: 4 })}
                        {sum.currency && <span style={s.currency}> {sum.currency}</span>}
                    </div>
                )}
            </div>

            <div style={s.gridRow}>
                <Meta label={t("analysis.detailTrend")} value={localizeTrend(detail.trend)} />
                <Meta label={t("analysis.detailVolatility")} value={localizeVolatility(detail.volatility)} />
                <Meta label={t("analysis.detailRisk")} value={localizeRisk(sum?.riskLevel)} />
                <Meta label={t("analysis.detailShortSignal")} value={localizeSignal(sum?.shortTermSignal)} />
                <Meta label={t("analysis.detailLongSignal")} value={localizeSignal(sum?.longTermSignal)} />
            </div>

            <Section title={t("analysis.detailShortTerm")}>{detail.shortTermNote}</Section>
            <Section title={t("analysis.detailLongTerm")}>{detail.longTermNote}</Section>
            <Section title={t("analysis.detailRiskNote")}>{detail.riskNote}</Section>

            {onShowChart && sum?.symbol && (
                <button
                    type="button"
                    onClick={() => onShowChart(toChartInstrument(sum))}
                    style={s.chartBtn}
                >
                    {t("analysis.detailShowChart")}
                </button>
            )}
        </div>
    );
}

InstrumentDetailCard.propTypes = {
    detail: PropTypes.object,
    loading: PropTypes.bool,
    error: PropTypes.string,
    onShowChart: PropTypes.func,
};

function Meta({ label, value }) {
    return (
        <div style={s.meta}>
            <div style={s.metaLabel}>{label}</div>
            <div style={s.metaValue}>{value}</div>
        </div>
    );
}

Meta.propTypes = {
    label: PropTypes.node,
    value: PropTypes.node,
};

function Section({ title, children }) {
    if (!children) return null;
    return (
        <div style={s.section}>
            <div style={s.sectionTitle}>{title}</div>
            <div style={s.sectionBody}>{children}</div>
        </div>
    );
}

Section.propTypes = {
    title: PropTypes.node,
    children: PropTypes.node,
};

const s = {
    state: { padding: 24, textAlign: "center", color: "var(--text-muted)", fontSize: 13 },
    card: {
        display: "flex",
        flexDirection: "column",
        gap: 14,
        padding: 16,
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        background: "var(--bg-card)",
    },
    head: { display: "flex", justifyContent: "space-between", gap: 12, alignItems: "baseline" },
    name: { fontSize: 15, fontWeight: 700, color: "var(--text-primary)" },
    symbol: { fontSize: 11, color: "var(--text-muted)", marginTop: 2 },
    value: { fontSize: 18, fontWeight: 700, color: "var(--text-primary)" },
    currency: { fontSize: 12, fontWeight: 400, color: "var(--text-muted)" },
    gridRow: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(80px, 1fr))", gap: 8 },
    meta: {
        display: "flex",
        flexDirection: "column",
        gap: 2,
        padding: "8px 10px",
        background: "var(--bg-card-secondary, rgba(255,255,255,0.02))",
        border: "1px solid var(--border-card)",
        borderRadius: 6,
    },
    metaLabel: { fontSize: 10, color: "var(--text-muted)", textTransform: "uppercase", letterSpacing: 0.4 },
    metaValue: { fontSize: 12, fontWeight: 600, color: "var(--text-primary)" },
    section: { display: "flex", flexDirection: "column", gap: 4 },
    sectionTitle: { fontSize: 11, fontWeight: 700, color: "var(--text-muted)", textTransform: "uppercase", letterSpacing: 0.4 },
    sectionBody: { fontSize: 13, color: "var(--text-primary)", lineHeight: 1.45 },
    chartBtn: {
        alignSelf: "flex-start",
        padding: "8px 14px",
        fontSize: 13,
        fontWeight: 600,
        background: "var(--accent-solid)",
        color: "#fff",
        border: "none",
        borderRadius: 8,
        cursor: "pointer",
        marginTop: 4,
    },
};

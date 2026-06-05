import { useEffect, useState } from "react";
import PropTypes from "prop-types";
import Modal from "./Modal";
import { getBondDetail, getBondHistory } from "../api/bondApi";
import { LWAreaChart } from "./common/LWAreaChart";
import { useI18n } from "../contexts/I18nContext";

const TYPE_LABEL_KEYS = {
    GOVERNMENT_BOND: "bonds.typeGovBond",
    TREASURY_BILL: "bonds.typeTBill",
    LEASE_CERTIFICATE: "bonds.typeSukuk",
    EUROBOND: "Eurobond",
    CORPORATE_BOND: "bonds.typeCorp",
    OTHER: "bonds.typeOther",
};

export default function BondDetailModal({ bondId, onClose, onBuy }) {
    const { t } = useI18n();
    const [bond, setBond] = useState(null);
    const [history, setHistory] = useState([]);
    const [loading, setLoading] = useState(true);
    const [historyLoading, setHistoryLoading] = useState(false);
    const [error, setError] = useState(null);
    const [period, setPeriod] = useState("30D");

    useEffect(() => {
        loadBondDetail();
    }, [bondId]);

    useEffect(() => {
        if (bond) {
            loadHistory();
        }
    }, [bond, period]);

    async function loadBondDetail() {
        try {
            setLoading(true);
            setError(null);
            const data = await getBondDetail(bondId);
            setBond(data);
        } catch (e) {
            setError(e?.message ?? t("bondDetail.loadError"));
        } finally {
            setLoading(false);
        }
    }

    async function loadHistory() {
        if (!bond) return;

        try {
            setHistoryLoading(true);
            const to = new Date().toISOString().split("T")[0];
            const from = new Date();

            if (period === "30D") from.setDate(from.getDate() - 30);
            else if (period === "90D") from.setDate(from.getDate() - 90);
            else from.setFullYear(from.getFullYear() - 1);

            const fromStr = from.toISOString().split("T")[0];
            const data = await getBondHistory(bondId, fromStr, to);
            setHistory(data);
        } catch (e) {
            console.error("Failed to load history:", e);
            setHistory([]);
        } finally {
            setHistoryLoading(false);
        }
    }

    if (loading) {
        return (
            <Modal open={true} title={t("common.loading")} onClose={onClose}>
                <div style={s.loading}>{t("common.loading")}</div>
            </Modal>
        );
    }

    if (error || !bond) {
        return (
            <Modal open={true} title={t("common.error")} onClose={onClose}>
                <div style={s.error}>{error || t("bondDetail.notFound")}</div>
            </Modal>
        );
    }

    const positive = bond.changeRate >= 0;
    const changeColor = positive ? "var(--green)" : "var(--red)";

    return (
        <Modal
            open={true}
            title={`${bond.symbol} — ${bond.name}`}
            onClose={onClose}
            maxWidth={900}
        >
            <div style={s.content}>
                {/* Header Info */}
                <div style={s.headerRow}>
                    <div>
                        <div style={s.yieldLabel}>{t("bondDetail.yieldRate")}</div>
                        <div style={s.yieldValue}>
                            {bond.latestYieldRate ? `${bond.latestYieldRate.toFixed(2)}%` : "-"}
                        </div>
                    </div>
                    <div style={{ textAlign: "right" }}>
                        <div style={s.changeLabel}>{t("bondDetail.change")}</div>
                        <div style={{ ...s.changeValue, color: changeColor }}>
                            {bond.changeRate
                                ? `${positive ? "+" : ""}${bond.changeRate.toFixed(2)}%`
                                : "-"}
                        </div>
                    </div>
                </div>

                {/* Details Grid */}
                <div style={s.detailsGrid}>
                    <div style={s.detailCard}>
                        <div style={s.detailLabel}>{t("bonds.colType")}</div>
                        <div style={s.detailValue}>
                            {bond.type === "EUROBOND"
                                ? TYPE_LABEL_KEYS.EUROBOND
                                : TYPE_LABEL_KEYS[bond.type]
                                    ? t(TYPE_LABEL_KEYS[bond.type])
                                    : bond.type}
                        </div>
                    </div>
                    <div style={s.detailCard}>
                        <div style={s.detailLabel}>ISIN</div>
                        <div style={s.detailValue}>{bond.isin || "-"}</div>
                    </div>
                    <div style={s.detailCard}>
                        <div style={s.detailLabel}>{t("assetDetail.issuer")}</div>
                        <div style={s.detailValue}>{bond.issuer || "-"}</div>
                    </div>
                    <div style={s.detailCard}>
                        <div style={s.detailLabel}>{t("assetDetail.currency")}</div>
                        <div style={s.detailValue}>{bond.currency || "-"}</div>
                    </div>
                    <div style={s.detailCard}>
                        <div style={s.detailLabel}>{t("bondDetail.maturityDate")}</div>
                        <div style={s.detailValue}>
                            {bond.maturityDate
                                ? new Date(bond.maturityDate).toLocaleDateString("tr-TR")
                                : "-"}
                        </div>
                    </div>
                    <div style={s.detailCard}>
                        <div style={s.detailLabel}>{t("bondDetail.daysToMaturity")}</div>
                        <div style={s.detailValue}>{bond.daysToMaturity || "-"}</div>
                    </div>
                    <div style={s.detailCard}>
                        <div style={s.detailLabel}>{t("bondDetail.couponRate")}</div>
                        <div style={s.detailValue}>
                            {bond.couponRate ? `${bond.couponRate.toFixed(2)}%` : "-"}
                        </div>
                    </div>
                    <div style={s.detailCard}>
                        <div style={s.detailLabel}>{t("bondDetail.couponType")}</div>
                        <div style={s.detailValue}>{bond.couponType || "-"}</div>
                    </div>
                    <div style={s.detailCard}>
                        <div style={s.detailLabel}>{t("bonds.colPrice")}</div>
                        <div style={s.detailValue}>
                            {bond.latestPrice ? bond.latestPrice.toFixed(2) : "-"}
                        </div>
                    </div>
                    <div style={s.detailCard}>
                        <div style={s.detailLabel}>{t("bondDetail.colCleanPrice")}</div>
                        <div style={s.detailValue}>
                            {bond.cleanPrice ? bond.cleanPrice.toFixed(2) : "-"}
                        </div>
                    </div>
                    <div style={s.detailCard}>
                        <div style={s.detailLabel}>{t("bondDetail.colDirtyPrice")}</div>
                        <div style={s.detailValue}>
                            {bond.dirtyPrice ? bond.dirtyPrice.toFixed(2) : "-"}
                        </div>
                    </div>
                    <div style={s.detailCard}>
                        <div style={s.detailLabel}>{t("common.volume")}</div>
                        <div style={s.detailValue}>
                            {bond.volume ? bond.volume.toLocaleString("tr-TR") : "-"}
                        </div>
                    </div>
                </div>

                {/* Chart Section */}
                <div style={s.chartSection}>
                    <div style={s.chartHeader}>
                        <h3 style={s.chartTitle}>{t("bondDetail.chartTitle")}</h3>
                        <div style={s.periodButtons}>
                            {["30D", "90D", "1Y"].map((p) => (
                                <button
                                    key={p}
                                    style={{
                                        ...s.periodBtn,
                                        ...(period === p ? s.periodBtnActive : {}),
                                    }}
                                    onClick={() => setPeriod(p)}
                                >
                                    {p}
                                </button>
                            ))}
                        </div>
                    </div>

                    {historyLoading ? (
                        <div style={s.chartLoading}>{t("common.loading")}</div>
                    ) : history.length === 0 ? (
                        <div style={s.chartEmpty}>{t("bondDetail.chartEmpty")}</div>
                    ) : (
                        <div style={s.chartWrapper}>
                            <LWAreaChart
                                data={history.map((h) => ({
                                    time: new Date(h.date).getTime() / 1000,
                                    value: h.yieldRate || 0,
                                }))}
                                color="var(--accent-solid)"
                                height={250}
                            />
                        </div>
                    )}
                </div>

                {/* Action row — surfaces a "Al" button so the user can buy
                    the bond straight from the detail card. Parent decides
                    whether the user is authenticated. */}
                {onBuy && (
                    <div style={s.actionRow}>
                        <button
                            style={s.buyBtn}
                            onClick={() => onBuy({
                                symbol: bond.symbol,
                                price: bond.latestPrice ?? null,
                            })}
                        >
                            {t("bondDetail.buyAction")}
                        </button>
                    </div>
                )}

                {/* Footer */}
                <div style={s.footer}>
                    <span style={s.footerText}>
                        {t("bondDetail.footerSource")} {bond.source}
                    </span>
                    <span style={s.footerText}>
                        {t("bondDetail.footerLastUpdate")} {bond.lastUpdatedAt
                            ? new Date(bond.lastUpdatedAt).toLocaleString("tr-TR")
                            : "-"}
                    </span>
                </div>
            </div>
        </Modal>
    );
}

BondDetailModal.propTypes = {
    bondId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
    onClose: PropTypes.func.isRequired,
    onBuy: PropTypes.func,
};

const s = {
    content: { display: "flex", flexDirection: "column", gap: 20 },
    loading: { padding: "40px", textAlign: "center", color: "var(--text-muted)" },
    error: { padding: "20px", color: "var(--danger-text)", background: "var(--danger-bg)", borderRadius: 8 },
    headerRow: { display: "flex", justifyContent: "space-between", padding: "16px 20px", background: "var(--bg-panel)", borderRadius: 10 },
    yieldLabel: { fontSize: 12, color: "var(--text-muted)", marginBottom: 4 },
    yieldValue: { fontSize: 28, fontWeight: 800, color: "var(--text-primary)" },
    changeLabel: { fontSize: 12, color: "var(--text-muted)", marginBottom: 4 },
    changeValue: { fontSize: 18, fontWeight: 700 },
    detailsGrid: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 12 },
    detailCard: { padding: "12px 16px", background: "var(--bg-panel)", borderRadius: 8, border: "1px solid var(--border-card)" },
    detailLabel: { fontSize: 11, color: "var(--text-muted)", marginBottom: 4 },
    detailValue: { fontSize: 14, fontWeight: 600, color: "var(--text-primary)" },
    chartSection: { marginTop: 8 },
    chartHeader: { display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 },
    chartTitle: { margin: 0, fontSize: 16, fontWeight: 600, color: "var(--text-primary)" },
    periodButtons: { display: "flex", gap: 6 },
    periodBtn: {
        padding: "6px 12px",
        borderRadius: 6,
        border: "1px solid var(--border-card)",
        background: "var(--input-bg)",
        color: "var(--text-muted)",
        fontSize: 12,
        fontWeight: 500,
        cursor: "pointer",
    },
    periodBtnActive: {
        border: "1px solid var(--accent-solid)",
        background: "var(--accent)",
        color: "var(--accent-solid)",
    },
    chartWrapper: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-panel)", padding: 16 },
    chartLoading: { padding: "60px", textAlign: "center", color: "var(--text-muted)", fontSize: 13 },
    chartEmpty: { padding: "60px", textAlign: "center", color: "var(--text-muted)", fontSize: 13 },
    footer: { display: "flex", justifyContent: "space-between", paddingTop: 16, borderTop: "1px solid var(--border-card)" },
    footerText: { fontSize: 11, color: "var(--text-muted)" },
    actionRow: { display: "flex", justifyContent: "flex-end", marginTop: 4 },
    buyBtn: {
        padding: "10px 18px",
        borderRadius: 8,
        border: "none",
        background: "var(--accent-solid)",
        color: "#000",
        fontSize: 13,
        fontWeight: 700,
        cursor: "pointer",
        boxShadow: "0 2px 6px rgba(16, 185, 129, 0.3)",
    },
};

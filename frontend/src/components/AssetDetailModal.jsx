import { useEffect, useState } from "react";
import { IconLock } from "./common/icons";
import PropTypes from "prop-types";
import Modal from "./Modal";
import { LWAreaChart } from "./common/LWAreaChart";
import { getFxHistory } from "../api/portfolioApi";
import { useI18n } from "../contexts/I18nContext";

const PERIODS = [
    { labelKey: "assetDetail.p5G", value: "5D" },
    { labelKey: "assetDetail.p1A", value: "30D" },
    { labelKey: "assetDetail.p1Y", value: "1Y" },
];

// Fund chart horizons (days). The reconstructed NAV series is filtered to the
// selected horizon so funds get the same period switching stocks have.
const FUND_PERIODS = [
    { labelKey: "assetDetail.p1A", days: 30 },
    { labelKey: "assetDetail.p6A", days: 180 },
    { labelKey: "assetDetail.p1Y", days: 365 },
    { labelKey: "assetDetail.p5Y", days: 1825 },
];

/**
 * Generic asset detail card used on the Bonds, Funds and FX pages — the
 * stocks/crypto/commodities pages already have InstrumentChartModal which
 * is heavier (compare, alerts, watchlist tabs). This one is intentionally
 * lighter: a tile grid of metrics, a chart for FX, and a single "Al"
 * action that opens AddPositionModal.
 *
 * Props:
 *   - asset:    the row object the user clicked (shape depends on kind)
 *   - kind:     "FX" | "BOND" | "FUND"
 *   - onClose:  close handler
 *   - keycloak: keycloak instance (used by parent for auth-gated buy)
 *   - onBuy:    invoked with { symbol, price } when the user clicks Al
 */
export default function AssetDetailModal({ asset, kind, onClose, keycloak, onBuy }) {
    const { t } = useI18n();
    const [period, setPeriod] = useState("30D");
    const [fundPeriod, setFundPeriod] = useState(365); // fund chart horizon, in days
    const [chartData, setChartData] = useState([]);
    const [chartLoading, setChartLoading] = useState(false);

    // FX-only: fetch chart from Yahoo (USD → USDTRY=X etc.). Other kinds
    // don't render a chart panel so we skip the network entirely.
    useEffect(() => {
        if (!asset || kind !== "FX") return undefined;
        const code = asset.currencyCode;
        if (!code) return undefined;

        let cancelled = false;
        // Reset prior result and show the spinner. The lint rule flags
        // these as "cascading renders" but it's a single render cycle —
        // React batches the two setStates with the effect's mount. Same
        // pattern as InstrumentChartModal.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setChartLoading(true);
        setChartData([]);

        getFxHistory(code, period)
            .then((data) => {
                if (cancelled) return;
                setChartData(Array.isArray(data) ? data : []);
            })
            .catch((err) => {
                if (cancelled) return;
                console.error("FX history fetch failed:", err);
                setChartData([]);
            })
            .finally(() => {
                if (!cancelled) setChartLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [asset, kind, period]);

    if (!asset) return null;

    const title = buildTitle(asset, kind);
    // Funds have no historical NAV feed, but their reported period returns let
    // us reconstruct the implied price path: value N days ago = current ÷ (1+r).
    // fundFullSeries gates whether the panel shows at all; fundSeries is the
    // currently-selected horizon (1A / 6A / 1Y / 5Y).
    const fundFullSeries = kind === "FUND" ? buildFundSeries(asset, 1825) : [];
    const fundSeries = kind === "FUND" ? buildFundSeries(asset, fundPeriod) : [];
    const fundUp = (asset?.yearlyReturn ?? asset?.monthlyReturn ?? 0) >= 0;

    function handleBuy() {
        if (!onBuy) return;
        if (kind === "FX") {
            // Katalog FX sembolü parite formatındadır (CHF → CHFTRY).
            onBuy({ symbol: `${asset.currencyCode}TRY`, price: asset.sellingRate ?? null });
        } else if (kind === "BOND") {
            onBuy({ symbol: asset.symbol, price: asset.latestPrice ?? null });
        } else if (kind === "FUND") {
            onBuy({ symbol: asset.fundCode, price: asset.unitPrice ?? null });
        }
    }

    return (
        <Modal open={!!asset} title={title} onClose={onClose} maxWidth={840}>
            <div style={s.body}>
                {kind === "FX" && <FxBody asset={asset} t={t} />}
                {kind === "BOND" && <BondBody asset={asset} t={t} />}
                {kind === "FUND" && <FundBody asset={asset} t={t} />}

                {kind === "FUND" && fundFullSeries.length >= 2 && (
                    <div style={s.chartSection}>
                        <div style={s.chartHeader}>
                            <h3 style={s.chartTitle}>{t("assetDetail.fundChartTitle")}</h3>
                            <div style={s.periodGroup} role="tablist">
                                {FUND_PERIODS.map((p) => (
                                    <button
                                        key={p.days}
                                        role="tab"
                                        aria-selected={fundPeriod === p.days}
                                        style={{
                                            ...s.periodBtn,
                                            ...(fundPeriod === p.days ? s.periodActive : {}),
                                        }}
                                        onClick={() => setFundPeriod(p.days)}
                                    >
                                        {t(p.labelKey)}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div style={s.chartWrap}>
                            {fundSeries.length >= 2 ? (
                                <LWAreaChart
                                    data={fundSeries}
                                    color={fundUp ? "#10b981" : "#ef4444"}
                                    height={260}
                                />
                            ) : (
                                <div style={s.chartEmpty}>{t("assetDetail.chartUnavailable")}</div>
                            )}
                        </div>
                        <div style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 6 }}>
                            {t("assetDetail.fundChartNote")}
                        </div>
                    </div>
                )}

                {kind === "FX" && (
                    <div style={s.chartSection}>
                        <div style={s.chartHeader}>
                            <h3 style={s.chartTitle}>{t("assetDetail.chartTitle")}</h3>
                            <div style={s.periodGroup} role="tablist">
                                {PERIODS.map((p) => (
                                    <button
                                        key={p.value}
                                        role="tab"
                                        aria-selected={period === p.value}
                                        style={{
                                            ...s.periodBtn,
                                            ...(period === p.value ? s.periodActive : {}),
                                        }}
                                        onClick={() => setPeriod(p.value)}
                                    >
                                        {t(p.labelKey)}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div style={s.chartWrap}>
                            {chartLoading && (
                                <div style={s.chartLoading}>
                                    <div style={s.spinner} />
                                    <span>{t("common.loading")}</span>
                                </div>
                            )}
                            {!chartLoading && chartData.length === 0 && (
                                <div style={s.chartEmpty}>
                                    {t("assetDetail.chartUnavailable")}
                                </div>
                            )}
                            {!chartLoading && chartData.length > 0 && (
                                <LWAreaChart
                                    data={chartData.map((d) => ({ time: d.timestamp, value: Number(d.close) }))}
                                    color="#10b981"
                                    height={260}
                                />
                            )}
                        </div>
                    </div>
                )}

                <div style={s.actionRow}>
                    {keycloak?.authenticated ? (
                        <button style={s.primaryBtn} onClick={handleBuy}>
                            + {t("assetDetail.buyAction")}
                        </button>
                    ) : (
                        <button style={s.primaryBtn} onClick={handleBuy}>
                            <IconLock size={14} style={{ verticalAlign: "-2px", marginRight: 4 }} />{t("assetDetail.buyAction")}
                        </button>
                    )}
                </div>

                <div style={s.disclaimer}>
                    {t("assetDetail.disclaimer")}
                </div>
            </div>
        </Modal>
    );
}

/**
 * Reconstruct a fund's implied NAV path from its reported period returns.
 * Each return is relative to the latest unit price, so the value `days` ago is
 * current ÷ (1 + return/100). Produces ascending {time(unix sec), value} points
 * for LWAreaChart; missing horizons are skipped. Needs ≥2 points to draw.
 */
function buildFundSeries(asset, maxDays = 1825) {
    const base = Number(asset?.unitPrice);
    if (!base || Number.isNaN(base)) return [];
    const anchorMs = asset?.priceDate ? new Date(asset.priceDate).getTime() : Date.now();
    const nowSec = Math.floor(anchorMs / 1000);
    const DAY = 86400;
    const horizons = [
        { days: 1825, ret: asset.fiveYearReturn },
        { days: 1095, ret: asset.threeYearReturn },
        { days: 365, ret: asset.yearlyReturn },
        { days: 180, ret: asset.sixMonthReturn },
        { days: 90, ret: asset.threeMonthReturn },
        { days: 30, ret: asset.monthlyReturn },
        { days: 7, ret: asset.weeklyReturn },
        { days: 1, ret: asset.dailyReturn },
    ];
    const pts = [];
    for (const h of horizons) {
        if (h.days > maxDays) continue; // only points within the selected horizon
        const r = Number(h.ret);
        if (h.ret == null || Number.isNaN(r) || 1 + r / 100 === 0) continue;
        pts.push({ time: nowSec - h.days * DAY, value: base / (1 + r / 100) });
    }
    pts.push({ time: nowSec, value: base });
    return pts;
}

function buildTitle(asset, kind) {
    if (kind === "FX") {
        return `${asset.currencyCode} — ${asset.currencyName ?? ""}`.trim();
    }
    if (kind === "BOND") {
        return `${asset.symbol} — ${asset.name ?? ""}`.trim();
    }
    if (kind === "FUND") {
        return `${asset.fundCode} — ${asset.fundName ?? ""}`.trim();
    }
    return "";
}

function formatTry(value) {
    if (value === undefined || value === null || Number.isNaN(Number(value))) return "—";
    return "₺" + new Intl.NumberFormat("tr-TR", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 4,
    }).format(Number(value));
}

function formatDate(value) {
    if (!value) return "—";
    try {
        return new Date(value).toLocaleDateString("tr-TR");
    } catch {
        return String(value);
    }
}

function PercentCell({ value }) {
    if (value === undefined || value === null) {
        return <span style={{ color: "var(--text-muted)" }}>—</span>;
    }
    const positive = value >= 0;
    const color = positive ? "#10b981" : "#ef4444";
    const sign = positive ? "▲" : "▼";
    return (
        <span style={{ color, fontWeight: 700 }}>
            {sign} {positive ? "+" : ""}{Number(value).toFixed(2)}%
        </span>
    );
}

PercentCell.propTypes = {
    value: PropTypes.number,
};

function Tile({ label, children }) {
    return (
        <div style={s.tile}>
            <div style={s.tileLabel}>{label}</div>
            <div style={s.tileValue}>{children}</div>
        </div>
    );
}

Tile.propTypes = {
    label: PropTypes.node.isRequired,
    children: PropTypes.node,
};

function FxBody({ asset, t }) {
    return (
        <div style={s.tileGrid}>
            <Tile label={t("fx.bid")}>{formatTry(asset.buyingRate)}</Tile>
            <Tile label={t("fx.ask")}>{formatTry(asset.sellingRate)}</Tile>
            <Tile label={t("fx.bidEff")}>{formatTry(asset.effectiveBuyingRate)}</Tile>
            <Tile label={t("fx.askEff")}>{formatTry(asset.effectiveSellingRate)}</Tile>
            <Tile label={t("common.source")}>{asset.source ?? "—"}</Tile>
            <Tile label={t("assetDetail.date")}>{formatDate(asset.rateDate)}</Tile>
        </div>
    );
}
FxBody.propTypes = { asset: PropTypes.object.isRequired, t: PropTypes.func.isRequired };

function BondBody({ asset, t }) {
    return (
        <div style={s.tileGrid}>
            <Tile label={t("bonds.colType")}>{asset.type ?? "—"}</Tile>
            <Tile label={t("assetDetail.issuer")}>{asset.issuer ?? "—"}</Tile>
            <Tile label={t("assetDetail.currency")}>{asset.currency ?? "—"}</Tile>
            <Tile label={t("bonds.colMaturity")}>{formatDate(asset.maturityDate)}</Tile>
            <Tile label={t("bonds.colCoupon")}>
                {asset.couponRate != null ? `${Number(asset.couponRate).toFixed(2)}%` : "—"}
            </Tile>
            <Tile label={t("assetDetail.couponType")}>{asset.couponType ?? "—"}</Tile>
        </div>
    );
}
BondBody.propTypes = { asset: PropTypes.object.isRequired, t: PropTypes.func.isRequired };

function FundBody({ asset, t }) {
    return (
        <>
            {asset.managementCompany && (
                <div style={s.fundCaption}>{asset.managementCompany}</div>
            )}
            <div style={s.tileGrid}>
                <Tile label={t("funds.colUnitPrice")}>{formatTry(asset.unitPrice)}</Tile>
                <Tile label={t("assetDetail.date")}>{formatDate(asset.priceDate)}</Tile>
                <Tile label={t("funds.colRisk")}>{asset.riskLevel ?? "—"}</Tile>
                <Tile label={t("assetDetail.fundType")}>{asset.fundType ?? "—"}</Tile>
            </div>

            <div style={{ ...s.tileGrid, marginTop: 12 }}>
                <Tile label={t("funds.colDaily")}><PercentCell value={asset.dailyReturn} /></Tile>
                <Tile label={t("assetDetail.weekly")}><PercentCell value={asset.weeklyReturn} /></Tile>
                <Tile label={t("funds.col1m")}><PercentCell value={asset.monthlyReturn} /></Tile>
                <Tile label={t("funds.col3m")}><PercentCell value={asset.threeMonthReturn} /></Tile>
                <Tile label={t("funds.col6m")}><PercentCell value={asset.sixMonthReturn} /></Tile>
                <Tile label={t("funds.col1y")}><PercentCell value={asset.yearlyReturn} /></Tile>
                <Tile label={t("funds.col3y")}><PercentCell value={asset.threeYearReturn} /></Tile>
                <Tile label={t("funds.col5y")}><PercentCell value={asset.fiveYearReturn} /></Tile>
            </div>
        </>
    );
}
FundBody.propTypes = { asset: PropTypes.object.isRequired, t: PropTypes.func.isRequired };

AssetDetailModal.propTypes = {
    asset: PropTypes.object,
    kind: PropTypes.oneOf(["FX", "BOND", "FUND"]).isRequired,
    onClose: PropTypes.func.isRequired,
    keycloak: PropTypes.object,
    onBuy: PropTypes.func,
};

const s = {
    body: {
        display: "flex",
        flexDirection: "column",
        gap: 16,
    },
    tileGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
        gap: 10,
    },
    tile: {
        padding: "12px 14px",
        borderRadius: 10,
        background: "var(--bg-panel)",
        border: "1px solid var(--border-card)",
        display: "flex",
        flexDirection: "column",
        gap: 4,
        minHeight: 60,
    },
    tileLabel: {
        fontSize: 11,
        color: "var(--text-muted)",
        textTransform: "uppercase",
        letterSpacing: 0.4,
    },
    tileValue: {
        fontSize: 14,
        fontWeight: 700,
        color: "var(--text-primary)",
        fontVariantNumeric: "tabular-nums",
    },
    fundCaption: {
        fontSize: 12,
        color: "var(--text-muted)",
        marginTop: -4,
    },
    chartSection: {
        marginTop: 4,
    },
    chartHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: 12,
        flexWrap: "wrap",
        marginBottom: 8,
    },
    chartTitle: {
        margin: 0,
        fontSize: 14,
        fontWeight: 700,
        color: "var(--text-primary)",
    },
    periodGroup: {
        display: "inline-flex",
        background: "var(--bg-panel)",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        padding: 3,
        gap: 2,
    },
    periodBtn: {
        padding: "5px 12px",
        borderRadius: 6,
        border: "1px solid transparent",
        background: "transparent",
        color: "var(--text-muted)",
        cursor: "pointer",
        fontSize: 12,
        fontWeight: 600,
    },
    periodActive: {
        background: "#10b981",
        color: "#000",
        border: "1px solid #10b981",
    },
    chartWrap: {
        borderRadius: 10,
        border: "1px solid var(--border-card)",
        background: "var(--bg-panel)",
        padding: 12,
        minHeight: 260,
    },
    chartLoading: {
        minHeight: 240,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 8,
        color: "var(--text-muted)",
        fontSize: 12,
    },
    chartEmpty: {
        minHeight: 240,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        color: "var(--text-muted)",
        fontSize: 13,
    },
    spinner: {
        width: 24,
        height: 24,
        border: "3px solid var(--border-card)",
        borderTopColor: "#10b981",
        borderRadius: "50%",
        animation: "spin 0.8s linear infinite",
    },
    actionRow: {
        display: "flex",
        justifyContent: "flex-end",
        gap: 10,
        marginTop: 4,
    },
    primaryBtn: {
        padding: "10px 18px",
        borderRadius: 8,
        border: "none",
        background: "#10b981",
        color: "#000",
        fontSize: 13,
        fontWeight: 700,
        cursor: "pointer",
        boxShadow: "0 2px 6px rgba(16, 185, 129, 0.3)",
    },
    disclaimer: {
        fontSize: 11,
        color: "var(--text-muted)",
        textAlign: "center",
        paddingTop: 8,
        borderTop: "1px solid var(--border-card)",
        opacity: 0.85,
    },
};

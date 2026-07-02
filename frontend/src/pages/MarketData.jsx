import { useState, useEffect, useMemo, useCallback } from 'react';
import { IconAlertTriangle, IconExchange } from "../components/common/icons";
import PropTypes from "prop-types";
import { getExchangeRates } from '../api/portfolioApi';
import CurrencyConverter from '../components/CurrencyConverter';
import DataFreshnessHeader from "../components/common/DataFreshnessHeader";
import TermInfo from "../components/common/TermInfo";
import AssetDetailModal from "../components/AssetDetailModal";
import BuyModalMount from "../components/BuyModalMount";
import { clickable } from "../utils/clickable";
import { useI18n } from "../contexts/I18nContext";
import { useBuyTarget } from "../hooks/useBuyTarget";

const MarketData = ({ keycloak, onAdded }) => {
    const { t } = useI18n();
    const [exchangeRates, setExchangeRates] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    // Detail modal target (full asset row); separate from buy target so the
    // buy modal can open from a row-action click without first showing the
    // detail card. Buy target + auth guard live in the shared hook.
    const [selected, setSelected] = useState(null);
    const [buyTarget, openBuy, clearBuy] = useBuyTarget(keycloak);
    // Header-driven sort. Default null = natural order from the API.
    const [sortKey, setSortKey] = useState(null);
    const [sortDir, setSortDir] = useState("asc");

    const toggleSort = useCallback((key) => {
        setSortKey((prevKey) => {
            if (prevKey === key) {
                setSortDir((d) => (d === "asc" ? "desc" : "asc"));
                return prevKey;
            }
            // Currency name / code → asc; numeric rates → desc.
            setSortDir(["currencyName", "currencyCode", "source"].includes(key) ? "asc" : "desc");
            return key;
        });
    }, []);

    const sortedRates = useMemo(() => {
        if (!sortKey) return exchangeRates;
        const stringKeys = new Set(["currencyName", "currencyCode", "source"]);
        return [...exchangeRates].sort((a, b) => {
            const av = a[sortKey];
            const bv = b[sortKey];
            let cmp;
            if (stringKeys.has(sortKey)) {
                cmp = (av || "").localeCompare(bv || "", "tr", { sensitivity: "base" });
            } else {
                cmp = Number(av ?? -Infinity) - Number(bv ?? -Infinity);
            }
            return sortDir === "asc" ? cmp : -cmp;
        });
    }, [exchangeRates, sortKey, sortDir]);

    // Per-column sort-direction arrows. Extracted out of JSX so each header
    // cell isn't a triple-nested ternary (Sonar S3358).
    const sortArrow = (key) => {
        if (sortKey !== key) return "";
        return sortDir === "asc" ? "▲" : "▼";
    };
    const sortArrowSpaced = (key) => {
        if (sortKey !== key) return "";
        return sortDir === "asc" ? " ▲" : " ▼";
    };

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            setLoading(true);
            setError(null);
            // Sadece döviz kurlarını çek - yatırım fonları artık ayrı sayfada
            const ratesData = await getExchangeRates();

            setExchangeRates(ratesData);
        } catch (error) {
            console.error('Error loading market data:', error);
            setError('Veriler yüklenirken bir hata oluştu. Lütfen daha sonra tekrar deneyin.');
        } finally {
            setLoading(false);
        }
    };

    const formatCurrency = (value) => {
        return '₺' + new Intl.NumberFormat('tr-TR', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 4
        }).format(value);
    };

    if (loading) {
        return (
            <div style={s.loading}>
                <div style={s.spinner}></div>
                <div style={{ color: "var(--text-muted)", marginTop: 12 }}>{t("common.loading")}</div>
            </div>
        );
    }

    if (error) {
        return (
            <div style={s.error}>
                <div style={{ marginBottom: 16 }}><IconAlertTriangle size={48} /></div>
                <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>{t("common.error")}</div>
                <div style={{ color: "var(--text-muted)", fontSize: 13, marginBottom: 16 }}>{error}</div>
                <button style={s.retryBtn} onClick={loadData}>
                    {t("common.retry")}
                </button>
            </div>
        );
    }

    // Freshest rateDate across all FX rows — surfaced as the "Son güncelleme"
    // chip. TCMB ships daily rates so we expect this to bump once per day.
    const asOf = exchangeRates.reduce((freshest, r) => {
        const d = r?.rateDate ? new Date(r.rateDate).getTime() : 0;
        return d > freshest ? d : freshest;
    }, 0);

    return (
        <div style={s.root}>
            <h1 style={s.pageTitle}>{t("nav.fxFull")} <TermInfo termKey="cross_rate" placement="bottom" /></h1>
            <DataFreshnessHeader
                asOf={asOf > 0 ? new Date(asOf) : null}
                onRefresh={loadData}
                refreshing={loading}
            />

            {/* Live FX converter — sits above the table for quick access */}
            <CurrencyConverter rates={exchangeRates} />

            {/* Exchange Rates Table */}
            <div style={s.tableContainer} className="fp-card-table">
                {/* Table Header */}
                <div style={{ ...s.tableHeader, ...s.tableHeaderExchange }}>
                    <div style={{ ...s.colCurrency, cursor: "pointer" }} {...clickable(() => toggleSort("currencyName"))}>
                        {t("fx.currency")} {sortArrow("currencyName")}
                    </div>
                    <div style={{ ...s.colRate, cursor: "pointer" }} {...clickable(() => toggleSort("buyingRate"))}>
                        {t("fx.bid")} <TermInfo termKey="forex_bid" placement="bottom" />
                        {sortArrowSpaced("buyingRate")}
                    </div>
                    <div style={{ ...s.colRate, cursor: "pointer" }} {...clickable(() => toggleSort("sellingRate"))}>
                        {t("fx.ask")} <TermInfo termKey="forex_ask" placement="bottom" />
                        {sortArrowSpaced("sellingRate")}
                    </div>
                    <div style={{ ...s.colRate, cursor: "pointer" }} {...clickable(() => toggleSort("effectiveBuyingRate"))}>
                        {t("fx.bidEff")} <TermInfo termKey="forex_effective" placement="bottom" />
                        {sortArrowSpaced("effectiveBuyingRate")}
                    </div>
                    <div style={{ ...s.colRate, cursor: "pointer" }} {...clickable(() => toggleSort("effectiveSellingRate"))}>
                        {t("fx.askEff")} <TermInfo termKey="forex_effective" placement="bottom" />
                        {sortArrowSpaced("effectiveSellingRate")}
                    </div>
                    <div style={{ ...s.colSource, cursor: "pointer" }} {...clickable(() => toggleSort("source"))}>
                        {t("common.source")} {sortArrow("source")}
                    </div>
                    <div style={s.colAction}>{t("fx.actionsCol")}</div>
                </div>

                {/* Table Body */}
                <div style={s.tableBody}>
                    {sortedRates.length === 0 ? (
                        <div style={s.emptyState}>
                            <div style={{ marginBottom: 12 }}><IconExchange size={48} /></div>
                            <div style={{ color: "var(--text-muted)", fontSize: 13 }}>
                                {t("fx.empty")}
                            </div>
                        </div>
                    ) : (
                        sortedRates.map((rate) => (
                            <div
                                key={rate.id}
                                style={{ ...s.tableRow, ...s.tableRowExchange }}
                                {...clickable(() => setSelected(rate))}
                            >
                                <div style={s.colCurrency}>
                                    <div style={s.currencyIcon}>
                                        {rate.currencyCode.substring(0, 2)}
                                    </div>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                                        <div style={s.currencyCode}>{rate.currencyCode}</div>
                                        <div style={s.currencyName}>{rate.currencyName}</div>
                                    </div>
                                </div>
                                <div style={s.colRate}>
                                    <div style={s.rateValue}>{formatCurrency(rate.buyingRate)}</div>
                                </div>
                                <div style={s.colRate}>
                                    <div style={s.rateValue}>{formatCurrency(rate.sellingRate)}</div>
                                </div>
                                <div style={s.colRate}>
                                    <div style={s.rateValueSecondary}>{formatCurrency(rate.effectiveBuyingRate)}</div>
                                </div>
                                <div style={s.colRate}>
                                    <div style={s.rateValueSecondary}>{formatCurrency(rate.effectiveSellingRate)}</div>
                                </div>
                                <div style={s.colSource}>
                                    <span style={s.sourceBadge}>{rate.source}</span>
                                </div>
                                <div style={s.colAction}>
                                    <button
                                        style={s.actionBtn}
                                        onClick={(e) => {
                                            // Stop the row click from also opening the detail modal.
                                            e.stopPropagation();
                                            openBuy({
                                                // Katalog FX sembolü parite formatındadır (CHF → CHFTRY);
                                                // çıplak ISO kodu backend'de "Unknown symbol" hatası verir.
                                                symbol: `${rate.currencyCode}TRY`,
                                                price: rate.sellingRate,
                                            });
                                        }}
                                    >
                                        {t("common.buy")}
                                    </button>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>

            {/* Detail modal — opens when a row is clicked. The user can hit
                "Al" from inside, or from the per-row action button. */}
            <AssetDetailModal
                asset={selected}
                kind="FX"
                onClose={() => setSelected(null)}
                keycloak={keycloak}
                onBuy={(payload) => {
                    setSelected(null);
                    openBuy(payload);
                }}
            />

            {/* Buy modal — seeded with the FX code and TCMB selling rate so
                the user only has to confirm the quantity. */}
            <BuyModalMount target={buyTarget} clear={clearBuy} keycloak={keycloak} onAdded={onAdded} />
        </div>
    );
};

MarketData.propTypes = {
    keycloak: PropTypes.object,
    onAdded: PropTypes.func,
};

const s = {
    root: { display: "flex", flexDirection: "column", gap: 16 },
    pageTitle: { fontSize: 24, fontWeight: 700, margin: 0, color: "var(--text-primary)" },
    loading: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: 400,
    },
    spinner: {
        width: 40,
        height: 40,
        border: "3px solid var(--border)",
        borderTop: "3px solid var(--blue)",
        borderRadius: "50%",
        animation: "spin 0.8s linear infinite",
    },
    error: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: 400,
        color: "var(--text-primary)",
    },
    retryBtn: {
        padding: "10px 20px",
        borderRadius: 8,
        border: "none",
        background: "#10b981",
        color: "#000",
        fontSize: 14,
        fontWeight: 700,
        cursor: "pointer",
        transition: "all 0.2s",
    },
    tableContainer: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        overflow: "hidden",
    },
    tableHeader: {
        display: "grid",
        gap: 16,
        padding: "14px 20px",
        background: "var(--bg-panel)",
        borderBottom: "1px solid var(--border-card)",
        fontSize: 11,
        fontWeight: 600,
        color: "var(--text-muted)",
        textTransform: "uppercase",
    },
    tableHeaderExchange: {
        // Fixed pixel tracks for every column except the currency one
        // (minmax(0, 2fr) absorbs slack). Without this each row's fr
        // columns sized to its own price min-content, so a long banknote
        // value like "₺34,3389" got a wider cell than "₺7,1932" and the
        // KAYNAK badge column drifted out of alignment across rows. See
        // FinexStyleMarket for the same fix.
        gridTemplateColumns: "minmax(0, 2fr) 90px 90px 90px 90px 70px 80px",
    },
    tableBody: {
        display: "flex",
        flexDirection: "column",
    },
    tableRow: {
        display: "grid",
        gap: 16,
        padding: "14px 20px",
        borderBottom: "1px solid var(--border-card)",
        cursor: "pointer",
        transition: "background 0.2s",
    },
    tableRowExchange: {
        // Fixed pixel tracks for every column except the currency one
        // (minmax(0, 2fr) absorbs slack). Without this each row's fr
        // columns sized to its own price min-content, so a long banknote
        // value like "₺34,3389" got a wider cell than "₺7,1932" and the
        // KAYNAK badge column drifted out of alignment across rows. See
        // FinexStyleMarket for the same fix.
        gridTemplateColumns: "minmax(0, 2fr) 90px 90px 90px 90px 70px 80px",
    },
    emptyState: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "60px 20px",
    },
    // Exchange Rate Columns
    colCurrency: { display: "flex", alignItems: "center", gap: 12 },
    colRate: { display: "flex", alignItems: "center", justifyContent: "flex-end" },
    colSource: { display: "flex", alignItems: "center", justifyContent: "center" },
    colAction: { display: "flex", alignItems: "center", justifyContent: "flex-end" },
    // Matches FinexStyleMarket.actionBtn so the affordance reads the same
    // across all market pages.
    actionBtn: {
        padding: "6px 14px",
        borderRadius: 6,
        border: "none",
        background: "#10b981",
        color: "#000",
        fontSize: 12,
        fontWeight: 700,
        cursor: "pointer",
        boxShadow: "0 2px 6px rgba(16, 185, 129, 0.3)",
    },
    currencyIcon: {
        width: 40,
        height: 40,
        borderRadius: "50%",
        background: "rgba(16, 185, 129, 0.15)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: 14,
        fontWeight: 700,
        color: "#10b981",
        flexShrink: 0,
    },
    currencyCode: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    currencyName: { fontSize: 10, color: "var(--text-muted)" },
    rateValue: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    rateValueSecondary: { fontSize: 12, color: "var(--text-muted)" },
    sourceBadge: {
        padding: "4px 12px",
        borderRadius: 12,
        background: "rgba(59, 130, 246, 0.15)",
        color: "#3b82f6",
        fontSize: 11,
        fontWeight: 600,
    },
};

export default MarketData;

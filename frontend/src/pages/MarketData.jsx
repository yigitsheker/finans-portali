import { useState, useEffect, useMemo } from 'react';
import { getExchangeRates } from '../api/portfolioApi';
import CurrencyConverter from '../components/CurrencyConverter';
import DataFreshnessHeader from "../components/common/DataFreshnessHeader";
import { useI18n } from "../contexts/I18nContext";

const MarketData = () => {
    const { t } = useI18n();
    const [exchangeRates, setExchangeRates] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

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
                <div style={{ fontSize: 48, marginBottom: 16 }}>⚠️</div>
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
            <DataFreshnessHeader
                asOf={asOf > 0 ? new Date(asOf) : null}
                onRefresh={loadData}
                refreshing={loading}
            />

            {/* Live FX converter — sits above the table for quick access */}
            <CurrencyConverter rates={exchangeRates} />

            {/* Exchange Rates Table */}
            <div style={s.tableContainer}>
                {/* Table Header */}
                <div style={{ ...s.tableHeader, ...s.tableHeaderExchange }}>
                    <div style={s.colCurrency}>{t("fx.currency")}</div>
                    <div style={s.colRate}>{t("fx.bid")}</div>
                    <div style={s.colRate}>{t("fx.ask")}</div>
                    <div style={s.colRate}>{t("fx.bidEff")}</div>
                    <div style={s.colRate}>{t("fx.askEff")}</div>
                    <div style={s.colSource}>{t("common.source")}</div>
                </div>

                {/* Table Body */}
                <div style={s.tableBody}>
                    {exchangeRates.length === 0 ? (
                        <div style={s.emptyState}>
                            <div style={{ fontSize: 48, marginBottom: 12 }}>💱</div>
                            <div style={{ color: "var(--text-muted)", fontSize: 13 }}>
                                {t("fx.empty")}
                            </div>
                        </div>
                    ) : (
                        exchangeRates.map((rate) => (
                            <div key={rate.id} style={{ ...s.tableRow, ...s.tableRowExchange }}>
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
                            </div>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
};

const s = {
    root: { display: "flex", flexDirection: "column", gap: 16 },
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
        borderTop: "3px solid #3b82f6",
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
        gridTemplateColumns: "2fr 1fr 1fr 1fr 1fr auto",
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
        gridTemplateColumns: "2fr 1fr 1fr 1fr 1fr auto",
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

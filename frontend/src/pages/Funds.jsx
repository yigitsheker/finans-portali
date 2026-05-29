import { useState, useEffect, useMemo } from "react";
import { getInvestmentFunds, getFundTypes, refreshInvestmentFunds } from "../api/portfolioApi";
import CheckboxFilterGroup from "../components/common/CheckboxFilterGroup";
import Pagination from "../components/common/Pagination";
import TermInfo from "../components/common/TermInfo";
import { useI18n } from "../contexts/I18nContext";

export default function Funds({ keycloak }) {
    const { t } = useI18n();
    const [funds, setFunds] = useState([]);
    const [fundTypes, setFundTypes] = useState([]);
    // Multi-select fund-type filter; empty array = "all types".
    const [selectedFundTypes, setSelectedFundTypes] = useState([]);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    // Header sort state. `sortKey` is the field on InvestmentFund (e.g.
    // "fundName", "unitPrice", "yearlyReturn"); null = natural API order.
    const [sortKey, setSortKey] = useState(null);
    const [sortDir, setSortDir] = useState("asc");
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState(null);
    const [lastUpdate, setLastUpdate] = useState(null);

    // Check if user is admin
    const isAdmin = keycloak.hasRealmRole('ADMIN');

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            setLoading(true);
            setError(null);
            const [fundsData, typesData] = await Promise.all([
                getInvestmentFunds(),
                getFundTypes()
            ]);

            setFunds(fundsData);
            setFundTypes(typesData);

            // Get last update time from the most recent fund
            if (fundsData.length > 0 && fundsData[0].priceDate) {
                setLastUpdate(fundsData[0].priceDate);
            }
        } catch (error) {
            console.error('Error loading funds:', error);
            setError('Veriler yüklenirken bir hata oluştu. Lütfen daha sonra tekrar deneyin.');
        } finally {
            setLoading(false);
        }
    };

    const handleRefresh = async () => {
        if (!isAdmin) return;

        try {
            setRefreshing(true);
            setError(null);

            const result = await refreshInvestmentFunds(keycloak.token);

            if (result.success) {
                // Reload data after successful refresh
                await loadData();
                alert(`Başarılı! ${result.fundsAdded} yeni fon eklendi. Toplam: ${result.fundsAfter} fon.`);
            } else {
                setError(result.message || 'Güncelleme başarısız oldu.');
            }
        } catch (error) {
            console.error('Error refreshing funds:', error);
            setError('Güncelleme sırasında bir hata oluştu.');
        } finally {
            setRefreshing(false);
        }
    };

    // Filter + paginate. Counts are per-type so the checkbox chips can
    // show "Hisse 24" / "Para Piyasası 17" badges without a separate query.
    const typeCounts = useMemo(() => {
        const out = {};
        for (const f of funds) {
            if (!f.fundType) continue;
            out[f.fundType] = (out[f.fundType] || 0) + 1;
        }
        return out;
    }, [funds]);

    const filteredFunds = useMemo(() => {
        let rows = funds;
        if (selectedFundTypes.length > 0) {
            const set = new Set(selectedFundTypes);
            rows = rows.filter((f) => set.has(f.fundType));
        }
        if (sortKey) {
            // String columns (fund name / code) sort with Turkish locale,
            // numeric returns/prices fall back to numeric subtraction.
            const isString = sortKey === "fundName" || sortKey === "fundCode" || sortKey === "fundType";
            rows = [...rows].sort((a, b) => {
                const av = a[sortKey];
                const bv = b[sortKey];
                let cmp;
                if (isString) {
                    cmp = (av || "").localeCompare(bv || "", "tr", { sensitivity: "base" });
                } else {
                    cmp = Number(av ?? -Infinity) - Number(bv ?? -Infinity);
                }
                return sortDir === "asc" ? cmp : -cmp;
            });
        }
        return rows;
    }, [funds, selectedFundTypes, sortKey, sortDir]);

    const toggleSort = (key) => {
        if (sortKey === key) {
            setSortDir((d) => (d === "asc" ? "desc" : "asc"));
        } else {
            setSortKey(key);
            setSortDir(key === "fundName" || key === "fundCode" ? "asc" : "desc");
        }
    };

    useEffect(() => { setPage(1); }, [selectedFundTypes, pageSize, sortKey, sortDir]);
    const totalFiltered = filteredFunds.length;
    const pagedFunds = useMemo(() => {
        const start = (page - 1) * pageSize;
        return filteredFunds.slice(start, start + pageSize);
    }, [filteredFunds, page, pageSize]);

    const formatCurrency = (value) => {
        if (value === undefined || value === null || Number(value) === 0) return '—';
        return '₺' + new Intl.NumberFormat('tr-TR', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 4
        }).format(value);
    };

    const formatPercent = (value) => {
        if (value === undefined || value === null) return '-';
        const color = value >= 0 ? '#10b981' : '#ef4444';
        const sign = value >= 0 ? '▲' : '▼';
        return <span style={{ color }}>{sign} {value >= 0 ? '+' : ''}{value.toFixed(2)}%</span>;
    };

    const formatLargeNumber = (value) => {
        if (value === undefined || value === null || Number(value) === 0) return '—';
        if (value >= 1000000) {
            return (value / 1000000).toFixed(1) + 'M ₺';
        }
        return new Intl.NumberFormat('tr-TR', {
            minimumFractionDigits: 0,
            maximumFractionDigits: 0
        }).format(value) + ' ₺';
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

    return (
        <div style={s.root}>
            <h1 style={s.pageTitle}>{t("nav.fundsFull")} <TermInfo termKey="nav" placement="bottom" /></h1>
            <div style={s.tableContainer}>
                {/* Header with Filter and Admin Refresh */}
                <div style={s.headerContainer}>
                    <div style={s.headerLeft}>
                        {lastUpdate && (
                            <div style={s.lastUpdate}>
                                {t("funds.lastUpdate")}: {new Date(lastUpdate).toLocaleDateString('tr-TR')}
                            </div>
                        )}
                    </div>
                    <div style={s.headerRight}>
                        {isAdmin && (
                            <button
                                onClick={handleRefresh}
                                disabled={refreshing}
                                style={{
                                    ...s.refreshBtn,
                                    ...(refreshing ? s.refreshBtnDisabled : {})
                                }}
                            >
                                {refreshing ? `🔄 ${t("funds.refreshing")}` : `🔄 ${t("funds.refresh")}`}
                            </button>
                        )}
                    </div>
                </div>

                {/* Fund-type checkbox filter — multi-select, replaces the old
                    single-select dropdown so users can tick e.g. "Hisse"
                    AND "Karma" simultaneously. */}
                {fundTypes.length > 0 && (
                    <div style={{ padding: "8px 0 16px" }}>
                        <CheckboxFilterGroup
                            options={fundTypes.map((t) => ({
                                key: t,
                                label: t,
                                count: typeCounts[t] || 0,
                            }))}
                            selected={selectedFundTypes}
                            onChange={setSelectedFundTypes}
                            allLabel={t("funds.allTypes")}
                        />
                    </div>
                )}

                {/* Table Header */}
                <div style={s.tableHeader}>
                    <div style={{ ...s.colFund, cursor: "pointer" }} onClick={() => toggleSort("fundName")}>
                        {t("funds.colInfo")} {sortKey === "fundName" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                    </div>
                    <div style={{ ...s.colPrice, cursor: "pointer" }} onClick={() => toggleSort("unitPrice")}>
                        {t("funds.colUnitPrice")} <TermInfo termKey="nav" placement="bottom" />
                        {sortKey === "unitPrice" ? (sortDir === "asc" ? " ▲" : " ▼") : ""}
                    </div>
                    <div style={{ ...s.colReturn, cursor: "pointer" }} onClick={() => toggleSort("dailyReturn")}>
                        {t("funds.colDaily")} {sortKey === "dailyReturn" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                    </div>
                    <div style={{ ...s.colReturn, cursor: "pointer" }} onClick={() => toggleSort("monthlyReturn")}>
                        {t("funds.col1m")} {sortKey === "monthlyReturn" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                    </div>
                    <div style={{ ...s.colReturn, cursor: "pointer" }} onClick={() => toggleSort("threeMonthReturn")}>
                        {t("funds.col3m")} {sortKey === "threeMonthReturn" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                    </div>
                    <div style={{ ...s.colReturn, cursor: "pointer" }} onClick={() => toggleSort("sixMonthReturn")}>
                        {t("funds.col6m")} {sortKey === "sixMonthReturn" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                    </div>
                    <div style={{ ...s.colReturn, cursor: "pointer" }} onClick={() => toggleSort("yearlyReturn")}>
                        {t("funds.col1y")} {sortKey === "yearlyReturn" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                    </div>
                    <div style={{ ...s.colReturn, cursor: "pointer" }} onClick={() => toggleSort("threeYearReturn")}>
                        {t("funds.col3y")} {sortKey === "threeYearReturn" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                    </div>
                    <div style={{ ...s.colReturn, cursor: "pointer" }} onClick={() => toggleSort("fiveYearReturn")}>
                        {t("funds.col5y")} {sortKey === "fiveYearReturn" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                    </div>
                    <div style={s.colRisk}>{t("funds.colRisk")} <TermInfo termKey="risk_level" placement="bottom" /></div>
                </div>

                {/* Table Body */}
                <div style={s.tableBody}>
                    {filteredFunds.length === 0 ? (
                        <div style={s.emptyState}>
                            <div style={{ fontSize: 48, marginBottom: 12 }}>📊</div>
                            <div style={{ color: "var(--text-muted)", fontSize: 13 }}>
                                {funds.length === 0
                                    ? t("funds.emptyAll")
                                    : selectedFundTypes.length > 0
                                        ? t("funds.emptyFiltered", { types: selectedFundTypes.join(', ') })
                                        : t("funds.empty")
                                }
                            </div>
                            {funds.length === 0 && isAdmin && (
                                <button
                                    style={{ ...s.retryBtn, marginTop: 16 }}
                                    onClick={handleRefresh}
                                    disabled={refreshing}
                                >
                                    {refreshing ? t("funds.refreshing") : t("funds.manualRefresh")}
                                </button>
                            )}
                        </div>
                    ) : (
                        pagedFunds.map((fund) => (
                            <div key={fund.id} style={s.tableRow}>
                                <div style={s.colFund}>
                                    <div style={s.fundCode}>{fund.fundCode}</div>
                                    <div style={s.fundName}>{fund.fundName}</div>
                                    <div style={s.fundCompany}>{fund.managementCompany}</div>
                                </div>
                                <div style={s.colPrice}>
                                    <div style={s.priceValue}>{formatCurrency(fund.unitPrice)}</div>
                                </div>
                                <div style={s.colReturn}>
                                    <div style={{ fontSize: 13, fontWeight: 600 }}>
                                        {formatPercent(fund.dailyReturn)}
                                    </div>
                                </div>
                                <div style={s.colReturn}>
                                    <div style={{ fontSize: 13, fontWeight: 600 }}>
                                        {formatPercent(fund.monthlyReturn)}
                                    </div>
                                </div>
                                <div style={s.colReturn}>
                                    <div style={{ fontSize: 13, fontWeight: 600 }}>
                                        {formatPercent(fund.threeMonthReturn)}
                                    </div>
                                </div>
                                <div style={s.colReturn}>
                                    <div style={{ fontSize: 13, fontWeight: 600 }}>
                                        {formatPercent(fund.sixMonthReturn)}
                                    </div>
                                </div>
                                <div style={s.colReturn}>
                                    <div style={{ fontSize: 13, fontWeight: 600 }}>
                                        {formatPercent(fund.yearlyReturn)}
                                    </div>
                                </div>
                                <div style={s.colReturn}>
                                    <div style={{ fontSize: 13, fontWeight: 600 }}>
                                        {formatPercent(fund.threeYearReturn)}
                                    </div>
                                </div>
                                <div style={s.colReturn}>
                                    <div style={{ fontSize: 13, fontWeight: 600 }}>
                                        {formatPercent(fund.fiveYearReturn)}
                                    </div>
                                </div>
                                <div style={s.colRisk}>
                                    {fund.riskLevel && (
                                        <span style={{
                                            ...s.riskBadge,
                                            ...(fund.riskLevel === 'DÜŞÜK' ? s.riskLow :
                                                fund.riskLevel === 'ORTA' ? s.riskMedium :
                                                s.riskHigh)
                                        }}>
                                            {fund.riskLevel === 'DÜŞÜK' ? t("funds.riskLow") :
                                             fund.riskLevel === 'ORTA' ? t("funds.riskMed") :
                                             fund.riskLevel === 'YÜKSEK' ? t("funds.riskHigh") :
                                             fund.riskLevel}
                                        </span>
                                    )}
                                </div>
                            </div>
                        ))
                    )}
                </div>

                {totalFiltered > 0 && (
                    <Pagination
                        page={page}
                        pageSize={pageSize}
                        total={totalFiltered}
                        onPageChange={setPage}
                        onPageSizeChange={setPageSize}
                    />
                )}
            </div>
        </div>
    );
}

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
    filterContainer: {
        padding: "16px 20px",
        borderBottom: "1px solid var(--border-card)",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: 12,
    },
    headerContainer: {
        padding: "16px 20px",
        borderBottom: "1px solid var(--border-card)",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: 12,
    },
    headerLeft: {
        display: "flex",
        alignItems: "center",
        gap: 12,
    },
    headerRight: {
        display: "flex",
        alignItems: "center",
        gap: 12,
    },
    lastUpdate: {
        fontSize: 12,
        color: "var(--text-muted)",
        fontWeight: 500,
    },
    filterSelect: {
        padding: "8px 16px",
        borderRadius: 6,
        border: "1px solid var(--input-border, var(--border-card))",
        background: "var(--input-bg, var(--bg-card))",
        color: "var(--text-primary)",
        fontSize: 13,
        fontWeight: 500,
        cursor: "pointer",
        outline: "none",
    },
    refreshBtn: {
        padding: "8px 16px",
        borderRadius: 6,
        border: "none",
        background: "#10b981",
        color: "#000",
        fontSize: 13,
        fontWeight: 700,
        cursor: "pointer",
        transition: "all 0.2s",
        whiteSpace: "nowrap",
    },
    refreshBtnDisabled: {
        opacity: 0.5,
        cursor: "not-allowed",
    },
    tableHeader: {
        display: "grid",
        gridTemplateColumns: "2.2fr 1fr 0.8fr 0.8fr 0.8fr 0.8fr 0.8fr 0.8fr 0.8fr 90px",
        gap: 16,
        padding: "14px 20px",
        background: "var(--bg-panel)",
        borderBottom: "1px solid var(--border-card)",
        fontSize: 11,
        fontWeight: 600,
        color: "var(--text-muted)",
        textTransform: "uppercase",
    },
    tableBody: {
        display: "flex",
        flexDirection: "column",
    },
    tableRow: {
        display: "grid",
        gridTemplateColumns: "2.2fr 1fr 0.8fr 0.8fr 0.8fr 0.8fr 0.8fr 0.8fr 0.8fr 90px",
        gap: 16,
        padding: "14px 20px",
        borderBottom: "1px solid var(--border-card)",
        cursor: "pointer",
        transition: "background 0.2s",
    },
    emptyState: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "60px 20px",
    },
    colFund: { display: "flex", flexDirection: "column", gap: 2 },
    colPrice: { display: "flex", alignItems: "center", justifyContent: "flex-end" },
    colReturn: { display: "flex", alignItems: "center", justifyContent: "flex-end" },
    colRisk: { display: "flex", alignItems: "center", justifyContent: "center" },
    colValue: { display: "flex", alignItems: "center", justifyContent: "flex-end" },
    fundCode: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    fundName: { fontSize: 12, color: "var(--text-muted)" },
    fundCompany: { fontSize: 10, color: "var(--text-muted)", marginTop: 2 },
    priceValue: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    valueAmount: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    riskBadge: {
        padding: "4px 12px",
        borderRadius: 12,
        fontSize: 11,
        fontWeight: 600,
    },
    riskLow: {
        background: "rgba(16, 185, 129, 0.15)",
        color: "#10b981",
    },
    riskMedium: {
        background: "rgba(251, 191, 36, 0.15)",
        color: "#fbbf24",
    },
    riskHigh: {
        background: "rgba(239, 68, 68, 0.15)",
        color: "#ef4444",
    },
};

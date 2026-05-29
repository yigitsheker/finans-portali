import { useEffect, useMemo, useState } from "react";
import {
    getBonds,
    getBondSummary,
    refreshBondData,
} from "../api/bondApi";
import BondDetailModal from "../components/BondDetailModal";
import DepositRatesCard from "../components/DepositRatesCard";
import DataFreshnessHeader from "../components/common/DataFreshnessHeader";
import TermInfo from "../components/common/TermInfo";
import { useI18n } from "../contexts/I18nContext";

const TYPE_KEYS = {
    GOVERNMENT_BOND: "bonds.typeGovBond",
    TREASURY_BILL: "bonds.typeTBill",
    LEASE_CERTIFICATE: "bonds.typeSukuk",
    EUROBOND: "bonds.typeEurobond",
    CORPORATE_BOND: "bonds.typeCorp",
    OTHER: "bonds.typeOther",
};

const CURRENCY_OPTIONS = ["TRY", "USD", "EUR"];

export default function Bonds({ keycloak }) {
    const { t } = useI18n();
    const TYPE_LABELS = {
        GOVERNMENT_BOND: t("bonds.typeGovBond"),
        TREASURY_BILL: t("bonds.typeTBill"),
        LEASE_CERTIFICATE: t("bonds.typeSukuk"),
        EUROBOND: t("bonds.typeEurobond"),
        CORPORATE_BOND: t("bonds.typeCorp"),
        OTHER: t("bonds.typeOther"),
    };
    const [bonds, setBonds] = useState([]);
    const [summary, setSummary] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // Filters
    const [typeFilter, setTypeFilter] = useState("ALL");
    const [currencyFilter, setCurrencyFilter] = useState("ALL");
    const [search, setSearch] = useState("");

    // Selected bond for detail modal
    const [selectedBond, setSelectedBond] = useState(null);

    // Refresh state
    const [refreshing, setRefreshing] = useState(false);
    const [refreshMessage, setRefreshMessage] = useState(null);

    // Check if user is admin
    const isAdmin = useMemo(() => {
        const parsed = keycloak.tokenParsed;
        const roles = parsed?.realm_access?.roles || [];
        return roles.includes("ADMIN");
    }, [keycloak.tokenParsed]);

    useEffect(() => {
        loadData();
    }, [typeFilter, currencyFilter, search]);

    async function loadData() {
        try {
            setLoading(true);
            setError(null);

            const filters = {
                type: typeFilter !== "ALL" ? typeFilter : undefined,
                currency: currencyFilter !== "ALL" ? currencyFilter : undefined,
                search: search.trim() || undefined,
            };

            const [bondsData, summaryData] = await Promise.all([
                getBonds(filters),
                getBondSummary(),
            ]);

            setBonds(bondsData);
            setSummary(summaryData);
        } catch (e) {
            setError(e?.message ?? "Veri yüklenirken hata oluştu");
        } finally {
            setLoading(false);
        }
    }

    async function handleRefresh() {
        try {
            setRefreshing(true);
            setRefreshMessage(null);
            const message = await refreshBondData(keycloak);
            setRefreshMessage(message);
            // Reload data after refresh
            setTimeout(() => loadData(), 1000);
        } catch (e) {
            setRefreshMessage(`Hata: ${e?.message ?? "Güncelleme başarısız"}`);
        } finally {
            setRefreshing(false);
        }
    }

    // Header-driven sort state. Defaults to yield (desc) — that's what the
    // page showed before sortable headers existed. User clicks on any
    // header cell to override.
    const [sortKey, setSortKey] = useState("latestYieldRate");
    const [sortDir, setSortDir] = useState("desc");

    const toggleSort = (key) => {
        if (sortKey === key) {
            setSortDir((d) => (d === "asc" ? "desc" : "asc"));
        } else {
            setSortKey(key);
            // Strings open ascending, numbers descending — matches user
            // expectation (A→Z vs biggest-first).
            setSortDir(["name", "type", "isin", "source"].includes(key) ? "asc" : "desc");
        }
    };

    const sortedBonds = useMemo(() => {
        if (!sortKey) return bonds;
        const stringKeys = new Set(["name", "type", "isin", "source"]);
        return [...bonds].sort((a, b) => {
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
    }, [bonds, sortKey, sortDir]);

    return (
        <div style={s.root}>
            {/* Header */}
            <div style={s.header}>
                <div>
                    <h1 style={s.title}>{t("bonds.title")} <TermInfo termKey="bond" placement="bottom" /></h1>
                    <p style={s.subtitle}>
                        {t("bonds.subtitle")}
                    </p>
                </div>
                {isAdmin && (
                    <button
                        style={s.refreshBtn}
                        onClick={handleRefresh}
                        disabled={refreshing}
                    >
                        {refreshing ? t("bonds.refreshing") : `🔄 ${t("bonds.refresh")}`}
                    </button>
                )}
            </div>

            {refreshMessage && (
                <div style={s.refreshMessage}>{refreshMessage}</div>
            )}

            {/* Freshness chip — visible to everyone, including non-admins who
                can't trigger a TCMB refresh but can still re-pull what the
                backend already has cached. */}
            <DataFreshnessHeader
                asOf={summary?.lastUpdateDate}
                onRefresh={loadData}
                refreshing={loading}
            />

            {/* Data-quality disclosure — bond yields here are derived from EVDS3's
                "dirty price" series via a textbook YTM approximation. Both inputs
                are real but the dirty-price (= clean + accrued coupon) makes the
                computed YTM read low when the bond is well into its coupon period.
                Front-end note keeps user expectations honest without hiding the data. */}
            <div style={s.disclaimer}>
                <strong>{t("bonds.noteLabel")}</strong> {t("bonds.noteBody")}
            </div>

            {/* Mevduat Faizi (TCMB EVDS3) — tahvil veriliğinin doğal komşusu */}
            <DepositRatesCard />

            {/* Summary Cards */}
            {summary && (
                <div style={s.summaryGrid}>
                    <div style={s.summaryCard}>
                        <div style={s.summaryLabel}>{t("bonds.totalInstruments")} <TermInfo termKey="bond" /></div>
                        <div style={s.summaryValue}>{summary.totalInstruments}</div>
                    </div>
                    <div style={s.summaryCard}>
                        <div style={s.summaryLabel}>{t("bonds.avgYield")} <TermInfo termKey="yield" /></div>
                        <div style={s.summaryValue}>
                            {summary.averageYield ? `${summary.averageYield.toFixed(2)}%` : "-"}
                        </div>
                    </div>
                    <div style={s.summaryCard}>
                        <div style={s.summaryLabel}>{t("bonds.maxYield")} <TermInfo termKey="yield_curve" /></div>
                        <div style={s.summaryValue}>
                            {summary.highestYield ? `${summary.highestYield.toFixed(2)}%` : "-"}
                        </div>
                    </div>
                    <div style={s.summaryCard}>
                        <div style={s.summaryLabel}>{t("bonds.lastUpdate")}</div>
                        <div style={s.summaryValue}>
                            {summary.lastUpdateDate
                                ? new Date(summary.lastUpdateDate).toLocaleDateString("tr-TR")
                                : "-"}
                        </div>
                    </div>
                </div>
            )}

            {/* Filters */}
            <div style={s.filterSection}>
                <input
                    type="text"
                    placeholder={t("bonds.searchPh")}
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    style={s.searchInput}
                />
                <select
                    value={typeFilter}
                    onChange={(e) => setTypeFilter(e.target.value)}
                    style={s.select}
                >
                    <option value="ALL">{t("bonds.allTypes")}</option>
                    {Object.entries(TYPE_LABELS).map(([key, label]) => (
                        <option key={key} value={key}>{label}</option>
                    ))}
                </select>
                <select
                    value={currencyFilter}
                    onChange={(e) => setCurrencyFilter(e.target.value)}
                    style={s.select}
                >
                    <option value="ALL">{t("bonds.allCurrencies")}</option>
                    {CURRENCY_OPTIONS.map((curr) => (
                        <option key={curr} value={curr}>{curr}</option>
                    ))}
                </select>
            </div>

            {/* Error State */}
            {error && <div style={s.error}>{error}</div>}

            {/* Loading State */}
            {loading && <div style={s.loading}>{t("common.loading")}</div>}

            {/* Empty State */}
            {!loading && !error && sortedBonds.length === 0 && (
                <div style={s.empty}>{t("common.noResults")}</div>
            )}

            {/* Bonds Table */}
            {!loading && !error && sortedBonds.length > 0 && (
                <div style={s.tableWrapper} className="fp-table-scroll">
                    <table style={s.table}>
                        <thead>
                            <tr>
                                <th style={{ ...s.th, cursor: "pointer" }} onClick={() => toggleSort("name")}>
                                    {t("bonds.colInstrument")} {sortKey === "name" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                                </th>
                                <th style={{ ...s.th, cursor: "pointer" }} onClick={() => toggleSort("type")}>
                                    {t("bonds.colType")} <TermInfo termKey="bond" placement="bottom" />
                                    {sortKey === "type" ? (sortDir === "asc" ? " ▲" : " ▼") : ""}
                                </th>
                                <th style={{ ...s.th, cursor: "pointer" }} onClick={() => toggleSort("isin")}>
                                    {t("bonds.colIsin")} <TermInfo termKey="isin" placement="bottom" />
                                    {sortKey === "isin" ? (sortDir === "asc" ? " ▲" : " ▼") : ""}
                                </th>
                                <th style={{ ...s.th, cursor: "pointer" }} onClick={() => toggleSort("maturityDate")}>
                                    {t("bonds.colMaturity")} <TermInfo termKey="maturity" placement="bottom" />
                                    {sortKey === "maturityDate" ? (sortDir === "asc" ? " ▲" : " ▼") : ""}
                                </th>
                                <th style={{ ...s.th, cursor: "pointer" }} onClick={() => toggleSort("couponRate")}>
                                    {t("bonds.colCoupon")} <TermInfo termKey="coupon" placement="bottom" />
                                    {sortKey === "couponRate" ? (sortDir === "asc" ? " ▲" : " ▼") : ""}
                                </th>
                                <th style={{ ...s.th, cursor: "pointer" }} onClick={() => toggleSort("latestPrice")}>
                                    {t("bonds.colPrice")} {sortKey === "latestPrice" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                                </th>
                                <th style={{ ...s.th, cursor: "pointer" }} onClick={() => toggleSort("latestYieldRate")}>
                                    {t("bonds.colYield")} <TermInfo termKey="yield" placement="bottom" />
                                    {sortKey === "latestYieldRate" ? (sortDir === "asc" ? " ▲" : " ▼") : ""}
                                </th>
                                <th style={{ ...s.th, cursor: "pointer" }} onClick={() => toggleSort("latestChangeRate")}>
                                    {t("bonds.colChange")} {sortKey === "latestChangeRate" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                                </th>
                                <th style={{ ...s.th, cursor: "pointer" }} onClick={() => toggleSort("source")}>
                                    {t("bonds.colSource")} {sortKey === "source" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {sortedBonds.map((bond) => {
                                const hasChange = bond.changeRate !== null && bond.changeRate !== undefined && bond.changeRate !== 0;
                                const changeColor = !hasChange
                                    ? "var(--text-muted)"
                                    : bond.changeRate > 0 ? "var(--green)" : "var(--red)";
                                return (
                                    <tr
                                        key={bond.id}
                                        style={s.tr}
                                        onClick={() => setSelectedBond(bond)}
                                    >
                                        <td style={s.td}>
                                            <div style={s.bondName}>
                                                <div style={s.bondSymbol}>{bond.symbol}</div>
                                                <div style={s.bondFullName}>{bond.name}</div>
                                            </div>
                                        </td>
                                        <td style={s.td}>
                                            <span style={s.typeBadge}>
                                                {TYPE_LABELS[bond.type]}
                                            </span>
                                        </td>
                                        <td style={s.td}>{bond.isin || "-"}</td>
                                        <td style={s.td}>
                                            {bond.maturityDate
                                                ? new Date(bond.maturityDate).toLocaleDateString("tr-TR")
                                                : "-"}
                                        </td>
                                        <td style={s.td}>
                                            {bond.couponRate ? bond.couponRate.toFixed(2) : "-"}
                                        </td>
                                        <td style={s.td}>
                                            {bond.latestPrice ? bond.latestPrice.toFixed(2) : "-"}
                                        </td>
                                        <td style={s.td}>
                                            <strong>
                                                {bond.latestYieldRate ? bond.latestYieldRate.toFixed(2) : "-"}
                                            </strong>
                                        </td>
                                        <td style={{ ...s.td, color: changeColor }}>
                                            {bond.changeRate !== null && bond.changeRate !== undefined
                                                ? `${bond.changeRate > 0 ? "+" : ""}${bond.changeRate.toFixed(2)}`
                                                : "-"}
                                        </td>
                                        <td style={s.td}>
                                            <span style={s.sourceBadge} title={
                                                bond.source === "TCMB"
                                                    ? t("bonds.sourceTcmbHint")
                                                    : bond.source === "BIST/BIGPARA"
                                                        ? t("bonds.sourceBistHint")
                                                        : bond.source
                                            }>
                                                {bond.source}
                                            </span>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}

            {/* Bond Detail Modal */}
            {selectedBond && (
                <BondDetailModal
                    bondId={selectedBond.id}
                    onClose={() => setSelectedBond(null)}
                />
            )}
        </div>
    );
}

const s = {
    root: { padding: "20px 24px", maxWidth: 1400, margin: "0 auto" },
    header: { display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 },
    title: { margin: 0, fontSize: 28, fontWeight: 800, color: "var(--text-primary)" },
    subtitle: { margin: "4px 0 0 0", fontSize: 14, color: "var(--text-muted)" },
    refreshBtn: {
        padding: "10px 20px",
        borderRadius: 8,
        border: "1px solid var(--accent-border)",
        background: "var(--accent)",
        color: "var(--accent-solid)",
        fontSize: 14,
        fontWeight: 600,
        cursor: "pointer",
    },
    refreshMessage: {
        padding: "12px 16px",
        borderRadius: 8,
        background: "var(--accent)",
        border: "1px solid var(--accent-border)",
        color: "var(--text-primary)",
        fontSize: 13,
        marginBottom: 16,
    },
    disclaimer: {
        padding: "12px 16px",
        marginBottom: 16,
        borderRadius: 8,
        background: "rgba(245, 158, 11, 0.10)",
        border: "1px solid rgba(245, 158, 11, 0.35)",
        color: "var(--text-secondary)",
        fontSize: 12.5,
        lineHeight: 1.55,
    },
    summaryGrid: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 16, marginBottom: 24 },
    summaryCard: {
        padding: "16px 20px",
        borderRadius: 10,
        border: "1px solid var(--border-card)",
        background: "var(--bg-card)",
    },
    summaryLabel: { fontSize: 12, color: "var(--text-muted)", marginBottom: 8 },
    summaryValue: { fontSize: 24, fontWeight: 700, color: "var(--text-primary)" },
    filterSection: { display: "flex", gap: 12, marginBottom: 20, flexWrap: "wrap" },
    searchInput: {
        flex: 1,
        minWidth: 200,
        padding: "10px 14px",
        borderRadius: 8,
        border: "1px solid var(--input-border)",
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        fontSize: 14,
        outline: "none",
    },
    select: {
        padding: "10px 14px",
        borderRadius: 8,
        border: "1px solid var(--input-border)",
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        fontSize: 14,
        outline: "none",
        cursor: "pointer",
    },
    error: { padding: "16px", borderRadius: 8, background: "var(--danger-bg)", border: "1px solid var(--danger-border)", color: "var(--danger-text)", fontSize: 14 },
    loading: { padding: "40px", textAlign: "center", color: "var(--text-muted)", fontSize: 14 },
    empty: { padding: "40px", textAlign: "center", color: "var(--text-muted)", fontSize: 14 },
    tableWrapper: { overflowX: "auto", borderRadius: 10, border: "1px solid var(--border-card)" },
    table: { width: "100%", borderCollapse: "collapse", background: "var(--bg-card)" },
    th: {
        padding: "12px 16px",
        textAlign: "left",
        fontSize: 12,
        fontWeight: 600,
        color: "var(--text-muted)",
        borderBottom: "1px solid var(--border-card)",
        background: "var(--bg-panel)",
    },
    tr: { cursor: "pointer", transition: "background 0.2s" },
    td: { padding: "14px 16px", fontSize: 13, color: "var(--text-primary)", borderBottom: "1px solid var(--border-soft)" },
    bondName: { display: "flex", flexDirection: "column", gap: 2 },
    bondSymbol: { fontWeight: 600, fontSize: 14 },
    bondFullName: { fontSize: 11, color: "var(--text-muted)" },
    typeBadge: {
        padding: "4px 8px",
        borderRadius: 4,
        background: "var(--accent)",
        border: "1px solid var(--accent-border)",
        fontSize: 11,
        fontWeight: 500,
    },
    sourceBadge: {
        padding: "2px 6px",
        borderRadius: 4,
        background: "var(--input-bg)",
        border: "1px solid var(--border-soft)",
        fontSize: 10,
        fontWeight: 500,
        color: "var(--text-muted)",
    },
};

import { useEffect, useMemo, useState } from "react";
import {
    getBonds,
    getBondSummary,
    refreshBondData,
} from "../api/bondApi";
import BondDetailModal from "../components/BondDetailModal";
import DepositRatesCard from "../components/DepositRatesCard";

const TYPE_LABELS = {
    GOVERNMENT_BOND: "Devlet Tahvili",
    TREASURY_BILL: "Hazine Bonosu",
    LEASE_CERTIFICATE: "Kira Sertifikası",
    EUROBOND: "Eurobond",
    CORPORATE_BOND: "Özel Sektör Tahvili",
    OTHER: "Diğer",
};

const CURRENCY_OPTIONS = ["TRY", "USD", "EUR"];

export default function Bonds({ keycloak }) {
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

    const sortedBonds = useMemo(() => {
        return [...bonds].sort((a, b) => {
            // Sort by yield rate descending
            if (a.latestYieldRate && b.latestYieldRate) {
                return b.latestYieldRate - a.latestYieldRate;
            }
            return 0;
        });
    }, [bonds]);

    return (
        <div style={s.root}>
            {/* Header */}
            <div style={s.header}>
                <div>
                    <h1 style={s.title}>Tahvil ve Bono</h1>
                    <p style={s.subtitle}>
                        Devlet tahvilleri, hazine bonoları ve borçlanma araçları verileri
                    </p>
                </div>
                {isAdmin && (
                    <button
                        style={s.refreshBtn}
                        onClick={handleRefresh}
                        disabled={refreshing}
                    >
                        {refreshing ? "Güncelleniyor..." : "🔄 Verileri Yenile"}
                    </button>
                )}
            </div>

            {refreshMessage && (
                <div style={s.refreshMessage}>{refreshMessage}</div>
            )}

            {/* Mevduat Faizi (TCMB EVDS3) — tahvil veriliğinin doğal komşusu */}
            <DepositRatesCard />

            {/* Summary Cards */}
            {summary && (
                <div style={s.summaryGrid}>
                    <div style={s.summaryCard}>
                        <div style={s.summaryLabel}>Toplam Enstrüman</div>
                        <div style={s.summaryValue}>{summary.totalInstruments}</div>
                    </div>
                    <div style={s.summaryCard}>
                        <div style={s.summaryLabel}>Ortalama Getiri</div>
                        <div style={s.summaryValue}>
                            {summary.averageYield ? `${summary.averageYield.toFixed(2)}%` : "-"}
                        </div>
                    </div>
                    <div style={s.summaryCard}>
                        <div style={s.summaryLabel}>En Yüksek Getiri</div>
                        <div style={s.summaryValue}>
                            {summary.highestYield ? `${summary.highestYield.toFixed(2)}%` : "-"}
                        </div>
                    </div>
                    <div style={s.summaryCard}>
                        <div style={s.summaryLabel}>Son Güncelleme</div>
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
                    placeholder="Ara (sembol, isim, ISIN)..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    style={s.searchInput}
                />
                <select
                    value={typeFilter}
                    onChange={(e) => setTypeFilter(e.target.value)}
                    style={s.select}
                >
                    <option value="ALL">Tüm Türler</option>
                    {Object.entries(TYPE_LABELS).map(([key, label]) => (
                        <option key={key} value={key}>{label}</option>
                    ))}
                </select>
                <select
                    value={currencyFilter}
                    onChange={(e) => setCurrencyFilter(e.target.value)}
                    style={s.select}
                >
                    <option value="ALL">Tüm Para Birimleri</option>
                    {CURRENCY_OPTIONS.map((curr) => (
                        <option key={curr} value={curr}>{curr}</option>
                    ))}
                </select>
            </div>

            {/* Error State */}
            {error && <div style={s.error}>{error}</div>}

            {/* Loading State */}
            {loading && <div style={s.loading}>Yükleniyor...</div>}

            {/* Empty State */}
            {!loading && !error && sortedBonds.length === 0 && (
                <div style={s.empty}>Sonuç bulunamadı</div>
            )}

            {/* Bonds Table */}
            {!loading && !error && sortedBonds.length > 0 && (
                <div style={s.tableWrapper}>
                    <table style={s.table}>
                        <thead>
                            <tr>
                                <th style={s.th}>Enstrüman</th>
                                <th style={s.th}>Tür</th>
                                <th style={s.th}>ISIN</th>
                                <th style={s.th}>Vade</th>
                                <th style={s.th}>Kupon %</th>
                                <th style={s.th}>Fiyat</th>
                                <th style={s.th}>Getiri %</th>
                                <th style={s.th}>Değişim %</th>
                                <th style={s.th}>Kaynak</th>
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
                                                    ? "TCMB EVDS3'ten canlı çekilen politika faizine piyasa spread'i eklenerek hesaplanmıştır"
                                                    : bond.source === "BIST/BIGPARA"
                                                        ? "BIST kaynaklı; sayfa scrape edilemezse son bilinen değer kullanılır"
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

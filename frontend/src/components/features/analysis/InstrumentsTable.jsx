import { useEffect, useMemo, useState } from "react";
import Pagination from "../../common/Pagination";
import { useI18n } from "../../../contexts/I18nContext";

// Category keys map to i18n labels resolved at render time so the chip row
// flips locale with the rest of the UI. Inflation rows live in the
// context strip above the table (they're the reference for the
// "Reel Yıllık" column), so they don't get their own chips here.
const CATEGORY_KEYS = [
    { key: "ALL", labelKey: "analysis.catAll" },
    { key: "STOCK", labelKey: "analysis.catStock" },
    { key: "CRYPTO", labelKey: "analysis.catCrypto" },
    { key: "FX", labelKey: "analysis.catFx" },
    { key: "COMMODITY", labelKey: "analysis.catCommodity" },
    { key: "FUND", labelKey: "analysis.catFund" },
    { key: "INDEX", labelKey: "analysis.catIndex" },
];

const RISK_COLORS = {
    LOW: "#16a34a",
    MEDIUM: "#d97706",
    HIGH: "#dc2626",
};

const SIGNAL_COLORS = {
    BUY: "#16a34a",
    HOLD: "#6b7280",
    SELL: "#dc2626",
    NEUTRAL: "#94a3b8",
};

// Localised labels are looked up via t() at render time (see below). The
// SIGNAL_COLORS map above stays language-agnostic — colours mean the same
// thing in either locale.

function fmtPct(v) {
    if (v == null) return "—";
    const n = Number(v);
    if (!Number.isFinite(n)) return "—";
    const sign = n > 0 ? "+" : "";
    return `${sign}${n.toFixed(2)}%`;
}

function pctColor(v) {
    if (v == null) return "var(--text-muted)";
    const n = Number(v);
    if (!Number.isFinite(n) || n === 0) return "var(--text-muted)";
    return n > 0 ? "#16a34a" : "#dc2626";
}

function fmtValue(v, currency) {
    if (v == null) return "—";
    const formatted = Number(v).toLocaleString("tr-TR", { maximumFractionDigits: 4 });
    return currency ? `${formatted} ${currency}` : formatted;
}

/**
 * Cross-asset table for the Analysis page. Local-only filter/sort/search —
 * the underlying list is small enough (a few hundred rows max) that
 * client-side state is the simplest correct approach.
 */
export default function InstrumentsTable({ items, loading, error, onRowClick, selectedSymbol }) {
    const { t } = useI18n();
    const [category, setCategory] = useState("ALL");
    const [search, setSearch] = useState("");
    const [beatsInflationOnly, setBeatsInflationOnly] = useState(false);
    const [sortKey, setSortKey] = useState(null);
    const [sortDir, setSortDir] = useState("desc");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);

    const SIGNAL_LABELS = {
        BUY: t("analysis.signalBuy"),
        HOLD: t("analysis.signalHold"),
        SELL: t("analysis.signalSell"),
        NEUTRAL: t("analysis.signalNeutral"),
    };
    const RISK_LABELS = {
        LOW: t("analysis.riskLow"),
        MEDIUM: t("analysis.riskMedium"),
        HIGH: t("analysis.riskHigh"),
    };

    // Reset to page 1 whenever the filter set changes — without this the
    // user can sit on "page 7" of a category that now only has 2 pages.
    useEffect(() => {
        setPage(1);
    }, [category, search, beatsInflationOnly, sortKey, sortDir]);

    // Inflation rows act as the reference for "real return" — surface
    // their yearly figure as a context strip above the table so the user
    // can see what each row is being compared against.
    const inflationRefs = useMemo(() => {
        const refs = {};
        for (const r of items || []) {
            if (r.category === "INFLATION_TR") refs.tr = r.changeYearly;
            else if (r.category === "INFLATION_US") refs.us = r.changeYearly;
        }
        return refs;
    }, [items]);

    const filtered = useMemo(() => {
        // Inflation rows are reference data (rendered in the context strip
        // above), not investable instruments — strip them from the table view.
        let rows = (items || []).filter(
            (r) => r.category !== "INFLATION_TR" && r.category !== "INFLATION_US"
        );
        if (category !== "ALL") rows = rows.filter((r) => r.category === category);
        if (beatsInflationOnly) {
            rows = rows.filter((r) => r.beatsInflation === true);
        }
        if (search.trim()) {
            const q = search.trim().toUpperCase();
            rows = rows.filter(
                (r) =>
                    (r.symbol && r.symbol.toUpperCase().includes(q)) ||
                    (r.name && r.name.toUpperCase().includes(q))
            );
        }
        if (sortKey) {
            // String columns (symbol, name, category) use locale-aware
            // collation so Turkish characters land in the right spots
            // (ç, ş, ğ etc.). Numeric columns keep the cast-and-subtract
            // path for proper +/- ordering with -Infinity for nulls.
            const stringKey = sortKey === "symbol" || sortKey === "name" || sortKey === "category";
            rows = [...rows].sort((a, b) => {
                const av = a[sortKey];
                const bv = b[sortKey];
                let cmp;
                if (stringKey) {
                    cmp = (av || "").localeCompare(bv || "", "tr", { sensitivity: "base" });
                } else {
                    cmp = (av ?? -Infinity) - (bv ?? -Infinity);
                }
                return sortDir === "asc" ? cmp : -cmp;
            });
        }
        return rows;
    }, [items, category, search, beatsInflationOnly, sortKey, sortDir]);

    const toggleSort = (key) => {
        if (sortKey === key) {
            setSortDir((d) => (d === "asc" ? "desc" : "asc"));
        } else {
            setSortKey(key);
            setSortDir("desc");
        }
    };

    const total = filtered.length;
    const paged = useMemo(() => {
        const start = (page - 1) * pageSize;
        return filtered.slice(start, start + pageSize);
    }, [filtered, page, pageSize]);

    return (
        <div style={s.wrap}>
            {(inflationRefs.tr != null || inflationRefs.us != null) && (
                <div style={s.ctxStrip}>
                    {inflationRefs.tr != null && (
                        <span style={s.ctxItem}>
                            <span style={s.ctxLabel}>{t("analysis.ctxTrCpi")}:</span>
                            <strong style={{ ...s.ctxValue, color: "#dc2626" }}>
                                {fmtPct(inflationRefs.tr)}
                            </strong>
                        </span>
                    )}
                    {inflationRefs.us != null && (
                        <span style={s.ctxItem}>
                            <span style={s.ctxLabel}>{t("analysis.ctxUsCpi")}:</span>
                            <strong style={{ ...s.ctxValue, color: "#dc2626" }}>
                                {fmtPct(inflationRefs.us)}
                            </strong>
                        </span>
                    )}
                    <button
                        type="button"
                        onClick={() => setBeatsInflationOnly((v) => !v)}
                        style={{
                            ...s.chip,
                            ...(beatsInflationOnly ? s.chipActive : {}),
                            marginLeft: "auto",
                        }}
                    >
                        ⚡ {t("analysis.filterBeatsInflation")}
                    </button>
                </div>
            )}

            <div style={s.controls}>
                <input
                    type="text"
                    placeholder={t("analysis.tblSearchPh")}
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    style={s.search}
                />
                <div style={s.chipRow}>
                    {CATEGORY_KEYS.map((c) => (
                        <button
                            key={c.key}
                            type="button"
                            onClick={() => setCategory(c.key)}
                            style={{
                                ...s.chip,
                                ...(category === c.key ? s.chipActive : {}),
                            }}
                        >
                            {t(c.labelKey)}
                        </button>
                    ))}
                </div>
            </div>

            {loading ? (
                <div style={s.state}>{t("analysis.tblLoading")}</div>
            ) : error ? (
                <div style={{ ...s.state, color: "#dc2626" }}>{error}</div>
            ) : filtered.length === 0 ? (
                <div style={s.state}>{t("analysis.tblEmpty")}</div>
            ) : (
                <div style={s.tableWrap} className="fp-table-scroll">
                    <table style={s.table}>
                        <thead>
                            <tr>
                                <th
                                    style={{ ...s.th, cursor: "pointer" }}
                                    onClick={() => toggleSort("symbol")}
                                >
                                    {t("analysis.tblColSymbol")} {sortKey === "symbol" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                                </th>
                                <th
                                    style={{ ...s.th, cursor: "pointer" }}
                                    onClick={() => toggleSort("name")}
                                >
                                    {t("analysis.tblColName")} {sortKey === "name" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                                </th>
                                <th
                                    style={{ ...s.th, cursor: "pointer" }}
                                    onClick={() => toggleSort("category")}
                                >
                                    {t("analysis.tblColCategory")} {sortKey === "category" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                                </th>
                                <th style={{ ...s.th, textAlign: "right" }}>{t("analysis.tblColValue")}</th>
                                <th
                                    style={{ ...s.th, textAlign: "right", cursor: "pointer" }}
                                    onClick={() => toggleSort("changeDaily")}
                                >
                                    {t("analysis.tblColDaily")} {sortKey === "changeDaily" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                                </th>
                                <th
                                    style={{ ...s.th, textAlign: "right", cursor: "pointer" }}
                                    onClick={() => toggleSort("changeWeekly")}
                                >
                                    {t("analysis.tblColWeekly")} {sortKey === "changeWeekly" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                                </th>
                                <th
                                    style={{ ...s.th, textAlign: "right", cursor: "pointer" }}
                                    onClick={() => toggleSort("changeMonthly")}
                                >
                                    {t("analysis.tblColMonthly")} {sortKey === "changeMonthly" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                                </th>
                                <th
                                    style={{ ...s.th, textAlign: "right", cursor: "pointer" }}
                                    onClick={() => toggleSort("changeYearly")}
                                >
                                    {t("analysis.tblColYearly")} {sortKey === "changeYearly" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                                </th>
                                <th
                                    style={{ ...s.th, textAlign: "right", cursor: "pointer" }}
                                    onClick={() => toggleSort("realChangeYearly")}
                                >
                                    {t("analysis.tblColRealYearly")} {sortKey === "realChangeYearly" ? (sortDir === "asc" ? "▲" : "▼") : ""}
                                </th>
                                <th style={s.th}>{t("analysis.tblColRisk")}</th>
                                <th style={s.th}>{t("analysis.tblColShort")}</th>
                                <th style={s.th}>{t("analysis.tblColLong")}</th>
                            </tr>
                        </thead>
                        <tbody>
                            {paged.map((r) => (
                                <tr
                                    key={`${r.category}-${r.symbol}`}
                                    onClick={() => onRowClick?.(r)}
                                    style={{
                                        ...s.tr,
                                        ...(selectedSymbol === r.symbol ? s.trActive : {}),
                                    }}
                                >
                                    <td style={s.tdBold}>{r.symbol}</td>
                                    <td style={s.td}>{r.name}</td>
                                    <td style={s.tdMuted}>{r.category}</td>
                                    <td style={{ ...s.td, textAlign: "right" }}>{fmtValue(r.value, r.currency)}</td>
                                    <td style={{ ...s.td, textAlign: "right", color: pctColor(r.changeDaily) }}>
                                        {fmtPct(r.changeDaily)}
                                    </td>
                                    <td style={{ ...s.td, textAlign: "right", color: pctColor(r.changeWeekly) }}>
                                        {fmtPct(r.changeWeekly)}
                                    </td>
                                    <td style={{ ...s.td, textAlign: "right", color: pctColor(r.changeMonthly) }}>
                                        {fmtPct(r.changeMonthly)}
                                    </td>
                                    <td style={{ ...s.td, textAlign: "right", color: pctColor(r.changeYearly) }}>
                                        {fmtPct(r.changeYearly)}
                                    </td>
                                    <td style={{ ...s.td, textAlign: "right", color: pctColor(r.realChangeYearly), fontWeight: 600 }}>
                                        {fmtPct(r.realChangeYearly)}
                                    </td>
                                    <td style={s.td}>
                                        <span style={{ ...s.badge, color: RISK_COLORS[r.riskLevel] || "#6b7280" }}>
                                            {RISK_LABELS[r.riskLevel] || "—"}
                                        </span>
                                    </td>
                                    <td style={s.td}>
                                        <span style={{ ...s.badge, color: SIGNAL_COLORS[r.shortTermSignal] || "#6b7280" }}>
                                            {SIGNAL_LABELS[r.shortTermSignal] || "—"}
                                        </span>
                                    </td>
                                    <td style={s.td}>
                                        <span style={{ ...s.badge, color: SIGNAL_COLORS[r.longTermSignal] || "#6b7280" }}>
                                            {SIGNAL_LABELS[r.longTermSignal] || "—"}
                                        </span>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {!loading && !error && total > 0 && (
                <Pagination
                    page={page}
                    pageSize={pageSize}
                    total={total}
                    onPageChange={setPage}
                    onPageSizeChange={(n) => {
                        setPageSize(n);
                        setPage(1);
                    }}
                />
            )}
        </div>
    );
}

const s = {
    wrap: { display: "flex", flexDirection: "column", gap: 12 },
    ctxStrip: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: 16,
        padding: "10px 14px",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        background: "var(--bg-card)",
        fontSize: 12,
    },
    ctxItem: { display: "inline-flex", alignItems: "center", gap: 6 },
    ctxLabel: { color: "var(--text-muted)" },
    ctxValue: { fontSize: 13, fontWeight: 700 },
    controls: { display: "flex", flexDirection: "column", gap: 10 },
    search: {
        padding: "10px 12px",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        fontSize: 13,
        background: "var(--bg-input, var(--bg-card))",
        color: "var(--text-primary)",
        outline: "none",
    },
    chipRow: { display: "flex", flexWrap: "wrap", gap: 6 },
    chip: {
        padding: "6px 12px",
        borderRadius: 999,
        border: "1px solid var(--border-card)",
        background: "transparent",
        cursor: "pointer",
        fontSize: 12,
        color: "var(--text-secondary, var(--text-muted))",
    },
    chipActive: {
        background: "var(--accent-hover-bg)",
        borderColor: "var(--accent-solid)",
        color: "var(--accent-solid)",
        fontWeight: 600,
    },
    state: { padding: 24, textAlign: "center", color: "var(--text-muted)", fontSize: 13 },
    tableWrap: { overflowX: "auto", border: "1px solid var(--border-card)", borderRadius: 8 },
    table: { width: "100%", borderCollapse: "collapse", fontSize: 12 },
    th: {
        padding: "10px 12px",
        textAlign: "left",
        fontWeight: 600,
        color: "var(--text-muted)",
        borderBottom: "1px solid var(--border-card)",
        whiteSpace: "nowrap",
        background: "var(--bg-card)",
    },
    tr: {
        cursor: "pointer",
        borderBottom: "1px solid var(--border-card)",
    },
    trActive: { background: "var(--accent-hover-bg)" },
    td: { padding: "10px 12px", color: "var(--text-primary)" },
    tdBold: { padding: "10px 12px", color: "var(--text-primary)", fontWeight: 700 },
    tdMuted: { padding: "10px 12px", color: "var(--text-muted)", fontSize: 11 },
    badge: {
        fontWeight: 600,
        fontSize: 11,
        textTransform: "uppercase",
        letterSpacing: 0.3,
    },
};

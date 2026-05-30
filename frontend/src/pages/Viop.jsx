import { useCallback, useEffect, useMemo, useState } from "react";
import PropTypes from "prop-types";
import { getViopContracts } from "../api/viopApi";
import CheckboxFilterGroup from "../components/common/CheckboxFilterGroup";
import Pagination from "../components/common/Pagination";
import TermInfo from "../components/common/TermInfo";
import BuyModalMount from "../components/BuyModalMount";
import { useI18n } from "../contexts/I18nContext";
import { useBuyTarget } from "../hooks/useBuyTarget";

// Empty selection => "all categories". Same convention used by the
// CheckboxFilterGroup component.
const CATEGORY_OPTIONS = [
    { key: "INDEX",      label: "Endeks" },
    { key: "STOCK",      label: "Pay (Hisse)" },
    { key: "FX_TRY",     label: "Döviz / TRY" },
    { key: "FX_USD",     label: "Döviz / USD" },
    { key: "METAL_TRY",  label: "Kıymetli Maden / TRY" },
    { key: "METAL_USD",  label: "Kıymetli Maden / USD" },
    { key: "METAL",      label: "Metal" },
];

// BIST VIOP contract multipliers per category. Each LOT of a contract
// represents this many units of the underlying — saved into positions
// so the portfolio's qty × price valuation matches real TL exposure.
//   STOCK     — Pay sözleşmesi = 100 adet hisse
//   INDEX     — BIST 30 Endeks  = 10 TL × endeks puanı
//   FX_TRY    — USDTRY/EURTRY   = 1.000 baz para
//   FX_USD    — Cross-FX (EURUSD vb.) = 1.000 baz para
//   METAL_TRY — Gram altın/gümüş = 1 gram
//   METAL_USD — Ons altın        = 10 ons
//   METAL     — Sanayi metalleri = 1 (BIST'te yaygın değil; varsayılan)
const VIOP_MULTIPLIERS = {
    STOCK: 100,
    INDEX: 10,
    FX_TRY: 1000,
    FX_USD: 1000,
    METAL_TRY: 1,
    METAL_USD: 10,
    METAL: 1,
};

// Short month names localised via Intl so EN gets "Jan/Feb/..." without
// a hand-maintained translation table. Read once per render via the helper
// below so a language toggle updates the maturity column live.
function shortMonthName(monthIndex) {
    let lang = "tr";
    try {
        const v = (localStorage.getItem("i18n-lang") || "").toLowerCase();
        if (v === "en") lang = "en";
    } catch { /* ignore */ }
    const locale = lang === "en" ? "en-US" : "tr-TR";
    const date = new Date(2020, monthIndex, 1);
    return new Intl.DateTimeFormat(locale, { month: "short" }).format(date);
}

const numFmt = (value, fractionDigits = 2) => {
    if (value === null || value === undefined) return "—";
    const n = typeof value === "number" ? value : Number(value);
    if (!Number.isFinite(n)) return "—";
    return n.toLocaleString("tr-TR", {
        minimumFractionDigits: fractionDigits,
        maximumFractionDigits: fractionDigits,
    });
};

const pctFmt = (value) => {
    if (value === null || value === undefined) return "—";
    const n = typeof value === "number" ? value : Number(value);
    if (!Number.isFinite(n)) return "—";
    const sign = n > 0 ? "+" : "";
    return `${sign}${n.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
};

export default function Viop({ keycloak, onAdded }) {
    const { t } = useI18n();
    const [contracts, setContracts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [categories, setCategories] = useState([]); // [] = all
    const [search, setSearch] = useState("");
    const [sortKey, setSortKey] = useState("volumeTl");
    const [sortDir, setSortDir] = useState("desc");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    // Buy modal target + auth guard via the shared hook. Carries symbol +
    // price + multiplier forwarded to AddPositionModal so VIOP positions land
    // on the books with the correct exposure (lots × contract size).
    const [buyTarget, openBuyRaw, clearBuy] = useBuyTarget(keycloak);
    const openBuy = useCallback((contract) => openBuyRaw({
        symbol: contract.symbol,
        price: contract.lastPrice,
        multiplier: VIOP_MULTIPLIERS[contract.category] || 1,
    }), [openBuyRaw]);

    // Fetch the entire universe once; multi-select category filter is
    // applied client-side so we can flip filters instantly without re-hitting
    // the API. `loadVersion` is bumped by the retry button to re-run.
    const [loadVersion, setLoadVersion] = useState(0);
    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(null);
        getViopContracts()
            .then((data) => {
                if (!cancelled) setContracts(Array.isArray(data) ? data : []);
            })
            .catch((e) => {
                console.error("VIOP fetch failed:", e);
                if (!cancelled) setError(t("viop.loadError"));
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [loadVersion]);

    // Per-category row count for the filter chip badges.
    const categoryCounts = useMemo(() => {
        const out = {};
        for (const c of contracts) {
            if (!c.category) continue;
            out[c.category] = (out[c.category] || 0) + 1;
        }
        return out;
    }, [contracts]);

    const filteredRows = useMemo(() => {
        const q = search.trim().toUpperCase();
        const catSet = new Set(categories);
        let list = contracts.filter((c) => {
            if (catSet.size > 0 && !catSet.has(c.category)) return false;
            if (q) {
                const inSymbol = (c.symbol || "").toUpperCase().includes(q);
                const inUnder  = (c.underlying || "").toUpperCase().includes(q);
                const inName   = (c.name || "").toUpperCase().includes(q);
                if (!inSymbol && !inUnder && !inName) return false;
            }
            return true;
        });
        const dir = sortDir === "asc" ? 1 : -1;
        list = [...list].sort((a, b) => {
            const av = a[sortKey];
            const bv = b[sortKey];
            if (av === null || av === undefined) return 1;
            if (bv === null || bv === undefined) return -1;
            if (typeof av === "number" && typeof bv === "number") return (av - bv) * dir;
            return String(av).localeCompare(String(bv), "tr") * dir;
        });
        return list;
    }, [contracts, search, sortKey, sortDir, categories]);

    // Slice the filtered set for the current page. Reset to page 1 whenever
    // the filter/search changes so the user isn't stranded on page 7 of a
    // 3-page result set.
    useEffect(() => { setPage(1); }, [categories, search, pageSize]);
    const totalRows = filteredRows.length;
    const pagedRows = useMemo(() => {
        const start = (page - 1) * pageSize;
        return filteredRows.slice(start, start + pageSize);
    }, [filteredRows, page, pageSize]);

    const updatedAt = useMemo(() => {
        if (contracts.length === 0) return null;
        const latest = contracts.reduce((max, c) => {
            const t = c.updatedAt ? new Date(c.updatedAt).getTime() : 0;
            return t > max ? t : max;
        }, 0);
        if (!latest) return null;
        return new Date(latest).toLocaleString("tr-TR", {
            day: "2-digit", month: "long", hour: "2-digit", minute: "2-digit",
        });
    }, [contracts]);

    const handleSort = (key) => {
        if (sortKey === key) {
            setSortDir((d) => (d === "asc" ? "desc" : "asc"));
        } else {
            setSortKey(key);
            setSortDir("desc");
        }
    };

    return (
        <div style={s.page}>
            <header style={s.header}>
                <div>
                    <h1 style={s.title}>{t("viop.title")} <TermInfo termKey="futures" placement="bottom" /></h1>
                    <p style={s.sub}>
                        {t("viop.subtitle")}
                        {updatedAt && <span style={{ marginLeft: 8 }}>{`${t("viop.lastUpdate")}: `}<strong>{updatedAt}</strong></span>}
                    </p>
                </div>
            </header>

            <div style={s.controls}>
                <CheckboxFilterGroup
                    options={CATEGORY_OPTIONS.map((c) => ({
                        ...c,
                        count: categoryCounts[c.key] || 0,
                    }))}
                    selected={categories}
                    onChange={setCategories}
                    allLabel={t("common.all")}
                />
                <input
                    type="text"
                    placeholder={t("viop.searchPh")}
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    style={s.search}
                />
            </div>

            {error && (
                <div style={s.error}>
                    <span>{error}</span>
                    <button
                        type="button"
                        onClick={() => setLoadVersion((v) => v + 1)}
                        style={s.retryBtn}
                    >
                        🔄 {t("common.retry")}
                    </button>
                </div>
            )}

            {loading ? (
                <div style={s.placeholder}>{t("common.loadingDots")}</div>
            ) : totalRows === 0 ? (
                <div style={s.placeholder}>{t("viop.empty")}</div>
            ) : (
                <>
                    <div style={s.tableWrap} className="fp-table-scroll">
                        <table style={s.table}>
                            <thead>
                                <tr>
                                    <ThSort label={<>{t("viop.colSymbol")} <TermInfo termKey="futures" placement="bottom" /></>}      onClick={() => handleSort("symbol")}     active={sortKey === "symbol"}     dir={sortDir} align="left" />
                                    <ThSort label={<>{t("viop.colUnderlying")} <TermInfo termKey="underlying" placement="bottom" /></>}  onClick={() => handleSort("underlying")} active={sortKey === "underlying"} dir={sortDir} align="left" />
                                    <ThSort label={<>{t("viop.colMaturity")} <TermInfo termKey="maturity" placement="bottom" /></>}    onClick={() => handleSort("maturityYear")} active={sortKey === "maturityYear"} dir={sortDir} align="left" />
                                    <ThSort label={t("viop.colLast")}        onClick={() => handleSort("lastPrice")}  active={sortKey === "lastPrice"}  dir={sortDir} />
                                    <ThSort label={t("viop.colChange")}      onClick={() => handleSort("changePct")}  active={sortKey === "changePct"}  dir={sortDir} />
                                    <ThSort label={<>{t("viop.colVolumeTl")} <TermInfo termKey="volume" placement="bottom" /></>}    onClick={() => handleSort("volumeTl")}   active={sortKey === "volumeTl"}   dir={sortDir} />
                                    <ThSort label={t("viop.colVolumeQty")}   onClick={() => handleSort("volumeLots")} active={sortKey === "volumeLots"} dir={sortDir} />
                                    <th style={{ ...s.th, textAlign: "right", cursor: "default" }}>İşlemler</th>
                                </tr>
                            </thead>
                            <tbody>
                                {pagedRows.map((c) => {
                                    const pct = c.changePct == null ? null : Number(c.changePct);
                                    const tone = pct == null ? "neutral" : (pct > 0 ? "up" : (pct < 0 ? "down" : "neutral"));
                                    return (
                                        <tr key={c.id} style={s.tr}>
                                            <td style={s.tdSymbol}>{c.symbol}</td>
                                            <td>{c.underlying}</td>
                                            <td style={{ color: "var(--text-muted)" }}>
                                                {shortMonthName((c.maturityMonth || 1) - 1)} {c.maturityYear}
                                            </td>
                                            <td style={s.tdNum}>{numFmt(c.lastPrice, 2)}</td>
                                            <td style={{ ...s.tdNum, color: TONE_COLOR[tone], fontWeight: 600 }}>
                                                {pctFmt(c.changePct)}
                                            </td>
                                            <td style={s.tdNum}>{numFmt(c.volumeTl, 0)}</td>
                                            <td style={s.tdNum}>{numFmt(c.volumeLots, 0)}</td>
                                            <td style={{ ...s.tdNum, padding: "8px 14px" }}>
                                                <button
                                                    type="button"
                                                    style={s.buyBtn}
                                                    onClick={() => openBuy(c)}
                                                    title={`Lot çarpanı: ${VIOP_MULTIPLIERS[c.category] || 1}`}
                                                >
                                                    Al
                                                </button>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                    <Pagination
                        page={page}
                        pageSize={pageSize}
                        total={totalRows}
                        onPageChange={setPage}
                        onPageSizeChange={setPageSize}
                    />
                </>
            )}

            <p style={s.footer}>
                {t("viop.footerNote")}
            </p>

            <BuyModalMount target={buyTarget} clear={clearBuy} keycloak={keycloak} onAdded={onAdded} />
        </div>
    );
}

Viop.propTypes = {
    keycloak: PropTypes.object,
    onAdded: PropTypes.func,
};

function ThSort({ label, onClick, active, dir, align = "right" }) {
    const arrow = !active ? "" : (dir === "asc" ? " ▲" : " ▼");
    return (
        <th
            onClick={onClick}
            style={{
                ...s.th,
                textAlign: align,
                color: active ? "var(--text-primary)" : "var(--text-muted)",
            }}
        >
            {label}{arrow}
        </th>
    );
}

ThSort.propTypes = {
    label: PropTypes.node,
    onClick: PropTypes.func,
    active: PropTypes.bool,
    dir: PropTypes.string,
    align: PropTypes.string,
};

const TONE_COLOR = {
    up: "var(--green, #10b981)",
    down: "var(--red, #ef4444)",
    neutral: "var(--text-secondary)",
};

const s = {
    page: { maxWidth: 1280, margin: "0 auto", padding: "0 4px" },
    header: { marginBottom: 18 },
    title: { fontSize: 24, fontWeight: 700, margin: 0, color: "var(--text-primary)" },
    sub: { fontSize: 13, color: "var(--text-muted)", margin: "6px 0 0" },
    controls: {
        display: "flex", flexWrap: "wrap", gap: 12, alignItems: "center",
        justifyContent: "space-between", marginBottom: 16,
    },
    tabs: { display: "flex", flexWrap: "wrap", gap: 6 },
    tab: {
        padding: "8px 14px", borderRadius: 8,
        background: "var(--bg-card)", border: "1px solid var(--border-card)",
        color: "var(--text-secondary)", fontSize: 13, fontWeight: 500, cursor: "pointer",
    },
    tabActive: {
        background: "var(--accent-hover-bg)", color: "var(--accent-solid)",
        borderColor: "var(--accent-solid)",
    },
    search: {
        flex: "1 1 280px", maxWidth: 360,
        padding: "9px 12px", borderRadius: 8,
        background: "var(--input-bg)", border: "1px solid var(--border-card)",
        color: "var(--text-primary)", fontSize: 13,
    },
    tableWrap: {
        background: "var(--bg-card)", border: "1px solid var(--border-card)",
        borderRadius: 10, overflow: "auto",
    },
    table: { width: "100%", borderCollapse: "collapse", fontSize: 13 },
    th: {
        padding: "12px 14px", fontSize: 11, fontWeight: 700,
        letterSpacing: "0.05em", textTransform: "uppercase",
        borderBottom: "1px solid var(--border-card)", cursor: "pointer",
        whiteSpace: "nowrap", userSelect: "none",
    },
    tr: { borderBottom: "1px solid var(--border-soft, var(--border-card))" },
    tdSymbol: { padding: "10px 14px", fontWeight: 600, color: "var(--text-primary)", whiteSpace: "nowrap" },
    tdNum: {
        padding: "10px 14px", textAlign: "right",
        fontVariantNumeric: "tabular-nums", whiteSpace: "nowrap",
    },
    placeholder: { padding: 40, textAlign: "center", color: "var(--text-muted)" },
    error: {
        padding: 12, marginBottom: 12, borderRadius: 8,
        background: "rgba(239, 68, 68, 0.1)", color: "#ef4444",
        border: "1px solid rgba(239, 68, 68, 0.3)",
        display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12,
    },
    retryBtn: {
        padding: "6px 12px",
        background: "var(--bg-panel)",
        color: "var(--text-primary)",
        border: "1px solid var(--border)",
        borderRadius: 6,
        fontSize: 12,
        fontWeight: 500,
        cursor: "pointer",
        whiteSpace: "nowrap",
    },
    footer: { fontSize: 12, color: "var(--text-muted)", marginTop: 16, lineHeight: 1.5 },
    buyBtn: {
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
};

s.tdSymbol = { ...s.tdSymbol, fontVariantNumeric: "tabular-nums" };

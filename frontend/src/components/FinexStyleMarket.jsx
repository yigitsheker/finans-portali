import { useEffect, useMemo, useState } from "react";
import {
    getMarketSummary,
    getMarketHistoryBatch,
    upsertPosition,
} from "../api/portfolioApi";
import Modal from "./Modal";
import InstrumentChartModal from "./InstrumentChartModal";
import CompareInstrumentsModal from "./CompareInstrumentsModal";
import notify from "../utils/notify";
import { readHistoryCache, writeHistoryCache } from "../utils/historyCache";
import { LWSparkline } from "./common/LWSparkline";
import Pagination from "./common/Pagination";
import CheckboxFilterGroup from "./common/CheckboxFilterGroup";
import { usePriceDisplay } from "../contexts/CurrencyDisplayContext";
import { useI18n } from "../contexts/I18nContext";

// SVG Icon Components
const AllIcon = () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="3" y="3" width="7" height="7" rx="1" fill="currentColor"/>
        <rect x="14" y="3" width="7" height="7" rx="1" fill="currentColor"/>
        <rect x="3" y="14" width="7" height="7" rx="1" fill="currentColor"/>
        <rect x="14" y="14" width="7" height="7" rx="1" fill="currentColor"/>
    </svg>
);

const BISTIcon = () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="12" cy="12" r="10" fill="#e30a17"/>
        <circle cx="12" cy="12" r="8" fill="#ffffff"/>
        <path d="M12 6L14 10H10L12 6Z" fill="#e30a17"/>
        <circle cx="12" cy="12" r="3" fill="#e30a17"/>
    </svg>
);

const GlobalIcon = () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
        <path d="M2 12H22M12 2C14.5 4.5 16 8 16 12C16 16 14.5 19.5 12 22M12 2C9.5 4.5 8 8 8 12C8 16 9.5 19.5 12 22" stroke="currentColor" strokeWidth="2"/>
    </svg>
);

const ChartIcon = () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M3 17L9 11L13 15L21 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M16 7H21V12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
);

const CheckIcon = () => (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M5 13L9 17L19 7" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
);

const UpArrowIcon = () => (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M12 19V5M5 12L12 5L19 12" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
);

const DownArrowIcon = () => (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M12 5V19M5 12L12 19L19 12" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
);

// BIST Index Compositions
const BIST30_STOCKS = [
    "AKBNK", "ARCLK", "ASELS", "BIMAS", "EKGYO", "EREGL", "FROTO", "GARAN",
    "GUBRF", "HEKTS", "ISCTR", "KCHOL", "KOZAA", "KOZAL", "KRDMD", "PETKM",
    "PGSUS", "SAHOL", "SASA", "SISE", "TAVHL", "TCELL", "THYAO", "TKFEN",
    "TOASO", "TTKOM", "TUPRS", "VAKBN", "VESTL", "YKBNK"
];

const BIST50_STOCKS = [
    ...BIST30_STOCKS,
    "AEFES", "AKSA", "ALARK", "ALGYO", "AYGAZ", "DOHOL", "ENKAI", "ENJSA",
    "GESAN", "HALKB", "ISGYO", "KLMSN", "KONTR", "ODAS", "OYAKC", "SOKM",
    "TSKB", "TTRAK", "ULKER", "ZOREN"
];

const BIST100_STOCKS = [
    ...BIST50_STOCKS,
    "ADEL", "ADESE", "AFYON", "AHGAZ", "AKCNS", "AKFGY", "AKFYE", "AKGRT",
    "AKSEN", "AKSGY", "AKSUE", "ALBRK", "ALCAR", "ALCTL", "ALFAS", "ALKIM",
    "ALMAD", "ANELE", "ANGEN", "ANHYT", "ANSGR", "ARDYZ", "ARENA", "ARMDA",
    "ARTMS", "ARZUM", "ASTOR", "ASUZU", "ATAGY", "ATAKP", "ATATP", "AVGYO",
    "AVHOL", "AVOD", "AVTUR", "AYCES", "AYEN", "BAGFS", "BAKAB", "BALAT",
    "BANVT", "BARMA", "BASGZ", "BAYRK", "BEGYO", "BERA", "BEYAZ", "BFREN"
];

export default function FinexStyleMarket({
    keycloak,
    onAdded,
    theme,
    onThemeToggle,
    onLogout,
    onAlertsClick,
    filterType,
    instruments: externalInstruments,
    onAddToWatchlist,
    onRemoveFromWatchlist,
    watchlistSymbols = []
}) {
    const { format: formatPrice, convert: convertPrice } = usePriceDisplay();
    const { t } = useI18n();
    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    // Column sort. null → default (grouped/natural) order. Click toggles
    // direction on the same column; clicking another column starts at "desc".
    const [sortField, setSortField] = useState(null); // "price" | "change" | null
    const [sortDir, setSortDir] = useState("desc");   // "asc" | "desc"
    const [err, setErr] = useState(null);
    const [selected, setSelected] = useState(null);
    const [addTarget, setAddTarget] = useState(null);
    const [addQty, setAddQty] = useState(1);
    const [addSaving, setAddSaving] = useState(false);
    const [addErr, setAddErr] = useState(null);
    // Lot vs budget mode — mirrors the Portfolio AddPositionModal so the
    // same affordance exists wherever a user can place a buy.
    const [addMode, setAddMode] = useState("quantity"); // "quantity" | "amount"
    const [addAmount, setAddAmount] = useState(0);
    const [compareTarget, setCompareTarget] = useState(null);
    const [indexFilter, setIndexFilter] = useState(null);
    // Multi-select stock category filter. Empty array == "show all".
    // Replaces the old single-string state to support ticking both BIST and
    // STOCK at once.
    const [categoryFilters, setCategoryFilters] = useState([]);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);

    // Sparkline data: symbol → last 30 daily closes
    const [sparklines, setSparklines] = useState({});

    /**
     * Open the buy-modal only for authenticated users. If the user isn't
     * logged in, prompt them and route to Keycloak login on confirm.
     * Without this guard the modal opens for anonymous visitors and the
     * portfolio POST fails server-side with a 401.
     */
    const openBuyModalIfAuthed = (item) => {
        const authed = keycloak?.authenticated === true;
        if (!authed) {
            const goLogin = window.confirm(t("market.authPrompt"));
            if (goLogin && keycloak?.login) {
                keycloak.login({ redirectUri: window.location.href });
            }
            return;
        }
        setAddTarget(item);
        setAddQty(1);
        setAddMode("quantity");
        setAddAmount(0);
        setAddErr(null);
    };

    useEffect(() => {
        // If external instruments are provided (watchlist mode), use them directly
        if (externalInstruments) {
            setItems(externalInstruments);
            setLoading(false);
            return;
        }

        // Otherwise, fetch from API
        getMarketSummary()
            .then((data) => {
                setItems(data);
            })
            .catch((e) => setErr(e?.message ?? "Fetch error"))
            .finally(() => setLoading(false));
    }, [externalInstruments]);

    const indices = useMemo(
        () => items.filter((i) => i.type === "INDEX"),
        [items]
    );

    const filtered = useMemo(() => {
        let list = items.filter((i) => i.type !== "INDEX");

        // Apply filterType prop if provided (for dedicated pages)
        if (filterType) {
            if (filterType === "STOCK") {
                // For stocks page, show both STOCK and BIST types
                list = list.filter((i) => i.type === "STOCK" || i.type === "BIST");
            } else {
                list = list.filter((i) => i.type === filterType);
            }
        }

        // Apply category filter (BIST vs STOCK)
        if (categoryFilters.length > 0) {
            const set = new Set(categoryFilters);
            list = list.filter((i) => set.has(i.type));
        }

        // Apply BIST index filter
        if (indexFilter === "XU030") {
            list = list.filter((i) => BIST30_STOCKS.includes(i.symbol));
        } else if (indexFilter === "XU050") {
            list = list.filter((i) => BIST50_STOCKS.includes(i.symbol));
        } else if (indexFilter === "XU100") {
            list = list.filter((i) => BIST100_STOCKS.includes(i.symbol));
        }

        if (search.trim()) {
            const q = search.trim().toUpperCase();
            list = list.filter((i) => i.symbol.includes(q) || i.name.toUpperCase().includes(q));
        }
        return list;
    }, [items, search, indexFilter, categoryFilters, filterType]);

    // Apply sort on top of the filtered list. Price sort normalises currencies
    // through usePriceDisplay.convert(): a $200 stock and a ₺294 stock must
    // both be compared in the same unit (the active display mode), otherwise
    // raw `last` values would put BIST tickers above USD ones for the wrong
    // reason. Change sort is currency-agnostic — percent is unitless.
    const sorted = useMemo(() => {
        if (!sortField) return filtered;
        const dirMul = sortDir === "asc" ? 1 : -1;
        const keyOf = (it) => {
            if (sortField === "change") {
                const v = Number(it.changePct);
                return Number.isFinite(v) ? v : -Infinity;
            }
            // price
            const c = convertPrice(it.last, it.type, it.symbol);
            const v = c?.value;
            return Number.isFinite(v) ? v : -Infinity;
        };
        return [...filtered].sort((a, b) => {
            const va = keyOf(a);
            const vb = keyOf(b);
            if (va === vb) return 0;
            return va < vb ? -1 * dirMul : 1 * dirMul;
        });
    }, [filtered, sortField, sortDir, convertPrice]);

    const toggleSort = (field) => {
        if (sortField !== field) {
            setSortField(field);
            setSortDir("desc");
        } else if (sortDir === "desc") {
            setSortDir("asc");
        } else {
            // third click clears the sort, restoring natural/grouped order
            setSortField(null);
            setSortDir("desc");
        }
    };

    // Group stocks by category (BIST vs STOCK) - only when no filters are
    // active AND no column sort is in effect. A user-chosen sort spans both
    // groups, so keeping the split would silently override it.
    const groupedStocks = useMemo(() => {
        if (filterType !== "STOCK" || categoryFilters.length > 0 || indexFilter || sortField) return null;

        const groups = {};
        filtered.forEach(item => {
            const category = item.type === "BIST" ? t("market.groupBist") : t("market.groupIntl");
            if (!groups[category]) {
                groups[category] = [];
            }
            groups[category].push(item);
        });
        return groups;
    }, [filtered, filterType, categoryFilters, indexFilter, sortField]);

    // Reset to first page whenever the visible set changes — prevents the
    // user from being stuck on "page 7" of a 2-page result after a filter.
    useEffect(() => { setPage(1); }, [search, indexFilter, categoryFilters, filterType, pageSize, sortField, sortDir]);

    // Pagination is only meaningful in the flat (non-grouped) view; the
    // grouped stocks view stays as-is because the groups themselves are
    // already a navigational chunking.
    const totalFiltered = sorted.length;
    const pagedFiltered = useMemo(() => {
        if (groupedStocks) return sorted; // not used; left as the sorted list for safety
        const start = (page - 1) * pageSize;
        return sorted.slice(start, start + pageSize);
    }, [sorted, groupedStocks, page, pageSize]);

    // Sparkline data loader — cache-first, then one batch network call for
    // anything stale or missing.
    //
    // Previously: 50 sequential getMarketHistory calls (~7.5 s wall time)
    //             and a hard failure when the backend / Yahoo was unreachable.
    // Now      : (a) paint instantly from localStorage cache if present, even
    //                if stale (offline survives on the last-known curve), and
    //            (b) one HTTP request for the symbols whose cache is stale,
    //                writing fresh results back to the cache.
    useEffect(() => {
        if (filtered.length === 0) return;
        let cancelled = false;
        const PERIOD = "1M";
        const visible = filtered.slice(0, 50);

        // Step 1: synchronous cache hydration. Fills sparklines from cache
        // even for stale entries — those still render and look right while
        // the background refresh runs.
        const cachedSeed = {};
        const needsFetch = [];
        visible.forEach((item) => {
            if (sparklines[item.symbol]) return; // already in component state
            const cached = readHistoryCache(item.symbol, PERIOD);
            if (cached) {
                cachedSeed[item.symbol] = cached.data.map((h) => ({
                    time: h.day.split("T")[0],
                    value: h.close,
                }));
                if (!cached.fresh) needsFetch.push(item.symbol);
            } else {
                needsFetch.push(item.symbol);
            }
        });
        if (Object.keys(cachedSeed).length > 0) {
            setSparklines((prev) => ({ ...cachedSeed, ...prev }));
        }
        if (needsFetch.length === 0) return;

        // Step 2: one batch request for the missing/stale ones.
        (async () => {
            try {
                const map = await getMarketHistoryBatch(needsFetch, PERIOD);
                if (cancelled) return;
                const update = {};
                for (const sym of needsFetch) {
                    const history = map[sym];
                    if (Array.isArray(history) && history.length > 0) {
                        update[sym] = history.map((h) => ({
                            time: h.day.split("T")[0],
                            value: h.close,
                        }));
                        writeHistoryCache(sym, PERIOD, history);
                    } else if (!cachedSeed[sym]) {
                        // No fresh data and no stale fallback — record empty
                        // so we don't keep retrying within this mount.
                        update[sym] = [];
                    }
                }
                if (Object.keys(update).length > 0) {
                    setSparklines((prev) => ({ ...prev, ...update }));
                }
            } catch {
                // Network/backend offline. We already painted whatever the
                // cache had; mark the rest as empty so the loop ends.
                if (cancelled) return;
                const update = {};
                needsFetch.forEach((sym) => {
                    if (!cachedSeed[sym]) update[sym] = [];
                });
                if (Object.keys(update).length > 0) {
                    setSparklines((prev) => ({ ...prev, ...update }));
                }
            }
        })();

        return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [filtered]);

    // In amount mode the buyer enters a budget (e.g. ₺5000); we floor-divide by
    // the live price to get whole lots, leaving small change ("artık para")
    // unused. This avoids fractional shares that the backend doesn't accept.
    const effectiveQty = useMemo(() => {
        if (addMode !== "amount") return Math.max(0, Number(addQty) || 0);
        const price = addTarget?.last;
        if (!price || price <= 0) return 0;
        return Math.floor((Number(addAmount) || 0) / price);
    }, [addMode, addAmount, addQty, addTarget]);

    const amountLeftover = useMemo(() => {
        if (addMode !== "amount") return 0;
        const price = addTarget?.last;
        if (!price || price <= 0 || effectiveQty <= 0) return 0;
        return Math.max(0, (Number(addAmount) || 0) - effectiveQty * price);
    }, [addMode, addAmount, addTarget, effectiveQty]);

    const addTotal = useMemo(() => {
        if (!addTarget || !effectiveQty || effectiveQty <= 0) return 0;
        return Number((addTarget.last * effectiveQty).toFixed(4));
    }, [addTarget, effectiveQty]);

    async function onConfirmAdd() {
        if (!addTarget) return;
        if (!keycloak) return;
        if (!effectiveQty || effectiveQty <= 0) {
            return setAddErr(
                addMode === "amount"
                    ? t("market.errAmountTooSmall")
                    : t("market.errQtyMin")
            );
        }
        try {
            setAddSaving(true);
            setAddErr(null);
            await upsertPosition(keycloak, {
                symbol: addTarget.symbol,
                quantity: effectiveQty,
                avgCost: addTarget.last,
            });
            // Toast lives in the Notification → İşlem Bildirimleri category
            // and is filtered by the Settings → Bildirimler preferences.
            notify.tx(`${addTarget.symbol}: ${effectiveQty} ${t("market.qtyUnit")} alındı`);
            setAddTarget(null);
            onAdded?.();
        } catch (e) {
            setAddErr(e?.message ?? "Add error");
        } finally {
            setAddSaving(false);
        }
    }

    if (loading) {
        return (
            <div style={s.loading}>
                <div style={s.spinner}></div>
                <div style={{ color: "var(--text-muted)", marginTop: 12 }}>{t("common.loading")}</div>
            </div>
        );
    }

    if (err) {
        return (
            <div style={s.error}>
                <div style={{ fontSize: 48, marginBottom: 16 }}>⚠️</div>
                <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>{t("common.error")}</div>
                <div style={{ color: "var(--text-muted)", fontSize: 13 }}>{err}</div>
            </div>
        );
    }

    return (
        <div style={s.root}>
            {/* Header Section with Title and Index Cards */}
            <div style={s.headerSection}>
                <div style={s.titleRow}>
                    <div style={s.titleArea}>
                        <h1 style={s.pageTitle}>
                            {filterType === "CRYPTO" ? t("market.pageCrypto") :
                             filterType === "STOCK" ? t("market.pageStocks") :
                             filterType === "COMMODITY" ? t("market.pageCommodities") :
                             t("market.pagePrices")}
                        </h1>
                        <p style={s.pageSubtitle}>
                            {filterType === "CRYPTO" ? t("market.subCrypto") :
                             filterType === "STOCK" ? t("market.subStocks") :
                             filterType === "COMMODITY" ? t("market.subCommodities") :
                             t("market.subStocks")}
                        </p>
                    </div>

                    {/* User Controls */}
                    <div style={s.userControls}>
                        {onAlertsClick && (
                            <button
                                style={s.iconBtn}
                                onClick={onAlertsClick}
                                title={t("market.fiyatAlarmlari")}
                            >
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M18 16V11C18 7.69 15.31 5 12 5C8.69 5 6 7.69 6 11V16L4 18V19H20V18L18 16Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
                                    <path d="M10 21C10 22.1 10.9 23 12 23C13.1 23 14 22.1 14 21" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
                                </svg>
                            </button>
                        )}
                        {onThemeToggle && (
                            <button
                                style={s.iconBtn}
                                onClick={onThemeToggle}
                                title={theme === "dark" ? t("topbar.themeLight") : t("topbar.themeDark")}
                            >
                                {theme === "dark" ? "☀️" : "🌙"}
                            </button>
                        )}
                        {onLogout && (
                            <button style={s.logoutBtn} onClick={onLogout}>
                                {t("topbar.logout")}
                            </button>
                        )}
                    </div>
                </div>

                {/* Index Cards - Only show for STOCK type */}
                {filterType === "STOCK" && indices.length > 0 && (
                    <>
                        <div style={s.indexGrid} className="fp-index-grid">
                            {indices.map((idx) => {
                                const pos = idx.changePct >= 0;
                                const color = pos ? "#10b981" : "#ef4444";
                                const isActive = indexFilter === idx.symbol;
                                return (
                                    <div
                                        key={idx.symbol}
                                        style={{
                                            ...s.indexCard,
                                            ...(isActive ? s.indexCardActive : {}),
                                            cursor: "pointer",
                                        }}
                                        onClick={() => {
                                            if (indexFilter === idx.symbol) {
                                                setIndexFilter(null);
                                            } else {
                                                setIndexFilter(idx.symbol);
                                                setCategoryFilters([]); // Clear category filter when index is selected
                                            }
                                        }}
                                    >
                                        <div style={s.indexLabel}>
                                            {idx.symbol}
                                            {isActive && <span style={{ marginLeft: 8, color: "#00ff00", fontSize: 10 }}><CheckIcon /></span>}
                                        </div>
                                        <div style={s.indexPrice}>
                                            {idx.last?.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                        </div>
                                        <div style={{ color, fontSize: 13, fontWeight: 600, marginTop: 6, display: "flex", alignItems: "center", gap: 4 }}>
                                            <span>{pos ? <UpArrowIcon /> : <DownArrowIcon />}</span>
                                            <span>{pos ? "+" : ""}{idx.changePct?.toFixed(2)}%</span>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>

                        {/* Multi-select category checkboxes — replaces the single-active
                            BIST/STOCK button row. Picking an index card above
                            clears these via setCategoryFilters([]). */}
                        <div style={s.categoryFilterContainer}>
                            <CheckboxFilterGroup
                                options={[
                                    { key: "BIST",  label: t("market.groupBist") },
                                    { key: "STOCK", label: t("market.groupIntl") },
                                ]}
                                selected={categoryFilters}
                                onChange={(next) => {
                                    setCategoryFilters(next);
                                    if (next.length > 0) setIndexFilter(null);
                                }}
                                allLabel={t("common.all")}
                            />
                        </div>
                    </>
                )}
            </div>

            {/* Filter banner — surfaces the active index filter so the user can
                clear it without scrolling back to the index cards. */}
            {filterType === "STOCK" && indexFilter && (
                <div style={s.filterBanner}>
                    <span style={{ fontSize: 13, color: "var(--text-primary)", display: "flex", alignItems: "center", gap: 6 }}>
                        <ChartIcon /> <strong>{indexFilter}</strong> {t("market.indexBanner")}
                    </span>
                    <button
                        style={s.clearFilterBtn}
                        onClick={() => setIndexFilter(null)}
                    >
                        {t("market.clearFilter")}
                    </button>
                </div>
            )}

            {/* Search and Filter */}
            <div style={s.controls}>
                <div style={s.searchBox}>
                    <span style={{ fontSize: 14, color: "var(--text-muted)" }}>🔍</span>
                    <input
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder={filterType === "CRYPTO" ? t("market.searchCrypto") : filterType === "COMMODITY" ? t("market.searchCommodities") : t("market.searchStocks")}
                        style={s.searchInput}
                    />
                </div>
            </div>

            {/* Main Layout: Full Width List */}
            <div style={s.mainLayout}>
                <div style={s.tableContainer}>
                    {/* Table Header */}
                    <div style={s.tableHeader}>
                        <div style={s.colHisse}>{t("market.colSymbol")}</div>
                        <div
                            style={{ ...s.colFiyat, ...s.sortableHeader, ...(sortField === "price" ? s.sortableHeaderActive : {}) }}
                            onClick={() => toggleSort("price")}
                            title={t("market.sortByPrice")}
                        >
                            {t("market.colPrice")}
                            <span style={s.sortArrow}>
                                {sortField === "price" ? (sortDir === "asc" ? "▲" : "▼") : "↕"}
                            </span>
                        </div>
                        <div
                            style={{ ...s.colDegisim, ...s.sortableHeader, ...(sortField === "change" ? s.sortableHeaderActive : {}) }}
                            onClick={() => toggleSort("change")}
                            title={t("market.sortByChange")}
                        >
                            {t("market.colChange")}
                            <span style={s.sortArrow}>
                                {sortField === "change" ? (sortDir === "asc" ? "▲" : "▼") : "↕"}
                            </span>
                        </div>
                        <div style={s.colHacim}>{t("market.colVolume")}</div>
                        <div style={s.colGrafik}>{t("market.colChart")}</div>
                        <div style={s.colIslem}>{t("market.colActions")}</div>
                    </div>

                    {/* Table Body */}
                    <div style={s.tableBody}>
                        {groupedStocks ? (
                            // Grouped view for stocks page
                            Object.entries(groupedStocks).map(([category, categoryItems]) => (
                                <div key={category}>
                                    {categoryItems.map((item) => {
                                        const pos = item.changePct >= 0;
                                        const color = pos ? "#10b981" : "#ef4444";
                                        return (
                                            <div
                                                key={item.symbol}
                                                style={s.tableRow}
                                                onClick={() => setSelected(item)}
                                            >
                                                <div style={s.colHisse}>
                                                    <div style={s.stockSymbol}>{item.symbol}</div>
                                                    <div style={s.stockName}>{item.name}</div>
                                                </div>
                                                <div style={s.colFiyat}>
                                                    <div style={s.stockPrice}>
                                                        {formatPrice(item.last, item.type, { symbol: item.symbol })}
                                                    </div>
                                                </div>
                                                <div style={s.colDegisim}>
                                                    <div style={{ color, fontSize: 13, fontWeight: 600 }}>
                                                        {pos ? "▲" : "▼"} {pos ? "+" : ""}
                                                        {item.changePct?.toFixed(2)}%
                                                    </div>
                                                </div>
                                                <div style={s.colHacim}>
                                                    <div style={{ fontSize: 12, color: "var(--text-muted)" }}>
                                                        {(Math.random() * 100).toFixed(1)}M
                                                    </div>
                                                </div>
                                                <div style={s.colGrafik}>
                                                    <LWSparkline
                                                        data={sparklines[item.symbol] ?? []}
                                                        positive={pos}
                                                        width={100}
                                                        height={36}
                                                    />
                                                </div>
                                                <div style={s.colIslem}>
                                                    {onAddToWatchlist && onRemoveFromWatchlist ? (
                                                        <div style={{ display: 'flex', gap: '5px' }}>
                                                            {watchlistSymbols.includes(item.symbol) ? (
                                                                <button
                                                                    style={{...s.actionBtn, background: 'var(--red)', borderColor: 'var(--red)'}}
                                                                    onClick={(e) => {
                                                                        e.stopPropagation();
                                                                        onRemoveFromWatchlist(item.symbol);
                                                                    }}
                                                                    title={t("market.removeFromList")}
                                                                >
                                                                    ★
                                                                </button>
                                                            ) : (
                                                                <button
                                                                    style={{...s.actionBtn, background: 'var(--warning)', borderColor: 'var(--warning)'}}
                                                                    onClick={(e) => {
                                                                        e.stopPropagation();
                                                                        onAddToWatchlist(item.symbol);
                                                                    }}
                                                                    title={t("market.addToList")}
                                                                >
                                                                    ☆
                                                                </button>
                                                            )}
                                                            <button
                                                                style={s.actionBtn}
                                                                onClick={(e) => {
                                                                    e.stopPropagation();
                                                                    openBuyModalIfAuthed(item);
                                                                }}
                                                            >
                                                                {t("market.buy")}
                                                            </button>
                                                        </div>
                                                    ) : (
                                                        <button
                                                            style={s.actionBtn}
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                openBuyModalIfAuthed(item);
                                                            }}
                                                        >
                                                            {t("market.buy")}
                                                        </button>
                                                    )}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            ))
                        ) : (
                            // Regular view for other pages — paginated slice.
                            pagedFiltered.map((item) => {
                                const pos = item.changePct >= 0;
                                const color = pos ? "#10b981" : "#ef4444";
                                return (
                                    <div
                                        key={item.symbol}
                                        style={s.tableRow}
                                        onClick={() => setSelected(item)}
                                    >
                                        <div style={s.colHisse}>
                                            <div style={s.stockSymbol}>{item.symbol}</div>
                                            <div style={s.stockName}>{item.name}</div>
                                        </div>
                                        <div style={s.colFiyat}>
                                            <div style={s.stockPrice}>
                                                {formatPrice(item.last, item.type, { symbol: item.symbol })}
                                            </div>
                                        </div>
                                        <div style={s.colDegisim}>
                                            <div style={{ color, fontSize: 13, fontWeight: 600 }}>
                                                {pos ? "▲" : "▼"} {pos ? "+" : ""}
                                                {item.changePct?.toFixed(2)}%
                                            </div>
                                        </div>
                                        <div style={s.colHacim}>
                                            <div style={{ fontSize: 12, color: "var(--text-muted)" }}>
                                                {(Math.random() * 100).toFixed(1)}M
                                            </div>
                                        </div>
                                        <div style={s.colGrafik}>
                                            <LWSparkline
                                                data={sparklines[item.symbol] ?? []}
                                                positive={pos}
                                                width={100}
                                                height={36}
                                            />
                                        </div>
                                        <div style={s.colIslem}>
                                            {onAddToWatchlist && onRemoveFromWatchlist ? (
                                                <div style={{ display: 'flex', gap: '5px' }}>
                                                    {watchlistSymbols.includes(item.symbol) ? (
                                                        <button
                                                            style={{...s.actionBtn, background: 'var(--red)', borderColor: 'var(--red)'}}
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                onRemoveFromWatchlist(item.symbol);
                                                            }}
                                                            title={t("market.removeFromList")}
                                                        >
                                                            ★
                                                        </button>
                                                    ) : (
                                                        <button
                                                            style={{...s.actionBtn, background: 'var(--warning)', borderColor: 'var(--warning)'}}
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                onAddToWatchlist(item.symbol);
                                                            }}
                                                            title={t("market.addToList")}
                                                        >
                                                            ☆
                                                        </button>
                                                    )}
                                                    <button
                                                        style={s.actionBtn}
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            openBuyModalIfAuthed(item);
                                                        }}
                                                    >
                                                        {t("market.buy")}
                                                    </button>
                                                </div>
                                            ) : (
                                                <button
                                                    style={s.actionBtn}
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        openBuyModalIfAuthed(item);
                                                    }}
                                                >
                                                    {t("market.buy")}
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                );
                            })
                        )}
                    </div>
                </div>
            </div>

            {/* Pagination — flat view only; the grouped Stocks view manages
                its own structure and would fight against page slicing. */}
            {!groupedStocks && (
                <Pagination
                    page={page}
                    pageSize={pageSize}
                    total={totalFiltered}
                    onPageChange={setPage}
                    onPageSizeChange={setPageSize}
                />
            )}

            {/* Modals */}
            <InstrumentChartModal
                instrument={selected}
                onClose={() => setSelected(null)}
                keycloak={keycloak}
                onAddToPortfolio={(instrument) => {
                    setSelected(null);
                    openBuyModalIfAuthed(instrument);
                }}
                onCompare={(instrument) => {
                    setCompareTarget(instrument);
                    setSelected(null);
                }}
            />

            <CompareInstrumentsModal
                baseInstrument={compareTarget}
                onClose={() => setCompareTarget(null)}
            />

            <Modal
                open={!!addTarget}
                title={t("market.modalTitle") + " - " + (addTarget?.symbol ?? "")}
                onClose={() => setAddTarget(null)}
                footer={
                    <>
                        <button style={s.ghostBtn} onClick={() => setAddTarget(null)} disabled={addSaving}>
                            {t("common.cancel")}
                        </button>
                        <button style={s.primaryBtn} onClick={onConfirmAdd} disabled={addSaving}>
                            {addSaving ? t("market.processing") : t("common.confirm")}
                        </button>
                    </>
                }
            >
                {addTarget && (() => {
                    const sym = addTarget.type === "BIST" ? "₺" : "$";
                    const tabBtn = (active) => ({
                        flex: 1,
                        padding: "8px 12px",
                        border: "1px solid var(--border-card)",
                        background: active ? "var(--accent-hover-bg, var(--accent))" : "var(--input-bg)",
                        color: active ? "var(--accent-solid)" : "var(--text-muted)",
                        fontSize: 12,
                        fontWeight: 600,
                        cursor: "pointer",
                        borderRadius: 6,
                        transition: "all 0.15s",
                    });
                    return (
                        <div style={{ display: "grid", gap: 14 }}>
                            <div style={s.infoBox}>
                                <div style={s.infoRow}>
                                    <span style={{ color: "var(--text-muted)", fontSize: 13 }}>{t("market.currentPrice")}</span>
                                    <span style={{ color: "var(--text-primary)", fontWeight: 700, fontSize: 18 }}>
                                        {sym}{addTarget.last?.toLocaleString("tr-TR")}
                                    </span>
                                </div>
                            </div>

                            {/* Mode toggle — same affordance as PortfolioPage AddPositionModal */}
                            <div style={{ display: "flex", gap: 6 }}>
                                <button type="button" style={tabBtn(addMode === "quantity")} onClick={() => setAddMode("quantity")}>
                                    {t("market.qty")}
                                </button>
                                <button type="button" style={tabBtn(addMode === "amount")} onClick={() => setAddMode("amount")}>
                                    {t("market.amount")}
                                </button>
                            </div>

                            {addMode === "quantity" ? (
                                <div style={{ display: "grid", gap: 6 }}>
                                    <div style={{ fontSize: 12, color: "var(--text-muted)" }}>{t("market.qty")}</div>
                                    <input
                                        type="number"
                                        value={addQty}
                                        min={1}
                                        onChange={(e) => setAddQty(Number(e.target.value))}
                                        style={s.input}
                                        autoFocus
                                    />
                                </div>
                            ) : (
                                <>
                                    <div style={{ display: "grid", gap: 6 }}>
                                        <div style={{ fontSize: 12, color: "var(--text-muted)" }}>{`${t("market.amount")} (${sym})`}</div>
                                        <input
                                            type="number"
                                            value={addAmount || ""}
                                            min={0}
                                            step="any"
                                            placeholder={t("market.amountPh")}
                                            onChange={(e) => setAddAmount(Number(e.target.value))}
                                            style={s.input}
                                            autoFocus
                                        />
                                    </div>
                                    <div style={{
                                        padding: "10px 12px",
                                        borderRadius: 8,
                                        border: "1px solid var(--border-soft, var(--border-card))",
                                        background: "var(--bg-panel)",
                                        fontSize: 12,
                                        color: "var(--text-muted)",
                                        display: "flex",
                                        justifyContent: "space-between",
                                        alignItems: "center",
                                    }}>
                                        <span>{t("market.amountToBuy")}</span>
                                        <span style={{ fontWeight: 700, color: "var(--text-primary)", fontSize: 15 }}>
                                            {effectiveQty > 0 ? effectiveQty.toLocaleString("tr-TR") + " " + t("market.qtyUnit") : "—"}
                                        </span>
                                    </div>
                                    {amountLeftover > 0 && effectiveQty > 0 && (
                                        <div style={{ fontSize: 11, color: "var(--text-muted)", paddingLeft: 4 }}>
                                            {t("market.leftover", { sym, value: amountLeftover.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) })}
                                        </div>
                                    )}
                                </>
                            )}

                            <div style={s.infoBox}>
                                <div style={s.infoRow}>
                                    <span style={{ color: "var(--text-muted)", fontSize: 13 }}>{t("market.estimatedTotal")}</span>
                                    <span style={{ color: "var(--text-primary)", fontWeight: 700, fontSize: 16 }}>
                                        {sym}{addTotal > 0 ? addTotal.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}
                                    </span>
                                </div>
                            </div>
                            {addErr && <div style={{ color: "#ef4444", fontSize: 13 }}>{addErr}</div>}
                        </div>
                    );
                })()}
            </Modal>
        </div>
    );
}

const s = {
    root: { display: "flex", flexDirection: "column", gap: 20 },
    headerSection: {
        display: "flex",
        flexDirection: "column",
        gap: 20,
        marginBottom: 8,
    },
    titleRow: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
    },
    titleArea: {
        display: "flex",
        flexDirection: "column",
        gap: 4,
    },
    userControls: {
        display: "flex",
        alignItems: "center",
        gap: 8,
    },
    iconBtn: {
        width: 38,
        height: 38,
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        fontSize: 16,
        cursor: "pointer",
        display: "grid",
        placeItems: "center",
        transition: "all 0.2s",
    },
    logoutBtn: {
        padding: "9px 16px",
        borderRadius: 8,
        border: "1px solid var(--danger-border)",
        background: "var(--danger-bg)",
        color: "var(--danger-text)",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
        transition: "all 0.2s",
    },
    pageTitle: {
        fontSize: 28,
        fontWeight: 700,
        color: "var(--text-primary)",
        margin: 0,
        padding: 0,
    },
    pageSubtitle: {
        fontSize: 14,
        color: "var(--text-muted)",
        margin: 0,
        padding: 0,
    },
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
    indexGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: 16,
    },
    indexCard: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        padding: "18px 20px",
        transition: "all 0.2s",
    },
    indexCardActive: {
        border: "2px solid #22c55e",
        background: "rgba(34, 197, 94, 0.1)",
        boxShadow: "0 0 0 3px rgba(34, 197, 94, 0.1)",
    },
    indexLabel: {
        fontSize: 12,
        color: "var(--text-muted)",
        marginBottom: 8,
        fontWeight: 500,
        display: "flex",
        alignItems: "center",
    },
    indexPrice: {
        fontSize: 24,
        fontWeight: 700,
        color: "var(--text-primary)",
        marginBottom: 4,
        letterSpacing: "-0.5px",
    },
    categoryFilterContainer: {
        display: "flex",
        gap: 12,
        marginTop: 16,
    },
    categoryFilterBtn: {
        flex: 1,
        padding: "12px 20px",
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "var(--bg-card)",
        color: "var(--text-muted)",
        fontSize: 14,
        fontWeight: 600,
        cursor: "pointer",
        transition: "all 0.2s",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: 8,
    },
    categoryFilterActive: {
        border: "2px solid #00ff00",
        background: "rgba(0, 255, 0, 0.15)",
        color: "#00ff00",
        boxShadow: "0 0 0 3px rgba(0, 255, 0, 0.1)",
    },
    filterBanner: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "12px 20px",
        background: "rgba(0, 255, 0, 0.1)",
        border: "1px solid rgba(0, 255, 0, 0.3)",
        borderRadius: 8,
        marginBottom: 16,
    },
    clearFilterBtn: {
        padding: "6px 12px",
        borderRadius: 6,
        border: "1px solid rgba(59, 130, 246, 0.5)",
        background: "transparent",
        color: "#3b82f6",
        fontSize: 12,
        fontWeight: 600,
        cursor: "pointer",
        transition: "all 0.2s",
    },
    controls: {
        display: "flex",
        gap: 12,
        alignItems: "center",
    },
    searchBox_old: {
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "10px 14px",
        background: "var(--input-bg)",
        border: "1px solid var(--input-border)",
        borderRadius: 8,
        flex: 1,
        maxWidth: 400,
    },
    searchInput_old: {
        flex: 1,
        background: "transparent",
        border: "none",
        outline: "none",
        color: "var(--text-primary)",
        fontSize: 13,
    },
    filterRow_old: { display: "flex", gap: 6, flexWrap: "wrap" },
    filterBtn_old: {
        padding: "8px 16px",
        borderRadius: 6,
        border: "1px solid #374151",
        background: "#1f2937",
        color: "#9ca3af",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 500,
        transition: "all 0.2s",
    },
    filterActive_old: {
        border: "1px solid #10b981",
        background: "rgba(16, 185, 129, 0.15)",
        color: "#10b981",
    },
    mainLayout: {
        display: "flex",
        flexDirection: "column",
        gap: 12,
    },
    tableContainer: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        overflow: "hidden",
    },
    tableHeader: {
        display: "grid",
        gridTemplateColumns: "2fr 1fr 1fr 1fr 1.5fr auto",
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
    categoryHeader: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        padding: "16px 20px",
        background: "rgba(59, 130, 246, 0.1)",
        borderBottom: "2px solid rgba(59, 130, 246, 0.3)",
        position: "sticky",
        top: 0,
        zIndex: 1,
    },
    categoryTitle: {
        fontSize: 14,
        fontWeight: 700,
        color: "#3b82f6",
        textTransform: "uppercase",
        letterSpacing: "0.5px",
    },
    categoryCount: {
        fontSize: 12,
        color: "var(--text-muted)",
        fontWeight: 500,
    },
    tableRow: {
        display: "grid",
        gridTemplateColumns: "2fr 1fr 1fr 1fr 1.5fr auto",
        gap: 16,
        padding: "14px 20px",
        borderBottom: "1px solid var(--border-card)",
        cursor: "pointer",
        transition: "background 0.2s",
    },
    colHisse: { display: "flex", flexDirection: "column", gap: 2 },
    colFiyat: { display: "flex", alignItems: "center" },
    colDegisim: { display: "flex", alignItems: "center" },
    colHacim: { display: "flex", alignItems: "center" },
    sortableHeader: {
        cursor: "pointer",
        userSelect: "none",
        gap: 6,
        transition: "color 0.15s",
    },
    sortableHeaderActive: {
        color: "var(--text-primary)",
    },
    sortArrow: {
        fontSize: 10,
        opacity: 0.75,
    },
    colGrafik: { display: "flex", alignItems: "center", justifyContent: "center" },
    colIslem: { display: "flex", alignItems: "center", justifyContent: "flex-end" },
    actionBtn: {
        padding: "8px 20px",
        borderRadius: 6,
        border: "none",
        background: "#10b981",
        color: "#000",
        fontSize: 13,
        fontWeight: 700,
        cursor: "pointer",
        transition: "all 0.2s",
        boxShadow: "0 2px 6px rgba(16, 185, 129, 0.3)",
    },
    searchBox: {
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "10px 14px",
        background: "var(--input-bg)",
        border: "1px solid var(--input-border)",
        borderRadius: 8,
    },
    searchInput: {
        flex: 1,
        background: "transparent",
        border: "none",
        outline: "none",
        color: "var(--text-primary)",
        fontSize: 13,
    },
    filterRow: { display: "flex", gap: 6, flexWrap: "wrap" },
    filterBtn: {
        padding: "5px 12px",
        borderRadius: 6,
        border: "1px solid var(--border-card)",
        background: "transparent",
        color: "var(--text-muted)",
        cursor: "pointer",
        fontSize: 11,
        fontWeight: 500,
        transition: "all 0.2s",
    },
    filterActive: {
        border: "1px solid #3b82f6",
        background: "rgba(59, 130, 246, 0.15)",
        color: "var(--text-primary)",
    },
    stockList: {
        display: "flex",
        flexDirection: "column",
        gap: 1,
        maxHeight: 600,
        overflowY: "auto",
    },
    stockItem: {
        display: "grid",
        gridTemplateColumns: "1fr auto auto",
        alignItems: "center",
        gap: 12,
        padding: "10px 12px",
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 6,
        cursor: "pointer",
        transition: "all 0.2s",
    },
    stockItemActive: {
        border: "1px solid #3b82f6",
        background: "rgba(59, 130, 246, 0.1)",
    },
    stockLeft: { display: "flex", flexDirection: "column", gap: 2 },
    stockSymbol: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    stockName: { fontSize: 10, color: "var(--text-muted)" },
    stockRight: { display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 2 },
    stockPrice: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    rightPanel: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        padding: 20,
        display: "flex",
        flexDirection: "column",
        gap: 20,
    },
    emptyDetail: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100%",
    },
    detailHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        paddingBottom: 16,
        borderBottom: "1px solid var(--border-card)",
    },
    detailSymbol: { fontSize: 24, fontWeight: 700, color: "var(--text-primary)", marginBottom: 4 },
    detailName: { fontSize: 13, color: "var(--text-muted)" },
    detailPriceBox: { textAlign: "right" },
    detailPrice: { fontSize: 28, fontWeight: 700, color: "var(--text-primary)", marginBottom: 4 },
    detailChart: {
        padding: "16px 0",
    },
    detailActions: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 12,
    },
    buyBtn: {
        padding: "12px",
        borderRadius: 8,
        border: "none",
        background: "#10b981",
        color: "#fff",
        fontSize: 14,
        fontWeight: 600,
        cursor: "pointer",
        transition: "opacity 0.2s",
    },
    sellBtn: {
        padding: "12px",
        borderRadius: 8,
        border: "none",
        background: "#ef4444",
        color: "#fff",
        fontSize: 14,
        fontWeight: 600,
        cursor: "pointer",
        transition: "opacity 0.2s",
    },
    detailStats: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 16,
        paddingTop: 16,
        borderTop: "1px solid var(--border-card)",
    },
    statItem: { display: "flex", flexDirection: "column", gap: 4 },
    statLabel: { fontSize: 11, color: "var(--text-muted)" },
    statValue: { fontSize: 14, fontWeight: 600, color: "var(--text-primary)" },
    infoBox: {
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "var(--bg-panel)",
        padding: "10px 12px",
        display: "grid",
        gap: 8,
    },
    infoRow: { display: "flex", justifyContent: "space-between", alignItems: "center" },
    input: {
        padding: "9px 12px",
        borderRadius: 8,
        border: "1px solid var(--input-border)",
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        outline: "none",
        width: "100%",
        boxSizing: "border-box",
        fontSize: 14,
    },
    primaryBtn: {
        padding: "10px 20px",
        borderRadius: 8,
        border: "none",
        background: "#10b981",
        color: "#000",
        cursor: "pointer",
        fontWeight: 700,
        fontSize: 14,
        transition: "all 0.2s",
    },
    ghostBtn: {
        padding: "10px 20px",
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "transparent",
        color: "var(--text-muted)",
        cursor: "pointer",
        fontWeight: 600,
        fontSize: 14,
        transition: "all 0.2s",
    },
};

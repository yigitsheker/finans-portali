import { useEffect, useMemo, useRef, useState } from "react";
import { IconTrendingUp, IconBarChart } from "../components/common/icons";
import PropTypes from "prop-types";
import { PortfolioAreaChart } from "../components/common/PortfolioAreaChart";
import Modal from "../components/Modal";
import ImportPreviewModal from "../components/ImportPreviewModal";
import InstrumentChartModal from "../components/InstrumentChartModal";
import CompareInstrumentsModal from "../components/CompareInstrumentsModal";
import { 
  getLatestPrice, 
  getMarketHistory, 
  getMarketSummary, 
  getMarketHistoryBatch,
  getHistoricalPositions,
  addHistoricalPosition,
  updateHistoricalPosition,
  deleteHistoricalPosition,
  deleteAllHistoricalPositions
} from "../api/portfolioApi";
import { compareInflation } from "../api/inflationApi";
import { useI18n } from "../contexts/I18nContext";
import { useCurrencyDisplay } from "../contexts/CurrencyDisplayContext";
import notify from "../utils/notify";
import { parsePortfolioExcel } from "../utils/excelImport";

const localYmd = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
// Buy date must be in the past → the date pickers cap at yesterday (local), in
// step with the "< today" validation (avoids the UTC drift of toISOString()).
const yesterdayLocal = () => { const d = new Date(); d.setDate(d.getDate() - 1); return localYmd(d); };

export default function HistoricalComparison({ keycloak }) {
  const { t } = useI18n();
  // Mode comes from the topbar toggle (Original | ₺ | $). Original is
  // treated as TRY here because that's the user's home currency for this
  // page — we never want to sum mixed currencies in the totals card.
  const { mode, usdRate } = useCurrencyDisplay();
  const effectiveCurrency = mode === "USD" ? "USD" : "TRY";
  const effectiveSym = effectiveCurrency === "USD" ? "$" : "₺";
  // Locale-aware numeric formatter for the active display currency.
  const fmt = (n) => Number(n).toLocaleString("tr-TR", { maximumFractionDigits: 2 });
  // Convert a value stored in `nativeCurrency` ("TRY" | "USD") into the
  // active display currency. Uses the spot USDTRY cached on the context.
  function toEffective(value, nativeCurrency) {
    const n = Number(value);
    if (!Number.isFinite(n)) return 0;
    if (effectiveCurrency === nativeCurrency) return n;
    if (effectiveCurrency === "TRY") return n * usdRate;          // USD → TRY
    return usdRate > 0 ? n / usdRate : 0;                          // TRY → USD
  }
  const [positions, setPositions] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [instruments, setInstruments] = useState([]);

  // Add modal state
  const [addOpen, setAddOpen] = useState(false);
  const [addSymbol, setAddSymbol] = useState("");
  const [addDate, setAddDate] = useState("");
  // Free-text so fractional quantities (e.g. 0.5 BTC) can be typed without the
  // value being clamped mid-keystroke; parsed to a number on submit.
  const [addLots, setAddLots] = useState("1");
  const [addLoading, setAddLoading] = useState(false);
  const [addError, setAddError] = useState(null);
  const [showSugg, setShowSugg] = useState(false);
  const [importing, setImporting] = useState(false);
  const [histPreview, setHistPreview] = useState(null);
  // Clicking a row opens its chart card (same modal as the Stocks page).
  const [chartTarget, setChartTarget] = useState(null);
  const [perfPeriod, setPerfPeriod] = useState("30D"); // historical performance area-chart range
  const [perfSeries, setPerfSeries] = useState([]);
  const [perfLoading, setPerfLoading] = useState(false);
  const [compareTarget, setCompareTarget] = useState(null);
  // When set, the "Geçmişten Performans" chart shows only this position's
  // value from its buy date to today instead of the combined portfolio.
  const [focusPosition, setFocusPosition] = useState(null);
  const fileRef = useRef(null);
  const perfCardRef = useRef(null);

  // Inline edit (lot + date) for an existing row.
  const [editTarget, setEditTarget] = useState(null);
  const [editLots, setEditLots] = useState(1);
  const [editDate, setEditDate] = useState("");
  const [editSaving, setEditSaving] = useState(false);
  const [editError, setEditError] = useState(null);

  // Optional buy-price/amount override for manual entry/edit. Empty → use the
  // market price at the buy date. Filled → per-lot price OR total amount.
  const [addPrice, setAddPrice] = useState("");
  const [addPriceMode, setAddPriceMode] = useState("perLot"); // "perLot" | "total"
  const [editPrice, setEditPrice] = useState("");
  const [editPriceMode, setEditPriceMode] = useState("perLot");

  const validSymbols = useMemo(
    () => new Set(instruments.map((i) => String(i.symbol).toUpperCase())),
    [instruments],
  );

  useEffect(() => {
    getMarketSummary().then(setInstruments).catch(() => {});

    // Load positions from backend
    if (keycloak.authenticated) {
      getHistoricalPositions(keycloak)
        .then(data => {
          setPositions(data);
          setLoaded(true);
          // The backend only persists buy-time data (no live price). Fetch
          // each position's current price + inflation-adjusted return so the
          // table shows real current/PnL figures instead of 0/-100%.
          Promise.all(data.map(enrichWithLiveData)).then(setPositions);
        })
        .catch(err => {
          console.error("Failed to load historical positions:", err);
          setLoaded(true);
        });
    } else {
      setLoaded(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keycloak]);

  // Remove localStorage save effect - data now lives in backend
  // useEffect(() => {
  //   if (!loaded) return;
  //   localStorage.setItem("historicalPositions", JSON.stringify(positions));
  // }, [positions, loaded]);

  // When a known symbol + a past buy date are chosen in the add modal, pre-fill
  // "Alınan Tutar" with the market price at that date (per lot). The user can
  // then edit it. Debounced + symbol-gated so typing doesn't fire per keystroke.
  useEffect(() => {
    if (!addOpen) return;
    const sym = addSymbol.trim().toUpperCase();
    if (!sym || !addDate || !validSymbols.has(sym)) return;
    const sel = new Date(addDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (!(sel < today)) return;
    let cancelled = false;
    const handle = setTimeout(async () => {
      try {
        const p = await fetchBuyPriceAt(sym, addDate);
        if (!cancelled && p != null && p > 0) {
          setAddPrice(String(p));
          setAddPriceMode("perLot");
        }
      } catch { /* leave the field as-is if the price can't be fetched */ }
    }, 500);
    return () => { cancelled = true; clearTimeout(handle); };
    // fetchBuyPriceAt is a stable component function; deps intentionally limited.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [addOpen, addSymbol, addDate, validSymbols]);

  const suggestions = useMemo(() => {
    if (!addSymbol.trim()) return instruments.slice(0, 8);
    const q = addSymbol.trim().toUpperCase();
    return instruments.filter((i) => i.symbol.includes(q) || i.name.toUpperCase().includes(q)).slice(0, 8);
  }, [addSymbol, instruments]);

  // Determine native storage currency for a symbol (used at add-time so
  // the stored row knows what currency its buyPrice/currentPrice are in).
  // Returns "₺" or "$" — the historical schema kept these symbol literals.
  const getCurrency = (symbol) => {
    if (!symbol) return "₺";
    const s = symbol.toUpperCase();
    // Most reliable: the catalog instrument type. BIST stocks and TEFAS funds
    // are quoted in TRY; crypto in USD. (A hardcoded stock list missed most
    // BIST symbols — e.g. BIMAS — and wrongly treated them as USD, applying a
    // ~USDTRY x46 conversion to TRY prices.)
    const inst = instruments.find((i) => String(i.symbol).toUpperCase() === s);
    if (inst?.type === "BIST" || inst?.type === "FUND") return "₺";
    if (inst?.type === "CRYPTO") return "$";
    // Fallbacks by symbol shape when the catalog isn't loaded yet:
    if (s.endsWith("TRY")) return "₺";   // TRY FX pairs (USDTRY, EURTRY, ...)
    if (s.endsWith(".IS")) return "₺";   // BIST .IS suffix
    return "$";                           // commodities / global default to USD
  };

  // Native currency for converting a STORED row's prices. Once the catalog is
  // loaded, prefer getCurrency (this also corrects rows saved before the
  // type-based fix). BEFORE it loads, fall back to the currency stored at
  // add-time — otherwise a BIST (₺) row would briefly resolve to "$" and flash
  // a ×USDTRY-inflated current value until the catalog arrives.
  const nativeOf = (p) =>
    ((instruments.length ? getCurrency(p.symbol) : (p.currency || "₺")) === "₺" ? "TRY" : "USD");

  // Build one historical position row (price at buy date from history, current
  // price, inflation-adjusted real return). Returns the row WITHOUT an id, or
  // null when there's no price history for that symbol/window. Shared by the
  // manual add modal and the Excel bulk-import.
  // Resolve an optional buy-price override from the form: "perLot" uses the
  // value as-is; "total" divides by the lot count. Returns null (→ use the
  // historical market price) when the field is blank/invalid.
  function overrideFrom(priceStr, mode, lots) {
    const v = parseFloat(String(priceStr ?? "").replace(",", "."));
    if (!(v > 0) || !(Number(lots) > 0)) return null;
    return mode === "total" ? v / Number(lots) : v;
  }

  // Closing price for `sym` nearest to `dateISO`, picking the right history
  // window from how far back the date is. Returns null when no history covers
  // it. Shared by buildHistoricalPosition and the buy-price auto-fill effect.
  async function fetchBuyPriceAt(sym, dateISO) {
    const selectedDate = new Date(dateISO);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const daysDiff = Math.floor((today.getTime() - selectedDate.getTime()) / (1000 * 60 * 60 * 24));
    let period = "30D";
    if (daysDiff > 365) period = "5Y";
    else if (daysDiff > 180) period = "1Y";
    else if (daysDiff > 90) period = "6M";
    else if (daysDiff > 30) period = "3M";
    const historyData = await getMarketHistory(sym, period);
    if (!historyData || historyData.length === 0) return null;
    const targetTime = selectedDate.getTime();
    let closestPoint = historyData[0];
    let minDiff = Math.abs(new Date(historyData[0].day).getTime() - targetTime);
    for (const point of historyData) {
      const diff = Math.abs(new Date(point.day).getTime() - targetTime);
      if (diff < minDiff) { minDiff = diff; closestPoint = point; }
    }
    return closestPoint.close;
  }

  async function buildHistoricalPosition(sym, dateISO, lots, priceOverride) {
    const currentPrice = await getLatestPrice(sym, keycloak);
    // No live quote (endpoint returned 0) → bail rather than persist a bogus
    // -100% row. Import counts it skipped; edit shows an error (keeps old row).
    if (!(currentPrice > 0)) return null;

    let buyPrice;
    if (priceOverride != null && priceOverride > 0) {
      // User supplied the buy price directly (per-lot) → skip the history lookup,
      // so even a symbol without price history can be tracked at a manual cost.
      buyPrice = priceOverride;
    } else {
      buyPrice = await fetchBuyPriceAt(sym, dateISO);
      if (buyPrice == null) return null;
    }
    const instrument = instruments.find((i) => i.symbol === sym);
    const currency = getCurrency(sym);

    // Real return adjusted for cumulative CPI over the same window. The compare
    // endpoint returns null outside available CPI months — tolerated.
    const nominalPct = buyPrice > 0 ? ((currentPrice - buyPrice) / buyPrice) * 100 : 0;
    const todayISO = new Date().toISOString().split("T")[0];
    let inflationData = null;
    try {
      inflationData = await compareInflation(dateISO, todayISO, nominalPct);
    } catch (e) {
      console.debug("Inflation compare unavailable:", e?.message);
    }

    return {
      symbol: sym,
      name: instrument?.name || sym,
      buyDate: dateISO,
      buyPrice,
      currentPrice,
      lots,
      currency,
      cumulativeInflationPct: inflationData?.cumulativeInflationPct ?? null,
      realReturnPct: inflationData?.realReturnPct ?? null,
    };
  }

  // A position loaded from the backend has only the buy-time fields (no live
  // price, no inflation comparison — those aren't persisted). Fetch the
  // current price and recompute the inflation-adjusted return, mirroring
  // buildHistoricalPosition. Returns the position unchanged if no live quote
  // is available.
  async function enrichWithLiveData(p) {
    try {
      const currentPrice = await getLatestPrice(p.symbol, keycloak);
      if (!(currentPrice > 0)) return p;
      const nominalPct = p.buyPrice > 0 ? ((currentPrice - p.buyPrice) / p.buyPrice) * 100 : 0;
      const todayISO = new Date().toISOString().split("T")[0];
      let inflationData = null;
      try {
        inflationData = await compareInflation(p.buyDate, todayISO, nominalPct);
      } catch (e) {
        console.debug("Inflation compare unavailable:", e?.message);
      }
      return {
        ...p,
        currentPrice,
        cumulativeInflationPct: inflationData?.cumulativeInflationPct ?? null,
        realReturnPct: inflationData?.realReturnPct ?? null,
      };
    } catch {
      return p;
    }
  }

  async function onAdd() {
    const sym = addSymbol.trim().toUpperCase();
    if (!sym) {
      setAddError(t("historical.errSymbol"));
      return;
    }
    if (!addDate) {
      setAddError(t("historical.errDate"));
      return;
    }
    const lots = Number(addLots);
    if (!(lots > 0)) {
      setAddError(t("historical.errQty"));
      return;
    }

    const selectedDate = new Date(addDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    if (selectedDate >= today) {
      setAddError(t("historical.errPast"));
      return;
    }

    try {
      setAddLoading(true);
      setAddError(null);

      const override = overrideFrom(addPrice, addPriceMode, lots);
      const pos = await buildHistoricalPosition(sym, addDate, lots, override);
      if (!pos) {
        setAddError(t("historical.errNoHistory"));
        return;
      }

      // Save to backend instead of localStorage. The response only carries
      // the persisted buy-time fields, so keep the locally computed
      // currentPrice/inflation figures from `pos` and just adopt the new id.
      const savedPos = await addHistoricalPosition(keycloak, pos);
      setPositions([...positions, { ...pos, id: savedPos.id }]);
      setAddOpen(false);
      setAddSymbol("");
      setAddDate("");
      setAddLots("1");
      setAddPrice("");
    } catch (e) {
      console.error("Error adding position:", e);
      setAddError(e?.message ?? t("historical.errPrice"));
    } finally {
      setAddLoading(false);
    }
  }

  // Excel pick → parse (columns symbol + lot + buy date, all required) → open
  // the editable preview so missing/wrong cells can be fixed before importing.
  async function onImportFile(file) {
    if (!file) return;
    const parsed = await parsePortfolioExcel(file, { requireDate: true });
    if (!parsed.ok) {
      notify(t("historical.importUnreadable"), { variant: "error" });
      return;
    }
    if (!parsed.rows.length) {
      notify(t("historical.importEmpty"), { variant: "warning" });
      return;
    }
    // Ensure the catalog is loaded so the preview flags unknown symbols.
    if (!instruments.length) {
      const fetched = await getMarketSummary().catch(() => []);
      if (fetched.length) setInstruments(fetched);
    }
    setHistPreview(parsed.rows);
  }

  // Confirm import: unknown symbols and rows with a missing/future date are
  // skipped and counted; the outcome is surfaced as a site notification.
  async function runHistoricalImport(rows) {
    setImporting(true);
    try {
      // Catalog loads async; fetch it now if needed so validation works.
      let catalog = instruments;
      if (!catalog.length) {
        catalog = await getMarketSummary().catch(() => []);
        if (catalog.length) setInstruments(catalog);
      }
      const valid = new Set(catalog.map((i) => String(i.symbol).toUpperCase()));
      // Compare dates as yyyy-MM-dd strings (local "today") to avoid the UTC
      // shift that new Date("yyyy-MM-dd") introduces near midnight.
      const now = new Date();
      const todayISO = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;

      const added = [];
      let skipped = 0;
      for (const row of rows) {
        const sym = String(row.symbol || "").trim().toUpperCase();
        const dateOk = row.date && row.date < todayISO;
        if (!sym || !valid.has(sym) || !(Number(row.lot) > 0) || !dateOk) { skipped++; continue; }
        try {
          const pos = await buildHistoricalPosition(sym, row.date, Number(row.lot));
          if (pos) {
            // Save each position to backend; keep the locally computed
            // currentPrice/inflation figures (not persisted) and adopt the new id.
            const saved = await addHistoricalPosition(keycloak, pos);
            added.push({ ...pos, id: saved.id });
          } else {
            skipped++;
          }
        } catch {
          skipped++;
        }
      }

      if (added.length > 0) setPositions((prev) => [...prev, ...added]);
      setHistPreview(null);
      if (added.length === 0 && skipped === 0) {
        notify(t("historical.importEmpty"), { variant: "warning" });
      } else {
        notify(t("historical.importDone", { imported: added.length, skipped }),
          { variant: added.length > 0 ? "success" : "warning" });
      }
    } finally {
      setImporting(false);
    }
  }

  function openEdit(p) {
    setEditTarget(p);
    setEditLots(p.lots);
    setEditDate(p.buyDate);
    setEditPrice(p.buyPrice != null ? String(p.buyPrice) : "");
    setEditPriceMode("perLot");
    setEditError(null);
  }

  // Save a row edit: a new date re-fetches the buy price from history and
  // re-runs the inflation adjustment, so all derived figures stay correct.
  async function onSaveEdit() {
    if (!editTarget) return;
    if (!(Number(editLots) > 0)) { setEditError(t("historical.errQty")); return; }
    if (!editDate) { setEditError(t("historical.errDate")); return; }
    const sel = new Date(editDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (sel >= today) { setEditError(t("historical.errPast")); return; }
    try {
      setEditSaving(true);
      setEditError(null);
      const override = overrideFrom(editPrice, editPriceMode, Number(editLots));
      const pos = await buildHistoricalPosition(editTarget.symbol, editDate, Number(editLots), override);
      if (!pos) { setEditError(t("historical.errNoHistory")); return; }
      
      // Update in backend; keep the locally computed currentPrice/inflation
      // figures (not persisted) and adopt the (unchanged) id.
      const updated = await updateHistoricalPosition(keycloak, editTarget.id, pos);
      setPositions((prev) => prev.map((x) => (x.id === editTarget.id ? { ...pos, id: updated.id } : x)));
      setEditTarget(null);
    } catch (e) {
      setEditError(e?.message ?? t("historical.errPrice"));
    } finally {
      setEditSaving(false);
    }
  }

  async function onDelete(id) {
    try {
      await deleteHistoricalPosition(keycloak, id);
      setPositions(positions.filter(p => p.id !== id));
      setFocusPosition((cur) => (cur?.id === id ? null : cur));
    } catch (e) {
      console.error("Failed to delete position:", e);
      notify(t("common.errorOccurred"), { variant: "error" });
    }
  }

  function openChart(p) {
    const inst = instruments.find((i) => i.symbol === p.symbol) || { symbol: p.symbol, name: p.name };
    setChartTarget(inst);
  }

  async function onClearAll() {
    if (confirm(t("historical.confirmClearAll"))) {
      try {
        await deleteAllHistoricalPositions(keycloak);
        setPositions([]);
        setFocusPosition(null);
      } catch (e) {
        console.error("Failed to clear all positions:", e);
        notify(t("common.errorOccurred"), { variant: "error" });
      }
    }
  }

  // Single-currency totals in the active display currency. Each row's
  // invested/current is converted from its native currency through
  // toEffective() before summing — mixed-currency portfolios stay coherent.
  const totals = useMemo(() => {
    let invested = 0;
    let current = 0;
    for (const p of positions) {
      const native = nativeOf(p);
      invested += toEffective(p.buyPrice * p.lots, native);
      current += toEffective(p.currentPrice * p.lots, native);
    }
    const change = current - invested;
    const changePct = invested > 0 ? (change / invested) * 100 : 0;
    return { invested, current, change, changePct };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [positions, effectiveCurrency, usdRate, instruments]);

  // Historical performance: total value of the held positions over time, built
  // client-side from each symbol's price history (carry-forward + buy-date
  // gating) and converted to the active display currency. Feeds the area chart.
  // When `focusPosition` is set (per-row "chart" button), only that position
  // is plotted and the range always spans its buy date → today, regardless of
  // the 1M/3M/1Y/All toggle.
  useEffect(() => {
    const activePositions = focusPosition ? [focusPosition] : positions;
    if (!activePositions.length) { setPerfSeries([]); return undefined; }
    let cancel = false;
    setPerfLoading(true);
    const symbols = [...new Set(activePositions.map((p) => p.symbol))];
    // "ALL" (or a focused position) → smallest backend range that covers the
    // earliest buy date, so the chart spans buy-date → today (the
    // per-position gating below trims the rest).
    let batchPeriod = perfPeriod;
    if (perfPeriod === "ALL" || focusPosition) {
      const earliestBuy = activePositions.reduce((min, p) => {
        const d = p.buyDate ? String(p.buyDate).slice(0, 10) : null;
        return d && (!min || d < min) ? d : min;
      }, null);
      if (!earliestBuy) batchPeriod = "1Y";
      else {
        const days = Math.floor((Date.now() - new Date(earliestBuy).getTime()) / 86400000);
        batchPeriod = days <= 30 ? "30D" : days <= 90 ? "3M" : days <= 180 ? "6M" : days <= 365 ? "1Y" : days <= 730 ? "2Y" : days <= 1825 ? "5Y" : "ALL";
      }
    }
    getMarketHistoryBatch(symbols, batchPeriod)
      .then((batch) => {
        if (cancel) return;
        const perSym = {};
        const allDays = new Set();
        for (const sym of symbols) {
          const hist = (batch[sym] || [])
            .filter((h) => h && Number(h.close) > 0)
            .map((h) => ({ day: String(h.day).slice(0, 10), close: Number(h.close) }))
            .sort((a, b) => a.day.localeCompare(b.day));
          perSym[sym] = hist;
          hist.forEach((h) => allDays.add(h.day));
        }
        const days = [...allDays].sort();
        const series = [];
        for (const day of days) {
          let total = 0;
          let any = false;
          for (const p of activePositions) {
            const bd = p.buyDate ? String(p.buyDate).slice(0, 10) : null;
            if (bd && day < bd) continue;                 // not yet held on this day
            const hist = perSym[p.symbol] || [];
            let close = null;
            for (let i = hist.length - 1; i >= 0; i--) {
              if (hist[i].day <= day) { close = hist[i].close; break; }  // carry-forward
            }
            if (close == null) continue;
            total += toEffective(close * Number(p.lots || 0), nativeOf(p));
            any = true;
          }
          if (any) series.push({ time: day, value: Number(total.toFixed(2)) });
        }
        setPerfSeries(series);
      })
      .catch(() => { if (!cancel) setPerfSeries([]); })
      .finally(() => { if (!cancel) setPerfLoading(false); });
    return () => { cancel = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [positions, perfPeriod, effectiveCurrency, usdRate, instruments, focusPosition]);

  return (
    <div style={s.root}>
      {/* Header */}
      <div style={s.header}>
        <div>
          <div style={{ ...s.title, display: "flex", alignItems: "center", gap: 8 }}><IconBarChart size={22} />{t("historical.title")}</div>
          <div style={s.subtitle}>{t("historical.subtitle")}</div>
        </div>
        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          <a href="/ornek-gecmis.xlsx" download style={s.sampleLink}>
            {t("historical.importSample")}
          </a>
          <a href="/ornek-gecmis-eksik.xlsx" download style={s.sampleLink}>
            {t("historical.importSampleMissing")}
          </a>
          <button
            style={{ ...s.importBtn, ...(importing ? { opacity: 0.6, cursor: "default" } : {}) }}
            onClick={() => fileRef.current?.click()}
            disabled={importing}
          >
            {importing ? t("historical.importing") : t("historical.importBtn")}
          </button>
          <input
            ref={fileRef}
            type="file"
            accept=".xlsx,.xls,.csv"
            style={{ display: "none" }}
            onChange={(e) => {
              const f = e.target.files?.[0];
              e.target.value = "";
              if (f) onImportFile(f);
            }}
          />
          {positions.length > 0 && (
            <button style={s.clearBtn} onClick={onClearAll}>
              {t("historical.clearAll")}
            </button>
          )}
          <button style={s.addBtn} onClick={() => {
            setAddSymbol("");
            setAddDate("");
            setAddLots("1");
            setAddError(null);
            setAddOpen(true);
          }}>
            {t("historical.addPosition")}
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      {positions.length > 0 && (
        <div style={s.summaryGrid}>
          <SCard
            label={`${t("historical.totalInvest")} (${effectiveCurrency})`}
            value={effectiveSym + fmt(totals.invested)}
          />
          <SCard
            label={`${t("historical.currentValue")} (${effectiveCurrency})`}
            value={effectiveSym + fmt(totals.current)}
          />
          <SCard
            label={`${t("historical.pnl")} (${effectiveCurrency})`}
            value={(totals.change >= 0 ? "+" : "-") + effectiveSym + fmt(Math.abs(totals.change))}
            sub={(totals.change >= 0 ? "+" : "") + totals.changePct.toFixed(2) + "%"}
            valueColor={totals.change >= 0 ? "var(--green)" : "var(--red)"}
          />
        </div>
      )}

      {/* Geçmişten performans — değer/zaman alan grafiği (lightweight-charts) */}
      {positions.length > 0 && (
        <div style={s.card} ref={perfCardRef}>
          <div style={s.chartHeader}>
            <div style={s.chartTitle}>
              {focusPosition
                ? t("historical.perfFocusTitle", {
                    symbol: focusPosition.symbol,
                    date: new Date(focusPosition.buyDate).toLocaleDateString("tr-TR"),
                  })
                : t("historical.perfTitle")}
            </div>
            {focusPosition ? (
              <button type="button" style={s.pnlToggleBtn} onClick={() => setFocusPosition(null)}>
                {t("historical.perfShowAll")}
              </button>
            ) : (
              <div style={s.pnlToggle}>
                {[{ p: "30D", l: "1M" }, { p: "3M", l: "3M" }, { p: "1Y", l: "1Y" }, { p: "ALL", l: "All" }].map(({ p, l }) => (
                  <button key={p} type="button"
                    style={{ ...s.pnlToggleBtn, ...(perfPeriod === p ? s.pnlToggleActive : {}) }}
                    onClick={() => setPerfPeriod(p)}>{l}</button>
                ))}
              </div>
            )}
          </div>
          {perfLoading ? (
            <div style={s.perfMsg}>{t("common.loadingDots")}</div>
          ) : perfSeries.length >= 2 ? (
            <PortfolioAreaChart
              data={perfSeries}
              height={240}
              positive={focusPosition ? focusPosition.currentPrice >= focusPosition.buyPrice : totals.change >= 0}
            />
          ) : (
            <div style={s.perfMsg}>{t("historical.perfEmpty")}</div>
          )}
        </div>
      )}

      {/* Positions Table */}
      <div style={s.card}>
        {positions.length === 0 ? (
          <div style={s.empty}>
            <div style={{ marginBottom: 12 }}><IconTrendingUp size={48} /></div>
            <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 6 }}>{t("historical.empty")}</div>
            <div style={{ fontSize: 13, color: "var(--text-muted)", marginBottom: 16 }}>
              {t("historical.emptySub")}
            </div>
            <button style={s.addBtn} onClick={() => setAddOpen(true)}>
              {t("historical.emptyCta")}
            </button>
          </div>
        ) : (
          <div style={s.tableWrap} className="fp-table-scroll">
            <table style={s.table}>
              <thead>
                <tr>
                  {[t("historical.colSymbol"), t("historical.colName"), t("historical.colBuyDate"), t("historical.colLots"), t("historical.colBuy"), t("historical.colCurrent"), t("historical.colInvested"), t("historical.colValue"), t("historical.colPnl"), t("historical.colNominal"), t("historical.colInflation"), t("historical.colReal"), ""].map((h, i) => (
                    <th key={i} style={s.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {positions.map((p) => {
                  const native = nativeOf(p);
                  const buyPx = toEffective(p.buyPrice, native);
                  const curPx = toEffective(p.currentPrice, native);
                  const invested = toEffective(p.buyPrice * p.lots, native);
                  const current = toEffective(p.currentPrice * p.lots, native);
                  const change = current - invested;
                  const changePct = invested > 0 ? (change / invested) * 100 : 0;
                  const isPositive = change >= 0;

                  return (
                    <tr key={p.id} style={{ ...s.tr, cursor: "pointer" }} onClick={() => openChart(p)}>
                      <td style={s.td}>
                        <span style={s.symbolBadge}>{p.symbol}</span>
                      </td>
                      <td style={{ ...s.td, color: "var(--text-muted)" }}>{p.name}</td>
                      <td style={{ ...s.td, fontSize: 12 }}>
                        {new Date(p.buyDate).toLocaleDateString("tr-TR")}
                      </td>
                      <td style={s.td}>{p.lots.toLocaleString("tr-TR")}</td>
                      <td style={s.td}>
                        {effectiveSym}{fmt(buyPx)}
                      </td>
                      <td style={s.td}>
                        {effectiveSym}{fmt(curPx)}
                      </td>
                      <td style={s.td}>
                        {effectiveSym}{fmt(invested)}
                      </td>
                      <td style={{ ...s.td, fontWeight: 600 }}>
                        {effectiveSym}{fmt(current)}
                      </td>
                      <td style={{ ...s.td, color: isPositive ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                        {isPositive ? "+" : ""}{effectiveSym}{fmt(change)}
                      </td>
                      <td style={{ ...s.td, color: isPositive ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                        {isPositive ? "▲ +" : "▼ "}{Math.abs(changePct).toFixed(2)}%
                      </td>
                      <td title={p.cumulativeInflationPct == null ? t("historical.noInflationData") : undefined} style={{ ...s.td, color: "var(--text-muted)", fontWeight: 500 }}>
                        {p.cumulativeInflationPct != null
                          ? "+" + Number(p.cumulativeInflationPct).toFixed(2) + "%"
                          : "—"}
                      </td>
                      <td title={p.realReturnPct == null ? t("historical.noInflationData") : undefined} style={{
                        ...s.td,
                        color: p.realReturnPct == null ? "var(--text-muted)" : (Number(p.realReturnPct) >= 0 ? "var(--green)" : "var(--red)"),
                        fontWeight: 700,
                      }}>
                        {p.realReturnPct != null
                          ? (Number(p.realReturnPct) >= 0 ? "▲ +" : "▼ ") + Math.abs(Number(p.realReturnPct)).toFixed(2) + "%"
                          : "—"}
                      </td>
                      <td style={s.td}>
                        <div style={{ display: "flex", gap: 6 }}>
                          <button
                            style={{ ...s.editBtn, ...(focusPosition?.id === p.id ? s.chartBtnActive : {}) }}
                            title={t("historical.perfFocusBtn")}
                            onClick={(e) => {
                              e.stopPropagation();
                              // Toggle: clicking the already-focused position's
                              // button clears focus → chart falls back to the
                              // combined view of all positions.
                              setFocusPosition((cur) => (cur?.id === p.id ? null : p));
                              perfCardRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
                            }}
                          >
                            <IconTrendingUp size={16} />
                          </button>
                          <button style={s.editBtn} onClick={(e) => { e.stopPropagation(); openEdit(p); }}>
                            {t("common.edit")}
                          </button>
                          <button style={s.deleteBtn} onClick={(e) => { e.stopPropagation(); onDelete(p.id); }}>
                            {t("historical.delete")}
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Add Modal */}
      <Modal
        open={addOpen}
        title={t("historical.modalTitle")}
        onClose={() => setAddOpen(false)}
        footer={
          <>
            <button style={s.ghostBtn} onClick={() => setAddOpen(false)} disabled={addLoading}>
              {t("common.cancel")}
            </button>
            <button style={s.primaryBtn} onClick={onAdd} disabled={addLoading}>
              {addLoading ? t("common.adding") : t("common.add")}
            </button>
          </>
        }
      >
        <div style={{ display: "grid", gap: 14 }}>
          <div style={{ display: "grid", gap: 6 }}>
            <label style={s.label}>{t("historical.modalSymbol")}</label>
            <div style={{ position: "relative" }}>
              <input
                value={addSymbol}
                onChange={(e) => {
                  setAddSymbol(e.target.value);
                  setShowSugg(true);
                }}
                onFocus={() => setShowSugg(true)}
                onBlur={() => setTimeout(() => setShowSugg(false), 150)}
                placeholder={t("historical.modalSymbolPh")}
                style={s.input}
                autoComplete="off"
              />
              {showSugg && suggestions.length > 0 && (
                <div style={s.dropdown}>
                  {suggestions.map((inst) => (
                    <div
                      key={inst.symbol}
                      style={s.dropdownItem}
                      role="button"
                      tabIndex={0}
                      onMouseDown={() => {
                        setAddSymbol(inst.symbol);
                        setShowSugg(false);
                      }}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          setAddSymbol(inst.symbol);
                          setShowSugg(false);
                        }
                      }}
                    >
                      <span style={{ fontWeight: 600 }}>{inst.symbol}</span>
                      <span style={{ color: "var(--text-muted)", fontSize: 11, marginLeft: 8 }}>{inst.name}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div style={{ display: "grid", gap: 6 }}>
              <label style={s.label}>{t("historical.modalDate")}</label>
              <input
                type="date"
                value={addDate}
                max={new Date().toISOString().split('T')[0]}
                onChange={(e) => setAddDate(e.target.value)}
                style={s.input}
              />
            </div>

            <div style={{ display: "grid", gap: 6 }}>
              <label style={s.label}>{t("historical.modalQty")}</label>
              <input
                type="number"
                value={addLots}
                min="0"
                step="any"
                inputMode="decimal"
                onChange={(e) => setAddLots(e.target.value)}
                style={s.input}
                placeholder="1"
              />
            </div>
          </div>

          <div style={{ display: "grid", gap: 6 }}>
            <label style={s.label}>{t("historical.modalAmount")}</label>
            <div style={{ display: "flex", gap: 8 }}>
              <input
                type="number" min="0" step="any"
                value={addPrice}
                onChange={(e) => setAddPrice(e.target.value)}
                style={{ ...s.input, flex: 1 }}
                placeholder={t("historical.amountPlaceholder")}
              />
              <div style={{ display: "flex", border: "1px solid var(--border-card)", borderRadius: 8, overflow: "hidden", flexShrink: 0 }}>
                <button type="button" onClick={() => setAddPriceMode("perLot")}
                  style={{ padding: "0 12px", border: "none", cursor: "pointer", fontSize: 12, fontWeight: 600, background: addPriceMode === "perLot" ? "var(--accent-solid)" : "transparent", color: addPriceMode === "perLot" ? "#000" : "var(--text-muted)" }}>
                  {t("historical.perLot")}
                </button>
                <button type="button" onClick={() => setAddPriceMode("total")}
                  style={{ padding: "0 12px", border: "none", cursor: "pointer", fontSize: 12, fontWeight: 600, background: addPriceMode === "total" ? "var(--accent-solid)" : "transparent", color: addPriceMode === "total" ? "#000" : "var(--text-muted)" }}>
                  {t("historical.total")}
                </button>
              </div>
            </div>
            <div style={{ fontSize: 11, color: "var(--text-muted)" }}>{t("historical.amountHint")}</div>
          </div>

          {addError && (
            <div style={{ color: "var(--danger-text)", fontSize: 13, padding: "8px 12px", background: "var(--danger-bg)", borderRadius: 6, border: "1px solid var(--danger-border)" }}>
              {addError}
            </div>
          )}

          <div style={{ fontSize: 12, color: "var(--text-muted)", padding: "8px 12px", background: "var(--bg-panel)", borderRadius: 6, border: "1px solid var(--border-card)" }}>
            {t("historical.modalHint")}
          </div>
        </div>
      </Modal>

      {/* Edit a row (lot + buy date) */}
      <Modal
        open={!!editTarget}
        title={t("historical.editTitle")}
        onClose={() => setEditTarget(null)}
        footer={
          <>
            <button style={s.ghostBtn} onClick={() => setEditTarget(null)} disabled={editSaving}>
              {t("common.cancel")}
            </button>
            <button style={s.primaryBtn} onClick={onSaveEdit} disabled={editSaving}>
              {editSaving ? t("common.saving") : t("common.save")}
            </button>
          </>
        }
      >
        <div style={{ display: "grid", gap: 14 }}>
          <div style={{ display: "grid", gap: 6 }}>
            <label style={s.label}>{t("historical.modalSymbol")}</label>
            <div style={{ ...s.input, opacity: 0.7 }}>{editTarget?.symbol}</div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div style={{ display: "grid", gap: 6 }}>
              <label style={s.label}>{t("historical.modalDate")}</label>
              <input
                type="date"
                value={editDate}
                max={yesterdayLocal()}
                onChange={(e) => setEditDate(e.target.value)}
                style={s.input}
              />
            </div>
            <div style={{ display: "grid", gap: 6 }}>
              <label style={s.label}>{t("historical.modalQty")}</label>
              <input
                type="number"
                value={editLots}
                min={1}
                onChange={(e) => setEditLots(Math.max(1, Number(e.target.value)))}
                style={s.input}
              />
            </div>
          </div>
          <div style={{ display: "grid", gap: 6 }}>
            <label style={s.label}>{t("historical.modalAmount")}</label>
            <div style={{ display: "flex", gap: 8 }}>
              <input
                type="number" min="0" step="any"
                value={editPrice}
                onChange={(e) => setEditPrice(e.target.value)}
                style={{ ...s.input, flex: 1 }}
                placeholder={t("historical.amountPlaceholder")}
              />
              <div style={{ display: "flex", border: "1px solid var(--border-card)", borderRadius: 8, overflow: "hidden", flexShrink: 0 }}>
                <button type="button" onClick={() => setEditPriceMode("perLot")}
                  style={{ padding: "0 12px", border: "none", cursor: "pointer", fontSize: 12, fontWeight: 600, background: editPriceMode === "perLot" ? "var(--accent-solid)" : "transparent", color: editPriceMode === "perLot" ? "#000" : "var(--text-muted)" }}>
                  {t("historical.perLot")}
                </button>
                <button type="button" onClick={() => setEditPriceMode("total")}
                  style={{ padding: "0 12px", border: "none", cursor: "pointer", fontSize: 12, fontWeight: 600, background: editPriceMode === "total" ? "var(--accent-solid)" : "transparent", color: editPriceMode === "total" ? "#000" : "var(--text-muted)" }}>
                  {t("historical.total")}
                </button>
              </div>
            </div>
            <div style={{ fontSize: 11, color: "var(--text-muted)" }}>{t("historical.amountHint")}</div>
          </div>

          {editError && (
            <div style={{ color: "var(--danger-text)", fontSize: 13, padding: "8px 12px", background: "var(--danger-bg)", borderRadius: 6, border: "1px solid var(--danger-border)" }}>
              {editError}
            </div>
          )}
        </div>
      </Modal>

      <ImportPreviewModal
        open={!!histPreview}
        initialRows={histPreview || []}
        requireDate
        validSymbols={validSymbols}
        importing={importing}
        onConfirm={runHistoricalImport}
        onClose={() => { if (!importing) setHistPreview(null); }}
      />

      {/* Chart card — opens when a row is clicked (same as Stocks). */}
      <InstrumentChartModal
        instrument={chartTarget}
        onClose={() => setChartTarget(null)}
        keycloak={keycloak}
        onCompare={(inst) => { setChartTarget(null); setCompareTarget(inst); }}
      />
      <CompareInstrumentsModal
        baseInstrument={compareTarget}
        onClose={() => setCompareTarget(null)}
      />
    </div>
  );
}

HistoricalComparison.propTypes = {
  keycloak: PropTypes.object.isRequired,
};

function SCard({ label, value, sub, valueColor }) {
  return (
    <div style={s.summaryCard}>
      <div style={s.summaryLabel}>{label}</div>
      <div style={{ ...s.summaryValue, color: valueColor ?? "var(--text-primary)" }}>{value}</div>
      {sub && <div style={s.summarySub}>{sub}</div>}
    </div>
  );
}

SCard.propTypes = {
  label: PropTypes.node,
  value: PropTypes.node,
  sub: PropTypes.node,
  valueColor: PropTypes.string,
};

const s = {
  root: { display: "flex", flexDirection: "column", gap: 16 },
  header: { display: "flex", justifyContent: "space-between", alignItems: "flex-start" },
  title: { fontSize: 24, fontWeight: 700, color: "var(--text-primary)", marginBottom: 4 },
  subtitle: { fontSize: 14, color: "var(--text-muted)" },
  summaryGrid: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 10 },
  summaryCard: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-card)", padding: "16px 18px" },
  summaryLabel: { fontSize: 12, color: "var(--text-muted)", marginBottom: 8 },
  summaryValue: { fontSize: 22, fontWeight: 700 },
  summarySub: { fontSize: 11, color: "var(--text-muted)", marginTop: 4 },
  card: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-card)", padding: "16px 18px" },
  chartHeader: { display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14, flexWrap: "wrap", gap: 8 },
  chartTitle: { fontSize: 15, fontWeight: 700, color: "var(--text-primary)" },
  pnlToggle: { display: "flex", background: "var(--bg-panel)", borderRadius: 6, padding: 2, gap: 2 },
  pnlToggleBtn: { padding: "5px 12px", border: "none", background: "transparent", color: "var(--text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer", borderRadius: 4 },
  pnlToggleActive: { background: "var(--accent-solid, #3b82f6)", color: "#fff" },
  perfMsg: { height: 240, display: "grid", placeItems: "center", color: "var(--text-muted)", fontSize: 13 },
  empty: { display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "48px 20px", textAlign: "center" },
  tableWrap: { overflowX: "auto" },
  table: { width: "100%", borderCollapse: "collapse" },
  th: { textAlign: "left", padding: "8px 12px", fontSize: 11, fontWeight: 600, color: "var(--text-muted)", borderBottom: "1px solid var(--border)", whiteSpace: "nowrap" },
  tr: { borderBottom: "1px solid var(--border-soft)" },
  td: { padding: "10px 12px", fontSize: 13, color: "var(--text-primary)", whiteSpace: "nowrap" },
  symbolBadge: { padding: "2px 8px", borderRadius: 4, background: "rgba(37,99,235,0.15)", border: "1px solid var(--accent-border)", fontSize: 12, fontWeight: 600 },
  addBtn: { padding: "8px 16px", borderRadius: 8, border: "none", background: "var(--accent-solid)", color: "#fff", cursor: "pointer", fontWeight: 600, fontSize: 13 },
  importBtn: { padding: "8px 16px", borderRadius: 8, border: "1px solid var(--accent-border)", background: "transparent", color: "var(--accent-solid)", cursor: "pointer", fontWeight: 600, fontSize: 13, whiteSpace: "nowrap" },
  sampleLink: { fontSize: 12, color: "var(--text-muted)", textDecoration: "underline", whiteSpace: "nowrap" },
  clearBtn: { padding: "8px 16px", borderRadius: 8, border: "1px solid var(--danger-border)", background: "var(--danger-bg)", color: "var(--danger-text)", cursor: "pointer", fontWeight: 600, fontSize: 13 },
  editBtn: { padding: "6px 12px", borderRadius: 6, border: "1px solid var(--accent-border)", background: "transparent", color: "var(--accent-solid)", cursor: "pointer", fontSize: 12, fontWeight: 600 },
  chartBtnActive: { background: "var(--accent-solid, #3b82f6)", color: "#fff", borderColor: "var(--accent-solid, #3b82f6)" },
  deleteBtn: { padding: "6px 12px", borderRadius: 6, border: "1px solid var(--danger-border)", background: "var(--danger-bg)", color: "var(--danger-text)", cursor: "pointer", fontSize: 12, fontWeight: 500 },
  ghostBtn: { padding: "8px 16px", borderRadius: 8, border: "1px solid var(--border-card)", background: "transparent", color: "var(--text-primary)", cursor: "pointer" },
  primaryBtn: { padding: "8px 16px", borderRadius: 8, border: "none", background: "var(--accent-solid)", color: "#fff", cursor: "pointer", fontWeight: 600 },
  label: { fontSize: 12, color: "var(--text-muted)" },
  input: { padding: "9px 12px", borderRadius: 8, border: "1px solid var(--input-border)", background: "var(--input-bg)", color: "var(--text-primary)", outline: "none", width: "100%", boxSizing: "border-box" },
  dropdown: { position: "absolute", top: "100%", left: 0, right: 0, zIndex: 100, background: "var(--dropdown-bg)", border: "1px solid var(--border-card)", borderRadius: 8, marginTop: 4, overflow: "hidden" },
  dropdownItem: { display: "flex", alignItems: "center", padding: "9px 12px", cursor: "pointer", fontSize: 13, borderBottom: "1px solid var(--border-soft)", color: "var(--text-primary)" },
};

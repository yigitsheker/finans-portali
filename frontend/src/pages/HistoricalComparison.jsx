import { useEffect, useMemo, useRef, useState } from "react";
import PropTypes from "prop-types";
import Modal from "../components/Modal";
import ImportPreviewModal from "../components/ImportPreviewModal";
import InstrumentChartModal from "../components/InstrumentChartModal";
import CompareInstrumentsModal from "../components/CompareInstrumentsModal";
import { getLatestPrice, getMarketHistory, getMarketInstruments } from "../api/portfolioApi";
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
  // True once we've finished reading the localStorage seed. Stops the save
  // effect from firing on the very first render and overwriting persisted
  // data with the initial empty state — without it React would sometimes
  // setItem("[]") before the load effect's setPositions resolved.
  const [loaded, setLoaded] = useState(false);
  const [instruments, setInstruments] = useState([]);

  // Add modal state
  const [addOpen, setAddOpen] = useState(false);
  const [addSymbol, setAddSymbol] = useState("");
  const [addDate, setAddDate] = useState("");
  const [addLots, setAddLots] = useState(1);
  const [addLoading, setAddLoading] = useState(false);
  const [addError, setAddError] = useState(null);
  const [showSugg, setShowSugg] = useState(false);
  const [importing, setImporting] = useState(false);
  const [histPreview, setHistPreview] = useState(null);
  // Clicking a row opens its chart card (same modal as the Stocks page).
  const [chartTarget, setChartTarget] = useState(null);
  const [compareTarget, setCompareTarget] = useState(null);
  const fileRef = useRef(null);

  // Inline edit (lot + date) for an existing row.
  const [editTarget, setEditTarget] = useState(null);
  const [editLots, setEditLots] = useState(1);
  const [editDate, setEditDate] = useState("");
  const [editSaving, setEditSaving] = useState(false);
  const [editError, setEditError] = useState(null);

  const validSymbols = useMemo(
    () => new Set(instruments.map((i) => String(i.symbol).toUpperCase())),
    [instruments],
  );

  useEffect(() => {
    getMarketInstruments().then(setInstruments).catch(() => {});

    // Load from localStorage
    const saved = localStorage.getItem("historicalPositions");
    if (saved) {
      try {
        setPositions(JSON.parse(saved));
      } catch (e) {
        console.error("Failed to load historical positions:", e);
      }
    }
    setLoaded(true);
  }, []);

  // Save to localStorage whenever positions change. We gate on `loaded`
  // so the first mount doesn't immediately setItem("[]") and clobber the
  // persisted data before the load effect's setPositions has a chance to
  // resolve. Once loaded flips true the effect runs on every change —
  // including deletes that empty the list, which is what the earlier
  // `length > 0` guard was silently dropping.
  useEffect(() => {
    if (!loaded) return;
    localStorage.setItem("historicalPositions", JSON.stringify(positions));
  }, [positions, loaded]);

  const suggestions = useMemo(() => {
    if (!addSymbol.trim()) return instruments.slice(0, 8);
    const q = addSymbol.trim().toUpperCase();
    return instruments.filter((i) => i.symbol.includes(q) || i.name.toUpperCase().includes(q)).slice(0, 8);
  }, [addSymbol, instruments]);

  // Determine native storage currency for a symbol (used at add-time so
  // the stored row knows what currency its buyPrice/currentPrice are in).
  // Returns "₺" or "$" — the historical schema kept these symbol literals.
  const getCurrency = (symbol) => {
    if (!symbol) return "$";
    const s = symbol.toUpperCase();
    // FX pairs vs TRY (USDTRY, EURTRY, GBPTRY, ...) — quoted in TRY.
    if (s.endsWith("TRY")) return "₺";
    // BIST stocks: .IS suffix or 3-5 caps that match the known list.
    if (s.endsWith(".IS")) return "₺";
    if (/^[A-Z]{3,5}$/.test(s)) {
      const turkishStocks = ["THYAO", "AKBNK", "GARAN", "ISCTR", "YKBNK", "SAHOL", "TUPRS", "ASELS", "KCHOL", "PETKM"];
      if (turkishStocks.some(ts => s.startsWith(ts))) {
        return "₺";
      }
    }
    return "$";
  };

  // Build one historical position row (price at buy date from history, current
  // price, inflation-adjusted real return). Returns the row WITHOUT an id, or
  // null when there's no price history for that symbol/window. Shared by the
  // manual add modal and the Excel bulk-import.
  async function buildHistoricalPosition(sym, dateISO, lots) {
    const selectedDate = new Date(dateISO);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const currentPrice = await getLatestPrice(sym, keycloak);
    // No live quote (endpoint returned 0) → bail rather than persist a bogus
    // -100% row. Import counts it skipped; edit shows an error (keeps old row).
    if (!(currentPrice > 0)) return null;

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

    const buyPrice = closestPoint.close;
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
    if (addLots <= 0) {
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

      const pos = await buildHistoricalPosition(sym, addDate, addLots);
      if (!pos) {
        setAddError(t("historical.errNoHistory"));
        return;
      }

      setPositions([...positions, { id: Date.now().toString(), ...pos }]);
      setAddOpen(false);
      setAddSymbol("");
      setAddDate("");
      setAddLots(1);
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
      const fetched = await getMarketInstruments().catch(() => []);
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
        catalog = await getMarketInstruments().catch(() => []);
        if (catalog.length) setInstruments(catalog);
      }
      const valid = new Set(catalog.map((i) => String(i.symbol).toUpperCase()));
      // Compare dates as yyyy-MM-dd strings (local "today") to avoid the UTC
      // shift that new Date("yyyy-MM-dd") introduces near midnight.
      const now = new Date();
      const todayISO = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
      const rid = () => (typeof crypto !== "undefined" && crypto.randomUUID
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.round(Math.random() * 1e9)}`);

      const added = [];
      let skipped = 0;
      for (const row of rows) {
        const sym = String(row.symbol || "").trim().toUpperCase();
        const dateOk = row.date && row.date < todayISO;
        if (!sym || !valid.has(sym) || !(Number(row.lot) > 0) || !dateOk) { skipped++; continue; }
        try {
          const pos = await buildHistoricalPosition(sym, row.date, Number(row.lot));
          if (pos) added.push({ id: rid(), ...pos });
          else skipped++;
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
      const pos = await buildHistoricalPosition(editTarget.symbol, editDate, Number(editLots));
      if (!pos) { setEditError(t("historical.errNoHistory")); return; }
      setPositions((prev) => prev.map((x) => (x.id === editTarget.id ? { id: editTarget.id, ...pos } : x)));
      setEditTarget(null);
    } catch (e) {
      setEditError(e?.message ?? t("historical.errPrice"));
    } finally {
      setEditSaving(false);
    }
  }

  function onDelete(id) {
    setPositions(positions.filter(p => p.id !== id));
  }

  function openChart(p) {
    const inst = instruments.find((i) => i.symbol === p.symbol) || { symbol: p.symbol, name: p.name };
    setChartTarget(inst);
  }

  function onClearAll() {
    if (confirm(t("historical.confirmClearAll"))) {
      setPositions([]);
      localStorage.removeItem("historicalPositions");
    }
  }

  // Single-currency totals in the active display currency. Each row's
  // invested/current is converted from its native currency through
  // toEffective() before summing — mixed-currency portfolios stay coherent.
  const totals = useMemo(() => {
    let invested = 0;
    let current = 0;
    for (const p of positions) {
      const native = p.currency === "₺" ? "TRY" : "USD";
      invested += toEffective(p.buyPrice * p.lots, native);
      current += toEffective(p.currentPrice * p.lots, native);
    }
    const change = current - invested;
    const changePct = invested > 0 ? (change / invested) * 100 : 0;
    return { invested, current, change, changePct };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [positions, effectiveCurrency, usdRate]);

  return (
    <div style={s.root}>
      {/* Header */}
      <div style={s.header}>
        <div>
          <div style={s.title}>{t("historical.title")}</div>
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
            setAddLots(1);
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

      {/* Positions Table */}
      <div style={s.card}>
        {positions.length === 0 ? (
          <div style={s.empty}>
            <div style={{ fontSize: 48, marginBottom: 12 }}>📈</div>
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
                  const native = p.currency === "₺" ? "TRY" : "USD";
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
                      <td style={{ ...s.td, color: "var(--text-muted)", fontWeight: 500 }}>
                        {p.cumulativeInflationPct != null
                          ? "+" + Number(p.cumulativeInflationPct).toFixed(2) + "%"
                          : "—"}
                      </td>
                      <td style={{
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
                      onMouseDown={() => {
                        setAddSymbol(inst.symbol);
                        setShowSugg(false);
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
                min={1}
                onChange={(e) => setAddLots(Math.max(1, Number(e.target.value)))}
                style={s.input}
                placeholder="1"
              />
            </div>
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
  deleteBtn: { padding: "6px 12px", borderRadius: 6, border: "1px solid var(--danger-border)", background: "var(--danger-bg)", color: "var(--danger-text)", cursor: "pointer", fontSize: 12, fontWeight: 500 },
  ghostBtn: { padding: "8px 16px", borderRadius: 8, border: "1px solid var(--border-card)", background: "transparent", color: "var(--text-primary)", cursor: "pointer" },
  primaryBtn: { padding: "8px 16px", borderRadius: 8, border: "none", background: "var(--accent-solid)", color: "#fff", cursor: "pointer", fontWeight: 600 },
  label: { fontSize: 12, color: "var(--text-muted)" },
  input: { padding: "9px 12px", borderRadius: 8, border: "1px solid var(--input-border)", background: "var(--input-bg)", color: "var(--text-primary)", outline: "none", width: "100%", boxSizing: "border-box" },
  dropdown: { position: "absolute", top: "100%", left: 0, right: 0, zIndex: 100, background: "var(--dropdown-bg)", border: "1px solid var(--border-card)", borderRadius: 8, marginTop: 4, overflow: "hidden" },
  dropdownItem: { display: "flex", alignItems: "center", padding: "9px 12px", cursor: "pointer", fontSize: 13, borderBottom: "1px solid var(--border-soft)", color: "var(--text-primary)" },
};

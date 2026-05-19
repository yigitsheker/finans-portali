import { useEffect, useMemo, useState } from "react";
import Modal from "../components/Modal";
import { getLatestPrice, getMarketInstruments } from "../api/portfolioApi";
import { compareInflation } from "../api/inflationApi";
import { useI18n } from "../contexts/I18nContext";

export default function HistoricalComparison({ keycloak }) {
  const { t } = useI18n();
  const [positions, setPositions] = useState([]);
  const [instruments, setInstruments] = useState([]);

  // Add modal state
  const [addOpen, setAddOpen] = useState(false);
  const [addSymbol, setAddSymbol] = useState("");
  const [addDate, setAddDate] = useState("");
  const [addLots, setAddLots] = useState(1);
  const [addLoading, setAddLoading] = useState(false);
  const [addError, setAddError] = useState(null);
  const [showSugg, setShowSugg] = useState(false);

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
  }, []);

  // Save to localStorage whenever positions change
  useEffect(() => {
    if (positions.length > 0) {
      localStorage.setItem("historicalPositions", JSON.stringify(positions));
    }
  }, [positions]);

  const suggestions = useMemo(() => {
    if (!addSymbol.trim()) return instruments.slice(0, 8);
    const q = addSymbol.trim().toUpperCase();
    return instruments.filter((i) => i.symbol.includes(q) || i.name.toUpperCase().includes(q)).slice(0, 8);
  }, [addSymbol, instruments]);

  // Determine currency based on symbol
  const getCurrency = (symbol) => {
    // BIST stocks end with .IS or are Turkish symbols
    if (symbol.endsWith(".IS") || /^[A-Z]{3,5}$/.test(symbol)) {
      // Check if it's a known Turkish stock
      const turkishStocks = ["THYAO", "AKBNK", "GARAN", "ISCTR", "YKBNK", "SAHOL", "TUPRS", "ASELS", "KCHOL", "PETKM"];
      if (turkishStocks.some(ts => symbol.startsWith(ts))) {
        return "₺";
      }
    }
    return "$";
  };

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

      // Get current price
      const currentPrice = await getLatestPrice(sym, keycloak);

      // Calculate days difference to determine appropriate period
      const daysDiff = Math.floor((today.getTime() - selectedDate.getTime()) / (1000 * 60 * 60 * 24));
      let period = "30D";
      if (daysDiff > 365) period = "5Y";
      else if (daysDiff > 180) period = "1Y";
      else if (daysDiff > 90) period = "6M";
      else if (daysDiff > 30) period = "3M";

      // Fetch historical data
      const { getMarketHistory } = await import("../api/portfolioApi");
      const historyData = await getMarketHistory(sym, period);

      if (!historyData || historyData.length === 0) {
        setAddError(t("historical.errNoHistory"));
        return;
      }

      // Find the closest date to the selected date
      const targetTime = selectedDate.getTime();
      let closestPoint = historyData[0];
      let minDiff = Math.abs(new Date(historyData[0].day).getTime() - targetTime);

      for (const point of historyData) {
        const pointTime = new Date(point.day).getTime();
        const diff = Math.abs(pointTime - targetTime);
        if (diff < minDiff) {
          minDiff = diff;
          closestPoint = point;
        }
      }

      const buyPrice = closestPoint.close;
      const instrument = instruments.find(i => i.symbol === sym);
      const currency = getCurrency(sym);

      // Compute real return adjusted for cumulative CPI inflation over the same window.
      // /inflation/compare returns null when buy/today fall outside available CPI months;
      // we tolerate that and just leave realReturnPct empty.
      const nominalPct = buyPrice > 0 ? ((currentPrice - buyPrice) / buyPrice) * 100 : 0;
      const todayISO = new Date().toISOString().split("T")[0];
      let inflationData = null;
      try {
        inflationData = await compareInflation(addDate, todayISO, nominalPct);
      } catch (e) {
        console.debug("Inflation compare unavailable:", e?.message);
      }

      const newPosition = {
        id: Date.now().toString(),
        symbol: sym,
        name: instrument?.name || sym,
        buyDate: addDate,
        buyPrice,
        currentPrice,
        lots: addLots,
        currency,
        cumulativeInflationPct: inflationData?.cumulativeInflationPct ?? null,
        realReturnPct: inflationData?.realReturnPct ?? null,
      };

      setPositions([...positions, newPosition]);
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

  function onDelete(id) {
    setPositions(positions.filter(p => p.id !== id));
  }

  function onClearAll() {
    if (confirm(t("historical.confirmClearAll"))) {
      setPositions([]);
      localStorage.removeItem("historicalPositions");
    }
  }

  // Calculate totals
  const totals = useMemo(() => {
    let totalInvestedTRY = 0;
    let totalInvestedUSD = 0;
    let totalCurrentTRY = 0;
    let totalCurrentUSD = 0;

    positions.forEach(p => {
      const invested = p.buyPrice * p.lots;
      const current = p.currentPrice * p.lots;

      if (p.currency === "₺") {
        totalInvestedTRY += invested;
        totalCurrentTRY += current;
      } else {
        totalInvestedUSD += invested;
        totalCurrentUSD += current;
      }
    });

    const totalChangeTRY = totalCurrentTRY - totalInvestedTRY;
    const totalChangeUSD = totalCurrentUSD - totalInvestedUSD;
    const totalChangePctTRY = totalInvestedTRY > 0 ? (totalChangeTRY / totalInvestedTRY) * 100 : 0;
    const totalChangePctUSD = totalInvestedUSD > 0 ? (totalChangeUSD / totalInvestedUSD) * 100 : 0;

    return {
      totalInvestedTRY,
      totalInvestedUSD,
      totalCurrentTRY,
      totalCurrentUSD,
      totalChangeTRY,
      totalChangeUSD,
      totalChangePctTRY,
      totalChangePctUSD
    };
  }, [positions]);

  return (
    <div style={s.root}>
      {/* Header */}
      <div style={s.header}>
        <div>
          <div style={s.title}>{t("historical.title")}</div>
          <div style={s.subtitle}>{t("historical.subtitle")}</div>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
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
          {totals.totalInvestedTRY > 0 && (
            <>
              <SCard
                label={`${t("historical.totalInvest")} (TRY)`}
                value={"₺" + totals.totalInvestedTRY.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
              />
              <SCard
                label={`${t("historical.currentValue")} (TRY)`}
                value={"₺" + totals.totalCurrentTRY.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
              />
              <SCard
                label={`${t("historical.pnl")} (TRY)`}
                value={(totals.totalChangeTRY >= 0 ? "+₺" : "-₺") + Math.abs(totals.totalChangeTRY).toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
                sub={(totals.totalChangeTRY >= 0 ? "+" : "") + totals.totalChangePctTRY.toFixed(2) + "%"}
                valueColor={totals.totalChangeTRY >= 0 ? "var(--green)" : "var(--red)"}
              />
            </>
          )}
          {totals.totalInvestedUSD > 0 && (
            <>
              <SCard
                label={`${t("historical.totalInvest")} (USD)`}
                value={"$" + totals.totalInvestedUSD.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
              />
              <SCard
                label={`${t("historical.currentValue")} (USD)`}
                value={"$" + totals.totalCurrentUSD.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
              />
              <SCard
                label={`${t("historical.pnl")} (USD)`}
                value={(totals.totalChangeUSD >= 0 ? "+$" : "-$") + Math.abs(totals.totalChangeUSD).toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
                sub={(totals.totalChangeUSD >= 0 ? "+" : "") + totals.totalChangePctUSD.toFixed(2) + "%"}
                valueColor={totals.totalChangeUSD >= 0 ? "var(--green)" : "var(--red)"}
              />
            </>
          )}
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
                  {[t("historical.colSymbol"), t("historical.colName"), t("historical.colBuyDate"), t("historical.colLots"), t("historical.colBuy"), t("historical.colCurrent"), t("historical.colInvested"), t("historical.colValue"), t("historical.colPnl"), t("historical.colNominal"), t("historical.colInflation"), t("historical.colReal"), ""].map((h) => (
                    <th key={h} style={s.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {positions.map((p) => {
                  const invested = p.buyPrice * p.lots;
                  const current = p.currentPrice * p.lots;
                  const change = current - invested;
                  const changePct = invested > 0 ? (change / invested) * 100 : 0;
                  const isPositive = change >= 0;

                  return (
                    <tr key={p.id} style={s.tr}>
                      <td style={s.td}>
                        <span style={s.symbolBadge}>{p.symbol}</span>
                      </td>
                      <td style={{ ...s.td, color: "var(--text-muted)" }}>{p.name}</td>
                      <td style={{ ...s.td, fontSize: 12 }}>
                        {new Date(p.buyDate).toLocaleDateString("tr-TR")}
                      </td>
                      <td style={s.td}>{p.lots.toLocaleString("tr-TR")}</td>
                      <td style={s.td}>
                        {p.currency}{p.buyPrice.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
                      </td>
                      <td style={s.td}>
                        {p.currency}{p.currentPrice.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
                      </td>
                      <td style={s.td}>
                        {p.currency}{invested.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
                      </td>
                      <td style={{ ...s.td, fontWeight: 600 }}>
                        {p.currency}{current.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
                      </td>
                      <td style={{ ...s.td, color: isPositive ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                        {isPositive ? "+" : ""}{p.currency}{change.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
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
                        <button style={s.deleteBtn} onClick={() => onDelete(p.id)}>
                          {t("historical.delete")}
                        </button>
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
    </div>
  );
}

function SCard({ label, value, sub, valueColor }) {
  return (
    <div style={s.summaryCard}>
      <div style={s.summaryLabel}>{label}</div>
      <div style={{ ...s.summaryValue, color: valueColor ?? "var(--text-primary)" }}>{value}</div>
      {sub && <div style={s.summarySub}>{sub}</div>}
    </div>
  );
}

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
  clearBtn: { padding: "8px 16px", borderRadius: 8, border: "1px solid var(--danger-border)", background: "var(--danger-bg)", color: "var(--danger-text)", cursor: "pointer", fontWeight: 600, fontSize: 13 },
  deleteBtn: { padding: "6px 12px", borderRadius: 6, border: "1px solid var(--danger-border)", background: "var(--danger-bg)", color: "var(--danger-text)", cursor: "pointer", fontSize: 12, fontWeight: 500 },
  ghostBtn: { padding: "8px 16px", borderRadius: 8, border: "1px solid var(--border-card)", background: "transparent", color: "var(--text-primary)", cursor: "pointer" },
  primaryBtn: { padding: "8px 16px", borderRadius: 8, border: "none", background: "var(--accent-solid)", color: "#fff", cursor: "pointer", fontWeight: 600 },
  label: { fontSize: 12, color: "var(--text-muted)" },
  input: { padding: "9px 12px", borderRadius: 8, border: "1px solid var(--input-border)", background: "var(--input-bg)", color: "var(--text-primary)", outline: "none", width: "100%", boxSizing: "border-box" },
  dropdown: { position: "absolute", top: "100%", left: 0, right: 0, zIndex: 100, background: "var(--dropdown-bg)", border: "1px solid var(--border-card)", borderRadius: 8, marginTop: 4, overflow: "hidden" },
  dropdownItem: { display: "flex", alignItems: "center", padding: "9px 12px", cursor: "pointer", fontSize: 13, borderBottom: "1px solid var(--border-soft)", color: "var(--text-primary)" },
};

import { useEffect, useState } from "react";
import PropTypes from "prop-types";
import { PortfolioAreaChart } from "../../common/PortfolioAreaChart";
import { getPortfolioTransactions } from "../../../api/portfolioApi";
import { useI18n } from "../../../contexts/I18nContext";

const GREEN = "#16a34a";
const RED = "#dc2626";

const fmt = (n) => Number(n || 0).toLocaleString("tr-TR", { maximumFractionDigits: 2 });
// Quantities can be fractional for crypto (e.g. 0.0001 BTC) — show up to 6 dp.
const fmtQty = (n) => Number(n || 0).toLocaleString("tr-TR", { maximumFractionDigits: 6 });
const fmtDate = (iso) => {
  try { return new Date(iso).toLocaleString("tr-TR", { dateStyle: "short", timeStyle: "short" }); }
  catch { return String(iso ?? ""); }
};

/**
 * Observable buy/sell movement history + a realized (closed-position) P&L chart.
 * Realized P&L per symbol survives even after a position is fully sold, because
 * it comes from the backend transaction ledger, not the live position rows.
 */
export default function PortfolioHistory({ keycloak, reloadSignal }) {
  const { t } = useI18n();
  const [txns, setTxns] = useState([]);
  const [loading, setLoading] = useState(true);

  // Re-fetch whenever the portfolio mutates (buy/sell/import) — `reloadSignal`
  // changes reference on every refresh — so a fresh sell shows up immediately
  // instead of only after a full page reload.
  useEffect(() => {
    let cancel = false;
    getPortfolioTransactions(keycloak)
      .then((d) => { if (!cancel) setTxns(Array.isArray(d) ? d : []); })
      .catch(() => { if (!cancel) setTxns([]); })
      .finally(() => { if (!cancel) setLoading(false); });
    return () => { cancel = true; };
  }, [keycloak, reloadSignal]);

  if (loading) return null;

  // Cumulative realized (closed-position) P&L over time, from SELL movements.
  // A 0 baseline the day before the first sell guarantees >=2 points so even a
  // single closed position draws a line (lightweight-charts, like the portfolio
  // performance chart).
  const sells = txns
    .filter((tx) => tx.type === "SELL" && tx.realizedPnl != null)
    .map((tx) => ({ day: String(tx.executedAt).slice(0, 10), pnl: Number(tx.realizedPnl) }))
    .sort((a, b) => a.day.localeCompare(b.day));
  const closedSeries = [];
  if (sells.length > 0) {
    const base = new Date(sells[0].day);
    base.setDate(base.getDate() - 1);
    closedSeries.push({ time: base.toISOString().slice(0, 10), value: 0 });
    let cum = 0;
    const byDay = new Map();
    for (const sx of sells) { cum += sx.pnl; byDay.set(sx.day, Number(cum.toFixed(2))); }
    for (const [day, value] of byDay) closedSeries.push({ time: day, value });
    // Carry the last realized P&L forward to today as a flat line — on days with
    // no sales the cumulative value doesn't change, so the chart stays level
    // instead of stopping at the last sell date.
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
    const lastSellDay = sells[sells.length - 1].day;
    if (today > lastSellDay) closedSeries.push({ time: today, value: Number(cum.toFixed(2)) });
  }

  return (
    <div style={s.wrap}>
      {closedSeries.length >= 2 && (
        <div style={s.card}>
          <div style={s.title}>{t("portfolio.closedPnlTitle")}</div>
          <div style={s.sub}>{t("portfolio.closedPnlSub")}</div>
          <PortfolioAreaChart data={closedSeries} height={240} />
        </div>
      )}

      <div style={s.card}>
        <div style={s.title}>{t("portfolio.historyTitle")}</div>
        {txns.length === 0 ? (
          <div style={s.empty}>{t("portfolio.noMovements")}</div>
        ) : (
        <div style={s.tableWrap} className="fp-table-scroll">
          <table style={s.table}>
            <thead>
              <tr>
                <th style={s.th}>{t("portfolio.colDate")}</th>
                <th style={s.th}>{t("portfolio.colType")}</th>
                <th style={s.th}>{t("portfolio.colSymbol")}</th>
                <th style={{ ...s.th, textAlign: "right" }}>{t("portfolio.colQty")}</th>
                <th style={{ ...s.th, textAlign: "right" }}>{t("portfolio.colPrice")}</th>
                <th style={{ ...s.th, textAlign: "right" }}>{t("portfolio.colAmount")}</th>
                <th style={{ ...s.th, textAlign: "right" }}>{t("portfolio.realizedPnl")}</th>
              </tr>
            </thead>
            <tbody>
              {txns.map((tx) => {
                const buy = tx.type === "BUY";
                return (
                  <tr key={tx.id} style={s.tr}>
                    <td style={s.td}>{fmtDate(tx.executedAt)}</td>
                    <td style={{ ...s.td, color: buy ? GREEN : RED, fontWeight: 700 }}>
                      {t(buy ? "portfolio.buy" : "portfolio.sell")}
                    </td>
                    <td style={{ ...s.td, fontWeight: 600 }}>{tx.symbol}</td>
                    <td style={{ ...s.td, textAlign: "right" }}>{fmtQty(tx.quantity)}</td>
                    <td style={{ ...s.td, textAlign: "right" }}>{tx.price != null ? "₺" + fmt(tx.price) : "—"}</td>
                    <td style={{ ...s.td, textAlign: "right" }}>{tx.amount != null ? "₺" + fmt(tx.amount) : "—"}</td>
                    <td style={{ ...s.td, textAlign: "right", fontWeight: 600, color: tx.realizedPnl == null ? "var(--text-muted)" : (tx.realizedPnl >= 0 ? GREEN : RED) }}>
                      {tx.realizedPnl != null ? `${tx.realizedPnl >= 0 ? "+" : ""}₺${fmt(tx.realizedPnl)}` : "—"}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        )}
      </div>
    </div>
  );
}

PortfolioHistory.propTypes = { keycloak: PropTypes.object.isRequired, reloadSignal: PropTypes.any };

const s = {
  wrap: { display: "flex", flexDirection: "column", gap: 16 },
  card: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-card)", padding: "16px 18px" },
  title: { fontSize: 15, fontWeight: 700, color: "var(--text-primary)" },
  sub: { fontSize: 12, color: "var(--text-muted)", margin: "2px 0 10px" },
  tableWrap: { overflowX: "auto", marginTop: 12 },
  table: { width: "100%", borderCollapse: "collapse" },
  th: { textAlign: "left", padding: "8px 12px", fontSize: 11, fontWeight: 600, color: "var(--text-muted)", borderBottom: "1px solid var(--border)", textTransform: "uppercase", whiteSpace: "nowrap" },
  tr: { borderBottom: "1px solid var(--border-soft)" },
  td: { padding: "9px 12px", fontSize: 13, color: "var(--text-primary)", whiteSpace: "nowrap" },
  empty: { padding: "18px 4px", fontSize: 13, color: "var(--text-muted)", textAlign: "center" },
};

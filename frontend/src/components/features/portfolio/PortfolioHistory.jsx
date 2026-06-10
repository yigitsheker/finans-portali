import { useEffect, useState } from "react";
import PropTypes from "prop-types";
import { BarChart, Bar, Cell, XAxis, YAxis, Tooltip, ResponsiveContainer, ReferenceLine } from "recharts";
import { getPortfolioTransactions } from "../../../api/portfolioApi";
import { useI18n } from "../../../contexts/I18nContext";

const GREEN = "#16a34a";
const RED = "#dc2626";

const fmt = (n) => Number(n || 0).toLocaleString("tr-TR", { maximumFractionDigits: 2 });
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

  // Realized P&L per symbol = Σ SELL realizedPnl (a "closed position" result).
  const bySymbol = new Map();
  for (const tx of txns) {
    if (tx.type === "SELL" && tx.realizedPnl != null) {
      bySymbol.set(tx.symbol, (bySymbol.get(tx.symbol) || 0) + Number(tx.realizedPnl));
    }
  }
  const closed = [...bySymbol.entries()]
    .map(([symbol, pnl]) => ({ symbol, pnl }))
    .sort((a, b) => b.pnl - a.pnl);

  const isDark = document.documentElement.getAttribute("data-theme") !== "light";
  const tipBg = isDark ? "#1c2128" : "#ffffff";
  const tipBorder = isDark ? "#30363d" : "#d0d7de";
  const tipColor = isDark ? "#e6edf3" : "#1f2328";

  return (
    <div style={s.wrap}>
      {closed.length > 0 && (
        <div style={s.card}>
          <div style={s.title}>{t("portfolio.closedPnlTitle")}</div>
          <div style={s.sub}>{t("portfolio.closedPnlSub")}</div>
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={closed} margin={{ top: 18, right: 12, left: 0, bottom: 4 }}>
              <XAxis dataKey="symbol" tick={{ fill: "var(--text-muted)", fontSize: 11 }} />
              <YAxis tick={{ fill: "var(--text-muted)", fontSize: 11 }} tickFormatter={(v) => fmt(v)} width={72} />
              <ReferenceLine y={0} stroke={tipBorder} />
              <Tooltip
                cursor={{ fill: "rgba(125,125,125,0.08)" }}
                contentStyle={{ background: tipBg, border: `1px solid ${tipBorder}`, borderRadius: 6, color: tipColor, fontSize: 12 }}
                formatter={(v) => [`${v >= 0 ? "+" : ""}₺${fmt(v)}`, t("portfolio.realizedPnl")]}
              />
              <Bar dataKey="pnl" radius={[3, 3, 0, 0]}>
                {closed.map((d, i) => <Cell key={i} fill={d.pnl >= 0 ? GREEN : RED} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
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
                    <td style={{ ...s.td, textAlign: "right" }}>{fmt(tx.quantity)}</td>
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

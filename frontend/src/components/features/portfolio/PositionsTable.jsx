import PropTypes from "prop-types";
import { portfolioStyles as s } from "./portfolioStyles";
import { usePriceDisplay } from "../../../contexts/CurrencyDisplayContext";
import { useI18n } from "../../../contexts/I18nContext";
import TermInfo from "../../common/TermInfo";

/**
 * Two render paths exist for historical reasons:
 *   1) summaryDetail present  → backend returns rich per-position figures
 *      (buyPrice, currentPrice, currentValue, dailyChangePercent, totalChangePercent,
 *       currency). Use these directly.
 *   2) Fallback  → only have items[] + spot prices[]; compute change locally.
 *
 * Either way every monetary value is fed through usePriceDisplay.format()
 * so the currency-mode toggle in the topbar drives the entire table.
 */
export function PositionsTable({
  loading,
  items,
  prices,
  marketData,
  summaryDetail,
  openAdd,
  openSell,
}) {
  const { format: formatPrice } = usePriceDisplay();
  const { t } = useI18n();

  // Helper: look up the InstrumentType for a symbol from the loaded market data.
  // Used in the fallback path where positions don't carry currency.
  const typeOf = (symbol) =>
    marketData.find((m) => m.symbol === symbol)?.type;

  return (
    <div style={s.holdingsCard}>
      <div style={s.holdingsHeader}>
        <div>
          <div style={s.chartTitle}>{t("portfolio.positionsTitle")}</div>
          <div style={s.chartSub}>{t("portfolio.positionsSub")}</div>
        </div>
        <button style={s.addBtn} onClick={openAdd}>
          {t("portfolio.addPosition")}
        </button>
      </div>

      {loading ? (
        <div style={s.empty}>{t("portfolio.loading")}</div>
      ) : items.length === 0 ? (
        <div style={s.emptyBox}>
          <div style={{ fontWeight: 600, fontSize: 15, marginBottom: 6 }}>{t("portfolio.emptyTitle")}</div>
          <div style={{ color: "var(--text-muted)", fontSize: 13 }}>{t("portfolio.emptySub")}</div>
          <button style={{ ...s.addBtn, marginTop: 14 }} onClick={openAdd}>{t("portfolio.addPosition")}</button>
        </div>
      ) : (
        <div style={s.tableWrap}>
          <table style={s.table}>
            <thead>
              <tr>
                {[
                  { label: t("portfolio.colSymbol"), term: "position" },
                  { label: t("portfolio.colName") },
                  { label: t("portfolio.colQty") },
                  { label: t("portfolio.colBuyDate") },
                  { label: t("portfolio.colBuyPrice"), term: "avg_cost" },
                  { label: t("portfolio.colCurrentPrice") },
                  { label: t("portfolio.colValue") },
                  { label: t("portfolio.colTotalChange"), term: "pnl" },
                  { label: t("portfolio.colDailyChange") },
                  { label: "" },
                ].map((h, idx) => (
                  <th key={idx} style={s.th}>
                    {h.label}
                    {h.term && <> <TermInfo termKey={h.term} placement="bottom" /></>}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {summaryDetail && summaryDetail.positions.length > 0
                ? summaryDetail.positions.map((position) => {
                  const sourcePosition = items.find((item) => item.symbol === position.symbol);
                  if (!sourcePosition) return null;

                  const totalChangePos = position.totalChangePercent >= 0;
                  // Backend returns null daily change when the quote has no
                  // changePct field — we show "—" rather than fake a 0.00%.
                  const hasDaily = position.dailyChangePercent != null;
                  const dailyChangePos = hasDaily && position.dailyChangePercent >= 0;
                  const purchaseDate = position.buyDate ? new Date(position.buyDate).toLocaleDateString("tr-TR") : "-";
                  // position.currency is the authoritative native currency on this path.
                  const cur = position.currency;

                  return (
                    <tr key={position.symbol} style={s.tr}>
                      <td style={s.td}><span style={s.symbolBadge}>{position.symbol}</span></td>
                      <td style={{ ...s.td, color: "var(--text-muted)" }}>{position.name ?? "-"}</td>
                      <td style={s.td}>{position.quantity.toLocaleString("tr-TR")}</td>
                      <td style={{ ...s.td, color: "var(--text-muted)", fontSize: 12 }}>{purchaseDate}</td>
                      <td style={s.td}>{formatPrice(position.buyPrice, null, { currency: cur })}</td>
                      <td style={s.td}>{formatPrice(position.currentPrice, null, { currency: cur })}</td>
                      <td style={{ ...s.td, fontWeight: 600 }}>{formatPrice(position.currentValue, null, { currency: cur })}</td>
                      <td style={{ ...s.td, color: totalChangePos ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                        {totalChangePos ? "+" : ""}{position.totalChangePercent.toFixed(2)}%
                      </td>
                      <td style={{
                        ...s.td,
                        color: hasDaily ? (dailyChangePos ? "var(--green)" : "var(--red)") : "var(--text-muted)",
                        fontWeight: 600,
                      }}>
                        {hasDaily
                          ? `${dailyChangePos ? "▲ +" : "▼ "}${Math.abs(position.dailyChangePercent).toFixed(2)}%`
                          : "—"}
                      </td>
                      <td style={s.td}>
                        <button style={s.sellBtn} onClick={() => openSell(sourcePosition)}>{t("portfolio.sell")}</button>
                      </td>
                    </tr>
                  );
                })
                : items.map((position) => {
                  const qty = Number(position.quantity);
                  const cost = Number(position.avgCost ?? 0);
                  const current = prices[position.symbol] ?? cost;
                  const value = current * qty;
                  const change = cost > 0 ? ((current - cost) / cost) * 100 : 0;
                  const totalChangePos = change >= 0;
                  const market = marketData.find((item) => item.symbol === position.symbol);
                  // Distinguish "no quote data" from "0% change" so the column
                  // doesn't fake movement when the scheduler hasn't run yet.
                  const hasDaily = market?.changePct != null;
                  const dailyChangePct = market?.changePct ?? 0;
                  const dailyChangePos = hasDaily && dailyChangePct >= 0;
                  const purchaseDate = position.purchaseDate ? new Date(position.purchaseDate).toLocaleDateString("tr-TR") : "-";
                  const type = typeOf(position.symbol);

                  return (
                    <tr key={position.symbol} style={s.tr}>
                      <td style={s.td}><span style={s.symbolBadge}>{position.symbol}</span></td>
                      <td style={{ ...s.td, color: "var(--text-muted)" }}>{market?.name ?? "-"}</td>
                      <td style={s.td}>{qty.toLocaleString("tr-TR")}</td>
                      <td style={{ ...s.td, color: "var(--text-muted)", fontSize: 12 }}>{purchaseDate}</td>
                      <td style={s.td}>{formatPrice(cost, type, { symbol: position.symbol })}</td>
                      <td style={s.td}>{formatPrice(current, type, { symbol: position.symbol })}</td>
                      <td style={{ ...s.td, fontWeight: 600 }}>{formatPrice(value, type, { symbol: position.symbol })}</td>
                      <td style={{ ...s.td, color: totalChangePos ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                        {totalChangePos ? "+" : ""}{change.toFixed(2)}%
                      </td>
                      <td style={{
                        ...s.td,
                        color: hasDaily ? (dailyChangePos ? "var(--green)" : "var(--red)") : "var(--text-muted)",
                        fontWeight: 600,
                      }}>
                        {hasDaily
                          ? `${dailyChangePos ? "▲ +" : "▼ "}${Math.abs(dailyChangePct).toFixed(2)}%`
                          : "—"}
                      </td>
                      <td style={s.td}>
                        <button style={s.sellBtn} onClick={() => openSell(position)}>{t("portfolio.sell")}</button>
                      </td>
                    </tr>
                  );
                })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

PositionsTable.propTypes = {
  loading: PropTypes.bool,
  items: PropTypes.array.isRequired,
  prices: PropTypes.object,
  marketData: PropTypes.array.isRequired,
  summaryDetail: PropTypes.object,
  openAdd: PropTypes.func.isRequired,
  openSell: PropTypes.func.isRequired,
};

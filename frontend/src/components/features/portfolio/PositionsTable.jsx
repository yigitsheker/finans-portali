import { portfolioStyles as s } from "./portfolioStyles";

export function PositionsTable({
  loading,
  items,
  prices,
  marketData,
  summaryDetail,
  openAdd,
  openSell,
}) {
  return (
    <div style={s.holdingsCard}>
      <div style={s.holdingsHeader}>
        <div>
          <div style={s.chartTitle}>Pozisyonlar</div>
          <div style={s.chartSub}>Mevcut yatirim pozisyonlariniz</div>
        </div>
        <button style={s.addBtn} onClick={openAdd}>
          + Pozisyon Ekle
        </button>
      </div>

      {loading ? (
        <div style={s.empty}>Yukleniyor...</div>
      ) : items.length === 0 ? (
        <div style={s.emptyBox}>
          <div style={{ fontWeight: 600, fontSize: 15, marginBottom: 6 }}>Henuz pozisyon yok</div>
          <div style={{ color: "var(--text-muted)", fontSize: 13 }}>Ilk pozisyonunu ekleyerek portfoyunu olustur.</div>
          <button style={{ ...s.addBtn, marginTop: 14 }} onClick={openAdd}>+ Pozisyon Ekle</button>
        </div>
      ) : (
        <div style={s.tableWrap}>
          <table style={s.table}>
            <thead>
              <tr>
                {["Sembol", "Isim", "Adet", "Alis Tarihi", "Alis Fiyati", "Guncel Fiyat", "Deger", "Toplam Degisim", "Gunluk Degisim", ""].map((heading) => (
                  <th key={heading} style={s.th}>{heading}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {summaryDetail && summaryDetail.positions.length > 0
                ? summaryDetail.positions.map((position) => {
                  const sourcePosition = items.find((item) => item.symbol === position.symbol);
                  if (!sourcePosition) return null;

                  const totalChangePos = position.totalChangePercent >= 0;
                  const dailyChangePos = position.dailyChangePercent >= 0;
                  const purchaseDate = position.buyDate ? new Date(position.buyDate).toLocaleDateString("tr-TR") : "-";
                  const currencySymbol = position.currency === "USD" ? "$" : "₺";
                  const buyPriceFormatted = position.buyPrice > 0 ? currencySymbol + position.buyPrice.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-";
                  const currentPriceFormatted = position.currentPrice > 0 ? currencySymbol + position.currentPrice.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-";
                  const usdtry = marketData.find((item) => item.symbol === "USDTRY");
                  const usdRate = usdtry?.last ?? 35.0;
                  const currentValueInTRY = position.currency === "USD" ? position.currentValue * usdRate : position.currentValue;

                  return (
                    <tr key={position.symbol} style={s.tr}>
                      <td style={s.td}><span style={s.symbolBadge}>{position.symbol}</span></td>
                      <td style={{ ...s.td, color: "var(--text-muted)" }}>{position.name ?? "-"}</td>
                      <td style={s.td}>{position.quantity.toLocaleString("tr-TR")}</td>
                      <td style={{ ...s.td, color: "var(--text-muted)", fontSize: 12 }}>{purchaseDate}</td>
                      <td style={s.td}>{buyPriceFormatted}</td>
                      <td style={s.td}>{currentPriceFormatted}</td>
                      <td style={{ ...s.td, fontWeight: 600 }}>{"₺" + currentValueInTRY.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}</td>
                      <td style={{ ...s.td, color: totalChangePos ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                        {totalChangePos ? "+" : ""}{position.totalChangePercent.toFixed(2)}%
                      </td>
                      <td style={{ ...s.td, color: dailyChangePos ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                        {dailyChangePos ? "▲ +" : "▼ "}{Math.abs(position.dailyChangePercent).toFixed(2)}%
                      </td>
                      <td style={s.td}>
                        <button style={s.sellBtn} onClick={() => openSell(sourcePosition)}>Sat</button>
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
                  const dailyChangePct = market?.changePct ?? 0;
                  const dailyChangePos = dailyChangePct >= 0;
                  const purchaseDate = position.purchaseDate ? new Date(position.purchaseDate).toLocaleDateString("tr-TR") : "-";

                  return (
                    <tr key={position.symbol} style={s.tr}>
                      <td style={s.td}><span style={s.symbolBadge}>{position.symbol}</span></td>
                      <td style={{ ...s.td, color: "var(--text-muted)" }}>{market?.name ?? "-"}</td>
                      <td style={s.td}>{qty.toLocaleString("tr-TR")}</td>
                      <td style={{ ...s.td, color: "var(--text-muted)", fontSize: 12 }}>{purchaseDate}</td>
                      <td style={s.td}>{cost > 0 ? "₺" + cost.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}</td>
                      <td style={s.td}>{current > 0 ? "₺" + current.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}</td>
                      <td style={{ ...s.td, fontWeight: 600 }}>{value > 0 ? "₺" + value.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}</td>
                      <td style={{ ...s.td, color: totalChangePos ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                        {totalChangePos ? "+" : ""}{change.toFixed(2)}%
                      </td>
                      <td style={{ ...s.td, color: dailyChangePos ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                        {dailyChangePos ? "▲ +" : "▼ "}{Math.abs(dailyChangePct).toFixed(2)}%
                      </td>
                      <td style={s.td}>
                        <button style={s.sellBtn} onClick={() => openSell(position)}>Sat</button>
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

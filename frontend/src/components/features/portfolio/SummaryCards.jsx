import { SummaryCard } from "./InfoRow";
import { portfolioStyles as s } from "./portfolioStyles";

export function SummaryCards({ stats, loading, error }) {
  const gainPos = stats.totalGain >= 0;

  return (
    <div style={s.summaryGrid}>
      <SummaryCard
        label="Toplam Portfoy Degeri"
        value={"₺" + stats.totalValue.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
        sub={"Maliyet: ₺" + stats.totalCost.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
      />
      <SummaryCard
        label="Toplam Kazanc / Kayip"
        value={(gainPos ? "+₺" : "-₺") + Math.abs(stats.totalGain).toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
        sub={(gainPos ? "+" : "") + stats.totalGainPct.toFixed(2) + "% tum zamanlarda"}
        valueColor={gainPos ? "var(--green)" : "var(--red)"}
      />
      <SummaryCard label="Pozisyon Sayisi" value={String(stats.count)} sub="Hisse, ETF ve kripto" />
      <SummaryCard label="Durum" value={loading ? "Yukleniyor..." : error ? "Hata" : "Guncel"} sub="Portfoy durumu" />
    </div>
  );
}

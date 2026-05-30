import PropTypes from "prop-types";
import { SummaryCard } from "./InfoRow";
import { portfolioStyles as s } from "./portfolioStyles";
import { usePriceDisplay } from "../../../contexts/CurrencyDisplayContext";
import DataFreshnessHeader from "../../common/DataFreshnessHeader";
import TermInfo from "../../common/TermInfo";

/**
 * Stats coming in from usePortfolioPage are already summed in TRY
 * (positions in USD are pre-multiplied by spot USDTRY there). We treat
 * the figures as native-TRY and let formatPrice handle the display
 * conversion based on the topbar toggle.
 *
 * The "Son güncelleme + Yenile" chrome is now the shared
 * <DataFreshnessHeader> so Bonds / FX / etc. can reuse the exact same
 * pattern without each page rolling its own relative-time formatter.
 */
export function SummaryCards({ stats, loading, error, asOf, onRefresh, refreshing }) {
  const { format: formatPrice, convert } = usePriceDisplay();
  const gainPos = stats.totalGain >= 0;

  // Build a signed string for the gain card. `convert` does the math; we just
  // attach the +/- prefix and the converted currency symbol.
  const gainConverted = convert(Math.abs(stats.totalGain), null, undefined, "TRY");
  const gainText = gainConverted.value == null
    ? "—"
    : (gainPos ? "+" : "-") +
      gainConverted.symbol +
      new Intl.NumberFormat("tr-TR", { maximumFractionDigits: 2 }).format(gainConverted.value);

  return (
    <div>
      <DataFreshnessHeader asOf={asOf} onRefresh={onRefresh} refreshing={refreshing} />

      <div style={s.summaryGrid}>
        <SummaryCard
          label={<>Toplam Portfoy Degeri <TermInfo termKey="position" /></>}
          value={formatPrice(stats.totalValue, null, { currency: "TRY", maxDigits: 2 })}
          sub={"Maliyet: " + formatPrice(stats.totalCost, null, { currency: "TRY", maxDigits: 2 })}
        />
        <SummaryCard
          label={<>Toplam Kazanc / Kayip <TermInfo termKey="pnl" /></>}
          value={gainText}
          sub={(gainPos ? "+" : "") + stats.totalGainPct.toFixed(2) + "% tum zamanlarda"}
          valueColor={gainPos ? "var(--green)" : "var(--red)"}
        />
        <SummaryCard label="Pozisyon Sayisi" value={String(stats.count)} sub="Hisse, ETF ve kripto" />
        <SummaryCard label="Durum" value={loading ? "Yukleniyor..." : error ? "Hata" : "Guncel"} sub="Portfoy durumu" />
      </div>
    </div>
  );
}

SummaryCards.propTypes = {
  stats: PropTypes.shape({
    totalValue: PropTypes.number,
    totalCost: PropTypes.number,
    totalGain: PropTypes.number,
    totalGainPct: PropTypes.number,
    count: PropTypes.number,
  }).isRequired,
  loading: PropTypes.bool,
  error: PropTypes.any,
  asOf: PropTypes.oneOfType([PropTypes.string, PropTypes.instanceOf(Date)]),
  onRefresh: PropTypes.func,
  refreshing: PropTypes.bool,
};

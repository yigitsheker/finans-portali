import { useEffect, useState } from "react";
import PropTypes from "prop-types";
import { SummaryCard } from "./InfoRow";
import { portfolioStyles as s } from "./portfolioStyles";
import { usePriceDisplay } from "../../../contexts/CurrencyDisplayContext";
import DataFreshnessHeader from "../../common/DataFreshnessHeader";
import TermInfo from "../../common/TermInfo";

// BIST equity continuous session ≈ 10:00–18:00 Europe/Istanbul, Mon–Fri.
// Public holidays aren't modeled — this is an approximation good enough for a
// status badge. The Date round-trip reads Istanbul wall-clock regardless of the
// viewer's own timezone.
function isBistOpen(now) {
  const ist = new Date(now.toLocaleString("en-US", { timeZone: "Europe/Istanbul" }));
  const day = ist.getDay(); // 0 Sun … 6 Sat
  const mins = ist.getHours() * 60 + ist.getMinutes();
  return day >= 1 && day <= 5 && mins >= 600 && mins < 1080;
}

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

  // Tick every minute so the market-open state and freshness stay live even if
  // the user leaves the page sitting open.
  const [, setTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 60000);
    return () => clearInterval(id);
  }, []);

  // "Durum" card combines data freshness (value) with BIST market state (sub).
  const now = new Date();
  const marketOpen = isBistOpen(now);
  const asOfMs = asOf ? new Date(asOf).getTime() : NaN;
  const ageMin = Number.isFinite(asOfMs) ? (now.getTime() - asOfMs) / 60000 : null;
  // Stale only matters while the market is open — when it's closed the last
  // close IS the current value, so the status stays "Güncel".
  const stale = marketOpen && ageMin != null && ageMin > 20;
  const statusValue = loading ? "Yukleniyor..." : error ? "Hata" : stale ? "Gecikmeli" : "Guncel";
  const statusColor = loading ? undefined : error ? "var(--red)" : stale ? "var(--amber)" : "var(--green)";
  const marketSub = (
    <span>
      <span style={{ color: marketOpen ? "var(--green)" : "var(--text-faint)" }}>●</span>{" "}
      {marketOpen ? "BIST acik" : "BIST kapali"}
    </span>
  );

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
        <SummaryCard label="Durum" value={statusValue} sub={marketSub} valueColor={statusColor} />
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

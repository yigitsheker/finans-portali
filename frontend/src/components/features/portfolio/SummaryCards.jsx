import { SummaryCard } from "./InfoRow";
import { portfolioStyles as s } from "./portfolioStyles";
import { usePriceDisplay } from "../../../contexts/CurrencyDisplayContext";

/**
 * Stats coming in from usePortfolioPage are already summed in TRY
 * (positions in USD are pre-multiplied by spot USDTRY there). We treat
 * the figures as native-TRY and let formatPrice handle the display
 * conversion based on the topbar toggle.
 *
 * Props:
 *   asOf       — ISO/Date string of the freshest quote across positions.
 *                Rendered as "Güncelleme: HH:mm" so the user can see how
 *                stale the numbers are.
 *   onRefresh  — manual refetch handler (button next to the title).
 *   refreshing — disables the button while a refetch is in flight.
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

  const asOfLabel = asOf ? formatRelativeTime(asOf) : null;

  return (
    <div>
      {(asOfLabel || onRefresh) && (
        <div style={styles.header}>
          {asOfLabel && (
            <span style={styles.asOf} title={asOf ? new Date(asOf).toLocaleString("tr-TR") : ""}>
              Son güncelleme: <strong>{asOfLabel}</strong>
            </span>
          )}
          {onRefresh && (
            <button
              type="button"
              onClick={onRefresh}
              disabled={refreshing}
              style={{
                ...styles.refreshBtn,
                opacity: refreshing ? 0.6 : 1,
                cursor: refreshing ? "wait" : "pointer",
              }}
              title="Verileri yeniden yükle"
            >
              <span style={{ display: "inline-block", transform: refreshing ? "rotate(180deg)" : "none", transition: "transform 200ms" }}>
                ⟳
              </span>
              <span style={{ marginLeft: 6 }}>Yenile</span>
            </button>
          )}
        </div>
      )}

      <div style={s.summaryGrid}>
        <SummaryCard
          label="Toplam Portfoy Degeri"
          value={formatPrice(stats.totalValue, null, { currency: "TRY", maxDigits: 2 })}
          sub={"Maliyet: " + formatPrice(stats.totalCost, null, { currency: "TRY", maxDigits: 2 })}
        />
        <SummaryCard
          label="Toplam Kazanc / Kayip"
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

/**
 * "az önce" / "5 dakika önce" / "13:42" — favours absolute time over relative
 * once the gap exceeds an hour, so the user doesn't have to do mental math
 * for stale data.
 */
function formatRelativeTime(input) {
  const t = new Date(input).getTime();
  if (Number.isNaN(t)) return null;
  const diffMin = Math.round((Date.now() - t) / 60000);
  if (diffMin < 1) return "az önce";
  if (diffMin < 60) return `${diffMin} dakika önce`;
  const sameDay = new Date(t).toDateString() === new Date().toDateString();
  if (sameDay) {
    return new Date(t).toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" });
  }
  return new Date(t).toLocaleString("tr-TR", {
    day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit",
  });
}

const styles = {
  header: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 8,
    gap: 12,
    flexWrap: "wrap",
  },
  asOf: {
    fontSize: 12,
    color: "var(--text-muted)",
  },
  refreshBtn: {
    padding: "6px 12px",
    background: "var(--bg-panel)",
    color: "var(--text-primary)",
    border: "1px solid var(--border)",
    borderRadius: 8,
    fontSize: 13,
    fontWeight: 500,
    display: "inline-flex",
    alignItems: "center",
  },
};

import { useI18n } from "../../contexts/I18nContext";

/**
 * Shared "Son güncelleme: X · Yenile" header used above any list page that
 * displays time-sensitive market data. Pulls a uniform UX into one place so
 * Portfolio / Bonds / FX / Inflation / Stocks all behave identically:
 *
 *   • Relative time when the data is fresh ("az önce", "5 dakika önce")
 *   • Absolute clock once it crosses an hour, then date+time for prior days
 *   • Single "🔄 Yenile" button that disables itself while a refetch is
 *     in flight; the icon spins to confirm the action registered
 *
 * Props:
 *   asOf       — ISO string / Date / millis of the freshest data point
 *                (null hides the "Son güncelleme" label, button still shows)
 *   onRefresh  — async or sync callback. Optional; the button disappears
 *                if omitted (e.g. for read-only public pages).
 *   refreshing — boolean disables the button + spins the icon
 *   label      — optional override; defaults to t("common.lastUpdated")
 *
 * Locale-aware via useI18n() — "az önce" vs "just now", "5 dakika önce"
 * vs "5 minutes ago", clock formatted with the right locale.
 */
export function DataFreshnessHeader({ asOf, onRefresh, refreshing = false, label, style }) {
  const { t, lang } = useI18n();
  const text = asOf ? formatRelativeTime(asOf, lang, t) : null;
  if (!text && !onRefresh) return null;

  return (
    <div style={{ ...styles.row, ...(style || {}) }}>
      {text && (
        <span
          style={styles.asOf}
          title={asOf ? new Date(asOf).toLocaleString(lang === "en" ? "en-US" : "tr-TR") : ""}
        >
          {(label ?? t("common.lastUpdated")) + ": "}
          <strong>{text}</strong>
        </span>
      )}
      {onRefresh && (
        <button
          type="button"
          onClick={onRefresh}
          disabled={refreshing}
          style={{
            ...styles.btn,
            opacity: refreshing ? 0.6 : 1,
            cursor: refreshing ? "wait" : "pointer",
          }}
          title={t("common.refreshTooltip")}
          aria-label={t("common.refresh")}
        >
          <span
            style={{
              display: "inline-block",
              transform: refreshing ? "rotate(180deg)" : "none",
              transition: "transform 200ms",
            }}
          >
            ⟳
          </span>
          <span style={{ marginLeft: 6 }}>{t("common.refresh")}</span>
        </button>
      )}
    </div>
  );
}

function formatRelativeTime(input, lang, t) {
  const ts = new Date(input).getTime();
  if (Number.isNaN(ts)) return null;
  const diffMin = Math.round((Date.now() - ts) / 60000);
  if (diffMin < 1) return t("common.justNow");
  if (diffMin < 60) {
    return t("common.minutesAgo", { n: diffMin });
  }
  const sameDay = new Date(ts).toDateString() === new Date().toDateString();
  const locale = lang === "en" ? "en-US" : "tr-TR";
  if (sameDay) {
    return new Date(ts).toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" });
  }
  return new Date(ts).toLocaleString(locale, {
    day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit",
  });
}

const styles = {
  row: {
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
  btn: {
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

export default DataFreshnessHeader;

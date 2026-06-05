import { useEffect, useState, useMemo, useCallback } from "react";
import PropTypes from "prop-types";
import { getInflationHistory } from "../api/inflationApi";
import DataFreshnessHeader from "../components/common/DataFreshnessHeader";
import TermInfo from "../components/common/TermInfo";
import { useI18n } from "../contexts/I18nContext";

export default function Inflation() {
  const { t } = useI18n();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [view, setView] = useState("yearly"); // "yearly" | "monthly"
  const [country, setCountry] = useState("TR"); // "TR" | "US"

  // Pulled out into a callable so the error-state retry button can call
  // exactly the same load path the initial mount does. Don't fold this
  // into the useEffect — we want a single source of truth for "fetch
  // inflation data".
  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    getInflationHistory(country)
      .then(setRows)
      .catch((e) => setError(e.message || t("common.fetchError")))
      .finally(() => setLoading(false));
  }, [t, country]);

  useEffect(() => { load(); }, [load]);

  const stats = useMemo(() => {
    if (!rows.length) return null;
    const withYearly = rows.filter((r) => r.cpiYearlyChange != null);
    const latest = rows[rows.length - 1];
    const latestYearly = [...withYearly].reverse().find((r) => r.cpiYearlyChange != null);
    const yearAgo = withYearly.find((r) => r.cpiYearlyChange != null);

    // 5y cumulative inflation (between rows[0] and rows[-1]'s index)
    let cumulative5y = null;
    if (rows.length >= 60) {
      const start = rows[rows.length - 60];
      const end = rows[rows.length - 1];
      if (start.cpiIndex && end.cpiIndex && start.cpiIndex > 0) {
        cumulative5y = ((end.cpiIndex / start.cpiIndex - 1) * 100).toFixed(2);
      }
    }
    return {
      latestPeriod: latest.periodDate,
      latestYearly: latestYearly?.cpiYearlyChange,
      latestMonthly: latest.cpiMonthlyChange,
      latestIndex: latest.cpiIndex,
      cumulative5y,
    };
  }, [rows]);

  if (loading) {
    return (
      <div style={s.loading}>
        <div style={s.spinner} />
        <div style={{ color: "var(--text-muted)", marginTop: 12 }}>{t("common.loadingDots")}</div>
      </div>
    );
  }

  if (error) {
    return (
      <div style={s.error}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>⚠️</div>
        <div>{error}</div>
        <button type="button" onClick={load} style={s.retryBtn}>
          🔄 {t("common.retry")}
        </button>
      </div>
    );
  }

  if (!rows.length) {
    return (
      <div style={s.error}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>📊</div>
        <div>{t("inflation.empty")}</div>
        <div style={{ fontSize: 12, color: "var(--text-muted)", marginTop: 8 }}>
          {t("inflation.emptyHint")}
        </div>
      </div>
    );
  }

  // Last 24 months in chronological order for the monthly chart + the detail table.
  const last24 = rows.slice(-24);

  // For the yearly chart: collapse the full monthly history into one bar per
  // calendar year. Each bar uses the latest month available in that year's
  // `cpiYearlyChange` (December if the print has landed; otherwise the most
  // recent month, e.g. the current year before the December figure exists).
  // That matches the standard "year-end annual inflation" definition that
  // central banks report.
  const yearlyBars = (() => {
    if (!rows.length) return [];
    const byYear = new Map();
    for (const r of rows) {
      if (r.cpiYearlyChange == null) continue;
      const year = r.periodDate.substring(0, 4);
      const existing = byYear.get(year);
      if (!existing || existing.periodDate < r.periodDate) {
        byYear.set(year, r);
      }
    }
    return [...byYear.values()].sort((a, b) =>
      a.periodDate.localeCompare(b.periodDate)
    );
  })();

  // Latest periodDate doubles as our "as of" — TCMB publishes monthly, so the
  // chip shows e.g. "Son güncelleme: Mayıs 2026" once the May print lands.
  const asOf = stats?.latestPeriod ? new Date(stats.latestPeriod) : null;

  return (
    <div style={s.root}>
      <h1 style={s.pageTitle}>{t("nav.inflation")} <TermInfo termKey="cpi" placement="bottom" /></h1>

      <DataFreshnessHeader asOf={asOf} onRefresh={load} refreshing={loading} />

      {/* Country selector — TCMB (TR) vs FRED (US) */}
      <div style={s.countryToggle}>
        <button
          type="button"
          style={{ ...s.countryBtn, ...(country === "TR" ? s.countryBtnActive : {}) }}
          onClick={() => setCountry("TR")}
        >
          🇹🇷 {t("inflation.countryTR")}
        </button>
        <button
          type="button"
          style={{ ...s.countryBtn, ...(country === "US" ? s.countryBtnActive : {}) }}
          onClick={() => setCountry("US")}
        >
          🇺🇸 {t("inflation.countryUS")}
        </button>
      </div>

      {/* Summary cards */}
      <div style={s.summaryGrid}>
        <SCard
          label={<>{t("inflation.yearlyTitle")} <TermInfo termKey="yearly_change" /></>}
          value={stats?.latestYearly != null ? stats.latestYearly.toFixed(2) + "%" : "—"}
          sub={stats?.latestPeriod ? t("inflation.lastData") + ": " + formatPeriod(stats.latestPeriod) : ""}
          color="var(--red)"
        />
        <SCard
          label={<>{t("inflation.monthlyTitle")} <TermInfo termKey="monthly_change" /></>}
          value={stats?.latestMonthly != null ? stats.latestMonthly.toFixed(2) + "%" : "—"}
          sub={t("inflation.monthlySub")}
        />
        <SCard
          label={<>{t("inflation.cpiTitle")} <TermInfo termKey="cpi" /></>}
          value={stats?.latestIndex != null ? Number(stats.latestIndex).toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "—"}
          sub={t("inflation.cpiSub")}
        />
        <SCard
          label={<>{t("inflation.cumul5y")} <TermInfo termKey="cumulative_inflation" /></>}
          value={stats?.cumulative5y != null ? "+" + stats.cumulative5y + "%" : "—"}
          sub={t("inflation.cumul5ySub")}
          color="var(--red)"
        />
      </div>

      {/* Bar chart */}
      <div style={s.card}>
        <div style={s.cardHeader}>
          <div>
            <div style={s.cardTitle}>{t("inflation.chartTitle")}</div>
            <div style={s.cardSub}>{t(view === "yearly" ? "inflation.chartSubYearly" : "inflation.chartSubMonthly")}</div>
          </div>
          <div style={s.toggle}>
            <button
              style={{ ...s.toggleBtn, ...(view === "yearly" ? s.toggleBtnActive : {}) }}
              onClick={() => setView("yearly")}
            >
              {t("inflation.viewYearly")}
            </button>
            <button
              style={{ ...s.toggleBtn, ...(view === "monthly" ? s.toggleBtnActive : {}) }}
              onClick={() => setView("monthly")}
            >
              {t("inflation.viewMonthly")}
            </button>
          </div>
        </div>
        <BarChart
          data={view === "yearly" ? yearlyBars : last24}
          field={view === "yearly" ? "cpiYearlyChange" : "cpiMonthlyChange"}
          view={view}
          t={t}
        />
      </div>

      {/* Table — last 24 months */}
      <div style={s.card}>
        <div style={s.cardTitle}>{t("inflation.detailsTitle")}</div>
        <div style={s.tableWrap} className="fp-table-scroll">
          <table style={s.table}>
            <thead>
              <tr>
                <th style={s.th}>{t("inflation.colPeriod")}</th>
                <th style={{ ...s.th, textAlign: "right" }}>
                  {t("inflation.colCpi")} <TermInfo termKey="cpi" placement="bottom" />
                </th>
                <th style={{ ...s.th, textAlign: "right" }}>
                  {t("inflation.colMonthly")} <TermInfo termKey="monthly_change" placement="bottom" />
                </th>
                <th style={{ ...s.th, textAlign: "right" }}>
                  {t("inflation.colYearly")} <TermInfo termKey="yearly_change" placement="bottom" />
                </th>
              </tr>
            </thead>
            <tbody>
              {[...last24].reverse().map((r) => (
                <tr key={r.periodDate} style={s.tr}>
                  <td style={s.td}>{formatPeriod(r.periodDate)}</td>
                  <td style={{ ...s.td, textAlign: "right" }}>
                    {r.cpiIndex != null ? Number(r.cpiIndex).toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "—"}
                  </td>
                  <td style={{ ...s.td, textAlign: "right", color: pctColor(r.cpiMonthlyChange), fontWeight: 600 }}>
                    {fmtPct(r.cpiMonthlyChange)}
                  </td>
                  <td style={{ ...s.td, textAlign: "right", color: pctColor(r.cpiYearlyChange), fontWeight: 600 }}>
                    {fmtPct(r.cpiYearlyChange)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function BarChart({ data, field, view, t }) {
  const values = data.map((r) => r[field]).filter((v) => v != null);
  if (values.length === 0) {
    return <div style={{ height: 200, display: "flex", alignItems: "center", justifyContent: "center", color: "var(--text-muted)", fontSize: 13 }}>{t("inflation.notEnough")}</div>;
  }
  const max = Math.max(...values, 0);
  const min = Math.min(...values, 0);
  const range = max - min || 1;

  // Yearly view: emit each year label only once (at the leftmost bar of that
  // year) so the axis reads "2024 ... 2025 ... 2026" instead of repeating
  // the same year on every monthly bar. Monthly view keeps the per-bar
  // "YY-MM" tick like before.
  let prevYear = null;
  const labelFor = (periodDate) => {
    if (view !== "yearly") return periodDate.substring(2, 7);
    const year = periodDate.substring(0, 4);
    if (year === prevYear) return "";
    prevYear = year;
    return year;
  };

  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: 4, height: 220, padding: "8px 0", borderTop: "1px dashed var(--border-soft)", borderBottom: "1px dashed var(--border-soft)" }}>
      {data.map((r) => {
        const v = r[field];
        const label = labelFor(r.periodDate);
        if (v == null) return <div key={r.periodDate} style={{ flex: 1 }} />;
        const heightPct = (Math.abs(v) / range) * 90;
        return (
          <div
            key={r.periodDate}
            title={`${formatPeriod(r.periodDate)}: ${fmtPct(v)}`}
            style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "flex-end", height: "100%" }}
          >
            {view === "yearly" && (
              <div
                style={{
                  fontSize: 11,
                  fontWeight: 600,
                  color: v >= 0 ? "var(--red)" : "var(--green)",
                  marginBottom: 4,
                  whiteSpace: "nowrap",
                }}
              >
                {fmtPct(v)}
              </div>
            )}
            <div
              style={{
                width: "85%",
                height: `${heightPct}%`,
                background: v >= 0 ? "linear-gradient(180deg, var(--red) 0%, var(--red) 100%)" : "linear-gradient(180deg, var(--green) 0%, var(--green) 100%)",
                borderRadius: "3px 3px 0 0",
                minHeight: 2,
              }}
            />
            <div style={{ fontSize: view === "yearly" ? 11 : 9, color: "var(--text-muted)", marginTop: 4, whiteSpace: "nowrap", transform: view === "yearly" ? "none" : "rotate(-45deg)", transformOrigin: "center", fontWeight: view === "yearly" ? 600 : 400 }}>
              {label}
            </div>
          </div>
        );
      })}
    </div>
  );
}

BarChart.propTypes = {
  data: PropTypes.array.isRequired,
  field: PropTypes.string.isRequired,
  view: PropTypes.string.isRequired,
  t: PropTypes.func.isRequired,
};

function SCard({ label, value, sub, color }) {
  return (
    <div style={s.summaryCard}>
      <div style={s.summaryLabel}>{label}</div>
      <div style={{ ...s.summaryValue, color: color ?? "var(--text-primary)" }}>{value}</div>
      {sub && <div style={s.summarySub}>{sub}</div>}
    </div>
  );
}

SCard.propTypes = {
  label: PropTypes.node,
  value: PropTypes.node,
  sub: PropTypes.node,
  color: PropTypes.string,
};

function fmtPct(v) {
  if (v == null) return "—";
  const n = Number(v);
  const sign = n >= 0 ? "+" : "";
  return sign + n.toFixed(2) + "%";
}

function pctColor(v) {
  if (v == null) return "var(--text-muted)";
  return Number(v) >= 0 ? "var(--red, #ef4444)" : "var(--green, #10b981)";
}

function formatPeriod(isoDate) {
  // Intl handles month-name localization for us — no hand-written month
  // tables to translate. Falls back to TR if the active language is anything
  // other than "en" (matches the rest of the app's binary-locale convention).
  let lang = "tr";
  try {
    const v = (localStorage.getItem("i18n-lang") || "").toLowerCase();
    if (v === "en") lang = "en";
  } catch { /* ignore */ }
  const locale = lang === "en" ? "en-US" : "tr-TR";
  const [y, m] = isoDate.split("-");
  const date = new Date(Number(y), Number(m) - 1, 1);
  return new Intl.DateTimeFormat(locale, { month: "long", year: "numeric" }).format(date);
}

const s = {
  root: { display: "flex", flexDirection: "column", gap: 16 },
  pageTitle: { fontSize: 24, fontWeight: 700, margin: 0, color: "var(--text-primary)" },
  loading: { display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: 400 },
  spinner: { width: 40, height: 40, border: "3px solid var(--border)", borderTop: "3px solid var(--accent-solid, #3b82f6)", borderRadius: "50%", animation: "spin 0.8s linear infinite" },
  error: { padding: 60, textAlign: "center", color: "var(--text-primary)" },
  retryBtn: {
    marginTop: 16,
    padding: "8px 16px",
    background: "var(--bg-panel)",
    color: "var(--text-primary)",
    border: "1px solid var(--border)",
    borderRadius: 8,
    fontSize: 13,
    fontWeight: 500,
    cursor: "pointer",
  },
  summaryGrid: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 12 },
  summaryCard: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-card)", padding: "16px 18px" },
  summaryLabel: { fontSize: 12, color: "var(--text-muted)", marginBottom: 8 },
  summaryValue: { fontSize: 24, fontWeight: 700 },
  summarySub: { fontSize: 11, color: "var(--text-muted)", marginTop: 4 },
  card: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-card)", padding: "20px 22px" },
  cardHeader: { display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 },
  cardTitle: { fontSize: 15, fontWeight: 600, color: "var(--text-primary)" },
  cardSub: { fontSize: 12, color: "var(--text-muted)", marginTop: 2 },
  toggle: { display: "flex", background: "var(--bg-panel)", borderRadius: 6, padding: 2, gap: 2 },
  toggleBtn: { padding: "5px 12px", border: "none", background: "transparent", color: "var(--text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer", borderRadius: 4 },
  toggleBtnActive: { background: "var(--accent-solid, #3b82f6)", color: "#fff" },
  countryToggle: { display: "flex", gap: 8 },
  countryBtn: {
    flex: "0 0 auto",
    padding: "8px 16px",
    border: "1px solid var(--border-card)",
    background: "var(--bg-card)",
    color: "var(--text-muted)",
    borderRadius: 8,
    fontSize: 14,
    fontWeight: 600,
    cursor: "pointer",
    transition: "all 0.15s",
  },
  countryBtnActive: {
    background: "var(--accent-solid, #3b82f6)",
    color: "#fff",
    borderColor: "var(--accent-solid, #3b82f6)",
  },
  tableWrap: { overflowX: "auto", marginTop: 12 },
  table: { width: "100%", borderCollapse: "collapse" },
  th: { textAlign: "left", padding: "8px 12px", fontSize: 11, fontWeight: 600, color: "var(--text-muted)", borderBottom: "1px solid var(--border)", textTransform: "uppercase" },
  tr: { borderBottom: "1px solid var(--border-soft)" },
  td: { padding: "9px 12px", fontSize: 13, color: "var(--text-primary)" },
};

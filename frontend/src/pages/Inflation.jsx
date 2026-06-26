import { useEffect, useState, useMemo, useCallback, useRef } from "react";
import { IconAlertTriangle, IconRefresh, IconBarChart } from "../components/common/icons";
import PropTypes from "prop-types";
import { getInflationHistory } from "../api/inflationApi";
import DataFreshnessHeader from "../components/common/DataFreshnessHeader";
import DepositRatesCard from "../components/DepositRatesCard";
import TermInfo from "../components/common/TermInfo";
import { useI18n } from "../contexts/I18nContext";

export default function Inflation() {
  const { t } = useI18n();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [view, setView] = useState("yearly"); // "yearly" | "monthly"
  const [country, setCountry] = useState("TR"); // "TR" | "US"
  const [tab, setTab] = useState("inflation");  // "inflation" | "deposit"
  const [selYear, setSelYear] = useState(null);    // month-lookup: selected year
  const [selMonth, setSelMonth] = useState(null);  // month-lookup: selected month "MM"
  const [tableRange, setTableRange] = useState("24"); // "24" | "all"
  const selectedRowRef = useRef(null);

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

  // Switching country gives a different available range → reset the lookup so
  // the default-effect below re-picks the new latest period.
  useEffect(() => { setSelYear(null); setSelMonth(null); }, [country]);

  // Default the month-lookup to the latest available period once data lands.
  useEffect(() => {
    if (!rows.length) return;
    const [y, m] = rows[rows.length - 1].periodDate.split("-");
    setSelYear((prev) => prev ?? y);
    setSelMonth((prev) => prev ?? m);
  }, [rows]);

  // When viewing the full history, scroll the selected month's row into view.
  useEffect(() => {
    if (tableRange === "all") selectedRowRef.current?.scrollIntoView({ block: "nearest" });
  }, [selYear, selMonth, tableRange]);

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

  // Top tab strip — TR / ABD enflasyonu + Mevduat Faizi. Shared between the
  // deposit view (below) and the main inflation view so the deposit tab is
  // always reachable from the top instead of being buried at page bottom.
  const tabBar = (
    <div style={s.countryToggle}>
      <button
        type="button"
        style={{ ...s.countryBtn, ...(tab === "inflation" && country === "TR" ? s.countryBtnActive : {}) }}
        onClick={() => { setTab("inflation"); setCountry("TR"); }}
      >
        🇹🇷 {t("inflation.countryTR")}
      </button>
      <button
        type="button"
        style={{ ...s.countryBtn, ...(tab === "inflation" && country === "US" ? s.countryBtnActive : {}) }}
        onClick={() => { setTab("inflation"); setCountry("US"); }}
      >
        🇺🇸 {t("inflation.countryUS")}
      </button>
      <button
        type="button"
        style={{ ...s.countryBtn, ...(tab === "deposit" ? s.countryBtnActive : {}) }}
        onClick={() => setTab("deposit")}
      >
        💰 {t("inflation.tabDeposit")}
      </button>
    </div>
  );

  // Deposit-rates view is independent of the inflation data load, so it short
  // -circuits before the inflation loading/error/empty guards below.
  if (tab === "deposit") {
    return (
      <div style={s.root}>
        <h1 style={s.pageTitle}>{t("nav.inflation")} <TermInfo termKey="cpi" placement="bottom" /></h1>
        {tabBar}
        <DepositRatesCard />
      </div>
    );
  }

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
        <div style={{ marginBottom: 12 }}><IconAlertTriangle size={48} /></div>
        <div>{error}</div>
        <button type="button" onClick={load} style={s.retryBtn}>
          <IconRefresh size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />{t("common.retry")}
        </button>
      </div>
    );
  }

  if (!rows.length) {
    return (
      <div style={s.error}>
        <div style={{ marginBottom: 12 }}><IconBarChart size={48} /></div>
        <div>{t("inflation.empty")}</div>
        <div style={{ fontSize: 12, color: "var(--text-muted)", marginTop: 8 }}>
          {t("inflation.emptyHint")}
        </div>
      </div>
    );
  }

  // Last 24 months in chronological order for the monthly chart + the detail table.
  const last24 = rows.slice(-24);

  // Month-lookup + table-range helpers — the full ~10y monthly history is available.
  const byPeriod = new Map(rows.map((r) => [r.periodDate.slice(0, 7), r])); // "YYYY-MM" → row
  const years = [...new Set(rows.map((r) => r.periodDate.slice(0, 4)))].sort().reverse();
  const selKey = selYear && selMonth ? `${selYear}-${selMonth}` : null;     // "YYYY-MM"
  const selectedRow = selKey ? byPeriod.get(selKey) : null;
  const tableRows = tableRange === "all" ? rows : last24;

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

      {/* TR / ABD enflasyonu + Mevduat Faizi sekmeleri */}
      {tabBar}

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

      {/* Belirli bir ayın enflasyonu — yıl + ay seçici */}
      <div style={s.card}>
        <div style={s.cardTitle}>{t("inflation.lookupTitle")}</div>
        <div style={s.cardSub}>{t("inflation.lookupHint")}</div>
        <div style={s.lookupRow}>
          <select
            value={selYear ?? ""}
            onChange={(e) => {
              const y = e.target.value;
              setSelYear(y);
              // Keep the month valid for the new year — snap to its latest
              // available month if the current one has no data that year.
              if (!byPeriod.has(`${y}-${selMonth}`)) {
                const avail = MONTHS.filter((mm) => byPeriod.has(`${y}-${mm}`));
                if (avail.length) setSelMonth(avail[avail.length - 1]);
              }
            }}
            style={s.select}
            aria-label={t("inflation.colPeriod")}
          >
            {years.map((y) => <option key={y} value={y}>{y}</option>)}
          </select>
          <select value={selMonth ?? ""} onChange={(e) => setSelMonth(e.target.value)} style={s.select} aria-label={t("inflation.monthLabel")}>
            {MONTHS.map((mm) => (
              <option key={mm} value={mm} disabled={selYear ? !byPeriod.has(`${selYear}-${mm}`) : false}>
                {monthName(mm)}
              </option>
            ))}
          </select>
        </div>
        {stats?.latestPeriod && (
          <div style={s.lookupLatest}>
            {t("inflation.latestPublished")}: <b>{formatPeriod(stats.latestPeriod)}</b>
          </div>
        )}
        {selectedRow ? (
          <div style={s.lookupResult}>
            <div style={s.lookupPeriod}>{formatPeriod(selectedRow.periodDate)}</div>
            <div style={s.lookupMetrics}>
              <LookupMetric label={t("inflation.colMonthly")} value={fmtPct(selectedRow.cpiMonthlyChange)} color={pctColor(selectedRow.cpiMonthlyChange)} />
              <LookupMetric label={t("inflation.colYearly")} value={fmtPct(selectedRow.cpiYearlyChange)} color={pctColor(selectedRow.cpiYearlyChange)} />
              <LookupMetric label={t("inflation.colCpi")} value={selectedRow.cpiIndex != null ? Number(selectedRow.cpiIndex).toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "—"} />
            </div>
          </div>
        ) : (
          <div style={s.lookupEmpty}>{t("inflation.noPeriodData")}</div>
        )}
      </div>

      {/* Detay tablosu — aralık seçilebilir (son 24 ay / son 10 yıl), seçili ay vurgulanır */}
      <div style={s.card}>
        <div style={s.cardHeader}>
          <div style={s.cardTitle}>{t("inflation.detailsTitle")}</div>
          <div style={s.toggle}>
            <button type="button" style={{ ...s.toggleBtn, ...(tableRange === "24" ? s.toggleBtnActive : {}) }} onClick={() => setTableRange("24")}>
              {t("inflation.rangeLast24")}
            </button>
            <button type="button" style={{ ...s.toggleBtn, ...(tableRange === "all" ? s.toggleBtnActive : {}) }} onClick={() => setTableRange("all")}>
              {t("inflation.rangeAll")}
            </button>
          </div>
        </div>
        <div style={{ ...s.tableWrap, ...(tableRange === "all" ? s.tableScroll : {}) }} className="fp-table-scroll">
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
              {[...tableRows].reverse().map((r) => {
                const isSel = r.periodDate.slice(0, 7) === selKey;
                return (
                  <tr key={r.periodDate} ref={isSel ? selectedRowRef : null} style={{ ...s.tr, ...(isSel ? s.trSelected : {}) }}>
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
                );
              })}
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

function LookupMetric({ label, value, color }) {
  return (
    <div style={s.lookupMetric}>
      <div style={s.lookupMetricLabel}>{label}</div>
      <div style={{ ...s.lookupMetricValue, color: color ?? "var(--text-primary)" }}>{value}</div>
    </div>
  );
}

LookupMetric.propTypes = { label: PropTypes.node, value: PropTypes.node, color: PropTypes.string };

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

const MONTHS = ["01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12"];

function monthName(mm) {
  let lang = "tr";
  try { if ((localStorage.getItem("i18n-lang") || "").toLowerCase() === "en") lang = "en"; } catch { /* ignore */ }
  const locale = lang === "en" ? "en-US" : "tr-TR";
  return new Intl.DateTimeFormat(locale, { month: "long" }).format(new Date(2000, Number(mm) - 1, 1));
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
  tableScroll: { maxHeight: 480, overflowY: "auto" },
  trSelected: { background: "var(--accent-soft, rgba(59,130,246,0.16))", outline: "1px solid var(--accent-solid, #3b82f6)" },
  lookupRow: { display: "flex", gap: 10, marginTop: 12, flexWrap: "wrap" },
  select: { padding: "9px 12px", borderRadius: 8, border: "1px solid var(--border-card)", background: "var(--input-bg, var(--bg-panel))", color: "var(--text-primary)", fontSize: 14, fontWeight: 600, cursor: "pointer", minWidth: 130 },
  lookupResult: { marginTop: 14, padding: "14px 16px", borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-panel)" },
  lookupPeriod: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)", marginBottom: 10 },
  lookupMetrics: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(120px, 1fr))", gap: 12 },
  lookupMetric: { textAlign: "left" },
  lookupMetricLabel: { fontSize: 11, color: "var(--text-muted)", marginBottom: 4 },
  lookupMetricValue: { fontSize: 22, fontWeight: 700 },
  lookupEmpty: { marginTop: 14, padding: "14px 16px", borderRadius: 10, border: "1px dashed var(--border-card)", color: "var(--text-muted)", fontSize: 13, textAlign: "center" },
  lookupLatest: { marginTop: 8, fontSize: 12, color: "var(--text-muted)" },
};

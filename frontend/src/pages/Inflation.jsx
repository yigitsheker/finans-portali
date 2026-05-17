import { useEffect, useState, useMemo } from "react";
import { getInflationHistory } from "../api/inflationApi";

export default function Inflation() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [view, setView] = useState("yearly"); // "yearly" | "monthly"

  useEffect(() => {
    setLoading(true);
    getInflationHistory()
      .then(setRows)
      .catch((e) => setError(e.message || "Veri çekilemedi"))
      .finally(() => setLoading(false));
  }, []);

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
        <div style={{ color: "var(--text-muted)", marginTop: 12 }}>Yükleniyor…</div>
      </div>
    );
  }

  if (error) {
    return (
      <div style={s.error}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>⚠️</div>
        <div>{error}</div>
      </div>
    );
  }

  if (!rows.length) {
    return (
      <div style={s.error}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>📊</div>
        <div>Henüz enflasyon verisi yok.</div>
        <div style={{ fontSize: 12, color: "var(--text-muted)", marginTop: 8 }}>
          TCMB EVDS3 erişimi sağlandığında otomatik dolacak.
        </div>
      </div>
    );
  }

  // Last 24 months in chronological order for the chart
  const last24 = rows.slice(-24);

  return (
    <div style={s.root}>
      {/* Summary cards */}
      <div style={s.summaryGrid}>
        <SCard
          label="Son Yıllık Enflasyon (TÜFE)"
          value={stats?.latestYearly != null ? stats.latestYearly.toFixed(2) + "%" : "—"}
          sub={stats?.latestPeriod ? `Son veri: ${formatPeriod(stats.latestPeriod)}` : ""}
          color="var(--red)"
        />
        <SCard
          label="Son Aylık Enflasyon"
          value={stats?.latestMonthly != null ? stats.latestMonthly.toFixed(2) + "%" : "—"}
          sub="Önceki aya göre"
        />
        <SCard
          label="TÜFE Endeksi"
          value={stats?.latestIndex != null ? Number(stats.latestIndex).toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "—"}
          sub="2003 = 100"
        />
        <SCard
          label="5 Yıllık Birikimli"
          value={stats?.cumulative5y != null ? "+" + stats.cumulative5y + "%" : "—"}
          sub="Son 60 ayın bileşik etkisi"
          color="var(--red)"
        />
      </div>

      {/* Bar chart */}
      <div style={s.card}>
        <div style={s.cardHeader}>
          <div>
            <div style={s.cardTitle}>Tarihsel Enflasyon</div>
            <div style={s.cardSub}>Son 24 ay {view === "yearly" ? "yıllık" : "aylık"} değişim</div>
          </div>
          <div style={s.toggle}>
            <button
              style={{ ...s.toggleBtn, ...(view === "yearly" ? s.toggleBtnActive : {}) }}
              onClick={() => setView("yearly")}
            >
              Yıllık %
            </button>
            <button
              style={{ ...s.toggleBtn, ...(view === "monthly" ? s.toggleBtnActive : {}) }}
              onClick={() => setView("monthly")}
            >
              Aylık %
            </button>
          </div>
        </div>
        <BarChart
          data={last24}
          field={view === "yearly" ? "cpiYearlyChange" : "cpiMonthlyChange"}
        />
      </div>

      {/* Table — last 24 months */}
      <div style={s.card}>
        <div style={s.cardTitle}>Son 24 Ay Detayı</div>
        <div style={s.tableWrap}>
          <table style={s.table}>
            <thead>
              <tr>
                <th style={s.th}>Dönem</th>
                <th style={{ ...s.th, textAlign: "right" }}>TÜFE Endeksi</th>
                <th style={{ ...s.th, textAlign: "right" }}>Aylık %</th>
                <th style={{ ...s.th, textAlign: "right" }}>Yıllık %</th>
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

function BarChart({ data, field }) {
  const values = data.map((r) => r[field]).filter((v) => v != null);
  if (values.length === 0) {
    return <div style={{ height: 200, display: "flex", alignItems: "center", justifyContent: "center", color: "var(--text-muted)", fontSize: 13 }}>Bu görünüm için yeterli veri yok</div>;
  }
  const max = Math.max(...values, 0);
  const min = Math.min(...values, 0);
  const range = max - min || 1;

  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: 4, height: 220, padding: "8px 0", borderTop: "1px dashed var(--border-soft)", borderBottom: "1px dashed var(--border-soft)" }}>
      {data.map((r) => {
        const v = r[field];
        if (v == null) return <div key={r.periodDate} style={{ flex: 1 }} />;
        const heightPct = (Math.abs(v) / range) * 90;
        return (
          <div
            key={r.periodDate}
            title={`${formatPeriod(r.periodDate)}: ${fmtPct(v)}`}
            style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "flex-end", height: "100%" }}
          >
            <div
              style={{
                width: "85%",
                height: `${heightPct}%`,
                background: v >= 0 ? "linear-gradient(180deg, #ef4444 0%, #dc2626 100%)" : "linear-gradient(180deg, #10b981 0%, #059669 100%)",
                borderRadius: "3px 3px 0 0",
                minHeight: 2,
              }}
            />
            <div style={{ fontSize: 9, color: "var(--text-muted)", marginTop: 4, whiteSpace: "nowrap", transform: "rotate(-45deg)", transformOrigin: "center" }}>
              {r.periodDate.substring(2, 7)}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function SCard({ label, value, sub, color }) {
  return (
    <div style={s.summaryCard}>
      <div style={s.summaryLabel}>{label}</div>
      <div style={{ ...s.summaryValue, color: color ?? "var(--text-primary)" }}>{value}</div>
      {sub && <div style={s.summarySub}>{sub}</div>}
    </div>
  );
}

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
  // "2024-01-01" → "Ocak 2024"
  const months = ["Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"];
  const [y, m] = isoDate.split("-");
  return `${months[Number(m) - 1]} ${y}`;
}

const s = {
  root: { display: "flex", flexDirection: "column", gap: 16 },
  loading: { display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: 400 },
  spinner: { width: 40, height: 40, border: "3px solid var(--border)", borderTop: "3px solid var(--accent-solid, #3b82f6)", borderRadius: "50%", animation: "spin 0.8s linear infinite" },
  error: { padding: 60, textAlign: "center", color: "var(--text-primary)" },
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
  tableWrap: { overflowX: "auto", marginTop: 12 },
  table: { width: "100%", borderCollapse: "collapse" },
  th: { textAlign: "left", padding: "8px 12px", fontSize: 11, fontWeight: 600, color: "var(--text-muted)", borderBottom: "1px solid var(--border)", textTransform: "uppercase" },
  tr: { borderBottom: "1px solid var(--border-soft)" },
  td: { padding: "9px 12px", fontSize: 13, color: "var(--text-primary)" },
};

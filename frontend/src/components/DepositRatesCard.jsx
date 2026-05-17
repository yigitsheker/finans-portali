import { useEffect, useState } from "react";
import { getLatestDepositRates } from "../api/depositRateApi";

const MATURITY_COLS = [
  { key: "rate1m",       label: "1 Ay" },
  { key: "rate3m",       label: "1-3 Ay" },
  { key: "rate6m",       label: "3-6 Ay" },
  { key: "rate12m",      label: "6-12 Ay" },
  { key: "rateOver12m",  label: "12 Ay+" },
  { key: "rateAvg",      label: "Ortalama" },
];

const CURRENCIES = [
  { code: "TRY", label: "₺ Türk Lirası", flag: "🇹🇷" },
  { code: "USD", label: "$ ABD Doları",  flag: "🇺🇸" },
  { code: "EUR", label: "€ Euro",        flag: "🇪🇺" },
];

export default function DepositRatesCard() {
  const [data, setData] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    getLatestDepositRates()
      .then((d) => setData(d || {}))
      .catch((e) => setError(e?.message || "Yüklenemedi"))
      .finally(() => setLoading(false));
  }, []);

  const fmtRate = (v) => {
    if (v == null) return "—";
    return Number(v).toFixed(2) + "%";
  };

  const periodLabel = (() => {
    const first = Object.values(data)[0];
    if (!first?.periodDate) return null;
    const months = ["Ocak","Şubat","Mart","Nisan","Mayıs","Haziran","Temmuz","Ağustos","Eylül","Ekim","Kasım","Aralık"];
    const [y, m] = String(first.periodDate).split("-");
    const mi = Number(m) - 1;
    return Number.isInteger(mi) && mi >= 0 && mi <= 11 ? `${months[mi]} ${y}` : first.periodDate;
  })();

  return (
    <div style={s.card}>
      <div style={s.header}>
        <div>
          <div style={s.title}>🏦 Mevduat Faiz Oranları</div>
          <div style={s.subtitle}>
            Bankalar ortalama mevduat faizi (TCMB EVDS3)
            {periodLabel && <span style={s.periodBadge}>{periodLabel}</span>}
          </div>
        </div>
      </div>

      {loading ? (
        <div style={s.loadingRow}>Yükleniyor…</div>
      ) : error ? (
        <div style={s.errorRow}>{error}</div>
      ) : Object.keys(data).length === 0 ? (
        <div style={s.errorRow}>Henüz veri yok — TCMB EVDS3 cookie'leri yenilenmeli.</div>
      ) : (
        <div style={s.tableWrap}>
          <table style={s.table}>
            <thead>
              <tr>
                <th style={s.th}>Para Birimi</th>
                {MATURITY_COLS.map((col) => (
                  <th key={col.key} style={{ ...s.th, textAlign: "right" }}>{col.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {CURRENCIES.map((ccy) => {
                const row = data[ccy.code];
                if (!row) return null;
                return (
                  <tr key={ccy.code} style={s.tr}>
                    <td style={s.td}>
                      <span style={{ marginRight: 8 }}>{ccy.flag}</span>
                      <span style={{ fontWeight: 600 }}>{ccy.label}</span>
                    </td>
                    {MATURITY_COLS.map((col) => (
                      <td key={col.key} style={{
                        ...s.td,
                        textAlign: "right",
                        fontWeight: col.key === "rateAvg" ? 700 : 500,
                        color: col.key === "rateAvg" ? "var(--accent-solid, #3b82f6)" : "var(--text-primary)",
                      }}>
                        {fmtRate(row[col.key])}
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <div style={s.footnote}>
        Kaynak: TCMB EVDS3 — TP.{`{TRY|USD|EUR}`}.MT{`{01-06}`} aylık ağırlıklı ortalama faiz oranları.
      </div>
    </div>
  );
}

const s = {
  card: {
    borderRadius: 10,
    border: "1px solid var(--border-card)",
    background: "var(--bg-card)",
    padding: "18px 20px",
    marginBottom: 16,
  },
  header: { display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 14 },
  title: { fontSize: 16, fontWeight: 700, color: "var(--text-primary)", marginBottom: 4 },
  subtitle: { fontSize: 12, color: "var(--text-muted)", display: "flex", alignItems: "center", gap: 10 },
  periodBadge: {
    padding: "2px 8px",
    borderRadius: 10,
    background: "rgba(59, 130, 246, 0.12)",
    color: "var(--accent-solid, #3b82f6)",
    fontSize: 11,
    fontWeight: 600,
  },
  loadingRow: { padding: 24, textAlign: "center", color: "var(--text-muted)", fontSize: 13 },
  errorRow: { padding: 24, textAlign: "center", color: "var(--text-muted)", fontSize: 13 },
  tableWrap: { overflowX: "auto" },
  table: { width: "100%", borderCollapse: "collapse" },
  th: {
    textAlign: "left",
    padding: "10px 12px",
    fontSize: 11,
    fontWeight: 600,
    color: "var(--text-muted)",
    borderBottom: "1px solid var(--border)",
    textTransform: "uppercase",
    letterSpacing: "0.3px",
    whiteSpace: "nowrap",
  },
  tr: { borderBottom: "1px solid var(--border-soft)" },
  td: { padding: "11px 12px", fontSize: 13, color: "var(--text-primary)", whiteSpace: "nowrap" },
  footnote: { fontSize: 10, color: "var(--text-muted)", marginTop: 10, fontStyle: "italic" },
};

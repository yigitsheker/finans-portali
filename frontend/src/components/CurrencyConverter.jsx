import { useEffect, useMemo, useState } from "react";
import { IconExchange } from "./common/icons";
import { useI18n } from "../contexts/I18nContext";

/**
 * Bidirectional FX converter built on top of the TCMB exchange-rate table.
 *
 * Every TCMB rate is quoted in TRY (buying / selling). To go between two
 * non-TRY currencies we cross-rate through TRY:
 *     amount in TARGET = amount_in_TRY / TARGET.sellingRate
 *                     = (amount * SOURCE.buyingRate) / TARGET.sellingRate
 *
 * Buying rate is used when the user is *selling* the source currency;
 * selling rate when the user is *buying* the target currency. That matches
 * what a customer would actually pay at a desk.
 */
export default function CurrencyConverter({ rates }) {
  const { t, lang } = useI18n();
  const TRY_ROW = useMemo(() => ({
    currencyCode: "TRY",
    currencyName: t("fx.turkishLira"),
    buyingRate: 1,
    sellingRate: 1,
  }), [t]);

  // Build a TRY-inclusive option list, deduped by code.
  const options = useMemo(() => {
    const seen = new Set(["TRY"]);
    const out = [TRY_ROW];
    for (const r of rates || []) {
      if (!r?.currencyCode || seen.has(r.currencyCode)) continue;
      seen.add(r.currencyCode);
      out.push(r);
    }
    return out;
  }, [rates, TRY_ROW]);

  const [from, setFrom] = useState("USD");
  const [to, setTo] = useState("TRY");
  const [amount, setAmount] = useState("100");

  // Ensure defaults exist in the data; fall back to first two if not.
  useEffect(() => {
    if (options.length === 0) return;
    const codes = options.map((o) => o.currencyCode);
    if (!codes.includes(from)) setFrom(codes.find((c) => c !== "TRY") || codes[0]);
    if (!codes.includes(to))   setTo(codes.includes("TRY") ? "TRY" : codes[codes.length - 1]);
  }, [options, from, to]);

  const fromRate = useMemo(() => options.find((o) => o.currencyCode === from), [options, from]);
  const toRate   = useMemo(() => options.find((o) => o.currencyCode === to),   [options, to]);

  // Cross-rate calculation (see jsdoc above)
  const { converted, unitRate, inverseRate } = useMemo(() => {
    if (!fromRate || !toRate) return { converted: null, unitRate: null, inverseRate: null };
    const n = Number(amount);
    if (!Number.isFinite(n)) return { converted: null, unitRate: null, inverseRate: null };

    const amountInTry = n * Number(fromRate.buyingRate || 0);
    const sell = Number(toRate.sellingRate || 0);
    const result = sell > 0 ? amountInTry / sell : 0;

    // Unit rate: 1 FROM = ? TO
    const oneInTry = 1 * Number(fromRate.buyingRate || 0);
    const unit = sell > 0 ? oneInTry / sell : 0;
    // Inverse: 1 TO = ? FROM
    const inverse = unit > 0 ? 1 / unit : 0;

    return { converted: result, unitRate: unit, inverseRate: inverse };
  }, [fromRate, toRate, amount]);

  function swap() {
    setFrom(to);
    setTo(from);
  }

  const fmt = (v, opts = {}) => {
    if (v == null || !Number.isFinite(v)) return "—";
    return new Intl.NumberFormat(lang === "en" ? "en-US" : "tr-TR", {
      minimumFractionDigits: opts.minDigits ?? 2,
      maximumFractionDigits: opts.maxDigits ?? 4,
    }).format(v);
  };

  return (
    <div style={s.card}>
      <div style={s.header}>
        <div>
          <div style={s.title}><IconExchange size={16} style={{ verticalAlign: "-3px", marginRight: 6 }} />{t("fx.title")}</div>
          <div style={s.sub}>{t("fx.subtitle")}</div>
        </div>
      </div>

      <div style={s.row} className="fp-fx-conv-row">
        {/* FROM */}
        <div style={s.field}>
          <div style={s.label}>{t("fx.amountToConvert")}</div>
          <div style={s.combo}>
            <input
              type="number"
              value={amount}
              min={0}
              step="any"
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0"
              style={s.amountInput}
            />
            <select value={from} onChange={(e) => setFrom(e.target.value)} style={s.select}>
              {options.map((o) => (
                <option key={o.currencyCode} value={o.currencyCode}>
                  {o.currencyCode} — {o.currencyName}
                </option>
              ))}
            </select>
          </div>
        </div>

        <button type="button" onClick={swap} style={s.swapBtn} title={t("fx.swapTitle")} aria-label={t("fx.swapAria")}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M7 16V4M7 4 3 8M7 4l4 4M17 8v12M17 20l4-4M17 20l-4-4"/>
          </svg>
        </button>

        {/* TO */}
        <div style={s.field}>
          <div style={s.label}>{t("fx.result")}</div>
          <div style={s.combo}>
            <div style={s.resultBox} title={converted != null ? fmt(converted, { maxDigits: 6 }) : ""}>
              {converted != null ? fmt(converted) : "—"}
            </div>
            <select value={to} onChange={(e) => setTo(e.target.value)} style={s.select}>
              {options.map((o) => (
                <option key={o.currencyCode} value={o.currencyCode}>
                  {o.currencyCode} — {o.currencyName}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Rate hints */}
      {unitRate != null && unitRate > 0 && (
        <div style={s.hintRow}>
          <div style={s.hintBlock}>
            <span style={s.hintNum}>1 {from}</span>
            <span style={s.hintEq}>=</span>
            <span style={s.hintAmount}>{fmt(unitRate, { minDigits: 4, maxDigits: 6 })} {to}</span>
          </div>
          <div style={s.hintBlock}>
            <span style={s.hintNum}>1 {to}</span>
            <span style={s.hintEq}>=</span>
            <span style={s.hintAmount}>{fmt(inverseRate, { minDigits: 4, maxDigits: 6 })} {from}</span>
          </div>
        </div>
      )}

      <div style={s.footnote}>
        {t("fx.sourceNote")}
      </div>
    </div>
  );
}

const s = {
  card: {
    borderRadius: "var(--radius-md, 10px)",
    border: "1px solid var(--border-card)",
    background: "var(--bg-card)",
    padding: "20px 22px",
    marginBottom: 16,
  },
  header: { marginBottom: 16 },
  title: { fontSize: "var(--font-lg, 16px)", fontWeight: 700, color: "var(--text-primary)" },
  sub:   { fontSize: "var(--font-md, 12px)", color: "var(--text-muted)", marginTop: 3 },

  row: {
    display: "grid",
    gridTemplateColumns: "1fr auto 1fr",
    gap: 12,
    alignItems: "flex-end",
  },

  field: { display: "flex", flexDirection: "column", gap: 6, minWidth: 0 },
  label: { fontSize: 11, color: "var(--text-muted)", fontWeight: 600, letterSpacing: "0.04em", textTransform: "uppercase" },

  combo: {
    display: "grid",
    gridTemplateColumns: "1fr 140px",
    gap: 0,
    border: "1px solid var(--input-border)",
    borderRadius: "var(--radius-md, 10px)",
    background: "var(--input-bg)",
    overflow: "hidden",
  },

  amountInput: {
    border: "none",
    background: "transparent",
    color: "var(--text-primary)",
    fontSize: 22,
    fontWeight: 600,
    padding: "12px 14px",
    outline: "none",
    minWidth: 0,
    width: "100%",
    fontVariantNumeric: "tabular-nums",
  },

  resultBox: {
    padding: "12px 14px",
    fontSize: 22,
    fontWeight: 700,
    color: "var(--accent-solid)",
    minWidth: 0,
    whiteSpace: "nowrap",
    overflow: "hidden",
    textOverflow: "ellipsis",
    fontVariantNumeric: "tabular-nums",
  },

  select: {
    border: "none",
    borderLeft: "1px solid var(--input-border)",
    background: "var(--bg-panel, var(--input-bg))",
    color: "var(--text-primary)",
    fontSize: 13,
    fontWeight: 600,
    padding: "0 10px",
    cursor: "pointer",
    outline: "none",
  },

  swapBtn: {
    alignSelf: "center",
    marginBottom: 0,
    width: 42,
    height: 42,
    borderRadius: "var(--radius-pill, 999px)",
    border: "1px solid var(--accent-border)",
    background: "var(--accent)",
    color: "var(--accent-solid)",
    display: "grid",
    placeItems: "center",
    cursor: "pointer",
    transition: "transform 0.15s ease",
  },

  hintRow: {
    display: "flex",
    gap: 14,
    marginTop: 14,
    flexWrap: "wrap",
  },
  hintBlock: {
    display: "flex",
    alignItems: "center",
    gap: 6,
    padding: "8px 12px",
    background: "var(--bg-panel)",
    border: "1px solid var(--border-soft)",
    borderRadius: 8,
    fontSize: 12,
  },
  hintNum:    { color: "var(--text-muted)", fontWeight: 600 },
  hintEq:     { color: "var(--text-muted)" },
  hintAmount: { color: "var(--text-primary)", fontWeight: 700, fontVariantNumeric: "tabular-nums" },

  footnote: {
    fontSize: 11,
    color: "var(--text-muted)",
    marginTop: 12,
    fontStyle: "italic",
    lineHeight: 1.5,
  },
};

import { createContext, useContext, useEffect, useState, useCallback } from "react";

/**
 * Display mode for prices across the app.
 *   "original" → show each instrument in its native currency (TRY for BIST, USD for AAPL…)
 *   "TRY"      → everything coerced to TRY, USD values multiplied by spot USDTRY
 *   "USD"      → everything coerced to USD, TRY values divided by spot USDTRY
 *
 * Spot rate is fetched once on mount and refreshed every minute from
 * /api/v1/market/summary. If the fetch fails we fall back to a sane default so
 * formatting never produces NaN.
 */

const STORAGE_KEY = "currency-display-mode";
const DEFAULT_MODE = "original";
const VALID_MODES = ["original", "TRY", "USD"];

const Ctx = createContext({
  mode: DEFAULT_MODE,
  setMode: () => {},
  usdRate: 1,
});

// Native currency by InstrumentType. Anything not in the TRY set is assumed USD.
const TRY_NATIVE_TYPES = new Set(["BIST", "INDEX", "VIOP", "BOND", "FUND"]);
export function nativeCurrencyOf(instrumentType, symbol, explicitCurrency) {
  // Highest priority: caller knows the currency (e.g. PortfolioPosition.currency).
  if (explicitCurrency === "USD" || explicitCurrency === "TRY") return explicitCurrency;
  if (TRY_NATIVE_TYPES.has(instrumentType)) return "TRY";
  // FX rows are quoted vs TRY (USDTRY=44.75) — treat as TRY-side so they
  // don't get themselves converted again.
  if (instrumentType === "FX") return "TRY";
  // Unknown / generic — guess by symbol shape: BIST tickers are 3-5 caps
  if (!instrumentType && symbol && /^[A-Z]{3,5}$/.test(symbol) && !symbol.endsWith("USD")) {
    return "TRY";
  }
  return "USD";
}

export function CurrencyDisplayProvider({ children }) {
  const [mode, setModeState] = useState(() => {
    try {
      const s = localStorage.getItem(STORAGE_KEY);
      return VALID_MODES.includes(s) ? s : DEFAULT_MODE;
    } catch { return DEFAULT_MODE; }
  });

  const [usdRate, setUsdRate] = useState(35.0);

  useEffect(() => {
    let cancelled = false;
    const fetchRate = async () => {
      try {
        // Lightweight FX-only endpoint (a few rows) instead of /summary (~250
        // instruments). We only need USDTRY for client-side currency conversion,
        // so polling the full summary every 60s was wasteful bandwidth.
        const r = await fetch("/api/v1/market/spot-rates");
        if (!r.ok) return;
        const data = await r.json();
        const usdtry = data.find((d) => d.symbol === "USDTRY");
        if (usdtry?.last && !cancelled) setUsdRate(Number(usdtry.last));
      } catch { /* keep previous */ }
    };
    fetchRate();
    const id = setInterval(fetchRate, 60_000);
    return () => { cancelled = true; clearInterval(id); };
  }, []);

  const setMode = useCallback((m) => {
    if (!VALID_MODES.includes(m)) return;
    setModeState(m);
    try { localStorage.setItem(STORAGE_KEY, m); } catch { /* ignore */ }
  }, []);

  return <Ctx.Provider value={{ mode, setMode, usdRate }}>{children}</Ctx.Provider>;
}

export function useCurrencyDisplay() {
  return useContext(Ctx);
}

/**
 * Convert + format a single price value respecting the active display mode.
 * Returns helpers as well as the raw converted number so callers can do their
 * own coloring/sizing without re-running the math.
 */
export function usePriceDisplay() {
  const { mode, usdRate } = useCurrencyDisplay();

  const convert = useCallback((value, instrumentType, symbol, explicitCurrency) => {
    if (value == null || !Number.isFinite(Number(value))) {
      return { value: null, currency: "TRY", symbol: "₺" };
    }
    const n = Number(value);
    const native = nativeCurrencyOf(instrumentType, symbol, explicitCurrency);

    let outCurrency, outValue;
    if (mode === "original") {
      outCurrency = native;
      outValue = n;
    } else if (mode === "TRY") {
      outCurrency = "TRY";
      outValue = native === "TRY" ? n : n * usdRate;
    } else {
      // USD
      outCurrency = "USD";
      outValue = native === "USD" ? n : (usdRate > 0 ? n / usdRate : 0);
    }
    return {
      value: outValue,
      currency: outCurrency,
      symbol: outCurrency === "TRY" ? "₺" : "$",
    };
  }, [mode, usdRate]);

  /**
   * Format a number for display.
   * @param value
   * @param instrumentType  optional InstrumentType string ("BIST", "STOCK", ...)
   * @param opts.symbol     instrument symbol (helps inference when type is unknown)
   * @param opts.currency   explicit native currency ("USD"|"TRY") — overrides inference
   * @param opts.minDigits  min fraction digits
   * @param opts.maxDigits  max fraction digits
   */
  const format = useCallback((value, instrumentType, opts = {}) => {
    const c = convert(value, instrumentType, opts.symbol, opts.currency);
    if (c.value == null) return "—";
    const formatted = new Intl.NumberFormat("tr-TR", {
      minimumFractionDigits: opts.minDigits ?? 2,
      maximumFractionDigits: opts.maxDigits ?? (Math.abs(c.value) >= 1 ? 2 : 6),
    }).format(c.value);
    return `${c.symbol}${formatted}`;
  }, [convert]);

  return { mode, usdRate, convert, format };
}

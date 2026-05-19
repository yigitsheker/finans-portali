import { createContext, useContext, useCallback, useMemo, useState } from "react";
import { dict } from "./i18nDict";

/**
 * Lightweight i18n context — no react-i18next dependency.
 *
 *   const { t, lang, setLang } = useI18n();
 *   t("nav.stocks")                        → "Hisseler" | "Stocks"
 *   t("admin.confirmBan", { user: "ada" }) → "Are you sure you want to ban ada?"
 *
 * Keys are dot-paths. Missing keys fall back to the Turkish entry, and then
 * to the literal key string — so a typo never blanks out the UI.
 *
 * Interpolation: `{name}` placeholders are substituted from the second
 * argument's matching keys. Anything not provided is left as-is so the
 * unbound placeholder is visible (helps spot mismatches in dev).
 */

const STORAGE_KEY = "i18n-lang";
const VALID = ["tr", "en"];
const DEFAULT = "tr";

function lookup(bag, path) {
  const parts = path.split(".");
  let node = bag;
  for (const p of parts) {
    if (node == null || typeof node !== "object") return undefined;
    node = node[p];
  }
  return node;
}

function interpolate(str, vars) {
  if (!vars || typeof str !== "string") return str;
  return str.replace(/\{(\w+)\}/g, (m, name) =>
    Object.prototype.hasOwnProperty.call(vars, name) ? String(vars[name]) : m
  );
}

const Ctx = createContext({
  lang: DEFAULT,
  setLang: () => {},
  t: (k) => k,
});

export function I18nProvider({ children }) {
  const [lang, setLangState] = useState(() => {
    try {
      const s = localStorage.getItem(STORAGE_KEY);
      return VALID.includes(s) ? s : DEFAULT;
    } catch {
      return DEFAULT;
    }
  });

  const setLang = useCallback((next) => {
    if (!VALID.includes(next)) return;
    setLangState(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      /* ignore quota / private mode */
    }
    try {
      document.documentElement.setAttribute("lang", next);
    } catch {
      /* SSR / non-DOM env */
    }
  }, []);

  const t = useCallback(
    (key, varsOrFallback, maybeFallback) => {
      // Allow both t(key, vars) and t(key, fallback) — overload by type.
      let vars = null;
      let fallback;
      if (typeof varsOrFallback === "object" && varsOrFallback !== null) {
        vars = varsOrFallback;
        fallback = maybeFallback;
      } else {
        fallback = varsOrFallback;
      }
      const hit = lookup(dict[lang], key);
      if (hit !== undefined) return interpolate(hit, vars);
      const trHit = lookup(dict.tr, key);
      if (trHit !== undefined) return interpolate(trHit, vars);
      return interpolate(fallback ?? key, vars);
    },
    [lang]
  );

  const value = useMemo(() => ({ lang, setLang, t }), [lang, setLang, t]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useI18n() {
  return useContext(Ctx);
}

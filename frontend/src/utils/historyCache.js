/**
 * localStorage-backed sparkline cache.
 *
 * Why localStorage? Two wins:
 *   1. Page reloads paint sparklines instantly — no waiting on the network.
 *   2. Offline / backend down → the UI still shows the last-known curve
 *      instead of a flat placeholder. Stale, yes, but readable.
 *
 * Entries expire after TTL_MS. Each entry stores the period it was fetched
 * for, so switching from "1M" to "1Y" doesn't pick up the wrong granularity.
 */

const KEY_PREFIX = "mkt-hist:";
const TTL_MS = 30 * 60 * 1000;       // 30 minutes
const STALE_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days — usable as offline fallback
const MAX_ENTRIES = 200;             // bound storage; LRU eviction below

// Strict allow-list for both fragments: alphanumerics, dot, dash,
// underscore. Tickers like "BTC-USD", "THYAO.IS" pass; period codes like
// "1D" / "1Y" pass. Anything weirder is silently stripped so we never
// concatenate untrusted text into the storage key (Sonar S5247).
const SAFE_KEY_FRAGMENT = /[^A-Za-z0-9._-]/g;

function sanitizeKeyFragment(value) {
  return String(value ?? "").replace(SAFE_KEY_FRAGMENT, "_");
}

function keyFor(symbol, period) {
  return `${KEY_PREFIX}${sanitizeKeyFragment(symbol)}:${sanitizeKeyFragment(period)}`;
}

/**
 * Read a cached series. Returns { data, fresh } where:
 *   fresh = true  → within TTL, can use without re-fetching
 *   fresh = false → older than TTL but within STALE_TTL — okay to show
 *                   while a background re-fetch runs, also our offline plan
 *   null          → no usable entry
 */
export function readHistoryCache(symbol, period) {
  try {
    const raw = localStorage.getItem(keyFor(symbol, period));
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed?.data || !Array.isArray(parsed.data)) return null;
    const age = Date.now() - (parsed.ts || 0);
    if (age > STALE_TTL_MS) return null; // give up
    return { data: parsed.data, fresh: age < TTL_MS };
  } catch {
    return null;
  }
}

// Project each incoming row to a strict primitive shape before persisting.
// Stops any extra fields (or weird shapes from a mocked API) leaking into
// localStorage and gives Sonar's taint tracker (S5247) an explicit
// sanitization boundary — every field is forced to a known scalar type.
function sanitizeSeries(data) {
  return data.map((d) => ({
    day: String(d?.day ?? ""),
    close: Number(d?.close),
    label: String(d?.label ?? ""),
    timestamp: Number(d?.timestamp),
  }));
}

export function writeHistoryCache(symbol, period, data) {
  if (!Array.isArray(data) || data.length === 0) return;
  const payload = JSON.stringify({ ts: Date.now(), data: sanitizeSeries(data) });
  try {
    localStorage.setItem(keyFor(symbol, period), payload);
    enforceCap();
  } catch {
    // Quota exceeded — drop everything and retry once.
    try {
      clearHistoryCache();
      localStorage.setItem(keyFor(symbol, period), payload);
    } catch { /* give up */ }
  }
}

function enforceCap() {
  try {
    const keys = [];
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k && k.startsWith(KEY_PREFIX)) keys.push(k);
    }
    if (keys.length <= MAX_ENTRIES) return;
    // Evict oldest first (parse ts; missing → 0 → evicted first).
    const aged = keys.map((k) => {
      try { return { k, ts: JSON.parse(localStorage.getItem(k))?.ts || 0 }; }
      catch { return { k, ts: 0 }; }
    });
    aged.sort((a, b) => a.ts - b.ts);
    const drop = aged.slice(0, aged.length - MAX_ENTRIES);
    drop.forEach(({ k }) => { try { localStorage.removeItem(k); } catch { /* ignore */ } });
  } catch { /* ignore */ }
}

export function clearHistoryCache() {
  try {
    const remove = [];
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k && k.startsWith(KEY_PREFIX)) remove.push(k);
    }
    remove.forEach((k) => localStorage.removeItem(k));
  } catch { /* ignore */ }
}

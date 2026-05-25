const BASE = "/api/v1/inflation";

// Strict allow-list — country codes are 2-letter ISO 3166-1 alpha-2 only.
// Anything else is rejected before it lands in the request URL (Sonar
// S5247 / S5247) and the call falls back to the backend's default TR.
function safeCountry(country) {
  if (typeof country !== "string") return "TR";
  return /^[A-Za-z]{2}$/.test(country) ? country.toUpperCase() : "TR";
}

/**
 * Full monthly inflation history.
 * @param {string} [country="TR"] ISO 3166-1 alpha-2 country code.
 *   "TR" → TCMB CPI (TÜFE), "US" → FRED CPIAUCSL.
 */
export async function getInflationHistory(country = "TR") {
  const params = new URLSearchParams({ country: safeCountry(country) });
  const r = await fetch(`${BASE}?${params}`);
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r.json();
}

export async function getLatestInflation(country = "TR") {
  const params = new URLSearchParams({ country: safeCountry(country) });
  const r = await fetch(`${BASE}/latest?${params}`);
  if (r.status === 204) return null;
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r.json();
}

/**
 * Compute cumulative inflation between two dates and (optionally) the real return.
 * @param {string} fromIsoDate YYYY-MM-DD
 * @param {string} toIsoDate   YYYY-MM-DD
 * @param {number=} nominalPct
 */
export async function compareInflation(fromIsoDate, toIsoDate, nominalPct) {
  const params = new URLSearchParams({ from: fromIsoDate, to: toIsoDate });
  if (nominalPct !== undefined && nominalPct !== null) {
    params.append("nominalPct", String(nominalPct));
  }
  const r = await fetch(`${BASE}/compare?${params}`);
  if (r.status === 204) return null;
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r.json();
}

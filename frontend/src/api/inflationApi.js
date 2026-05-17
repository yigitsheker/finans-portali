const BASE = "/api/v1/inflation";

export async function getInflationHistory() {
  const r = await fetch(BASE);
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r.json();
}

export async function getLatestInflation() {
  const r = await fetch(`${BASE}/latest`);
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

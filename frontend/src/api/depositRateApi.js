const BASE = "/api/v1/deposit-rates";

/** Latest published month's deposit rates for all currencies (TRY/USD/EUR). */
export async function getLatestDepositRates() {
  const r = await fetch(`${BASE}/latest`);
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r.json();
}

/** All monthly history for a single currency, ascending by period. */
export async function getDepositRateHistory(currency = "TRY") {
  const r = await fetch(`${BASE}?currency=${encodeURIComponent(currency)}`);
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r.json();
}

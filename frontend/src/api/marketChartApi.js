import axios from "axios";

/**
 * OHLC candles for the native chart. Public endpoint (no auth) — same as the
 * rest of /api/v1/market. Returns [{ time, open, high, low, close, volume }]
 * with time in Unix epoch seconds (UTC).
 */
export async function getCandles(symbol, period = "30D") {
    const { data } = await axios.get("/api/v1/market/candles", {
        params: { symbol, period },
    });
    if (!Array.isArray(data)) return [];
    // Normalize BigDecimal-as-string/number to plain numbers for the chart lib.
    return data
        .map((c) => ({
            time: Number(c.time),
            open: Number(c.open),
            high: Number(c.high),
            low: Number(c.low),
            close: Number(c.close),
            volume: c.volume == null ? 0 : Number(c.volume),
        }))
        .filter((c) => Number.isFinite(c.time) && Number.isFinite(c.close));
}

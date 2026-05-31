import { useEffect, useState } from "react";
import { readHistoryCache } from "../utils/historyCache";
import { getMarketHistoryBatch } from "../api/marketApi";

const PERIOD = "1M";
const SPARKLINE_LIMIT = 200;
const BATCH_SIZE = 50;

const toSeries = (history) =>
    history.map((h) => ({ time: h.day.split("T")[0], value: h.close }));

// Sparkline mini-chart data for a list of market rows. Two-phase, shared by
// the market listing pages (Funds, MarketData, …) so there's a single copy
// of the cache-hydrate + batch-fetch logic:
//   1. Synchronous hydrate from the per-symbol history cache (instant, even
//      if stale) so the column paints immediately.
//   2. Background batch-fetch only the symbols whose cache is missing/stale,
//      writing the fresh series back into component state.
//
// `rows` is the already-filtered list of items (each with a `.symbol`). The
// returned map is keyed by symbol; values are [{ time, value }] series.
export default function useSparklines(rows) {
    const [sparklines, setSparklines] = useState({});

    useEffect(() => {
        if (rows.length === 0) return;
        let cancelled = false;
        const visible = rows.slice(0, SPARKLINE_LIMIT);

        const cachedSeed = {};
        const needsFetch = [];
        visible.forEach((item) => {
            if (sparklines[item.symbol]) return;
            const cached = readHistoryCache(item.symbol, PERIOD);
            if (cached) {
                cachedSeed[item.symbol] = toSeries(cached.data);
                if (!cached.fresh) needsFetch.push(item.symbol);
            } else {
                needsFetch.push(item.symbol);
            }
        });
        if (Object.keys(cachedSeed).length > 0) {
            setSparklines((prev) => ({ ...cachedSeed, ...prev }));
        }
        if (needsFetch.length === 0) return;

        (async () => {
            for (let i = 0; i < needsFetch.length; i += BATCH_SIZE) {
                if (cancelled) return;
                const chunk = needsFetch.slice(i, i + BATCH_SIZE);
                try {
                    const map = await getMarketHistoryBatch(chunk, PERIOD);
                    if (cancelled) return;
                    const update = {};
                    for (const sym of chunk) {
                        const history = map[sym];
                        if (Array.isArray(history) && history.length > 0) {
                            update[sym] = toSeries(history);
                        }
                    }
                    if (Object.keys(update).length > 0 && !cancelled) {
                        setSparklines((prev) => ({ ...prev, ...update }));
                    }
                } catch {
                    // ignore sparkline fetch errors — they're non-critical
                }
            }
        })();

        return () => {
            cancelled = true;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [rows]);

    return sparklines;
}

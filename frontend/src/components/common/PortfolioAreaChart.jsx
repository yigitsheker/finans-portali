import { useEffect, useRef } from "react";
import {
  createChart,
  ColorType,
  CrosshairMode,
  AreaSeries,
  TickMarkType,
  createSeriesMarkers,
} from "lightweight-charts";

// A normalized series time is either a 'YYYY-MM-DD' string (daily) or a unix
// timestamp number (intraday). Reduce either to a 'YYYY-MM-DD' day string so a
// buy date can be matched against the series points.
function pointDayStr(time) {
  if (typeof time === "number") return new Date(time * 1000).toISOString().slice(0, 10);
  if (typeof time === "string") return time.slice(0, 10);
  return "";
}

// Lightweight-charts may invoke formatters with UTCTimestamp (number),
// BusinessDay ({year,month,day}), or a 'YYYY-MM-DD' string.
function toDate(time) {
  if (typeof time === "number") return new Date(time * 1000);
  if (typeof time === "string") {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(time);
    if (m) return new Date(Date.UTC(+m[1], +m[2] - 1, +m[3]));
    return new Date(time);
  }
  if (time && typeof time === "object" && "year" in time) {
    return new Date(Date.UTC(time.year, time.month - 1, time.day));
  }
  return new Date(Number.NaN);
}

export function PortfolioAreaChart({ data, isIntraday = false, height = 200, positive = null, markerDates = [] }) {
  const containerRef = useRef(null);
  const chartRef = useRef(null);
  const seriesRef = useRef(null);
  const markersRef = useRef(null);
  // Joined into a primitive so the data effect's dep array compares by value,
  // not array identity (the parent rebuilds the array each render).
  const markerKey = (markerDates || []).join(",");

  // Determine chart color: prefer the caller-supplied P/L sign (based on real
  // buy price vs current price), since the first/last series values reflect
  // historical market closes which may differ from the user's actual cost basis.
  const isPositive = positive !== null ? positive : (data.length < 2 || data[data.length - 1].value >= data[0].value);
  const lineColor   = isPositive ? "#22c55e" : "#ef4444";
  const topColor    = isPositive ? "rgba(34,197,94,0.28)" : "rgba(239,68,68,0.28)";
  const bottomColor = isPositive ? "rgba(34,197,94,0.02)" : "rgba(239,68,68,0.02)";

  // Create chart once
  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: "#7d8590",
        fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
        fontSize: 11,
        attributionLogo: false,
      },
      grid: {
        vertLines: { color: "rgba(48,54,61,0.5)" },
        horzLines: { color: "rgba(48,54,61,0.5)" },
      },
      crosshair: {
        mode: CrosshairMode.Magnet,
        vertLine: {
          color: "#3b82f6",
          width: 1,
          style: 3, // dashed
          labelBackgroundColor: "#1d4ed8",
        },
        horzLine: {
          color: "#3b82f6",
          width: 1,
          style: 3,
          labelBackgroundColor: "#1d4ed8",
        },
      },
      rightPriceScale: {
        borderColor: "rgba(48,54,61,0.6)",
        scaleMargins: { top: 0.1, bottom: 0.1 },
      },
      timeScale: {
        borderColor: "rgba(48,54,61,0.6)",
        timeVisible: isIntraday,
        secondsVisible: false,
        tickMarkFormatter: isIntraday
          ? (time, tickMarkType) => {
              const d = toDate(time);
              // Day-level ticks: show "D MMM" so multi-day intraday charts are readable.
              // Time-level ticks: show "HH:MM".
              if (tickMarkType === TickMarkType.Year || tickMarkType === TickMarkType.Month || tickMarkType === TickMarkType.DayOfMonth) {
                return d.toLocaleDateString("tr-TR", { day: "numeric", month: "short" });
              }
              return d.toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" });
            }
          : undefined,
      },
      localization: {
        locale: "tr-TR",
        priceFormatter: (price) =>
          "₺" + price.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
        timeFormatter: isIntraday
          ? (time) => {
              const d = toDate(time);
              return d.toLocaleString("tr-TR", {
                month: "short", day: "numeric",
                hour: "2-digit", minute: "2-digit",
              });
            }
          : (time) => {
              const d = toDate(time);
              return d.toLocaleDateString("tr-TR", { year: "numeric", month: "short", day: "numeric" });
            },
      },
      handleScroll: true,
      handleScale: true,
      width: containerRef.current.clientWidth,
      height,
    });

    const series = chart.addSeries(AreaSeries, {
      lineColor,
      topColor,
      bottomColor,
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: true,
      crosshairMarkerVisible: true,
      crosshairMarkerRadius: 5,
      crosshairMarkerBorderColor: lineColor,
      crosshairMarkerBackgroundColor: "#0d1117",
    });

    chartRef.current = chart;
    seriesRef.current = series;
    // Markers primitive lives on the series — recreated alongside it. Capital
    // -injection markers (buy dates) are pushed in the data effect below.
    markersRef.current = createSeriesMarkers(series, []);

    // ResizeObserver for responsive sizing
    const ro = new ResizeObserver((entries) => {
      const { width } = entries[0].contentRect;
      chart.applyOptions({ width });
    });
    ro.observe(containerRef.current);

    return () => {
      ro.disconnect();
      chart.remove();
      chartRef.current = null;
      seriesRef.current = null;
      markersRef.current = null;
    };
  }, [isIntraday, height]); // recreate only when mode changes

  // Update data and colors when data changes
  useEffect(() => {
    if (!seriesRef.current || !chartRef.current || data.length === 0) return;

    // Normalize time values
    const normalized = data.map((p) => {
      // If it's an ISO datetime string, convert to unix timestamp
      if (p.time.includes("T") || p.time.includes(" ")) {
        return { time: Math.floor(new Date(p.time).getTime() / 1000), value: p.value };
      }
      return { time: p.time, value: p.value };
    });

    // Update colors based on trend
    const trendPositive = positive !== null
      ? positive
      : (normalized.length < 2 || normalized[normalized.length - 1].value >= normalized[0].value);
    const lc   = trendPositive ? "#22c55e" : "#ef4444";
    const tc   = trendPositive ? "rgba(34,197,94,0.28)" : "rgba(239,68,68,0.28)";
    const bc   = trendPositive ? "rgba(34,197,94,0.02)" : "rgba(239,68,68,0.02)";

    seriesRef.current.applyOptions({ lineColor: lc, topColor: tc, bottomColor: bc });
    seriesRef.current.setData(normalized);

    // Capital-injection markers: an upward arrow on each buy date so the user
    // sees the jump is a new position being added, not a natural price move.
    // Each buy date snaps to the first series point on/after it; dates outside
    // the visible window are skipped, and duplicates on the same point merge.
    if (markersRef.current) {
      const dayStrs = normalized.map((p) => pointDayStr(p.time));
      const firstDay = dayStrs[0];
      const lastDay = dayStrs[dayStrs.length - 1];
      const seen = new Set();
      const built = [];
      for (const raw of (markerDates || [])) {
        const md = String(raw || "").slice(0, 10);
        if (!md || md < firstDay || md > lastDay) continue;
        const idx = dayStrs.findIndex((ds) => ds >= md);
        if (idx < 0 || seen.has(idx)) continue;
        seen.add(idx);
        built.push({
          idx,
          time: normalized[idx].time,
          position: "belowBar",
          color: "#f59e0b",
          shape: "arrowUp",
          text: "Ekleme",
        });
      }
      built.sort((a, b) => a.idx - b.idx); // markers must be time-ascending
      markersRef.current.setMarkers(built.map(({ idx, ...m }) => m));
    }

    chartRef.current.timeScale().fitContent();
  }, [data, positive, markerKey]);

  return (
    <div
      ref={containerRef}
      style={{ width: "100%", height }}
    />
  );
}

import { useEffect, useRef } from "react";
import {
  createChart,
  ColorType,
  CrosshairMode,
  AreaSeries,
  TickMarkType,
} from "lightweight-charts";

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
  return new Date(NaN);
}

export function PortfolioAreaChart({ data, isIntraday = false, height = 200 }) {
  const containerRef = useRef(null);
  const chartRef = useRef(null);
  const seriesRef = useRef(null);

  // Determine chart color based on first vs last value
  const isPositive = data.length < 2 || data[data.length - 1].value >= data[0].value;
  const lineColor   = isPositive ? "#3b82f6" : "#ef4444";
  const topColor    = isPositive ? "rgba(59,130,246,0.28)" : "rgba(239,68,68,0.28)";
  const bottomColor = isPositive ? "rgba(59,130,246,0.02)" : "rgba(239,68,68,0.02)";

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
    const positive = normalized.length < 2 || normalized[normalized.length - 1].value >= normalized[0].value;
    const lc   = positive ? "#3b82f6" : "#ef4444";
    const tc   = positive ? "rgba(59,130,246,0.28)" : "rgba(239,68,68,0.28)";
    const bc   = positive ? "rgba(59,130,246,0.02)" : "rgba(239,68,68,0.02)";

    seriesRef.current.applyOptions({ lineColor: lc, topColor: tc, bottomColor: bc });
    seriesRef.current.setData(normalized);
    chartRef.current.timeScale().fitContent();
  }, [data]);

  return (
    <div
      ref={containerRef}
      style={{ width: "100%", height }}
    />
  );
}

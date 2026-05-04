import { useEffect, useRef } from "react";
import {
  createChart,
  type IChartApi,
  type ISeriesApi,
  ColorType,
  AreaSeries,
} from "lightweight-charts";

export interface SparklinePoint {
  time: string;   // 'YYYY-MM-DD'
  value: number;
}

interface LWSparklineProps {
  data: SparklinePoint[];
  positive: boolean;
  width?: number;
  height?: number;
}

export function LWSparkline({ data, positive, width = 100, height = 36 }: LWSparklineProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef     = useRef<IChartApi | null>(null);
  const seriesRef    = useRef<ISeriesApi<"Area"> | null>(null);

  // Create chart once on mount
  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: "transparent",
      },
      grid: {
        vertLines: { visible: false },
        horzLines: { visible: false },
      },
      crosshair: {
        vertLine: { visible: false },
        horzLine: { visible: false },
      },
      rightPriceScale: { visible: false, borderVisible: false },
      leftPriceScale:  { visible: false },
      timeScale:       { visible: false, borderVisible: false },
      // Disable all interactions — pure display/watch mode
      handleScroll: false,
      handleScale:  false,
      width,
      height,
    });

    const series = chart.addSeries(AreaSeries, {
      lineColor:  positive ? "#22c55e" : "#ef4444",
      topColor:   positive ? "rgba(34,197,94,0.20)"  : "rgba(239,68,68,0.20)",
      bottomColor: "transparent",
      lineWidth: 2,
      priceLineVisible:       false,
      lastValueVisible:       false,
      crosshairMarkerVisible: false,
    });

    chartRef.current  = chart;
    seriesRef.current = series;

    return () => {
      chart.remove();
      chartRef.current  = null;
      seriesRef.current = null;
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // mount once — intentionally no deps

  // Update series data and colors whenever data/positive changes
  useEffect(() => {
    if (!seriesRef.current || !chartRef.current) return;

    const lc = positive ? "#22c55e" : "#ef4444";
    const tc = positive ? "rgba(34,197,94,0.20)" : "rgba(239,68,68,0.20)";

    seriesRef.current.applyOptions({ lineColor: lc, topColor: tc });

    if (data.length > 0) {
      seriesRef.current.setData(data as any);
      chartRef.current.timeScale().fitContent();
    }
  }, [data, positive]);

  return (
    <div
      ref={containerRef}
      style={{ width, height, display: "block", overflow: "hidden", flexShrink: 0 }}
    />
  );
}

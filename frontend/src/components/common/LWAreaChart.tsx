/**
 * LWAreaChart — Lightweight Charts v5 area chart wrapper.
 * Watermark is hidden via CSS overflow:hidden on the container.
 */
import { useEffect, useRef } from "react";
import {
    createChart,
    type IChartApi,
    type ISeriesApi,
    ColorType,
    CrosshairMode,
    AreaSeries,
} from "lightweight-charts";

export interface LWChartPoint {
    time: number;   // Unix timestamp (seconds)
    value: number;
}

interface LWAreaChartProps {
    data: LWChartPoint[];
    color?: string;
    height?: number;
}

export function LWAreaChart({ data, color = "#22c55e", height = 300 }: LWAreaChartProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const chartRef     = useRef<IChartApi | null>(null);
    const seriesRef    = useRef<ISeriesApi<"Area"> | null>(null);

    useEffect(() => {
        if (!containerRef.current) return;

        const chart = createChart(containerRef.current, {
            layout: {
                background: { type: ColorType.Solid, color: "transparent" },
                textColor: "var(--text-muted, #6b8f6b)",
                fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
                fontSize: 11,
            },
            grid: {
                vertLines: { color: "rgba(34,197,94,0.08)" },
                horzLines: { color: "rgba(34,197,94,0.08)" },
            },
            crosshair: {
                mode: CrosshairMode.Magnet,
                vertLine: { color: color, width: 1, style: 3, labelBackgroundColor: "#0f1410" },
                horzLine: { color: color, width: 1, style: 3, labelBackgroundColor: "#0f1410" },
            },
            rightPriceScale: {
                borderColor: "rgba(34,197,94,0.15)",
                scaleMargins: { top: 0.1, bottom: 0.1 },
            },
            timeScale: {
                borderColor: "rgba(34,197,94,0.15)",
                timeVisible: true,
                secondsVisible: false,
            },
            localization: {
                locale: "tr-TR",
                priceFormatter: (p: number) =>
                    p.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
            },
            handleScroll: true,
            handleScale: true,
            width: containerRef.current.clientWidth,
            height,
        });

        const series = chart.addSeries(AreaSeries, {
            lineColor: color,
            topColor: color.replace(")", ", 0.25)").replace("rgb", "rgba"),
            bottomColor: "transparent",
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: true,
            crosshairMarkerVisible: true,
            crosshairMarkerRadius: 5,
            crosshairMarkerBorderColor: color,
            crosshairMarkerBackgroundColor: "#0f1410",
        });

        chartRef.current  = chart;
        seriesRef.current = series;

        const ro = new ResizeObserver(entries => {
            chart.applyOptions({ width: entries[0].contentRect.width });
        });
        ro.observe(containerRef.current);

        return () => {
            ro.disconnect();
            chart.remove();
            chartRef.current  = null;
            seriesRef.current = null;
        };
    }, [height]);

    useEffect(() => {
        if (!seriesRef.current || !chartRef.current || data.length === 0) return;
        const sorted = [...data].sort((a, b) => a.time - b.time);
        // Deduplicate by time
        const deduped = sorted.filter((p, i) => i === 0 || p.time !== sorted[i - 1].time);
        seriesRef.current.setData(deduped as any);
        chartRef.current.timeScale().fitContent();
    }, [data]);

    useEffect(() => {
        if (!seriesRef.current) return;
        const tc = color.startsWith("#")
            ? hexToRgba(color, 0.25)
            : color;
        seriesRef.current.applyOptions({
            lineColor: color,
            topColor: tc,
        });
    }, [color]);

    return (
        // overflow:hidden clips the Lightweight Charts watermark that appears outside the chart area
        <div style={{ position: "relative", overflow: "hidden", borderRadius: 8 }}>
            <div ref={containerRef} style={{ width: "100%", height }} />
        </div>
    );
}

function hexToRgba(hex: string, alpha: number): string {
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return `rgba(${r},${g},${b},${alpha})`;
}

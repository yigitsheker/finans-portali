/**
 * LWMultiLineChart — Lightweight Charts v5 multi-series line chart.
 * Watermark hidden via CSS overflow:hidden.
 */
import { useEffect, useRef } from "react";
import {
    createChart,
    type IChartApi,
    type ISeriesApi,
    ColorType,
    CrosshairMode,
    LineSeries,
} from "lightweight-charts";

export interface LWSeriesData {
    symbol: string;
    color: string;
    data: { time: number; value: number }[];
}

interface LWMultiLineChartProps {
    series: LWSeriesData[];
    height?: number;
    priceFormatter?: (v: number) => string;
}

export function LWMultiLineChart({ series, height = 380, priceFormatter }: LWMultiLineChartProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const chartRef     = useRef<IChartApi | null>(null);
    const seriesRefs   = useRef<Map<string, ISeriesApi<"Line">>>(new Map());

    // Create chart once
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
                vertLine: { color: "rgba(34,197,94,0.5)", width: 1, style: 3, labelBackgroundColor: "#0f1410" },
                horzLine: { color: "rgba(34,197,94,0.5)", width: 1, style: 3, labelBackgroundColor: "#0f1410" },
            },
            rightPriceScale: {
                borderColor: "rgba(34,197,94,0.15)",
                scaleMargins: { top: 0.08, bottom: 0.08 },
            },
            timeScale: {
                borderColor: "rgba(34,197,94,0.15)",
                timeVisible: true,
                secondsVisible: false,
            },
            localization: {
                locale: "tr-TR",
                priceFormatter: priceFormatter ?? ((v: number) =>
                    v.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
                ),
            },
            handleScroll: true,
            handleScale: true,
            width: containerRef.current.clientWidth,
            height,
        });

        chartRef.current = chart;

        const ro = new ResizeObserver(entries => {
            chart.applyOptions({ width: entries[0].contentRect.width });
        });
        ro.observe(containerRef.current);

        return () => {
            ro.disconnect();
            chart.remove();
            chartRef.current = null;
            seriesRefs.current.clear();
        };
    }, [height]);

    // Update series data when series prop changes
    useEffect(() => {
        if (!chartRef.current) return;

        const chart = chartRef.current;
        const existingSymbols = new Set(seriesRefs.current.keys());
        const newSymbols = new Set(series.map(s => s.symbol));

        // Remove series that are no longer needed
        for (const sym of existingSymbols) {
            if (!newSymbols.has(sym)) {
                const s = seriesRefs.current.get(sym);
                if (s) chart.removeSeries(s);
                seriesRefs.current.delete(sym);
            }
        }

        // Add or update series
        for (const s of series) {
            let lineSeries = seriesRefs.current.get(s.symbol);

            if (!lineSeries) {
                lineSeries = chart.addSeries(LineSeries, {
                    color: s.color,
                    lineWidth: 2,
                    priceLineVisible: false,
                    lastValueVisible: true,
                    crosshairMarkerVisible: true,
                    crosshairMarkerRadius: 4,
                    crosshairMarkerBorderColor: s.color,
                    crosshairMarkerBackgroundColor: "#0f1410",
                    title: s.symbol,
                });
                seriesRefs.current.set(s.symbol, lineSeries);
            } else {
                lineSeries.applyOptions({ color: s.color });
            }

            if (s.data.length > 0) {
                // Sort and deduplicate by time
                const sorted = [...s.data].sort((a, b) => a.time - b.time);
                const deduped = sorted.filter((p, i) => i === 0 || p.time !== sorted[i - 1].time);
                lineSeries.setData(deduped as any);
            }
        }

        if (series.some(s => s.data.length > 0)) {
            chart.timeScale().fitContent();
        }
    }, [series]);

    return (
        <div style={{ position: "relative", overflow: "hidden", borderRadius: 8 }}>
            <div ref={containerRef} style={{ width: "100%", height }} />
        </div>
    );
}

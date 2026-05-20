/**
 * LWAreaChart — Lightweight Charts v5 area chart wrapper.
 * Watermark is hidden via CSS overflow:hidden on the container.
 */
import { useEffect, useRef } from "react";
import {
    createChart,
    ColorType,
    CrosshairMode,
    AreaSeries,
} from "lightweight-charts";

/**
 * Lightweight-charts paints on a canvas and can't resolve CSS variables —
 * it'd render them as a literal string and fall back to black, which is
 * unreadable on dark backgrounds. Compute the theme-appropriate axis
 * colours up front and re-apply them whenever the user flips theme.
 */
function readChartPalette() {
    const isLight = document.documentElement.getAttribute("data-theme") === "light";
    return isLight
        ? {
            text: "#475569",
            gridLine: "rgba(21, 128, 61, 0.10)",
            border: "rgba(21, 128, 61, 0.20)",
            crosshairBg: "#0f172a",
        }
        : {
            text: "#a8c5a8",
            gridLine: "rgba(34, 197, 94, 0.10)",
            border: "rgba(34, 197, 94, 0.25)",
            crosshairBg: "#0f1410",
        };
}

export function LWAreaChart({ data, color = "#22c55e", height = 300 }) {
    const containerRef = useRef(null);
    const chartRef     = useRef(null);
    const seriesRef    = useRef(null);

    useEffect(() => {
        if (!containerRef.current) return;
        const palette = readChartPalette();

        const chart = createChart(containerRef.current, {
            layout: {
                background: { type: ColorType.Solid, color: "transparent" },
                textColor: palette.text,
                fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
                fontSize: 11,
            },
            grid: {
                vertLines: { color: palette.gridLine },
                horzLines: { color: palette.gridLine },
            },
            crosshair: {
                mode: CrosshairMode.Magnet,
                vertLine: { color: color, width: 1, style: 3, labelBackgroundColor: palette.crosshairBg },
                horzLine: { color: color, width: 1, style: 3, labelBackgroundColor: palette.crosshairBg },
            },
            rightPriceScale: {
                borderColor: palette.border,
                scaleMargins: { top: 0.1, bottom: 0.1 },
            },
            timeScale: {
                borderColor: palette.border,
                timeVisible: true,
                secondsVisible: false,
            },
            localization: {
                locale: "tr-TR",
                priceFormatter: (p) =>
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

        // Re-apply axis colours when the user flips between light/dark.
        // The Topbar toggle mutates `data-theme` on <html>; a MutationObserver
        // is the simplest cross-component listener.
        const repaintTheme = () => {
            const p = readChartPalette();
            chart.applyOptions({
                layout: { textColor: p.text, background: { type: ColorType.Solid, color: "transparent" } },
                grid: { vertLines: { color: p.gridLine }, horzLines: { color: p.gridLine } },
                crosshair: {
                    vertLine: { labelBackgroundColor: p.crosshairBg },
                    horzLine: { labelBackgroundColor: p.crosshairBg },
                },
                rightPriceScale: { borderColor: p.border },
                timeScale: { borderColor: p.border },
            });
        };
        const themeObs = new MutationObserver(repaintTheme);
        themeObs.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ["data-theme"],
        });

        return () => {
            themeObs.disconnect();
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
        seriesRef.current.setData(deduped);
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

function hexToRgba(hex, alpha) {
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return `rgba(${r},${g},${b},${alpha})`;
}

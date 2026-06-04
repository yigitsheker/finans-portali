import { useSearchParams } from "react-router-dom";
import KLineChart from "../components/KLineChart";

// Full-page detailed chart opened in its own browser tab (route /chart).
// Backed by the app's OWN OHLC data (/api/v1/market/candles) and rendered with
// klinecharts, which provides a full trader drawing-tool suite (trend, ray,
// Fibonacci, parallel/channel…) plus MA/VOL/RSI/MACD. Works for every symbol
// incl. BIST, unlike the old TradingView free embed. The page is just chrome.
export default function ChartPage() {
    const [searchParams] = useSearchParams();
    const symbol = searchParams.get("symbol") || "THYAO";

    return (
        <div style={s.root}>
            <div style={s.header}>
                <button style={s.backBtn} onClick={() => window.close()}>
                    ← Kapat
                </button>
                <div style={s.symbolInfo}>
                    <h1 style={s.symbolName}>{symbol}</h1>
                </div>
                <div></div>
            </div>
            <div style={s.widget}>
                <KLineChart symbol={symbol} />
            </div>
        </div>
    );
}

const s = {
    root: {
        width: "100vw",
        height: "100vh",
        display: "flex",
        flexDirection: "column",
        background: "#0d1117",
        color: "#e6edf3",
        overflow: "hidden",
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        margin: 0,
        padding: 0,
    },
    header: {
        padding: 20,
        background: "#161b22",
        borderBottom: "1px solid #30363d",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexShrink: 0,
    },
    backBtn: {
        padding: "8px 16px",
        background: "#21262d",
        border: "1px solid #30363d",
        borderRadius: 6,
        color: "#e6edf3",
        cursor: "pointer",
        fontSize: 14,
        fontWeight: 500,
        transition: "background 0.2s",
    },
    symbolInfo: {
        textAlign: "center",
    },
    symbolName: {
        fontSize: 24,
        fontWeight: 700,
        margin: 0,
    },
    widget: {
        flex: 1,
        padding: 20,
        position: "relative",
    },
};

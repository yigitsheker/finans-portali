import { useSearchParams } from "react-router-dom";
import NativeChart from "../components/NativeChart";

// Full-page detailed chart opened in its own browser tab (route /chart).
// Uses the native lightweight-charts component fed by our OWN backend OHLC
// (/api/v1/market/candles) — so BIST and every other symbol render with data
// consistent with the rest of the app, unlike the TradingView free embed which
// gated BIST. The page is just the chrome (close button + symbol header).
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
                <NativeChart symbol={symbol} />
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

import { useSearchParams } from "react-router-dom";
import TradingViewWidget from "../components/TradingViewWidget";

// Full-page TradingView chart opened in its own browser tab (route /chart).
// The widget itself — tv.js bootstrap, SYMBOL_MAP, the S5725/SRI handling
// and the widget-instance lifecycle — lives once in TradingViewWidget. This
// page is just the surrounding chrome (close button + symbol header) and
// maps the query string onto the widget's props, so there's a single copy
// of the embed logic to maintain.
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
                <TradingViewWidget symbol={symbol} theme="dark" height="100%" />
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

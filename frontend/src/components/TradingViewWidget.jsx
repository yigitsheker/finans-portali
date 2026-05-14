import { useEffect, useRef } from "react";

// Symbol mapping for TradingView
const SYMBOL_MAP = {
    // BIST Stocks
    "THYAO": "BIST:THYAO",
    "GARAN": "BIST:GARAN",
    "AKBNK": "BIST:AKBNK",
    "EREGL": "BIST:EREGL",
    "SAHOL": "BIST:SAHOL",
    "SISE": "BIST:SISE",
    "ASELS": "BIST:ASELS",
    "KCHOL": "BIST:KCHOL",
    "TUPRS": "BIST:TUPRS",
    "PETKM": "BIST:PETKM",
    "TCELL": "BIST:TCELL",
    "ISCTR": "BIST:ISCTR",
    "VAKBN": "BIST:VAKBN",
    "ENKAI": "BIST:ENKAI",
    "KOZAL": "BIST:KOZAL",
    "BIMAS": "BIST:BIMAS",
    "TTKOM": "BIST:TTKOM",
    "PGSUS": "BIST:PGSUS",
    "FROTO": "BIST:FROTO",
    "TOASO": "BIST:TOASO",
    "HALKB": "BIST:HALKB",
    "ARCLK": "BIST:ARCLK",
    "KOZAA": "BIST:KOZAA",
    "TAVHL": "BIST:TAVHL",
    "SODA": "BIST:SODA",

    // Indices
    "XU100": "BIST:XU100",
    "XU030": "BIST:XU030",

    // Forex
    "USDTRY": "FX:USDTRY",
    "EURTRY": "FX:EURTRY",
    "GBPTRY": "FX:GBPTRY",

    // Crypto
    "BTCUSD": "BINANCE:BTCUSDT",
    "ETHUSD": "BINANCE:ETHUSDT",
    "SOLUSD": "BINANCE:SOLUSDT",

    // Commodities
    "XAUUSD": "OANDA:XAUUSD",
    "XAGUSD": "OANDA:XAGUSD",

    // US Stocks
    "AAPL": "NASDAQ:AAPL",
    "MSFT": "NASDAQ:MSFT",
    "GOOGL": "NASDAQ:GOOGL",
    "AMZN": "NASDAQ:AMZN",
    "NVDA": "NASDAQ:NVDA",
    "TSLA": "NASDAQ:TSLA",
    "META": "NASDAQ:META",

    // US Indices
    "IXIC": "NASDAQ:IXIC",
    "SPX": "SP:SPX",
    "DJI": "DJ:DJI",
};

export default function TradingViewWidget({ symbol, theme = "dark" }) {
    const containerRef = useRef(null);

    useEffect(() => {
        if (!containerRef.current) return;

        // Clear previous widget
        containerRef.current.innerHTML = "";

        // Map symbol to TradingView format
        const tvSymbol = SYMBOL_MAP[symbol] || `BIST:${symbol}`;

        // Create TradingView widget
        const script = document.createElement("script");
        script.src = "https://s3.tradingview.com/tv.js";
        script.async = true;
        script.onload = () => {
            if (typeof window.TradingView !== "undefined") {
                new window.TradingView.widget({
                    container_id: containerRef.current?.id || "tradingview_widget",
                    autosize: true,
                    symbol: tvSymbol,
                    interval: "D",
                    timezone: "Europe/Istanbul",
                    theme: theme,
                    style: "1",
                    locale: "tr",
                    toolbar_bg: theme === "dark" ? "#161b22" : "#f1f3f6",
                    enable_publishing: false,
                    hide_top_toolbar: false,
                    hide_legend: false,
                    save_image: true,
                    allow_symbol_change: true,
                    studies: [
                        "MASimple@tv-basicstudies",
                        "RSI@tv-basicstudies",
                        "MACD@tv-basicstudies"
                    ],
                    show_popup_button: true,
                    popup_width: "1000",
                    popup_height: "650",
                });
            }
        };

        document.head.appendChild(script);

        return () => {
            if (script.parentNode) {
                script.parentNode.removeChild(script);
            }
        };
    }, [symbol, theme]);

    return (
        <div
            id="tradingview_widget"
            ref={containerRef}
            style={{
                width: "100%",
                height: "500px",
                background: theme === "dark" ? "#161b22" : "#ffffff",
                borderRadius: "8px",
                overflow: "hidden",
            }}
        />
    );
}

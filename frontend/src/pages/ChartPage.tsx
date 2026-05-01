import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

// Symbol mapping for TradingView
const SYMBOL_MAP: Record<string, string> = {
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

declare global {
    interface Window {
        TradingView: any;
    }
}

export default function ChartPage() {
    const [searchParams] = useSearchParams();
    const symbol = searchParams.get('symbol') || 'THYAO';
    const [scriptLoaded, setScriptLoaded] = useState(false);

    useEffect(() => {
        // Check if script already loaded
        if (window.TradingView) {
            setScriptLoaded(true);
            return;
        }

        // Load TradingView script
        const script = document.createElement('script');
        script.src = 'https://s3.tradingview.com/tv.js';
        script.async = true;
        script.onload = () => {
            setScriptLoaded(true);
        };
        document.head.appendChild(script);

        return () => {
            // Don't remove script on unmount to avoid reloading
        };
    }, []);

    useEffect(() => {
        if (!scriptLoaded) return;

        const tvSymbol = SYMBOL_MAP[symbol] || `BIST:${symbol}`;

        // Clear previous widget
        const container = document.getElementById('tradingview_widget');
        if (container) {
            container.innerHTML = '';
        }

        // Create new widget
        if (window.TradingView) {
            new window.TradingView.widget({
                container_id: 'tradingview_widget',
                autosize: true,
                symbol: tvSymbol,
                interval: 'D',
                timezone: 'Europe/Istanbul',
                theme: 'dark',
                style: '1',
                locale: 'tr',
                toolbar_bg: '#161b22',
                enable_publishing: false,
                hide_top_toolbar: false,
                hide_legend: false,
                save_image: true,
                allow_symbol_change: true,
                studies: [
                    'MASimple@tv-basicstudies',
                    'RSI@tv-basicstudies',
                    'MACD@tv-basicstudies',
                    'Volume@tv-basicstudies'
                ],
                show_popup_button: true,
                popup_width: '1000',
                popup_height: '650',
            });
        }
    }, [scriptLoaded, symbol]);

    const tvSymbol = SYMBOL_MAP[symbol] || `BIST:${symbol}`;

    return (
        <div style={s.root}>
            <div style={s.header}>
                <button style={s.backBtn} onClick={() => window.close()}>
                    ← Kapat
                </button>
                <div style={s.symbolInfo}>
                    <h1 style={s.symbolName}>{symbol}</h1>
                    <p style={s.symbolDesc}>{tvSymbol}</p>
                </div>
                <div></div>
            </div>
            <div id="tradingview_widget" style={s.widget}>
                {!scriptLoaded && (
                    <div style={s.loading}>
                        <div style={s.spinner}></div>
                        <div style={{ color: '#7d8590', marginTop: 12 }}>Grafik yükleniyor...</div>
                    </div>
                )}
            </div>
        </div>
    );
}

const s: Record<string, React.CSSProperties> = {
    root: {
        width: '100vw',
        height: '100vh',
        display: 'flex',
        flexDirection: 'column',
        background: '#0d1117',
        color: '#e6edf3',
        overflow: 'hidden',
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        margin: 0,
        padding: 0,
    },
    header: {
        padding: 20,
        background: '#161b22',
        borderBottom: '1px solid #30363d',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexShrink: 0,
    },
    backBtn: {
        padding: '8px 16px',
        background: '#21262d',
        border: '1px solid #30363d',
        borderRadius: 6,
        color: '#e6edf3',
        cursor: 'pointer',
        fontSize: 14,
        fontWeight: 500,
        transition: 'background 0.2s',
    },
    symbolInfo: {
        textAlign: 'center',
    },
    symbolName: {
        fontSize: 24,
        marginBottom: 4,
        fontWeight: 700,
        margin: 0,
    },
    symbolDesc: {
        fontSize: 14,
        color: '#7d8590',
        margin: 0,
    },
    widget: {
        flex: 1,
        padding: 20,
        position: 'relative',
    },
    loading: {
        position: 'absolute',
        top: '50%',
        left: '50%',
        transform: 'translate(-50%, -50%)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
    },
    spinner: {
        width: 40,
        height: 40,
        border: '3px solid #30363d',
        borderTop: '3px solid #3b82f6',
        borderRadius: '50%',
        animation: 'spin 0.8s linear infinite',
    },
};

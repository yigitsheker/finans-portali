import { useEffect, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import TradingViewWidget from "../components/TradingViewWidget";
import { getMarketSummary, type MarketSummaryItem } from "../api/portfolioApi";

export default function ChartDetail() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const symbol = searchParams.get("symbol");
    const [instrument, setInstrument] = useState<MarketSummaryItem | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        if (!symbol) {
            navigate("/");
            return;
        }

        getMarketSummary()
            .then((data) => {
                const found = data.find((item) => item.symbol === symbol);
                if (found) {
                    setInstrument(found);
                } else {
                    navigate("/");
                }
            })
            .catch(() => navigate("/"))
            .finally(() => setLoading(false));
    }, [symbol, navigate]);

    if (loading) {
        return (
            <div style={s.loading}>
                <div style={s.spinner}></div>
                <div style={{ color: "var(--text-muted)", marginTop: 12 }}>Yükleniyor...</div>
            </div>
        );
    }

    if (!instrument) {
        return null;
    }

    const positive = instrument.changePct >= 0;
    const color = positive ? "#10b981" : "#ef4444";

    return (
        <div style={s.root}>
            <div style={s.header}>
                <button style={s.backBtn} onClick={() => navigate(-1)}>
                    ← Geri
                </button>
                <div style={s.headerInfo}>
                    <div>
                        <h1 style={s.symbol}>{instrument.symbol}</h1>
                        <p style={s.name}>{instrument.name}</p>
                    </div>
                    <div style={s.priceBox}>
                        <div style={s.price}>
                            ₺{instrument.last?.toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                        </div>
                        <div style={{ color, fontSize: 16, fontWeight: 600 }}>
                            {positive ? "▲" : "▼"} {positive ? "+" : ""}
                            {instrument.changePct?.toFixed(2)}%
                        </div>
                    </div>
                </div>
            </div>

            <div style={s.chartContainer}>
                <TradingViewWidget symbol={instrument.symbol} theme="dark" />
            </div>
        </div>
    );
}

const s: Record<string, React.CSSProperties> = {
    root: {
        display: "flex",
        flexDirection: "column",
        gap: 20,
        height: "calc(100vh - 120px)",
    },
    loading: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: 400,
    },
    spinner: {
        width: 40,
        height: 40,
        border: "3px solid var(--border)",
        borderTop: "3px solid #3b82f6",
        borderRadius: "50%",
        animation: "spin 0.8s linear infinite",
    },
    header: {
        display: "flex",
        flexDirection: "column",
        gap: 16,
    },
    backBtn: {
        padding: "8px 16px",
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        color: "var(--text-primary)",
        fontSize: 14,
        fontWeight: 500,
        cursor: "pointer",
        alignSelf: "flex-start",
        transition: "all 0.2s",
    },
    headerInfo: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 12,
        padding: 24,
    },
    symbol: {
        fontSize: 32,
        fontWeight: 700,
        color: "var(--text-primary)",
        marginBottom: 4,
    },
    name: {
        fontSize: 16,
        color: "var(--text-muted)",
    },
    priceBox: {
        textAlign: "right",
    },
    price: {
        fontSize: 36,
        fontWeight: 700,
        color: "var(--text-primary)",
        marginBottom: 8,
    },
    chartContainer: {
        flex: 1,
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 12,
        padding: 20,
        minHeight: 600,
    },
};

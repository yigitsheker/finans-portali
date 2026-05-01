import React, { useState, useEffect } from 'react';
import { 
    getExchangeRates, 
    getInvestmentFunds, 
    getFundTypes,
    type ExchangeRate, 
    type InvestmentFund 
} from '../api/portfolioApi';

const MarketData: React.FC = () => {
    const [activeTab, setActiveTab] = useState<'exchange' | 'funds'>('exchange');
    const [exchangeRates, setExchangeRates] = useState<ExchangeRate[]>([]);
    const [funds, setFunds] = useState<InvestmentFund[]>([]);
    const [fundTypes, setFundTypes] = useState<string[]>([]);
    const [selectedFundType, setSelectedFundType] = useState<string>('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            setLoading(true);
            setError(null);
            const [ratesData, fundsData, typesData] = await Promise.all([
                getExchangeRates(),
                getInvestmentFunds(),
                getFundTypes()
            ]);
            
            setExchangeRates(ratesData);
            setFunds(fundsData);
            setFundTypes(typesData);
        } catch (error) {
            console.error('Error loading market data:', error);
            setError('Veriler yüklenirken bir hata oluştu. Lütfen daha sonra tekrar deneyin.');
        } finally {
            setLoading(false);
        }
    };

    const filteredFunds = selectedFundType 
        ? funds.filter(fund => fund.fundType === selectedFundType)
        : funds;

    const formatCurrency = (value: number) => {
        return '₺' + new Intl.NumberFormat('tr-TR', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 4
        }).format(value);
    };

    const formatPercent = (value?: number) => {
        if (value === undefined || value === null) return '-';
        const color = value >= 0 ? '#10b981' : '#ef4444';
        const sign = value >= 0 ? '▲' : '▼';
        return <span style={{ color }}>{sign} {value >= 0 ? '+' : ''}{value.toFixed(2)}%</span>;
    };

    const formatLargeNumber = (value: number) => {
        if (value >= 1000000) {
            return (value / 1000000).toFixed(1) + 'M ₺';
        }
        return new Intl.NumberFormat('tr-TR', {
            minimumFractionDigits: 0,
            maximumFractionDigits: 0
        }).format(value) + ' ₺';
    };

    if (loading) {
        return (
            <div style={s.loading}>
                <div style={s.spinner}></div>
                <div style={{ color: "var(--text-muted)", marginTop: 12 }}>Yükleniyor...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div style={s.error}>
                <div style={{ fontSize: 48, marginBottom: 16 }}>⚠️</div>
                <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 8 }}>Bir Hata Oluştu</div>
                <div style={{ color: "var(--text-muted)", fontSize: 13, marginBottom: 16 }}>{error}</div>
                <button style={s.retryBtn} onClick={loadData}>
                    Tekrar Dene
                </button>
            </div>
        );
    }

    return (
        <div style={s.root}>
            {/* Tab Buttons */}
            <div style={s.tabContainer}>
                <button
                    style={{
                        ...s.tabBtn,
                        ...(activeTab === 'exchange' ? s.tabActive : {}),
                    }}
                    onClick={() => setActiveTab('exchange')}
                >
                    💱 Döviz Kurları
                </button>
                <button
                    style={{
                        ...s.tabBtn,
                        ...(activeTab === 'funds' ? s.tabActive : {}),
                    }}
                    onClick={() => setActiveTab('funds')}
                >
                    📊 Yatırım Fonları
                </button>
            </div>

            {/* Exchange Rates Table */}
            {activeTab === 'exchange' && (
                <div style={s.tableContainer}>
                    {/* Table Header */}
                    <div style={{ ...s.tableHeader, ...s.tableHeaderExchange }}>
                        <div style={s.colCurrency}>Para Birimi</div>
                        <div style={s.colRate}>Alış</div>
                        <div style={s.colRate}>Satış</div>
                        <div style={s.colRate}>Efektif Alış</div>
                        <div style={s.colRate}>Efektif Satış</div>
                        <div style={s.colSource}>Kaynak</div>
                    </div>

                    {/* Table Body */}
                    <div style={s.tableBody}>
                        {exchangeRates.length === 0 ? (
                            <div style={s.emptyState}>
                                <div style={{ fontSize: 48, marginBottom: 12 }}>💱</div>
                                <div style={{ color: "var(--text-muted)", fontSize: 13 }}>
                                    Henüz döviz kuru verisi bulunmuyor.
                                </div>
                            </div>
                        ) : (
                            exchangeRates.map((rate) => (
                                <div key={rate.id} style={{ ...s.tableRow, ...s.tableRowExchange }}>
                                    <div style={s.colCurrency}>
                                        <div style={s.currencyIcon}>
                                            {rate.currencyCode.substring(0, 2)}
                                        </div>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                                            <div style={s.currencyCode}>{rate.currencyCode}</div>
                                            <div style={s.currencyName}>{rate.currencyName}</div>
                                        </div>
                                    </div>
                                    <div style={s.colRate}>
                                        <div style={s.rateValue}>{formatCurrency(rate.buyingRate)}</div>
                                    </div>
                                    <div style={s.colRate}>
                                        <div style={s.rateValue}>{formatCurrency(rate.sellingRate)}</div>
                                    </div>
                                    <div style={s.colRate}>
                                        <div style={s.rateValueSecondary}>{formatCurrency(rate.effectiveBuyingRate)}</div>
                                    </div>
                                    <div style={s.colRate}>
                                        <div style={s.rateValueSecondary}>{formatCurrency(rate.effectiveSellingRate)}</div>
                                    </div>
                                    <div style={s.colSource}>
                                        <span style={s.sourceBadge}>{rate.source}</span>
                                    </div>
                                </div>
                            ))
                        )}
                    </div>
                </div>
            )}

            {/* Investment Funds Table */}
            {activeTab === 'funds' && (
                <div style={s.tableContainer}>
                    {/* Filter */}
                    <div style={s.filterContainer}>
                        <select
                            value={selectedFundType}
                            onChange={(e) => setSelectedFundType(e.target.value)}
                            style={s.filterSelect}
                        >
                            <option value="">Tüm Fon Türleri</option>
                            {fundTypes.map((type) => (
                                <option key={type} value={type}>
                                    {type}
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Table Header */}
                    <div style={{ ...s.tableHeader, ...s.tableHeaderFunds }}>
                        <div style={s.colFund}>Fon Bilgileri</div>
                        <div style={s.colPrice}>Birim Fiyat</div>
                        <div style={s.colReturn}>Günlük</div>
                        <div style={s.colReturn}>Aylık</div>
                        <div style={s.colReturn}>Yıllık</div>
                        <div style={s.colRisk}>Risk</div>
                        <div style={s.colValue}>Toplam Değer</div>
                    </div>

                    {/* Table Body */}
                    <div style={s.tableBody}>
                        {filteredFunds.length === 0 ? (
                            <div style={s.emptyState}>
                                <div style={{ fontSize: 48, marginBottom: 12 }}>📊</div>
                                <div style={{ color: "var(--text-muted)", fontSize: 13 }}>
                                    {selectedFundType 
                                        ? `${selectedFundType} türünde fon bulunmuyor.`
                                        : 'Henüz yatırım fonu verisi bulunmuyor.'
                                    }
                                </div>
                            </div>
                        ) : (
                            filteredFunds.map((fund) => (
                                <div key={fund.id} style={{ ...s.tableRow, ...s.tableRowFunds }}>
                                    <div style={s.colFund}>
                                        <div style={s.fundCode}>{fund.fundCode}</div>
                                        <div style={s.fundName}>{fund.fundName}</div>
                                        <div style={s.fundCompany}>{fund.managementCompany}</div>
                                    </div>
                                    <div style={s.colPrice}>
                                        <div style={s.priceValue}>{formatCurrency(fund.unitPrice)}</div>
                                    </div>
                                    <div style={s.colReturn}>
                                        <div style={{ fontSize: 13, fontWeight: 600 }}>
                                            {formatPercent(fund.dailyReturn)}
                                        </div>
                                    </div>
                                    <div style={s.colReturn}>
                                        <div style={{ fontSize: 13, fontWeight: 600 }}>
                                            {formatPercent(fund.monthlyReturn)}
                                        </div>
                                    </div>
                                    <div style={s.colReturn}>
                                        <div style={{ fontSize: 13, fontWeight: 600 }}>
                                            {formatPercent(fund.yearlyReturn)}
                                        </div>
                                    </div>
                                    <div style={s.colRisk}>
                                        {fund.riskLevel && (
                                            <span style={{
                                                ...s.riskBadge,
                                                ...(fund.riskLevel === 'DÜŞÜK' ? s.riskLow :
                                                    fund.riskLevel === 'ORTA' ? s.riskMedium :
                                                    s.riskHigh)
                                            }}>
                                                {fund.riskLevel}
                                            </span>
                                        )}
                                    </div>
                                    <div style={s.colValue}>
                                        <div style={s.valueAmount}>{formatLargeNumber(fund.totalValue)}</div>
                                    </div>
                                </div>
                            ))
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};

const s: Record<string, React.CSSProperties> = {
    root: { display: "flex", flexDirection: "column", gap: 16 },
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
    error: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: 400,
        color: "var(--text-primary)",
    },
    retryBtn: {
        padding: "10px 20px",
        borderRadius: 8,
        border: "none",
        background: "#10b981",
        color: "#000",
        fontSize: 14,
        fontWeight: 700,
        cursor: "pointer",
        transition: "all 0.2s",
    },
    tabContainer: {
        display: "flex",
        gap: 12,
        marginBottom: 8,
    },
    tabBtn: {
        padding: "10px 24px",
        borderRadius: 8,
        border: "1px solid #374151",
        background: "#1f2937",
        color: "#9ca3af",
        cursor: "pointer",
        fontSize: 14,
        fontWeight: 600,
        transition: "all 0.2s",
    },
    tabActive: {
        border: "1px solid #10b981",
        background: "rgba(16, 185, 129, 0.15)",
        color: "#10b981",
    },
    tableContainer: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        overflow: "hidden",
    },
    filterContainer: {
        padding: "16px 20px",
        borderBottom: "1px solid var(--border-card)",
        display: "flex",
        justifyContent: "flex-end",
    },
    filterSelect: {
        padding: "8px 16px",
        borderRadius: 6,
        border: "1px solid #374151",
        background: "#1f2937",
        color: "var(--text-primary)",
        fontSize: 13,
        fontWeight: 500,
        cursor: "pointer",
        outline: "none",
    },
    tableHeader: {
        display: "grid",
        gap: 16,
        padding: "14px 20px",
        background: "var(--bg-panel)",
        borderBottom: "1px solid var(--border-card)",
        fontSize: 11,
        fontWeight: 600,
        color: "var(--text-muted)",
        textTransform: "uppercase",
    },
    tableHeaderExchange: {
        gridTemplateColumns: "2fr 1fr 1fr 1fr 1fr auto",
    },
    tableHeaderFunds: {
        gridTemplateColumns: "2.5fr 1fr 1fr 1fr 1fr auto 1fr",
    },
    tableBody: {
        display: "flex",
        flexDirection: "column",
    },
    tableRow: {
        display: "grid",
        gap: 16,
        padding: "14px 20px",
        borderBottom: "1px solid var(--border-card)",
        cursor: "pointer",
        transition: "background 0.2s",
    },
    tableRowExchange: {
        gridTemplateColumns: "2fr 1fr 1fr 1fr 1fr auto",
    },
    tableRowFunds: {
        gridTemplateColumns: "2.5fr 1fr 1fr 1fr 1fr auto 1fr",
    },
    emptyState: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "60px 20px",
    },
    // Exchange Rate Columns
    colCurrency: { display: "flex", alignItems: "center", gap: 12 },
    colRate: { display: "flex", alignItems: "center", justifyContent: "flex-end" },
    colSource: { display: "flex", alignItems: "center", justifyContent: "center" },
    currencyIcon: {
        width: 40,
        height: 40,
        borderRadius: "50%",
        background: "rgba(16, 185, 129, 0.15)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: 14,
        fontWeight: 700,
        color: "#10b981",
        flexShrink: 0,
    },
    currencyCode: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    currencyName: { fontSize: 10, color: "var(--text-muted)" },
    rateValue: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    rateValueSecondary: { fontSize: 12, color: "var(--text-muted)" },
    sourceBadge: {
        padding: "4px 12px",
        borderRadius: 12,
        background: "rgba(59, 130, 246, 0.15)",
        color: "#3b82f6",
        fontSize: 11,
        fontWeight: 600,
    },
    // Investment Fund Columns
    colFund: { display: "flex", flexDirection: "column", gap: 2 },
    colPrice: { display: "flex", alignItems: "center", justifyContent: "flex-end" },
    colReturn: { display: "flex", alignItems: "center", justifyContent: "flex-end" },
    colRisk: { display: "flex", alignItems: "center", justifyContent: "center" },
    colValue: { display: "flex", alignItems: "center", justifyContent: "flex-end" },
    fundCode: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    fundName: { fontSize: 12, color: "var(--text-muted)" },
    fundCompany: { fontSize: 10, color: "var(--text-muted)", marginTop: 2 },
    priceValue: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    valueAmount: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    riskBadge: {
        padding: "4px 12px",
        borderRadius: 12,
        fontSize: 11,
        fontWeight: 600,
    },
    riskLow: {
        background: "rgba(16, 185, 129, 0.15)",
        color: "#10b981",
    },
    riskMedium: {
        background: "rgba(251, 191, 36, 0.15)",
        color: "#fbbf24",
    },
    riskHigh: {
        background: "rgba(239, 68, 68, 0.15)",
        color: "#ef4444",
    },
};

export default MarketData;
import { useState } from "react";
import FinexStyleMarket from "../components/FinexStyleMarket";
import WatchlistManager from "../components/WatchlistManager";
import { useI18n } from "../contexts/I18nContext";

// SVG Icon Components
const StocksIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style={{ display: 'inline-block', verticalAlign: 'middle', marginRight: '6px' }}>
        <path d="M3 17L9 11L13 15L21 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M16 7H21V12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
);

const StarIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style={{ display: 'inline-block', verticalAlign: 'middle', marginRight: '6px' }}>
        <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="#ffaa00"/>
    </svg>
);

export default function Stocks({ keycloak, onAdded }) {
    const { t } = useI18n();
    const [activeTab, setActiveTab] = useState('stocks');

    return (
        <div>
            {/* Tab Navigation */}
            <div style={{
                display: 'flex',
                gap: '10px',
                padding: '20px 20px 0 20px',
                borderBottom: '2px solid var(--border)'
            }}>
                <button
                    onClick={() => setActiveTab('stocks')}
                    style={{
                        padding: '12px 24px',
                        border: 'none',
                        borderBottom: activeTab === 'stocks' ? '3px solid var(--accent-solid)' : '3px solid transparent',
                        background: 'transparent',
                        cursor: 'pointer',
                        fontWeight: activeTab === 'stocks' ? 'bold' : 'normal',
                        color: activeTab === 'stocks' ? 'var(--accent-solid)' : 'var(--text-muted)',
                        fontSize: '16px',
                        transition: 'all 0.2s',
                        display: 'flex',
                        alignItems: 'center'
                    }}
                >
                    <StocksIcon /> {t("stocks.tabStocks")}
                </button>
                <button
                    onClick={() => setActiveTab('watchlist')}
                    style={{
                        padding: '12px 24px',
                        border: 'none',
                        borderBottom: activeTab === 'watchlist' ? '3px solid var(--accent-solid)' : '3px solid transparent',
                        background: 'transparent',
                        cursor: 'pointer',
                        fontWeight: activeTab === 'watchlist' ? 'bold' : 'normal',
                        color: activeTab === 'watchlist' ? 'var(--accent-solid)' : 'var(--text-muted)',
                        fontSize: '16px',
                        transition: 'all 0.2s',
                        display: 'flex',
                        alignItems: 'center'
                    }}
                >
                    <StarIcon /> {t("stocks.tabWatchlists")}
                </button>
            </div>

            {/* Tab Content */}
            <div>
                {activeTab === 'stocks' ? (
                    <FinexStyleMarket keycloak={keycloak} onAdded={onAdded} filterType="STOCK" />
                ) : keycloak.authenticated ? (
                    <WatchlistManager keycloak={keycloak} />
                ) : (
                    <div style={{ padding: 60, textAlign: 'center', maxWidth: 480, margin: '0 auto' }}>
                        <div style={{ fontSize: 56 }}>⭐</div>
                        <h3 style={{ color: 'var(--text-primary)', marginTop: 16 }}>{t("stocks.loginNeeded")}</h3>
                        <p style={{ color: 'var(--text-muted)', lineHeight: 1.6 }}>
                            {t("stocks.loginNeededSub")}
                        </p>
                        <div style={{ display: 'flex', gap: 10, justifyContent: 'center', marginTop: 16, flexWrap: 'wrap' }}>
                            <button
                                onClick={() => keycloak.login({ redirectUri: window.location.href })}
                                style={{
                                    padding: '10px 24px',
                                    borderRadius: 10,
                                    border: '1px solid var(--accent-solid)',
                                    background: 'var(--accent-solid)',
                                    color: '#000',
                                    fontSize: 14,
                                    fontWeight: 700,
                                    cursor: 'pointer',
                                }}
                            >
                                {t("topbar.login")}
                            </button>
                            <button
                                onClick={() => keycloak.register({ redirectUri: window.location.href })}
                                style={{
                                    padding: '10px 24px',
                                    borderRadius: 10,
                                    border: '1px solid var(--border-card)',
                                    background: 'transparent',
                                    color: 'var(--text-primary)',
                                    fontSize: 14,
                                    fontWeight: 600,
                                    cursor: 'pointer',
                                }}
                            >
                                {t("common.register")}
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

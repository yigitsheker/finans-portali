import type Keycloak from "keycloak-js";
import { useEffect, useMemo, useState } from "react";
import Layout from "./components/Layout";
import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";
import FinexStyleMarket from "./components/FinexStyleMarket";
import Portfolio from "./pages/Portfolio";
import Settings from "./pages/Settings";
import MarketData from "./pages/MarketData";
import News from "./pages/News";
import PriceAlertModal from "./components/PriceAlertModal";
import { applyTheme, getStoredTheme, type Theme } from "./theme";
import { ThemeProvider } from "./contexts/ThemeContext";

type Tab = "market" | "portfolio" | "settings" | "market-data" | "news-enhanced";
type Props = { keycloak: Keycloak };

export default function App({ keycloak }: Props) {
    const [tab, setTab] = useState<Tab>("news-enhanced");
    const [portfolioKey, setPortfolioKey] = useState(0);
    const [theme, setTheme] = useState<Theme>(getStoredTheme);
    const [showAlertModal, setShowAlertModal] = useState(false);

    useEffect(() => { applyTheme(theme); }, [theme]);

    function toggleTheme() {
        setTheme((t) => (t === "dark" ? "light" : "dark"));
    }

    const username = useMemo(() => {
        const parsed: any = keycloak.tokenParsed;
        return parsed?.preferred_username ?? parsed?.name ?? parsed?.email ?? "unknown";
    }, [keycloak.tokenParsed]);

    const titles: Record<Tab, string> = {
        market: "Hisse Fiyatları",
        portfolio: "Yatırımlarım",
        settings: "Ayarlar",
        "market-data": "Piyasa Verileri",
        "news-enhanced": "Finans Haberleri",
    };
    const subtitles: Record<Tab, string> = {
        market: "Gerçek zamanlı hisse fiyatları ve piyasa performansı.",
        portfolio: "Portföy değeri, dağılım ve pozisyonlar.",
        settings: "Hesap ayarlarınızı ve tercihlerinizi yönetin.",
        "market-data": "Döviz kurları, yatırım fonları ve piyasa verileri.",
        "news-enhanced": "Kategorize edilmiş finans haberleri ve analiz.",
    };

    return (
        <ThemeProvider>
            <Layout
                sidebar={
                    <Sidebar
                        tab={tab}
                        onTabChange={(t) => setTab(t as Tab)}
                        keycloak={keycloak}
                    />
                }
                topbar={
                    tab !== "market" ? (
                        <Topbar
                            title={titles[tab]}
                            subtitle={subtitles[tab]}
                            username={username}
                            theme={theme}
                            onThemeToggle={toggleTheme}
                            onLogout={() => keycloak.logout({ redirectUri: window.location.origin })}
                            showAlerts={keycloak.authenticated}
                            onAlertsClick={() => setShowAlertModal(true)}
                        />
                    ) : undefined
                }
            >
                {tab === "news-enhanced" && <News />}
                {tab === "market" && (
                    <FinexStyleMarket
                        keycloak={keycloak}
                        onAdded={() => setPortfolioKey((k) => k + 1)}
                        username={username}
                        theme={theme}
                        onThemeToggle={toggleTheme}
                        onLogout={() => keycloak.logout({ redirectUri: window.location.origin })}
                        onAlertsClick={() => setShowAlertModal(true)}
                    />
                )}
                {tab === "market-data" && <MarketData />}
                {tab === "portfolio" && (
                    <Portfolio key={portfolioKey} keycloak={keycloak} />
                )}
                {tab === "settings" && (
                    <Settings keycloak={keycloak} theme={theme} onThemeChange={setTheme} />
                )}
                
                {/* Global Price Alert Modal */}
                {showAlertModal && (
                    <PriceAlertModal
                        open={showAlertModal}
                        onClose={() => setShowAlertModal(false)}
                        keycloak={keycloak}
                    />
                )}
            </Layout>
        </ThemeProvider>
    );
}

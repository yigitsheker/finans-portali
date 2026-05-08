import type Keycloak from "keycloak-js";
import { useEffect, useMemo, useState } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import Layout from "./components/Layout";
import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";
import FinexStyleMarket from "./components/FinexStyleMarket";
import Portfolio from "./pages/Portfolio";
import Settings from "./pages/Settings";
import MarketData from "./pages/MarketData";
import News from "./pages/News";
import Admin from "./pages/Admin";
import PriceAlertModal from "./components/PriceAlertModal";
import { applyTheme, getStoredTheme, type Theme } from "./theme";
import { ThemeProvider } from "./contexts/ThemeContext";

type Props = { keycloak: Keycloak };

export default function App({ keycloak }: Props) {
    const location = useLocation();
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

    // Map routes to titles and subtitles
    const pageInfo: Record<string, { title: string; subtitle: string; hideTopbar?: boolean }> = {
        "/news": {
            title: "Finans Haberleri",
            subtitle: "Kategorize edilmiş finans haberleri ve analiz.",
        },
        "/market": {
            title: "Hisse Fiyatları",
            subtitle: "Gerçek zamanlı hisse fiyatları ve piyasa performansı.",
            hideTopbar: true,
        },
        "/market-data": {
            title: "Piyasa Verileri",
            subtitle: "Döviz kurları, yatırım fonları ve piyasa verileri.",
        },
        "/portfolio": {
            title: "Yatırımlarım",
            subtitle: "Portföy değeri, dağılım ve pozisyonlar.",
        },
        "/settings": {
            title: "Ayarlar",
            subtitle: "Hesap ayarlarınızı ve tercihlerinizi yönetin.",
        },
        "/admin": {
            title: "Yönetim Paneli",
            subtitle: "Sistem yönetimi ve veri yönetimi işlemleri.",
        },
    };

    const currentPage = pageInfo[location.pathname] || pageInfo["/news"];

    return (
        <ThemeProvider>
            <Layout
                sidebar={<Sidebar keycloak={keycloak} />}
                topbar={
                    !currentPage.hideTopbar ? (
                        <Topbar
                            title={currentPage.title}
                            subtitle={currentPage.subtitle}
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
                <Routes>
                    <Route path="/" element={<Navigate to="/news" replace />} />
                    <Route path="/news" element={<News />} />
                    <Route
                        path="/market"
                        element={
                            <FinexStyleMarket
                                keycloak={keycloak}
                                onAdded={() => setPortfolioKey((k) => k + 1)}
                                theme={theme}
                                onThemeToggle={toggleTheme}
                                onLogout={() => keycloak.logout({ redirectUri: window.location.origin })}
                                onAlertsClick={() => setShowAlertModal(true)}
                            />
                        }
                    />
                    <Route path="/market-data" element={<MarketData />} />
                    <Route
                        path="/portfolio"
                        element={<Portfolio key={portfolioKey} keycloak={keycloak} />}
                    />
                    <Route
                        path="/settings"
                        element={<Settings keycloak={keycloak} theme={theme} onThemeChange={setTheme} />}
                    />
                    <Route path="/admin" element={<Admin keycloak={keycloak} />} />
                </Routes>

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

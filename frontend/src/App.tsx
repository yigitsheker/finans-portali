import type Keycloak from "keycloak-js";
import { useEffect, useMemo, useState } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import Layout from "./components/Layout";
import Sidebar from "./components/Sidebar";
import Topbar from "./components/Topbar";
import Stocks from "./pages/Stocks";
import Crypto from "./pages/Crypto";
import Funds from "./pages/Funds";
import Bonds from "./pages/Bonds";
import Portfolio from "./pages/Portfolio";
import HistoricalComparison from "./pages/HistoricalComparison";
import Settings from "./pages/Settings";
import MarketData from "./pages/MarketData";
import News from "./pages/News";
import NewsDetail from "./pages/NewsDetail";
import Admin from "./pages/Admin";
import PriceAlertModal from "./components/PriceAlertModal";
import { applyTheme, getStoredTheme, type Theme } from "./theme";
import { ThemeProvider } from "./contexts/ThemeContext";

type Props = { keycloak: Keycloak };

/**
 * Login gerektiren sayfalar için sarmalayıcı. Auth yoksa kullanıcıyı login flow'una
 * gönderiyor (Keycloak'ta giriş yapınca aynı sayfaya geri döner).
 */
function RequireAuth({
    keycloak,
    children,
}: {
    keycloak: Keycloak;
    children: React.ReactNode;
}) {
    if (!keycloak.authenticated) {
        return (
            <div style={gateStyles.wrap}>
                <div style={gateStyles.icon}>🔒</div>
                <h2 style={gateStyles.title}>Giriş gerekli</h2>
                <p style={gateStyles.text}>
                    Bu sayfayı görüntülemek için hesabınıza giriş yapmanız gerekiyor.
                </p>
                <div style={gateStyles.buttons}>
                    <button
                        style={gateStyles.primary}
                        onClick={() =>
                            keycloak.login({ redirectUri: window.location.href })
                        }
                    >
                        Giriş Yap
                    </button>
                    <button
                        style={gateStyles.secondary}
                        onClick={() =>
                            keycloak.register({ redirectUri: window.location.href })
                        }
                    >
                        Kayıt Ol
                    </button>
                </div>
            </div>
        );
    }
    return <>{children}</>;
}

export default function App({ keycloak }: Props) {
    const location = useLocation();
    const [portfolioKey, setPortfolioKey] = useState(0);
    const [theme, setTheme] = useState<Theme>(getStoredTheme);
    const [showAlertModal, setShowAlertModal] = useState(false);

    useEffect(() => {
        applyTheme(theme);
    }, [theme]);

    function toggleTheme() {
        setTheme((t) => (t === "dark" ? "light" : "dark"));
    }

    const username = useMemo(() => {
        const parsed: any = keycloak.tokenParsed;
        return (
            parsed?.preferred_username ??
            parsed?.name ??
            parsed?.email ??
            "Misafir"
        );
    }, [keycloak.tokenParsed]);

    const isAuthenticated = !!keycloak.authenticated;

    // Map routes to titles and subtitles
    const pageInfo: Record<
        string,
        { title: string; subtitle: string; hideTopbar?: boolean }
    > = {
        "/news": {
            title: "Anasayfa",
            subtitle: "Piyasalar, ekonomi ve yatırım dünyasından son haberler.",
        },
        "/stocks": {
            title: "Hisse Senetleri",
            subtitle: "Gerçek zamanlı hisse fiyatları ve piyasa performansı.",
            hideTopbar: true,
        },
        "/crypto": {
            title: "Kripto Paralar",
            subtitle: "Kripto para fiyatları ve piyasa verileri.",
            hideTopbar: true,
        },
        "/funds": {
            title: "Yatırım Fonları",
            subtitle: "Yatırım fonu fiyatları ve performans verileri.",
            hideTopbar: true,
        },
        "/bonds": {
            title: "Tahvil ve Bono",
            subtitle: "Devlet tahvilleri, hazine bonoları ve borçlanma araçları.",
        },
        "/market-data": {
            title: "Döviz Kurları",
            subtitle: "Güncel döviz kurları ve çapraz kurlar.",
        },
        "/portfolio": {
            title: "Yatırımlarım",
            subtitle: "Portföy değeri, dağılım ve pozisyonlar.",
        },
        "/historical": {
            title: "Geçmişten Bugüne Değişim",
            subtitle:
                "Geçmiş alımlarınızı takip edin ve toplam kar/zarar hesaplayın.",
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

    const isNewsDetail = location.pathname.startsWith("/news/");
    const currentPage = isNewsDetail
        ? { title: "", subtitle: "", hideTopbar: true }
        : pageInfo[location.pathname] || pageInfo["/news"];

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
                            isAuthenticated={isAuthenticated}
                            theme={theme}
                            onThemeToggle={toggleTheme}
                            onLogin={() =>
                                keycloak.login({
                                    redirectUri: window.location.href,
                                })
                            }
                            onRegister={() =>
                                keycloak.register({
                                    redirectUri: window.location.href,
                                })
                            }
                            onLogout={() =>
                                keycloak.logout({
                                    redirectUri: window.location.origin,
                                })
                            }
                            showAlerts={isAuthenticated}
                            onAlertsClick={() => setShowAlertModal(true)}
                        />
                    ) : undefined
                }
            >
                <Routes>
                    <Route path="/" element={<Navigate to="/news" replace />} />
                    <Route path="/news" element={<News keycloak={keycloak} />} />
                    <Route path="/news/:id" element={<NewsDetail />} />
                    <Route
                        path="/stocks"
                        element={
                            <Stocks
                                keycloak={keycloak}
                                onAdded={() => setPortfolioKey((k) => k + 1)}
                            />
                        }
                    />
                    <Route
                        path="/crypto"
                        element={
                            <Crypto
                                keycloak={keycloak}
                                onAdded={() => setPortfolioKey((k) => k + 1)}
                            />
                        }
                    />
                    <Route
                        path="/funds"
                        element={
                            <Funds
                                keycloak={keycloak}
                                onAdded={() => setPortfolioKey((k) => k + 1)}
                            />
                        }
                    />
                    <Route path="/bonds" element={<Bonds keycloak={keycloak} />} />
                    <Route path="/market-data" element={<MarketData />} />
                    <Route
                        path="/portfolio"
                        element={
                            <RequireAuth keycloak={keycloak}>
                                <Portfolio key={portfolioKey} keycloak={keycloak} />
                            </RequireAuth>
                        }
                    />
                    <Route
                        path="/historical"
                        element={
                            <RequireAuth keycloak={keycloak}>
                                <HistoricalComparison keycloak={keycloak} />
                            </RequireAuth>
                        }
                    />
                    <Route
                        path="/settings"
                        element={
                            <RequireAuth keycloak={keycloak}>
                                <Settings
                                    keycloak={keycloak}
                                    theme={theme}
                                    onThemeChange={setTheme}
                                />
                            </RequireAuth>
                        }
                    />
                    <Route
                        path="/admin"
                        element={
                            <RequireAuth keycloak={keycloak}>
                                <Admin keycloak={keycloak} />
                            </RequireAuth>
                        }
                    />
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

const gateStyles: Record<string, React.CSSProperties> = {
    wrap: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        textAlign: "center",
        padding: "80px 24px",
        gap: 16,
        maxWidth: 480,
        margin: "0 auto",
    },
    icon: { fontSize: 56 },
    title: {
        margin: 0,
        fontSize: 24,
        fontWeight: 700,
        color: "var(--text-primary)",
    },
    text: {
        margin: 0,
        fontSize: 14,
        color: "var(--text-muted)",
        lineHeight: 1.6,
    },
    buttons: {
        display: "flex",
        gap: 10,
        marginTop: 8,
        flexWrap: "wrap",
        justifyContent: "center",
    },
    primary: {
        padding: "10px 24px",
        borderRadius: 10,
        border: "1px solid var(--accent-solid)",
        background: "var(--accent-solid)",
        color: "#000",
        cursor: "pointer",
        fontSize: 14,
        fontWeight: 700,
    },
    secondary: {
        padding: "10px 24px",
        borderRadius: 10,
        border: "1px solid var(--border-card)",
        background: "transparent",
        color: "var(--text-primary)",
        cursor: "pointer",
        fontSize: 14,
        fontWeight: 600,
    },
};

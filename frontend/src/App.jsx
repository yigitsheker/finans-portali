import { useEffect, useMemo, useRef, useState, lazy, Suspense } from "react";
import { IconLock } from "./components/common/icons";
import { Routes, Route, useLocation } from "react-router-dom";
import Layout from "./components/Layout";
import Topbar from "./components/Topbar";
import Ticker from "./components/Ticker";
// Pages are code-split so the initial bundle no longer ships every page (and
// every chart library) at once — each route's JS is fetched on first visit.
// This collapses the ~1.6 MB single bundle into a small shell + per-route
// chunks, and the heavy chart libs (recharts/lightweight-charts/klinecharts)
// only download when a page that uses them is actually opened.
const Stocks = lazy(() => import("./pages/Stocks"));
const Watchlists = lazy(() => import("./pages/Watchlists"));
const Crypto = lazy(() => import("./pages/Crypto"));
const Funds = lazy(() => import("./pages/Funds"));
const Bonds = lazy(() => import("./pages/Bonds"));
const Portfolio = lazy(() => import("./pages/Portfolio"));
const HistoricalComparison = lazy(() => import("./pages/HistoricalComparison"));
const Settings = lazy(() => import("./pages/Settings"));
const MarketData = lazy(() => import("./pages/MarketData"));
const Home = lazy(() => import("./pages/Home"));
const News = lazy(() => import("./pages/News"));
const NewsDetail = lazy(() => import("./pages/NewsDetail"));
const Admin = lazy(() => import("./pages/Admin"));
const Inflation = lazy(() => import("./pages/Inflation"));
const Commodities = lazy(() => import("./pages/Commodities"));
const Viop = lazy(() => import("./pages/Viop"));
const Analysis = lazy(() => import("./pages/Analysis"));
import ScrollToTop from "./components/common/ScrollToTop";
import PriceAlertModal from "./components/PriceAlertModal";
import { applyTheme, getStoredTheme } from "./theme";
import { ThemeProvider } from "./contexts/ThemeContext";
import { CurrencyDisplayProvider } from "./contexts/CurrencyDisplayContext";
import { I18nProvider } from "./contexts/I18nContext";
import { Toaster } from "react-hot-toast";
import notify from "./utils/notify";
import { isAdmin } from "./utils/roleUtils";

/**
 * Login gerektiren sayfalar için sarmalayıcı. Auth yoksa kullanıcıyı login flow'una
 * gönderiyor (Keycloak'ta giriş yapınca aynı sayfaya geri döner).
 */
function RequireAuth({ keycloak, children }) {
    if (!keycloak.authenticated) {
        return (
            <div style={gateStyles.wrap}>
                <div style={gateStyles.icon}><IconLock size={40} /></div>
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

// Centered fallback shown while a lazily-loaded route chunk downloads.
const routeFallbackStyle = {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    minHeight: "50vh",
};

export default function App({ keycloak }) {
    const location = useLocation();
    const [portfolioKey, setPortfolioKey] = useState(0);
    const [theme, setTheme] = useState(getStoredTheme);
    const [showAlertModal, setShowAlertModal] = useState(false);

    useEffect(() => {
        applyTheme(theme);
    }, [theme]);

    function toggleTheme() {
        setTheme((t) => (t === "dark" ? "light" : "dark"));
    }

    const username = useMemo(() => {
        const parsed = keycloak.tokenParsed;
        return (
            parsed?.preferred_username ??
            parsed?.name ??
            parsed?.email ??
            "Misafir"
        );
    }, [keycloak.tokenParsed]);

    const isAuthenticated = !!keycloak.authenticated;

    // Sign-in security toast — fires exactly once per authentication
    // transition (false → true). A ref tracks the previous state so that
    // token refreshes (which churn `tokenParsed` while staying authenticated)
    // don't re-fire it, and a logout → re-login DOES fire it again. Gated by
    // Settings → Güvenlik Uyarıları. App sits outside I18nProvider so we
    // read the language directly from localStorage here rather than via
    // useI18n(); notify.js does the same trick for its type labels.
    const wasAuthenticatedRef = useRef(false);
    useEffect(() => {
        const prev = wasAuthenticatedRef.current;
        wasAuthenticatedRef.current = isAuthenticated;
        if (!isAuthenticated || prev) return;     // only on false → true edge
        const user = keycloak.tokenParsed?.preferred_username || "user";
        let lang = "tr";
        try {
            const stored = (localStorage.getItem("i18n-lang") || "").toLowerCase();
            if (stored === "en") lang = "en";
        } catch { /* private mode — fall back to Turkish */ }
        const title = lang === "en" ? `Welcome, ${user}` : `Hoş geldin, ${user}`;
        const message = lang === "en"
            ? "You have signed in to your account."
            : "Hesabınıza giriş yapıldı.";
        notify.security(message, { title });
    }, [isAuthenticated, keycloak.tokenParsed]);

    // Map routes to titles and subtitles
    const pageInfo = {
        "/news": {
            title: "Anasayfa",
            subtitle: "Piyasalar, ekonomi ve yatırım dünyasından son haberler.",
        },
        "/stocks": {
            title: "Hisse Senetleri",
            subtitle: "Gerçek zamanlı hisse fiyatları ve piyasa performansı.",
        },
        "/crypto": {
            title: "Kripto Paralar",
            subtitle: "Kripto para fiyatları ve piyasa verileri.",
        },
        "/funds": {
            title: "Yatırım Fonları",
            subtitle: "Yatırım fonu fiyatları ve performans verileri.",
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
        "/inflation": {
            title: "Enflasyon",
            subtitle: "TÜFE / TCMB EVDS3 — Türkiye enflasyon verileri ve tarihsel grafik.",
        },
        "/commodities": {
            title: "Emtia",
            subtitle: "Altın, gümüş, petrol, doğalgaz, bakır, platin — global emtia fiyatları.",
        },
        "/analysis": {
            title: "Analiz",
            subtitle: "Tüm enstrümanların kısa ve uzun vadeli analizi + Finans Portalı AI chatbot.",
        },
    };

    const isNewsDetail = location.pathname.startsWith("/news/");
    const currentPage = isNewsDetail
        ? { title: "", subtitle: "", hideTopbar: true }
        : pageInfo[location.pathname] || pageInfo["/news"];

    return (
        <ThemeProvider>
            <I18nProvider>
            <CurrencyDisplayProvider>
            <Layout
                ticker={<Ticker keycloak={keycloak} />}
                topbar={
                    !currentPage.hideTopbar ? (
                        <Topbar
                            keycloak={keycloak}
                            username={username}
                            isAuthenticated={isAuthenticated}
                            theme={theme}
                            onThemeToggle={toggleTheme}
                            onLogin={() =>
                                keycloak.login({
                                    redirectUri: window.location.href,
                                })
                            }
                            onLogout={() =>
                                keycloak.logout({
                                    redirectUri: window.location.origin,
                                })
                            }
                            // Hide the price-alarms button for admin users:
                            // alerts are a per-user trading feature, and the bell
                            // for admins would just surface stale system-testing data.
                            showAlerts={isAuthenticated && !isAdmin(keycloak)}
                            onAlertsClick={() => setShowAlertModal(true)}
                        />
                    ) : undefined
                }
            >
                <Suspense fallback={<div style={routeFallbackStyle}><div className="fp-route-spinner" /></div>}>
                <Routes>
                    <Route path="/" element={<Home keycloak={keycloak} />} />
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
                    <Route
                        path="/bonds"
                        element={
                            <Bonds
                                keycloak={keycloak}
                                onAdded={() => setPortfolioKey((k) => k + 1)}
                            />
                        }
                    />
                    <Route
                        path="/market-data"
                        element={
                            <MarketData
                                keycloak={keycloak}
                                onAdded={() => setPortfolioKey((k) => k + 1)}
                            />
                        }
                    />
                    <Route path="/inflation" element={<Inflation />} />
                    <Route
                        path="/viop"
                        element={
                            <Viop
                                keycloak={keycloak}
                                onAdded={() => setPortfolioKey((k) => k + 1)}
                            />
                        }
                    />
                    <Route
                        path="/commodities"
                        element={
                            <Commodities
                                keycloak={keycloak}
                                onAdded={() => setPortfolioKey((k) => k + 1)}
                            />
                        }
                    />
                    <Route
                        path="/analysis"
                        element={
                            <RequireAuth keycloak={keycloak}>
                                <Analysis keycloak={keycloak} />
                            </RequireAuth>
                        }
                    />
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
                        path="/lists"
                        element={
                            <RequireAuth keycloak={keycloak}>
                                <Watchlists keycloak={keycloak} />
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
                </Suspense>

                {/* Global Price Alert Modal */}
                {showAlertModal && (
                    <PriceAlertModal
                        open={showAlertModal}
                        onClose={() => setShowAlertModal(false)}
                        keycloak={keycloak}
                    />
                )}
            </Layout>
            {/* Floating "scroll to top" — appears on every page once the
                user has scrolled ~400px down. Lives outside <Layout> so it
                stays pinned to the viewport regardless of page padding. */}
            <ScrollToTop />
            {/* Toast notifications — anchored just below the navbar (ticker 40px
                + topbar ≈60-70px = ~110px), 5s auto-dismiss. Visual is fully
                handled by the AppToast component via notify.js; we only place
                the Toaster container here. zIndex must beat Modal.jsx's card
                (10000) — react-hot-toast's own default is 9999, same as the
                modal backdrop, which left incoming toasts rendering behind an
                open modal/card instead of on top of it. */}
            <Toaster
                position="top-right"
                gutter={10}
                containerStyle={{ top: 110, right: 16, zIndex: 10500 }}
                toastOptions={{ duration: 5000 }}
            />
            </CurrencyDisplayProvider>
            </I18nProvider>
        </ThemeProvider>
    );
}

const gateStyles = {
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

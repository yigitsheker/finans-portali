import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { useState, useEffect } from "react";
import "./index.css";
import App from "./App";
import ChartPage from "./pages/ChartPage";
import keycloak from "./auth/keycloak";
import { I18nProvider } from "./contexts/I18nContext";

// AuthGate: holds auth state in React so re-renders happen on login/logout
function AuthGate() {
    const [authenticated, setAuthenticated] = useState(null);

    useEffect(() => {
        // Register Keycloak event callbacks BEFORE init
        keycloak.onAuthSuccess = () => setAuthenticated(true);
        keycloak.onAuthError = () => setAuthenticated(false);
        keycloak.onAuthLogout = () => setAuthenticated(false);
        keycloak.onTokenExpired = () => {
            keycloak.updateToken(30).catch(() => setAuthenticated(false));
        };

        keycloak
            .init({
                // check-sso: her sayfa yüklemesinde (yenileme dahil) mevcut Keycloak
                // SSO oturumunu sessizce kontrol et. Oturum varsa kullanıcı otomatik
                // olarak authenticate olur — F5'te "giriş yapmamış gibi" atılmaz.
                // Kontrol gizli bir iframe ile (silentCheckSsoRedirectUri) yapılır,
                // görünür bir yönlendirme/flash olmaz. Oturum yoksa anonim başlar ve
                // "Giriş Yap"a basınca normal akış çalışır.
                onLoad: "check-sso",
                silentCheckSsoRedirectUri: window.location.origin + "/silent-check-sso.html",
                pkceMethod: "S256",
                // Sürekli oturum-durumu izleme iframe'ini kapalı tut (3rd-party cookie
                // sorunlarından kaçınır); ilk SSO kontrolü silent redirect ile yapılır.
                checkLoginIframe: false,
            })
            .then((auth) => {
                setAuthenticated(auth);
            })
            .catch((err) => {
                console.error("Keycloak init error:", err);
                setAuthenticated(false);
            });
    }, []);

    // Still initializing
    if (authenticated === null) {
        return (
            <div style={{
                minHeight: "100vh",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                background: "#080b08",
            }}>
                <div style={{
                    width: 36,
                    height: 36,
                    border: "3px solid rgba(34,197,94,0.2)",
                    borderTop: "3px solid #22c55e",
                    borderRadius: "50%",
                    animation: "spin 0.8s linear infinite",
                }} />
            </div>
        );
    }

    return (
        <BrowserRouter>
            <Routes>
                {/* /chart opens in its own tab, OUTSIDE App — so it needs its
                    own I18nProvider for NativeChart's labels (t()) to resolve. */}
                <Route path="/chart" element={<I18nProvider><ChartPage /></I18nProvider>} />
                <Route path="/*" element={<App keycloak={keycloak} />} />
            </Routes>
        </BrowserRouter>
    );
}

ReactDOM.createRoot(document.getElementById("root")).render(<AuthGate />);

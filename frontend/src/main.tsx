import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import "./index.css";
import App from "./App.tsx";
import ChartPage from "./pages/ChartPage.tsx";
import LoginPage from "./pages/LoginPage.tsx";
import keycloak from "./auth/keycloak.ts";

keycloak
    .init({
        onLoad: "check-sso",          // Don't auto-redirect — we show our own login page
        pkceMethod: "S256",
        checkLoginIframe: false,
        silentCheckSsoRedirectUri: window.location.origin + "/silent-check-sso.html",
    })
    .then(() => {
        ReactDOM.createRoot(document.getElementById("root")!).render(
            <BrowserRouter>
                <Routes>
                    <Route path="/chart" element={<ChartPage />} />
                    <Route
                        path="/*"
                        element={
                            keycloak.authenticated
                                ? <App keycloak={keycloak} />
                                : <LoginPage keycloak={keycloak} />
                        }
                    />
                </Routes>
            </BrowserRouter>
        );
    })
    .catch((err) => {
        console.error("Keycloak init error:", err);
        // Even on error, render login page so user isn't stuck on blank screen
        ReactDOM.createRoot(document.getElementById("root")!).render(
            <BrowserRouter>
                <Routes>
                    <Route path="/*" element={<LoginPage keycloak={keycloak} />} />
                </Routes>
            </BrowserRouter>
        );
    });
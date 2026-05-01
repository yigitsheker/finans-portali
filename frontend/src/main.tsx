import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import "./index.css";
import App from "./App.tsx";
import ChartPage from "./pages/ChartPage.tsx";
import keycloak from "./auth/keycloak.ts";

keycloak
    .init({
        onLoad: "login-required",
        pkceMethod: "S256",
        checkLoginIframe: false, // dev ortamda gereksiz iframe kontrolünü kapatır, takılmaları azaltır
    })
    .then(() => {
        ReactDOM.createRoot(document.getElementById("root")!).render(
            <BrowserRouter>
                <Routes>
                    <Route path="/chart" element={<ChartPage />} />
                    <Route path="/*" element={<App keycloak={keycloak} />} />
                </Routes>
            </BrowserRouter>
        );
    })
    .catch((err) => {
        console.error("Keycloak init error:", err);
    });
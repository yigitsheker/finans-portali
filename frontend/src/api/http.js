import axios from "axios";

export function createApi(keycloak) {
    const api = axios.create({
        baseURL: "",
        timeout: 15000,
    });

    api.interceptors.request.use(async (config) => {
        // token yenile (gerekirse)
        try {
            await keycloak.updateToken(30);
        } catch {}

        config.headers = config.headers ?? {};

        const token = keycloak.token;
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }

        // Forward the UI language so the backend can localize anything that
        // outlives the request — most importantly, the price-alert email that
        // gets dispatched by the scheduler hours/days later.
        const lang = (localStorage.getItem("i18n-lang") || "tr").toLowerCase();
        config.headers["Accept-Language"] = lang === "en" ? "en" : "tr";

        return config;
    });

    return api;
}

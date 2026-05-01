import axios from "axios";
import type Keycloak from "keycloak-js";

export function createApi(keycloak: Keycloak) {
    const api = axios.create({
        baseURL: "http://localhost:8080",
        timeout: 15000,
    });

    api.interceptors.request.use(async (config) => {
        // token yenile (gerekirse)
        try {
            await keycloak.updateToken(30);
        } catch {}

        const token = keycloak.token;
        if (token) {
            config.headers = config.headers ?? {};
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    });

    return api;
}
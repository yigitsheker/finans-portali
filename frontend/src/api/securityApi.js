import axios from "axios";

// Self-service security (2FA) endpoints under /api/v1/users/me. Mirrors the
// resilient auth-header pattern in userPrefsApi.js: refresh the token best-effort
// (Settings mounts while keycloak may still be refreshing) and forward language.
async function authHeader(keycloak) {
    try {
        await keycloak.updateToken(30);
    } catch { /* token refresh failed — fall through with whatever we have */ }
    const lang = (localStorage.getItem("i18n-lang") || "tr").toLowerCase();
    const headers = { "Accept-Language": lang === "en" ? "en" : "tr" };
    if (keycloak.token) {
        headers.Authorization = `Bearer ${keycloak.token}`;
    }
    return headers;
}

/**
 * Returns { totpEnabled: boolean } — whether the user has a TOTP authenticator
 * configured. Keycloak's access token carries no such claim, so this asks the
 * backend (which checks the user's Keycloak credentials via the admin API).
 */
export async function getSecurityStatus(keycloak) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.get("/api/v1/users/me/security", { headers });
    return (data && typeof data === "object") ? data : {};
}

/** Removes the user's TOTP credential(s). Returns { totpEnabled: false }. */
export async function disable2fa(keycloak) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.delete("/api/v1/users/me/2fa", { headers });
    return data;
}

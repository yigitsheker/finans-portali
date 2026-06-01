import axios from "axios";

const BASE = "/api/v1/users/me/notification-prefs";

async function authHeader(keycloak) {
    // Tolerate a not-yet-ready token instead of throwing. This API is called on
    // Settings mount, where keycloak may still be refreshing; throwing here sent
    // the GET without a token → backend 401 ("Could not fetch notification
    // prefs"). Match the resilient pattern http.js uses (swallow refresh errors)
    // and forward the language too, consistent with the other API modules.
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
 * Returns the user's notification-toggle map persisted on the backend
 * (empty object for a first-time user). The Settings page hydrates from
 * this in addition to its localStorage mirror so the choice survives
 * across browsers / devices.
 */
export async function getNotificationPrefs(keycloak) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.get(BASE, { headers });
    return (data && typeof data === "object") ? data : {};
}

/**
 * Upsert the user's full notification map. Frontend sends the entire
 * map (not a diff) so the backend's PUT semantics stay simple — last
 * write wins, no merge ambiguity.
 */
export async function putNotificationPrefs(keycloak, prefs) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.put(BASE, prefs, { headers });
    return data;
}

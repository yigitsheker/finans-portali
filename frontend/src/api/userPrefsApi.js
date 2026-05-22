import axios from "axios";

const BASE = "/api/v1/users/me/notification-prefs";

async function authHeader(keycloak) {
    await keycloak.updateToken(30);
    if (!keycloak.token) throw new Error("No access token");
    return { Authorization: `Bearer ${keycloak.token}` };
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

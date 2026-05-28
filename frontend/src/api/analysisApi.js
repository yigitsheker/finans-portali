import axios from "axios";

const API_BASE = "";

async function authHeader(keycloak) {
    await keycloak.updateToken(30);
    if (!keycloak.token) throw new Error("Auth required");
    return { Authorization: `Bearer ${keycloak.token}` };
}

/**
 * Service layer for the Analysis page. All three endpoints require a valid
 * JWT — the page itself is RequireAuth-wrapped so by the time the user
 * lands here keycloak is guaranteed to be authenticated.
 */
export async function getAnalysisInstruments(keycloak) {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/analysis/instruments`, { headers });
    return res.data;
}

export async function getAnalysisDetail(keycloak, symbol) {
    const headers = await authHeader(keycloak);
    const res = await axios.get(
        `${API_BASE}/api/v1/analysis/instruments/${encodeURIComponent(symbol)}`,
        { headers }
    );
    return res.data;
}

export async function sendAnalysisChat(keycloak, message, lang) {
    const headers = await authHeader(keycloak);
    const res = await axios.post(
        `${API_BASE}/api/v1/analysis/chat`,
        { message, lang },
        { headers }
    );
    return res.data;
}

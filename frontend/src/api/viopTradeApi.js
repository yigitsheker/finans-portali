import axios from "axios";

// VİOP (futures) trading — SIMULATION ONLY. Authenticated calls under
// /api/v1/portfolio/viop. Mirrors portfolioApi's resilient auth-header pattern.
async function authHeader(keycloak) {
    try {
        await keycloak.updateToken(30);
    } catch { /* token refresh failed — fall through with whatever we have */ }
    if (!keycloak?.token) throw new Error("No access token");
    const lang = (localStorage.getItem("i18n-lang") || "tr").toLowerCase();
    return {
        Authorization: `Bearer ${keycloak.token}`,
        "Accept-Language": lang === "en" ? "en" : "tr",
    };
}

const BASE = "/api/v1/portfolio/viop";

/** Open a position. body: { contractSymbol, direction: 'LONG'|'SHORT', quantity, price? } */
export async function openViopPosition(keycloak, body) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.post(`${BASE}/positions/open`, body, { headers });
    return data;
}

/** Close/partial-close. body: { contractSymbol, quantity, price? } */
export async function closeViopPosition(keycloak, body) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.post(`${BASE}/positions/close`, body, { headers });
    return data;
}

/** Backend-computed preview (no persistence). Same body shape as open. */
export async function previewViopPosition(keycloak, body) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.post(`${BASE}/preview`, body, { headers });
    return data;
}

export async function getViopPositions(keycloak) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.get(`${BASE}/positions`, { headers });
    return Array.isArray(data) ? data : [];
}

export async function getViopTransactions(keycloak) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.get(`${BASE}/transactions`, { headers });
    return Array.isArray(data) ? data : [];
}

export async function getViopSummary(keycloak) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.get(`${BASE}/summary`, { headers });
    return data;
}

import axios from "axios";

// Bond/bill trading — SIMULATION ONLY. Authenticated calls under
// /api/v1/portfolio/bonds (distinct from the public market-data /api/v1/bonds).
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

const BASE = "/api/v1/portfolio/bonds";

/** body: { identifier (ISIN/symbol), nominal, cleanPrice, accruedInterest?, dirtyPrice? } */
export async function buyBond(keycloak, body) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.post(`${BASE}/buy`, body, { headers });
    return data;
}

/** body: { identifier, nominal, cleanPrice, accruedInterest?, dirtyPrice? } */
export async function sellBond(keycloak, body) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.post(`${BASE}/sell`, body, { headers });
    return data;
}

export async function previewBondBuy(keycloak, body) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.post(`${BASE}/preview/buy`, body, { headers });
    return data;
}

export async function previewBondSell(keycloak, body) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.post(`${BASE}/preview/sell`, body, { headers });
    return data;
}

export async function getBondPositions(keycloak) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.get(`${BASE}/positions`, { headers });
    return Array.isArray(data) ? data : [];
}

export async function getBondTransactions(keycloak) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.get(`${BASE}/transactions`, { headers });
    return Array.isArray(data) ? data : [];
}

export async function getBondPortfolioSummary(keycloak) {
    const headers = await authHeader(keycloak);
    const { data } = await axios.get(`${BASE}/summary`, { headers });
    return data;
}

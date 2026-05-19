import axios from "axios";

const API_BASE = "";

async function authHeader(keycloak) {
    await keycloak.updateToken(30);
    if (!keycloak.token) throw new Error("No access token");
    return { Authorization: `Bearer ${keycloak.token}` };
}

/** PORTFOLIO */
export async function getPositions(keycloak) {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/portfolio/positions`, { headers });
    return res.data;
}

export async function upsertPosition(keycloak, body) {
    const headers = await authHeader(keycloak);
    await axios.post(`${API_BASE}/api/v1/portfolio/positions`, body, { headers });
}

export async function deletePosition(keycloak, symbol) {
    const headers = await authHeader(keycloak);
    await axios.delete(`${API_BASE}/api/v1/portfolio/positions/${encodeURIComponent(symbol)}`, { headers });
}

export async function sellPosition(
    keycloak,
    symbol,
    quantity
) {
    const headers = await authHeader(keycloak);
    const res = await axios.post(
        `${API_BASE}/api/v1/portfolio/positions/sell`,
        { symbol, quantity },
        { headers }
    );
    return res.data;
}

/** MARKET */
export async function getMarketSummary() {
    const res = await axios.get(`${API_BASE}/api/v1/market/summary`);
    return res.data;
}

export async function getMarketInstruments() {
    const res = await axios.get(`${API_BASE}/api/v1/market/instruments`);
    return res.data;
}

export async function searchMarketInstruments(query) {
    const res = await axios.get(`${API_BASE}/api/v1/market/search`, {
        params: { query }
    });
    return res.data;
}

export async function getMarketHistory(symbol, period) {
    const res = await axios.get(`${API_BASE}/api/v1/market/history`, {
        params: { symbol, period },
    });
    return res.data;
}

/**
 * Batch variant — fetch history for many symbols in one HTTP round trip.
 * Collapses N sparkline lookups from N requests into one, cutting page-load
 * latency for instrument lists from "50 × 150ms" to a single request.
 *
 * Returns an object keyed by symbol; missing / failed symbols map to [].
 *
 *   await getMarketHistoryBatch(["THYAO", "GARAN"], "1M")
 *   → { THYAO: [...], GARAN: [...] }
 */
export async function getMarketHistoryBatch(symbols, period = "30D") {
    if (!symbols || symbols.length === 0) return {};
    const res = await axios.get(`${API_BASE}/api/v1/market/history/batch`, {
        params: { symbols: symbols.join(","), period },
    });
    return res.data || {};
}

export async function getLatestPrice(symbol, _keycloak) {
    const res = await axios.get(`${API_BASE}/api/v1/market/price`, {
        params: { symbol: symbol.toUpperCase() },
    });
    return Number(res.data?.price ?? 0);
}

/** PRICE ALERTS */

export async function createPriceAlert(keycloak, request) {
    console.log("[API] Creating price alert:", request);
    console.log("[API] Keycloak authenticated:", keycloak.authenticated);
    console.log("[API] Keycloak token exists:", !!keycloak.token);

    if (!keycloak.authenticated) {
        console.error("[API] User not authenticated");
        throw new Error("User not authenticated");
    }

    const headers = await authHeader(keycloak);
    console.log("[API] Auth headers:", headers);

    try {
        const res = await axios.post(`${API_BASE}/api/v1/alerts`, request, { headers });
        console.log("[API] Create alert response:", res.data);
        return res.data;
    } catch (error) {
        console.error("[API] Create alert error:", error);
        if (error.response) {
            console.error("[API] Error response status:", error.response.status);
            console.error("[API] Error response data:", error.response.data);
            console.error("[API] Error response headers:", error.response.headers);
        }
        throw error;
    }
}

export async function getUserAlerts(keycloak, activeOnly = true) {
    console.log("[API] Getting user alerts, activeOnly:", activeOnly);
    console.log("[API] Keycloak authenticated:", keycloak.authenticated);
    console.log("[API] Keycloak token exists:", !!keycloak.token);

    if (!keycloak.authenticated) {
        console.error("[API] User not authenticated");
        throw new Error("User not authenticated");
    }

    const headers = await authHeader(keycloak);
    console.log("[API] Auth headers:", headers);

    try {
        const res = await axios.get(`${API_BASE}/api/v1/alerts`, {
            headers,
            params: { includeTriggered: !activeOnly }
        });
        console.log("[API] Get alerts response:", res.data);
        return res.data;
    } catch (error) {
        console.error("[API] Get alerts error:", error);
        if (error.response) {
            console.error("[API] Error response status:", error.response.status);
            console.error("[API] Error response data:", error.response.data);
            console.error("[API] Error response headers:", error.response.headers);
        }
        throw error;
    }
}

export async function deletePriceAlert(keycloak, alertId) {
    const headers = await authHeader(keycloak);
    await axios.delete(`${API_BASE}/api/v1/alerts/${alertId}`, { headers });
}

export async function togglePriceAlert(keycloak, alertId) {
    const headers = await authHeader(keycloak);
    await axios.put(`${API_BASE}/api/v1/alerts/${alertId}/toggle`, {}, { headers });
}

export async function triggerAlertManually(keycloak, alertId) {
    const headers = await authHeader(keycloak);
    const res = await axios.post(`${API_BASE}/api/v1/alerts/${alertId}/test`, {}, { headers });
    return res.data;
}

/** PORTFOLIO ALLOCATION */

export async function getPortfolioAllocation(keycloak) {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/portfolio/allocation`, { headers });
    return res.data;
}

export async function getPortfolioAllocationByType(keycloak) {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/portfolio/allocation-by-type`, { headers });
    return res.data;
}

/** PORTFOLIO SUMMARY DETAIL */

export async function getPortfolioSummaryDetail(keycloak) {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/portfolio/summary-detail`, { headers });
    return res.data;
}

/** PORTFOLIO PERFORMANCE */

export async function getPortfolioPerformance(
    keycloak,
    range = "ALL"
) {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/portfolio/performance`, {
        headers,
        params: { range }
    });
    return res.data;
}

/** NEWS */

export async function getNews(category) {
    const res = await axios.get(`${API_BASE}/api/v1/news`, {
        params: category ? { category } : {}
    });
    return res.data;
}

export async function getNewsCategories() {
    const res = await axios.get(`${API_BASE}/api/v1/news/categories`);
    return res.data;
}

export async function getNewsCategoryCounts() {
    const res = await axios.get(`${API_BASE}/api/v1/news/category-counts`);
    return res.data;
}

export async function fetchNewsContent(id) {
    const res = await axios.post(`${API_BASE}/api/v1/news/${id}/fetch-content`);
    return res.data;
}

export async function getNewsById(id) {
    const res = await axios.get(`${API_BASE}/api/v1/news/${id}`);
    return res.data;
}

/** EXCHANGE RATES */

export async function getExchangeRates() {
    const res = await axios.get(`${API_BASE}/api/v1/exchange-rates`);
    return res.data;
}

export async function getExchangeRatesBySource(source) {
    const res = await axios.get(`${API_BASE}/api/v1/exchange-rates/source/${source}`);
    return res.data;
}

export async function getExchangeRateSources() {
    const res = await axios.get(`${API_BASE}/api/v1/exchange-rates/sources`);
    return res.data;
}

/** INVESTMENT FUNDS */

export async function getInvestmentFunds() {
    const res = await axios.get(`${API_BASE}/api/v1/investment-funds`);
    return res.data;
}

export async function getFundTypes() {
    const res = await axios.get(`${API_BASE}/api/v1/investment-funds/types`);
    return res.data;
}

export async function getFundsByType(fundType) {
    const res = await axios.get(`${API_BASE}/api/v1/investment-funds/type/${fundType}`);
    return res.data;
}

export async function searchFunds(query) {
    const res = await axios.get(`${API_BASE}/api/v1/investment-funds/search`, {
        params: { q: query }
    });
    return res.data;
}

export async function refreshInvestmentFunds(token) {
    const res = await axios.post(
        `${API_BASE}/api/v1/investment-funds/admin/refresh`,
        {},
        { headers: { Authorization: `Bearer ${token}` } }
    );
    return res.data;
}

/** TECHNICAL ANALYSIS */

export async function getMovingAverages(symbol, period = 20) {
    const res = await axios.get(`${API_BASE}/api/v1/technical-analysis/${symbol}/moving-averages`, {
        params: { period }
    });
    return res.data;
}

export async function getTrendAnalysis(symbol) {
    const res = await axios.get(`${API_BASE}/api/v1/technical-analysis/${symbol}/trend`);
    return res.data;
}

export async function getSupportResistance(symbol) {
    const res = await axios.get(`${API_BASE}/api/v1/technical-analysis/${symbol}/support-resistance`);
    return res.data;
}


/** TECHNICAL ANALYSIS */

export async function getTechnicalAnalysis(
    symbol,
    from,
    to
) {
    const params = {};
    if (from) params.from = from;
    if (to) params.to = to;

    const res = await axios.get(`${API_BASE}/api/v1/technical-analysis/${encodeURIComponent(symbol)}`, {
        params
    });
    return res.data;
}

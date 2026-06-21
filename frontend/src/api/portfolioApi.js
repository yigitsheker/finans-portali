import axios from "axios";

const API_BASE = "";

async function authHeader(keycloak) {
    await keycloak.updateToken(30);
    if (!keycloak.token) throw new Error("No access token");
    // Forward the UI language so the backend can localize things that outlive
    // the request — most importantly the price-alert email dispatched by the
    // scheduler hours/days later (it snapshots this into price_alerts.language).
    // This module uses its own axios calls rather than the shared http.js
    // client, so the Accept-Language header is added here instead of an
    // interceptor; without it the backend always defaulted alerts to Turkish.
    const lang = (localStorage.getItem("i18n-lang") || "tr").toLowerCase();
    return {
        Authorization: `Bearer ${keycloak.token}`,
        "Accept-Language": lang === "en" ? "en" : "tr",
    };
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

/** Observable buy/sell movement history (newest first). */
export async function getPortfolioTransactions(keycloak) {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/portfolio/transactions`, { headers });
    return res.data;
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
 * FX history pass-through — hits Yahoo via the backend's <CODE>TRY=X
 * synthetic ticker so FX rates (which live in exchange_rates, not
 * market_instruments) can render a chart in the detail modal.
 */
export async function getFxHistory(code, period = "30D") {
    const res = await axios.get(`${API_BASE}/api/v1/market/history/fx`, {
        params: { code, period },
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

// `lang` is the user's current i18n language ("tr" | "en"). It gets passed
// through to NewsService so Turkish-source articles are translated to English
// (and vice-versa) before they reach the frontend. Translations are cached
// on the row, so subsequent calls for the same article hit the DB only.
export async function getNews(category, lang) {
    const params = {};
    if (category) params.category = category;
    if (lang) params.lang = lang;
    const res = await axios.get(`${API_BASE}/api/v1/news`, { params });
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

// News IDs are integer primary keys from the backend. Cast + validate so
// Sonar's taint tracker (S5247) sees a guaranteed-safe primitive before
// it lands in the URL.
function safeNewsId(value) {
    const n = Number(value);
    if (!Number.isFinite(n) || n < 0 || !Number.isInteger(n)) {
        throw new Error('Invalid news id');
    }
    return n;
}

export async function fetchNewsContent(id, lang) {
    const safeId = safeNewsId(id);
    const res = await axios.post(`${API_BASE}/api/v1/news/${safeId}/fetch-content`, null, {
        params: lang ? { lang } : {}
    });
    return res.data;
}

export async function getNewsById(id, lang) {
    const safeId = safeNewsId(id);
    const res = await axios.get(`${API_BASE}/api/v1/news/${safeId}`, {
        params: lang ? { lang } : {}
    });
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

/** HISTORICAL POSITIONS */

export async function getHistoricalPositions(keycloak) {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/historical-positions`, { headers });
    return res.data?.data || [];
}

export async function addHistoricalPosition(keycloak, position) {
    const headers = await authHeader(keycloak);
    const res = await axios.post(`${API_BASE}/api/historical-positions`, position, { headers });
    return res.data?.data;
}

export async function updateHistoricalPosition(keycloak, id, position) {
    const headers = await authHeader(keycloak);
    const res = await axios.put(`${API_BASE}/api/historical-positions/${id}`, position, { headers });
    return res.data?.data;
}

export async function deleteHistoricalPosition(keycloak, id) {
    const headers = await authHeader(keycloak);
    await axios.delete(`${API_BASE}/api/historical-positions/${id}`, { headers });
}

export async function deleteAllHistoricalPositions(keycloak) {
    const headers = await authHeader(keycloak);
    await axios.delete(`${API_BASE}/api/historical-positions`, { headers });
}

import axios from "axios";
import type Keycloak from "keycloak-js";

const API_BASE = "http://localhost:8080";

export type Position = {
    id?: number;
    symbol: string;
    quantity: number;
    avgCost?: number | null;
    userId?: string;
    purchaseDate?: string | null;
};

export type UpsertPositionRequest = {
    symbol: string;
    quantity: number;
    avgCost?: number | null;
};

export type MarketInstrument = {
    id: number;
    symbol: string;
    name: string;
    type: string;
    finnhubSymbol?: string | null;
};

export type MarketSummaryItem = {
    symbol: string;
    name: string;
    type: string;
    last: number;
    changeAbs: number;
    changePct: number;
    asOf: string;
    delayed: boolean;
    delayLabel: string | null;
};

async function authHeader(keycloak: Keycloak) {
    await keycloak.updateToken(30);
    if (!keycloak.token) throw new Error("No access token");
    return { Authorization: `Bearer ${keycloak.token}` };
}

/** PORTFOLIO */
export async function getPositions(keycloak: Keycloak): Promise<Position[]> {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/portfolio/positions`, { headers });
    return res.data;
}

export async function upsertPosition(keycloak: Keycloak, body: UpsertPositionRequest): Promise<void> {
    const headers = await authHeader(keycloak);
    await axios.post(`${API_BASE}/api/v1/portfolio/positions`, body, { headers });
}

export async function deletePosition(keycloak: Keycloak, symbol: string): Promise<void> {
    const headers = await authHeader(keycloak);
    await axios.delete(`${API_BASE}/api/v1/portfolio/positions/${encodeURIComponent(symbol)}`, { headers });
}

export async function sellPosition(
    keycloak: Keycloak,
    symbol: string,
    quantity: number
): Promise<{ proceeds: number }> {
    const headers = await authHeader(keycloak);
    const res = await axios.post(
        `${API_BASE}/api/v1/portfolio/positions/sell`,
        { symbol, quantity },
        { headers }
    );
    return res.data;
}

/** MARKET */
export async function getMarketSummary(): Promise<MarketSummaryItem[]> {
    const res = await axios.get(`${API_BASE}/api/v1/market/summary`);
    return res.data;
}

export async function getMarketInstruments(): Promise<MarketInstrument[]> {
    const res = await axios.get(`${API_BASE}/api/v1/market/instruments`);
    return res.data;
}

export type MarketHistoryPoint = {
    day: string;
    close: number;
    label: string;
};

export async function getMarketHistory(symbol: string, period: string): Promise<MarketHistoryPoint[]> {
    const res = await axios.get(`${API_BASE}/api/v1/market/history`, {
        params: { symbol, period },
    });
    return res.data;
}

export async function getLatestPrice(symbol: string, _keycloak?: Keycloak): Promise<number> {
    const res = await axios.get(`${API_BASE}/api/v1/market/price`, {
        params: { symbol: symbol.toUpperCase() },
    });
    return Number(res.data?.price ?? 0);
}

/** PRICE ALERTS */
export type AlertType = "PRICE_ABOVE" | "PRICE_BELOW" | "PERCENT_GAIN" | "PERCENT_LOSS";

export type CreateAlertRequest = {
    symbol: string;
    alertType: AlertType;
    targetPrice: number;
    note?: string;
};

export type AlertView = {
    id: number;
    symbol: string;
    instrumentName: string;
    alertType: AlertType;
    targetPrice: number;
    creationPrice: number;
    currentPrice: number;
    active: boolean;
    createdAt: string;
    triggeredAt?: string;
    triggeredPrice?: number;
    note?: string;
    status: string;
    progressPercent?: number;
};

export async function createPriceAlert(keycloak: Keycloak, request: CreateAlertRequest): Promise<AlertView> {
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
    } catch (error: any) {
        console.error("[API] Create alert error:", error);
        if (error.response) {
            console.error("[API] Error response status:", error.response.status);
            console.error("[API] Error response data:", error.response.data);
            console.error("[API] Error response headers:", error.response.headers);
        }
        throw error;
    }
}

export async function getUserAlerts(keycloak: Keycloak, activeOnly: boolean = true): Promise<AlertView[]> {
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
    } catch (error: any) {
        console.error("[API] Get alerts error:", error);
        if (error.response) {
            console.error("[API] Error response status:", error.response.status);
            console.error("[API] Error response data:", error.response.data);
            console.error("[API] Error response headers:", error.response.headers);
        }
        throw error;
    }
}

export async function deletePriceAlert(keycloak: Keycloak, alertId: number): Promise<void> {
    const headers = await authHeader(keycloak);
    await axios.delete(`${API_BASE}/api/v1/alerts/${alertId}`, { headers });
}

export async function togglePriceAlert(keycloak: Keycloak, alertId: number): Promise<void> {
    const headers = await authHeader(keycloak);
    await axios.put(`${API_BASE}/api/v1/alerts/${alertId}/toggle`, {}, { headers });
}

/** PORTFOLIO ALLOCATION */
export type AllocationItem = {
    symbol: string;
    marketValue: number;
    percentage: number;
};

export type AllocationByTypeItem = {
    type: string;
    marketValue: number;
    percentage: number;
};

export async function getPortfolioAllocation(keycloak: Keycloak): Promise<AllocationItem[]> {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/portfolio/allocation`, { headers });
    return res.data;
}

export async function getPortfolioAllocationByType(keycloak: Keycloak): Promise<AllocationByTypeItem[]> {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/portfolio/allocation-by-type`, { headers });
    return res.data;
}

/** PORTFOLIO SUMMARY DETAIL */
export type PortfolioPositionDetail = {
    symbol: string;
    name: string;
    quantity: number;
    buyDate: string;
    buyPrice: number;
    currentPrice: number;
    investedAmount: number;
    currentValue: number;
    totalChangeValue: number;
    totalChangePercent: number;
    dailyChangePercent: number;
    dailyChangeValue: number;
};

export type PortfolioSummaryDetail = {
    totalInvested: number;
    totalCurrentValue: number;
    totalChangeValue: number;
    totalChangePercent: number;
    positions: PortfolioPositionDetail[];
};

export async function getPortfolioSummaryDetail(keycloak: Keycloak): Promise<PortfolioSummaryDetail> {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/portfolio/summary-detail`, { headers });
    return res.data;
}

/** PORTFOLIO PERFORMANCE */
export type PortfolioPerformancePoint = {
    date: string;
    value: number;
};

export type PortfolioPerformanceResponse = {
    range: string;
    startDate: string;
    endDate: string;
    points: PortfolioPerformancePoint[];
};

export async function getPortfolioPerformance(
    keycloak: Keycloak, 
    range: string = "ALL"
): Promise<PortfolioPerformanceResponse> {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/portfolio/performance`, { 
        headers,
        params: { range }
    });
    return res.data;
}

/** NEWS */
export type NewsArticle = {
    id: number;
    title: string;
    summary: string;
    category: string;
    publishedAt: string;
    sourceUrl?: string;
    sourceName?: string;
};

export async function getNews(category?: string): Promise<NewsArticle[]> {
    const res = await axios.get(`${API_BASE}/api/v1/news`, {
        params: category ? { category } : {}
    });
    return res.data;
}

export async function getNewsCategories(): Promise<string[]> {
    const res = await axios.get(`${API_BASE}/api/v1/news/categories`);
    return res.data;
}

export async function getNewsById(id: number): Promise<NewsArticle> {
    const res = await axios.get(`${API_BASE}/api/v1/news/${id}`);
    return res.data;
}

/** EXCHANGE RATES */
export type ExchangeRate = {
    id: number;
    currencyCode: string;
    currencyName: string;
    buyingRate: number;
    sellingRate: number;
    effectiveBuyingRate: number;
    effectiveSellingRate: number;
    rateDate: string;
    source: string;
};

export async function getExchangeRates(): Promise<ExchangeRate[]> {
    const res = await axios.get(`${API_BASE}/api/v1/exchange-rates`);
    return res.data;
}

export async function getExchangeRatesBySource(source: string): Promise<ExchangeRate[]> {
    const res = await axios.get(`${API_BASE}/api/v1/exchange-rates/source/${source}`);
    return res.data;
}

export async function getExchangeRateSources(): Promise<string[]> {
    const res = await axios.get(`${API_BASE}/api/v1/exchange-rates/sources`);
    return res.data;
}

/** INVESTMENT FUNDS */
export type InvestmentFund = {
    id: number;
    fundCode: string;
    fundName: string;
    fundType: string;
    managementCompany: string;
    unitPrice: number;
    totalValue: number;
    managementFee?: number;
    performanceFee?: number;
    priceDate: string;
    dailyReturn?: number;
    weeklyReturn?: number;
    monthlyReturn?: number;
    yearlyReturn?: number;
    riskLevel?: string;
};

export async function getInvestmentFunds(): Promise<InvestmentFund[]> {
    const res = await axios.get(`${API_BASE}/api/v1/investment-funds`);
    return res.data;
}

export async function getFundTypes(): Promise<string[]> {
    const res = await axios.get(`${API_BASE}/api/v1/investment-funds/types`);
    return res.data;
}

export async function getFundsByType(fundType: string): Promise<InvestmentFund[]> {
    const res = await axios.get(`${API_BASE}/api/v1/investment-funds/type/${fundType}`);
    return res.data;
}

export async function searchFunds(query: string): Promise<InvestmentFund[]> {
    const res = await axios.get(`${API_BASE}/api/v1/investment-funds/search`, {
        params: { q: query }
    });
    return res.data;
}

/** TECHNICAL ANALYSIS */
export type TechnicalAnalysis = {
    symbol: string;
    [key: string]: any;
};

export async function getMovingAverages(symbol: string, period: number = 20): Promise<TechnicalAnalysis> {
    const res = await axios.get(`${API_BASE}/api/v1/technical-analysis/${symbol}/moving-averages`, {
        params: { period }
    });
    return res.data;
}

export async function getTrendAnalysis(symbol: string): Promise<TechnicalAnalysis> {
    const res = await axios.get(`${API_BASE}/api/v1/technical-analysis/${symbol}/trend`);
    return res.data;
}

export async function getSupportResistance(symbol: string): Promise<TechnicalAnalysis> {
    const res = await axios.get(`${API_BASE}/api/v1/technical-analysis/${symbol}/support-resistance`);
    return res.data;
}

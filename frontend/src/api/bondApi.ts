import type Keycloak from "keycloak-js";

const API_BASE = "/api/v1/bonds";

export type DebtInstrumentType = 
    | "GOVERNMENT_BOND" 
    | "TREASURY_BILL" 
    | "LEASE_CERTIFICATE" 
    | "EUROBOND" 
    | "CORPORATE_BOND" 
    | "OTHER";

export interface BondListItem {
    id: number;
    symbol: string;
    isin: string;
    name: string;
    type: DebtInstrumentType;
    currency: string;
    maturityDate: string;
    couponRate: number;
    latestPrice: number;
    latestYieldRate: number;
    changeRate: number;
    source: string;
    lastUpdatedAt: string;
}

export interface BondDetail {
    id: number;
    symbol: string;
    isin: string;
    name: string;
    type: DebtInstrumentType;
    issuer: string;
    currency: string;
    maturityDate: string;
    couponRate: number;
    couponType: string;
    daysToMaturity: number;
    latestPrice: number;
    latestYieldRate: number;
    cleanPrice: number;
    dirtyPrice: number;
    volume: number;
    changeRate: number;
    source: string;
    lastUpdatedAt: string;
}

export interface BondHistoryPoint {
    date: string;
    price: number;
    yieldRate: number;
    volume: number;
}

export interface BondSummary {
    totalInstruments: number;
    averageYield: number;
    highestYield: number;
    nearestMaturity: string;
    farthestMaturity: string;
    lastUpdateDate: string;
}

export interface BondFilters {
    type?: DebtInstrumentType;
    currency?: string;
    maturityFrom?: string;
    maturityTo?: string;
    search?: string;
}

/**
 * Tahvil ve bono listesini getirir.
 */
export async function getBonds(filters?: BondFilters): Promise<BondListItem[]> {
    const params = new URLSearchParams();
    if (filters?.type) params.append("type", filters.type);
    if (filters?.currency) params.append("currency", filters.currency);
    if (filters?.maturityFrom) params.append("maturityFrom", filters.maturityFrom);
    if (filters?.maturityTo) params.append("maturityTo", filters.maturityTo);
    if (filters?.search) params.append("search", filters.search);

    const url = params.toString() ? `${API_BASE}?${params}` : API_BASE;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Failed to fetch bonds: ${res.statusText}`);
    return res.json();
}

/**
 * Tahvil/bono detayını getirir.
 */
export async function getBondDetail(id: number): Promise<BondDetail> {
    const res = await fetch(`${API_BASE}/${id}`);
    if (!res.ok) throw new Error(`Failed to fetch bond detail: ${res.statusText}`);
    return res.json();
}

/**
 * Tahvil/bono tarihsel verilerini getirir.
 */
export async function getBondHistory(
    id: number, 
    from: string, 
    to: string
): Promise<BondHistoryPoint[]> {
    const params = new URLSearchParams({ from, to });
    const res = await fetch(`${API_BASE}/${id}/history?${params}`);
    if (!res.ok) throw new Error(`Failed to fetch bond history: ${res.statusText}`);
    return res.json();
}

/**
 * Tahvil/bono özet istatistiklerini getirir.
 */
export async function getBondSummary(): Promise<BondSummary> {
    const res = await fetch(`${API_BASE}/summary`);
    if (!res.ok) throw new Error(`Failed to fetch bond summary: ${res.statusText}`);
    return res.json();
}

/**
 * Manuel veri güncelleme (ADMIN only).
 */
export async function refreshBondData(keycloak: Keycloak): Promise<string> {
    const res = await fetch(`${API_BASE}/refresh`, {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${keycloak.token}`,
            "Content-Type": "application/json",
        },
    });
    if (!res.ok) throw new Error(`Failed to refresh bond data: ${res.statusText}`);
    return res.text();
}

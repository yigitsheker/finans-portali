const API_BASE = "/api/v1/bonds";

/**
 * Tahvil ve bono listesini getirir.
 */
export async function getBonds(filters) {
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
export async function getBondDetail(id) {
    const res = await fetch(`${API_BASE}/${id}`);
    if (!res.ok) throw new Error(`Failed to fetch bond detail: ${res.statusText}`);
    return res.json();
}

/**
 * Tahvil/bono tarihsel verilerini getirir.
 */
export async function getBondHistory(
    id,
    from,
    to
) {
    const params = new URLSearchParams({ from, to });
    const res = await fetch(`${API_BASE}/${id}/history?${params}`);
    if (!res.ok) throw new Error(`Failed to fetch bond history: ${res.statusText}`);
    return res.json();
}

/**
 * Tahvil/bono özet istatistiklerini getirir.
 */
export async function getBondSummary() {
    const res = await fetch(`${API_BASE}/summary`);
    if (!res.ok) throw new Error(`Failed to fetch bond summary: ${res.statusText}`);
    return res.json();
}

/**
 * Manuel veri güncelleme (ADMIN only).
 */
export async function refreshBondData(keycloak) {
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

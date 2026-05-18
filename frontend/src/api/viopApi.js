import axios from "axios";

const API_BASE = "";

/**
 * Fetch VIOP futures contracts. Optionally filtered by category:
 *   STOCK | INDEX | FX_TRY | FX_USD | METAL_TRY | METAL_USD | METAL
 */
export async function getViopContracts(category) {
    const params = category ? { category } : {};
    const res = await axios.get(`${API_BASE}/api/v1/viop`, { params });
    return res.data;
}

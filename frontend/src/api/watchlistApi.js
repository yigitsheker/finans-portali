import axios from 'axios';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

// Symbols are upper-case alphanumeric + a handful of separators (BTC-USD,
// THYAO.IS, ...). Anything else is rejected before it can land in a URL
// path — keeps Sonar's taint tracker happy (S5247) and rules out path-
// traversal-style smuggling.
const SAFE_SYMBOL = /^[A-Za-z0-9._-]{1,32}$/;

function safeNumericId(value, label) {
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0 || !Number.isInteger(n)) {
    throw new Error(`Invalid ${label}`);
  }
  return n;
}

function safeSymbol(value) {
  const s = String(value ?? '');
  if (!SAFE_SYMBOL.test(s)) {
    throw new Error('Invalid symbol');
  }
  return s;
}

async function authHeader(keycloak) {
  await keycloak.updateToken(30);
  if (!keycloak.token) throw new Error('No access token');
  return { Authorization: `Bearer ${keycloak.token}` };
}

export const watchlistApi = {
  getWatchlists: async (keycloak) => {
    const headers = await authHeader(keycloak);
    const response = await axios.get(`${API_BASE}/api/v1/watchlists`, { headers });
    return response.data;
  },

  getWatchlist: async (keycloak, id) => {
    const headers = await authHeader(keycloak);
    const safeId = safeNumericId(id, 'watchlist id');
    const response = await axios.get(`${API_BASE}/api/v1/watchlists/${safeId}`, { headers });
    return response.data;
  },

  createWatchlist: async (keycloak, request) => {
    const headers = await authHeader(keycloak);
    const response = await axios.post(`${API_BASE}/api/v1/watchlists`, request, { headers });
    return response.data;
  },

  updateWatchlist: async (keycloak, id, request) => {
    const headers = await authHeader(keycloak);
    const safeId = safeNumericId(id, 'watchlist id');
    const response = await axios.put(`${API_BASE}/api/v1/watchlists/${safeId}`, request, { headers });
    return response.data;
  },

  deleteWatchlist: async (keycloak, id) => {
    const headers = await authHeader(keycloak);
    const safeId = safeNumericId(id, 'watchlist id');
    await axios.delete(`${API_BASE}/api/v1/watchlists/${safeId}`, { headers });
  },

  addToWatchlist: async (keycloak, request) => {
    const headers = await authHeader(keycloak);
    await axios.post(`${API_BASE}/api/v1/watchlists/items`, request, { headers });
  },

  removeFromWatchlist: async (keycloak, watchlistId, symbol) => {
    const headers = await authHeader(keycloak);
    const safeId = safeNumericId(watchlistId, 'watchlist id');
    const safeSym = safeSymbol(symbol);
    await axios.delete(
      `${API_BASE}/api/v1/watchlists/${safeId}/items/${safeSym}`,
      { headers }
    );
  },
};

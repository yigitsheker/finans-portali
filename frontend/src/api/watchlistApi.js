import axios from 'axios';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

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
    const response = await axios.get(`${API_BASE}/api/v1/watchlists/${id}`, { headers });
    return response.data;
  },

  createWatchlist: async (keycloak, request) => {
    const headers = await authHeader(keycloak);
    const response = await axios.post(`${API_BASE}/api/v1/watchlists`, request, { headers });
    return response.data;
  },

  updateWatchlist: async (keycloak, id, request) => {
    const headers = await authHeader(keycloak);
    const response = await axios.put(`${API_BASE}/api/v1/watchlists/${id}`, request, { headers });
    return response.data;
  },

  deleteWatchlist: async (keycloak, id) => {
    const headers = await authHeader(keycloak);
    await axios.delete(`${API_BASE}/api/v1/watchlists/${id}`, { headers });
  },

  addToWatchlist: async (keycloak, request) => {
    const headers = await authHeader(keycloak);
    await axios.post(`${API_BASE}/api/v1/watchlists/items`, request, { headers });
  },

  removeFromWatchlist: async (keycloak, watchlistId, symbol) => {
    const headers = await authHeader(keycloak);
    await axios.delete(`${API_BASE}/api/v1/watchlists/${watchlistId}/items/${symbol}`, { headers });
  },
};

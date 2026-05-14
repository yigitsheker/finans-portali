import axios from 'axios';
import type Keycloak from 'keycloak-js';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export interface Watchlist {
  id: number;
  name: string;
  createdAt: string;
  updatedAt: string;
  symbols: string[];
}

export interface CreateWatchlistRequest {
  name: string;
}

export interface AddToWatchlistRequest {
  watchlistId: number;
  symbol: string;
}

async function authHeader(keycloak: Keycloak) {
  await keycloak.updateToken(30);
  if (!keycloak.token) throw new Error('No access token');
  return { Authorization: `Bearer ${keycloak.token}` };
}

export const watchlistApi = {
  getWatchlists: async (keycloak: Keycloak): Promise<Watchlist[]> => {
    const headers = await authHeader(keycloak);
    const response = await axios.get(`${API_BASE}/api/v1/watchlists`, { headers });
    return response.data;
  },

  getWatchlist: async (keycloak: Keycloak, id: number): Promise<Watchlist> => {
    const headers = await authHeader(keycloak);
    const response = await axios.get(`${API_BASE}/api/v1/watchlists/${id}`, { headers });
    return response.data;
  },

  createWatchlist: async (keycloak: Keycloak, request: CreateWatchlistRequest): Promise<Watchlist> => {
    const headers = await authHeader(keycloak);
    const response = await axios.post(`${API_BASE}/api/v1/watchlists`, request, { headers });
    return response.data;
  },

  updateWatchlist: async (keycloak: Keycloak, id: number, request: CreateWatchlistRequest): Promise<Watchlist> => {
    const headers = await authHeader(keycloak);
    const response = await axios.put(`${API_BASE}/api/v1/watchlists/${id}`, request, { headers });
    return response.data;
  },

  deleteWatchlist: async (keycloak: Keycloak, id: number): Promise<void> => {
    const headers = await authHeader(keycloak);
    await axios.delete(`${API_BASE}/api/v1/watchlists/${id}`, { headers });
  },

  addToWatchlist: async (keycloak: Keycloak, request: AddToWatchlistRequest): Promise<void> => {
    const headers = await authHeader(keycloak);
    await axios.post(`${API_BASE}/api/v1/watchlists/items`, request, { headers });
  },

  removeFromWatchlist: async (keycloak: Keycloak, watchlistId: number, symbol: string): Promise<void> => {
    const headers = await authHeader(keycloak);
    await axios.delete(`${API_BASE}/api/v1/watchlists/${watchlistId}/items/${symbol}`, { headers });
  },
};

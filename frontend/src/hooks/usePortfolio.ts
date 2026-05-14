/**
 * Custom hook for portfolio data management
 * Encapsulates portfolio state and operations
 */

import { useState, useEffect, useCallback } from 'react';
import type Keycloak from 'keycloak-js';
import {
  getPositions,
  getPortfolioSummaryDetail,
  getPortfolioPerformance,
  upsertPosition,
  sellPosition,
  type Position,
  type PortfolioSummaryDetail,
  type PortfolioPerformanceResponse,
  type UpsertPositionRequest,
} from '../api/portfolioApi';

interface UsePortfolioOptions {
  keycloak: Keycloak;
  autoRefresh?: boolean;
  refreshInterval?: number;
}

interface UsePortfolioReturn {
  // State
  positions: Position[];
  summaryDetail: PortfolioSummaryDetail | null;
  performance: PortfolioPerformanceResponse | null;
  loading: boolean;
  error: string | null;
  
  // Actions
  refresh: () => Promise<void>;
  addPosition: (request: UpsertPositionRequest) => Promise<void>;
  removePosition: (symbol: string, quantity: number) => Promise<void>;
  loadPerformance: (range: string) => Promise<void>;
}

export const usePortfolio = ({
  keycloak,
  autoRefresh = false,
  refreshInterval = 60000, // 1 minute
}: UsePortfolioOptions): UsePortfolioReturn => {
  const [positions, setPositions] = useState<Position[]>([]);
  const [summaryDetail, setSummaryDetail] = useState<PortfolioSummaryDetail | null>(null);
  const [performance, setPerformance] = useState<PortfolioPerformanceResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * Refresh all portfolio data
   */
  const refresh = useCallback(async () => {
    if (!keycloak.authenticated) {
      setError('Not authenticated');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      // Load positions
      const positionsData = await getPositions(keycloak);
      setPositions(Array.isArray(positionsData) ? positionsData : []);

      // Load summary detail
      try {
        const summary = await getPortfolioSummaryDetail(keycloak);
        setSummaryDetail(summary);
      } catch (e) {
        console.error('Failed to load portfolio summary:', e);
      }
    } catch (e: any) {
      setError(e?.message ?? 'Failed to load portfolio');
      console.error('Portfolio refresh error:', e);
    } finally {
      setLoading(false);
    }
  }, [keycloak]);

  /**
   * Load portfolio performance for a specific range
   */
  const loadPerformance = useCallback(async (range: string) => {
    if (!keycloak.authenticated) return;

    try {
      const perfData = await getPortfolioPerformance(keycloak, range);
      setPerformance(perfData);
    } catch (e) {
      console.error('Failed to load performance:', e);
      setPerformance(null);
    }
  }, [keycloak]);

  /**
   * Add or update a position
   */
  const addPosition = useCallback(async (request: UpsertPositionRequest) => {
    if (!keycloak.authenticated) {
      throw new Error('Not authenticated');
    }

    try {
      setLoading(true);
      setError(null);
      await upsertPosition(keycloak, request);
      await refresh();
    } catch (e: any) {
      const errorMsg = e?.message ?? 'Failed to add position';
      setError(errorMsg);
      throw new Error(errorMsg);
    } finally {
      setLoading(false);
    }
  }, [keycloak, refresh]);

  /**
   * Sell (remove) a position
   */
  const removePosition = useCallback(async (symbol: string, quantity: number) => {
    if (!keycloak.authenticated) {
      throw new Error('Not authenticated');
    }

    try {
      setLoading(true);
      setError(null);
      await sellPosition(keycloak, symbol, quantity);
      await refresh();
    } catch (e: any) {
      const errorMsg = e?.message ?? 'Failed to sell position';
      setError(errorMsg);
      throw new Error(errorMsg);
    } finally {
      setLoading(false);
    }
  }, [keycloak, refresh]);

  // Initial load
  useEffect(() => {
    if (keycloak.authenticated) {
      refresh();
    }
  }, [keycloak.authenticated, refresh]);

  // Auto-refresh
  useEffect(() => {
    if (!autoRefresh || !keycloak.authenticated) return;

    const interval = setInterval(() => {
      refresh();
    }, refreshInterval);

    return () => clearInterval(interval);
  }, [autoRefresh, refreshInterval, keycloak.authenticated, refresh]);

  return {
    positions,
    summaryDetail,
    performance,
    loading,
    error,
    refresh,
    addPosition,
    removePosition,
    loadPerformance,
  };
};

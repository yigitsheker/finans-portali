/**
 * Custom hook for portfolio data management
 * Encapsulates portfolio state and operations
 */

import { useState, useEffect, useCallback } from 'react';
import {
  getPositions,
  getPortfolioSummaryDetail,
  getPortfolioPerformance,
  upsertPosition,
  sellPosition,
} from '../api/portfolioApi';

export const usePortfolio = ({
  keycloak,
  autoRefresh = false,
  refreshInterval = 60000, // 1 minute
}) => {
  const [positions, setPositions] = useState([]);
  const [summaryDetail, setSummaryDetail] = useState(null);
  const [performance, setPerformance] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

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
    } catch (e) {
      setError(e?.message ?? 'Failed to load portfolio');
      console.error('Portfolio refresh error:', e);
    } finally {
      setLoading(false);
    }
  }, [keycloak]);

  /**
   * Load portfolio performance for a specific range
   */
  const loadPerformance = useCallback(async (range) => {
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
  const addPosition = useCallback(async (request) => {
    if (!keycloak.authenticated) {
      throw new Error('Not authenticated');
    }

    try {
      setLoading(true);
      setError(null);
      await upsertPosition(keycloak, request);
      await refresh();
    } catch (e) {
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
  const removePosition = useCallback(async (symbol, quantity) => {
    if (!keycloak.authenticated) {
      throw new Error('Not authenticated');
    }

    try {
      setLoading(true);
      setError(null);
      await sellPosition(keycloak, symbol, quantity);
      await refresh();
    } catch (e) {
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

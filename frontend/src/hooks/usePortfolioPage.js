import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  getLatestPrice,
  getMarketInstruments,
  getMarketSummary,
  getPortfolioPerformance,
  getPortfolioSummaryDetail,
  getPositions,
  sellPosition,
  upsertPosition,
} from "../api/portfolioApi";

export function usePortfolioPage(keycloak) {
  const [items, setItems] = useState([]);
  const [prices, setPrices] = useState({});
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState(null);
  const [instruments, setInstruments] = useState([]);
  const [marketData, setMarketData] = useState([]);
  const [summaryDetail, setSummaryDetail] = useState(null);
  const [perfResponse, setPerfResponse] = useState(null);
  const [perfLoading, setPerfLoading] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [addSymbol, setAddSymbol] = useState("");
  const [addQty, setAddQty] = useState(1);
  const [addPrice, setAddPrice] = useState(0);
  const [addPriceLoading, setAddPriceLoading] = useState(false);
  const [addSaving, setAddSaving] = useState(false);
  const [showSugg, setShowSugg] = useState(false);
  const [sellOpen, setSellOpen] = useState(false);
  const [sellTarget, setSellTarget] = useState(null);
  const [sellQty, setSellQty] = useState(1);
  const [sellSaving, setSellSaving] = useState(false);
  const [perfPeriod, setPerfPeriodState] = useState(null);
  const userOverrodePeriodRef = useRef(false);
  const setPerfPeriod = useCallback((period) => {
    userOverrodePeriodRef.current = true;
    setPerfPeriodState(period);
  }, []);
  const [allocView, setAllocView] = useState("symbol");

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setErr(null);
      const data = await getPositions(keycloak);
      const list = Array.isArray(data) ? data : [];
      setItems(list);

      const priceMap = {};
      await Promise.all(list.map(async (position) => {
        try {
          priceMap[position.symbol] = await getLatestPrice(position.symbol, keycloak);
        } catch {
          priceMap[position.symbol] = Number(position.avgCost ?? 0);
        }
      }));
      setPrices(priceMap);

      try {
        const summary = await getPortfolioSummaryDetail(keycloak);
        setSummaryDetail(summary);
      } catch (error) {
        console.error("[Portfolio] Failed to load summary detail:", error);
      }
    } catch (error) {
      setErr(error?.message ?? "Fetch error");
    } finally {
      setLoading(false);
    }
  }, [keycloak]);

  // Auto-pick a period based on the oldest purchase, but only on the first items load
  // and only if the user hasn't already picked one manually.
  useEffect(() => {
    if (items.length === 0 || userOverrodePeriodRef.current) return;

    const today = new Date().toISOString().split("T")[0];
    const earliest = items
      .map((position) => position.purchaseDate ?? today)
      .sort()[0];
    const daysDiff = Math.floor((Date.now() - new Date(earliest).getTime()) / 86400000);

    let next;
    if (daysDiff === 0) next = "1D";
    else if (daysDiff <= 5) next = "5D";
    else if (daysDiff <= 30) next = "1M";
    else if (daysDiff <= 90) next = "3M";
    else if (daysDiff <= 365) next = "1Y";
    else next = "ALL";

    setPerfPeriodState(next);
  }, [items]);

  // Fetch performance. Cancellable to avoid stale responses overwriting newer ones
  // (e.g. when perfPeriod changes mid-flight from auto-select).
  useEffect(() => {
    if (!keycloak.authenticated || items.length === 0 || !perfPeriod) return;

    let cancelled = false;
    setPerfLoading(true);
    getPortfolioPerformance(keycloak, perfPeriod)
      .then((response) => { if (!cancelled) setPerfResponse(response); })
      .catch((error) => {
        if (!cancelled) {
          console.error("[Portfolio] Failed to load performance:", error);
          setPerfResponse(null);
        }
      })
      .finally(() => { if (!cancelled) setPerfLoading(false); });

    return () => { cancelled = true; };
  }, [keycloak, keycloak.authenticated, perfPeriod, items.length]);

  useEffect(() => {
    refresh();
    getMarketInstruments().then(setInstruments).catch(() => {});
    getMarketSummary().then(setMarketData).catch(() => {});
  }, [refresh]);

  useEffect(() => {
    if (!addOpen) return;

    const symbol = addSymbol.trim().toUpperCase();
    if (!symbol) {
      setAddPrice(0);
      return;
    }

    let cancelled = false;
    setAddPriceLoading(true);
    const timer = setTimeout(async () => {
      try {
        const price = await getLatestPrice(symbol, keycloak);
        if (!cancelled) setAddPrice(price);
      } catch {
        if (!cancelled) setAddPrice(0);
      } finally {
        if (!cancelled) setAddPriceLoading(false);
      }
    }, 400);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [addSymbol, addOpen, keycloak]);

  const suggestions = useMemo(() => {
    if (!addSymbol.trim()) return instruments.slice(0, 8);

    const query = addSymbol.trim().toUpperCase();
    return instruments
      .filter((instrument) => instrument.symbol.includes(query) || instrument.name.toUpperCase().includes(query))
      .slice(0, 8);
  }, [addSymbol, instruments]);

  const stats = useMemo(() => {
    const usdtry = marketData.find((item) => item.symbol === "USDTRY");
    const usdRate = usdtry?.last ?? 35.0;

    if (summaryDetail) {
      let totalValueTRY = 0;
      let totalCostTRY = 0;

      summaryDetail.positions.forEach((position) => {
        const multiplier = position.currency === "USD" ? usdRate : 1;
        totalValueTRY += position.currentValue * multiplier;
        totalCostTRY += position.investedAmount * multiplier;
      });

      const totalGain = totalValueTRY - totalCostTRY;
      const totalGainPct = totalCostTRY > 0 ? (totalGain / totalCostTRY) * 100 : 0;

      return {
        totalValue: totalValueTRY,
        totalCost: totalCostTRY,
        totalGain,
        totalGainPct,
        count: summaryDetail.positions.length,
      };
    }

    let totalValue = 0;
    let totalCost = 0;
    items.forEach((position) => {
      const qty = Number(position.quantity);
      const price = prices[position.symbol] ?? Number(position.avgCost ?? 0);
      totalValue += price * qty;
      totalCost += Number(position.avgCost ?? 0) * qty;
    });

    const totalGain = totalValue - totalCost;
    const totalGainPct = totalCost > 0 ? (totalGain / totalCost) * 100 : 0;
    return { totalValue, totalCost, totalGain, totalGainPct, count: items.length };
  }, [summaryDetail, items, prices, marketData]);

  const perfData = useMemo(() => {
    if (!perfResponse || perfResponse.points.length === 0) return [];

    return perfResponse.points.map((point) => {
      let time;
      if (point.datetime) {
        time = point.datetime;
      } else if (point.date) {
        time = typeof point.date === "string" ? point.date.split("T")[0] : String(point.date);
      } else {
        time = new Date().toISOString().split("T")[0];
      }
      return { time, value: Number(point.value) };
    });
  }, [perfResponse]);

  const allocData = useMemo(() => {
    const usdtry = marketData.find((item) => item.symbol === "USDTRY");
    const usdRate = usdtry?.last ?? 35.0;

    if (summaryDetail && summaryDetail.positions.length > 0) {
      return getSummaryAllocation(summaryDetail, allocView, usdRate);
    }

    return getFallbackAllocation(items, prices, instruments, allocView, usdRate);
  }, [summaryDetail, items, prices, instruments, marketData, allocView]);

  const addTotal = useMemo(() => {
    const quantity = Number(addQty);
    return quantity > 0 && addPrice > 0 ? Number((addPrice * quantity).toFixed(4)) : 0;
  }, [addPrice, addQty]);

  const sellCurrentPrice = sellTarget ? (prices[sellTarget.symbol] ?? Number(sellTarget.avgCost ?? 0)) : 0;
  const sellProceeds = sellCurrentPrice * Number(sellQty);

  const openAddModal = useCallback(() => {
    setAddSymbol("");
    setAddQty(1);
    setAddPrice(0);
    setErr(null);
    setAddOpen(true);
  }, []);

  const closeAddModal = useCallback(() => setAddOpen(false), []);

  const pickSuggestion = useCallback((symbol) => {
    setAddSymbol(symbol);
    setShowSugg(false);
  }, []);

  const openSellModal = useCallback((position) => {
    setSellTarget(position);
    setSellQty(1);
    setErr(null);
    setSellOpen(true);
  }, []);

  const closeSellModal = useCallback(() => setSellOpen(false), []);

  const onAdd = useCallback(async () => {
    const symbol = addSymbol.trim().toUpperCase();
    if (!symbol) return setErr("Sembol zorunlu");
    if (!addQty || addQty <= 0) return setErr("Adet 1 veya daha buyuk olmali");
    if (!addPrice || addPrice <= 0) return setErr("Gecerli bir sembol girin");

    try {
      setAddSaving(true);
      setErr(null);
      await upsertPosition(keycloak, { symbol, quantity: addQty, avgCost: addPrice });
      setAddOpen(false);
      await refresh();
    } catch (error) {
      setErr(error?.message ?? "Save error");
    } finally {
      setAddSaving(false);
    }
  }, [addSymbol, addQty, addPrice, keycloak, refresh]);

  const onSell = useCallback(async () => {
    if (!sellTarget) return;

    const quantity = Number(sellQty);
    if (!quantity || quantity <= 0) return setErr("Adet 1 veya daha buyuk olmali");
    if (quantity > Number(sellTarget.quantity)) return setErr("Yetersiz miktar");

    try {
      setSellSaving(true);
      setErr(null);
      await sellPosition(keycloak, sellTarget.symbol, quantity);
      setSellOpen(false);
      await refresh();
    } catch (error) {
      setErr(error?.message ?? "Sell error");
    } finally {
      setSellSaving(false);
    }
  }, [sellTarget, sellQty, keycloak, refresh]);

  return {
    items,
    prices,
    loading,
    err,
    marketData,
    summaryDetail,
    perfResponse,
    perfLoading,
    perfPeriod,
    setPerfPeriod,
    allocView,
    setAllocView,
    addOpen,
    addSymbol,
    addQty,
    addPrice,
    addPriceLoading,
    addSaving,
    addTotal,
    showSugg,
    suggestions,
    setAddSymbol,
    setAddQty,
    setShowSugg,
    sellOpen,
    sellTarget,
    sellQty,
    sellSaving,
    sellCurrentPrice,
    sellProceeds,
    setSellQty,
    stats,
    perfData,
    allocData,
    openAddModal,
    closeAddModal,
    pickSuggestion,
    openSellModal,
    closeSellModal,
    onAdd,
    onSell,
  };
}

function getSummaryAllocation(summaryDetail, allocView, usdRate) {
  if (allocView === "symbol") {
    return summaryDetail.positions
      .map((position) => ({
        name: position.symbol,
        value: Math.round(position.currentValue * (position.currency === "USD" ? usdRate : 1)),
      }))
      .filter((item) => item.value > 0);
  }

  const allocationMap = {};
  summaryDetail.positions.forEach((position) => {
    const name = allocView === "type" ? getTypeLabel(position.type) : getMarketLabel(position.type, position.currency);
    const valueInTRY = position.currentValue * (position.currency === "USD" ? usdRate : 1);
    allocationMap[name] = (allocationMap[name] || 0) + valueInTRY;
  });

  return mapAllocation(allocationMap);
}

function getFallbackAllocation(
  items,
  prices,
  instruments,
  allocView,
  usdRate,
) {
  if (allocView === "symbol") {
    return items
      .map((position) => {
        const instrument = instruments.find((item) => item.symbol === position.symbol);
        const price = convertFallbackPrice(position.symbol, prices[position.symbol] ?? Number(position.avgCost ?? 0), instrument?.type, usdRate);
        return { name: position.symbol, value: Math.round(price * Number(position.quantity)) };
      })
      .filter((item) => item.value > 0);
  }

  const allocationMap = {};
  items.forEach((position) => {
    const instrument = instruments.find((item) => item.symbol === position.symbol);
    const instrumentType = instrument?.type;
    const price = convertFallbackPrice(position.symbol, prices[position.symbol] ?? Number(position.avgCost ?? 0), instrumentType, usdRate);
    const key = allocView === "type"
      ? getFallbackTypeLabel(position.symbol, instrumentType)
      : getFallbackMarketLabel(position.symbol, instrumentType);

    allocationMap[key] = (allocationMap[key] || 0) + price * Number(position.quantity);
  });

  return mapAllocation(allocationMap);
}

function mapAllocation(allocationMap) {
  return Object.entries(allocationMap)
    .map(([name, value]) => ({ name, value: Math.round(value) }))
    .filter((item) => item.value > 0);
}

function convertFallbackPrice(symbol, price, type, usdRate) {
  if (type === "CRYPTO") return price * usdRate;
  if (type === "STOCK" && !isBistLikeSymbol(symbol)) return price * usdRate;
  if (!type && !isBistLikeSymbol(symbol) && !symbol.includes("TRY")) return price * usdRate;
  return price;
}

function getTypeLabel(type) {
  if (type === "STOCK" || type === "BIST") return "Hisse";
  if (type === "CRYPTO") return "Kripto";
  if (type === "FUND") return "Fon";
  if (type === "FX") return "Döviz";
  if (type === "COMMODITY") return "Emtia";
  return "Diğer";
}

function getMarketLabel(type, currency) {
  if (type === "BIST") return "BIST";
  if (type === "STOCK") return currency === "TRY" ? "BIST" : "Uluslararası Hisse";
  if (type === "CRYPTO") return "Kripto";
  if (type === "FUND") return "Fon";
  if (type === "FX") return "Döviz";
  if (type === "COMMODITY") return "Emtia";
  return "Diğer";
}

function getFallbackTypeLabel(symbol, type) {
  if (type) return getTypeLabel(type);
  if (symbol.includes("/") || symbol.includes("TRY")) return "Döviz";
  if (symbol.includes("BTC") || symbol.includes("ETH")) return "Kripto";
  return "Hisse";
}

function getFallbackMarketLabel(symbol, type) {
  if (type === "CRYPTO") return "Kripto";
  if (type === "FUND") return "Fon";
  if (type === "FX") return "Döviz";
  if (type === "COMMODITY") return "Emtia";
  if (type === "STOCK") return isBistLikeSymbol(symbol) ? "BIST" : "Uluslararası Hisse";
  if (symbol.includes("/") || symbol.includes("TRY")) return "Döviz";
  if (symbol.includes("BTC") || symbol.includes("ETH")) return "Kripto";
  return isBistLikeSymbol(symbol) ? "BIST" : "Uluslararası Hisse";
}

function isBistLikeSymbol(symbol) {
  return symbol.endsWith(".IS") || /^[A-Z]{3,5}$/.test(symbol);
}

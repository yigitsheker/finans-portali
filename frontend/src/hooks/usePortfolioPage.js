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
import notify from "../utils/notify";

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
  // "quantity" → user types lot count; "amount" → user types a budget and the
  // lot count is derived as floor(amount / price). Reset on every open.
  const [addInputMode, setAddInputMode] = useState("quantity");
  const [addAmount, setAddAmount] = useState(0);
  const [addPriceLoading, setAddPriceLoading] = useState(false);
  const [addSaving, setAddSaving] = useState(false);
  const [importing, setImporting] = useState(false);
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

  // In "amount" mode, the effective lot count is derived from the budget;
  // in "quantity" mode it's whatever the user typed. The save handler reads
  // this value (not addQty) so the two paths stay consistent.
  const addEffectiveQty = useMemo(() => {
    if (addInputMode === "amount") {
      if (!addAmount || !addPrice || addAmount <= 0 || addPrice <= 0) return 0;
      return Math.floor(Number(addAmount) / Number(addPrice));
    }
    return Number(addQty) || 0;
  }, [addInputMode, addAmount, addPrice, addQty]);

  const addTotal = useMemo(() => {
    const qty = addEffectiveQty;
    return qty > 0 && addPrice > 0 ? Number((addPrice * qty).toFixed(4)) : 0;
  }, [addPrice, addEffectiveQty]);

  // In "amount" mode, surface the leftover budget so users see why the lot
  // count rounded down (e.g. ₺1000 budget at ₺137/lot → 7 lots, ₺41 leftover).
  const addAmountLeftover = useMemo(() => {
    if (addInputMode !== "amount") return 0;
    if (!addAmount || !addPrice || addEffectiveQty <= 0) return Number(addAmount) || 0;
    return Number(addAmount) - addEffectiveQty * Number(addPrice);
  }, [addInputMode, addAmount, addPrice, addEffectiveQty]);

  const sellCurrentPrice = sellTarget ? (prices[sellTarget.symbol] ?? Number(sellTarget.avgCost ?? 0)) : 0;
  const sellProceeds = sellCurrentPrice * Number(sellQty);

  const openAddModal = useCallback(() => {
    setAddSymbol("");
    setAddQty(1);
    setAddPrice(0);
    setAddInputMode("quantity");
    setAddAmount(0);
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
    if (!addPrice || addPrice <= 0) return setErr("Gecerli bir sembol girin");
    if (addInputMode === "amount" && (!addAmount || Number(addAmount) <= 0)) {
      return setErr("Tutar 0'dan büyük olmalı");
    }
    const qty = addEffectiveQty;
    if (!qty || qty <= 0) {
      return setErr(addInputMode === "amount"
        ? "Bu tutarla en az 1 adet alınamıyor — daha yüksek bir tutar girin"
        : "Adet 1 veya daha büyük olmalı");
    }

    try {
      setAddSaving(true);
      setErr(null);
      await upsertPosition(keycloak, { symbol, quantity: qty, avgCost: addPrice });
      notify.tx(`${symbol}: ${qty} adet portföye eklendi`);
      setAddOpen(false);
      await refresh();
    } catch (error) {
      setErr(error?.message ?? "Save error");
    } finally {
      setAddSaving(false);
    }
  }, [addSymbol, addEffectiveQty, addPrice, addInputMode, addAmount, keycloak, refresh]);

  const onSell = useCallback(async () => {
    if (!sellTarget) return;

    const quantity = Number(sellQty);
    if (!quantity || quantity <= 0) return setErr("Adet 1 veya daha buyuk olmali");
    if (quantity > Number(sellTarget.quantity)) return setErr("Yetersiz miktar");

    try {
      setSellSaving(true);
      setErr(null);
      await sellPosition(keycloak, sellTarget.symbol, quantity);
      notify.tx(`${sellTarget.symbol}: ${quantity} adet satıldı`);
      setSellOpen(false);
      await refresh();
    } catch (error) {
      setErr(error?.message ?? "Sell error");
    } finally {
      setSellSaving(false);
    }
  }, [sellTarget, sellQty, keycloak, refresh]);

  // Bulk-add positions from already-parsed (and possibly user-edited, via the
  // import preview) rows: [{ symbol, lot }]. Unknown symbols and rows without a
  // live quote are skipped and counted. Returns counts for the page's toast.
  const importRows = useCallback(async (rows) => {
    if (!rows || !rows.length) return { ok: true, imported: 0, skipped: 0 };
    setImporting(true);
    try {
      // The catalog loads async on mount; if the user imports before it's ready
      // fetch it now so symbol validation isn't silently skipping everything.
      let catalog = instruments;
      if (!catalog.length) catalog = await getMarketInstruments().catch(() => []);
      const valid = new Set(catalog.map((i) => String(i.symbol).toUpperCase()));
      // Collapse duplicate symbols (e.g. after preview edits) into one upsert
      // with the summed lots — upsert is additive, so per-row calls would double
      // the booked quantity and over-count "imported".
      const bySymbol = new Map();
      let skipped = 0;
      for (const row of rows) {
        const sym = String(row.symbol || "").trim().toUpperCase();
        const lot = Number(row.lot);
        if (!sym || !(lot > 0) || !valid.has(sym)) { skipped++; continue; }
        bySymbol.set(sym, (bySymbol.get(sym) || 0) + lot);
      }
      let imported = 0;
      for (const [sym, lot] of bySymbol) {
        try {
          const price = await getLatestPrice(sym, keycloak);
          // No live quote → skip rather than book a position at cost 0 (which
          // would corrupt P&L). Counted as skipped.
          if (!(price > 0)) { skipped++; continue; }
          await upsertPosition(keycloak, { symbol: sym, quantity: lot, avgCost: price });
          imported++;
        } catch {
          skipped++;
        }
      }
      if (imported > 0) await refresh();
      return { ok: true, imported, skipped };
    } finally {
      setImporting(false);
    }
  }, [instruments, keycloak, refresh]);

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
    importing,
    importRows,
    instruments,
    addTotal,
    addInputMode,
    addAmount,
    addEffectiveQty,
    addAmountLeftover,
    showSugg,
    suggestions,
    setAddSymbol,
    setAddQty,
    setAddInputMode,
    setAddAmount,
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
    // Manual refresh — wired to the refresh button in the page header so the
    // user can pull fresh quotes without a full page reload.
    refresh,
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

import type Keycloak from "keycloak-js";
import { useEffect, useMemo, useState } from "react";
import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer,
} from "recharts";
import Modal from "../components/Modal";
import { PortfolioAreaChart, type ChartPoint } from "../components/common/PortfolioAreaChart";
import {
  getPositions, getLatestPrice, getMarketSummary,
  upsertPosition, sellPosition,
  type MarketInstrument, type Position, type MarketSummaryItem,
  getPortfolioSummaryDetail, getPortfolioPerformance,
  type PortfolioSummaryDetail, type PortfolioPerformanceResponse,
} from "../api/portfolioApi";
import { getMarketInstruments } from "../api/portfolioApi";

type Props = { keycloak: Keycloak };

const ALLOC_COLORS = ["#2563eb", "#3fb950", "#f59e0b", "#f85149", "#38bdf8", "#a78bfa"];

export default function Portfolio({ keycloak }: Props) {
  const [items, setItems] = useState<Position[]>([]);
  const [prices, setPrices] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [instruments, setInstruments] = useState<MarketInstrument[]>([]);
  const [marketData, setMarketData] = useState<MarketSummaryItem[]>([]);

  // Real API data states
  const [summaryDetail, setSummaryDetail] = useState<PortfolioSummaryDetail | null>(null);
  const [perfResponse, setPerfResponse] = useState<PortfolioPerformanceResponse | null>(null);
  const [perfLoading, setPerfLoading] = useState(false);

  const [addOpen, setAddOpen] = useState(false);
  const [addSymbol, setAddSymbol] = useState("");
  const [addQty, setAddQty] = useState(1);
  const [addPrice, setAddPrice] = useState(0);
  const [addPriceLoading, setAddPriceLoading] = useState(false);
  const [addSaving, setAddSaving] = useState(false);
  const [showSugg, setShowSugg] = useState(false);

  const [sellOpen, setSellOpen] = useState(false);
  const [sellTarget, setSellTarget] = useState<Position | null>(null);
  const [sellQty, setSellQty] = useState(1);
  const [sellSaving, setSellSaving] = useState(false);

  // Historical comparison state
  const [histOpen, setHistOpen] = useState(false);
  const [histSymbol, setHistSymbol] = useState("");
  const [histDate, setHistDate] = useState("");
  const [histPrice, setHistPrice] = useState<number | null>(null);
  const [histCurrentPrice, setHistCurrentPrice] = useState<number>(0);
  const [histLoading, setHistLoading] = useState(false);
  const [histError, setHistError] = useState<string | null>(null);
  const [showHistSugg, setShowHistSugg] = useState(false);

  // Portfolio performance period
  const [perfPeriod, setPerfPeriod] = useState<string>("1D");

  // Auto-select best default period based on earliest buy date
  useEffect(() => {
    if (items.length === 0) return;
    const today = new Date().toISOString().split("T")[0];
    const earliest = items
      .map(p => p.purchaseDate ?? today)
      .sort()[0];
    const daysDiff = Math.floor((Date.now() - new Date(earliest).getTime()) / 86400000);
    if (daysDiff === 0) setPerfPeriod("1D");
    else if (daysDiff <= 5) setPerfPeriod("5D");
    else if (daysDiff <= 30) setPerfPeriod("1M");
    else if (daysDiff <= 90) setPerfPeriod("3M");
    else if (daysDiff <= 365) setPerfPeriod("1Y");
    else setPerfPeriod("ALL");
  }, [items]);

  const isDark = document.documentElement.getAttribute("data-theme") !== "light";
  const tooltipBg = isDark ? "#1c2128" : "#ffffff";
  const tooltipBorder = isDark ? "#30363d" : "#d0d7de";
  const tooltipColor = isDark ? "#e6edf3" : "#1f2328";

  async function refresh() {
    try {
      setLoading(true); setErr(null);
      const data = await getPositions(keycloak);
      const list = Array.isArray(data) ? data : [];
      setItems(list);
      const priceMap: Record<string, number> = {};
      await Promise.all(list.map(async (p) => {
        try { priceMap[p.symbol] = await getLatestPrice(p.symbol, keycloak); }
        catch { priceMap[p.symbol] = Number(p.avgCost ?? 0); }
      }));
      setPrices(priceMap);
      
      // Fetch real portfolio summary
      try {
        const summary = await getPortfolioSummaryDetail(keycloak);
        setSummaryDetail(summary);
        console.log("[Portfolio] Summary detail loaded:", summary);
      } catch (e: any) {
        console.error("[Portfolio] Failed to load summary detail:", e);
      }
    } catch (e: any) { setErr(e?.message ?? "Fetch error"); }
    finally { setLoading(false); }
  }

  // Fetch portfolio performance when period changes
  useEffect(() => {
    if (!keycloak.authenticated || items.length === 0) return;
    
    setPerfLoading(true);
    getPortfolioPerformance(keycloak, perfPeriod)
      .then(response => {
        setPerfResponse(response);
        console.log("[Portfolio] Performance loaded:", response);
      })
      .catch(e => {
        console.error("[Portfolio] Failed to load performance:", e);
        setPerfResponse(null);
      })
      .finally(() => setPerfLoading(false));
  }, [keycloak.authenticated, perfPeriod, items.length]);

  useEffect(() => {
    refresh();
    getMarketInstruments().then(setInstruments).catch(() => {});
    getMarketSummary().then(setMarketData).catch(() => {});
  }, []);

  useEffect(() => {
    if (!addOpen) return;
    const sym = addSymbol.trim().toUpperCase();
    if (!sym) { setAddPrice(0); return; }
    let cancelled = false;
    setAddPriceLoading(true);
    const t = setTimeout(async () => {
      try { const p = await getLatestPrice(sym, keycloak); if (!cancelled) setAddPrice(p); }
      catch { if (!cancelled) setAddPrice(0); }
      finally { if (!cancelled) setAddPriceLoading(false); }
    }, 400);
    return () => { cancelled = true; clearTimeout(t); };
  }, [addSymbol, addOpen]);

  const suggestions = useMemo(() => {
    if (!addSymbol.trim()) return instruments.slice(0, 8);
    const q = addSymbol.trim().toUpperCase();
    return instruments.filter((i) => i.symbol.includes(q) || i.name.toUpperCase().includes(q)).slice(0, 8);
  }, [addSymbol, instruments]);

  const histSuggestions = useMemo(() => {
    if (!histSymbol.trim()) return instruments.slice(0, 8);
    const q = histSymbol.trim().toUpperCase();
    return instruments.filter((i) => i.symbol.includes(q) || i.name.toUpperCase().includes(q)).slice(0, 8);
  }, [histSymbol, instruments]);

  const stats = useMemo(() => {
    // Use real API data if available
    if (summaryDetail) {
      return {
        totalValue: summaryDetail.totalCurrentValue,
        totalCost: summaryDetail.totalInvested,
        totalGain: summaryDetail.totalChangeValue,
        totalGainPct: summaryDetail.totalChangePercent,
        count: summaryDetail.positions.length
      };
    }
    
    // Fallback to old calculation if API data not loaded yet
    let totalValue = 0, totalCost = 0;
    items.forEach((p) => {
      const qty = Number(p.quantity);
      const price = prices[p.symbol] ?? Number(p.avgCost ?? 0);
      totalValue += price * qty;
      totalCost += Number(p.avgCost ?? 0) * qty;
    });
    const totalGain = totalValue - totalCost;
    const totalGainPct = totalCost > 0 ? (totalGain / totalCost) * 100 : 0;
    return { totalValue, totalCost, totalGain, totalGainPct, count: items.length };
  }, [summaryDetail, items, prices]);

  const perfData = useMemo((): ChartPoint[] => {
    if (!perfResponse || perfResponse.points.length === 0) return [];

    return perfResponse.points.map((p) => {
      let time: string;
      if (p.datetime) {
        // Intraday: ISO datetime → keep as-is, PortfolioAreaChart handles it
        time = p.datetime;
      } else if (p.date) {
        // Daily: 'YYYY-MM-DD'
        time = typeof p.date === "string" ? p.date.split("T")[0] : String(p.date);
      } else {
        time = new Date().toISOString().split("T")[0];
      }
      return { time, value: Number(p.value) };
    });
  }, [perfResponse]);

  const allocData = useMemo(() => {
    // Per-symbol allocation so each position gets its own slice
    return items
      .map((p) => {
        const val = (prices[p.symbol] ?? Number(p.avgCost ?? 0)) * Number(p.quantity);
        return { name: p.symbol, value: Math.round(val) };
      })
      .filter((d) => d.value > 0);
  }, [items, prices]);

  const addTotal = useMemo(() => {
    const q = Number(addQty);
    return q > 0 && addPrice > 0 ? Number((addPrice * q).toFixed(4)) : 0;
  }, [addPrice, addQty]);

  const sellCurrentPrice = sellTarget ? (prices[sellTarget.symbol] ?? Number(sellTarget.avgCost ?? 0)) : 0;
  const sellProceeds = sellCurrentPrice * Number(sellQty);

  async function onAdd() {
    const sym = addSymbol.trim().toUpperCase();
    if (!sym) return setErr("Sembol zorunlu");
    if (!addQty || addQty <= 0) return setErr("Adet 1 veya daha buyuk olmali");
    if (!addPrice || addPrice <= 0) return setErr("Gecerli bir sembol girin");
    try {
      setAddSaving(true); setErr(null);
      await upsertPosition(keycloak, { symbol: sym, quantity: addQty, avgCost: addPrice });
      setAddOpen(false); await refresh();
    } catch (e: any) { setErr(e?.message ?? "Save error"); }
    finally { setAddSaving(false); }
  }

  async function onSell() {
    if (!sellTarget) return;
    const q = Number(sellQty);
    if (!q || q <= 0) return setErr("Adet 1 veya daha buyuk olmali");
    if (q > Number(sellTarget.quantity)) return setErr("Yetersiz miktar");
    try {
      setSellSaving(true); setErr(null);
      await sellPosition(keycloak, sellTarget.symbol, q);
      setSellOpen(false); await refresh();
    } catch (e: any) { setErr(e?.message ?? "Sell error"); }
    finally { setSellSaving(false); }
  }

  async function onHistoricalCompare() {
    const sym = histSymbol.trim().toUpperCase();
    if (!sym) {
      setHistError("Sembol zorunlu");
      return;
    }
    if (!histDate) {
      setHistError("Tarih zorunlu");
      return;
    }
    
    const selectedDate = new Date(histDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    
    if (selectedDate >= today) {
      setHistError("Tarih bugünden eski olmalı");
      return;
    }

    try {
      setHistLoading(true);
      setHistError(null);
      
      console.log("[Historical Compare] Symbol:", sym);
      console.log("[Historical Compare] Selected Date:", histDate);
      
      // Get current price
      const currentPrice = await getLatestPrice(sym, keycloak);
      console.log("[Historical Compare] Current Price:", currentPrice);
      
      // Calculate days difference to determine appropriate period
      const daysDiff = Math.floor((today.getTime() - selectedDate.getTime()) / (1000 * 60 * 60 * 24));
      let period = "30D";
      if (daysDiff > 365) period = "5Y";
      else if (daysDiff > 180) period = "1Y";
      else if (daysDiff > 90) period = "6M";
      else if (daysDiff > 30) period = "3M";
      
      console.log("[Historical Compare] Days Diff:", daysDiff, "Period:", period);
      
      // Fetch historical data
      const { getMarketHistory } = await import("../api/portfolioApi");
      const historyData = await getMarketHistory(sym, period);
      
      console.log("[Historical Compare] History Data Points:", historyData?.length);
      
      if (!historyData || historyData.length === 0) {
        setHistError("Bu sembol için geçmiş veri bulunamadı");
        return;
      }
      
      // Find the closest date to the selected date
      const targetTime = selectedDate.getTime();
      let closestPoint = historyData[0];
      let minDiff = Math.abs(new Date(historyData[0].day).getTime() - targetTime);
      
      for (const point of historyData) {
        const pointTime = new Date(point.day).getTime();
        const diff = Math.abs(pointTime - targetTime);
        if (diff < minDiff) {
          minDiff = diff;
          closestPoint = point;
        }
      }
      
      console.log("[Historical Compare] Closest Point:", closestPoint);
      console.log("[Historical Compare] Historical Price:", closestPoint.close);
      console.log("[Historical Compare] Change:", currentPrice - closestPoint.close);
      
      setHistPrice(closestPoint.close);
      setHistCurrentPrice(currentPrice);
      
      // Close modal after successful calculation
      setHistOpen(false);
    } catch (e: any) {
      console.error("[Historical Compare] Error:", e);
      setHistError(e?.message ?? "Fiyat alınamadı");
    } finally {
      setHistLoading(false);
    }
  }

  const gainPos = stats.totalGain >= 0;

  // Historical comparison calculations
  const histChange = histPrice && histCurrentPrice ? histCurrentPrice - histPrice : 0;
  const histChangePct = histPrice && histPrice > 0 ? ((histChange / histPrice) * 100) : 0;
  const histChangePos = histChange >= 0;

  return (
    <div style={s.root}>
      {/* Summary cards */}
      <div style={s.summaryGrid}>
        <SCard label="Toplam Portfoy Degeri" value={"$" + stats.totalValue.toLocaleString("tr-TR", { maximumFractionDigits: 2 })} sub={"Maliyet: $" + stats.totalCost.toLocaleString("tr-TR", { maximumFractionDigits: 2 })} />
        <SCard label="Toplam Kazanc / Kayip" value={(gainPos ? "+$" : "-$") + Math.abs(stats.totalGain).toLocaleString("tr-TR", { maximumFractionDigits: 2 })} sub={(gainPos ? "+" : "") + stats.totalGainPct.toFixed(2) + "% tum zamanlarda"} valueColor={gainPos ? "var(--green)" : "var(--red)"} />
        <SCard label="Pozisyon Sayisi" value={String(stats.count)} sub="Hisse, ETF ve kripto" />
        <SCard label="Durum" value={loading ? "Yukleniyor..." : err ? "Hata" : "Guncel"} sub="Portfoy durumu" />
      </div>

      {/* Historical Comparison Card */}
      <div style={s.histCard}>
        <div style={s.histHeader}>
          <div>
            <div style={s.histTitle}>📊 Geçmişten Bugüne Değişim</div>
            <div style={s.histSub}>Geçmiş bir tarihten bugüne fiyat değişimini görün</div>
          </div>
          <button 
            style={s.histBtn} 
            onClick={() => {
              // Clear input fields and errors, but keep previous results visible
              setHistSymbol("");
              setHistDate("");
              setHistError(null);
              setHistOpen(true);
            }}
          >
            + Karşılaştır
          </button>
        </div>
        
        {histPrice && histSymbol && histDate ? (
          <div style={s.histResult}>
            <div style={s.histResultGrid}>
              <div style={s.histResultItem}>
                <div style={s.histResultLabel}>Sembol</div>
                <div style={s.histResultValue}>{histSymbol.toUpperCase()}</div>
              </div>
              <div style={s.histResultItem}>
                <div style={s.histResultLabel}>Alış Tarihi</div>
                <div style={s.histResultValue}>{new Date(histDate).toLocaleDateString("tr-TR")}</div>
              </div>
              <div style={s.histResultItem}>
                <div style={s.histResultLabel}>Alış Fiyatı</div>
                <div style={s.histResultValue}>${histPrice.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}</div>
              </div>
              <div style={s.histResultItem}>
                <div style={s.histResultLabel}>Güncel Fiyat</div>
                <div style={s.histResultValue}>${histCurrentPrice.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}</div>
              </div>
              <div style={s.histResultItem}>
                <div style={s.histResultLabel}>Değişim</div>
                <div style={{ ...s.histResultValue, color: histChangePos ? "var(--green)" : "var(--red)" }}>
                  {histChangePos ? "+" : ""}${Math.abs(histChange).toLocaleString("tr-TR", { maximumFractionDigits: 2 })}
                </div>
              </div>
              <div style={s.histResultItem}>
                <div style={s.histResultLabel}>Değişim %</div>
                <div style={{ ...s.histResultValue, color: histChangePos ? "var(--green)" : "var(--red)", fontWeight: 700, fontSize: 18 }}>
                  {histChangePos ? "▲ +" : "▼ "}{histChangePct.toFixed(2)}%
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div style={s.histEmpty}>
            <div style={{ fontSize: 32, marginBottom: 8 }}>📈</div>
            <div style={{ fontSize: 13, color: "var(--text-muted)" }}>
              Bir hisse, döviz veya değerli maden seçip geçmiş bir tarih girerek o tarihten bugüne değişimi görün
            </div>
          </div>
        )}
      </div>

      {err && <div style={s.errBox}>{err}</div>}

      {!loading && items.length > 0 && (
        <div style={s.chartsRow}>
          <div style={s.chartCard}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
              <div>
                <div style={s.chartTitle}>Portfoy Performansi</div>
                <div style={s.chartSub}>
                  {perfLoading ? "Yukleniyor..." : 
                   perfResponse && perfResponse.points.length > 0 ? 
                   `${new Date(perfResponse.startDate).toLocaleDateString('tr-TR')} - ${new Date(perfResponse.endDate).toLocaleDateString('tr-TR')} (${perfResponse.source === 'BUY_CURRENT_FALLBACK' ? 'Alış-Güncel' : perfResponse.source === 'YAHOO_INTRADAY' ? 'Gün içi' : 'Günlük'})` :
                   "Gercek portfoy degeri"}
                </div>
              </div>
              <div style={{ display: "flex", gap: 6 }}>
                {["1D", "5D", "1M", "3M", "1Y", "ALL"].map((p) => (
                  <button
                    key={p}
                    style={{
                      ...s.periodBtn,
                      ...(perfPeriod === p ? s.periodBtnActive : {}),
                    }}
                    onClick={() => setPerfPeriod(p)}
                    disabled={perfLoading}
                  >
                    {p}
                  </button>
                ))}
              </div>
            </div>
            {perfLoading ? (
              <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: 200, color: "var(--text-muted)", fontSize: 13 }}>
                Performans verileri yükleniyor...
              </div>
            ) : perfData.length >= 2 ? (
              <PortfolioAreaChart
                data={perfData}
                isIntraday={perfResponse?.granularity === "INTRADAY"}
                height={200}
              />
            ) : (
              <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: 200, color: "var(--text-muted)", fontSize: 13 }}>
                Veri yükleniyor...
              </div>
            )}
          </div>

          <div style={s.allocCard}>
            <div style={s.chartTitle}>Portfoy Dagilimi</div>
            <div style={s.chartSub}>Kategoriye gore varlik dagilimi</div>
            {allocData.length > 0 ? (
              <>
                <ResponsiveContainer width="100%" height={160}>
                  <PieChart>
                    <Pie data={allocData} cx="50%" cy="50%" innerRadius={45} outerRadius={72} paddingAngle={3} dataKey="value">
                      {allocData.map((_, i) => <Cell key={i} fill={ALLOC_COLORS[i % ALLOC_COLORS.length]} />)}
                    </Pie>
                    <Tooltip contentStyle={{ background: tooltipBg, border: "1px solid " + tooltipBorder, borderRadius: 6, color: tooltipColor, fontSize: 11 }} formatter={(v: any) => ["$" + Number(v).toLocaleString("tr-TR"), ""]} />
                  </PieChart>
                </ResponsiveContainer>
                <div style={s.legendGrid}>
                  {allocData.map((d, i) => {
                    const total = allocData.reduce((a, x) => a + x.value, 0);
                    const pct = total > 0 ? ((d.value / total) * 100).toFixed(0) : "0";
                    return (
                      <div key={d.name} style={s.legendItem}>
                        <span style={{ ...s.legendDot, background: ALLOC_COLORS[i % ALLOC_COLORS.length] }} />
                        <span style={s.legendName}>{d.name}</span>
                        <span style={s.legendPct}>{pct}%</span>
                      </div>
                    );
                  })}
                </div>
              </>
            ) : <div style={s.empty}>Veri yok</div>}
          </div>
        </div>
      )}

      {/* Holdings */}
      <div style={s.holdingsCard}>
        <div style={s.holdingsHeader}>
          <div>
            <div style={s.chartTitle}>Pozisyonlar</div>
            <div style={s.chartSub}>Mevcut yatirim pozisyonlariniz</div>
          </div>
          <button style={s.addBtn} onClick={() => { setAddSymbol(""); setAddQty(1); setAddPrice(0); setErr(null); setAddOpen(true); }}>
            + Pozisyon Ekle
          </button>
        </div>

        {loading ? (
          <div style={s.empty}>Yukleniyor...</div>
        ) : items.length === 0 ? (
          <div style={s.emptyBox}>
            <div style={{ fontWeight: 600, fontSize: 15, marginBottom: 6 }}>Henuz pozisyon yok</div>
            <div style={{ color: "var(--text-muted)", fontSize: 13 }}>Ilk pozisyonunu ekleyerek portfoyunu olustur.</div>
            <button style={{ ...s.addBtn, marginTop: 14 }} onClick={() => setAddOpen(true)}>+ Pozisyon Ekle</button>
          </div>
        ) : (
          <div style={s.tableWrap}>
            <table style={s.table}>
              <thead>
                <tr>
                  {["Sembol", "Isim", "Adet", "Alis Tarihi", "Alis Fiyati", "Guncel Fiyat", "Deger", "Toplam Degisim", "Gunluk Degisim", ""].map((h) => (
                    <th key={h} style={s.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {summaryDetail && summaryDetail.positions.length > 0 ? (
                  // Use real API data
                  summaryDetail.positions.map((pos) => {
                    const totalChangePos = pos.totalChangePercent >= 0;
                    const dailyChangePos = pos.dailyChangePercent >= 0;
                    const purchaseDate = pos.buyDate ? new Date(pos.buyDate).toLocaleDateString("tr-TR") : "-";
                    return (
                      <tr key={pos.symbol} style={s.tr}>
                        <td style={s.td}><span style={s.symbolBadge}>{pos.symbol}</span></td>
                        <td style={{ ...s.td, color: "var(--text-muted)" }}>{pos.name ?? "-"}</td>
                        <td style={s.td}>{pos.quantity.toLocaleString("tr-TR")}</td>
                        <td style={{ ...s.td, color: "var(--text-muted)", fontSize: 12 }}>{purchaseDate}</td>
                        <td style={s.td}>{pos.buyPrice > 0 ? "$" + pos.buyPrice.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}</td>
                        <td style={s.td}>{pos.currentPrice > 0 ? "$" + pos.currentPrice.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}</td>
                        <td style={{ ...s.td, fontWeight: 600 }}>{pos.currentValue > 0 ? "$" + pos.currentValue.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}</td>
                        <td style={{ ...s.td, color: totalChangePos ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                          {totalChangePos ? "+" : ""}{pos.totalChangePercent.toFixed(2)}%
                        </td>
                        <td style={{ ...s.td, color: dailyChangePos ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                          {dailyChangePos ? "▲ +" : "▼ "}{Math.abs(pos.dailyChangePercent).toFixed(2)}%
                        </td>
                        <td style={s.td}>
                          <button style={s.sellBtn} onClick={() => { 
                            const position = items.find(p => p.symbol === pos.symbol);
                            if (position) {
                              setSellTarget(position); 
                              setSellQty(1); 
                              setErr(null); 
                              setSellOpen(true); 
                            }
                          }}>Sat</button>
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  // Fallback to old data
                  items.map((p) => {
                    const qty = Number(p.quantity);
                    const cost = Number(p.avgCost ?? 0);
                    const cur = prices[p.symbol] ?? cost;
                    const value = cur * qty;
                    const change = cost > 0 ? ((cur - cost) / cost) * 100 : 0;
                    const pos = change >= 0;
                    const mkt = marketData.find((m) => m.symbol === p.symbol);
                    const dailyChangePct = mkt?.changePct ?? 0;
                    const dailyChangePos = dailyChangePct >= 0;
                    const purchaseDate = p.purchaseDate ? new Date(p.purchaseDate).toLocaleDateString("tr-TR") : "-";
                    return (
                      <tr key={p.symbol} style={s.tr}>
                        <td style={s.td}><span style={s.symbolBadge}>{p.symbol}</span></td>
                        <td style={{ ...s.td, color: "var(--text-muted)" }}>{mkt?.name ?? "-"}</td>
                        <td style={s.td}>{qty.toLocaleString("tr-TR")}</td>
                        <td style={{ ...s.td, color: "var(--text-muted)", fontSize: 12 }}>{purchaseDate}</td>
                        <td style={s.td}>{cost > 0 ? "$" + cost.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}</td>
                        <td style={s.td}>{cur > 0 ? "$" + cur.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}</td>
                        <td style={{ ...s.td, fontWeight: 600 }}>{value > 0 ? "$" + value.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"}</td>
                        <td style={{ ...s.td, color: pos ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                          {pos ? "+" : ""}{change.toFixed(2)}%
                        </td>
                        <td style={{ ...s.td, color: dailyChangePos ? "var(--green)" : "var(--red)", fontWeight: 600 }}>
                          {dailyChangePos ? "▲ +" : "▼ "}{Math.abs(dailyChangePct).toFixed(2)}%
                        </td>
                        <td style={s.td}>
                          <button style={s.sellBtn} onClick={() => { setSellTarget(p); setSellQty(1); setErr(null); setSellOpen(true); }}>Sat</button>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Add Modal */}
      <Modal open={addOpen} title="Pozisyon Ekle" onClose={() => setAddOpen(false)}
        footer={
          <>
            <button style={s.ghostBtn} onClick={() => setAddOpen(false)} disabled={addSaving}>Vazgec</button>
            <button style={s.primaryBtn} onClick={onAdd} disabled={addSaving || addPriceLoading}>
              {addSaving ? "Kaydediliyor..." : "Kaydet"}
            </button>
          </>
        }
      >
        <div style={s.formGrid}>
          <div style={{ gridColumn: "span 2", display: "grid", gap: 6 }}>
            <label style={s.label}>Sembol</label>
            <div style={{ position: "relative" }}>
              <input value={addSymbol} onChange={(e) => { setAddSymbol(e.target.value); setShowSugg(true); }}
                onFocus={() => setShowSugg(true)} onBlur={() => setTimeout(() => setShowSugg(false), 150)}
                placeholder="THYAO, AAPL, BTC/USD..." style={s.input} autoComplete="off" />
              {showSugg && suggestions.length > 0 && (
                <div style={s.dropdown}>
                  {suggestions.map((inst) => (
                    <div key={inst.symbol} style={s.dropdownItem}
                      onMouseDown={() => { setAddSymbol(inst.symbol); setShowSugg(false); }}>
                      <span style={{ fontWeight: 600 }}>{inst.symbol}</span>
                      <span style={{ color: "var(--text-muted)", fontSize: 11, marginLeft: 8 }}>{inst.name}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
          <div style={{ display: "grid", gap: 6 }}>
            <label style={s.label}>Adet</label>
            <input type="number" value={addQty} min={1} onChange={(e) => setAddQty(Number(e.target.value))} style={s.input} />
          </div>
          <div style={{ display: "grid", gap: 6 }}>
            <label style={s.label}>Guncel Fiyat</label>
            <input value={addPriceLoading ? "Yukleniyor..." : addPrice > 0 ? addPrice.toLocaleString("tr-TR") : "-"} readOnly style={{ ...s.input, opacity: 0.75 }} />
          </div>
          <div style={{ gridColumn: "span 2", display: "grid", gap: 6 }}>
            <label style={s.label}>Toplam Tutar</label>
            <input value={addTotal > 0 ? "$" + addTotal.toLocaleString("tr-TR") : "-"} readOnly style={{ ...s.input, opacity: 0.75, fontWeight: 600 }} />
          </div>
        </div>
        {err && <div style={{ color: "var(--danger-text)", marginTop: 8, fontSize: 13 }}>{err}</div>}
      </Modal>

      {/* Sell Modal */}
      <Modal open={sellOpen} title={"Sat - " + (sellTarget?.symbol ?? "")} onClose={() => setSellOpen(false)}
        footer={
          <>
            <button style={s.ghostBtn} onClick={() => setSellOpen(false)} disabled={sellSaving}>Vazgec</button>
            <button style={s.sellBtn} onClick={onSell} disabled={sellSaving}>
              {sellSaving ? "Isleniyor..." : "Sat"}
            </button>
          </>
        }
      >
        {sellTarget && (
          <div style={{ display: "grid", gap: 12 }}>
            <div style={s.infoBox}>
              <IRow label="Mevcut Adet" value={String(sellTarget.quantity)} />
              <IRow label="Alis Fiyati" value={sellTarget.avgCost ? "$" + Number(sellTarget.avgCost).toLocaleString("tr-TR") : "-"} />
              <IRow label="Guncel Fiyat" value={sellCurrentPrice > 0 ? "$" + sellCurrentPrice.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"} />
            </div>
            <div style={{ display: "grid", gap: 6 }}>
              <label style={s.label}>Satilacak Adet</label>
              <input type="number" value={sellQty} min={1} max={Number(sellTarget.quantity)} onChange={(e) => setSellQty(Number(e.target.value))} style={s.input} />
            </div>
            <div style={s.infoBox}>
              <IRow label="Elde Edilecek" value={sellProceeds > 0 ? "$" + sellProceeds.toLocaleString("tr-TR", { maximumFractionDigits: 2 }) : "-"} valueColor="var(--green)" />
            </div>
            {Number(sellQty) >= Number(sellTarget.quantity) && (
              <div style={s.warnBox}>Tum pozisyon satilacak ve portfoyden kaldirilacak.</div>
            )}
            {err && <div style={{ color: "var(--danger-text)", fontSize: 13 }}>{err}</div>}
          </div>
        )}
      </Modal>

      {/* Historical Comparison Modal */}
      <Modal 
        open={histOpen} 
        title="Geçmişten Bugüne Değişim" 
        onClose={() => {
          setHistOpen(false);
          // Don't clear state so results remain visible in the card
        }}
        footer={
          <>
            <button style={s.ghostBtn} onClick={() => setHistOpen(false)} disabled={histLoading}>
              Kapat
            </button>
            <button style={s.primaryBtn} onClick={onHistoricalCompare} disabled={histLoading}>
              {histLoading ? "Hesaplanıyor..." : "Hesapla"}
            </button>
          </>
        }
      >
        <div style={{ display: "grid", gap: 14 }}>
          <div style={{ display: "grid", gap: 6 }}>
            <label style={s.label}>Sembol (Hisse, Döviz, Değerli Maden)</label>
            <div style={{ position: "relative" }}>
              <input 
                value={histSymbol} 
                onChange={(e) => {
                  setHistSymbol(e.target.value);
                  setShowHistSugg(true);
                  setHistPrice(null);
                }}
                onFocus={() => setShowHistSugg(true)}
                onBlur={() => setTimeout(() => setShowHistSugg(false), 150)}
                placeholder="THYAO, USDTRY, XAUUSD..." 
                style={s.input} 
                autoComplete="off" 
              />
              {showHistSugg && histSuggestions.length > 0 && (
                <div style={s.dropdown}>
                  {histSuggestions.map((inst) => (
                    <div 
                      key={inst.symbol} 
                      style={s.dropdownItem}
                      onMouseDown={() => {
                        setHistSymbol(inst.symbol);
                        setShowHistSugg(false);
                        setHistPrice(null);
                      }}
                    >
                      <span style={{ fontWeight: 600 }}>{inst.symbol}</span>
                      <span style={{ color: "var(--text-muted)", fontSize: 11, marginLeft: 8 }}>{inst.name}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div style={{ display: "grid", gap: 6 }}>
            <label style={s.label}>Alış Tarihi (Geçmiş bir tarih seçin)</label>
            <input 
              type="date" 
              value={histDate} 
              max={new Date().toISOString().split('T')[0]}
              onChange={(e) => {
                setHistDate(e.target.value);
                setHistPrice(null);
              }}
              style={s.input} 
            />
          </div>

          {histError && (
            <div style={{ color: "var(--danger-text)", fontSize: 13, padding: "8px 12px", background: "var(--danger-bg)", borderRadius: 6, border: "1px solid var(--danger-border)" }}>
              {histError}
            </div>
          )}

          <div style={{ fontSize: 12, color: "var(--text-muted)", padding: "8px 12px", background: "var(--bg-panel)", borderRadius: 6, border: "1px solid var(--border-card)" }}>
            💡 Seçtiğiniz tarihten bugüne kadar olan fiyat değişimini göreceksiniz. Tarih bugünden eski olmalıdır.
          </div>
        </div>
      </Modal>
    </div>
  );
}

function SCard({ label, value, sub, valueColor }: { label: string; value: string; sub?: string; valueColor?: string }) {
  return (
    <div style={s.summaryCard}>
      <div style={s.summaryLabel}>{label}</div>
      <div style={{ ...s.summaryValue, color: valueColor ?? "var(--text-primary)" }}>{value}</div>
      {sub && <div style={s.summarySub}>{sub}</div>}
    </div>
  );
}

function IRow({ label, value, valueColor }: { label: string; value: string; valueColor?: string }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
      <span style={{ color: "var(--text-muted)", fontSize: 13 }}>{label}</span>
      <span style={{ color: valueColor ?? "var(--text-primary)", fontWeight: 600, fontSize: 13 }}>{value}</span>
    </div>
  );
}

const s: Record<string, React.CSSProperties> = {
  root: { display: "flex", flexDirection: "column", gap: 16 },
  summaryGrid: { display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 10 },
  summaryCard: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-card)", padding: "16px 18px" },
  summaryLabel: { fontSize: 12, color: "var(--text-muted)", marginBottom: 8 },
  summaryValue: { fontSize: 22, fontWeight: 700 },
  summarySub: { fontSize: 11, color: "var(--text-muted)", marginTop: 4 },
  errBox: { borderRadius: 8, border: "1px solid var(--danger-border)", background: "var(--danger-bg)", color: "var(--danger-text)", padding: "10px 14px", fontSize: 13 },
  chartsRow: { display: "grid", gridTemplateColumns: "1fr 300px", gap: 12 },
  chartCard: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-card)", padding: "16px 18px" },
  allocCard: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-card)", padding: "16px 18px" },
  chartTitle: { fontSize: 14, fontWeight: 600, color: "var(--text-primary)", marginBottom: 2 },
  chartSub: { fontSize: 12, color: "var(--text-muted)", marginBottom: 12 },
  legendGrid: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "4px 10px", marginTop: 8 },
  legendItem: { display: "flex", alignItems: "center", gap: 6 },
  legendDot: { width: 8, height: 8, borderRadius: "50%", flexShrink: 0 },
  legendName: { fontSize: 11, color: "var(--text-muted)", flex: 1 },
  legendPct: { fontSize: 11, fontWeight: 600, color: "var(--text-primary)" },
  holdingsCard: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-card)", padding: "16px 18px" },
  holdingsHeader: { display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 14 },
  tableWrap: { overflowX: "auto" },
  table: { width: "100%", borderCollapse: "collapse" },
  th: { textAlign: "left", padding: "8px 12px", fontSize: 11, fontWeight: 600, color: "var(--text-muted)", borderBottom: "1px solid var(--border)", whiteSpace: "nowrap" },
  tr: { borderBottom: "1px solid var(--border-soft)" },
  td: { padding: "10px 12px", fontSize: 13, color: "var(--text-primary)", whiteSpace: "nowrap" },
  symbolBadge: { padding: "2px 8px", borderRadius: 4, background: "rgba(37,99,235,0.15)", border: "1px solid var(--accent-border)", fontSize: 12, fontWeight: 600 },
  empty: { color: "var(--text-muted)", fontSize: 13, padding: "20px 0", textAlign: "center" },
  emptyBox: { borderRadius: 8, border: "1px dashed var(--border-card)", padding: "24px", textAlign: "center" },
  addBtn: { padding: "8px 16px", borderRadius: 8, border: "none", background: "var(--accent-solid)", color: "#fff", cursor: "pointer", fontWeight: 600, fontSize: 13 },
  sellBtn: { padding: "6px 12px", borderRadius: 6, border: "1px solid var(--danger-border)", background: "var(--danger-bg)", color: "var(--danger-text)", cursor: "pointer", fontSize: 12, fontWeight: 500 },
  ghostBtn: { padding: "8px 16px", borderRadius: 8, border: "1px solid var(--border-card)", background: "transparent", color: "var(--text-primary)", cursor: "pointer" },
  primaryBtn: { padding: "8px 16px", borderRadius: 8, border: "none", background: "var(--accent-solid)", color: "#fff", cursor: "pointer", fontWeight: 600 },
  formGrid: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 },
  label: { fontSize: 12, color: "var(--text-muted)" },
  input: { padding: "9px 12px", borderRadius: 8, border: "1px solid var(--input-border)", background: "var(--input-bg)", color: "var(--text-primary)", outline: "none", width: "100%", boxSizing: "border-box" },
  dropdown: { position: "absolute", top: "100%", left: 0, right: 0, zIndex: 100, background: "var(--dropdown-bg)", border: "1px solid var(--border-card)", borderRadius: 8, marginTop: 4, overflow: "hidden" },
  dropdownItem: { display: "flex", alignItems: "center", padding: "9px 12px", cursor: "pointer", fontSize: 13, borderBottom: "1px solid var(--border-soft)", color: "var(--text-primary)" },
  infoBox: { borderRadius: 8, border: "1px solid var(--border-card)", background: "var(--bg-panel)", padding: "10px 12px", display: "grid", gap: 8 },
  warnBox: { borderRadius: 6, border: "1px solid rgba(245,158,11,0.35)", background: "rgba(245,158,11,0.10)", color: "#fbbf24", padding: "8px 12px", fontSize: 12 },
  histCard: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-card)", padding: "16px 18px" },
  histHeader: { display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 14 },
  histTitle: { fontSize: 16, fontWeight: 700, color: "var(--text-primary)", marginBottom: 4 },
  histSub: { fontSize: 12, color: "var(--text-muted)" },
  histBtn: { padding: "8px 16px", borderRadius: 8, border: "none", background: "#3b82f6", color: "#fff", cursor: "pointer", fontWeight: 600, fontSize: 13, transition: "all 0.2s" },
  histEmpty: { display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "32px 20px", textAlign: "center" },
  histResult: { borderRadius: 8, border: "1px solid var(--border-card)", background: "var(--bg-panel)", padding: "16px" },
  histResultGrid: { display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16 },
  histResultItem: { display: "flex", flexDirection: "column", gap: 4 },
  histResultLabel: { fontSize: 11, color: "var(--text-muted)", fontWeight: 500 },
  histResultValue: { fontSize: 15, fontWeight: 600, color: "var(--text-primary)" },
  periodBtn: {
    padding: "6px 12px",
    borderRadius: 6,
    border: "1px solid var(--border-card)",
    background: "transparent",
    color: "var(--text-muted)",
    cursor: "pointer",
    fontSize: 11,
    fontWeight: 600,
    transition: "all 0.2s",
  },
  periodBtnActive: {
    border: "1px solid #3b82f6",
    background: "rgba(59, 130, 246, 0.15)",
    color: "#3b82f6",
  },
};

import { useEffect, useState } from "react";
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from "recharts";
import { PortfolioAreaChart } from "../../common/PortfolioAreaChart";
import { ALLOC_COLORS, portfolioStyles as s } from "./portfolioStyles";

// Slices smaller than this fraction of the total roll up into a single
// "Diğer" bucket. With raw data, a 1% holding next to a 99% holding is
// visually a single pixel — useless. The threshold is forgiving (2%)
// because real portfolios have a few small positions.
const SMALL_SLICE_PCT = 2;

function consolidateSmallSlices(data) {
  if (!data || data.length <= 4) return data || [];
  const total = data.reduce((s, d) => s + d.value, 0);
  if (total <= 0) return data;
  const big = [];
  let smallSum = 0;
  let smallCount = 0;
  for (const d of data) {
    if ((d.value / total) * 100 >= SMALL_SLICE_PCT) big.push(d);
    else { smallSum += d.value; smallCount++; }
  }
  if (smallCount === 0) return data;
  if (smallCount === 1) {
    // single small slice — keep it, grouping into "Diğer" with one item
    // is misleading. Just return the raw data sorted.
    return data;
  }
  big.push({ name: `Diğer (${smallCount})`, value: smallSum, isOther: true });
  return big;
}

export function PortfolioCharts({
  perfData,
  perfResponse,
  perfLoading,
  perfPeriod,
  setPerfPeriod,
  allocView,
  setAllocView,
  allocData,
}) {
  const isDark = document.documentElement.getAttribute("data-theme") !== "light";
  const tooltipBg = isDark ? "#1c2128" : "#ffffff";
  const tooltipBorder = isDark ? "#30363d" : "#d0d7de";
  const tooltipColor = isDark ? "#e6edf3" : "#1f2328";

  // Which allocation slice is shown in the donut centre. null → default to the
  // largest. Clicking a slice or a legend row selects it. Reset when the
  // grouping (symbol/type/market) changes so a stale name doesn't linger.
  const [activeName, setActiveName] = useState(null);
  useEffect(() => { setActiveName(null); }, [allocView]);

  return (
    <div style={s.chartsRow}>
      <div style={s.chartCard}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
          <div>
            <div style={s.chartTitle}>Portfoy Performansi</div>
            <div style={s.chartSub}>
              {perfLoading
                ? "Yukleniyor..."
                : perfResponse && perfResponse.points.length > 0
                  ? `${new Date(perfResponse.startDate).toLocaleDateString("tr-TR")} - ${new Date(perfResponse.endDate).toLocaleDateString("tr-TR")} (${perfResponse.source === "BUY_CURRENT_FALLBACK" ? "Alış-Güncel" : perfResponse.source === "YAHOO_INTRADAY" ? "Gün içi" : "Günlük"})`
                  : "Gercek portfoy degeri"}
            </div>
          </div>
          <div style={{ display: "flex", gap: 6 }}>
            {["1D", "5D", "1M", "3M", "1Y", "ALL"].map((period) => (
              <button
                key={period}
                style={{ ...s.periodBtn, ...(perfPeriod === period ? s.periodBtnActive : {}) }}
                onClick={() => setPerfPeriod(period)}
                disabled={perfLoading}
              >
                {period}
              </button>
            ))}
          </div>
        </div>
        {perfLoading ? (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: 200, color: "var(--text-muted)", fontSize: 13 }}>
            Performans verileri yükleniyor...
          </div>
        ) : perfData.length >= 2 ? (
          <PortfolioAreaChart data={perfData} isIntraday={perfResponse?.granularity === "INTRADAY"} height={200} />
        ) : perfData.length === 1 ? (
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: 200, color: "var(--text-muted)", fontSize: 13, gap: 8 }}>
            <div>📊</div>
            <div>Grafik için en az 2 veri noktası gerekli</div>
            <div style={{ fontSize: 11 }}>Tek veri noktası: ₺{perfData[0].value.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}</div>
          </div>
        ) : (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: 200, color: "var(--text-muted)", fontSize: 13 }}>
            Bu dönem için veri bulunamadı
          </div>
        )}
      </div>

      <div style={s.allocCard}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12, gap: 10 }}>
          <div style={{ minWidth: 0 }}>
            <div style={s.chartTitle}>Portföy Dağılımı</div>
            <div style={s.chartSub}>
              {allocView === "symbol" ? "Enstrüman bazında" : allocView === "type" ? "Varlık tipi bazında" : "Piyasa bazında"}
            </div>
          </div>
          <div style={{ display: "flex", gap: 4 }}>
            <button style={{ ...s.allocViewBtn, ...(allocView === "symbol" ? s.allocViewBtnActive : {}) }} onClick={() => setAllocView("symbol")} title="Enstrüman bazında">📊</button>
            <button style={{ ...s.allocViewBtn, ...(allocView === "type" ? s.allocViewBtnActive : {}) }} onClick={() => setAllocView("type")} title="Varlık tipi bazında">🏷️</button>
            <button style={{ ...s.allocViewBtn, ...(allocView === "market" ? s.allocViewBtnActive : {}) }} onClick={() => setAllocView("market")} title="Piyasa bazında">🌍</button>
          </div>
        </div>
        {(() => {
          if (!allocData || allocData.length === 0) {
            return <div style={s.empty}>Veri yok</div>;
          }
          // Sort by value desc so the legend reads top-to-bottom largest-first,
          // then roll up tiny slices into "Diğer" so the donut isn't a 99%
          // wedge with invisible 1% slivers.
          const sorted = [...allocData].sort((a, b) => b.value - a.value);
          const display = consolidateSmallSlices(sorted);
          const total = sorted.reduce((sum, x) => sum + x.value, 0);
          // Largest slice is the default; the user can select another by
          // clicking a slice or a legend row. Fall back to the largest when the
          // selected name isn't in the current grouping.
          const top = sorted[0];
          const active = display.find((d) => d.name === activeName) || top;
          const activePct = total > 0 ? (active.value / total) * 100 : 0;
          return (
            <>
              {/* Toplam değer — kullanıcı her şeyden önce ne kadarı toplam
                  portföy olduğunu görsün. */}
              <div style={s.allocTotalRow}>
                <span style={s.allocTotalLabel}>Toplam</span>
                <span style={s.allocTotalValue}>
                  ₺{Math.round(total).toLocaleString("tr-TR")}
                </span>
              </div>

              <div style={{ position: "relative" }}>
                <ResponsiveContainer width="100%" height={180}>
                  <PieChart>
                    <Pie
                      data={display}
                      cx="50%"
                      cy="50%"
                      innerRadius={56}
                      outerRadius={82}
                      paddingAngle={display.length > 1 ? 2 : 0}
                      dataKey="value"
                      stroke="none"
                      style={{ cursor: "pointer", outline: "none" }}
                      onClick={(d) => setActiveName(d?.name ?? d?.payload?.name ?? null)}
                    >
                      {display.map((d, index) => {
                        const isActive = d.name === active.name;
                        return (
                          <Cell key={index}
                            fill={d.isOther ? "var(--text-muted)" : ALLOC_COLORS[index % ALLOC_COLORS.length]}
                            fillOpacity={d.isOther ? 0.45 : (isActive ? 1 : 0.7)}
                            stroke={isActive ? "var(--text-primary)" : "none"}
                            strokeWidth={isActive ? 2 : 0}
                          />
                        );
                      })}
                    </Pie>
                    <Tooltip
                      contentStyle={{ background: tooltipBg, border: "1px solid " + tooltipBorder, borderRadius: 6, color: tooltipColor, fontSize: 11 }}
                      formatter={(value, name) => {
                        const pct = total > 0 ? ((value / total) * 100).toFixed(1) : "0";
                        return [`₺${Number(value).toLocaleString("tr-TR")} • %${pct}`, name];
                      }}
                    />
                  </PieChart>
                </ResponsiveContainer>
                {/* Seçili (varsayılan: en büyük) dilimi ortada özetle. */}
                {active && (
                  <div style={s.allocCenter}>
                    <div style={s.allocCenterPct}>{activePct.toFixed(0)}%</div>
                    <div style={s.allocCenterLabel}>{active.name}</div>
                  </div>
                )}
              </div>

              {/* Zenginleştirilmiş legend: sembol/etiket + ₺ değer + % */}
              <div style={s.allocLegendList}>
                {display.map((item, index) => {
                  const pct = total > 0 ? (item.value / total) * 100 : 0;
                  const color = item.isOther
                    ? "var(--text-muted)"
                    : ALLOC_COLORS[index % ALLOC_COLORS.length];
                  const isActive = item.name === active.name;
                  return (
                    <div
                      key={item.name}
                      style={{
                        ...s.allocLegendRow,
                        cursor: "pointer",
                        ...(isActive ? { background: "var(--bg-elev)", borderRadius: 6 } : {}),
                      }}
                      onClick={() => setActiveName(item.name)}
                    >
                      <span style={{ ...s.legendDot, background: color, opacity: item.isOther ? 0.45 : 1 }} />
                      <span style={{ ...s.allocLegendName, ...(isActive ? { fontWeight: 700 } : {}) }}>{item.name}</span>
                      <span style={s.allocLegendValue}>
                        ₺{Math.round(item.value).toLocaleString("tr-TR")}
                      </span>
                      <span style={s.allocLegendPct}>%{pct.toFixed(1)}</span>
                    </div>
                  );
                })}
              </div>
            </>
          );
        })()}
      </div>
    </div>
  );
}

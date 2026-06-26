import { useEffect, useState } from "react";
import { PortfolioAreaChart } from "../../common/PortfolioAreaChart";
import { ALLOC_COLORS, portfolioStyles as s } from "./portfolioStyles";
import { IconBarChart, IconTag, IconGlobe } from "../../common/icons";

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

// ── SVG donut geometry (replaces the recharts PieChart dependency) ──
const DONUT = { cx: 90, cy: 90, rOut: 82, rIn: 56 };
function donutPoint(r, angleDeg) {
  const a = ((angleDeg - 90) * Math.PI) / 180; // 0° = top, clockwise
  return [DONUT.cx + r * Math.cos(a), DONUT.cy + r * Math.sin(a)];
}
function donutSlicePath(startDeg, endDeg) {
  const { rOut, rIn } = DONUT;
  const [ox1, oy1] = donutPoint(rOut, startDeg);
  const [ox2, oy2] = donutPoint(rOut, endDeg);
  const [ix2, iy2] = donutPoint(rIn, endDeg);
  const [ix1, iy1] = donutPoint(rIn, startDeg);
  const large = endDeg - startDeg > 180 ? 1 : 0;
  return `M ${ox1} ${oy1} A ${rOut} ${rOut} 0 ${large} 1 ${ox2} ${oy2} L ${ix2} ${iy2} A ${rIn} ${rIn} 0 ${large} 0 ${ix1} ${iy1} Z`;
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
  markerDates = [],
}) {
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
        <div style={s.chartBody}>
          {perfLoading ? (
            <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: 200, color: "var(--text-muted)", fontSize: 13 }}>
              Performans verileri yükleniyor...
            </div>
          ) : perfData.length >= 2 ? (
            <>
              <PortfolioAreaChart data={perfData} isIntraday={perfResponse?.granularity === "INTRADAY"} height={200} markerDates={markerDates} />
              {markerDates.length > 0 && (
                <div style={s.markerNote}>
                  <span style={{ color: "#f59e0b", fontWeight: 700 }}>↑ Ekleme</span> işaretli noktalar yeni
                  pozisyon eklendiğini gösterir — bu yükseliş grafiğin doğal hareketi değil, portföye eklenen sermayedir.
                </div>
              )}
            </>
          ) : perfData.length === 1 ? (
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: 200, color: "var(--text-muted)", fontSize: 13, gap: 8 }}>
              <IconBarChart size={28} />
              <div>Grafik için en az 2 veri noktası gerekli</div>
              <div style={{ fontSize: 11 }}>Tek veri noktası: ₺{perfData[0].value.toLocaleString("tr-TR", { maximumFractionDigits: 2 })}</div>
            </div>
          ) : (
            <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: 200, color: "var(--text-muted)", fontSize: 13 }}>
              Bu dönem için veri bulunamadı
            </div>
          )}
        </div>
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
            <button style={{ ...s.allocViewBtn, ...(allocView === "symbol" ? s.allocViewBtnActive : {}) }} onClick={() => setAllocView("symbol")} title="Enstrüman bazında" aria-label="Enstrüman bazında"><IconBarChart /></button>
            <button style={{ ...s.allocViewBtn, ...(allocView === "type" ? s.allocViewBtnActive : {}) }} onClick={() => setAllocView("type")} title="Varlık tipi bazında" aria-label="Varlık tipi bazında"><IconTag /></button>
            <button style={{ ...s.allocViewBtn, ...(allocView === "market" ? s.allocViewBtnActive : {}) }} onClick={() => setAllocView("market")} title="Piyasa bazında" aria-label="Piyasa bazında"><IconGlobe /></button>
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
                {/* Pure-SVG donut (no recharts). Each slice is a clickable arc;
                    native <title> gives the hover tooltip. */}
                <svg viewBox="0 0 180 180" width="100%" height={180} style={{ display: "block" }}>
                  {display.length === 1 ? (
                    <circle
                      cx={DONUT.cx} cy={DONUT.cy} r={(DONUT.rOut + DONUT.rIn) / 2}
                      fill="none"
                      stroke={display[0].isOther ? "var(--text-muted)" : ALLOC_COLORS[0]}
                      strokeWidth={DONUT.rOut - DONUT.rIn}
                      style={{ cursor: "pointer" }}
                      onClick={() => setActiveName(display[0].name)}
                    >
                      <title>{`${display[0].name}: ₺${Math.round(display[0].value).toLocaleString("tr-TR")} • %100`}</title>
                    </circle>
                  ) : (() => {
                    const pad = 1; // ~2° gap between slices
                    let cursor = 0;
                    return display.map((d, index) => {
                      const frac = total > 0 ? d.value / total : 0;
                      const start = cursor;
                      const end = cursor + frac * 360;
                      cursor = end;
                      const s0 = start + pad;
                      const e0 = Math.max(s0, end - pad);
                      const isActive = d.name === active.name;
                      const color = d.isOther ? "var(--text-muted)" : ALLOC_COLORS[index % ALLOC_COLORS.length];
                      const pct = total > 0 ? ((d.value / total) * 100).toFixed(1) : "0";
                      return (
                        <path
                          key={d.name}
                          d={donutSlicePath(s0, e0)}
                          fill={color}
                          fillOpacity={d.isOther ? 0.45 : (isActive ? 1 : 0.7)}
                          stroke={isActive ? "var(--text-primary)" : "none"}
                          strokeWidth={isActive ? 2 : 0}
                          style={{ cursor: "pointer" }}
                          onClick={() => setActiveName(d.name)}
                        >
                          <title>{`${d.name}: ₺${Math.round(d.value).toLocaleString("tr-TR")} • %${pct}`}</title>
                        </path>
                      );
                    });
                  })()}
                </svg>
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
                      role="button"
                      tabIndex={0}
                      style={{
                        ...s.allocLegendRow,
                        cursor: "pointer",
                        ...(isActive ? { background: "var(--bg-elev)", borderRadius: 6 } : {}),
                      }}
                      onClick={() => setActiveName(item.name)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          setActiveName(item.name);
                        }
                      }}
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

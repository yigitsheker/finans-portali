import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from "recharts";
import { PortfolioAreaChart, type ChartPoint } from "../../common/PortfolioAreaChart";
import type { PortfolioPerformanceResponse } from "../../../api/portfolioApi";
import { ALLOC_COLORS, portfolioStyles as s } from "./portfolioStyles";

export type AllocationView = "symbol" | "type" | "market";

type AllocationItem = {
  name: string;
  value: number;
};

type PortfolioChartsProps = {
  perfData: ChartPoint[];
  perfResponse: PortfolioPerformanceResponse | null;
  perfLoading: boolean;
  perfPeriod: string | null;
  setPerfPeriod: (period: string) => void;
  allocView: AllocationView;
  setAllocView: (view: AllocationView) => void;
  allocData: AllocationItem[];
};

export function PortfolioCharts({
  perfData,
  perfResponse,
  perfLoading,
  perfPeriod,
  setPerfPeriod,
  allocView,
  setAllocView,
  allocData,
}: PortfolioChartsProps) {
  const isDark = document.documentElement.getAttribute("data-theme") !== "light";
  const tooltipBg = isDark ? "#1c2128" : "#ffffff";
  const tooltipBorder = isDark ? "#30363d" : "#d0d7de";
  const tooltipColor = isDark ? "#e6edf3" : "#1f2328";

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
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
          <div>
            <div style={s.chartTitle}>Portfoy Dagilimi</div>
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
        {allocData.length > 0 ? (
          <>
            <ResponsiveContainer width="100%" height={160}>
              <PieChart>
                <Pie data={allocData} cx="50%" cy="50%" innerRadius={45} outerRadius={72} paddingAngle={3} dataKey="value">
                  {allocData.map((_, index) => <Cell key={index} fill={ALLOC_COLORS[index % ALLOC_COLORS.length]} />)}
                </Pie>
                <Tooltip
                  contentStyle={{ background: tooltipBg, border: "1px solid " + tooltipBorder, borderRadius: 6, color: tooltipColor, fontSize: 11 }}
                  formatter={(value: any) => ["₺" + Number(value).toLocaleString("tr-TR"), ""]}
                />
              </PieChart>
            </ResponsiveContainer>
            <div style={s.legendGrid}>
              {allocData.map((item, index) => {
                const total = allocData.reduce((sum, current) => sum + current.value, 0);
                const pct = total > 0 ? ((item.value / total) * 100).toFixed(0) : "0";
                return (
                  <div key={item.name} style={s.legendItem}>
                    <span style={{ ...s.legendDot, background: ALLOC_COLORS[index % ALLOC_COLORS.length] }} />
                    <span style={s.legendName}>{item.name}</span>
                    <span style={s.legendPct}>{pct}%</span>
                  </div>
                );
              })}
            </div>
          </>
        ) : <div style={s.empty}>Veri yok</div>}
      </div>
    </div>
  );
}

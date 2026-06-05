import { AddPositionModal } from "../components/features/portfolio/AddPositionModal";
import { PortfolioCharts } from "../components/features/portfolio/PortfolioCharts";
import { PositionsTable } from "../components/features/portfolio/PositionsTable";
import PortfolioDerivatives from "../components/features/portfolio/PortfolioDerivatives";
import { SellPositionModal } from "../components/features/portfolio/SellPositionModal";
import { SummaryCards } from "../components/features/portfolio/SummaryCards";
import { portfolioStyles as s } from "../components/features/portfolio/portfolioStyles";
import { usePortfolioPage } from "../hooks/usePortfolioPage";
import { useI18n } from "../contexts/I18nContext";
import notify from "../utils/notify";
import { useEffect, useMemo, useState } from "react";
import { parsePortfolioExcel } from "../utils/excelImport";
import { getMarketInstruments } from "../api/portfolioApi";
import ImportPreviewModal from "../components/ImportPreviewModal";
import InstrumentChartModal from "../components/InstrumentChartModal";
import CompareInstrumentsModal from "../components/CompareInstrumentsModal";

export default function Portfolio({ keycloak }) {
  const portfolio = usePortfolioPage(keycloak);
  const { t } = useI18n();
  const [previewRows, setPreviewRows] = useState(null);
  const [catalog, setCatalog] = useState([]);
  // Clicking a position row opens its chart card (same modal as the Stocks page).
  const [selectedInstrument, setSelectedInstrument] = useState(null);
  const [compareTarget, setCompareTarget] = useState(null);

  const openChart = (symbol) => {
    const inst = portfolio.marketData?.find((m) => m.symbol === symbol)
      || catalog.find((i) => i.symbol === symbol)
      || { symbol };
    setSelectedInstrument(inst);
  };

  useEffect(() => { if (portfolio.instruments?.length) setCatalog(portfolio.instruments); }, [portfolio.instruments]);
  const validSymbols = useMemo(
    () => new Set(catalog.map((i) => String(i.symbol).toUpperCase())),
    [catalog],
  );

  // Excel pick → parse → open the editable preview (so missing cells can be
  // filled in). The actual import runs on confirm.
  const handleImport = async (file) => {
    if (!file) return;
    const parsed = await parsePortfolioExcel(file, { requireDate: false });
    if (!parsed.ok) { notify(t("portfolio.importUnreadable"), { variant: "error" }); return; }
    if (!parsed.rows.length) { notify(t("portfolio.importEmpty"), { variant: "warning" }); return; }
    // Ensure the catalog is loaded so the preview flags unknown symbols (rather
    // than showing them green and silently skipping them on confirm).
    if (!catalog.length) {
      const fetched = await getMarketInstruments().catch(() => []);
      if (fetched.length) setCatalog(fetched);
    }
    setPreviewRows(parsed.rows);
  };

  const handleConfirmImport = async (rows) => {
    const res = await portfolio.importRows(rows);
    setPreviewRows(null);
    if (res.imported === 0 && res.skipped === 0) { notify(t("portfolio.importEmpty"), { variant: "warning" }); return; }
    notify(t("portfolio.importDone", { imported: res.imported, skipped: res.skipped }),
      { variant: res.imported > 0 ? "success" : "warning" });
  };

  // Pulled out of summaryDetail so the SummaryCards header can show
  // "Güncelleme: HH:mm" and the warnings banner can render any
  // server-side advisories (e.g. an instrument missing from the catalog).
  const asOf = portfolio.summaryDetail?.asOf ?? null;
  const warnings = portfolio.summaryDetail?.warnings ?? [];
  const isFallbackPerf = portfolio.perfResponse?.source === "BUY_CURRENT_FALLBACK";

  return (
    <div style={s.root}>
      <SummaryCards
        stats={portfolio.stats}
        loading={portfolio.loading}
        error={portfolio.err}
        asOf={asOf}
        onRefresh={portfolio.refresh}
        refreshing={portfolio.loading}
      />

      {portfolio.err && <div style={s.errBox}>{portfolio.err}</div>}

      {/* Server-side advisories — surfaces what would otherwise be silent
          (e.g. a position whose instrument was deleted from the catalog). */}
      {warnings.length > 0 && (
        <div style={styles.warnBanner} role="alert">
          <strong style={{ marginRight: 6 }}>⚠️ Bazı pozisyonlar gösterilmiyor:</strong>
          <ul style={styles.warnList}>
            {warnings.map((w, i) => (<li key={i}>{w}</li>))}
          </ul>
        </div>
      )}

      {!portfolio.loading && portfolio.items.length > 0 && (
        <>
          {/* Performance fallback hint — the backend marks the response as
              BUY_CURRENT_FALLBACK when it could only build a 2-point line
              (cost basis vs. current). Without this hint, the chart looks
              like real historical data and misleads the user. */}
          {isFallbackPerf && (
            <div style={styles.infoBanner}>
              ℹ️ Geçmiş veri bulunamadığı için grafik sadece alış fiyatı ve güncel fiyat ile çiziliyor.
              Fiyat geçmişi cache'lendikten sonra tam grafik görünecek.
            </div>
          )}
          <PortfolioCharts
            perfData={portfolio.perfData}
            perfResponse={portfolio.perfResponse}
            perfLoading={portfolio.perfLoading}
            perfPeriod={portfolio.perfPeriod}
            setPerfPeriod={portfolio.setPerfPeriod}
            allocView={portfolio.allocView}
            setAllocView={portfolio.setAllocView}
            allocData={portfolio.allocData}
          />
        </>
      )}

      <PositionsTable
        loading={portfolio.loading}
        items={portfolio.items}
        prices={portfolio.prices}
        marketData={portfolio.marketData}
        summaryDetail={portfolio.summaryDetail}
        openAdd={portfolio.openAddModal}
        openSell={portfolio.openSellModal}
        onImport={handleImport}
        importing={portfolio.importing}
        onRowClick={openChart}
      />

      {/* VİOP & bond/bill positions — shown separately (different economics
          from qty×price holdings), each with its own metrics + simulation note. */}
      <PortfolioDerivatives keycloak={keycloak} />

      <AddPositionModal
        open={portfolio.addOpen}
        symbol={portfolio.addSymbol}
        quantity={portfolio.addQty}
        price={portfolio.addPrice}
        total={portfolio.addTotal}
        priceLoading={portfolio.addPriceLoading}
        saving={portfolio.addSaving}
        showSuggestions={portfolio.showSugg}
        suggestions={portfolio.suggestions}
        error={portfolio.err}
        setSymbol={portfolio.setAddSymbol}
        setQuantity={portfolio.setAddQty}
        setShowSuggestions={portfolio.setShowSugg}
        onPickSuggestion={portfolio.pickSuggestion}
        onSave={portfolio.onAdd}
        onClose={portfolio.closeAddModal}
        inputMode={portfolio.addInputMode}
        amount={portfolio.addAmount}
        effectiveQty={portfolio.addEffectiveQty}
        amountLeftover={portfolio.addAmountLeftover}
        setInputMode={portfolio.setAddInputMode}
        setAmount={portfolio.setAddAmount}
      />

      <SellPositionModal
        open={portfolio.sellOpen}
        target={portfolio.sellTarget}
        quantity={portfolio.sellQty}
        currentPrice={portfolio.sellCurrentPrice}
        proceeds={portfolio.sellProceeds}
        saving={portfolio.sellSaving}
        error={portfolio.err}
        setQuantity={portfolio.setSellQty}
        onSell={portfolio.onSell}
        onClose={portfolio.closeSellModal}
      />

      <ImportPreviewModal
        open={!!previewRows}
        initialRows={previewRows || []}
        requireDate={false}
        validSymbols={validSymbols}
        importing={portfolio.importing}
        onConfirm={handleConfirmImport}
        onClose={() => { if (!portfolio.importing) setPreviewRows(null); }}
      />

      {/* Chart card — opens when a position row is clicked (same as Stocks). */}
      <InstrumentChartModal
        instrument={selectedInstrument}
        onClose={() => setSelectedInstrument(null)}
        keycloak={keycloak}
        onCompare={(inst) => { setSelectedInstrument(null); setCompareTarget(inst); }}
      />
      <CompareInstrumentsModal
        baseInstrument={compareTarget}
        onClose={() => setCompareTarget(null)}
      />
    </div>
  );
}

const styles = {
  warnBanner: {
    padding: "10px 14px",
    background: "rgba(245, 158, 11, 0.10)",
    color: "var(--text-primary)",
    border: "1px solid rgba(245, 158, 11, 0.35)",
    borderRadius: 8,
    fontSize: 13,
    marginBottom: 12,
  },
  warnList: {
    margin: "4px 0 0 18px",
    padding: 0,
    color: "var(--text-muted)",
  },
  infoBanner: {
    padding: "8px 12px",
    background: "rgba(59, 130, 246, 0.10)",
    color: "var(--text-primary)",
    border: "1px solid rgba(59, 130, 246, 0.35)",
    borderRadius: 8,
    fontSize: 12,
    marginBottom: 12,
  },
};

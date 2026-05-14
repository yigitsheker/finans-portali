import { AddPositionModal } from "../components/features/portfolio/AddPositionModal";
import { PortfolioCharts } from "../components/features/portfolio/PortfolioCharts";
import { PositionsTable } from "../components/features/portfolio/PositionsTable";
import { SellPositionModal } from "../components/features/portfolio/SellPositionModal";
import { SummaryCards } from "../components/features/portfolio/SummaryCards";
import { portfolioStyles as s } from "../components/features/portfolio/portfolioStyles";
import { usePortfolioPage } from "../hooks/usePortfolioPage";

export default function Portfolio({ keycloak }) {
  const portfolio = usePortfolioPage(keycloak);

  return (
    <div style={s.root}>
      <SummaryCards stats={portfolio.stats} loading={portfolio.loading} error={portfolio.err} />

      {portfolio.err && <div style={s.errBox}>{portfolio.err}</div>}

      {!portfolio.loading && portfolio.items.length > 0 && (
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
      )}

      <PositionsTable
        loading={portfolio.loading}
        items={portfolio.items}
        prices={portfolio.prices}
        marketData={portfolio.marketData}
        summaryDetail={portfolio.summaryDetail}
        openAdd={portfolio.openAddModal}
        openSell={portfolio.openSellModal}
      />

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
    </div>
  );
}

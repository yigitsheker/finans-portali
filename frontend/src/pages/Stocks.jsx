import FinexStyleMarket from "../components/FinexStyleMarket";

/**
 * Stocks page — just the market table now. The watchlist ("Takip Listem") tab
 * moved to its own navbar entry (/lists → Watchlists.jsx) so lists aren't tied
 * to the stocks view.
 */
export default function Stocks({ keycloak, onAdded }) {
    return <FinexStyleMarket keycloak={keycloak} onAdded={onAdded} filterType="STOCK" />;
}

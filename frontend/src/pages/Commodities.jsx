import FinexStyleMarket from "../components/FinexStyleMarket";

export default function Commodities({ keycloak, onAdded }) {
    return <FinexStyleMarket keycloak={keycloak} onAdded={onAdded} filterType="COMMODITY" />;
}

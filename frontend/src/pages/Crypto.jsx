import FinexStyleMarket from "../components/FinexStyleMarket";

export default function Crypto({ keycloak, onAdded }) {
    return <FinexStyleMarket keycloak={keycloak} onAdded={onAdded} filterType="CRYPTO" />;
}

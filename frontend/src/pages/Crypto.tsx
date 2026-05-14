import type Keycloak from "keycloak-js";
import FinexStyleMarket from "../components/FinexStyleMarket";

type Props = { 
    keycloak: Keycloak; 
    onAdded: () => void;
};

export default function Crypto({ keycloak, onAdded }: Props) {
    return <FinexStyleMarket keycloak={keycloak} onAdded={onAdded} filterType="CRYPTO" />;
}

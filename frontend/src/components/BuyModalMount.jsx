import PropTypes from "prop-types";
import AddPositionModal from "./AddPositionModal";

/**
 * Tiny wrapper around AddPositionModal that swallows the boilerplate
 * every "list" page used to repeat — open flag tied to buyTarget,
 * close via clearBuy, onCreated chains clearBuy + the page's onAdded
 * refresh hook, and the seed props pulled off the target shape used
 * by useBuyTarget.
 *
 * Caller passes:
 *   target    — the buyTarget the useBuyTarget hook returns (or null)
 *   clear     — the clearBuy callback from the same hook
 *   keycloak  — the auth handle for the POST
 *   onAdded   — page-level "refresh data" callback (optional)
 *
 * The four list pages (Bonds, Funds, MarketData, Viop) previously
 * each spelled out the same ~10-line block. SonarCloud's duplicate
 * detector flagged the cluster.
 */
export default function BuyModalMount({ target, clear, keycloak, onAdded }) {
    return (
        <AddPositionModal
            open={!!target}
            onClose={clear}
            onCreated={() => {
                clear();
                if (typeof onAdded === "function") onAdded();
            }}
            keycloak={keycloak}
            initialSymbol={target?.symbol ?? ""}
            initialPrice={target?.price ?? ""}
            contractMultiplier={target?.multiplier ?? 1}
        />
    );
}

BuyModalMount.propTypes = {
    target: PropTypes.shape({
        symbol: PropTypes.string,
        price: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
        multiplier: PropTypes.number,
    }),
    clear: PropTypes.func.isRequired,
    keycloak: PropTypes.object,
    onAdded: PropTypes.func,
};

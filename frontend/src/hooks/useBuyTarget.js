import { useCallback, useState } from "react";
import { useI18n } from "../contexts/I18nContext";

/**
 * Shared buy-modal state + auth guard used by every list page that wires
 * an "Al" button (Stocks/Crypto/Commodities live in FinexStyleMarket's
 * own openBuyModalIfAuthed; Bonds / Funds / MarketData / Viop all share
 * this hook).
 *
 * Returns a [buyTarget, openBuy, clear] tuple. `openBuy(payload)` checks
 * keycloak.authenticated — anonymous callers get a confirm prompt and
 * redirect to Keycloak login on accept (mirrors FinexStyleMarket's
 * inline guard). Authenticated callers get their payload stashed as
 * the buy target, ready to be passed into AddPositionModal.
 *
 * The `payload` shape is whatever the caller wants; AddPositionModal
 * only reads `payload.symbol` / `payload.price` / `payload.multiplier`
 * — extra fields are harmless.
 */
export function useBuyTarget(keycloak) {
    const { t } = useI18n();
    const [buyTarget, setBuyTarget] = useState(null);

    const openBuy = useCallback((payload) => {
        const authed = keycloak?.authenticated === true;
        if (!authed) {
            const goLogin = window.confirm(t("market.authPrompt"));
            if (goLogin && keycloak?.login) {
                keycloak.login({ redirectUri: window.location.href });
            }
            return;
        }
        setBuyTarget(payload);
    }, [keycloak, t]);

    const clear = useCallback(() => setBuyTarget(null), []);

    return [buyTarget, openBuy, clear];
}

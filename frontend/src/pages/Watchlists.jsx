import WatchlistManager from "../components/WatchlistManager";
import { useI18n } from "../contexts/I18nContext";

/**
 * Dedicated "Listelerim" (my watchlists) page, reached from the navbar. The
 * watchlist manager used to live behind a tab on the Stocks page; it now has
 * its own route so lists span all asset types, not just stocks.
 */
export default function Watchlists({ keycloak }) {
    const { t } = useI18n();

    if (keycloak.authenticated) {
        return (
            <div style={{ padding: "20px" }}>
                <h1 style={{ fontSize: 24, fontWeight: 800, color: "var(--text-primary)", margin: "0 0 16px" }}>
                    {t("nav.lists")}
                </h1>
                <WatchlistManager keycloak={keycloak} />
            </div>
        );
    }

    return (
        <div style={{ padding: 60, textAlign: "center", maxWidth: 480, margin: "0 auto" }}>
            <div style={{ fontSize: 56 }}>⭐</div>
            <h3 style={{ color: "var(--text-primary)", marginTop: 16 }}>{t("stocks.loginNeeded")}</h3>
            <p style={{ color: "var(--text-muted)", lineHeight: 1.6 }}>{t("stocks.loginNeededSub")}</p>
            <div style={{ display: "flex", gap: 10, justifyContent: "center", marginTop: 16, flexWrap: "wrap" }}>
                <button
                    onClick={() => keycloak.login({ redirectUri: window.location.href })}
                    style={{ padding: "10px 24px", borderRadius: 10, border: "1px solid var(--accent-solid)", background: "var(--accent-solid)", color: "#000", fontSize: 14, fontWeight: 700, cursor: "pointer" }}
                >
                    {t("topbar.login")}
                </button>
                <button
                    onClick={() => keycloak.register({ redirectUri: window.location.href })}
                    style={{ padding: "10px 24px", borderRadius: 10, border: "1px solid var(--border-card)", background: "transparent", color: "var(--text-primary)", fontSize: 14, fontWeight: 600, cursor: "pointer" }}
                >
                    {t("common.register")}
                </button>
            </div>
        </div>
    );
}

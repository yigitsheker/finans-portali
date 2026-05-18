import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
    url: "http://localhost:8090",
    realm: "finans",
    clientId: "finans-frontend",
});

/**
 * Forward the user's current app theme (light/dark) to Keycloak's hosted
 * login page as a URL parameter. Keycloak runs on a different origin so it
 * can't see our localStorage; the URL is the only sideband channel.
 *
 * keycloak-js builds the authorize URL internally via {@code createLoginUrl}
 * / {@code createRegisterUrl} before redirecting, so patching those two
 * methods is enough to cover every {@code keycloak.login()} /
 * {@code keycloak.register()} call site without touching them.
 */
const STORAGE_KEY = "theme";
const DARK_QUERY = "(prefers-color-scheme: dark)";

function resolveTheme() {
    let stored;
    try { stored = localStorage.getItem(STORAGE_KEY); } catch { stored = null; }
    if (stored === "light") return "light";
    if (stored === "dark") return "dark";
    // "system" or anything unrecognized: defer to the OS preference,
    // matching the in-app applyTheme() behaviour in theme.js.
    try {
        if (window.matchMedia && window.matchMedia(DARK_QUERY).matches) return "dark";
    } catch {}
    return "light";
}

function withTheme(url) {
    if (!url) return url;
    const sep = url.includes("?") ? "&" : "?";
    return `${url}${sep}kc_theme=${resolveTheme()}`;
}

const origLogin = keycloak.createLoginUrl.bind(keycloak);
const origRegister = keycloak.createRegisterUrl.bind(keycloak);
keycloak.createLoginUrl = (opts) => withTheme(origLogin(opts));
keycloak.createRegisterUrl = (opts) => withTheme(origRegister(opts));

export default keycloak;

import Keycloak from "keycloak-js";

// Keycloak base URL is resolved at RUNTIME so the same built image works in
// any environment. /runtime-config.js (loaded before the app bundle — see
// index.html) sets window.__RUNTIME_CONFIG__; container deploys replace that
// file with a ConfigMap carrying the public Keycloak URL. Falls back to the
// build-time env var, then the local dev server.
const RUNTIME = (typeof window !== "undefined" && window.__RUNTIME_CONFIG__) || {};
const keycloak = new Keycloak({
    url: RUNTIME.VITE_KEYCLOAK_URL || import.meta.env.VITE_KEYCLOAK_URL || "http://localhost:8090",
    realm: "finans",
    clientId: "finans-frontend",
});

/**
 * Forward the user's current app theme (light/dark) AND language (tr/en) to
 * Keycloak's hosted login page as URL parameters. Keycloak runs on a
 * different origin so it can't see our localStorage; the URL is the only
 * sideband channel.
 *
 * keycloak-js builds the authorize URL internally via {@code createLoginUrl}
 * / {@code createRegisterUrl} before redirecting, so patching those two
 * methods is enough to cover every {@code keycloak.login()} /
 * {@code keycloak.register()} call site without touching them.
 */
const THEME_KEY = "theme";
const LANG_KEY = "i18n-lang";
const DARK_QUERY = "(prefers-color-scheme: dark)";

function resolveTheme() {
    let stored;
    try { stored = localStorage.getItem(THEME_KEY); } catch { stored = null; }
    if (stored === "light") return "light";
    if (stored === "dark") return "dark";
    // "system" or anything unrecognized: defer to the OS preference,
    // matching the in-app applyTheme() behaviour in theme.js.
    try {
        if (window.matchMedia && window.matchMedia(DARK_QUERY).matches) return "dark";
    } catch {}
    return "light";
}

function resolveLocale() {
    let stored;
    try { stored = localStorage.getItem(LANG_KEY); } catch { stored = null; }
    if (stored === "tr" || stored === "en") return stored;
    // Fall back to browser language so a first-time visitor sees the
    // login page in something close to their preferred tongue.
    try {
        const nav = (navigator.language || "").toLowerCase();
        if (nav.startsWith("en")) return "en";
    } catch {}
    return "tr";
}

function decorate(url) {
    if (!url) return url;
    const lang = resolveLocale();
    let out = url;
    const sep1 = out.includes("?") ? "&" : "?";
    out = `${out}${sep1}kc_theme=${resolveTheme()}`;
    // ui_locales is the OIDC standard the initial /auth endpoint honours;
    // kc_locale is the Keycloak-specific override that sticks across steps.
    // Pass both so the language follows the user end-to-end.
    out = `${out}&ui_locales=${lang}&kc_locale=${lang}`;
    return out;
}

// keycloak-js 26.x makes createLoginUrl / createRegisterUrl async, so the
// patched wrappers must await before string-concatenating the params.
// Without the await our previous version returned `decorate(Promise)` →
// `Promise.includes("?")` threw silently and the redirect never happened.
const origLogin = keycloak.createLoginUrl.bind(keycloak);
const origRegister = keycloak.createRegisterUrl.bind(keycloak);
keycloak.createLoginUrl = async (opts) => decorate(await origLogin(opts));
keycloak.createRegisterUrl = async (opts) => decorate(await origRegister(opts));

export default keycloak;

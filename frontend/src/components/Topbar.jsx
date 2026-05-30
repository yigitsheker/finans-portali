import { useEffect, useState } from "react";
import PropTypes from "prop-types";
import { Link, useLocation } from "react-router-dom";
import { createPortal } from "react-dom";
import NotificationBell from "./NotificationBell";
import { useCurrencyDisplay } from "../contexts/CurrencyDisplayContext";
import { useI18n } from "../contexts/I18nContext";
import { isAdmin } from "../utils/roleUtils";

/**
 * Inline navigation items shown in the top bar.
 *
 * Three tiers, all rendered as flat chips in the nav strip:
 *   PUBLIC_NAV  — always visible. Market & content pages.
 *   PRIVATE_NAV — shown only to authenticated users (per-user dashboards).
 *   ADMIN_NAV   — shown only to users with the ADMIN realm role.
 *
 * Labels are i18n keys; `useI18n().t()` resolves them per the active language.
 */
const PUBLIC_NAV = [
  { to: "/",            key: "nav.home" },
  { to: "/stocks",      key: "nav.stocks" },
  { to: "/crypto",      key: "nav.crypto" },
  { to: "/funds",       key: "nav.funds" },
  { to: "/bonds",       key: "nav.bonds" },
  { to: "/market-data", key: "nav.fx" },
  { to: "/commodities", key: "nav.commodities" },
  { to: "/viop",        key: "nav.viop" },
  { to: "/inflation",   key: "nav.inflation" },
  { to: "/news",        key: "nav.news" },
];

const PRIVATE_NAV = [
  { to: "/analysis",   key: "nav.analysis" },
  { to: "/portfolio",  key: "nav.portfolio" },
  { to: "/historical", key: "nav.historical" },
  { to: "/settings",   key: "nav.settings" },
];

const ADMIN_NAV = [
  { to: "/admin", key: "nav.admin" },
];

function CurrencyToggle() {
  const { mode, setMode } = useCurrencyDisplay();
  const { t } = useI18n();
  const opts = [
    { v: "original", label: t("topbar.currencyOriginal"), title: t("topbar.currencyOriginalTitle") },
    { v: "TRY",      label: "₺",                          title: t("topbar.currencyTryTitle") },
    { v: "USD",      label: "$",                          title: t("topbar.currencyUsdTitle") },
  ];
  return (
    <div style={ctgl.wrap} role="group" aria-label={t("topbar.currencyMode")}>
      {opts.map((o) => (
        <button
          key={o.v}
          type="button"
          onClick={() => setMode(o.v)}
          title={o.title}
          style={{ ...ctgl.btn, ...(mode === o.v ? ctgl.btnActive : {}) }}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

function LanguageToggle() {
  const { lang, setLang, t } = useI18n();
  const opts = [
    { v: "tr", label: "TR", title: t("topbar.langTrTitle") },
    { v: "en", label: "EN", title: t("topbar.langEnTitle") },
  ];
  return (
    <div style={ctgl.wrap} role="group" aria-label={t("topbar.langMode")}>
      {opts.map((o) => (
        <button
          key={o.v}
          type="button"
          onClick={() => setLang(o.v)}
          title={o.title}
          style={{ ...ctgl.btn, ...(lang === o.v ? ctgl.btnActive : {}) }}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

const ctgl = {
  wrap: {
    display: "inline-flex",
    background: "var(--input-bg)",
    border: "1px solid var(--border-card)",
    borderRadius: 10,
    padding: 2,
  },
  btn: {
    border: "none",
    background: "transparent",
    color: "var(--text-muted)",
    padding: "6px 12px",
    borderRadius: 8,
    fontSize: 12,
    fontWeight: 600,
    cursor: "pointer",
    minWidth: 36,
  },
  btnActive: {
    background: "var(--accent-hover-bg)",
    color: "var(--accent-solid)",
    boxShadow: "0 1px 4px rgba(0,0,0,0.20)",
  },
};

export default function Topbar({
  isAuthenticated,
  onLogin,
  onLogout,
  theme,
  onThemeToggle,
  onAlertsClick,
  showAlerts,
  keycloak,
}) {
  const location = useLocation();
  const { t } = useI18n();
  const isDark = theme === "dark" ||
    (theme === "system" && typeof window !== "undefined" && window.matchMedia
      && window.matchMedia("(prefers-color-scheme: dark)").matches);

  // Mobile drawer state. The CSS hides the inline nav at <=900px and shows
  // the hamburger; clicking opens this drawer with the full nav inside.
  const [mobileOpen, setMobileOpen] = useState(false);
  // Close the drawer on route change so a click inside the drawer never
  // leaves it covering the page.
  useEffect(() => { setMobileOpen(false); }, [location.pathname]);
  // Lock background scroll while the drawer is open.
  useEffect(() => {
    if (!mobileOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.body.style.overflow = prev; };
  }, [mobileOpen]);

  // Active link matcher — "/" matches only the exact home route to avoid
  // every page lighting up "Anasayfa". Everything else uses prefix match
  // so /stocks/AAPL still highlights "Hisseler".
  const isActive = (to) => to === "/"
    ? location.pathname === "/"
    : location.pathname.startsWith(to);

  const allNavItems = [
    ...PUBLIC_NAV,
    ...(isAuthenticated ? PRIVATE_NAV : []),
    ...(isAuthenticated && isAdmin(keycloak) ? ADMIN_NAV : []),
  ];

  // Drawer rendered via portal so its fixed positioning isn't affected by
  // the topbar's sticky context (which can clip pointer events).
  const mobileDrawer = createPortal(
    <>
      <button
        type="button"
        className="fp-topbar-hamburger"
        onClick={() => setMobileOpen((v) => !v)}
        aria-label={mobileOpen ? t("topbar.menuClose") : t("topbar.menuOpen")}
        aria-expanded={mobileOpen}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          {mobileOpen ? (
            <>
              <line x1="6" y1="6" x2="18" y2="18" />
              <line x1="6" y1="18" x2="18" y2="6" />
            </>
          ) : (
            <>
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </>
          )}
        </svg>
      </button>

      <div
        className={`fp-topbar-backdrop ${mobileOpen ? "is-visible" : ""}`}
        onClick={() => setMobileOpen(false)}
        aria-hidden="true"
      />

      <aside className={`fp-topbar-drawer ${mobileOpen ? "is-open" : ""}`} aria-hidden={!mobileOpen}>
        <div style={s.drawerHeader}>
          <Link to="/" style={s.brand} onClick={() => setMobileOpen(false)}>
            <span style={s.brandLogo}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5">
                <polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/>
                <polyline points="16 7 22 7 22 13"/>
              </svg>
            </span>
            <span style={s.brandText}>
              <span style={s.brandName}>Piyasa</span>
              <span style={s.brandTag}>FINANS PORTALI</span>
            </span>
          </Link>
        </div>
        <nav style={s.drawerNav} aria-label={t("topbar.mainMenu")}>
          {allNavItems.map((item) => {
            const active = isActive(item.to);
            const isAdminItem = ADMIN_NAV.includes(item);
            return (
              <Link
                key={item.to}
                to={item.to}
                style={{
                  ...s.drawerLink,
                  ...(active ? s.drawerLinkActive : {}),
                  ...(isAdminItem ? s.drawerLinkAdmin : {}),
                }}
                onClick={() => setMobileOpen(false)}
              >
                {t(item.key)}
              </Link>
            );
          })}
        </nav>
      </aside>
    </>,
    document.body
  );

  return (
    <div style={s.row} className="fp-topbar">
      {mobileDrawer}

      {/* Brand */}
      <Link to="/" style={s.brand} className="fp-topbar-brand">
        <span style={s.brandLogo}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5">
            <polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/>
            <polyline points="16 7 22 7 22 13"/>
          </svg>
        </span>
        <span style={s.brandText}>
          <span style={s.brandName}>Piyasa</span>
          <span style={s.brandTag} className="fp-topbar-brand-tag">FINANS PORTALI</span>
        </span>
      </Link>

      {/* Inline nav links. Private items show only when authenticated;
          admin items show only when the user carries the ADMIN realm role. */}
      <nav style={s.nav} className="fp-topbar-nav" aria-label={t("topbar.mainMenu")}>
        {PUBLIC_NAV.map((item) => {
          const active = isActive(item.to);
          return (
            <Link
              key={item.to}
              to={item.to}
              style={{ ...s.navLink, ...(active ? s.navLinkActive : {}) }}
            >
              {t(item.key)}
            </Link>
          );
        })}
        {isAuthenticated && PRIVATE_NAV.map((item) => {
          const active = isActive(item.to);
          return (
            <Link
              key={item.to}
              to={item.to}
              style={{ ...s.navLink, ...(active ? s.navLinkActive : {}) }}
            >
              {t(item.key)}
            </Link>
          );
        })}
        {isAuthenticated && isAdmin(keycloak) && ADMIN_NAV.map((item) => {
          const active = isActive(item.to);
          return (
            <Link
              key={item.to}
              to={item.to}
              style={{ ...s.navLink, ...s.navLinkAdmin, ...(active ? s.navLinkAdminActive : {}) }}
            >
              {t(item.key)}
            </Link>
          );
        })}
      </nav>

      {/* Action cluster */}
      <div style={s.right} className="fp-topbar-actions">
        <CurrencyToggle />
        <LanguageToggle />

        {/* Notification bell — hidden for admin (their own test pings) */}
        {isAuthenticated && !isAdmin(keycloak) && <NotificationBell keycloak={keycloak} />}

        {showAlerts && onAlertsClick && (
          <button style={s.iconBtn} onClick={onAlertsClick} title={t("topbar.priceAlertCreate")} aria-label={t("topbar.priceAlert")}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 5V3M12 5C8.69 5 6 7.69 6 11V16L4 18V19H20V18L18 16V11C18 7.69 15.31 5 12 5Z"
                    stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
              <path d="M9 21H15" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
              <circle cx="18" cy="6" r="3" fill="var(--accent-solid)"/>
            </svg>
          </button>
        )}

        <button
          style={s.iconBtn}
          onClick={onThemeToggle}
          title={isDark ? t("topbar.themeLight") : t("topbar.themeDark")}
          aria-label={t("topbar.themeToggle")}
        >
          {isDark ? (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="4"/>
              <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/>
            </svg>
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z"/>
            </svg>
          )}
        </button>

        {isAuthenticated ? (
          <button style={s.logoutBtn} onClick={onLogout}>{t("topbar.logout")}</button>
        ) : (
          <button style={s.loginBtn} onClick={onLogin}>{t("topbar.login")}</button>
        )}
      </div>
    </div>
  );
}

Topbar.propTypes = {
  isAuthenticated: PropTypes.bool,
  onLogin: PropTypes.func,
  onLogout: PropTypes.func,
  theme: PropTypes.string,
  onThemeToggle: PropTypes.func,
  onAlertsClick: PropTypes.func,
  showAlerts: PropTypes.bool,
  keycloak: PropTypes.object,
};

const s = {
  row: {
    display: "flex",
    alignItems: "center",
    gap: 16,
    flexWrap: "wrap",
  },
  brand: {
    display: "inline-flex",
    alignItems: "center",
    gap: 10,
    textDecoration: "none",
    color: "inherit",
    flexShrink: 0,
  },
  brandLogo: {
    width: 36,
    height: 36,
    borderRadius: 9,
    background: "linear-gradient(135deg, var(--accent-strong, #15803d), var(--accent-solid, #22c55e))",
    display: "grid",
    placeItems: "center",
    boxShadow: "0 4px 12px rgba(34, 197, 94, 0.25)",
  },
  brandText: {
    display: "flex",
    flexDirection: "column",
    lineHeight: 1.1,
  },
  brandName: {
    fontSize: 16,
    fontWeight: 800,
    color: "var(--text-primary)",
    letterSpacing: "-0.3px",
  },
  brandTag: {
    fontSize: 9.5,
    fontWeight: 700,
    color: "var(--text-muted)",
    letterSpacing: "0.12em",
    textTransform: "uppercase",
    marginTop: 1,
  },
  nav: {
    display: "flex",
    alignItems: "center",
    gap: 2,
    flex: 1,
    justifyContent: "center",
    flexWrap: "wrap",
    minWidth: 0,
  },
  navLink: {
    padding: "8px 11px",
    borderRadius: 8,
    fontSize: 13.5,
    fontWeight: 600,
    color: "var(--text-muted)",
    textDecoration: "none",
    transition: "background-color 0.15s, color 0.15s",
    whiteSpace: "nowrap",
  },
  navLinkActive: {
    color: "var(--accent-solid)",
    background: "var(--accent-hover-bg)",
  },
  // Admin items are visually distinct so a privileged user can spot the
  // privileged-only pages at a glance and won't confuse them with
  // ordinary market links.
  navLinkAdmin: {
    color: "var(--accent-amber, #f59e0b)",
    border: "1px dashed var(--accent-amber, #f59e0b)",
    padding: "7px 10px",
  },
  navLinkAdminActive: {
    background: "rgba(245, 158, 11, 0.12)",
    color: "var(--accent-amber, #f59e0b)",
    borderStyle: "solid",
  },
  right: {
    display: "flex",
    alignItems: "center",
    gap: 8,
    flexShrink: 0,
  },
  iconBtn: {
    width: 38, height: 38,
    borderRadius: 10,
    border: "1px solid var(--border-card)",
    background: "var(--input-bg)",
    color: "var(--text-secondary)",
    cursor: "pointer",
    display: "grid",
    placeItems: "center",
  },
  logoutBtn: {
    padding: "8px 18px",
    borderRadius: 10,
    border: "1px solid var(--danger-border)",
    background: "var(--danger-bg)",
    color: "var(--danger-text)",
    cursor: "pointer",
    fontSize: 14,
    fontWeight: 600,
  },
  loginBtn: {
    padding: "9px 20px",
    borderRadius: 10,
    border: "none",
    background: "linear-gradient(135deg, var(--accent-strong), var(--accent-solid))",
    color: "#fff",
    cursor: "pointer",
    fontSize: 14,
    fontWeight: 700,
    boxShadow: "0 4px 14px rgba(34, 197, 94, 0.30)",
  },

  // ── Mobile drawer (visible only via media query in index.css) ──
  drawerHeader: {
    padding: "20px 20px 16px",
    borderBottom: "1px solid var(--border-card)",
  },
  drawerNav: {
    display: "flex",
    flexDirection: "column",
    padding: 12,
    gap: 2,
    overflowY: "auto",
  },
  drawerLink: {
    padding: "12px 14px",
    borderRadius: 10,
    fontSize: 15,
    fontWeight: 600,
    color: "var(--text-primary)",
    textDecoration: "none",
  },
  drawerLinkActive: {
    background: "var(--accent-hover-bg)",
    color: "var(--accent-solid)",
  },
  drawerLinkAdmin: {
    color: "var(--accent-amber, #f59e0b)",
    border: "1px dashed var(--accent-amber, #f59e0b)",
  },
};

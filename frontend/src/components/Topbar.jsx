import { useEffect, useRef, useState } from "react";
import PropTypes from "prop-types";
import { Link, useLocation } from "react-router-dom";
import { createPortal } from "react-dom";
import NotificationBell from "./NotificationBell";
import { useCurrencyDisplay } from "../contexts/CurrencyDisplayContext";
import { useI18n } from "../contexts/I18nContext";
import { isAdmin } from "../utils/roleUtils";
import BrandLogo from "./common/BrandLogo";

/**
 * Inline navigation items shown in the top bar.
 *
 * Tiers, all rendered as flat chips in the nav strip:
 *   PUBLIC_NAV  — always visible, top-level. Home + content.
 *   MARKET_NAV  — always visible, but grouped under one "Piyasalar" dropdown
 *                 instead of flat chips — 8 market-data pages used to sit
 *                 inline and pushed the strip past the available width,
 *                 forcing a horizontal scrollbar at ordinary desktop widths.
 *   PRIVATE_NAV — shown only to authenticated users (per-user dashboards).
 *   ADMIN_NAV   — shown only to users with the ADMIN realm role.
 *
 * Labels are i18n keys; `useI18n().t()` resolves them per the active language.
 */
const PUBLIC_NAV = [
  { to: "/",     key: "nav.home" },
];

const CONTENT_NAV = [
  { to: "/news", key: "nav.news" },
];

const MARKET_NAV = [
  { to: "/stocks",      key: "nav.stocks" },
  { to: "/crypto",      key: "nav.crypto" },
  { to: "/funds",       key: "nav.funds" },
  { to: "/bonds",       key: "nav.bonds" },
  { to: "/market-data", key: "nav.fx" },
  { to: "/commodities", key: "nav.commodities" },
  { to: "/viop",        key: "nav.viop" },
  { to: "/inflation",   key: "nav.inflation" },
];

const PRIVATE_NAV = [
  { to: "/analysis",   key: "nav.analysis" },
  { to: "/portfolio",  key: "nav.portfolio" },
  { to: "/historical", key: "nav.historical" },
  { to: "/lists",      key: "nav.lists" },
  { to: "/settings",   key: "nav.settings" }, // masaüstü şeritte gizli; sağ araç kümesinde ⚙️ olarak
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

/**
 * "Piyasalar" nav dropdown — groups the 8 market-data pages (Hisseler,
 * Kripto, Fonlar, Tahvil, Döviz, Emtia, VIOP, Enflasyon) behind one chip
 * instead of 8 flat links, so the inline nav strip fits without scrolling.
 * Highlights as active when the current route matches any grouped page.
 */
function MarketsDropdown({ isActive, t }) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);
  const location = useLocation();

  useEffect(() => { setOpen(false); }, [location.pathname]);

  useEffect(() => {
    if (!open) return undefined;
    const onDocDown = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
    };
    const onKey = (e) => { if (e.key === "Escape") setOpen(false); };
    document.addEventListener("mousedown", onDocDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDocDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const groupActive = MARKET_NAV.some((item) => isActive(item.to));

  return (
    <div ref={wrapRef} style={s.marketsWrap}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        style={{ ...s.navLink, ...s.marketsBtn, ...(groupActive ? s.navLinkActive : {}) }}
        aria-haspopup="menu"
        aria-expanded={open}
      >
        {t("nav.markets")}
        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"
             style={{ transform: open ? "rotate(180deg)" : "none", transition: "transform 0.15s" }}>
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </button>
      {open && (
        <div style={s.marketsPanel} role="menu">
          {MARKET_NAV.map((item) => {
            const active = isActive(item.to);
            return (
              <Link
                key={item.to}
                to={item.to}
                role="menuitem"
                style={{ ...s.marketsItem, ...(active ? s.marketsItemActive : {}) }}
                onClick={() => setOpen(false)}
              >
                {t(item.key)}
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}

MarketsDropdown.propTypes = {
  isActive: PropTypes.func.isRequired,
  t: PropTypes.func.isRequired,
};

const ctgl = {
  wrap: {
    display: "inline-flex",
    background: "var(--input-bg)",
    border: "1px solid var(--border-card)",
    borderRadius: 8,
    padding: 2,
    flexShrink: 0,
  },
  btn: {
    border: "none",
    background: "transparent",
    color: "var(--text-muted)",
    padding: "4px 7px",
    borderRadius: 6,
    fontSize: 11,
    fontWeight: 600,
    cursor: "pointer",
    minWidth: 26,
    lineHeight: 1.4,
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

  // Mobile drawer lists everything flat (it's a vertical scroll, not a
  // horizontal strip, so the "Piyasalar" grouping that the desktop nav
  // needs to avoid overflow scroll isn't necessary here).
  const allNavItems = [
    ...PUBLIC_NAV,
    ...MARKET_NAV,
    ...CONTENT_NAV,
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
          <Link to="/" style={s.brand} onClick={() => setMobileOpen(false)} aria-label="Finans Portalı">
            <BrandLogo size={44} />
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

      {/* Brand — inline-SVG mark + dual-colour "FİNANS PORTALI" wordmark.
          Theme-adaptive (the PNG version turned into a black box on the
          light theme — colours now read off --text-primary + --accent-solid). */}
      <Link to="/" style={s.brand} className="fp-topbar-brand" aria-label="Finans Portalı">
        <BrandLogo size={30} />
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
        <MarketsDropdown isActive={isActive} t={t} />
        {CONTENT_NAV.map((item) => {
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
        {isAuthenticated && PRIVATE_NAV.filter((item) => item.to !== "/settings").map((item) => {
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
          // Labelled (icon + "Alarm" text) so it can't be mistaken for the
          // bell-shaped notification button rendered immediately above —
          // both are bells, but only this one creates a price alert.
          <button style={s.alarmBtn} onClick={onAlertsClick} title={t("topbar.priceAlertCreate")} aria-label={t("topbar.priceAlert")}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 5V3M12 5C8.69 5 6 7.69 6 11V16L4 18V19H20V18L18 16V11C18 7.69 15.31 5 12 5Z"
                    stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
              <path d="M9 21H15" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
              <circle cx="18" cy="6" r="3" fill="var(--accent-solid)"/>
            </svg>
            <span style={s.alarmBtnLabel}>{t("topbar.priceAlert") || "Alarm"}</span>
          </button>
        )}

        {/* Ayarlar — nav şeridinden buraya, ikon araç kümesine taşındı (yer açmak için) */}
        {isAuthenticated && (
          <Link
            to="/settings"
            style={{ ...s.iconBtn, ...(isActive("/settings") ? { color: "var(--accent-solid)" } : {}) }}
            title={t("nav.settings")}
            aria-label={t("nav.settings")}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="3"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
          </Link>
        )}

        <button
          style={s.iconBtn}
          onClick={onThemeToggle}
          title={isDark ? t("topbar.themeLight") : t("topbar.themeDark")}
          aria-label={t("topbar.themeToggle")}
        >
          {isDark ? (
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="4"/>
              <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/>
            </svg>
          ) : (
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
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
    gap: 10,
    flexWrap: "wrap",
  },
  brand: {
    display: "inline-flex",
    alignItems: "center",
    textDecoration: "none",
    color: "inherit",
    flexShrink: 0,
  },
  nav: {
    display: "flex",
    alignItems: "center",
    gap: 1,
    flex: 1,
    justifyContent: "center",
    // The "Piyasalar" dropdown keeps the inline strip down to ~6 chips, so
    // this no longer needs to scroll at ordinary desktop widths — the
    // overflow-x stays only as a safety net for very narrow/zoomed windows.
    // Below 1100px the hamburger replaces this nav entirely (see index.css).
    flexWrap: "nowrap",
    overflowX: "auto",
    minWidth: 0,
  },
  navLink: {
    padding: "8px 8px",
    borderRadius: 8,
    fontSize: 13,
    fontWeight: 600,
    whiteSpace: "nowrap",
    color: "var(--text-muted)",
    textDecoration: "none",
    transition: "background-color 0.15s, color 0.15s",
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
  marketsWrap: {
    position: "relative",
    flexShrink: 0,
  },
  marketsBtn: {
    display: "inline-flex",
    alignItems: "center",
    gap: 4,
    border: "none",
    background: "transparent",
    cursor: "pointer",
    fontFamily: "inherit",
  },
  marketsPanel: {
    position: "absolute",
    top: "calc(100% + 6px)",
    left: 0,
    minWidth: 170,
    padding: 6,
    borderRadius: 10,
    border: "1px solid var(--border-card)",
    background: "var(--bg-card)",
    boxShadow: "0 12px 28px rgba(0,0,0,0.25)",
    display: "flex",
    flexDirection: "column",
    gap: 2,
    zIndex: 60,
  },
  marketsItem: {
    padding: "8px 10px",
    borderRadius: 8,
    fontSize: 13,
    fontWeight: 600,
    color: "var(--text-muted)",
    textDecoration: "none",
    whiteSpace: "nowrap",
  },
  marketsItemActive: {
    color: "var(--accent-solid)",
    background: "var(--accent-hover-bg)",
  },
  right: {
    display: "flex",
    alignItems: "center",
    gap: 6,
    flexShrink: 0,
  },
  iconBtn: {
    width: 32, height: 32,
    borderRadius: 8,
    border: "1px solid var(--border-card)",
    background: "var(--input-bg)",
    color: "var(--text-secondary)",
    cursor: "pointer",
    display: "grid",
    placeItems: "center",
    flexShrink: 0,
  },
  alarmBtn: {
    height: 32,
    padding: "0 9px",
    borderRadius: 8,
    border: "1px solid var(--border-card)",
    background: "var(--input-bg)",
    color: "var(--text-secondary)",
    cursor: "pointer",
    display: "inline-flex",
    alignItems: "center",
    gap: 5,
    flexShrink: 0,
  },
  alarmBtnLabel: {
    fontSize: 11,
    fontWeight: 600,
    color: "var(--text-primary)",
    letterSpacing: 0.2,
    whiteSpace: "nowrap",
  },
  // Explicit height — matches the action cluster's other buttons (iconBtn /
  // alarmBtn are both height:32). Without it, this button's height came
  // purely from padding + line-height (~27px), which sat visibly lower
  // than its 32px-tall siblings in the same flex row.
  logoutBtn: {
    height: 32,
    padding: "0 12px",
    borderRadius: 8,
    border: "1px solid var(--danger-border)",
    background: "var(--danger-bg)",
    color: "var(--danger-text)",
    cursor: "pointer",
    fontSize: 12.5,
    fontWeight: 600,
    flexShrink: 0,
  },
  loginBtn: {
    height: 32,
    padding: "0 14px",
    borderRadius: 8,
    border: "none",
    background: "linear-gradient(135deg, var(--accent-strong), var(--accent-solid))",
    color: "#fff",
    cursor: "pointer",
    fontSize: 12.5,
    fontWeight: 700,
    boxShadow: "0 4px 14px rgba(34, 197, 94, 0.30)",
    flexShrink: 0,
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

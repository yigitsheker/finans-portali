import { Link, useLocation } from "react-router-dom";
import NotificationBell from "./NotificationBell";
import { useCurrencyDisplay } from "../contexts/CurrencyDisplayContext";
import { isAdmin } from "../utils/roleUtils";

/**
 * Public navigation items shown inline in the top bar. These replace the
 * old left-rail sidebar — fewer clicks for the common pages, and a single
 * sticky header keeps the most-used controls visible while scrolling.
 *
 * Admin / private routes (Yatırımlarım, Yönetim) intentionally stay off
 * the public nav — they're surfaced from the user menu when authenticated.
 */
const PUBLIC_NAV = [
  { to: "/",        label: "Anasayfa" },
  { to: "/stocks",  label: "Hisseler" },
  { to: "/crypto",  label: "Kripto" },
  { to: "/funds",   label: "Fonlar" },
  { to: "/market-data", label: "Döviz" },
  { to: "/news",    label: "Haberler" },
];

function CurrencyToggle() {
  const { mode, setMode } = useCurrencyDisplay();
  const opts = [
    { v: "original", label: "Orijinal", title: "Her enstrümanı kendi para biriminde göster" },
    { v: "TRY",      label: "₺",        title: "Tüm fiyatları Türk Lirası'na çevir" },
    { v: "USD",      label: "$",        title: "Tüm fiyatları ABD Doları'na çevir" },
  ];
  return (
    <div style={ctgl.wrap} role="group" aria-label="Para birimi gösterim modu">
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
  const isDark = theme === "dark" ||
    (theme === "system" && typeof window !== "undefined" && window.matchMedia
      && window.matchMedia("(prefers-color-scheme: dark)").matches);

  // Active link matcher — "/" matches only the exact home route to avoid
  // every page lighting up "Anasayfa". Everything else uses prefix match
  // so /stocks/AAPL still highlights "Hisseler".
  const isActive = (to) => to === "/"
    ? location.pathname === "/"
    : location.pathname.startsWith(to);

  return (
    <div style={s.row} className="fp-topbar">
      {/* Brand */}
      <Link to="/" style={s.brand}>
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

      {/* Inline nav links */}
      <nav style={s.nav} aria-label="Ana menü">
        {PUBLIC_NAV.map((item) => {
          const active = isActive(item.to);
          return (
            <Link
              key={item.to}
              to={item.to}
              style={{ ...s.navLink, ...(active ? s.navLinkActive : {}) }}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>

      {/* Action cluster */}
      <div style={s.right}>
        <CurrencyToggle />

        {/* Notification bell — hidden for admin (their own test pings) */}
        {isAuthenticated && !isAdmin(keycloak) && <NotificationBell keycloak={keycloak} />}

        {showAlerts && onAlertsClick && (
          <button style={s.iconBtn} onClick={onAlertsClick} title="Fiyat Alarmı Oluştur" aria-label="Fiyat Alarmı">
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
          title={isDark ? "Açık tema" : "Koyu tema"}
          aria-label="Tema değiştir"
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
          <button style={s.logoutBtn} onClick={onLogout}>Çıkış</button>
        ) : (
          <button style={s.loginBtn} onClick={onLogin}>Giriş Yap</button>
        )}
      </div>
    </div>
  );
}

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
    gap: 4,
    flex: 1,
    justifyContent: "center",
    flexWrap: "wrap",
  },
  navLink: {
    padding: "8px 14px",
    borderRadius: 8,
    fontSize: 14,
    fontWeight: 600,
    color: "var(--text-muted)",
    textDecoration: "none",
    transition: "background-color 0.15s, color 0.15s",
  },
  navLinkActive: {
    color: "var(--accent-solid)",
    background: "var(--accent-hover-bg)",
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
};

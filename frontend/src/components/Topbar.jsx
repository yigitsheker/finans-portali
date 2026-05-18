import NotificationBell from "./NotificationBell";
import { useCurrencyDisplay } from "../contexts/CurrencyDisplayContext";
import { isAdmin } from "../utils/roleUtils";

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
    gap: 0,
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

/**
 * Topbar — page title on the left, action cluster on the right. The hamburger
 * button only appears on viewports ≤1024px (CSS-controlled) and toggles the
 * sidebar drawer via the `onMenuClick` prop injected by Layout.
 */
export default function Topbar({
  isAuthenticated,
  onLogin,
  onLogout,
  title,
  subtitle,
  right,
  theme,
  onThemeToggle,
  onAlertsClick,
  showAlerts,
  keycloak,
}) {
  const isDark = theme === "dark" ||
    (theme === "system" && typeof window !== "undefined" && window.matchMedia
      && window.matchMedia("(prefers-color-scheme: dark)").matches);

  return (
    <div style={s.row} className="topbar">
      <div style={s.left}>
        <div style={s.titleWrap}>
          <div style={s.title}>{title}</div>
          {subtitle && <div style={s.sub}>{subtitle}</div>}
        </div>
      </div>

      <div style={s.right}>
        {right}

        <CurrencyToggle />

        {/* The notification bell is a user feature (price-alert pings).
            Admins manage the system, not their own positions, so hide it
            for admin role to avoid confusion when the bell shows the
            admin's own old test notifications. */}
        {isAuthenticated && !isAdmin(keycloak) && <NotificationBell keycloak={keycloak} />}

        {showAlerts && onAlertsClick && (
          <button style={s.iconBtn} onClick={onAlertsClick} title="Fiyat Alarmı Oluştur">
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
              <circle cx="12" cy="12" r="4" />
              <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
            </svg>
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z" />
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
    justifyContent: "space-between",
    gap: 16,
    flexWrap: "wrap",
  },
  left: {
    minWidth: 0,
    flex: "1 1 auto",
    display: "flex",
    alignItems: "center",
    gap: 12,
  },
  hamburger: {
    display: "none",  // CSS override for ≤1024px
    width: 38, height: 38,
    borderRadius: 10,
    border: "1px solid var(--border-card)",
    background: "var(--input-bg)",
    color: "var(--text-primary)",
    cursor: "pointer",
    placeItems: "center",
    flexShrink: 0,
  },
  titleWrap: {
    minWidth: 0,
    flex: "1 1 auto",
  },
  title: {
    fontSize: "var(--font-2xl)",
    fontWeight: 700,
    color: "var(--text-primary)",
    letterSpacing: "-0.02em",
    lineHeight: 1.2,
  },
  sub: {
    color: "var(--text-muted)",
    fontSize: "var(--font-md)",
    marginTop: 2,
  },
  right: {
    display: "flex",
    alignItems: "center",
    gap: 8,
    flexWrap: "wrap",
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
    fontSize: "var(--font-md)",
    fontWeight: 600,
  },
  loginBtn: {
    padding: "9px 20px",
    borderRadius: 10,
    border: "none",
    background: "linear-gradient(135deg, var(--accent-strong), var(--accent-solid))",
    color: "#fff",
    cursor: "pointer",
    fontSize: "var(--font-md)",
    fontWeight: 700,
    boxShadow: "0 4px 14px rgba(34, 197, 94, 0.30)",
  },
};

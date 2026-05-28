import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate, useLocation } from "react-router-dom";
import { isAdmin } from "../utils/roleUtils";
import { useI18n } from "../contexts/I18nContext";

// SVG Icon Components
const NewsIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="2"/>
        <line x1="7" y1="8" x2="17" y2="8" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        <line x1="7" y1="12" x2="17" y2="12" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        <line x1="7" y1="16" x2="13" y2="16" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
);

const StocksIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M3 17L9 11L13 15L21 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M16 7H21V12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
);

const CryptoIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2"/>
        <path d="M9 8H13C14.1 8 15 8.9 15 10C15 11.1 14.1 12 13 12H9V8Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M9 12H13.5C14.6 12 15.5 12.9 15.5 14C15.5 15.1 14.6 16 13.5 16H9V12Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <line x1="12" y1="6" x2="12" y2="8" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        <line x1="12" y1="16" x2="12" y2="18" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
);

const FundsIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M12 2L2 7V17L12 22L22 17V7L12 2Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
        <path d="M12 8V16M8 12H16" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
);

const BondsIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="3" y="5" width="18" height="14" rx="2" stroke="currentColor" strokeWidth="2"/>
        <line x1="3" y1="10" x2="21" y2="10" stroke="currentColor" strokeWidth="2"/>
        <line x1="3" y1="14" x2="21" y2="14" stroke="currentColor" strokeWidth="2"/>
        <circle cx="7" cy="7.5" r="1" fill="currentColor"/>
        <circle cx="7" cy="12" r="1" fill="currentColor"/>
        <circle cx="7" cy="16.5" r="1" fill="currentColor"/>
    </svg>
);

const ExchangeIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M3 8H21M21 8L17 4M21 8L17 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M21 16H3M3 16L7 12M3 16L7 20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
);

const PortfolioIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="3" y="7" width="18" height="13" rx="2" stroke="currentColor" strokeWidth="2"/>
        <path d="M8 7V5C8 3.9 8.9 3 10 3H14C15.1 3 16 3.9 16 5V7" stroke="currentColor" strokeWidth="2"/>
        <line x1="3" y1="12" x2="21" y2="12" stroke="currentColor" strokeWidth="2"/>
    </svg>
);

const HistoricalIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2"/>
        <path d="M12 6V12L16 14" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        <path d="M3 12H1M23 12H21M12 3V1M12 23V21" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
);

const AdminIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="2"/>
        <path d="M12 1V3M12 21V23M23 12H21M3 12H1M20.49 3.51L19.07 4.93M4.93 19.07L3.51 20.49M20.49 20.49L19.07 19.07M4.93 4.93L3.51 3.51" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
);

const CommodityIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="12" cy="12" r="8" stroke="currentColor" strokeWidth="2"/>
        <path d="M12 6V12L16 14" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        <text x="12" y="20" fontSize="6" fontWeight="700" fill="currentColor" textAnchor="middle">Au</text>
    </svg>
);

const ViopIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M4 19L9 14L13 18L20 11" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M15 11H20V16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <rect x="3" y="3" width="18" height="3" rx="1" stroke="currentColor" strokeWidth="2"/>
    </svg>
);

const InflationIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M3 17L9 11L13 15L21 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M3 21H21" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        <text x="12" y="11" fontSize="7" fontWeight="700" fill="currentColor" textAnchor="middle">%</text>
    </svg>
);

const AnalysisIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M4 4V20H20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M7 14L11 10L14 13L20 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <circle cx="20" cy="7" r="2" fill="currentColor"/>
    </svg>
);

const SettingsIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="2"/>
        <path d="M19.4 15C19.5 14.7 19.5 14.3 19.4 14L21.5 12.3C21.7 12.1 21.7 11.8 21.6 11.6L19.6 8.4C19.5 8.2 19.2 8.1 19 8.2L16.6 9.2C16.1 8.8 15.6 8.5 15 8.3L14.6 5.8C14.6 5.6 14.4 5.4 14.2 5.4H10.2C10 5.4 9.8 5.6 9.8 5.8L9.4 8.3C8.8 8.5 8.3 8.8 7.8 9.2L5.4 8.2C5.2 8.1 4.9 8.2 4.8 8.4L2.8 11.6C2.7 11.8 2.7 12.1 2.9 12.3L5 14C4.9 14.3 4.9 14.7 5 15L2.9 16.7C2.7 16.9 2.7 17.2 2.8 17.4L4.8 20.6C4.9 20.8 5.2 20.9 5.4 20.8L7.8 19.8C8.3 20.2 8.8 20.5 9.4 20.7L9.8 23.2C9.8 23.4 10 23.6 10.2 23.6H14.2C14.4 23.6 14.6 23.4 14.6 23.2L15 20.7C15.6 20.5 16.1 20.2 16.6 19.8L19 20.8C19.2 20.9 19.5 20.8 19.6 20.6L21.6 17.4C21.7 17.2 21.7 16.9 21.5 16.7L19.4 15Z" stroke="currentColor" strokeWidth="2"/>
    </svg>
);

const PUBLIC_NAV_ITEMS = [
    { id: "news", i18nKey: "nav.home", icon: <NewsIcon />, path: "/news" },
    { id: "stocks", i18nKey: "nav.stocksFull", icon: <StocksIcon />, path: "/stocks" },
    { id: "crypto", i18nKey: "nav.cryptoFull", icon: <CryptoIcon />, path: "/crypto" },
    { id: "funds", i18nKey: "nav.fundsFull", icon: <FundsIcon />, path: "/funds" },
    { id: "bonds", i18nKey: "nav.bondsFull", icon: <BondsIcon />, path: "/bonds" },
    { id: "market-data", i18nKey: "nav.fxFull", icon: <ExchangeIcon />, path: "/market-data" },
    { id: "commodities", i18nKey: "nav.commodities", icon: <CommodityIcon />, path: "/commodities" },
    { id: "viop", i18nKey: "nav.viop", icon: <ViopIcon />, path: "/viop" },
    { id: "inflation", i18nKey: "nav.inflation", icon: <InflationIcon />, path: "/inflation" },
];

const PRIVATE_NAV_ITEMS = [
    { id: "analysis", i18nKey: "nav.analysis", icon: <AnalysisIcon />, path: "/analysis", requiresAuth: true },
    { id: "portfolio", i18nKey: "nav.portfolioFull", icon: <PortfolioIcon />, path: "/portfolio", requiresAuth: true },
    { id: "historical", i18nKey: "nav.historicalFull", icon: <HistoricalIcon />, path: "/historical", requiresAuth: true },
];

const ADMIN_ITEMS = [
    { id: "admin", i18nKey: "nav.admin", icon: <AdminIcon />, path: "/admin" },
];

const PREF_ITEMS = [
    { id: "settings", i18nKey: "nav.settings", icon: <SettingsIcon />, path: "/settings" },
];

export default function Sidebar({ keycloak }) {
    const navigate = useNavigate();
    const location = useLocation();
    const { t } = useI18n();

    // Mobile drawer state. Desktop hover-expand is pure CSS (see index.css).
    const [mobileOpen, setMobileOpen] = useState(false);
    const closeMobile = () => setMobileOpen(false);

    // Close the drawer whenever the route changes (clicking a nav item
    // shouldn't leave the drawer covering the page).
    useEffect(() => { setMobileOpen(false); }, [location.pathname]);

    // Lock body scroll while the mobile drawer is open so the page behind
    // the backdrop doesn't scroll on touch.
    useEffect(() => {
        if (!mobileOpen) return;
        const prev = document.body.style.overflow;
        document.body.style.overflow = "hidden";
        return () => { document.body.style.overflow = prev; };
    }, [mobileOpen]);

    const username = useMemo(() => {
        const p = keycloak.tokenParsed;
        return p?.preferred_username ?? p?.name ?? p?.email ?? "Kullanıcı";
    }, [keycloak.tokenParsed]);

    const email = useMemo(() => {
        const p = keycloak.tokenParsed;
        return p?.email ?? "";
    }, [keycloak.tokenParsed]);

    const userIsAdmin = useMemo(() => isAdmin(keycloak), [keycloak.tokenParsed]);

    const initials = username.slice(0, 2).toUpperCase();

    const isActive = (path) => location.pathname === path;

    // Portal mobile-overlay pieces (hamburger button + backdrop) directly into
    // <body>. Otherwise they live inside Layout's <aside class="fp-sidebar"> whose
    // width:0/height:0 in mobile mode causes browsers to skip layouting fixed
    // children — and the hamburger disappears entirely on small screens.
    const mobileOverlay = createPortal(
        <>
            <button
                type="button"
                className="fp-mobile-hamburger"
                onClick={() => setMobileOpen((v) => !v)}
                aria-label={mobileOpen ? t("topbar.menuClose") : t("topbar.menuOpen")}
                title={t("topbar.menu")}
            >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    {mobileOpen ? (
                        <>
                            <line x1="6" y1="6"  x2="18" y2="18" />
                            <line x1="6" y1="18" x2="18" y2="6" />
                        </>
                    ) : (
                        <>
                            <line x1="3" y1="6"  x2="21" y2="6" />
                            <line x1="3" y1="12" x2="21" y2="12" />
                            <line x1="3" y1="18" x2="21" y2="18" />
                        </>
                    )}
                </svg>
            </button>

            <div
                className={`fp-mobile-backdrop ${mobileOpen ? "is-visible" : ""}`}
                onClick={closeMobile}
                aria-hidden="true"
            />
        </>,
        document.body
    );

    return (
        <>
            {mobileOverlay}

            {/* Sidebar shell. Width is controlled by CSS:
                 - desktop default: rail (icon-only, ~64px)
                 - desktop hover: expanded (~232px)
                 - mobile: off-canvas, slides in when `is-mobile-open`         */}
            <div
                className={`fp-sidebar-shell ${mobileOpen ? "is-mobile-open" : ""}`}
                style={s.wrap}
            >
            {/* Brand */}
            <div className="fp-sidebar-brand" style={s.brand}>
                <div style={s.logo}>
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <polyline points="3 17 9 11 13 15 21 7" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"/>
                        <polyline points="14 7 21 7 21 14" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                </div>
                <div className="fp-sidebar-text">
                    <div style={s.brandName}>Finans Portal</div>
                    <div style={s.brandSub}>{t("sidebar.brandSub")}</div>
                </div>
            </div>

            {/* Navigation - public */}
            <div className="fp-sidebar-text" style={s.sectionLabel}>{t("sidebar.markets")}</div>
            {PUBLIC_NAV_ITEMS.map((item) => {
                const label = t(item.i18nKey);
                return (
                <button
                    key={item.id}
                    className="fp-nav-item"
                    style={isActive(item.path) ? s.itemActive : s.item}
                    onClick={() => navigate(item.path)}
                    title={label}
                >
                    <span style={s.icon}>{item.icon}</span>
                    <span className="fp-sidebar-text">{label}</span>
                </button>
                );
            })}

            {/* Navigation - private (only when logged in) */}
            {keycloak.authenticated && (
                <>
                    <div className="fp-sidebar-text" style={s.sectionLabel}>{t("sidebar.account")}</div>
                    {PRIVATE_NAV_ITEMS.map((item) => {
                        const label = t(item.i18nKey);
                        return (
                        <button
                            key={item.id}
                            className="fp-nav-item"
                            style={isActive(item.path) ? s.itemActive : s.item}
                            onClick={() => navigate(item.path)}
                            title={label}
                        >
                            <span style={s.icon}>{item.icon}</span>
                            <span className="fp-sidebar-text">{label}</span>
                        </button>
                        );
                    })}
                </>
            )}

            {/* Admin Section - Only visible for admins */}
            {userIsAdmin && (
                <>
                    <div className="fp-sidebar-text" style={s.sectionLabel}>{t("sidebar.admin")}</div>
                    {ADMIN_ITEMS.map((item) => {
                        const label = t(item.i18nKey);
                        return (
                        <button
                            key={item.id}
                            className="fp-nav-item"
                            style={isActive(item.path) ? s.itemActive : s.item}
                            onClick={() => navigate(item.path)}
                            title={label}
                        >
                            <span style={s.icon}>{item.icon}</span>
                            <span className="fp-sidebar-text">{label}</span>
                        </button>
                        );
                    })}
                </>
            )}

            <div style={{ flex: 1 }} />

            {/* Preferences */}
            <div className="fp-sidebar-text" style={s.sectionLabel}>{t("sidebar.preferences")}</div>
            {PREF_ITEMS.map((item) => {
                const label = t(item.i18nKey);
                return (
                <button
                    key={item.id}
                    className="fp-nav-item"
                    style={isActive(item.path) ? s.itemActive : s.item}
                    onClick={() => navigate(item.path)}
                    title={label}
                >
                    <span style={s.icon}>{item.icon}</span>
                    <span className="fp-sidebar-text">{label}</span>
                </button>
                );
            })}

            {/* User footer */}
            {keycloak.authenticated ? (
                <div style={s.userRow}>
                    <div style={s.avatar}>{initials}</div>
                    <div className="fp-sidebar-text" style={s.userInfo}>
                        <div style={s.userName}>{username}</div>
                        {email && <div style={s.userEmail}>{email}</div>}
                        {userIsAdmin && <div style={s.userBadge}>👑 Admin</div>}
                    </div>
                </div>
            ) : (
                <div className="fp-sidebar-text" style={s.guestRow}>
                    <button
                        style={s.guestLoginBtn}
                        onClick={() =>
                            keycloak.login({ redirectUri: window.location.href })
                        }
                    >
                        Giriş Yap
                    </button>
                </div>
            )}
            </div>
        </>
    );
}

const baseItem = {
    width: "100%",
    textAlign: "left",
    display: "flex",
    alignItems: "center",
    gap: 10,
    // 3px left border on every item — transparent when inactive — keeps text/icon
    // position byte-identical across active/inactive states (otherwise the active
    // border-left shifts the layout 1-3px depending on subpixel rendering).
    padding: "8px 12px 8px 9px",
    borderRadius: 8,
    border: "none",
    borderLeft: "3px solid transparent",
    cursor: "pointer",
    fontSize: 13,
    fontWeight: 500,
    marginBottom: 2,
    transition: "background 0.12s ease, color 0.12s ease, border-color 0.12s ease",
};

const s = {
    wrap: {
        display: "flex",
        flexDirection: "column",
        height: "100%",
        padding: "16px 12px",
    },
    brand: {
        display: "flex",
        alignItems: "center",
        gap: 10,
        marginBottom: 24,
        padding: "0 4px",
    },
    logo: {
        width: 38,
        height: 38,
        borderRadius: 10,
        background: "linear-gradient(135deg, var(--accent-strong), var(--accent-solid))",
        boxShadow: "0 4px 14px rgba(34, 197, 94, 0.30)",
        display: "grid",
        placeItems: "center",
        flexShrink: 0,
    },
    brandName: {
        fontWeight: 800,
        fontSize: 15,
        color: "var(--text-primary)",
        letterSpacing: "-0.01em",
    },
    brandSub: {
        fontSize: 10.5,
        color: "var(--text-muted)",
        marginTop: 2,
        letterSpacing: "0.04em",
        textTransform: "uppercase",
    },
    sectionLabel: {
        fontSize: 10,
        fontWeight: 700,
        letterSpacing: "0.10em",
        color: "var(--text-faint, var(--text-muted))",
        padding: "0 12px",
        marginBottom: 6,
        marginTop: 14,
        textTransform: "uppercase",
    },
    item: {
        ...baseItem,
        background: "transparent",
        color: "var(--text-muted)",
    },
    itemActive: {
        ...baseItem,
        background: "var(--accent-hover-bg)",
        color: "var(--text-primary)",
        borderLeft: "3px solid var(--accent-solid)",
    },
    icon: {
        fontSize: 14,
        width: 18,
        height: 18,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        flexShrink: 0,
    },
    userRow: {
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "12px 4px 4px",
        borderTop: "1px solid var(--border)",
        marginTop: 8,
    },
    avatar: {
        width: 32,
        height: 32,
        borderRadius: 8,
        background: "linear-gradient(135deg, var(--accent-strong), var(--accent-solid))",
        boxShadow: "0 2px 8px rgba(34, 197, 94, 0.25)",
        display: "grid",
        placeItems: "center",
        fontSize: 11,
        fontWeight: 700,
        color: "#fff",
        flexShrink: 0,
    },
    userInfo: { overflow: "hidden", flex: 1 },
    userName: {
        fontSize: 12,
        fontWeight: 600,
        color: "var(--text-primary)",
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },
    userEmail: {
        fontSize: 10,
        color: "var(--text-muted)",
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },
    userBadge: {
        fontSize: 9,
        fontWeight: 700,
        color: "var(--accent-solid)",
        marginTop: 2,
        letterSpacing: "0.04em",
    },
    guestRow: {
        display: "flex",
        flexDirection: "column",
        gap: 6,
        padding: "12px 4px 4px",
        borderTop: "1px solid var(--border)",
        marginTop: 8,
    },
    guestLoginBtn: {
        padding: "8px 12px",
        borderRadius: 8,
        border: "1px solid var(--accent-solid)",
        background: "var(--accent-solid)",
        color: "#000",
        fontSize: 12,
        fontWeight: 700,
        cursor: "pointer",
    },
    guestRegisterBtn: {
        padding: "8px 12px",
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "transparent",
        color: "var(--text-primary)",
        fontSize: 12,
        fontWeight: 600,
        cursor: "pointer",
    },
};

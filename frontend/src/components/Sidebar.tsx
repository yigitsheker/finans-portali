import type Keycloak from "keycloak-js";
import { useMemo } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { isAdmin } from "../utils/roleUtils";

type Tab = "stocks" | "crypto" | "funds" | "bonds" | "portfolio" | "historical" | "settings" | "market-data" | "news" | "admin";

type Props = {
    keycloak: Keycloak;
};

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
        <path d="M7 10L3 6L7 2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M3 6H17C19.2 6 21 7.8 21 10V11" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M17 14L21 18L17 22" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M21 18H7C4.8 18 3 16.2 3 14V13" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
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

const SettingsIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="2"/>
        <path d="M19.4 15C19.5 14.7 19.5 14.3 19.4 14L21.5 12.3C21.7 12.1 21.7 11.8 21.6 11.6L19.6 8.4C19.5 8.2 19.2 8.1 19 8.2L16.6 9.2C16.1 8.8 15.6 8.5 15 8.3L14.6 5.8C14.6 5.6 14.4 5.4 14.2 5.4H10.2C10 5.4 9.8 5.6 9.8 5.8L9.4 8.3C8.8 8.5 8.3 8.8 7.8 9.2L5.4 8.2C5.2 8.1 4.9 8.2 4.8 8.4L2.8 11.6C2.7 11.8 2.7 12.1 2.9 12.3L5 14C4.9 14.3 4.9 14.7 5 15L2.9 16.7C2.7 16.9 2.7 17.2 2.8 17.4L4.8 20.6C4.9 20.8 5.2 20.9 5.4 20.8L7.8 19.8C8.3 20.2 8.8 20.5 9.4 20.7L9.8 23.2C9.8 23.4 10 23.6 10.2 23.6H14.2C14.4 23.6 14.6 23.4 14.6 23.2L15 20.7C15.6 20.5 16.1 20.2 16.6 19.8L19 20.8C19.2 20.9 19.5 20.8 19.6 20.6L21.6 17.4C21.7 17.2 21.7 16.9 21.5 16.7L19.4 15Z" stroke="currentColor" strokeWidth="2"/>
    </svg>
);

type NavItem = { id: Tab; label: string; icon: React.ReactNode; path: string; requiresAuth?: boolean };

const PUBLIC_NAV_ITEMS: NavItem[] = [
    { id: "news", label: "Anasayfa", icon: <NewsIcon />, path: "/news" },
    { id: "stocks", label: "Hisse Senetleri", icon: <StocksIcon />, path: "/stocks" },
    { id: "crypto", label: "Kripto Paralar", icon: <CryptoIcon />, path: "/crypto" },
    { id: "funds", label: "Yatırım Fonları", icon: <FundsIcon />, path: "/funds" },
    { id: "bonds", label: "Tahvil ve Bono", icon: <BondsIcon />, path: "/bonds" },
    { id: "market-data", label: "Döviz Kurları", icon: <ExchangeIcon />, path: "/market-data" },
];

const PRIVATE_NAV_ITEMS: NavItem[] = [
    { id: "portfolio", label: "Yatırımlarım", icon: <PortfolioIcon />, path: "/portfolio", requiresAuth: true },
    { id: "historical", label: "Geçmişten Bugüne", icon: <HistoricalIcon />, path: "/historical", requiresAuth: true },
];

const ADMIN_ITEMS: { id: Tab; label: string; icon: React.ReactNode; path: string }[] = [
    { id: "admin", label: "Yönetim", icon: <AdminIcon />, path: "/admin" },
];

const PREF_ITEMS: { id: Tab; label: string; icon: React.ReactNode; path: string }[] = [
    { id: "settings", label: "Ayarlar", icon: <SettingsIcon />, path: "/settings" },
];

export default function Sidebar({ keycloak }: Props) {
    const navigate = useNavigate();
    const location = useLocation();

    const username = useMemo(() => {
        const p: any = keycloak.tokenParsed;
        return p?.preferred_username ?? p?.name ?? p?.email ?? "Kullanıcı";
    }, [keycloak.tokenParsed]);

    const email = useMemo(() => {
        const p: any = keycloak.tokenParsed;
        return p?.email ?? "";
    }, [keycloak.tokenParsed]);

    const userIsAdmin = useMemo(() => isAdmin(keycloak), [keycloak.tokenParsed]);

    const initials = username.slice(0, 2).toUpperCase();

    const isActive = (path: string) => location.pathname === path;

    return (
        <div style={s.wrap}>
            {/* Brand */}
            <div style={s.brand}>
                <div style={s.logo}>
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M12 2L4 8V14L12 20L20 14V8L12 2Z" fill="#00ff00"/>
                        <path d="M12 8V16M8 12H16" stroke="#000000" strokeWidth="2" strokeLinecap="round"/>
                    </svg>
                </div>
                <div>
                    <div style={s.brandName}>Finans</div>
                    <div style={s.brandSub}>Investment Portal</div>
                </div>
            </div>

            {/* Navigation - public */}
            <div style={s.sectionLabel}>PİYASALAR</div>
            {PUBLIC_NAV_ITEMS.map((item) => (
                <button
                    key={item.id}
                    style={isActive(item.path) ? s.itemActive : s.item}
                    onClick={() => navigate(item.path)}
                >
                    <span style={s.icon}>{item.icon}</span>
                    {item.label}
                </button>
            ))}

            {/* Navigation - private (only when logged in) */}
            {keycloak.authenticated && (
                <>
                    <div style={s.sectionLabel}>HESABIM</div>
                    {PRIVATE_NAV_ITEMS.map((item) => (
                        <button
                            key={item.id}
                            style={isActive(item.path) ? s.itemActive : s.item}
                            onClick={() => navigate(item.path)}
                        >
                            <span style={s.icon}>{item.icon}</span>
                            {item.label}
                        </button>
                    ))}
                </>
            )}

            {/* Admin Section - Only visible for admins */}
            {userIsAdmin && (
                <>
                    <div style={s.sectionLabel}>ADMIN</div>
                    {ADMIN_ITEMS.map((item) => (
                        <button
                            key={item.id}
                            style={isActive(item.path) ? s.itemActive : s.item}
                            onClick={() => navigate(item.path)}
                        >
                            <span style={s.icon}>{item.icon}</span>
                            {item.label}
                        </button>
                    ))}
                </>
            )}

            <div style={{ flex: 1 }} />

            {/* Preferences */}
            <div style={s.sectionLabel}>PREFERENCES</div>
            {PREF_ITEMS.map((item) => (
                <button
                    key={item.id}
                    style={isActive(item.path) ? s.itemActive : s.item}
                    onClick={() => navigate(item.path)}
                >
                    <span style={s.icon}>{item.icon}</span>
                    {item.label}
                </button>
            ))}

            {/* User footer */}
            {keycloak.authenticated ? (
                <div style={s.userRow}>
                    <div style={s.avatar}>{initials}</div>
                    <div style={s.userInfo}>
                        <div style={s.userName}>{username}</div>
                        {email && <div style={s.userEmail}>{email}</div>}
                        {userIsAdmin && <div style={s.userBadge}>👑 Admin</div>}
                    </div>
                </div>
            ) : (
                <div style={s.guestRow}>
                    <button
                        style={s.guestLoginBtn}
                        onClick={() =>
                            keycloak.login({ redirectUri: window.location.href })
                        }
                    >
                        Giriş Yap
                    </button>
                    <button
                        style={s.guestRegisterBtn}
                        onClick={() =>
                            keycloak.register({ redirectUri: window.location.href })
                        }
                    >
                        Kayıt Ol
                    </button>
                </div>
            )}
        </div>
    );
}

const baseItem: React.CSSProperties = {
    width: "100%",
    textAlign: "left",
    display: "flex",
    alignItems: "center",
    gap: 10,
    padding: "8px 12px",
    borderRadius: 8,
    border: "none",
    cursor: "pointer",
    fontSize: 13,
    fontWeight: 500,
    marginBottom: 2,
    transition: "background 0.12s ease, color 0.12s ease",
};

const s: Record<string, React.CSSProperties> = {
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
        width: 36,
        height: 36,
        borderRadius: 8,
        background: "#000000",
        border: "2px solid #00ff00",
        display: "grid",
        placeItems: "center",
        flexShrink: 0,
    },
    brandName: {
        fontWeight: 700,
        fontSize: 14,
        color: "var(--text-primary)",
    },
    brandSub: {
        fontSize: 11,
        color: "var(--text-muted)",
        marginTop: 1,
    },
    sectionLabel: {
        fontSize: 10,
        fontWeight: 600,
        letterSpacing: 1.0,
        color: "var(--text-muted)",
        padding: "0 4px",
        marginBottom: 4,
        marginTop: 4,
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
        paddingLeft: 9,
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
        width: 30,
        height: 30,
        borderRadius: 6,
        background: "#000000",
        border: "2px solid #00ff00",
        display: "grid",
        placeItems: "center",
        fontSize: 11,
        fontWeight: 700,
        color: "#00ff00",
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
        fontWeight: 600,
        color: "#00ff00",
        marginTop: 2,
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

import type Keycloak from "keycloak-js";
import { useMemo } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { isAdmin } from "../utils/roleUtils";

type Tab = "market" | "portfolio" | "settings" | "market-data" | "news" | "admin";

type Props = {
    keycloak: Keycloak;
};

const NAV_ITEMS: { id: Tab; label: string; icon: string; path: string }[] = [
    { id: "news", label: "Finans Haberleri", icon: "📊", path: "/news" },
    { id: "market", label: "Hisseler", icon: "📈", path: "/market" },
    { id: "market-data", label: "Piyasa Verileri", icon: "💱", path: "/market-data" },
    { id: "portfolio", label: "Yatırımlar", icon: "💼", path: "/portfolio" },
];

const ADMIN_ITEMS: { id: Tab; label: string; icon: string; path: string }[] = [
    { id: "admin", label: "Yönetim", icon: "🔧", path: "/admin" },
];

const PREF_ITEMS: { id: Tab; label: string; icon: string; path: string }[] = [
    { id: "settings", label: "Ayarlar", icon: "⚙️", path: "/settings" },
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
                    <span style={{ fontSize: 15, fontWeight: 900, color: "#fff" }}>IH</span>
                </div>
                <div>
                    <div style={s.brandName}>InvestHub</div>
                    <div style={s.brandSub}>Investment Portal</div>
                </div>
            </div>

            {/* Navigation */}
            <div style={s.sectionLabel}>NAVIGATION</div>
            {NAV_ITEMS.map((item) => (
                <button
                    key={item.id}
                    style={isActive(item.path) ? s.itemActive : s.item}
                    onClick={() => navigate(item.path)}
                >
                    <span style={s.icon}>{item.icon}</span>
                    {item.label}
                </button>
            ))}

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
            <div style={s.userRow}>
                <div style={s.avatar}>{initials}</div>
                <div style={s.userInfo}>
                    <div style={s.userName}>{username}</div>
                    {email && <div style={s.userEmail}>{email}</div>}
                    {userIsAdmin && <div style={s.userBadge}>👑 Admin</div>}
                </div>
            </div>
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
        background: "linear-gradient(135deg, #15803d, #22c55e)",
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
        textAlign: "center",
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
        background: "linear-gradient(135deg, #15803d, #22c55e)",
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
        fontWeight: 600,
        color: "#22c55e",
        marginTop: 2,
    },
};

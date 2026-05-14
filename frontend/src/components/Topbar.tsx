import type { Theme } from "../theme";

type Props = {
    username: string;
    isAuthenticated: boolean;
    onLogin: () => void;
    onRegister: () => void;
    onLogout: () => void;
    title: string;
    subtitle?: string;
    right?: React.ReactNode;
    theme: Theme;
    onThemeToggle: () => void;
    onAlertsClick?: () => void;
    showAlerts?: boolean;
};

export default function Topbar({
    isAuthenticated,
    onLogin,
    onRegister,
    onLogout,
    title,
    subtitle,
    right,
    theme,
    onThemeToggle,
    onAlertsClick,
    showAlerts,
}: Props) {
    const isDark = theme === "dark";
    return (
        <div style={s.row} className="topbar">
            <div style={s.left}>
                <div style={s.title}>{title}</div>
                {subtitle && <div style={s.sub}>{subtitle}</div>}
            </div>
            <div style={s.right}>
                {right}
                {showAlerts && onAlertsClick && (
                    <button
                        style={s.iconBtn}
                        onClick={onAlertsClick}
                        title="Fiyat Alarmları"
                    >
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M12 2C10.9 2 10 2.9 10 4V4.29C7.03 5.17 5 7.9 5 11V17L3 19V20H21V19L19 17V11C19 7.9 16.97 5.17 14 4.29V4C14 2.9 13.1 2 12 2ZM12 23C13.1 23 14 22.1 14 21H10C10 22.1 10.9 23 12 23Z" fill="#ffaa00"/>
                        </svg>
                    </button>
                )}
                <button style={s.iconBtn} onClick={onThemeToggle} title={isDark ? "Açık tema" : "Koyu tema"} aria-label="Tema değiştir">
                    {isDark ? (
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <circle cx="12" cy="12" r="5" fill="#ffaa00"/>
                            <path d="M12 1V3M12 21V23M23 12H21M3 12H1M20.49 3.51L19.07 4.93M4.93 19.07L3.51 20.49M20.49 20.49L19.07 19.07M4.93 4.93L3.51 3.51" stroke="#ffaa00" strokeWidth="2" strokeLinecap="round"/>
                        </svg>
                    ) : (
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z" fill="#1e293b"/>
                        </svg>
                    )}
                </button>
                {isAuthenticated ? (
                    <button style={s.logoutBtn} onClick={onLogout}>
                        Çıkış
                    </button>
                ) : (
                    <>
                        <button style={s.loginBtn} onClick={onLogin}>
                            Giriş Yap
                        </button>
                        <button style={s.registerBtn} onClick={onRegister}>
                            Kayıt Ol
                        </button>
                    </>
                )}
            </div>
        </div>
    );
}

const s: Record<string, React.CSSProperties> = {
    row: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 12,
        flexWrap: "wrap",
    },
    left: {
        minWidth: 0,
        flex: "1 1 auto",
    },
    title: {
        fontSize: 22,
        fontWeight: 700,
        color: "var(--text-primary)",
        letterSpacing: "-0.01em",
    },
    sub: {
        color: "var(--text-muted)",
        fontSize: 13,
        marginTop: 2,
    },
    right: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        flexWrap: "wrap",
    },
    iconBtn: {
        width: 36,
        height: 36,
        borderRadius: 10,
        border: "1px solid var(--border-card)",
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        fontSize: 14,
        cursor: "pointer",
        display: "grid",
        placeItems: "center",
        transition: "all 0.2s",
    },
    logoutBtn: {
        padding: "8px 16px",
        borderRadius: 10,
        border: "1px solid var(--danger-border)",
        background: "var(--danger-bg)",
        color: "var(--danger-text)",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
        transition: "all 0.2s",
    },
    loginBtn: {
        padding: "8px 16px",
        borderRadius: 10,
        border: "1px solid var(--border-card)",
        background: "transparent",
        color: "var(--text-primary)",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
        transition: "all 0.2s",
    },
    registerBtn: {
        padding: "8px 16px",
        borderRadius: 10,
        border: "1px solid var(--accent-solid)",
        background: "var(--accent-solid)",
        color: "#000",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 700,
        transition: "all 0.2s",
    },
};

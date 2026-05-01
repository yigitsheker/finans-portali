import type { Theme } from "../theme";

type Props = {
    username: string;
    onLogout: () => void;
    title: string;
    subtitle?: string;
    right?: React.ReactNode;
    theme: Theme;
    onThemeToggle: () => void;
    onAlertsClick?: () => void;
    showAlerts?: boolean;
};

export default function Topbar({ onLogout, title, subtitle, right, theme, onThemeToggle, onAlertsClick, showAlerts }: Props) {
    const isDark = theme === "dark";
    return (
        <div style={s.row}>
            <div>
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
                        🔔
                    </button>
                )}
                <button style={s.iconBtn} onClick={onThemeToggle} title={isDark ? "Açık tema" : "Koyu tema"}>
                    {isDark ? "☀️" : "🌙"}
                </button>
                <button style={s.logoutBtn} onClick={onLogout}>
                    Çıkış
                </button>
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
    },
    title: {
        fontSize: 20,
        fontWeight: 700,
        color: "var(--text-primary)",
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
    },
    iconBtn: {
        width: 34,
        height: 34,
        borderRadius: 8,
        border: "1px solid var(--border-card)",
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        fontSize: 14,
        cursor: "pointer",
        display: "grid",
        placeItems: "center",
    },
    logoutBtn: {
        padding: "7px 14px",
        borderRadius: 8,
        border: "1px solid var(--danger-border)",
        background: "var(--danger-bg)",
        color: "var(--danger-text)",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 500,
    },
};

type Props = {
    tab: "news" | "portfolio";
    onTabChange: (t: "news" | "portfolio") => void;
    username: string;
    onLogout: () => void;
};

export default function Navbar({
                                   tab,
                                   onTabChange,
                                   username,
                                   onLogout,
                               }: Props) {
    return (
        <header style={styles.header}>
            <div style={styles.inner}>
                <div style={styles.left}>
                    <div style={styles.brand}>Finans Portal</div>

                    <nav style={styles.nav}>
                        <button
                            onClick={() => onTabChange("news")}
                            style={tab === "news" ? styles.tabActive : styles.tab}
                        >
                            News
                        </button>
                        <button
                            onClick={() => onTabChange("portfolio")}
                            style={tab === "portfolio" ? styles.tabActive : styles.tab}
                        >
                            Portfolio
                        </button>
                    </nav>
                </div>

                <div style={styles.right}>
                    <div style={styles.user}>
                        <span style={{ opacity: 0.7 }}>User</span>{" "}
                        <b>{username}</b>
                    </div>
                    <button onClick={onLogout} style={styles.logout}>
                        Logout
                    </button>
                </div>
            </div>
        </header>
    );
}

const styles: Record<string, React.CSSProperties> = {
    header: {
        position: "sticky",
        top: 0,
        zIndex: 50,
        background: "rgba(11,18,32,0.75)",
        backdropFilter: "blur(10px)",
        borderBottom: "1px solid rgba(255,255,255,0.08)",
    },
    inner: {
        maxWidth: 980,
        margin: "0 auto",
        padding: "14px 16px",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 12,
    },
    left: { display: "flex", alignItems: "center", gap: 14 },
    brand: { fontWeight: 800, letterSpacing: 0.2 },
    nav: { display: "flex", gap: 8 },
    tab: {
        padding: "8px 12px",
        borderRadius: 10,
        border: "1px solid rgba(255,255,255,0.10)",
        background: "rgba(255,255,255,0.03)",
        color: "#e5e7eb",
        cursor: "pointer",
    },
    tabActive: {
        padding: "8px 12px",
        borderRadius: 10,
        border: "1px solid rgba(99,102,241,0.55)",
        background: "rgba(99,102,241,0.18)",
        color: "#e5e7eb",
        cursor: "pointer",
    },
    right: { display: "flex", alignItems: "center", gap: 12 },
    user: {
        padding: "6px 10px",
        borderRadius: 10,
        border: "1px solid rgba(255,255,255,0.08)",
        background: "rgba(255,255,255,0.03)",
        fontSize: 13,
    },
    logout: {
        padding: "8px 12px",
        borderRadius: 10,
        border: "1px solid rgba(239,68,68,0.5)",
        background: "rgba(239,68,68,0.12)",
        color: "#fee2e2",
        cursor: "pointer",
    },
};
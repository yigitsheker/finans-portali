export default function Layout({ sidebar, topbar, children }) {
    return (
        <div className="fp-shell">
            <aside className="fp-sidebar">
                {sidebar}
            </aside>
            <div className="fp-content">
                {topbar && (
                    <header style={s.topbar}>
                        {topbar}
                    </header>
                )}
                <main style={s.main}>
                    {children}
                </main>
            </div>
        </div>
    );
}

const s = {
    topbar: {
        padding: "14px 24px",
        borderBottom: "1px solid var(--border)",
        background: "var(--bg-topbar)",
        flexShrink: 0,
    },
    main: {
        flex: 1,
        padding: "24px",
        overflowY: "auto",
    },
};

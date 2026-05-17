export default function Layout({ sidebar, topbar, ticker, children }) {
  return (
    <div className="fp-shell">
      <aside className="fp-sidebar">{sidebar}</aside>
      <div className="fp-content">
        {ticker && <div style={s.tickerSlot}>{ticker}</div>}
        {topbar && (
          <header style={s.topbar} className="topbar">
            {topbar}
          </header>
        )}
        <main style={s.main}>{children}</main>
      </div>
    </div>
  );
}

const s = {
  tickerSlot: { flexShrink: 0 },
  topbar: {
    padding: "14px 24px",
    borderBottom: "1px solid var(--border-card)",
    background: "var(--bg-topbar)",
    flexShrink: 0,
    position: "sticky",
    top: 0,
    zIndex: 50,
    backdropFilter: "saturate(180%) blur(8px)",
  },
  main: {
    flex: 1,
    padding: "24px",
    overflowY: "auto",
  },
};

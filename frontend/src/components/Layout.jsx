/**
 * Page shell — vertical stack of three regions:
 *
 *   [Ticker]    sticky top:0, always visible while scrolling
 *   [Topbar]    sticky right below the ticker, holds brand + nav + widgets
 *   [Main]      scrolls under the sticky header
 *
 * The left rail sidebar from earlier iterations is gone: nav lives inline
 * in the Topbar now, so this component is a thin wrapper that arranges
 * those three slots and reserves enough z-index for them to layer above
 * page content (dropdowns, modals).
 */
export default function Layout({ topbar, ticker, children }) {
  return (
    <div style={s.shell}>
      {ticker && (
        <div style={s.tickerSlot} className="fp-ticker-slot">
          {ticker}
        </div>
      )}
      {topbar && (
        <header style={s.topbar} className="fp-topbar-slot">
          {topbar}
        </header>
      )}
      <main style={s.main} className="fp-main">
        {children}
      </main>
    </div>
  );
}

const s = {
  shell: {
    minHeight: "100vh",
    display: "flex",
    flexDirection: "column",
    background: "var(--bg-page, var(--bg-content))",
  },
  tickerSlot: {
    position: "sticky",
    top: 0,
    zIndex: 80,
    flexShrink: 0,
  },
  topbar: {
    position: "sticky",
    top: 40, // ticker height (.fp-ticker is 40px tall)
    zIndex: 70,
    flexShrink: 0,
    padding: "12px 24px",
    background: "var(--bg-topbar, var(--bg-card))",
    borderBottom: "1px solid var(--border-card)",
    backdropFilter: "saturate(180%) blur(10px)",
    WebkitBackdropFilter: "saturate(180%) blur(10px)",
  },
  main: {
    flex: 1,
    padding: "24px",
    overflow: "visible",
  },
};

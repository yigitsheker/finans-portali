import { useEffect, useRef, useState, useCallback } from "react";
import {
  getNotifications,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
} from "../api/notificationApi";
import notify from "../utils/notify";

const POLL_INTERVAL_MS = 60_000; // poll inbox every minute
// Where we remember the highest-seen notification ID so we only toast new
// arrivals — not the existing inbox on first load.
const LAST_SEEN_KEY = "notif-last-seen-id";

export default function NotificationBell({ keycloak }) {
  const [open, setOpen] = useState(false);
  const [count, setCount] = useState(0);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef(null);

  // Poll the unread count in the background so the badge stays fresh
  // even when the dropdown is closed. When the count rises since the last
  // tick, fetch the inbox and toast each notification whose id is newer
  // than the highest one we've ever seen (kept in localStorage so we
  // don't re-toast after a reload).
  useEffect(() => {
    if (!keycloak?.authenticated) return;
    let cancelled = false;
    let lastCount = -1;

    const readLastSeen = () => {
      try { return Number(localStorage.getItem(LAST_SEEN_KEY)) || 0; }
      catch { return 0; }
    };
    const writeLastSeen = (id) => {
      try { localStorage.setItem(LAST_SEEN_KEY, String(id)); } catch { /* ignore */ }
    };

    const toastNewArrivals = async () => {
      try {
        const list = await getNotifications(keycloak, 10);
        const lastSeen = readLastSeen();
        const fresh = (list || []).filter((n) => Number(n.id) > lastSeen);
        if (fresh.length === 0) return;
        // Toast in chronological order (oldest first) so the newest ends up
        // on top of the stack.
        fresh.slice().reverse().forEach((n) => {
          // Map backend notification type → our category. Unknown types use
          // the generic push channel.
          const t = String(n.type || "").toLowerCase();
          const message = n.title ? `${n.title}${n.message ? " — " + n.message : ""}` : (n.message || "Yeni bildirim");
          if (t.includes("alert") || t.includes("price")) {
            notify.push(message);
          } else if (t.includes("security") || t.includes("login")) {
            notify.security(message);
          } else if (t.includes("news") || t.includes("market") || t.includes("offer")) {
            notify.marketing(message);
          } else if (t.includes("transaction") || t.includes("trade") || t.includes("order")) {
            notify.tx(message);
          } else {
            notify.push(message);
          }
        });
        const maxId = Math.max(...fresh.map((n) => Number(n.id)));
        if (Number.isFinite(maxId)) writeLastSeen(maxId);
        setItems(list || []);
      } catch { /* ignore */ }
    };

    const tick = async () => {
      try {
        const n = await getUnreadCount(keycloak);
        if (cancelled) return;
        setCount(n);
        // Only fetch the full inbox to derive new arrivals when the count
        // actually rose — saves a request when nothing changed.
        if (lastCount >= 0 && n > lastCount) {
          await toastNewArrivals();
        } else if (lastCount === -1) {
          // First tick: seed lastSeen with the current top id so we don't
          // toast the user's existing inbox.
          try {
            const list = await getNotifications(keycloak, 1);
            if ((list || [])[0]?.id) writeLastSeen(Number(list[0].id));
          } catch { /* ignore */ }
        }
        lastCount = n;
      } catch { /* ignore — likely auth refresh in progress */ }
    };

    tick();
    const id = setInterval(tick, POLL_INTERVAL_MS);
    return () => { cancelled = true; clearInterval(id); };
  }, [keycloak, keycloak?.authenticated]);

  // Close the dropdown when clicking elsewhere on the page.
  useEffect(() => {
    if (!open) return;
    const onClick = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, [open]);

  const openDropdown = useCallback(async () => {
    setOpen(true);
    setLoading(true);
    try {
      const list = await getNotifications(keycloak, 20);
      setItems(list || []);
    } catch (e) {
      console.warn("Bildirimler yüklenemedi:", e);
    } finally {
      setLoading(false);
    }
  }, [keycloak]);

  const onItemClick = useCallback(async (n) => {
    if (n.read) return;
    try {
      await markAsRead(keycloak, n.id);
      setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
      setCount((c) => Math.max(0, c - 1));
    } catch { /* ignore */ }
  }, [keycloak]);

  const onMarkAll = useCallback(async () => {
    try {
      await markAllAsRead(keycloak);
      setItems((prev) => prev.map((x) => ({ ...x, read: true })));
      setCount(0);
    } catch { /* ignore */ }
  }, [keycloak]);

  if (!keycloak?.authenticated) return null;

  return (
    <div ref={containerRef} style={{ position: "relative" }}>
      <button
        style={s.iconBtn}
        onClick={() => (open ? setOpen(false) : openDropdown())}
        title="Bildirimler"
        aria-label="Bildirimler"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
          <path d="M18 16V11C18 7.69 15.31 5 12 5C8.69 5 6 7.69 6 11V16L4 18V19H20V18L18 16Z"
                stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
          <path d="M10 21C10 22.1 10.9 23 12 23C13.1 23 14 22.1 14 21"
                stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
        {count > 0 && (
          <span style={s.badge}>{count > 99 ? "99+" : count}</span>
        )}
      </button>

      {open && (
        <div style={s.panel}>
          <div style={s.panelHeader}>
            <span style={s.panelTitle}>Bildirimler</span>
            {items.some((n) => !n.read) && (
              <button style={s.markAllBtn} onClick={onMarkAll}>
                Tümünü okundu işaretle
              </button>
            )}
          </div>

          {loading ? (
            <div style={s.empty}>Yükleniyor…</div>
          ) : items.length === 0 ? (
            <div style={s.empty}>📭 Henüz bildirim yok</div>
          ) : (
            <div style={s.list}>
              {items.map((n) => (
                <button
                  key={n.id}
                  onClick={() => onItemClick(n)}
                  style={{ ...s.item, ...(n.read ? {} : s.itemUnread) }}
                >
                  <div style={s.itemHead}>
                    <span style={s.itemIcon}>
                      {n.type === "PRICE_ALERT" ? "🔔" : "ℹ️"}
                    </span>
                    <span style={s.itemTitle}>{n.title}</span>
                    {!n.read && <span style={s.unreadDot} />}
                  </div>
                  <div style={s.itemMessage}>{n.message}</div>
                  <div style={s.itemDate}>{formatDate(n.createdAt)}</div>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function formatDate(iso) {
  if (!iso) return "";
  try {
    const d = new Date(iso);
    const diffMs = Date.now() - d.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return "az önce";
    if (diffMin < 60) return `${diffMin} dk önce`;
    const diffH = Math.floor(diffMin / 60);
    if (diffH < 24) return `${diffH} sa önce`;
    return d.toLocaleString("tr-TR", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
  } catch { return iso; }
}

const s = {
  iconBtn: {
    position: "relative",
    width: 32, height: 32, borderRadius: 8,
    border: "1px solid var(--border-card)",
    background: "var(--bg-card)",
    color: "var(--text-primary)",
    cursor: "pointer",
    display: "inline-flex", alignItems: "center", justifyContent: "center",
    flexShrink: 0,
  },
  badge: {
    position: "absolute", top: -4, right: -4,
    minWidth: 18, height: 18,
    padding: "0 5px",
    borderRadius: 9,
    background: "#ef4444",
    color: "#fff",
    fontSize: 10, fontWeight: 700,
    display: "inline-flex", alignItems: "center", justifyContent: "center",
    boxShadow: "0 1px 4px rgba(0,0,0,0.4)",
  },
  panel: {
    position: "absolute",
    top: "100%", right: 0, marginTop: 8,
    width: 360, maxHeight: 480,
    background: "var(--bg-card)",
    border: "1px solid var(--border-card)",
    borderRadius: 10,
    boxShadow: "0 8px 24px rgba(0,0,0,0.25)",
    display: "flex", flexDirection: "column",
    zIndex: 1000,
  },
  panelHeader: {
    display: "flex", justifyContent: "space-between", alignItems: "center",
    padding: "12px 14px",
    borderBottom: "1px solid var(--border-soft)",
  },
  panelTitle: { fontSize: 14, fontWeight: 700, color: "var(--text-primary)" },
  markAllBtn: {
    border: "none",
    background: "transparent",
    color: "var(--accent-solid, #3b82f6)",
    fontSize: 11, fontWeight: 600,
    cursor: "pointer",
  },
  empty: { padding: "28px 16px", textAlign: "center", color: "var(--text-muted)", fontSize: 13 },
  list: { overflowY: "auto", maxHeight: 420, display: "flex", flexDirection: "column" },
  item: {
    textAlign: "left",
    padding: "12px 14px",
    border: "none",
    borderBottom: "1px solid var(--border-soft)",
    background: "transparent",
    cursor: "pointer",
    display: "flex", flexDirection: "column", gap: 4,
    color: "var(--text-primary)",
  },
  itemUnread: { background: "rgba(59,130,246,0.06)" },
  itemHead: { display: "flex", alignItems: "center", gap: 8 },
  itemIcon: { fontSize: 14 },
  itemTitle: { fontSize: 13, fontWeight: 600, flex: 1 },
  unreadDot: { width: 8, height: 8, borderRadius: "50%", background: "#3b82f6" },
  itemMessage: { fontSize: 12, color: "var(--text-muted)", lineHeight: 1.4 },
  itemDate: { fontSize: 10, color: "var(--text-muted)", opacity: 0.7, marginTop: 2 },
};

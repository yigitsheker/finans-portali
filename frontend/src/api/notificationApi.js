const BASE = "/api/v1/notifications";

function authHeaders(keycloak) {
  return keycloak?.token ? { Authorization: `Bearer ${keycloak.token}` } : {};
}

export async function getNotifications(keycloak, limit = 30) {
  const r = await fetch(`${BASE}?limit=${limit}`, { headers: authHeaders(keycloak) });
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r.json();
}

export async function getUnreadCount(keycloak) {
  const r = await fetch(`${BASE}/unread-count`, { headers: authHeaders(keycloak) });
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  const d = await r.json();
  return Number(d.count || 0);
}

export async function markAsRead(keycloak, id) {
  const r = await fetch(`${BASE}/${id}/read`, { method: "POST", headers: authHeaders(keycloak) });
  if (!r.ok && r.status !== 204) throw new Error(`HTTP ${r.status}`);
}

export async function markAllAsRead(keycloak) {
  const r = await fetch(`${BASE}/mark-all-read`, { method: "POST", headers: authHeaders(keycloak) });
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r.json();
}

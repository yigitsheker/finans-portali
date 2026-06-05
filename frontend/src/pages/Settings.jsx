import { useEffect, useMemo, useRef, useState } from "react";
import { applyTheme } from "../theme";
import { useI18n } from "../contexts/I18nContext";
import { getNotificationPrefs, putNotificationPrefs } from "../api/userPrefsApi";
import { getSecurityStatus, disable2fa } from "../api/securityApi";
import notify from "../utils/notify";

const NOTIF_ITEMS = [
  { key: "transactions", labelKey: "settings.txNotif",         descKey: "settings.txNotifSub",         defaultOn: true },
  { key: "budget",       labelKey: "settings.budgetWarn",      descKey: "settings.budgetWarnSub",      defaultOn: true },
  { key: "investments",  labelKey: "settings.investUpdates",   descKey: "settings.investUpdatesSub",   defaultOn: false },
  { key: "marketing",    labelKey: "settings.marketingComm",   descKey: "settings.marketingCommSub",   defaultOn: false },
  { key: "push",         labelKey: "settings.pushNotif",       descKey: "settings.pushNotifSub",       defaultOn: true },
  { key: "security",     labelKey: "settings.securityAlert",   descKey: "settings.securityAlertSub",   defaultOn: true },
];

const NOTIF_PREF_KEY = "notif-preferences";

function loadNotifPrefs() {
  try {
    const raw = localStorage.getItem(NOTIF_PREF_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      // Merge with defaults so newly added items in the source list still appear.
      const out = {};
      NOTIF_ITEMS.forEach((item) => {
        out[item.key] = typeof parsed[item.key] === "boolean" ? parsed[item.key] : item.defaultOn;
      });
      return out;
    }
  } catch { /* ignore corrupt storage */ }
  const out = {};
  NOTIF_ITEMS.forEach((item) => { out[item.key] = item.defaultOn; });
  return out;
}

export default function Settings({ keycloak, theme, onThemeChange }) {
  const { t } = useI18n();
  const parsed = keycloak.tokenParsed;

  // Profile fields — seeded from JWT (which is what Keycloak ships back after our
  // PATCH call refreshes the token), so this stays in sync with what's persisted.
  const [firstName, setFirstName] = useState(parsed?.given_name ?? parsed?.name?.split(" ")[0] ?? "");
  const [lastName,  setLastName]  = useState(parsed?.family_name ?? parsed?.name?.split(" ").slice(1).join(" ") ?? "");
  const [email,     setEmail]     = useState(parsed?.email ?? "");
  const [phone,     setPhone]     = useState(parsed?.phone ?? "");
  const [saving,    setSaving]    = useState(false);
  const [saveStatus, setSaveStatus] = useState(null); // null | "ok" | "error"
  const [saveError, setSaveError]   = useState(null);

  // Avatar — stored as a data URL in localStorage (per-browser; no backend
  // avatar endpoint). Clicking the avatar opens a file picker.
  const [avatarUrl, setAvatarUrl] = useState(() => { try { return localStorage.getItem("user-avatar") || ""; } catch { return ""; } });
  const avatarInputRef = useRef(null);

  const [notifs, setNotifs] = useState(loadNotifPrefs);
  // Tracks whether we've already pulled the server-side copy. We don't
  // want the initial render's localStorage value to immediately fire a
  // PUT — that would clobber server state with stale data on every load.
  const hydratedFromServerRef = useRef(false);

  // 1) On mount, pull the user's persisted choice from the backend.
  //    Server wins over localStorage (it's the cross-device source of
  //    truth). If the API is unreachable we keep whatever we hydrated
  //    from localStorage; the user's UX isn't blocked.
  useEffect(() => {
    if (!keycloak?.authenticated) return;
    let cancelled = false;
    getNotificationPrefs(keycloak)
      .then((server) => {
        if (cancelled) return;
        // Merge so a missing key on the server doesn't wipe out a default.
        setNotifs((prev) => {
          const merged = { ...prev };
          NOTIF_ITEMS.forEach((item) => {
            if (typeof server[item.key] === "boolean") {
              merged[item.key] = server[item.key];
            }
          });
          return merged;
        });
        hydratedFromServerRef.current = true;
      })
      .catch((e) => {
        console.warn("[Settings] Could not fetch notification prefs:", e?.message);
        // Still mark as hydrated so the local-only state can sync up
        // when the user toggles (better to write something than nothing).
        hydratedFromServerRef.current = true;
      });
    return () => { cancelled = true; };
  }, [keycloak]);

  // 2) Persist locally on every change (fast, offline-safe).
  useEffect(() => {
    try { localStorage.setItem(NOTIF_PREF_KEY, JSON.stringify(notifs)); } catch { /* ignore quota */ }
  }, [notifs]);

  // 3) Push to backend, debounced 500ms so rapid toggles coalesce.
  //    Skipped until the server hydration is done, so the first render
  //    doesn't immediately overwrite the server copy with localStorage.
  useEffect(() => {
    if (!hydratedFromServerRef.current || !keycloak?.authenticated) return;
    const id = setTimeout(() => {
      // Silently swallow — the server keeps the previous prefs and the UI
      // will retry on the next change. Logging e?.message would echo
      // server-side user data through console (Sonar S4507).
      putNotificationPrefs(keycloak, notifs).catch(() => { /* ignore */ });
    }, 500);
    return () => clearTimeout(id);
  }, [notifs, keycloak]);

  const initials = useMemo(() => {
    const name = (firstName + " " + lastName).trim();
    return name ? name.slice(0, 2).toUpperCase() : "JD";
  }, [firstName, lastName]);

  // 2FA status comes from the backend (Keycloak emits no "has TOTP" token
  // claim). null = still loading / unknown. Re-fetched on mount, which also
  // covers the return trip from Keycloak's CONFIGURE_TOTP redirect (the page
  // reloads at baseRedirect).
  const [totpEnabled, setTotpEnabled] = useState(null);
  const [twoFaBusy, setTwoFaBusy] = useState(false);

  useEffect(() => {
    if (!keycloak?.authenticated) return;
    let cancelled = false;
    getSecurityStatus(keycloak)
      .then((s) => { if (!cancelled) setTotpEnabled(Boolean(s.totpEnabled)); })
      .catch((e) => {
        console.warn("[Settings] Could not fetch 2FA status:", e?.message);
        if (!cancelled) setTotpEnabled(false); // fail open to the "set up" CTA
      });
    return () => { cancelled = true; };
  }, [keycloak]);

  async function disable2FA() {
    if (!window.confirm(t("settings.mfaDisableConfirm"))) return;
    setTwoFaBusy(true);
    notify(t("settings.mfaDisabling"), { variant: "loading" });
    try {
      await disable2fa(keycloak);
      setTotpEnabled(false);
      notify(t("settings.mfaDisabledOk"), { variant: "success" });
    } catch (e) {
      console.error("2FA devre dışı bırakılamadı:", e?.message);
      notify(t("settings.mfaDisableError"), { variant: "error" });
    } finally {
      setTwoFaBusy(false);
    }
  }

  function onPickAvatar(e) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    if (!file.type.startsWith("image/") || file.size > 2 * 1024 * 1024) {
      notify(t("settings.avatarError"), { variant: "error" });
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const url = String(reader.result || "");
      setAvatarUrl(url);
      try { localStorage.setItem("user-avatar", url); } catch { /* quota */ }
      notify(t("settings.avatarUpdated"), { variant: "success" });
    };
    reader.readAsDataURL(file);
  }
  function clearAvatar() {
    setAvatarUrl("");
    try { localStorage.removeItem("user-avatar"); } catch { /* ignore */ }
  }

  async function handleSave() {
    setSaving(true);
    setSaveStatus(null);
    setSaveError(null);
    try {
      const r = await fetch("/api/v1/users/me", {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${keycloak.token}`,
        },
        body: JSON.stringify({ firstName, lastName, email, phone }),
      });
      if (!r.ok) {
        const text = await r.text();
        throw new Error(text || `HTTP ${r.status}`);
      }
      // Refresh the JWT so its embedded claims match the new profile values.
      try { await keycloak.updateToken(-1); } catch { /* token may already be fresh */ }
      setSaveStatus("ok");
      notify(t("settings.saved"), { variant: "success" });
      setTimeout(() => setSaveStatus(null), 2500);
    } catch (e) {
      console.error("Profil kaydedilemedi:", e);
      setSaveStatus("error");
      setSaveError(e?.message ?? "Bilinmeyen hata");
      notify(`${t("settings.error")}: ${e?.message ?? ""}`, { variant: "error" });
    } finally {
      setSaving(false);
    }
  }

  function toggleNotif(key) {
    setNotifs((prev) => ({ ...prev, [key]: !prev[key] }));
  }

  function handleTheme(next) {
    onThemeChange(next);
    applyTheme(next);
  }

  const baseRedirect = window.location.href;
  function setup2FA() {
    keycloak.login({ action: "CONFIGURE_TOTP", redirectUri: baseRedirect });
  }
  function changePassword() {
    keycloak.login({ action: "UPDATE_PASSWORD", redirectUri: baseRedirect });
  }
  function manageAccount() {
    const url = keycloak.createAccountUrl({ redirectUri: baseRedirect });
    window.location.href = url;
  }

  const THEME_OPTIONS = [
    { key: "light",  label: t("settings.themeLight"),  icon: "☀️" },
    { key: "dark",   label: t("settings.themeDark"),   icon: "🌙" },
    { key: "system", label: t("settings.themeSystem"), icon: "💻" },
  ];

  return (
    <div style={s.root}>
      <div className="settings-grid" style={s.grid}>
        {/* Profile */}
        <div style={s.card}>
          <div style={s.cardTitle}>{t("settings.profile")}</div>
          <div style={s.cardSub}>{t("settings.profileSub")}</div>
          <div style={s.avatarRow}>
            <button type="button" onClick={() => avatarInputRef.current?.click()} style={s.avatarBtn} title={t("settings.changeAvatar")} aria-label={t("settings.changeAvatar")}>
              {avatarUrl
                ? <img src={avatarUrl} alt="" style={s.avatarImg} />
                : <span style={s.avatar}>{initials}</span>}
            </button>
            <input ref={avatarInputRef} type="file" accept="image/*" style={{ display: "none" }} onChange={onPickAvatar} />
            <div>
              <button type="button" onClick={() => avatarInputRef.current?.click()} style={s.avatarChangeLink}>{t("settings.changeAvatar")}</button>
              <div style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 2 }}>{t("settings.avatarHint")}</div>
              {avatarUrl && (
                <button type="button" onClick={clearAvatar} style={s.avatarRemove}>{t("settings.avatarRemove")}</button>
              )}
            </div>
          </div>
          <div className="settings-form-grid" style={s.formGrid}>
            <div style={s.field}>
              <label style={s.label}>{t("settings.firstName")}</label>
              <input value={firstName} onChange={(e) => setFirstName(e.target.value)} style={s.input} placeholder={t("settings.firstNamePh")} />
            </div>
            <div style={s.field}>
              <label style={s.label}>{t("settings.lastName")}</label>
              <input value={lastName} onChange={(e) => setLastName(e.target.value)} style={s.input} placeholder={t("settings.lastNamePh")} />
            </div>
            <div style={{ ...s.field, gridColumn: "span 2" }}>
              <label style={s.label}>{t("settings.email")}</label>
              <input value={email} onChange={(e) => setEmail(e.target.value)} style={s.input} placeholder={t("settings.emailPh")} type="email" />
            </div>
            <div style={{ ...s.field, gridColumn: "span 2" }}>
              <label style={s.label}>{t("settings.phone")}</label>
              <input value={phone} onChange={(e) => setPhone(e.target.value)} style={s.input} placeholder={t("settings.phonePh")} type="tel" />
            </div>
          </div>
          <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
            <button style={{ ...s.saveBtn, opacity: saving ? 0.6 : 1 }} onClick={handleSave} disabled={saving}>
              {saving ? t("settings.saving") : saveStatus === "ok" ? t("settings.saved") : t("settings.saveChanges")}
            </button>
            {saveStatus === "error" && (
              <span style={{ color: "var(--danger-text, #ef4444)", fontSize: 12 }}>
                {`${t("settings.error")}: ${saveError}`}
              </span>
            )}
          </div>
        </div>

        {/* Right column */}
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {/* Appearance */}
          <div style={s.card}>
            <div style={s.cardTitle}>{t("settings.appearance")}</div>
            <div style={s.cardSub}>{t("settings.appearanceSub")}</div>
            <div style={s.themeRow}>
              {THEME_OPTIONS.map((opt) => (
                <button
                  key={opt.key}
                  style={{ ...s.themeBtn, ...(theme === opt.key ? s.themeBtnActive : {}) }}
                  onClick={() => handleTheme(opt.key)}
                >
                  <span style={{ fontSize: 22 }}>{opt.icon}</span>
                  <span style={{ fontSize: 12, marginTop: 4 }}>{opt.label}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Security */}
          <div style={s.card}>
            <div style={s.cardTitle}>{t("settings.security")}</div>
            <div style={s.cardSub}>{t("settings.securitySub")}</div>

            <div style={s.secRow}>
              <div style={{ flex: 1 }}>
                <div style={s.secLabel}>{t("settings.mfa")}</div>
                <div style={s.secDesc}>
                  {totpEnabled === null
                    ? t("settings.mfaChecking")
                    : totpEnabled
                      ? t("settings.mfaActive")
                      : t("settings.mfaInactive")}
                </div>
              </div>
              {totpEnabled === null ? (
                <button style={{ ...s.secBtn, opacity: 0.6 }} disabled>
                  {t("settings.mfaChecking")}
                </button>
              ) : totpEnabled ? (
                // Already enrolled: don't blindly re-trigger setup (Keycloak's
                // CONFIGURE_TOTP would silently ADD a second authenticator).
                // Offer an explicit "add device" plus a real Disable action.
                <div style={s.secBtnGroup}>
                  <button style={s.secBtnOutline} onClick={setup2FA} disabled={twoFaBusy}>
                    {t("settings.mfaAddDevice")}
                  </button>
                  <button
                    style={{ ...s.secBtnDanger, opacity: twoFaBusy ? 0.6 : 1 }}
                    onClick={disable2FA}
                    disabled={twoFaBusy}
                  >
                    {twoFaBusy ? t("settings.mfaDisabling") : t("settings.mfaDisable")}
                  </button>
                </div>
              ) : (
                <button style={s.secBtn} onClick={setup2FA}>
                  {t("settings.setup")}
                </button>
              )}
            </div>

            <div style={{ ...s.secRow, borderTop: "1px solid var(--border-soft)" }}>
              <div style={{ flex: 1 }}>
                <div style={s.secLabel}>{t("settings.changePassword")}</div>
                <div style={s.secDesc}>{t("settings.changePasswordSub")}</div>
              </div>
              <button style={s.secBtn} onClick={changePassword}>{t("settings.changeBtn")}</button>
            </div>

            <div style={{ ...s.secRow, borderTop: "1px solid var(--border-soft)" }}>
              <div style={{ flex: 1 }}>
                <div style={s.secLabel}>{t("settings.keycloak")}</div>
                <div style={s.secDesc}>
                  {t("settings.keycloakSub")}
                </div>
              </div>
              <button style={s.secBtnOutline} onClick={manageAccount}>{t("settings.open")}</button>
            </div>
          </div>

          {/* Notifications */}
          <div style={s.card}>
            <div style={s.cardTitle}>{t("settings.notifications")}</div>
            <div style={s.cardSub}>{t("settings.notificationsSub")}</div>
            {NOTIF_ITEMS.map((item, i) => (
              <div key={item.key} style={{ ...s.notifRow, borderBottom: i < NOTIF_ITEMS.length - 1 ? "1px solid var(--border-soft)" : "none" }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 13, fontWeight: 500, color: "var(--text-primary)", marginBottom: 2 }}>{t(item.labelKey)}</div>
                  <div style={{ fontSize: 11, color: "var(--text-muted)" }}>{t(item.descKey)}</div>
                </div>
                <Toggle on={!!notifs[item.key]} onToggle={() => toggleNotif(item.key)} />
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function Toggle({ on, onToggle }) {
  return (
    <button onClick={onToggle} role="switch" aria-checked={on}
      style={{ width: 42, height: 22, borderRadius: 999, border: "none", background: on ? "var(--accent-solid)" : "var(--input-bg)", cursor: "pointer", position: "relative", flexShrink: 0, transition: "background 0.2s" }}>
      <span style={{ position: "absolute", top: 3, left: on ? 22 : 3, width: 16, height: 16, borderRadius: "50%", background: "#fff", transition: "left 0.2s", boxShadow: "0 1px 3px rgba(0,0,0,0.3)" }} />
    </button>
  );
}

const s = {
  root: { display: "flex", flexDirection: "column", gap: 16 },
  grid: { display: "grid", gap: 12, alignItems: "start" },
  card: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-card)", padding: "20px 22px" },
  cardTitle: { fontSize: 15, fontWeight: 600, color: "var(--text-primary)", marginBottom: 4 },
  cardSub: { fontSize: 12, color: "var(--text-muted)", marginBottom: 18 },
  avatarRow: { display: "flex", alignItems: "center", gap: 14, marginBottom: 20 },
  avatar: { width: 52, height: 52, borderRadius: 10, background: "linear-gradient(135deg, var(--accent-strong), var(--accent-solid))", display: "grid", placeItems: "center", fontSize: 16, fontWeight: 700, color: "#fff", flexShrink: 0 },
  avatarBtn: { padding: 0, border: "none", background: "transparent", cursor: "pointer", borderRadius: 10, flexShrink: 0, lineHeight: 0 },
  avatarImg: { width: 52, height: 52, borderRadius: 10, objectFit: "cover", display: "block" },
  avatarChangeLink: { padding: 0, border: "none", background: "transparent", color: "var(--accent-solid)", cursor: "pointer", fontSize: 13, fontWeight: 600 },
  avatarRemove: { display: "block", padding: 0, border: "none", background: "transparent", color: "var(--text-muted)", cursor: "pointer", fontSize: 11, marginTop: 4, textDecoration: "underline" },
  formGrid: { display: "grid", gap: 12, marginBottom: 18 },
  field: { display: "flex", flexDirection: "column", gap: 6 },
  label: { fontSize: 12, color: "var(--text-muted)" },
  input: { padding: "9px 12px", borderRadius: 8, border: "1px solid var(--input-border)", background: "var(--input-bg)", color: "var(--text-primary)", outline: "none", fontSize: 13, width: "100%", boxSizing: "border-box" },
  saveBtn: { padding: "9px 20px", borderRadius: 8, border: "none", background: "var(--accent-solid)", color: "#fff", cursor: "pointer", fontWeight: 600, fontSize: 13 },
  themeRow: { display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 },
  themeBtn: { display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "14px 8px", borderRadius: 8, border: "1px solid var(--border-card)", background: "transparent", color: "var(--text-muted)", cursor: "pointer", gap: 4 },
  themeBtnActive: { border: "1px solid var(--accent-border)", background: "rgba(37,99,235,0.12)", color: "var(--text-primary)" },
  notifRow: { display: "flex", alignItems: "center", gap: 14, padding: "11px 0" },
  secRow: { display: "flex", alignItems: "center", gap: 14, padding: "12px 0" },
  secLabel: { fontSize: 13, fontWeight: 600, color: "var(--text-primary)", marginBottom: 3 },
  secDesc: { fontSize: 11, color: "var(--text-muted)", lineHeight: 1.5 },
  secBtn: {
    padding: "8px 14px", borderRadius: 7, border: "none",
    background: "var(--accent-solid)", color: "#fff",
    cursor: "pointer", fontWeight: 600, fontSize: 12, flexShrink: 0,
  },
  secBtnOutline: {
    padding: "8px 14px", borderRadius: 7,
    border: "1px solid var(--border-card)", background: "transparent",
    color: "var(--text-primary)", cursor: "pointer", fontWeight: 600, fontSize: 12, flexShrink: 0,
  },
  secBtnGroup: { display: "flex", gap: 8, flexShrink: 0 },
  secBtnDanger: {
    padding: "8px 14px", borderRadius: 7, border: "none",
    background: "var(--danger-solid, #dc2626)", color: "#fff",
    cursor: "pointer", fontWeight: 600, fontSize: 12, flexShrink: 0,
  },
};

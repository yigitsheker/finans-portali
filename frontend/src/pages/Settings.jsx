import { useEffect, useMemo, useRef, useState } from "react";
import { applyTheme } from "../theme";
import { useI18n } from "../contexts/I18nContext";
import { getNotificationPrefs, putNotificationPrefs } from "../api/userPrefsApi";

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
      putNotificationPrefs(keycloak, notifs).catch((e) => {
        // Dev-only: error message may contain server-echoed user data.
        if (import.meta.env.DEV) {
          console.warn("[Settings] Could not save notification prefs:", e?.message);
        }
      });
    }, 500);
    return () => clearTimeout(id);
  }, [notifs, keycloak]);

  const initials = useMemo(() => {
    const name = (firstName + " " + lastName).trim();
    return name ? name.slice(0, 2).toUpperCase() : "JD";
  }, [firstName, lastName]);

  const otpConfigured = Boolean(parsed?.otp_enabled || parsed?.totp);

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
      setTimeout(() => setSaveStatus(null), 2500);
    } catch (e) {
      console.error("Profil kaydedilemedi:", e);
      setSaveStatus("error");
      setSaveError(e?.message ?? "Bilinmeyen hata");
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
      <div style={s.grid}>
        {/* Profile */}
        <div style={s.card}>
          <div style={s.cardTitle}>{t("settings.profile")}</div>
          <div style={s.cardSub}>{t("settings.profileSub")}</div>
          <div style={s.avatarRow}>
            <div style={s.avatar}>{initials}</div>
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text-primary)" }}>{t("settings.changeAvatar")}</div>
              <div style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 2 }}>{t("settings.avatarHint")}</div>
            </div>
          </div>
          <div style={s.formGrid}>
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
                  {otpConfigured
                    ? t("settings.mfaActive")
                    : t("settings.mfaInactive")}
                </div>
              </div>
              <button style={s.secBtn} onClick={setup2FA}>
                {otpConfigured ? t("settings.reSetup") : t("settings.setup")}
              </button>
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
  grid: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, alignItems: "start" },
  card: { borderRadius: 10, border: "1px solid var(--border-card)", background: "var(--bg-card)", padding: "20px 22px" },
  cardTitle: { fontSize: 15, fontWeight: 600, color: "var(--text-primary)", marginBottom: 4 },
  cardSub: { fontSize: 12, color: "var(--text-muted)", marginBottom: 18 },
  avatarRow: { display: "flex", alignItems: "center", gap: 14, marginBottom: 20 },
  avatar: { width: 52, height: 52, borderRadius: 10, background: "linear-gradient(135deg, #1d4ed8, #2563eb)", display: "grid", placeItems: "center", fontSize: 16, fontWeight: 700, color: "#fff", flexShrink: 0 },
  formGrid: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 18 },
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
};

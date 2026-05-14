import { useMemo, useState } from "react";
import { applyTheme } from "../theme";

const NOTIF_ITEMS = [
  { key: "transactions", label: "Islem Bildirimleri",      desc: "Hesabinizda islem gerceklestiginde bildirim alin.", defaultOn: true },
  { key: "budget",       label: "Butce Uyarilari",         desc: "Butce limitinize yaklastiginizda uyari alin.",      defaultOn: true },
  { key: "investments",  label: "Yatirim Guncellemeleri",  desc: "Portfoy performansinin haftalik ozeti.",            defaultOn: false },
  { key: "marketing",    label: "Pazarlama Iletisimi",     desc: "Haberler, urun guncellemeleri ve teklifler.",       defaultOn: false },
  { key: "push",         label: "Anlik Bildirimler",       desc: "Onemli aktiviteler icin anlik bildirimler.",        defaultOn: true },
  { key: "security",     label: "Guvenlik Uyarilari",      desc: "Supheli aktiviteler icin SMS uyarilari.",           defaultOn: true },
];

export default function Settings({ keycloak, theme, onThemeChange }) {
  const parsed = keycloak.tokenParsed;
  const [firstName, setFirstName] = useState(parsed?.given_name ?? parsed?.name?.split(" ")[0] ?? "");
  const [lastName,  setLastName]  = useState(parsed?.family_name ?? parsed?.name?.split(" ").slice(1).join(" ") ?? "");
  const [email,     setEmail]     = useState(parsed?.email ?? "");
  const [phone,     setPhone]     = useState("");
  const [saved,     setSaved]     = useState(false);
  const [notifs, setNotifs] = useState(() => {
    const init = {};
    NOTIF_ITEMS.forEach((n) => { init[n.key] = n.defaultOn; });
    return init;
  });

  const initials = useMemo(() => {
    const name = (firstName + " " + lastName).trim();
    return name ? name.slice(0, 2).toUpperCase() : "JD";
  }, [firstName, lastName]);

  function handleSave() { setSaved(true); setTimeout(() => setSaved(false), 2500); }
  function toggleNotif(key) { setNotifs((prev) => ({ ...prev, [key]: !prev[key] })); }
  function handleTheme(t) { onThemeChange(t); applyTheme(t); }

  return (
    <div style={s.root}>
      <div style={s.grid}>
        {/* Profile */}
        <div style={s.card}>
          <div style={s.cardTitle}>Profil</div>
          <div style={s.cardSub}>Kisisel bilgilerinizi ve hesap detaylarinizi yonetin.</div>
          <div style={s.avatarRow}>
            <div style={s.avatar}>{initials}</div>
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text-primary)" }}>Avatar Degistir</div>
              <div style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 2 }}>JPG, GIF veya PNG. Maks. 2MB.</div>
            </div>
          </div>
          <div style={s.formGrid}>
            <div style={s.field}>
              <label style={s.label}>Ad</label>
              <input value={firstName} onChange={(e) => setFirstName(e.target.value)} style={s.input} placeholder="Adiniz" />
            </div>
            <div style={s.field}>
              <label style={s.label}>Soyad</label>
              <input value={lastName} onChange={(e) => setLastName(e.target.value)} style={s.input} placeholder="Soyadiniz" />
            </div>
            <div style={{ ...s.field, gridColumn: "span 2" }}>
              <label style={s.label}>E-posta Adresi</label>
              <input value={email} onChange={(e) => setEmail(e.target.value)} style={s.input} placeholder="ornek@email.com" type="email" />
            </div>
            <div style={{ ...s.field, gridColumn: "span 2" }}>
              <label style={s.label}>Telefon Numarasi</label>
              <input value={phone} onChange={(e) => setPhone(e.target.value)} style={s.input} placeholder="+90 555 000 0000" type="tel" />
            </div>
          </div>
          <button style={s.saveBtn} onClick={handleSave}>
            {saved ? "Kaydedildi" : "Degisiklikleri Kaydet"}
          </button>
        </div>

        {/* Right column */}
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {/* Appearance */}
          <div style={s.card}>
            <div style={s.cardTitle}>Gorunum</div>
            <div style={s.cardSub}>Uygulamanin gorunumunu ozellestirin.</div>
            <div style={s.themeRow}>
              {["light", "dark"].map((t) => {
                const labels = { light: "Acik", dark: "Koyu" };
                const icons  = { light: "☀️",   dark: "🌙" };
                return (
                  <button key={t} style={{ ...s.themeBtn, ...(theme === t ? s.themeBtnActive : {}) }} onClick={() => handleTheme(t)}>
                    <span style={{ fontSize: 22 }}>{icons[t]}</span>
                    <span style={{ fontSize: 12, marginTop: 4 }}>{labels[t]}</span>
                  </button>
                );
              })}
              <button style={{ ...s.themeBtn }}>
                <span style={{ fontSize: 22 }}>💻</span>
                <span style={{ fontSize: 12, marginTop: 4 }}>Sistem</span>
              </button>
            </div>
          </div>

          {/* Notifications */}
          <div style={s.card}>
            <div style={s.cardTitle}>Bildirimler</div>
            <div style={s.cardSub}>Bildirimleri ve uyarilari nasil alacaginizi yapilandirin.</div>
            {NOTIF_ITEMS.map((item, i) => (
              <div key={item.key} style={{ ...s.notifRow, borderBottom: i < NOTIF_ITEMS.length - 1 ? "1px solid var(--border-soft)" : "none" }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 13, fontWeight: 500, color: "var(--text-primary)", marginBottom: 2 }}>{item.label}</div>
                  <div style={{ fontSize: 11, color: "var(--text-muted)" }}>{item.desc}</div>
                </div>
                <Toggle on={notifs[item.key]} onToggle={() => toggleNotif(item.key)} />
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
};

import { useState, useEffect } from "react";

const REMEMBER_ME_KEY = "investhub.rememberMe";
const REMEMBER_EMAIL_KEY = "investhub.rememberEmail";

/**
 * Split-screen auth page.
 *  - Left half: brand panel with a stylised chart background, headline, and
 *    trust badges. Pure decoration; no inputs live here.
 *  - Right half: the actual login or register form. Keycloak still handles
 *    credential verification — we only collect the email for `loginHint` and
 *    redirect to Keycloak's own page on submit.
 */
export default function LoginPage({ keycloak }) {
    const [mode, setMode] = useState("login");
    const [email, setEmail] = useState(
        () => localStorage.getItem(REMEMBER_EMAIL_KEY) || ""
    );
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirm, setShowConfirm] = useState(false);
    const [rememberMe, setRememberMe] = useState(
        () => localStorage.getItem(REMEMBER_ME_KEY) === "true"
    );
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    const isLogin = mode === "login";

    useEffect(() => {
        localStorage.setItem(REMEMBER_ME_KEY, String(rememberMe));
    }, [rememberMe]);

    function switchMode(next) {
        setMode(next);
        setError(null);
        setPassword("");
        setConfirmPassword("");
    }

    function handleLogin(e) {
        e.preventDefault();
        if (!email.trim()) { setError("E-posta adresi gereklidir."); return; }
        if (!password) { setError("Şifre gereklidir."); return; }
        setLoading(true);
        setError(null);
        if (rememberMe) localStorage.setItem(REMEMBER_EMAIL_KEY, email.trim());
        else localStorage.removeItem(REMEMBER_EMAIL_KEY);
        keycloak.login({ loginHint: email.trim() });
    }

    function handleRegister(e) {
        e.preventDefault();
        if (!email.trim()) { setError("E-posta adresi gereklidir."); return; }
        if (!password) { setError("Şifre gereklidir."); return; }
        if (password.length < 8) { setError("Şifre en az 8 karakter olmalıdır."); return; }
        if (password !== confirmPassword) { setError("Şifreler eşleşmiyor."); return; }
        setLoading(true);
        setError(null);
        keycloak.register({ loginHint: email.trim() });
    }

    return (
        <div style={s.page} data-login-page>
            {/* ───────── Left brand panel ───────── */}
            <aside style={s.left} data-login-left>
                <ChartArt />

                <div style={s.leftHeader}>
                    <div style={s.logoBox}>
                        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5">
                            <polyline points="22 7 13.5 15.5 8.5 10.5 2 17" />
                            <polyline points="16 7 22 7 22 13" />
                        </svg>
                    </div>
                    <span style={s.logoText}>InvestHub</span>
                </div>

                <div style={s.leftBody}>
                    <h1 style={s.heroTitle}>
                        Hoş geldin.<br/>
                        <span style={s.heroAccent}>Kaldığın yerden</span><br/>
                        devam et.
                    </h1>
                    <p style={s.heroLead}>
                        Hesabına giriş yaparak portföyüne, fiyat alarmlarına ve
                        piyasa verilerine saniyeler içinde ulaş.
                    </p>

                    <div style={s.badges}>
                        <span style={s.badge}>SOC 2</span>
                        <span style={s.badgeSep}>·</span>
                        <span style={s.badge}>GDPR</span>
                        <span style={s.badgeSep}>·</span>
                        <span style={s.badge}>ISO 27001</span>
                    </div>
                </div>

                <div style={s.leftFooter}>
                    © 2026 InvestHub. Tüm hakları saklıdır.
                </div>
            </aside>

            {/* ───────── Right form panel ───────── */}
            <section style={s.right} data-login-right>
                <div style={s.card}>
                    <h2 style={s.cardTitle}>
                        {isLogin ? "Giriş Yap" : "Kayıt Ol"}
                    </h2>
                    <p style={s.cardSub}>
                        {isLogin
                            ? "Hesabına erişmek için bilgilerini gir."
                            : "Ücretsiz hesap oluştur, yatırımlarını takip et."}
                    </p>

                    <form onSubmit={isLogin ? handleLogin : handleRegister} style={s.form}>
                        <div style={s.fieldGroup}>
                            <label style={s.label}>
                                {isLogin ? "Kullanıcı adı veya e-posta" : "E-posta adresi"}
                            </label>
                            <div style={s.inputWrap}>
                                <svg style={s.inputIcon} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                    <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
                                    <polyline points="22,6 12,13 2,6"/>
                                </svg>
                                <input
                                    type={isLogin ? "text" : "email"}
                                    value={email}
                                    onChange={(e) => { setEmail(e.target.value); setError(null); }}
                                    placeholder={isLogin ? "john.doe" : "ornek@email.com"}
                                    style={s.input}
                                    autoFocus
                                    autoComplete={isLogin ? "username" : "email"}
                                    required
                                />
                            </div>
                        </div>

                        <div style={s.fieldGroup}>
                            <label style={s.label}>Şifre</label>
                            <div style={s.inputWrap}>
                                <svg style={s.inputIcon} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                                    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                                </svg>
                                <input
                                    type={showPassword ? "text" : "password"}
                                    value={password}
                                    onChange={(e) => { setPassword(e.target.value); setError(null); }}
                                    placeholder={isLogin ? "••••••••••" : "En az 8 karakter"}
                                    style={{ ...s.input, paddingRight: 44 }}
                                    autoComplete={isLogin ? "current-password" : "new-password"}
                                    required
                                />
                                <EyeButton open={showPassword} onClick={() => setShowPassword((v) => !v)} />
                            </div>
                            {!isLogin && password.length > 0 && <PasswordStrength password={password} />}
                        </div>

                        {!isLogin && (
                            <div style={s.fieldGroup}>
                                <label style={s.label}>Şifre tekrar</label>
                                <div style={s.inputWrap}>
                                    <svg style={s.inputIcon} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                        <path d="M9 12l2 2 4-4"/>
                                        <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                                        <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                                    </svg>
                                    <input
                                        type={showConfirm ? "text" : "password"}
                                        value={confirmPassword}
                                        onChange={(e) => { setConfirmPassword(e.target.value); setError(null); }}
                                        placeholder="Şifrenizi tekrar girin"
                                        style={{
                                            ...s.input,
                                            paddingRight: 44,
                                            borderColor: confirmPassword && confirmPassword !== password
                                                ? "rgba(239,68,68,0.55)"
                                                : confirmPassword && confirmPassword === password
                                                ? "rgba(34,197,94,0.55)"
                                                : s.input.border,
                                        }}
                                        autoComplete="new-password"
                                        required
                                    />
                                    <EyeButton open={showConfirm} onClick={() => setShowConfirm((v) => !v)} />
                                </div>
                            </div>
                        )}

                        {isLogin && (
                            <div style={s.rememberRow}>
                                <label style={s.rememberLabel}>
                                    <input
                                        type="checkbox"
                                        checked={rememberMe}
                                        onChange={(e) => setRememberMe(e.target.checked)}
                                        style={s.checkbox}
                                    />
                                    <span>Beni hatırla</span>
                                </label>
                                <button
                                    type="button"
                                    style={s.forgotBtn}
                                    onClick={() => keycloak.login({ action: "UPDATE_PASSWORD", loginHint: email })}
                                >
                                    Şifremi unuttum?
                                </button>
                            </div>
                        )}

                        {error && (
                            <div style={s.errorBox}>
                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                    <circle cx="12" cy="12" r="10"/>
                                    <line x1="12" y1="8" x2="12" y2="12"/>
                                    <line x1="12" y1="16" x2="12.01" y2="16"/>
                                </svg>
                                <span>{error}</span>
                            </div>
                        )}

                        <button
                            type="submit"
                            style={{ ...s.submitBtn, ...(loading ? s.submitBtnDisabled : {}) }}
                            disabled={loading}
                        >
                            {loading ? (
                                <span style={s.spinner} />
                            ) : (
                                <>
                                    {isLogin ? "Giriş Yap" : "Hesap Oluştur"}
                                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                                        <line x1="5" y1="12" x2="19" y2="12"/>
                                        <polyline points="12 5 19 12 12 19"/>
                                    </svg>
                                </>
                            )}
                        </button>
                    </form>

                    <p style={s.footerSwitch}>
                        {isLogin ? (
                            <>
                                Yeni kullanıcı mısınız?{" "}
                                <button style={s.linkBtn} onClick={() => switchMode("register")}>Kayıt Ol</button>
                            </>
                        ) : (
                            <>
                                Zaten hesabınız var mı?{" "}
                                <button style={s.linkBtn} onClick={() => switchMode("login")}>Giriş Yap</button>
                            </>
                        )}
                    </p>
                </div>
            </section>
        </div>
    );
}

/* ── Helper components ──────────────────────────────────────────────────── */

function EyeButton({ open, onClick }) {
    return (
        <button
            type="button"
            style={s.eyeBtn}
            onClick={onClick}
            tabIndex={-1}
            aria-label={open ? "Şifreyi gizle" : "Şifreyi göster"}
        >
            {open ? (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
                    <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
                    <line x1="1" y1="1" x2="23" y2="23" />
                </svg>
            ) : (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                    <circle cx="12" cy="12" r="3" />
                </svg>
            )}
        </button>
    );
}

function PasswordStrength({ password }) {
    const checks = [
        password.length >= 8,
        /[A-Z]/.test(password),
        /[0-9]/.test(password),
        /[^A-Za-z0-9]/.test(password),
    ];
    const score = checks.filter(Boolean).length;
    const labels = ["Çok zayıf", "Zayıf", "Orta", "Güçlü", "Çok güçlü"];
    const colors = ["#f85149", "#f97316", "#d29922", "#22c55e", "#22c55e"];
    return (
        <div style={{ marginTop: 8 }}>
            <div style={{ display: "flex", gap: 4, marginBottom: 4 }}>
                {[0, 1, 2, 3].map((i) => (
                    <div
                        key={i}
                        style={{
                            flex: 1,
                            height: 3,
                            borderRadius: 2,
                            background: i < score ? colors[score] : "rgba(255,255,255,0.10)",
                            transition: "background 0.3s",
                        }}
                    />
                ))}
            </div>
            <span style={{ fontSize: 11, color: colors[score] }}>{labels[score]}</span>
        </div>
    );
}

/**
 * Decorative chart line behind the left brand panel. Two layered polylines
 * (zigzag market line + glow underlay) create the "trading chart" feel of the
 * reference design without an actual data source.
 */
function ChartArt() {
    return (
        <svg
            style={s.chartArt}
            viewBox="0 0 1000 600"
            preserveAspectRatio="xMidYMid slice"
            aria-hidden="true"
        >
            <defs>
                <linearGradient id="lineGlow" x1="0" y1="0" x2="1" y2="0">
                    <stop offset="0%"  stopColor="#22c55e" stopOpacity="0.0" />
                    <stop offset="50%" stopColor="#22c55e" stopOpacity="0.85" />
                    <stop offset="100%" stopColor="#22c55e" stopOpacity="0.0" />
                </linearGradient>
                <radialGradient id="bgGlow" cx="40%" cy="60%" r="60%">
                    <stop offset="0%"  stopColor="#0c2a18" stopOpacity="1" />
                    <stop offset="100%" stopColor="#040a06" stopOpacity="1" />
                </radialGradient>
            </defs>
            <rect width="1000" height="600" fill="url(#bgGlow)" />
            <polyline
                points="0,420 80,400 150,440 230,360 320,390 410,300 500,330 590,250 690,290 800,180 900,210 1000,140"
                fill="none"
                stroke="url(#lineGlow)"
                strokeWidth="3"
                strokeLinecap="round"
                strokeLinejoin="round"
                opacity="0.85"
            />
            <polyline
                points="0,420 80,400 150,440 230,360 320,390 410,300 500,330 590,250 690,290 800,180 900,210 1000,140"
                fill="none"
                stroke="#22c55e"
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeLinejoin="round"
                opacity="0.55"
            />
        </svg>
    );
}

/* ── Styles ─────────────────────────────────────────────────────────────── */

const ACCENT = "#22c55e";
const ACCENT_DARK = "#15803d";
const TEXT = "#e8f5e8";
const MUTED = "#6b8f6b";
const SURFACE = "#0e1410";
const BORDER = "rgba(34,197,94,0.16)";

const s = {
    page: {
        minHeight: "100vh",
        display: "grid",
        gridTemplateColumns: "1.05fr 0.95fr",
        background: "#040a06",
        color: TEXT,
        fontFamily: "inherit",
    },
    /* Left brand panel */
    left: {
        position: "relative",
        padding: "44px 56px",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        overflow: "hidden",
        minHeight: "100vh",
    },
    chartArt: {
        position: "absolute",
        inset: 0,
        width: "100%",
        height: "100%",
        zIndex: 0,
        pointerEvents: "none",
    },
    leftHeader: {
        position: "relative",
        zIndex: 1,
        display: "flex",
        alignItems: "center",
        gap: 12,
    },
    logoBox: {
        width: 42,
        height: 42,
        borderRadius: 10,
        background: `linear-gradient(135deg, ${ACCENT_DARK}, ${ACCENT})`,
        display: "grid",
        placeItems: "center",
        flexShrink: 0,
        boxShadow: "0 6px 24px rgba(34,197,94,0.35)",
    },
    logoText: {
        fontSize: 18,
        fontWeight: 800,
        color: TEXT,
        letterSpacing: "-0.3px",
    },
    leftBody: {
        position: "relative",
        zIndex: 1,
        maxWidth: 520,
    },
    heroTitle: {
        fontSize: "clamp(34px, 4vw, 52px)",
        fontWeight: 800,
        lineHeight: 1.08,
        letterSpacing: "-0.025em",
        margin: 0,
        color: "#fff",
    },
    heroAccent: {
        color: ACCENT,
    },
    heroLead: {
        marginTop: 22,
        fontSize: 15,
        color: "#a8c5a8",
        lineHeight: 1.6,
        maxWidth: 460,
    },
    badges: {
        marginTop: 36,
        display: "flex",
        alignItems: "center",
        gap: 10,
        fontSize: 12,
        fontWeight: 600,
        color: "#5f7a5f",
        letterSpacing: "0.08em",
    },
    badge: { color: "#7aa37a" },
    badgeSep: { color: "#3a5a3a" },
    leftFooter: {
        position: "relative",
        zIndex: 1,
        fontSize: 12,
        color: "#3f5a3f",
    },

    /* Right form panel */
    right: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "44px 40px",
        minHeight: "100vh",
        background: "#080d09",
    },
    card: {
        width: "100%",
        maxWidth: 440,
        background: SURFACE,
        border: `1px solid ${BORDER}`,
        borderRadius: 16,
        padding: "36px 32px",
        boxShadow: "0 24px 60px rgba(0,0,0,0.45)",
    },
    cardTitle: {
        margin: 0,
        fontSize: 26,
        fontWeight: 800,
        color: "#fff",
        letterSpacing: "-0.4px",
    },
    cardSub: {
        margin: "8px 0 28px",
        fontSize: 13.5,
        color: MUTED,
    },
    form: {
        display: "flex",
        flexDirection: "column",
        gap: 16,
    },
    fieldGroup: {
        display: "flex",
        flexDirection: "column",
        gap: 6,
    },
    label: {
        fontSize: 12.5,
        fontWeight: 600,
        color: TEXT,
        letterSpacing: "0.01em",
    },
    inputWrap: {
        position: "relative",
        display: "flex",
        alignItems: "center",
    },
    inputIcon: {
        position: "absolute",
        left: 12,
        color: MUTED,
        pointerEvents: "none",
        flexShrink: 0,
    },
    input: {
        width: "100%",
        padding: "12px 14px 12px 38px",
        borderRadius: 10,
        border: `1px solid ${BORDER}`,
        background: "#0a100c",
        color: TEXT,
        fontSize: 14,
        outline: "none",
        transition: "border-color 0.2s",
        boxSizing: "border-box",
    },
    eyeBtn: {
        position: "absolute",
        right: 10,
        background: "none",
        border: "none",
        color: MUTED,
        cursor: "pointer",
        padding: 4,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        borderRadius: 4,
    },
    rememberRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        marginTop: -2,
    },
    rememberLabel: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        fontSize: 13,
        color: "#a8c5a8",
        cursor: "pointer",
        userSelect: "none",
    },
    checkbox: {
        width: 15,
        height: 15,
        accentColor: ACCENT,
        cursor: "pointer",
    },
    forgotBtn: {
        background: "none",
        border: "none",
        color: ACCENT,
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 500,
        padding: 0,
    },
    errorBox: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        padding: "10px 12px",
        borderRadius: 8,
        background: "rgba(239,68,68,0.10)",
        border: "1px solid rgba(239,68,68,0.35)",
        color: "#fca5a5",
        fontSize: 13,
    },
    submitBtn: {
        marginTop: 6,
        padding: "13px",
        borderRadius: 10,
        border: "none",
        background: `linear-gradient(135deg, ${ACCENT_DARK}, ${ACCENT})`,
        color: "#04150a",
        fontSize: 14.5,
        fontWeight: 800,
        cursor: "pointer",
        transition: "all 0.2s",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: 10,
        boxShadow: "0 8px 22px rgba(34,197,94,0.30)",
        letterSpacing: "0.2px",
    },
    submitBtnDisabled: {
        opacity: 0.6,
        cursor: "not-allowed",
    },
    spinner: {
        width: 18,
        height: 18,
        border: "2px solid rgba(0,0,0,0.25)",
        borderTop: "2px solid #04150a",
        borderRadius: "50%",
        animation: "spin 0.7s linear infinite",
        display: "inline-block",
    },
    footerSwitch: {
        textAlign: "center",
        fontSize: 13.5,
        color: MUTED,
        margin: "22px 0 0",
    },
    linkBtn: {
        background: "none",
        border: "none",
        color: ACCENT,
        cursor: "pointer",
        fontSize: 13.5,
        fontWeight: 700,
        padding: 0,
    },
};

/* Mobile: stack panels vertically when the screen is too narrow to host both. */
const mediaCss = `
@media (max-width: 900px) {
    [data-login-page] { grid-template-columns: 1fr !important; }
    [data-login-left] { min-height: auto !important; padding: 28px 24px !important; }
    [data-login-right] { min-height: auto !important; padding: 28px 20px !important; }
}
`;

// Inject responsive overrides + spin keyframes once.
if (typeof document !== "undefined" && !document.getElementById("login-page-css")) {
    const tag = document.createElement("style");
    tag.id = "login-page-css";
    tag.textContent = `
        @keyframes spin { to { transform: rotate(360deg); } }
        ${mediaCss}
    `;
    document.head.appendChild(tag);
}

import { useState, useEffect } from "react";

const REMEMBER_ME_KEY = "investhub.rememberMe";
const REMEMBER_EMAIL_KEY = "investhub.rememberEmail";

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

    // ── Login ──────────────────────────────────────────────────────────────
    // Keycloak handles actual credential verification on its own page.
    // We collect email here for UX (loginHint pre-fills Keycloak's form),
    // then redirect to Keycloak's login endpoint.
    function handleLogin(e) {
        e.preventDefault();
        if (!email.trim()) { setError("E-posta adresi gereklidir."); return; }
        if (!password) { setError("Şifre gereklidir."); return; }
        setLoading(true);
        setError(null);

        if (rememberMe) {
            localStorage.setItem(REMEMBER_EMAIL_KEY, email.trim());
        } else {
            localStorage.removeItem(REMEMBER_EMAIL_KEY);
        }

        keycloak.login({ loginHint: email.trim() });
    }

    // ── Register ───────────────────────────────────────────────────────────
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
        <div style={s.root}>
            {/* Animated background grid */}
            <div style={s.grid} />

            {/* Glow blobs */}
            <div style={s.blob1} />
            <div style={s.blob2} />

            <div style={s.card}>
                {/* Logo */}
                <div style={s.logoRow}>
                    <div style={s.logoBox}>
                        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5">
                            <polyline points="22 7 13.5 15.5 8.5 10.5 2 17" />
                            <polyline points="16 7 22 7 22 13" />
                        </svg>
                    </div>
                    <div>
                        <div style={s.appName}>InvestHub</div>
                        <div style={s.appSub}>Investment Portal</div>
                    </div>
                </div>

                {/* Title */}
                <div style={s.titleBlock}>
                    <h1 style={s.title}>
                        {isLogin ? "Hesabınıza giriş yapın" : "Yeni hesap oluşturun"}
                    </h1>
                    <p style={s.subtitle}>
                        {isLogin
                            ? "Portföyünüzü yönetmek için giriş yapın"
                            : "Ücretsiz hesap oluşturun, yatırımlarınızı takip edin"}
                    </p>
                </div>

                {/* Tab switcher */}
                <div style={s.tabs}>
                    <button
                        style={{ ...s.tab, ...(isLogin ? s.tabActive : {}) }}
                        onClick={() => switchMode("login")}
                        type="button"
                    >
                        Giriş Yap
                    </button>
                    <button
                        style={{ ...s.tab, ...(!isLogin ? s.tabActive : {}) }}
                        onClick={() => switchMode("register")}
                        type="button"
                    >
                        Kayıt Ol
                    </button>
                </div>

                {/* Form */}
                <form onSubmit={isLogin ? handleLogin : handleRegister} style={s.form}>

                    {/* Email */}
                    <div style={s.fieldGroup}>
                        <label style={s.label}>E-posta adresi</label>
                        <div style={s.inputWrap}>
                            <svg style={s.inputIcon} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
                                <polyline points="22,6 12,13 2,6" />
                            </svg>
                            <input
                                type="email"
                                value={email}
                                onChange={(e) => { setEmail(e.target.value); setError(null); }}
                                placeholder="ornek@email.com"
                                style={s.input}
                                autoFocus
                                autoComplete="email"
                                required
                            />
                        </div>
                    </div>

                    {/* Password */}
                    <div style={s.fieldGroup}>
                        <label style={s.label}>Şifre</label>
                        <div style={s.inputWrap}>
                            <svg style={s.inputIcon} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                            </svg>
                            <input
                                type={showPassword ? "text" : "password"}
                                value={password}
                                onChange={(e) => { setPassword(e.target.value); setError(null); }}
                                placeholder={isLogin ? "Şifrenizi girin" : "En az 8 karakter"}
                                style={{ ...s.input, paddingRight: 44 }}
                                autoComplete={isLogin ? "current-password" : "new-password"}
                                required
                            />
                            <button
                                type="button"
                                style={s.eyeBtn}
                                onClick={() => setShowPassword((v) => !v)}
                                tabIndex={-1}
                                aria-label={showPassword ? "Şifreyi gizle" : "Şifreyi göster"}
                            >
                                {showPassword ? (
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
                        </div>
                        {/* Password strength bar — only on register */}
                        {!isLogin && password.length > 0 && (
                            <PasswordStrength password={password} />
                        )}
                    </div>

                    {/* Confirm Password — register only */}
                    {!isLogin && (
                        <div style={s.fieldGroup}>
                            <label style={s.label}>Şifre tekrar</label>
                            <div style={s.inputWrap}>
                                <svg style={s.inputIcon} width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                    <path d="M9 12l2 2 4-4" />
                                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                                    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
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
                                            ? "rgba(239,68,68,0.6)"
                                            : confirmPassword && confirmPassword === password
                                            ? "rgba(34,197,94,0.6)"
                                            : undefined,
                                    }}
                                    autoComplete="new-password"
                                    required
                                />
                                <button
                                    type="button"
                                    style={s.eyeBtn}
                                    onClick={() => setShowConfirm((v) => !v)}
                                    tabIndex={-1}
                                >
                                    {showConfirm ? (
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
                            </div>
                        </div>
                    )}

                    {/* Remember me + Forgot password — login only */}
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
                                Şifremi unuttum
                            </button>
                        </div>
                    )}

                    {/* Error */}
                    {error && (
                        <div style={s.errorBox}>
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <circle cx="12" cy="12" r="10" />
                                <line x1="12" y1="8" x2="12" y2="12" />
                                <line x1="12" y1="16" x2="12.01" y2="16" />
                            </svg>
                            <span>{error}</span>
                        </div>
                    )}

                    {/* Submit */}
                    <button
                        type="submit"
                        style={{ ...s.submitBtn, ...(loading ? s.submitBtnDisabled : {}) }}
                        disabled={loading}
                    >
                        {loading ? (
                            <span style={s.spinner} />
                        ) : isLogin ? (
                            <>
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                                    <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
                                    <polyline points="10 17 15 12 10 7" />
                                    <line x1="15" y1="12" x2="3" y2="12" />
                                </svg>
                                Giriş Yap
                            </>
                        ) : (
                            <>
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                                    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                                    <circle cx="9" cy="7" r="4" />
                                    <line x1="19" y1="8" x2="19" y2="14" />
                                    <line x1="22" y1="11" x2="16" y2="11" />
                                </svg>
                                Hesap Oluştur
                            </>
                        )}
                    </button>
                </form>

                {/* Footer link */}
                <p style={s.footerText}>
                    {isLogin ? (
                        <>
                            Hesabınız yok mu?{" "}
                            <button style={s.linkBtn} onClick={() => switchMode("register")}>
                                Kayıt olun
                            </button>
                        </>
                    ) : (
                        <>
                            Zaten hesabınız var mı?{" "}
                            <button style={s.linkBtn} onClick={() => switchMode("login")}>
                                Giriş yapın
                            </button>
                        </>
                    )}
                </p>
            </div>

            <p style={s.bottomNote}>© 2026 InvestHub · Tüm hakları saklıdır</p>
        </div>
    );
}

// ── Password strength indicator ────────────────────────────────────────────
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
                            background: i < score ? colors[score] : "rgba(255,255,255,0.1)",
                            transition: "background 0.3s",
                        }}
                    />
                ))}
            </div>
            <span style={{ fontSize: 11, color: colors[score] }}>{labels[score]}</span>
        </div>
    );
}

// ── Styles ─────────────────────────────────────────────────────────────────
const s = {
    root: {
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        background: "#080b08",
        padding: "24px 16px",
        position: "relative",
        overflow: "hidden",
    },
    grid: {
        position: "absolute",
        inset: 0,
        backgroundImage: `
            linear-gradient(rgba(34,197,94,0.04) 1px, transparent 1px),
            linear-gradient(90deg, rgba(34,197,94,0.04) 1px, transparent 1px)
        `,
        backgroundSize: "40px 40px",
        pointerEvents: "none",
    },
    blob1: {
        position: "absolute",
        top: -150,
        left: -150,
        width: 500,
        height: 500,
        borderRadius: "50%",
        background: "radial-gradient(circle, rgba(34,197,94,0.12) 0%, transparent 65%)",
        pointerEvents: "none",
    },
    blob2: {
        position: "absolute",
        bottom: -120,
        right: -120,
        width: 400,
        height: 400,
        borderRadius: "50%",
        background: "radial-gradient(circle, rgba(34,197,94,0.08) 0%, transparent 65%)",
        pointerEvents: "none",
    },
    card: {
        width: "100%",
        maxWidth: 420,
        background: "#0f1410",
        border: "1px solid rgba(34,197,94,0.18)",
        borderRadius: 16,
        padding: "32px 28px",
        boxShadow: "0 0 0 1px rgba(34,197,94,0.06), 0 20px 60px rgba(0,0,0,0.6)",
        position: "relative",
        zIndex: 1,
    },
    logoRow: {
        display: "flex",
        alignItems: "center",
        gap: 12,
        marginBottom: 28,
    },
    logoBox: {
        width: 44,
        height: 44,
        borderRadius: 10,
        background: "linear-gradient(135deg, #15803d, #22c55e)",
        display: "grid",
        placeItems: "center",
        flexShrink: 0,
        boxShadow: "0 4px 16px rgba(34,197,94,0.35)",
    },
    appName: {
        fontWeight: 800,
        fontSize: 17,
        color: "#e8f5e8",
        letterSpacing: "-0.3px",
    },
    appSub: {
        fontSize: 11,
        color: "#6b8f6b",
        marginTop: 1,
    },
    titleBlock: {
        marginBottom: 24,
    },
    title: {
        margin: "0 0 6px 0",
        fontSize: 22,
        fontWeight: 700,
        color: "#e8f5e8",
        letterSpacing: "-0.4px",
    },
    subtitle: {
        margin: 0,
        fontSize: 13,
        color: "#6b8f6b",
        lineHeight: 1.5,
    },
    tabs: {
        display: "flex",
        background: "#141a14",
        borderRadius: 10,
        padding: 4,
        marginBottom: 24,
        gap: 4,
        border: "1px solid rgba(34,197,94,0.10)",
    },
    tab: {
        flex: 1,
        padding: "8px 0",
        border: "none",
        background: "transparent",
        color: "#6b8f6b",
        fontSize: 13,
        fontWeight: 600,
        cursor: "pointer",
        borderRadius: 7,
        transition: "all 0.2s",
    },
    tabActive: {
        background: "rgba(34,197,94,0.15)",
        color: "#22c55e",
        boxShadow: "0 1px 4px rgba(0,0,0,0.3)",
    },
    form: {
        display: "flex",
        flexDirection: "column",
        gap: 14,
    },
    fieldGroup: {
        display: "flex",
        flexDirection: "column",
        gap: 6,
    },
    label: {
        fontSize: 11,
        fontWeight: 600,
        color: "#6b8f6b",
        letterSpacing: "0.5px",
        textTransform: "uppercase",
    },
    inputWrap: {
        position: "relative",
        display: "flex",
        alignItems: "center",
    },
    inputIcon: {
        position: "absolute",
        left: 12,
        color: "#6b8f6b",
        pointerEvents: "none",
        flexShrink: 0,
    },
    input: {
        width: "100%",
        padding: "11px 14px 11px 38px",
        borderRadius: 9,
        border: "1px solid rgba(34,197,94,0.20)",
        background: "#111611",
        color: "#e8f5e8",
        fontSize: 14,
        outline: "none",
        transition: "border-color 0.2s, box-shadow 0.2s",
        boxSizing: "border-box",
    },
    eyeBtn: {
        position: "absolute",
        right: 10,
        background: "none",
        border: "none",
        color: "#6b8f6b",
        cursor: "pointer",
        padding: 4,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        borderRadius: 4,
        transition: "color 0.15s",
    },
    forgotBtn: {
        background: "none",
        border: "none",
        color: "#22c55e",
        cursor: "pointer",
        fontSize: 12,
        fontWeight: 500,
        padding: 0,
        opacity: 0.8,
    },
    rememberRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        marginTop: -4,
        gap: 12,
    },
    rememberLabel: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        fontSize: 12,
        color: "#a8c5a8",
        cursor: "pointer",
        userSelect: "none",
    },
    checkbox: {
        width: 14,
        height: 14,
        accentColor: "#22c55e",
        cursor: "pointer",
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
        padding: "12px",
        borderRadius: 9,
        border: "none",
        background: "linear-gradient(135deg, #15803d, #22c55e)",
        color: "#fff",
        fontSize: 14,
        fontWeight: 700,
        cursor: "pointer",
        transition: "all 0.2s",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: 8,
        boxShadow: "0 4px 16px rgba(34,197,94,0.30)",
        marginTop: 4,
        letterSpacing: "0.2px",
    },
    submitBtnDisabled: {
        opacity: 0.55,
        cursor: "not-allowed",
    },
    spinner: {
        width: 18,
        height: 18,
        border: "2px solid rgba(255,255,255,0.3)",
        borderTop: "2px solid #fff",
        borderRadius: "50%",
        animation: "spin 0.7s linear infinite",
        display: "inline-block",
    },
    footerText: {
        textAlign: "center",
        fontSize: 13,
        color: "#6b8f6b",
        marginTop: 18,
        marginBottom: 0,
    },
    linkBtn: {
        background: "none",
        border: "none",
        color: "#22c55e",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
        padding: 0,
        textDecoration: "underline",
        textUnderlineOffset: 2,
    },
    bottomNote: {
        marginTop: 20,
        fontSize: 11,
        color: "#3a5a3a",
        textAlign: "center",
        position: "relative",
        zIndex: 1,
    },
};

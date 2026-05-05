import { useState } from "react";
import type Keycloak from "keycloak-js";

type Props = { keycloak: Keycloak };
type Mode = "login" | "register";

export default function LoginPage({ keycloak }: Props) {
    const [mode, setMode] = useState<Mode>("login");
    const [email, setEmail] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // ── Login ──────────────────────────────────────────────────────────────
    function handleLogin(e: React.FormEvent) {
        e.preventDefault();
        if (!email.trim()) {
            setError("E-posta adresi gereklidir.");
            return;
        }
        setLoading(true);
        setError(null);
        // Keycloak login flow — loginHint pre-fills the email field on Keycloak's page
        keycloak.login({ loginHint: email.trim() });
    }

    // ── Register ───────────────────────────────────────────────────────────
    function handleRegister(e: React.FormEvent) {
        e.preventDefault();
        if (!email.trim()) {
            setError("E-posta adresi gereklidir.");
            return;
        }
        setLoading(true);
        setError(null);
        // Keycloak register flow — loginHint pre-fills the email field
        keycloak.register({ loginHint: email.trim() });
    }

    // ── "Kayıt ol" redirect from login ────────────────────────────────────
    function goToRegisterWithEmail() {
        setMode("register");
        setError(null);
    }

    const isLogin = mode === "login";

    return (
        <div style={s.root}>
            {/* Background gradient blobs */}
            <div style={s.blob1} />
            <div style={s.blob2} />

            <div style={s.card}>
                {/* Logo */}
                <div style={s.logoRow}>
                    <div style={s.logoBox}>
                        <span style={{ fontSize: 18, fontWeight: 900, color: "#fff" }}>IH</span>
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
                            : "Ücretsiz hesap oluşturun ve yatırımlarınızı takip edin"}
                    </p>
                </div>

                {/* Tab switcher */}
                <div style={s.tabs}>
                    <button
                        style={{ ...s.tab, ...(isLogin ? s.tabActive : {}) }}
                        onClick={() => { setMode("login"); setError(null); }}
                    >
                        Giriş Yap
                    </button>
                    <button
                        style={{ ...s.tab, ...(!isLogin ? s.tabActive : {}) }}
                        onClick={() => { setMode("register"); setError(null); }}
                    >
                        Kayıt Ol
                    </button>
                </div>

                {/* Form */}
                <form onSubmit={isLogin ? handleLogin : handleRegister} style={s.form}>
                    <div style={s.fieldGroup}>
                        <label style={s.label}>E-posta adresi</label>
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

                    {error && (
                        <div style={s.errorBox}>
                            <span style={{ fontSize: 14 }}>⚠️</span>
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
                        ) : isLogin ? (
                            "Giriş Yap →"
                        ) : (
                            "Kayıt Ol →"
                        )}
                    </button>
                </form>

                {/* Footer link */}
                <div style={s.footerText}>
                    {isLogin ? (
                        <>
                            Hesabınız yok mu?{" "}
                            <button style={s.linkBtn} onClick={goToRegisterWithEmail}>
                                Kayıt olun
                            </button>
                        </>
                    ) : (
                        <>
                            Zaten hesabınız var mı?{" "}
                            <button style={s.linkBtn} onClick={() => { setMode("login"); setError(null); }}>
                                Giriş yapın
                            </button>
                        </>
                    )}
                </div>

                {/* Divider */}
                <div style={s.divider}>
                    <span style={s.dividerLine} />
                    <span style={s.dividerText}>veya</span>
                    <span style={s.dividerLine} />
                </div>

                {/* SSO button */}
                <button
                    style={s.ssoBtn}
                    onClick={() => keycloak.login()}
                    type="button"
                >
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                        <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                    </svg>
                    Keycloak SSO ile devam et
                </button>
            </div>

            {/* Bottom note */}
            <p style={s.bottomNote}>
                © 2026 InvestHub · Tüm hakları saklıdır
            </p>
        </div>
    );
}

// ── Styles ─────────────────────────────────────────────────────────────────
const s: Record<string, React.CSSProperties> = {
    root: {
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        background: "var(--bg-page)",
        padding: "24px 16px",
        position: "relative",
        overflow: "hidden",
    },
    // Decorative background blobs
    blob1: {
        position: "absolute",
        top: -120,
        left: -120,
        width: 400,
        height: 400,
        borderRadius: "50%",
        background: "radial-gradient(circle, rgba(37,99,235,0.18) 0%, transparent 70%)",
        pointerEvents: "none",
    },
    blob2: {
        position: "absolute",
        bottom: -100,
        right: -100,
        width: 350,
        height: 350,
        borderRadius: "50%",
        background: "radial-gradient(circle, rgba(63,185,80,0.12) 0%, transparent 70%)",
        pointerEvents: "none",
    },
    card: {
        width: "100%",
        maxWidth: 420,
        background: "var(--bg-panel)",
        border: "1px solid var(--border-card)",
        borderRadius: 16,
        padding: "32px 28px",
        boxShadow: "var(--shadow)",
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
        width: 42,
        height: 42,
        borderRadius: 10,
        background: "linear-gradient(135deg, #1d4ed8, #2563eb)",
        display: "grid",
        placeItems: "center",
        flexShrink: 0,
        boxShadow: "0 4px 12px rgba(37,99,235,0.35)",
    },
    appName: {
        fontWeight: 800,
        fontSize: 16,
        color: "var(--text-primary)",
        letterSpacing: "-0.3px",
    },
    appSub: {
        fontSize: 11,
        color: "var(--text-muted)",
        marginTop: 1,
    },
    titleBlock: {
        marginBottom: 24,
    },
    title: {
        margin: "0 0 6px 0",
        fontSize: 22,
        fontWeight: 700,
        color: "var(--text-primary)",
        letterSpacing: "-0.4px",
    },
    subtitle: {
        margin: 0,
        fontSize: 13,
        color: "var(--text-muted)",
        lineHeight: 1.5,
    },
    tabs: {
        display: "flex",
        background: "var(--bg-card)",
        borderRadius: 10,
        padding: 4,
        marginBottom: 24,
        gap: 4,
    },
    tab: {
        flex: 1,
        padding: "8px 0",
        border: "none",
        background: "transparent",
        color: "var(--text-muted)",
        fontSize: 13,
        fontWeight: 600,
        cursor: "pointer",
        borderRadius: 7,
        transition: "all 0.2s",
    },
    tabActive: {
        background: "var(--bg-panel)",
        color: "var(--text-primary)",
        boxShadow: "0 1px 4px rgba(0,0,0,0.25)",
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
        fontSize: 12,
        fontWeight: 600,
        color: "var(--text-muted)",
        letterSpacing: "0.3px",
        textTransform: "uppercase",
    },
    input: {
        padding: "11px 14px",
        borderRadius: 9,
        border: "1px solid var(--input-border)",
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        fontSize: 14,
        outline: "none",
        transition: "border-color 0.2s",
        width: "100%",
        boxSizing: "border-box",
    },
    errorBox: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        padding: "10px 12px",
        borderRadius: 8,
        background: "var(--danger-bg)",
        border: "1px solid var(--danger-border)",
        color: "var(--danger-text)",
        fontSize: 13,
    },
    submitBtn: {
        padding: "12px",
        borderRadius: 9,
        border: "none",
        background: "var(--accent-solid)",
        color: "#fff",
        fontSize: 14,
        fontWeight: 700,
        cursor: "pointer",
        transition: "all 0.2s",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: 8,
        boxShadow: "0 4px 14px rgba(37,99,235,0.35)",
        marginTop: 4,
    },
    submitBtnDisabled: {
        opacity: 0.6,
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
        color: "var(--text-muted)",
        marginTop: 16,
    },
    linkBtn: {
        background: "none",
        border: "none",
        color: "var(--accent-solid)",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
        padding: 0,
        textDecoration: "underline",
        textUnderlineOffset: 2,
    },
    divider: {
        display: "flex",
        alignItems: "center",
        gap: 10,
        margin: "20px 0",
    },
    dividerLine: {
        flex: 1,
        height: 1,
        background: "var(--border)",
    },
    dividerText: {
        fontSize: 12,
        color: "var(--text-muted)",
        flexShrink: 0,
    },
    ssoBtn: {
        width: "100%",
        padding: "11px",
        borderRadius: 9,
        border: "1px solid var(--border-card)",
        background: "var(--bg-card)",
        color: "var(--text-primary)",
        fontSize: 13,
        fontWeight: 600,
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: 8,
        transition: "all 0.2s",
        boxSizing: "border-box",
    },
    bottomNote: {
        marginTop: 24,
        fontSize: 11,
        color: "var(--text-muted)",
        textAlign: "center",
        position: "relative",
        zIndex: 1,
    },
};

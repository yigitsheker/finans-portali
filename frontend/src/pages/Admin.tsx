import type Keycloak from "keycloak-js";
import { useState } from "react";
import { isAdmin } from "../utils/roleUtils";

type Props = {
    keycloak: Keycloak;
};

export default function Admin({ keycloak }: Props) {
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    // Check if user is admin
    if (!isAdmin(keycloak)) {
        return (
            <div style={s.container}>
                <div style={s.card}>
                    <h2 style={s.title}>⛔ Erişim Reddedildi</h2>
                    <p style={s.text}>
                        Bu sayfaya erişim yetkiniz bulunmamaktadır.
                        Sadece yöneticiler bu sayfayı görüntüleyebilir.
                    </p>
                </div>
            </div>
        );
    }

    const callAdminEndpoint = async (endpoint: string, method: string = "POST") => {
        setLoading(true);
        setMessage(null);
        setError(null);

        try {
            const response = await fetch(`http://localhost:8080/api/v1/admin/${endpoint}`, {
                method,
                headers: {
                    "Authorization": `Bearer ${keycloak.token}`,
                    "Content-Type": "application/json",
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const text = await response.text();
            setMessage(text || "İşlem başarılı");
        } catch (err: any) {
            setError(err.message || "Bir hata oluştu");
        } finally {
            setLoading(false);
        }
    };

    const getAdminInfo = async () => {
        setLoading(true);
        setMessage(null);
        setError(null);

        try {
            const response = await fetch("http://localhost:8080/api/v1/admin/me", {
                headers: {
                    "Authorization": `Bearer ${keycloak.token}`,
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const data = await response.json();
            setMessage(JSON.stringify(data, null, 2));
        } catch (err: any) {
            setError(err.message || "Bir hata oluştu");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={s.container}>
            <div style={s.header}>
                <h1 style={s.mainTitle}>🔧 Yönetim Paneli</h1>
                <p style={s.subtitle}>
                    Sistem yönetimi ve veri yönetimi işlemleri
                </p>
            </div>

            {/* Admin Info Card */}
            <div style={s.card}>
                <h2 style={s.cardTitle}>👤 Yönetici Bilgileri</h2>
                <p style={s.cardText}>
                    JWT token'ınızdaki bilgileri görüntüleyin
                </p>
                <button
                    style={s.button}
                    onClick={getAdminInfo}
                    disabled={loading}
                >
                    {loading ? "Yükleniyor..." : "Bilgilerimi Göster"}
                </button>
            </div>

            {/* Data Management Cards */}
            <div style={s.grid}>
                <div style={s.card}>
                    <h2 style={s.cardTitle}>📈 Piyasa Verileri</h2>
                    <p style={s.cardText}>
                        Tüm piyasa verilerini sıfırlayın ve yeniden yükleyin
                    </p>
                    <button
                        style={s.button}
                        onClick={() => callAdminEndpoint("reset-market")}
                        disabled={loading}
                    >
                        {loading ? "İşleniyor..." : "Piyasa Verilerini Sıfırla"}
                    </button>
                </div>

                <div style={s.card}>
                    <h2 style={s.cardTitle}>💰 Fiyat Güncelleme</h2>
                    <p style={s.cardText}>
                        Tüm enstrümanların fiyatlarını hemen güncelleyin
                    </p>
                    <button
                        style={s.button}
                        onClick={() => callAdminEndpoint("refresh-prices")}
                        disabled={loading}
                    >
                        {loading ? "İşleniyor..." : "Fiyatları Güncelle"}
                    </button>
                </div>

                <div style={s.card}>
                    <h2 style={s.cardTitle}>📰 Haber Verileri</h2>
                    <p style={s.cardText}>
                        Tüm haberleri sıfırlayın ve RSS'den yeniden çekin
                    </p>
                    <button
                        style={s.button}
                        onClick={() => callAdminEndpoint("reset-news")}
                        disabled={loading}
                    >
                        {loading ? "İşleniyor..." : "Haberleri Sıfırla"}
                    </button>
                </div>
            </div>

            {/* Message Display */}
            {message && (
                <div style={s.messageBox}>
                    <h3 style={s.messageTitle}>✅ Başarılı</h3>
                    <pre style={s.messageContent}>{message}</pre>
                </div>
            )}

            {error && (
                <div style={s.errorBox}>
                    <h3 style={s.errorTitle}>❌ Hata</h3>
                    <p style={s.errorContent}>{error}</p>
                </div>
            )}

            {/* Info Box */}
            <div style={s.infoBox}>
                <h3 style={s.infoTitle}>ℹ️ Bilgi</h3>
                <p style={s.infoText}>
                    Bu sayfa sadece ADMIN rolüne sahip kullanıcılar tarafından görüntülenebilir.
                    Tüm işlemler backend'de yetkilendirme kontrolünden geçer.
                </p>
                <p style={s.infoText}>
                    <strong>Test Kullanıcıları:</strong>
                </p>
                <ul style={s.infoList}>
                    <li>Admin: admin.user / admin123</li>
                    <li>Normal Kullanıcı: john.doe / password123</li>
                </ul>
            </div>
        </div>
    );
}

const s: Record<string, React.CSSProperties> = {
    container: {
        padding: "24px",
        maxWidth: "1200px",
        margin: "0 auto",
    },
    header: {
        marginBottom: "32px",
    },
    mainTitle: {
        fontSize: "28px",
        fontWeight: "700",
        color: "var(--text-primary)",
        marginBottom: "8px",
    },
    subtitle: {
        fontSize: "14px",
        color: "var(--text-muted)",
    },
    grid: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))",
        gap: "16px",
        marginBottom: "24px",
    },
    card: {
        background: "var(--card-bg)",
        border: "1px solid var(--border)",
        borderRadius: "12px",
        padding: "24px",
        marginBottom: "16px",
    },
    cardTitle: {
        fontSize: "18px",
        fontWeight: "600",
        color: "var(--text-primary)",
        marginBottom: "8px",
    },
    cardText: {
        fontSize: "14px",
        color: "var(--text-muted)",
        marginBottom: "16px",
        lineHeight: "1.5",
    },
    button: {
        width: "100%",
        padding: "12px 24px",
        background: "#22c55e",
        color: "#fff",
        border: "none",
        borderRadius: "8px",
        fontSize: "14px",
        fontWeight: "600",
        cursor: "pointer",
        transition: "background 0.2s",
    },
    title: {
        fontSize: "24px",
        fontWeight: "700",
        color: "var(--text-primary)",
        marginBottom: "16px",
    },
    text: {
        fontSize: "14px",
        color: "var(--text-muted)",
        lineHeight: "1.6",
    },
    messageBox: {
        background: "#dcfce7",
        border: "1px solid #22c55e",
        borderRadius: "8px",
        padding: "16px",
        marginBottom: "16px",
    },
    messageTitle: {
        fontSize: "16px",
        fontWeight: "600",
        color: "#15803d",
        marginBottom: "8px",
    },
    messageContent: {
        fontSize: "13px",
        color: "#15803d",
        fontFamily: "monospace",
        whiteSpace: "pre-wrap",
        wordBreak: "break-all",
        margin: 0,
    },
    errorBox: {
        background: "#fee2e2",
        border: "1px solid #ef4444",
        borderRadius: "8px",
        padding: "16px",
        marginBottom: "16px",
    },
    errorTitle: {
        fontSize: "16px",
        fontWeight: "600",
        color: "#dc2626",
        marginBottom: "8px",
    },
    errorContent: {
        fontSize: "14px",
        color: "#dc2626",
        margin: 0,
    },
    infoBox: {
        background: "var(--card-bg)",
        border: "1px solid var(--border)",
        borderRadius: "8px",
        padding: "16px",
    },
    infoTitle: {
        fontSize: "16px",
        fontWeight: "600",
        color: "var(--text-primary)",
        marginBottom: "8px",
    },
    infoText: {
        fontSize: "13px",
        color: "var(--text-muted)",
        lineHeight: "1.6",
        marginBottom: "8px",
    },
    infoList: {
        fontSize: "13px",
        color: "var(--text-muted)",
        marginLeft: "20px",
        marginTop: "8px",
    },
};

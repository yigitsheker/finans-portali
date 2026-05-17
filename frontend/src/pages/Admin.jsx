import { useState, useEffect, useCallback } from "react";
import { isAdmin } from "../utils/roleUtils";
import { createAdminApi } from "../api/adminApi";

export default function Admin({ keycloak }) {
    const [tab, setTab] = useState("users");

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

    return (
        <div style={s.container}>
            <div style={s.header}>
                <h1 style={s.mainTitle}>🔧 Yönetim Paneli</h1>
                <p style={s.subtitle}>Kullanıcı ve sistem yönetimi.</p>
            </div>

            <div style={s.tabBar}>
                <TabButton active={tab === "users"} onClick={() => setTab("users")}>👥 Kullanıcılar</TabButton>
                <TabButton active={tab === "data"} onClick={() => setTab("data")}>📦 Veri Yönetimi</TabButton>
                <TabButton active={tab === "feeds"} onClick={() => setTab("feeds")}>📰 RSS Kaynakları</TabButton>
            </div>

            {tab === "users" && <UsersPanel keycloak={keycloak} />}
            {tab === "data" && <DataPanel keycloak={keycloak} />}
            {tab === "feeds" && <FeedsPanel keycloak={keycloak} />}
        </div>
    );
}

function TabButton({ active, onClick, children }) {
    return (
        <button
            onClick={onClick}
            style={{ ...s.tabBtn, ...(active ? s.tabBtnActive : {}) }}
        >
            {children}
        </button>
    );
}

// ── Users panel ───────────────────────────────────────────────────────────

function UsersPanel({ keycloak }) {
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [search, setSearch] = useState("");
    const [busyId, setBusyId] = useState(null);
    const [toast, setToast] = useState(null);

    const adminApi = createAdminApi(keycloak);

    const fetchUsers = useCallback(async (q = "") => {
        setLoading(true);
        setError(null);
        try {
            const data = await adminApi.listUsers(q);
            setUsers(data);
        } catch (e) {
            setError(e.response?.data?.message || e.message || "Kullanıcılar yüklenemedi");
        } finally {
            setLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => { fetchUsers(""); }, [fetchUsers]);

    function showToast(msg, kind = "success") {
        setToast({ msg, kind });
        setTimeout(() => setToast(null), 2500);
    }

    async function handleAction(user, action) {
        const confirms = {
            ban: `${user.username} kullanıcısını banlamak istediğinizden emin misiniz?`,
            unban: `${user.username} kullanıcısının banını kaldırmak istediğinizden emin misiniz?`,
            require2fa: `${user.username} için 2FA zorunlu kılınsın mı? (Sonraki girişte TOTP kuracaktır.)`,
            reset2fa: `${user.username} kullanıcısının tüm OTP kimlik bilgileri silinsin mi?`,
        };
        if (!window.confirm(confirms[action])) return;

        setBusyId(user.id);
        try {
            if (action === "ban") await adminApi.banUser(user.id);
            else if (action === "unban") await adminApi.unbanUser(user.id);
            else if (action === "require2fa") await adminApi.require2fa(user.id);
            else if (action === "reset2fa") await adminApi.reset2fa(user.id);
            showToast("İşlem başarılı.", "success");
            await fetchUsers(search);
        } catch (e) {
            showToast(e.response?.data?.message || e.message || "İşlem başarısız", "error");
        } finally {
            setBusyId(null);
        }
    }

    function handleSearchSubmit(e) {
        e.preventDefault();
        fetchUsers(search);
    }

    return (
        <div>
            <form onSubmit={handleSearchSubmit} style={s.searchBar}>
                <input
                    type="text"
                    placeholder="Kullanıcı ara (kullanıcı adı, email, ad/soyad)"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    style={s.searchInput}
                />
                <button type="submit" style={s.searchBtn} disabled={loading}>
                    {loading ? "Yükleniyor..." : "Ara"}
                </button>
                <button
                    type="button"
                    style={s.refreshBtn}
                    onClick={() => { setSearch(""); fetchUsers(""); }}
                    disabled={loading}
                >
                    Sıfırla
                </button>
            </form>

            {error && (
                <div style={s.errorBox}>
                    <h3 style={s.errorTitle}>❌ Hata</h3>
                    <p style={s.errorContent}>{error}</p>
                </div>
            )}

            {toast && (
                <div style={toast.kind === "error" ? s.errorBox : s.messageBox}>
                    {toast.msg}
                </div>
            )}

            <div style={s.tableWrap}>
                <table style={s.table}>
                    <thead>
                        <tr>
                            <th style={s.th}>Kullanıcı</th>
                            <th style={s.th}>E-posta</th>
                            <th style={s.th}>Durum</th>
                            <th style={s.th}>2FA</th>
                            <th style={s.th}>Oluşturulma</th>
                            <th style={s.th}>İşlemler</th>
                        </tr>
                    </thead>
                    <tbody>
                        {users.length === 0 && !loading && (
                            <tr>
                                <td colSpan={6} style={s.emptyCell}>Kullanıcı bulunamadı.</td>
                            </tr>
                        )}
                        {users.map((u) => (
                            <tr key={u.id}>
                                <td style={s.td}>
                                    <div style={{ fontWeight: 600 }}>{u.username}</div>
                                    <div style={s.muted}>
                                        {[u.firstName, u.lastName].filter(Boolean).join(" ") || "—"}
                                    </div>
                                </td>
                                <td style={s.td}>{u.email || "—"}</td>
                                <td style={s.td}>
                                    {u.enabled
                                        ? <span style={s.badgeActive}>Aktif</span>
                                        : <span style={s.badgeBanned}>Banlı</span>}
                                </td>
                                <td style={s.td}>
                                    {u.totpEnabled
                                        ? <span style={s.badgeActive}>Açık</span>
                                        : (u.requiredActions || []).includes("CONFIGURE_TOTP")
                                            ? <span style={s.badgePending}>Zorunlu (bekliyor)</span>
                                            : <span style={s.muted}>Kapalı</span>}
                                </td>
                                <td style={s.td}>
                                    {u.createdTimestamp
                                        ? new Date(u.createdTimestamp).toLocaleDateString("tr-TR")
                                        : "—"}
                                </td>
                                <td style={s.td}>
                                    <div style={s.actions}>
                                        {u.enabled ? (
                                            <button
                                                style={s.dangerBtn}
                                                disabled={busyId === u.id}
                                                onClick={() => handleAction(u, "ban")}
                                            >
                                                Banla
                                            </button>
                                        ) : (
                                            <button
                                                style={s.successBtn}
                                                disabled={busyId === u.id}
                                                onClick={() => handleAction(u, "unban")}
                                            >
                                                Banı Kaldır
                                            </button>
                                        )}
                                        <button
                                            style={s.secondaryBtn}
                                            disabled={busyId === u.id}
                                            onClick={() => handleAction(u, "require2fa")}
                                            title="Bir sonraki girişte TOTP kurmaya zorla"
                                        >
                                            2FA Zorla
                                        </button>
                                        {u.totpEnabled && (
                                            <button
                                                style={s.warnBtn}
                                                disabled={busyId === u.id}
                                                onClick={() => handleAction(u, "reset2fa")}
                                                title="Kullanıcının OTP kimlik bilgilerini sıfırla"
                                            >
                                                2FA Sıfırla
                                            </button>
                                        )}
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

// ── Data panel ────────────────────────────────────────────────────────────

function DataPanel({ keycloak }) {
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState(null);
    const [error, setError] = useState(null);

    const callAdminEndpoint = async (endpoint) => {
        setLoading(true);
        setMessage(null);
        setError(null);
        try {
            const response = await fetch(`/api/v1/admin/${endpoint}`, {
                method: "POST",
                headers: {
                    Authorization: `Bearer ${keycloak.token}`,
                    "Content-Type": "application/json",
                },
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            const text = await response.text();
            setMessage(text || "İşlem başarılı");
        } catch (err) {
            setError(err.message || "Bir hata oluştu");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={s.grid}>
            <div style={s.card}>
                <h2 style={s.cardTitle}>📈 Piyasa Verileri</h2>
                <p style={s.cardText}>Tüm piyasa verilerini sıfırlayın ve yeniden yükleyin.</p>
                <button style={s.button} onClick={() => callAdminEndpoint("reset-market")} disabled={loading}>
                    {loading ? "İşleniyor..." : "Piyasa Verilerini Sıfırla"}
                </button>
            </div>
            <div style={s.card}>
                <h2 style={s.cardTitle}>💰 Fiyat Güncelleme</h2>
                <p style={s.cardText}>Tüm enstrümanların fiyatlarını hemen güncelleyin.</p>
                <button style={s.button} onClick={() => callAdminEndpoint("refresh-prices")} disabled={loading}>
                    {loading ? "İşleniyor..." : "Fiyatları Güncelle"}
                </button>
            </div>
            <div style={s.card}>
                <h2 style={s.cardTitle}>📰 Haber Verileri</h2>
                <p style={s.cardText}>Tüm haberleri sıfırlayın ve RSS'den yeniden çekin.</p>
                <button style={s.button} onClick={() => callAdminEndpoint("reset-news")} disabled={loading}>
                    {loading ? "İşleniyor..." : "Haberleri Sıfırla"}
                </button>
            </div>

            <div style={s.card}>
                <h2 style={s.cardTitle}>💼 Yatırım Fonları</h2>
                <p style={s.cardText}>Demo fonları silin ve TEFAS public API'den canlı 1000+ fon çekin.</p>
                <button style={s.button} onClick={() => callAdminEndpoint("reset-funds")} disabled={loading}>
                    {loading ? "İşleniyor..." : "Fonları Sıfırla (TEFAS)"}
                </button>
            </div>

            {message && (
                <div style={{ ...s.messageBox, gridColumn: "1 / -1" }}>
                    <h3 style={s.messageTitle}>✅ Başarılı</h3>
                    <pre style={s.messageContent}>{message}</pre>
                </div>
            )}
            {error && (
                <div style={{ ...s.errorBox, gridColumn: "1 / -1" }}>
                    <h3 style={s.errorTitle}>❌ Hata</h3>
                    <p style={s.errorContent}>{error}</p>
                </div>
            )}
        </div>
    );
}

// ─── RSS Feeds Panel ─────────────────────────────────────────────────────────
function FeedsPanel({ keycloak }) {
    const [feeds, setFeeds] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [busyId, setBusyId] = useState(null);
    const [filter, setFilter] = useState("");

    // Add-form state
    const [newUrl, setNewUrl] = useState("");
    const [newCategory, setNewCategory] = useState("");
    const [newSource, setNewSource] = useState("");
    const [adding, setAdding] = useState(false);

    const authFetch = useCallback((url, opts = {}) =>
        fetch(url, {
            ...opts,
            headers: {
                ...(opts.headers || {}),
                Authorization: `Bearer ${keycloak.token}`,
                ...(opts.body ? { "Content-Type": "application/json" } : {}),
            },
        }), [keycloak]);

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const r = await authFetch("/api/v1/admin/feeds");
            if (!r.ok) throw new Error(`HTTP ${r.status}`);
            setFeeds(await r.json());
        } catch (e) {
            setError(e.message || "Liste alınamadı");
        } finally {
            setLoading(false);
        }
    }, [authFetch]);

    useEffect(() => { load(); }, [load]);

    const toggle = async (id) => {
        setBusyId(id);
        try {
            const r = await authFetch(`/api/v1/admin/feeds/${id}/toggle`, { method: "POST" });
            if (!r.ok) throw new Error(`HTTP ${r.status}`);
            const updated = await r.json();
            setFeeds((prev) => prev.map((f) => (f.id === id ? updated : f)));
        } catch (e) {
            alert("Aktiflik değiştirilemedi: " + e.message);
        } finally {
            setBusyId(null);
        }
    };

    const remove = async (id, url) => {
        if (!confirm(`'${url}' feed'i kaldırılsın mı?`)) return;
        setBusyId(id);
        try {
            const r = await authFetch(`/api/v1/admin/feeds/${id}`, { method: "DELETE" });
            if (!r.ok && r.status !== 204) throw new Error(`HTTP ${r.status}`);
            setFeeds((prev) => prev.filter((f) => f.id !== id));
        } catch (e) {
            alert("Silinemedi: " + e.message);
        } finally {
            setBusyId(null);
        }
    };

    const add = async () => {
        const url = newUrl.trim();
        if (!url) return alert("URL zorunlu");
        if (!/^https?:\/\//i.test(url)) return alert("URL http:// veya https:// ile başlamalı");
        setAdding(true);
        try {
            const r = await authFetch("/api/v1/admin/feeds", {
                method: "POST",
                body: JSON.stringify({
                    url,
                    category: newCategory.trim() || "diger",
                    source: newSource.trim() || "Custom",
                }),
            });
            if (r.status === 409) throw new Error("Bu URL zaten ekli");
            if (!r.ok) throw new Error(`HTTP ${r.status}`);
            const added = await r.json();
            setFeeds((prev) => [...prev, added].sort((a, b) =>
                (a.category + a.source).localeCompare(b.category + b.source)));
            setNewUrl(""); setNewCategory(""); setNewSource("");
        } catch (e) {
            alert("Eklenemedi: " + e.message);
        } finally {
            setAdding(false);
        }
    };

    const filtered = filter.trim()
        ? feeds.filter((f) =>
            f.url.toLowerCase().includes(filter.toLowerCase()) ||
            f.category.toLowerCase().includes(filter.toLowerCase()) ||
            f.source.toLowerCase().includes(filter.toLowerCase()))
        : feeds;

    const enabledCount = feeds.filter((f) => f.enabled).length;

    return (
        <div>
            {/* Add form */}
            <div style={{ ...s.card, marginBottom: 12 }}>
                <h3 style={s.cardTitle}>➕ Yeni RSS Kaynağı Ekle</h3>
                <div style={fp.addGrid}>
                    <input
                        style={fp.input}
                        placeholder="RSS URL (https://...)"
                        value={newUrl}
                        onChange={(e) => setNewUrl(e.target.value)}
                    />
                    <input
                        style={fp.input}
                        placeholder="Kategori (örn. hisse)"
                        value={newCategory}
                        onChange={(e) => setNewCategory(e.target.value)}
                    />
                    <input
                        style={fp.input}
                        placeholder="Kaynak (örn. Bloomberg HT)"
                        value={newSource}
                        onChange={(e) => setNewSource(e.target.value)}
                    />
                    <button style={s.button} onClick={add} disabled={adding}>
                        {adding ? "Ekleniyor..." : "Ekle"}
                    </button>
                </div>
                <div style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 8 }}>
                    Olası kategoriler: <code>genel-ekonomi, hisse, doviz, tahvil, kripto, emtia, fonlar, borsa, tcmb, uluslararasi, diger</code>
                </div>
            </div>

            {/* List header */}
            <div style={fp.listHeader}>
                <div style={{ fontSize: 13, color: "var(--text-muted)" }}>
                    {feeds.length} kaynak — <b style={{ color: "var(--accent-solid)" }}>{enabledCount}</b> aktif
                </div>
                <input
                    style={{ ...fp.input, maxWidth: 280 }}
                    placeholder="Filtre (URL / kategori / kaynak)"
                    value={filter}
                    onChange={(e) => setFilter(e.target.value)}
                />
            </div>

            {error && <div style={fp.error}>{error}</div>}

            {loading ? (
                <div style={fp.muted}>Yükleniyor...</div>
            ) : filtered.length === 0 ? (
                <div style={fp.muted}>Eşleşen kaynak yok.</div>
            ) : (
                <div style={s.tableWrap}>
                    <table style={s.table}>
                        <thead>
                            <tr>
                                <th style={s.th}>URL</th>
                                <th style={s.th}>Kategori</th>
                                <th style={s.th}>Kaynak</th>
                                <th style={s.th}>Durum</th>
                                <th style={s.th}>İşlemler</th>
                            </tr>
                        </thead>
                        <tbody>
                            {filtered.map((f) => (
                                <tr key={f.id}>
                                    <td style={{ ...s.td, maxWidth: 380, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}
                                        title={f.url}>
                                        <a href={f.url} target="_blank" rel="noreferrer" style={fp.urlLink}>{f.url}</a>
                                    </td>
                                    <td style={s.td}>
                                        <span style={fp.tag}>{f.category}</span>
                                    </td>
                                    <td style={s.td}>{f.source}</td>
                                    <td style={s.td}>
                                        <span style={{ ...fp.status, ...(f.enabled ? fp.statusOn : fp.statusOff) }}>
                                            {f.enabled ? "● Aktif" : "○ Pasif"}
                                        </span>
                                    </td>
                                    <td style={s.td}>
                                        <button
                                            style={{ ...fp.actionBtn, marginRight: 6 }}
                                            onClick={() => toggle(f.id)}
                                            disabled={busyId === f.id}
                                        >
                                            {f.enabled ? "Devre Dışı Bırak" : "Etkinleştir"}
                                        </button>
                                        <button
                                            style={fp.actionDanger}
                                            onClick={() => remove(f.id, f.url)}
                                            disabled={busyId === f.id}
                                        >
                                            Sil
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}

const fp = {
    addGrid: {
        display: "grid",
        gridTemplateColumns: "2fr 1fr 1fr auto",
        gap: 8,
        alignItems: "center",
    },
    input: {
        padding: "8px 12px",
        borderRadius: 8,
        border: "1px solid var(--input-border)",
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        fontSize: 13,
        outline: "none",
        width: "100%",
        boxSizing: "border-box",
    },
    listHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 10,
        flexWrap: "wrap",
        gap: 12,
    },
    error: { padding: 12, color: "var(--danger-text)", background: "var(--danger-bg)", borderRadius: 8, marginBottom: 12 },
    muted: { padding: 24, textAlign: "center", color: "var(--text-muted)", fontSize: 13 },
    urlLink: { color: "var(--text-secondary)", textDecoration: "none", fontSize: 12 },
    tag: {
        padding: "2px 10px",
        borderRadius: 999,
        background: "var(--accent-hover-bg)",
        color: "var(--accent-solid)",
        fontSize: 11,
        fontWeight: 600,
    },
    status: { padding: "3px 10px", borderRadius: 999, fontSize: 11, fontWeight: 600 },
    statusOn:  { background: "rgba(34, 197, 94, 0.14)", color: "var(--green)" },
    statusOff: { background: "rgba(148, 163, 184, 0.16)", color: "var(--text-muted)" },
    actionBtn: {
        padding: "5px 11px",
        borderRadius: 6,
        border: "1px solid var(--border-card)",
        background: "var(--input-bg)",
        color: "var(--text-primary)",
        fontSize: 11,
        fontWeight: 600,
        cursor: "pointer",
    },
    actionDanger: {
        padding: "5px 11px",
        borderRadius: 6,
        border: "1px solid var(--danger-border)",
        background: "var(--danger-bg)",
        color: "var(--danger-text)",
        fontSize: 11,
        fontWeight: 600,
        cursor: "pointer",
    },
};

const s = {
    container: { padding: "24px", maxWidth: "1300px", margin: "0 auto" },
    header: { marginBottom: "24px" },
    mainTitle: { fontSize: "26px", fontWeight: "700", color: "var(--text-primary)", marginBottom: "6px" },
    subtitle: { fontSize: "13px", color: "var(--text-muted)" },

    tabBar: {
        display: "flex", gap: 8, marginBottom: 20,
        borderBottom: "1px solid var(--border)", paddingBottom: 0,
    },
    tabBtn: {
        padding: "10px 18px", border: "none", background: "transparent",
        color: "var(--text-muted)", fontSize: 14, fontWeight: 600,
        cursor: "pointer", borderBottom: "2px solid transparent",
    },
    tabBtnActive: {
        color: "var(--text-primary)", borderBottom: "2px solid #22c55e",
    },

    grid: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "16px" },
    card: {
        background: "var(--card-bg)", border: "1px solid var(--border)",
        borderRadius: "12px", padding: "20px",
    },
    cardTitle: { fontSize: "16px", fontWeight: "600", color: "var(--text-primary)", marginBottom: "6px" },
    cardText: { fontSize: "13px", color: "var(--text-muted)", marginBottom: "14px", lineHeight: "1.5" },
    button: {
        width: "100%", padding: "10px 20px", background: "#22c55e",
        color: "#fff", border: "none", borderRadius: "8px",
        fontSize: "13px", fontWeight: "600", cursor: "pointer",
    },
    title: { fontSize: "22px", fontWeight: "700", color: "var(--text-primary)", marginBottom: "12px" },
    text: { fontSize: "14px", color: "var(--text-muted)", lineHeight: "1.6" },

    searchBar: { display: "flex", gap: 8, marginBottom: 16 },
    searchInput: {
        flex: 1, padding: "10px 14px", borderRadius: 8,
        border: "1px solid var(--input-border)", background: "var(--input-bg)",
        color: "var(--text-primary)", fontSize: 13, outline: "none",
    },
    searchBtn: {
        padding: "10px 18px", background: "#22c55e", color: "#fff",
        border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
    },
    refreshBtn: {
        padding: "10px 14px", background: "transparent", color: "var(--text-primary)",
        border: "1px solid var(--border)", borderRadius: 8, fontSize: 13, cursor: "pointer",
    },

    tableWrap: { overflowX: "auto", background: "var(--card-bg)", border: "1px solid var(--border)", borderRadius: 10 },
    table: { width: "100%", borderCollapse: "collapse", fontSize: 13 },
    th: {
        textAlign: "left", padding: "12px 14px",
        background: "var(--input-bg)", color: "var(--text-muted)",
        fontWeight: 600, fontSize: 12, textTransform: "uppercase", letterSpacing: "0.4px",
        borderBottom: "1px solid var(--border)",
    },
    td: { padding: "12px 14px", borderBottom: "1px solid var(--border-soft, var(--border))", color: "var(--text-primary)", verticalAlign: "middle" },
    muted: { fontSize: 11, color: "var(--text-muted)" },
    emptyCell: { padding: "32px", textAlign: "center", color: "var(--text-muted)" },

    badgeActive: {
        display: "inline-block", padding: "3px 10px", borderRadius: 999,
        background: "rgba(34,197,94,0.15)", color: "#22c55e",
        fontSize: 11, fontWeight: 600,
    },
    badgeBanned: {
        display: "inline-block", padding: "3px 10px", borderRadius: 999,
        background: "rgba(239,68,68,0.15)", color: "#ef4444",
        fontSize: 11, fontWeight: 600,
    },
    badgePending: {
        display: "inline-block", padding: "3px 10px", borderRadius: 999,
        background: "rgba(234,179,8,0.18)", color: "#eab308",
        fontSize: 11, fontWeight: 600,
    },

    actions: { display: "flex", gap: 6, flexWrap: "wrap" },
    dangerBtn: { padding: "6px 12px", borderRadius: 6, border: "none", background: "#ef4444", color: "#fff", fontSize: 12, fontWeight: 600, cursor: "pointer" },
    successBtn: { padding: "6px 12px", borderRadius: 6, border: "none", background: "#22c55e", color: "#fff", fontSize: 12, fontWeight: 600, cursor: "pointer" },
    secondaryBtn: { padding: "6px 12px", borderRadius: 6, border: "1px solid var(--border)", background: "transparent", color: "var(--text-primary)", fontSize: 12, fontWeight: 600, cursor: "pointer" },
    warnBtn: { padding: "6px 12px", borderRadius: 6, border: "none", background: "#eab308", color: "#000", fontSize: 12, fontWeight: 600, cursor: "pointer" },

    messageBox: { background: "rgba(34,197,94,0.08)", border: "1px solid rgba(34,197,94,0.4)", borderRadius: 8, padding: 14, marginBottom: 12, color: "#22c55e" },
    messageTitle: { fontSize: 14, fontWeight: 600, marginBottom: 6 },
    messageContent: { fontSize: 12, fontFamily: "monospace", whiteSpace: "pre-wrap", wordBreak: "break-all", margin: 0 },
    errorBox: { background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.4)", borderRadius: 8, padding: 14, marginBottom: 12, color: "#ef4444" },
    errorTitle: { fontSize: 14, fontWeight: 600, marginBottom: 6 },
    errorContent: { fontSize: 13, margin: 0 },
};

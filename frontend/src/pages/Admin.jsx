import { useState, useEffect, useCallback } from "react";
import { IconNewspaper, IconRefresh, IconSparkles } from "../components/common/icons";
import { isAdmin } from "../utils/roleUtils";
import { createAdminApi } from "../api/adminApi";
import { useI18n } from "../contexts/I18nContext";

export default function Admin({ keycloak }) {
    const { t } = useI18n();
    const [tab, setTab] = useState("users");

    if (!isAdmin(keycloak)) {
        return (
            <div style={s.container}>
                <div style={s.card}>
                    <h2 style={s.title}>{t("admin.denied")}</h2>
                    <p style={s.text}>
                        {t("admin.deniedBody")}
                    </p>
                </div>
            </div>
        );
    }

    return (
        <div style={s.container}>
            <div style={s.header}>
                <h1 style={s.mainTitle}>{t("admin.title")}</h1>
                <p style={s.subtitle}>{t("admin.subtitle")}</p>
            </div>

            <div style={s.tabBar}>
                <TabButton active={tab === "users"} onClick={() => setTab("users")}>{t("admin.tabUsers")}</TabButton>
                <TabButton active={tab === "data"} onClick={() => setTab("data")}>{t("admin.tabData")}</TabButton>
                <TabButton active={tab === "feeds"} onClick={() => setTab("feeds")}>{t("admin.tabRss")}</TabButton>
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
    const { t } = useI18n();
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
            ban: t("admin.confirmBan", { user: user.username }),
            unban: t("admin.confirmUnban", { user: user.username }),
            require2fa: t("admin.confirm2faForce", { user: user.username }),
            reset2fa: t("admin.confirm2faReset", { user: user.username }),
        };
        if (!window.confirm(confirms[action])) return;

        setBusyId(user.id);
        try {
            if (action === "ban") await adminApi.banUser(user.id);
            else if (action === "unban") await adminApi.unbanUser(user.id);
            else if (action === "require2fa") await adminApi.require2fa(user.id);
            else if (action === "reset2fa") await adminApi.reset2fa(user.id);
            showToast(t("admin.opSuccess"), "success");
            await fetchUsers(search);
        } catch (e) {
            showToast(e.response?.data?.message || e.message || t("admin.opFail"), "error");
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
                    placeholder={t("admin.searchPh")}
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    style={s.searchInput}
                />
                <button type="submit" style={s.searchBtn} disabled={loading}>
                    {loading ? t("common.loading") : t("admin.btnSearch")}
                </button>
                <button
                    type="button"
                    style={s.refreshBtn}
                    onClick={() => { setSearch(""); fetchUsers(""); }}
                    disabled={loading}
                >
                    {t("admin.btnReset")}
                </button>
            </form>

            {error && (
                <div style={s.errorBox}>
                    <h3 style={s.errorTitle}>{t("admin.errLabel")}</h3>
                    <p style={s.errorContent}>{error}</p>
                </div>
            )}

            {toast && (
                <div style={toast.kind === "error" ? s.errorBox : s.messageBox}>
                    {toast.msg}
                </div>
            )}

            <div style={s.tableWrap} className="fp-table-scroll">
                <table style={s.table}>
                    <thead>
                        <tr>
                            <th style={s.th}>{t("admin.colUser")}</th>
                            <th style={s.th}>{t("admin.colEmail")}</th>
                            <th style={s.th}>{t("admin.colStatus")}</th>
                            <th style={s.th}>{t("admin.col2fa")}</th>
                            <th style={s.th}>{t("admin.colCreated")}</th>
                            <th style={s.th}>{t("admin.colActions")}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {users.length === 0 && !loading && (
                            <tr>
                                <td colSpan={6} style={s.emptyCell}>{t("admin.noUsers")}</td>
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
                                        ? <span style={s.badgeActive}>{t("admin.statusActive")}</span>
                                        : <span style={s.badgeBanned}>{t("admin.statusBanned")}</span>}
                                </td>
                                <td style={s.td}>
                                    {u.totpEnabled
                                        ? <span style={s.badgeActive}>{t("admin.mfaOn")}</span>
                                        : (u.requiredActions || []).includes("CONFIGURE_TOTP")
                                            ? <span style={s.badgePending}>{t("admin.mfaPending")}</span>
                                            : <span style={s.muted}>{t("admin.mfaOff")}</span>}
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
                                                {t("admin.btnBan")}
                                            </button>
                                        ) : (
                                            <button
                                                style={s.successBtn}
                                                disabled={busyId === u.id}
                                                onClick={() => handleAction(u, "unban")}
                                            >
                                                {t("admin.btnUnban")}
                                            </button>
                                        )}
                                        <button
                                            style={s.secondaryBtn}
                                            disabled={busyId === u.id}
                                            onClick={() => handleAction(u, "require2fa")}
                                            title="Bir sonraki girişte TOTP kurmaya zorla"
                                        >
                                            {t("admin.btn2faForce")}
                                        </button>
                                        {u.totpEnabled && (
                                            <button
                                                style={s.warnBtn}
                                                disabled={busyId === u.id}
                                                onClick={() => handleAction(u, "reset2fa")}
                                                title="Kullanıcının OTP kimlik bilgilerini sıfırla"
                                            >
                                                {t("admin.btn2faReset")}
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
    const { t } = useI18n();
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
                <h2 style={s.cardTitle}>{t("admin.dataMarket")}</h2>
                <p style={s.cardText}>{t("admin.dataMarketSub")}</p>
                <button style={s.button} onClick={() => callAdminEndpoint("reset-market")} disabled={loading}>
                    {loading ? t("admin.processing") : t("admin.dataMarketBtn")}
                </button>
            </div>
            <div style={s.card}>
                <h2 style={s.cardTitle}>{t("admin.dataPrice")}</h2>
                <p style={s.cardText}>{t("admin.dataPriceSub")}</p>
                <button style={s.button} onClick={() => callAdminEndpoint("refresh-prices")} disabled={loading}>
                    {loading ? t("admin.processing") : t("admin.dataPriceBtn")}
                </button>
            </div>
            <div style={s.card}>
                <h2 style={s.cardTitle}>{t("admin.dataNews")}</h2>
                <p style={s.cardText}>{t("admin.dataNewsSub")}</p>
                <button style={s.button} onClick={() => callAdminEndpoint("reset-news")} disabled={loading}>
                    {loading ? t("admin.processing") : t("admin.dataNewsBtn")}
                </button>
            </div>

            <div style={s.card}>
                <h2 style={s.cardTitle}>{t("admin.dataFunds")}</h2>
                <p style={s.cardText}>{t("admin.dataFundsSub")}</p>
                <button style={s.button} onClick={() => callAdminEndpoint("reset-funds")} disabled={loading}>
                    {loading ? t("admin.processing") : t("admin.dataFundsBtn")}
                </button>
            </div>

            {message && (
                <div style={{ ...s.messageBox, gridColumn: "1 / -1" }}>
                    <h3 style={s.messageTitle}>{t("admin.successLabel")}</h3>
                    <pre style={s.messageContent}>{message}</pre>
                </div>
            )}
            {error && (
                <div style={{ ...s.errorBox, gridColumn: "1 / -1" }}>
                    <h3 style={s.errorTitle}>{t("admin.errLabel")}</h3>
                    <p style={s.errorContent}>{error}</p>
                </div>
            )}
        </div>
    );
}

// ─── RSS Feeds Panel ─────────────────────────────────────────────────────────
function FeedsPanel({ keycloak }) {
    const { t } = useI18n();
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
        if (!confirm(t("admin.rssRemoveQ", { url }))) return;
        setBusyId(id);
        try {
            const r = await authFetch(`/api/v1/admin/feeds/${id}`, { method: "DELETE" });
            if (!r.ok && r.status !== 204) throw new Error(`HTTP ${r.status}`);
            setFeeds((prev) => prev.filter((f) => f.id !== id));
        } catch (e) {
            alert(t("admin.rssDeleteErr", { error: e.message }));
        } finally {
            setBusyId(null);
        }
    };

    const add = async () => {
        const url = newUrl.trim();
        if (!url) return alert(t("admin.rssUrlReq"));
        if (!/^https?:\/\//i.test(url)) return alert(t("admin.rssUrlScheme"));
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
            if (r.status === 409) throw new Error(t("admin.rssDupErr"));
            if (!r.ok) throw new Error(`HTTP ${r.status}`);
            const added = await r.json();
            setFeeds((prev) => [...prev, added].sort((a, b) =>
                (a.category + a.source).localeCompare(b.category + b.source)));
            setNewUrl(""); setNewCategory(""); setNewSource("");
        } catch (e) {
            alert(t("admin.rssAddErr", { error: e.message }));
        } finally {
            setAdding(false);
        }
    };

    // Manual orphan sweep — articles whose feed has been deleted. The
    // backend also runs this on startup and daily at 03:00 UTC, but the
    // button gives admins an immediate trigger.
    const [cleaning, setCleaning] = useState(false);
    const cleanupOrphans = async () => {
        if (!confirm("Silinmiş kaynaklara ait yetim haberler silinsin mi?")) return;
        setCleaning(true);
        try {
            const r = await authFetch("/api/v1/admin/feeds/cleanup-orphans", { method: "POST" });
            if (!r.ok) throw new Error(`HTTP ${r.status}`);
            const { removed } = await r.json();
            alert(`Yetim haber temizliği tamamlandı: ${removed} haber silindi.`);
        } catch (e) {
            alert("Temizleme başarısız: " + e.message);
        } finally {
            setCleaning(false);
        }
    };

    // Manual news fetch (no reset) — fills under-stocked categories, e.g. right
    // after re-enabling a feed. The server runs it in the background.
    const [fetchingNews, setFetchingNews] = useState(false);
    const fetchNews = async () => {
        setFetchingNews(true);
        try {
            const r = await authFetch("/api/v1/admin/refresh-news", { method: "POST" });
            if (!r.ok) throw new Error(`HTTP ${r.status}`);
            alert((await r.text()) || "Haber çekme başlatıldı.");
        } catch (e) {
            alert("Haber çekme başarısız: " + e.message);
        } finally {
            setFetchingNews(false);
        }
    };

    // Reset: wipe ALL news and re-fetch from scratch (background on the server).
    const [resettingNews, setResettingNews] = useState(false);
    const resetNews = async () => {
        if (!confirm("Tüm haberler silinip RSS kaynaklarından baştan çekilecek. Devam edilsin mi?")) return;
        setResettingNews(true);
        try {
            const r = await authFetch("/api/v1/admin/reset-news", { method: "POST" });
            if (!r.ok) throw new Error(`HTTP ${r.status}`);
            alert((await r.text()) || "Sıfırlandı; baştan çekme başlatıldı.");
        } catch (e) {
            alert("Sıfırlama başarısız: " + e.message);
        } finally {
            setResettingNews(false);
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
                <h3 style={s.cardTitle}>{t("admin.rssAdd")}</h3>
                <div style={fp.addGrid}>
                    <input
                        style={fp.input}
                        placeholder={t("admin.rssUrlPh")}
                        value={newUrl}
                        onChange={(e) => setNewUrl(e.target.value)}
                    />
                    <input
                        style={fp.input}
                        placeholder={t("admin.rssCatPh")}
                        value={newCategory}
                        onChange={(e) => setNewCategory(e.target.value)}
                    />
                    <input
                        style={fp.input}
                        placeholder={t("admin.rssSourcePh")}
                        value={newSource}
                        onChange={(e) => setNewSource(e.target.value)}
                    />
                    <button style={s.button} onClick={add} disabled={adding}>
                        {adding ? t("admin.rssAdding") : t("admin.rssAddBtn")}
                    </button>
                </div>
                <div style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 8 }}>
                    {t("admin.rssCatsHint")}
                </div>
            </div>

            {/* List header */}
            <div style={fp.listHeader}>
                <div style={{ fontSize: 13, color: "var(--text-muted)" }}>
                    {t("admin.rssSummary", { count: feeds.length, enabled: enabledCount })}
                </div>
                <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                    <button
                        type="button"
                        style={s.button}
                        onClick={fetchNews}
                        disabled={fetchingNews}
                        title="RSS kaynaklarından yeni haberleri çeker (mevcutları silmez). Bir feed'i yeniden aktif ettikten sonra kullanın."
                    >
                        {fetchingNews ? "Çekiliyor..." : <><IconNewspaper size={13} style={{ verticalAlign: "-2px", marginRight: 6 }} />Haberleri Çek</>}
                    </button>
                    <button
                        type="button"
                        style={{ ...s.button, background: "var(--red, #dc2626)" }}
                        onClick={resetNews}
                        disabled={resettingNews}
                        title="Tüm haberleri siler ve RSS kaynaklarından baştan çeker"
                    >
                        {resettingNews ? "Sıfırlanıyor..." : <><IconRefresh size={13} style={{ verticalAlign: "-2px", marginRight: 6 }} />Sıfırla & Baştan Çek</>}
                    </button>
                    <button
                        type="button"
                        style={{ ...s.button, background: "var(--bg-card)", color: "var(--text-primary)", border: "1px solid var(--border-card)" }}
                        onClick={cleanupOrphans}
                        disabled={cleaning}
                        title="Silinmiş RSS kaynaklarına ait yetim haberleri kaldırır"
                    >
                        {cleaning ? "Temizleniyor..." : <><IconSparkles size={13} style={{ verticalAlign: "-2px", marginRight: 6 }} />Yetim Haberleri Temizle</>}
                    </button>
                    <input
                        style={{ ...fp.input, maxWidth: 280 }}
                        placeholder={t("admin.rssFilterPh")}
                        value={filter}
                        onChange={(e) => setFilter(e.target.value)}
                    />
                </div>
            </div>

            {error && <div style={fp.error}>{error}</div>}

            {loading ? (
                <div style={fp.muted}>{t("common.loading")}</div>
            ) : filtered.length === 0 ? (
                <div style={fp.muted}>{t("admin.rssNone")}</div>
            ) : (
                <div style={s.tableWrap} className="fp-table-scroll">
                    <table style={s.table}>
                        <thead>
                            <tr>
                                <th style={s.th}>{t("admin.rssUrl")}</th>
                                <th style={s.th}>{t("admin.rssCat")}</th>
                                <th style={s.th}>{t("admin.rssSource")}</th>
                                <th style={s.th}>{t("admin.rssStatus")}</th>
                                <th style={s.th}>{t("admin.colActions")}</th>
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
                                            {f.enabled ? t("admin.rssDisable") : t("admin.rssEnable")}
                                        </button>
                                        <button
                                            style={fp.actionDanger}
                                            onClick={() => remove(f.id, f.url)}
                                            disabled={busyId === f.id}
                                        >
                                            {t("admin.rssDelete")}
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

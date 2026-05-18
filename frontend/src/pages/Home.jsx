import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { getMarketSummary, getNews } from "../api/portfolioApi";

/**
 * Home — the new landing page.
 *
 * Three stacked sections:
 *   1. Hero      → tagline + CTAs + "günün hareketlileri" panel.
 *   2. Features  → six cards linking to each major section of the app.
 *   3. Snapshot  → BIST 100 top-movers table + Döviz/Kripto/Emtia sidecards.
 *   4. News      → latest 3 news cards as a preview, with "Tüm haberler" link.
 *
 * All data comes from the public market/news endpoints — no auth required.
 */
export default function Home({ keycloak }) {
    // /api/v1/market/summary returns a flat array — one quote per instrument
    // with { symbol, name, type, last, changeAbs, changePct, asOf }.
    // No separate /instruments call needed; everything we render below comes
    // from this single endpoint, filtered by `type`.
    const [summary, setSummary] = useState([]);
    const [news, setNews] = useState([]);
    const [filter, setFilter] = useState("ALL"); // ALL | UP | DOWN | VOL

    useEffect(() => {
        Promise.all([
            getMarketSummary().catch(() => []),
            getNews().catch(() => []),
        ]).then(([s, n]) => {
            setSummary(Array.isArray(s) ? s : []);
            setNews(Array.isArray(n) ? n : []);
        });
    }, []);

    // BIST 100 universe — backend tags these specifically with type "BIST"
    // (US stocks come back as type "STOCK"). Filter buttons sort/restrict.
    const bistRows = useMemo(() => {
        let rows = summary.filter((i) => i.type === "BIST");
        if (filter === "UP") {
            rows = rows.filter((r) => (r.changePct ?? 0) > 0)
                .sort((a, b) => (b.changePct ?? 0) - (a.changePct ?? 0));
        } else if (filter === "DOWN") {
            rows = rows.filter((r) => (r.changePct ?? 0) < 0)
                .sort((a, b) => (a.changePct ?? 0) - (b.changePct ?? 0));
        } else if (filter === "VOL") {
            // Volume isn't on the summary payload; fall back to absolute
            // price change as a "active" proxy so the filter still does
            // something useful instead of producing identical rows.
            rows = rows.sort((a, b) => Math.abs(b.changeAbs ?? 0) - Math.abs(a.changeAbs ?? 0));
        }
        return rows.slice(0, 8);
    }, [summary, filter]);

    // "Günün hareketlileri" — biggest absolute % movers across the whole
    // universe (not just BIST) so the panel feels global.
    const movers = useMemo(() => {
        return summary
            .filter((i) => i.last != null && i.changePct != null)
            .sort((a, b) => Math.abs(b.changePct) - Math.abs(a.changePct))
            .slice(0, 5);
    }, [summary]);

    // Side panels: 3 rows per asset class.
    const fxRows = useMemo(
        () => summary.filter((r) => r.type === "FX").slice(0, 4),
        [summary]
    );
    const cryptoRows = useMemo(
        () => summary.filter((r) => r.type === "CRYPTO").slice(0, 4),
        [summary]
    );
    const commodityRows = useMemo(
        () => summary.filter((r) => r.type === "COMMODITY").slice(0, 4),
        [summary]
    );

    const updatedAt = useMemo(() => {
        if (!summary.length) return null;
        // Pick the freshest asOf timestamp across all rows.
        const latest = summary.reduce((max, r) => {
            const t = r.asOf ? new Date(r.asOf).getTime() : 0;
            return t > max ? t : max;
        }, 0);
        if (!latest) return null;
        return new Date(latest).toLocaleString("tr-TR", {
            day: "2-digit", month: "long", hour: "2-digit", minute: "2-digit",
        });
    }, [summary]);

    return (
        <div style={s.page}>
            {/* ───────── HERO ───────── */}
            <section style={s.hero}>
                <div style={s.heroLeft}>
                    <span style={s.heroBadge}>📈 FINANS PORTALI</span>
                    <h1 style={s.heroTitle}>
                        <span style={s.heroTitleSoft}>Yatırım dünyası,</span><br/>
                        <span style={s.heroTitleAccent}>tek bir ekranda.</span>
                    </h1>
                    <p style={s.heroLead}>
                        Hisse, kripto, fon, tahvil ve döviz — Türkiye ve dünya
                        piyasalarını canlı verilerle izleyin, haberleri okuyun,
                        portföyünüzü tek yerden yönetin.
                    </p>
                    <div style={s.heroCtas}>
                        <Link to="/stocks" style={s.ctaPrimary}>
                            Piyasaları Keşfet
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                                <line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/>
                            </svg>
                        </Link>
                        <Link to="/portfolio" style={s.ctaSecondary}>
                            Portföyümü Görüntüle
                        </Link>
                    </div>

                    <div style={s.heroStats}>
                        <div style={s.statItem}>
                            <div style={s.statNum}>+1.200</div>
                            <div style={s.statLabel}>Enstrüman</div>
                        </div>
                        <div style={s.statItem}>
                            <div style={s.statNum}>7/24</div>
                            <div style={s.statLabel}>Canlı veri</div>
                        </div>
                        <div style={s.statItem}>
                            <div style={s.statNum}>%99,9</div>
                            <div style={s.statLabel}>Süreklilik</div>
                        </div>
                    </div>
                </div>

                <div style={s.heroRight}>
                    <div style={s.moversCard}>
                        <div style={s.moversHead}>
                            <span style={s.moversTitle}>Günün Hareketlileri</span>
                            <span style={s.moversChip}>BIST</span>
                        </div>
                        <ul style={s.moversList}>
                            {movers.length === 0 ? (
                                <li style={s.moversEmpty}>Veri yükleniyor…</li>
                            ) : movers.map((m) => {
                                const pct = Number(m.changePct ?? 0);
                                const up = pct >= 0;
                                return (
                                    <li key={m.symbol} style={s.moverRow}>
                                        <span style={{ ...s.moverDot, background: up ? "var(--green, #10b981)" : "var(--red, #ef4444)" }}>
                                            {up ? "↗" : "↘"}
                                        </span>
                                        <span style={s.moverSymbol}>{m.symbol}</span>
                                        <span style={{ ...s.moverPct, color: up ? "var(--green, #10b981)" : "var(--red, #ef4444)" }}>
                                            {up ? "+" : ""}{pct.toFixed(2)}%
                                        </span>
                                    </li>
                                );
                            })}
                        </ul>
                    </div>
                </div>
            </section>

            {/* ───────── FEATURE CARDS ───────── */}
            <section style={s.featuresGrid}>
                <FeatureCard to="/stocks" icon="📊" title="Hisse Senetleri" desc="BIST + uluslararası hisseler" />
                <FeatureCard to="/crypto" icon="₿" title="Kripto Paralar" desc="Anlık kripto fiyatları" />
                <FeatureCard to="/funds" icon="💼" title="Yatırım Fonları" desc="TEFAS fon performansı" />
                <FeatureCard to="/bonds" icon="🏛️" title="Tahvil ve Bono" desc="TCMB DİBS ve eurobondlar" />
                <FeatureCard to="/market-data" icon="💱" title="Döviz Kurları" desc="TCMB güncel kurlar" />
                <FeatureCard to="/news" icon="📰" title="Haberler" desc="Piyasa & ekonomi gündemi" />
            </section>

            {/* ───────── BIST + SIDECARDS ───────── */}
            <section style={s.snapshotGrid}>
                {/* BIST table */}
                <div style={s.bistCard}>
                    <div style={s.bistHead}>
                        <div>
                            <h3 style={s.bistTitle}>BIST 100 — En aktif hisseler</h3>
                            <div style={s.bistSub}>
                                {updatedAt ? `Güncellendi: ${updatedAt}` : "Yükleniyor…"}
                            </div>
                        </div>
                        <div style={s.bistFilters}>
                            {[
                                { k: "ALL",  label: "Tümü" },
                                { k: "UP",   label: "Yükselenler" },
                                { k: "DOWN", label: "Düşenler" },
                                { k: "VOL",  label: "Hacim" },
                            ].map((opt) => (
                                <button
                                    key={opt.k}
                                    onClick={() => setFilter(opt.k)}
                                    style={{
                                        ...s.bistFilter,
                                        ...(filter === opt.k ? s.bistFilterActive : {}),
                                    }}
                                >
                                    {opt.label}
                                </button>
                            ))}
                        </div>
                    </div>

                    <table style={s.bistTable}>
                        <thead>
                            <tr>
                                <th style={s.bistTh}>SEMBOL</th>
                                <th style={{ ...s.bistTh, textAlign: "right" }}>FİYAT ₺</th>
                                <th style={{ ...s.bistTh, textAlign: "right" }}>DEĞİŞİM %</th>
                                <th style={{ ...s.bistTh, textAlign: "right" }}>DEĞİŞİM ₺</th>
                            </tr>
                        </thead>
                        <tbody>
                            {bistRows.length === 0 ? (
                                <tr><td colSpan={4} style={s.bistEmpty}>Veri yükleniyor…</td></tr>
                            ) : bistRows.map((r) => {
                                const pct = Number(r.changePct ?? 0);
                                const up = pct >= 0;
                                return (
                                    <tr key={r.symbol} style={s.bistTr}>
                                        <td style={s.bistTdSym}>
                                            <span style={s.bistTag}>{r.symbol.slice(0, 2)}</span>
                                            <div style={{ display: "flex", flexDirection: "column" }}>
                                                <span style={{ fontWeight: 700 }}>{r.symbol}</span>
                                                <span style={s.bistTdName}>{r.name || ""}</span>
                                            </div>
                                        </td>
                                        <td style={s.bistTdNum}>{fmtPrice(r.last)}</td>
                                        <td style={{ ...s.bistTdNum, color: up ? "var(--green, #10b981)" : "var(--red, #ef4444)", fontWeight: 700 }}>
                                            {up ? "▲" : "▼"} {pct.toFixed(2)}
                                        </td>
                                        <td style={{ ...s.bistTdNum, color: up ? "var(--green, #10b981)" : "var(--red, #ef4444)" }}>
                                            {up ? "+" : ""}{fmtPrice(r.changeAbs)}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>

                {/* Right sidecards */}
                <div style={s.sideCol}>
                    <SidePanel title="Döviz" chip="TCMB" rows={fxRows} formatter={(r) => fmtPrice(r.last)} />
                    <SidePanel title="Kripto" chip="24S" rows={cryptoRows} formatter={(r) => "$" + fmtPrice(r.last)} />
                    <SidePanel title="Emtia" chip="SPOT" rows={commodityRows} formatter={(r) => "$" + fmtPrice(r.last)} />
                </div>
            </section>

            {/* ───────── NEWS PREVIEW ───────── */}
            <section style={s.newsSection}>
                <div style={s.newsHead}>
                    <h2 style={s.newsTitle}>Finans Haberleri</h2>
                    <Link to="/news" style={s.newsAll}>Tüm haberler →</Link>
                </div>
                <div style={s.newsGrid}>
                    {news.slice(0, 3).map((n) => (
                        <Link key={n.id} to={`/news/${n.id}`} style={s.newsCard}>
                            <div style={s.newsCategory}>{(n.category || "GENEL").toUpperCase()}</div>
                            <h3 style={s.newsCardTitle}>{n.title}</h3>
                            <p style={s.newsCardSummary}>{n.summary}</p>
                            <span style={s.newsRead}>Devamını oku ↗</span>
                        </Link>
                    ))}
                    {news.length === 0 && (
                        <div style={s.newsCardEmpty}>Haberler yükleniyor…</div>
                    )}
                </div>
            </section>
        </div>
    );
}

/* ── Helpers ───────────────────────────────────────────────────────── */

function FeatureCard({ to, icon, title, desc }) {
    return (
        <Link to={to} style={s.featureCard}>
            <div style={s.featureIcon}>{icon}</div>
            <div>
                <div style={s.featureTitle}>{title}</div>
                <div style={s.featureDesc}>{desc}</div>
            </div>
        </Link>
    );
}

function SidePanel({ title, chip, rows, formatter }) {
    return (
        <div style={s.sidePanel}>
            <div style={s.sidePanelHead}>
                <span style={s.sidePanelTitle}>{title}</span>
                <span style={s.sidePanelChip}>{chip}</span>
            </div>
            <ul style={s.sidePanelList}>
                {rows.length === 0 ? (
                    <li style={s.sidePanelEmpty}>—</li>
                ) : rows.map((r) => {
                    const pct = Number(r.changePct ?? 0);
                    const up = pct >= 0;
                    return (
                        <li key={r.symbol} style={s.sidePanelRow}>
                            <span style={s.sidePanelSym}>{r.symbol.replace("/", "/")}</span>
                            <span style={s.sidePanelPrice}>{formatter(r)}</span>
                            <span style={{ ...s.sidePanelPct, color: up ? "var(--green, #10b981)" : "var(--red, #ef4444)" }}>
                                {up ? "▲" : "▼"} {Math.abs(pct).toFixed(2)}%
                            </span>
                        </li>
                    );
                })}
            </ul>
        </div>
    );
}

function fmtPrice(v) {
    if (v == null || !Number.isFinite(Number(v))) return "—";
    return Number(v).toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 4 });
}
function fmtVolume(v) {
    if (v == null || !Number.isFinite(Number(v))) return "—";
    const n = Number(v);
    if (n >= 1e9) return (n / 1e9).toFixed(1).replace(".", ",") + " Mr ₺";
    if (n >= 1e6) return (n / 1e6).toFixed(0) + " M ₺";
    return n.toLocaleString("tr-TR");
}

/* ── Styles ────────────────────────────────────────────────────────── */

const s = {
    page: {
        maxWidth: 1400,
        margin: "0 auto",
        padding: "8px 0 40px",
        display: "flex",
        flexDirection: "column",
        gap: 32,
    },

    /* Hero */
    hero: {
        display: "grid",
        gridTemplateColumns: "1.3fr 1fr",
        gap: 32,
        padding: "40px 36px",
        borderRadius: 18,
        background: "linear-gradient(135deg, rgba(34,197,94,0.10) 0%, rgba(34,197,94,0.02) 60%)",
        border: "1px solid var(--border-card)",
    },
    heroLeft: { display: "flex", flexDirection: "column", gap: 18 },
    heroBadge: {
        display: "inline-flex",
        alignSelf: "flex-start",
        alignItems: "center",
        gap: 6,
        padding: "6px 14px",
        borderRadius: 999,
        background: "rgba(34,197,94,0.15)",
        color: "var(--accent-solid)",
        fontSize: 12,
        fontWeight: 700,
        letterSpacing: "0.05em",
    },
    heroTitle: {
        fontSize: "clamp(36px, 4.5vw, 56px)",
        fontWeight: 800,
        lineHeight: 1.05,
        letterSpacing: "-0.025em",
        margin: 0,
    },
    heroTitleSoft: { color: "var(--text-primary)" },
    heroTitleAccent: { color: "var(--accent-solid)" },
    heroLead: {
        fontSize: 15,
        color: "var(--text-secondary)",
        lineHeight: 1.65,
        maxWidth: 540,
        margin: 0,
    },
    heroCtas: {
        display: "flex",
        gap: 12,
        marginTop: 6,
        flexWrap: "wrap",
    },
    ctaPrimary: {
        display: "inline-flex",
        alignItems: "center",
        gap: 8,
        padding: "12px 22px",
        borderRadius: 10,
        background: "linear-gradient(135deg, var(--accent-strong, #15803d), var(--accent-solid))",
        color: "#fff",
        textDecoration: "none",
        fontWeight: 700,
        fontSize: 14,
        boxShadow: "0 8px 22px rgba(34,197,94,0.30)",
    },
    ctaSecondary: {
        display: "inline-flex",
        alignItems: "center",
        padding: "12px 22px",
        borderRadius: 10,
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        color: "var(--text-primary)",
        textDecoration: "none",
        fontWeight: 600,
        fontSize: 14,
    },
    heroStats: {
        marginTop: 8,
        display: "flex",
        gap: 36,
        flexWrap: "wrap",
    },
    statItem: { display: "flex", flexDirection: "column" },
    statNum: {
        fontSize: 22,
        fontWeight: 800,
        color: "var(--text-primary)",
        letterSpacing: "-0.02em",
    },
    statLabel: { fontSize: 12, color: "var(--text-muted)" },

    /* Movers card */
    heroRight: { display: "flex" },
    moversCard: {
        flex: 1,
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 14,
        padding: 18,
        display: "flex",
        flexDirection: "column",
        gap: 12,
    },
    moversHead: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
    },
    moversTitle: { fontSize: 14, fontWeight: 700, color: "var(--text-primary)" },
    moversChip: {
        fontSize: 10,
        fontWeight: 700,
        color: "var(--text-muted)",
        letterSpacing: "0.1em",
        padding: "3px 8px",
        borderRadius: 6,
        background: "var(--input-bg)",
    },
    moversList: { listStyle: "none", padding: 0, margin: 0, display: "flex", flexDirection: "column", gap: 8 },
    moversEmpty: { fontSize: 13, color: "var(--text-muted)", padding: "8px 0" },
    moverRow: {
        display: "grid",
        gridTemplateColumns: "32px 1fr auto",
        alignItems: "center",
        gap: 10,
        padding: "8px 10px",
        borderRadius: 8,
        background: "var(--input-bg)",
    },
    moverDot: {
        width: 24, height: 24,
        borderRadius: "50%",
        display: "grid", placeItems: "center",
        color: "#fff", fontSize: 12, fontWeight: 800,
    },
    moverSymbol: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    moverPct: { fontSize: 13, fontWeight: 700, fontVariantNumeric: "tabular-nums" },

    /* Features grid */
    featuresGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
        gap: 14,
    },
    featureCard: {
        display: "flex", alignItems: "center", gap: 14,
        padding: "18px 18px",
        borderRadius: 14,
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        textDecoration: "none",
        color: "inherit",
        transition: "transform 0.15s, border-color 0.15s",
    },
    featureIcon: {
        width: 44, height: 44,
        borderRadius: 11,
        background: "rgba(34,197,94,0.12)",
        display: "grid", placeItems: "center",
        fontSize: 22,
        flexShrink: 0,
    },
    featureTitle: { fontSize: 14.5, fontWeight: 700, color: "var(--text-primary)" },
    featureDesc: { fontSize: 12, color: "var(--text-muted)", marginTop: 2 },

    /* Snapshot grid */
    snapshotGrid: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 2fr) minmax(280px, 1fr)",
        gap: 18,
    },
    bistCard: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 14,
        padding: 20,
        display: "flex",
        flexDirection: "column",
        gap: 14,
    },
    bistHead: {
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "space-between",
        gap: 12,
        flexWrap: "wrap",
    },
    bistTitle: { margin: 0, fontSize: 16, fontWeight: 700, color: "var(--text-primary)" },
    bistSub: { fontSize: 11.5, color: "var(--text-muted)", marginTop: 2 },
    bistFilters: { display: "flex", gap: 6, flexWrap: "wrap" },
    bistFilter: {
        padding: "6px 12px",
        borderRadius: 8,
        background: "var(--input-bg)",
        border: "1px solid var(--border-card)",
        color: "var(--text-muted)",
        fontSize: 12,
        fontWeight: 600,
        cursor: "pointer",
    },
    bistFilterActive: {
        background: "rgba(34,197,94,0.15)",
        color: "var(--accent-solid)",
        borderColor: "var(--accent-solid)",
    },
    bistTable: { width: "100%", borderCollapse: "collapse", fontSize: 13 },
    bistTh: {
        padding: "10px 12px",
        fontSize: 10,
        fontWeight: 700,
        textTransform: "uppercase",
        letterSpacing: "0.06em",
        color: "var(--text-muted)",
        textAlign: "left",
        borderBottom: "1px solid var(--border-soft, var(--border-card))",
    },
    bistTr: { borderBottom: "1px solid var(--border-soft, var(--border-card))" },
    bistTdSym: { padding: "10px 12px", display: "flex", alignItems: "center", gap: 10 },
    bistTag: {
        width: 32, height: 32,
        borderRadius: 8,
        background: "rgba(34,197,94,0.15)",
        color: "var(--accent-solid)",
        display: "grid", placeItems: "center",
        fontSize: 11, fontWeight: 800,
    },
    bistTdName: { fontSize: 11, color: "var(--text-muted)", marginTop: 1 },
    bistTdNum: { padding: "10px 12px", textAlign: "right", fontVariantNumeric: "tabular-nums" },
    bistEmpty: { padding: 24, textAlign: "center", color: "var(--text-muted)" },

    /* Side col */
    sideCol: { display: "flex", flexDirection: "column", gap: 14 },
    sidePanel: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 14,
        padding: 16,
        display: "flex",
        flexDirection: "column",
        gap: 10,
    },
    sidePanelHead: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
    },
    sidePanelTitle: { fontSize: 14, fontWeight: 700, color: "var(--text-primary)" },
    sidePanelChip: {
        fontSize: 10,
        fontWeight: 700,
        color: "var(--text-muted)",
        letterSpacing: "0.1em",
        padding: "3px 8px",
        borderRadius: 6,
        background: "var(--input-bg)",
    },
    sidePanelList: { listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 6 },
    sidePanelEmpty: { fontSize: 12, color: "var(--text-muted)" },
    sidePanelRow: {
        display: "grid",
        gridTemplateColumns: "1fr auto auto",
        gap: 10,
        padding: "8px 10px",
        alignItems: "center",
        borderRadius: 8,
        background: "var(--input-bg)",
        fontSize: 13,
    },
    sidePanelSym: { fontWeight: 700, color: "var(--text-primary)" },
    sidePanelPrice: { fontVariantNumeric: "tabular-nums", color: "var(--text-secondary)" },
    sidePanelPct: { fontWeight: 700, fontSize: 12, fontVariantNumeric: "tabular-nums" },

    /* News section */
    newsSection: { display: "flex", flexDirection: "column", gap: 16 },
    newsHead: { display: "flex", justifyContent: "space-between", alignItems: "baseline" },
    newsTitle: { margin: 0, fontSize: 22, fontWeight: 800, color: "var(--text-primary)" },
    newsAll: { color: "var(--accent-solid)", textDecoration: "none", fontSize: 14, fontWeight: 700 },
    newsGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))",
        gap: 14,
    },
    newsCard: {
        display: "flex",
        flexDirection: "column",
        gap: 10,
        padding: 18,
        borderRadius: 14,
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        textDecoration: "none",
        color: "inherit",
    },
    newsCategory: {
        fontSize: 11,
        fontWeight: 700,
        letterSpacing: "0.1em",
        color: "var(--accent-solid)",
    },
    newsCardTitle: { margin: 0, fontSize: 15, fontWeight: 700, color: "var(--text-primary)", lineHeight: 1.35 },
    newsCardSummary: {
        margin: 0,
        fontSize: 13,
        color: "var(--text-muted)",
        lineHeight: 1.55,
        display: "-webkit-box",
        WebkitLineClamp: 3,
        WebkitBoxOrient: "vertical",
        overflow: "hidden",
    },
    newsRead: { fontSize: 12, fontWeight: 600, color: "var(--accent-solid)", marginTop: "auto" },
    newsCardEmpty: { padding: 24, color: "var(--text-muted)", textAlign: "center" },
};

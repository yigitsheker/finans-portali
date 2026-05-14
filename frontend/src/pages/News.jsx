import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    getNews,
    getNewsCategories,
    getNewsCategoryCounts,
    getMarketSummary,
} from '../api/portfolioApi';

const CATEGORY_LABELS = {
    'genel-ekonomi': 'Genel Ekonomi',
    'hisse': 'Hisse Senetleri',
    'doviz': 'Döviz',
    'tahvil': 'Tahvil & Bono',
    'kripto': 'Kripto Para',
    'emtia': 'Emtia',
    'fonlar': 'Yatırım Fonları',
    'borsa': 'Borsa Haberleri',
    'tcmb': 'TCMB Kararları',
    'uluslararasi': 'Uluslararası Piyasalar',
};

const QUICK_LINKS = [
    { path: '/stocks', label: 'Hisse Senetleri', emoji: '📈', desc: 'BIST + uluslararası hisseler' },
    { path: '/crypto', label: 'Kripto Paralar', emoji: '🪙', desc: 'Anlık kripto fiyatları' },
    { path: '/funds', label: 'Yatırım Fonları', emoji: '💼', desc: 'TEFAS fon performansı' },
    { path: '/bonds', label: 'Tahvil ve Bono', emoji: '🏛️', desc: 'TCMB DİBS ve eurobondlar' },
    { path: '/market-data', label: 'Döviz Kurları', emoji: '💱', desc: 'TCMB güncel kurlar' },
];

const News = ({ keycloak }) => {
    const navigate = useNavigate();
    const [news, setNews] = useState([]);
    const [categories, setCategories] = useState([]);
    const [categoryCounts, setCategoryCounts] = useState({});
    const [selectedCategory, setSelectedCategory] = useState('');
    const [loading, setLoading] = useState(true);
    const [topMovers, setTopMovers] = useState([]);
    const [showScrollTop, setShowScrollTop] = useState(false);

    useEffect(() => {
        loadData();
    }, []);

    useEffect(() => {
        loadNews();
    }, [selectedCategory]);

    useEffect(() => {
        const onScroll = () => setShowScrollTop(window.scrollY > 400);
        window.addEventListener('scroll', onScroll);
        return () => window.removeEventListener('scroll', onScroll);
    }, []);

    const loadData = async () => {
        try {
            const [cats, counts, market] = await Promise.all([
                getNewsCategories(),
                getNewsCategoryCounts(),
                getMarketSummary(),
            ]);
            setCategories(cats);
            setCategoryCounts(counts);
            const movers = market
                .filter((i) => i.type !== 'INDEX')
                .sort((a, b) => Math.abs(b.changePct) - Math.abs(a.changePct))
                .slice(0, 6);
            setTopMovers(movers);
        } catch (error) {
            console.error('[News] loadData error:', error);
        }
    };

    const loadNews = async () => {
        try {
            setLoading(true);
            const data = await getNews(selectedCategory || undefined);
            setNews(data);
        } catch (error) {
            console.error('[News] loadNews error:', error);
        } finally {
            setLoading(false);
        }
    };

    const formatRelativeTime = (dateString) => {
        const date = new Date(dateString);
        const diffMs = Date.now() - date.getTime();
        const diffMin = Math.floor(diffMs / 60_000);
        if (diffMin < 1) return 'Az önce';
        if (diffMin < 60) return `${diffMin} dakika önce`;
        const diffH = Math.floor(diffMin / 60);
        if (diffH < 24) return `${diffH} saat önce`;
        const diffD = Math.floor(diffH / 24);
        if (diffD < 30) return `${diffD} gün önce`;
        return date.toLocaleDateString('tr-TR');
    };

    const openArticle = (article) => navigate(`/news/${article.id}`);

    const featured = news[0];
    const rest = news.slice(1);

    return (
        <div style={s.root} className="home-page">
            {/* Hero / Welcome */}
            <section style={s.hero} className="home-hero">
                <div style={s.heroLeft}>
                    <div style={s.heroBadge}>FİNANS PORTALI</div>
                    <h1 style={s.heroTitle}>Yatırım dünyası tek bir ekranda</h1>
                    <p style={s.heroText}>
                        Hisse, kripto, fon, tahvil, döviz — Türkiye ve dünya piyasalarını
                        canlı verilerle takip edin, haberleri okuyun, portföyünüzü yönetin.
                    </p>
                    <div style={s.heroCtas}>
                        <button style={s.ctaPrimary} onClick={() => navigate('/stocks')}>
                            Piyasaları Keşfet
                        </button>
                        {!keycloak?.authenticated && (
                            <button
                                style={s.ctaSecondary}
                                onClick={() =>
                                    keycloak?.register({ redirectUri: window.location.href })
                                }
                            >
                                Ücretsiz Hesap Oluştur
                            </button>
                        )}
                        {keycloak?.authenticated && (
                            <button
                                style={s.ctaSecondary}
                                onClick={() => navigate('/portfolio')}
                            >
                                Portföyümü Görüntüle
                            </button>
                        )}
                    </div>
                </div>
                <div style={s.heroRight}>
                    <div style={s.heroStatsTitle}>Günün Hareketlileri</div>
                    {topMovers.length === 0 ? (
                        <div style={s.heroStatsEmpty}>Yükleniyor...</div>
                    ) : (
                        <div style={s.heroStatsList}>
                            {topMovers.slice(0, 4).map((m) => {
                                const positive = m.changePct >= 0;
                                return (
                                    <div key={m.symbol} style={s.heroStatRow}>
                                        <div style={s.heroStatSymbol}>{m.symbol}</div>
                                        <div
                                            style={{
                                                ...s.heroStatChange,
                                                color: positive ? '#10b981' : '#ef4444',
                                            }}
                                        >
                                            {positive ? '▲' : '▼'} {positive ? '+' : ''}
                                            {m.changePct.toFixed(2)}%
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </section>

            {/* Quick links */}
            <section style={s.quickGrid} className="home-quick-grid">
                {QUICK_LINKS.map((link) => (
                    <button
                        key={link.path}
                        style={s.quickCard}
                        onClick={() => navigate(link.path)}
                    >
                        <div style={s.quickEmoji}>{link.emoji}</div>
                        <div style={s.quickLabel}>{link.label}</div>
                        <div style={s.quickDesc}>{link.desc}</div>
                    </button>
                ))}
            </section>

            {/* Main grid: news + sidebar */}
            <section style={s.mainGrid} className="home-main-grid">
                {/* Left: news feed */}
                <div style={s.newsCol}>
                    <div style={s.sectionHeader}>
                        <h2 style={s.sectionTitle}>Finans Haberleri</h2>
                        <span style={s.sectionMeta}>
                            {selectedCategory
                                ? CATEGORY_LABELS[selectedCategory] ?? selectedCategory
                                : 'Tüm haberler'}
                        </span>
                    </div>

                    {loading && news.length === 0 ? (
                        <div style={s.loadingState}>
                            <div style={s.spinner} />
                            <span>Haberler yükleniyor...</span>
                        </div>
                    ) : news.length === 0 ? (
                        <div style={s.emptyState}>Bu kategoride henüz haber yok.</div>
                    ) : (
                        <>
                            {/* Featured news card */}
                            {featured && (
                                <article
                                    style={s.featured}
                                    onClick={() => openArticle(featured)}
                                    role="link"
                                    tabIndex={0}
                                    onKeyDown={(e) => { if (e.key === 'Enter') openArticle(featured); }}
                                >
                                    <div style={s.featuredMeta}>
                                        <span style={s.featuredCategory}>
                                            {CATEGORY_LABELS[featured.category] ?? featured.category}
                                        </span>
                                        <span style={s.dot}>•</span>
                                        <span style={s.muted}>{featured.sourceName || 'Piyasalar'}</span>
                                        <span style={s.dot}>•</span>
                                        <span style={s.muted}>{formatRelativeTime(featured.publishedAt)}</span>
                                    </div>
                                    <h3 style={s.featuredTitle}>{featured.title}</h3>
                                    <p style={s.featuredSummary}>{featured.summary}</p>
                                    <span style={s.readMore}>Devamını Oku →</span>
                                </article>
                            )}

                            {/* Other news */}
                            <div style={s.list}>
                                {rest.map((a) => (
                                    <article
                                        key={a.id}
                                        style={s.listItem}
                                        onClick={() => openArticle(a)}
                                        role="link"
                                        tabIndex={0}
                                        onKeyDown={(e) => { if (e.key === 'Enter') openArticle(a); }}
                                    >
                                        <div style={s.listMeta}>
                                            <span style={s.listCategory}>
                                                {CATEGORY_LABELS[a.category] ?? a.category}
                                            </span>
                                            <span style={s.dot}>•</span>
                                            <span style={s.muted}>{a.sourceName || 'Piyasalar'}</span>
                                            <span style={s.dot}>•</span>
                                            <span style={s.muted}>{formatRelativeTime(a.publishedAt)}</span>
                                        </div>
                                        <h4 style={s.listTitle}>{a.title}</h4>
                                        <p style={s.listSummary}>{a.summary}</p>
                                    </article>
                                ))}
                            </div>
                        </>
                    )}
                </div>

                {/* Right: sidebar */}
                <aside style={s.sidebar} className="home-sidebar">
                    {/* Top movers */}
                    <div style={s.sideCard}>
                        <h3 style={s.sideTitle}>📊 Günün En Çok Değişenleri</h3>
                        <div style={s.sideList}>
                            {topMovers.length === 0 ? (
                                <div style={s.muted}>Yükleniyor...</div>
                            ) : (
                                topMovers.map((m) => {
                                    const positive = m.changePct >= 0;
                                    return (
                                        <div key={m.symbol} style={s.moverRow}>
                                            <div>
                                                <div style={s.moverSymbol}>{m.symbol}</div>
                                                <div style={s.moverName}>{m.name}</div>
                                            </div>
                                            <div
                                                style={{
                                                    ...s.moverChange,
                                                    color: positive ? '#10b981' : '#ef4444',
                                                }}
                                            >
                                                {positive ? '▲' : '▼'} {positive ? '+' : ''}
                                                {m.changePct.toFixed(2)}%
                                            </div>
                                        </div>
                                    );
                                })
                            )}
                        </div>
                    </div>

                    {/* Categories */}
                    <div style={s.sideCard}>
                        <h3 style={s.sideTitle}>📰 Kategoriler</h3>
                        <div style={s.categoryList}>
                            <button
                                style={{
                                    ...s.categoryBtn,
                                    ...(selectedCategory === '' ? s.categoryBtnActive : {}),
                                }}
                                onClick={() => setSelectedCategory('')}
                            >
                                <span>Tüm Haberler</span>
                                <span style={s.catCount}>{categoryCounts['all'] || 0}</span>
                            </button>
                            {categories.map((cat) => (
                                <button
                                    key={cat}
                                    style={{
                                        ...s.categoryBtn,
                                        ...(selectedCategory === cat ? s.categoryBtnActive : {}),
                                    }}
                                    onClick={() => setSelectedCategory(cat)}
                                >
                                    <span>{CATEGORY_LABELS[cat] ?? cat}</span>
                                    <span style={s.catCount}>{categoryCounts[cat] || 0}</span>
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* CTA card for non-authenticated */}
                    {!keycloak?.authenticated && (
                        <div style={s.ctaCard}>
                            <div style={s.ctaIcon}>🔐</div>
                            <h3 style={s.ctaTitle}>Yatırımcı Hesabı</h3>
                            <p style={s.ctaText}>
                                Portföyünüzü takip edin, fiyat alarmları kurun, teknik analiz
                                araçlarını kullanın.
                            </p>
                            <button
                                style={s.ctaCardBtn}
                                onClick={() =>
                                    keycloak?.register({ redirectUri: window.location.href })
                                }
                            >
                                Ücretsiz Kayıt Ol
                            </button>
                        </div>
                    )}
                </aside>
            </section>

            {showScrollTop && (
                <button
                    onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
                    style={s.scrollTopBtn}
                    aria-label="Yukarı çık"
                >
                    ↑
                </button>
            )}
        </div>
    );
};

const s = {
    root: {
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
        paddingBottom: 32,
    },

    // Hero
    hero: {
        display: 'grid',
        gridTemplateColumns: '1.5fr 1fr',
        gap: 24,
        padding: 32,
        background:
            'linear-gradient(135deg, var(--accent-hover-bg) 0%, var(--bg-card) 60%)',
        border: '1px solid var(--border-card)',
        borderRadius: 18,
        position: 'relative',
        overflow: 'hidden',
    },
    heroLeft: { display: 'flex', flexDirection: 'column', gap: 14, minWidth: 0 },
    heroBadge: {
        alignSelf: 'flex-start',
        padding: '4px 12px',
        background: 'var(--accent-solid)',
        color: '#000',
        borderRadius: 999,
        fontSize: 11,
        fontWeight: 800,
        letterSpacing: 1,
    },
    heroTitle: {
        margin: 0,
        fontSize: 32,
        fontWeight: 800,
        color: 'var(--text-primary)',
        letterSpacing: '-0.02em',
        lineHeight: 1.15,
    },
    heroText: {
        margin: 0,
        fontSize: 15,
        color: 'var(--text-muted)',
        lineHeight: 1.6,
        maxWidth: 540,
    },
    heroCtas: { display: 'flex', gap: 10, marginTop: 8, flexWrap: 'wrap' },
    ctaPrimary: {
        padding: '11px 22px',
        background: 'var(--accent-solid)',
        color: '#000',
        border: 'none',
        borderRadius: 10,
        fontSize: 14,
        fontWeight: 700,
        cursor: 'pointer',
        transition: 'transform 0.15s, box-shadow 0.15s',
    },
    ctaSecondary: {
        padding: '11px 22px',
        background: 'transparent',
        color: 'var(--text-primary)',
        border: '1px solid var(--border-card)',
        borderRadius: 10,
        fontSize: 14,
        fontWeight: 600,
        cursor: 'pointer',
    },
    heroRight: {
        background: 'var(--bg-panel)',
        border: '1px solid var(--border-card)',
        borderRadius: 12,
        padding: 16,
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
    },
    heroStatsTitle: { fontSize: 12, fontWeight: 700, color: 'var(--text-muted)', letterSpacing: 0.5 },
    heroStatsList: { display: 'flex', flexDirection: 'column', gap: 8 },
    heroStatsEmpty: { color: 'var(--text-muted)', fontSize: 12 },
    heroStatRow: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '6px 0',
        borderBottom: '1px solid var(--border-card)',
    },
    heroStatSymbol: { fontSize: 13, fontWeight: 700, color: 'var(--text-primary)' },
    heroStatChange: { fontSize: 13, fontWeight: 700 },

    // Quick grid
    quickGrid: {
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12,
    },
    quickCard: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'flex-start',
        gap: 8,
        padding: 18,
        background: 'var(--bg-card)',
        border: '1px solid var(--border-card)',
        borderRadius: 12,
        cursor: 'pointer',
        textAlign: 'left',
        transition: 'border-color 0.15s, transform 0.15s',
    },
    quickEmoji: { fontSize: 28 },
    quickLabel: {
        fontSize: 15,
        fontWeight: 700,
        color: 'var(--text-primary)',
    },
    quickDesc: {
        fontSize: 12,
        color: 'var(--text-muted)',
        lineHeight: 1.4,
    },

    // Main grid
    mainGrid: {
        display: 'grid',
        gridTemplateColumns: '1fr 320px',
        gap: 20,
        alignItems: 'flex-start',
    },
    newsCol: { display: 'flex', flexDirection: 'column', gap: 14, minWidth: 0 },

    sectionHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'baseline',
        marginBottom: 4,
    },
    sectionTitle: { margin: 0, fontSize: 20, fontWeight: 700, color: 'var(--text-primary)' },
    sectionMeta: { fontSize: 12, color: 'var(--text-muted)' },

    featured: {
        background: 'var(--bg-card)',
        border: '1px solid var(--border-card)',
        borderRadius: 14,
        padding: 22,
        cursor: 'pointer',
        transition: 'border-color 0.15s',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
    },
    featuredMeta: { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
    featuredCategory: {
        fontSize: 11,
        fontWeight: 700,
        color: 'var(--accent-solid)',
        textTransform: 'uppercase',
        letterSpacing: 0.5,
    },
    featuredTitle: {
        margin: 0,
        fontSize: 22,
        fontWeight: 700,
        color: 'var(--text-primary)',
        lineHeight: 1.3,
    },
    featuredSummary: {
        margin: 0,
        fontSize: 14,
        color: 'var(--text-muted)',
        lineHeight: 1.6,
    },
    readMore: {
        fontSize: 13,
        fontWeight: 600,
        color: '#3b82f6',
    },

    list: { display: 'flex', flexDirection: 'column', gap: 10 },
    listItem: {
        background: 'var(--bg-card)',
        border: '1px solid var(--border-card)',
        borderRadius: 12,
        padding: 16,
        cursor: 'pointer',
        transition: 'border-color 0.15s',
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
    },
    listMeta: { display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' },
    listCategory: {
        fontSize: 10,
        fontWeight: 700,
        color: 'var(--accent-solid)',
        textTransform: 'uppercase',
        letterSpacing: 0.5,
    },
    listTitle: { margin: 0, fontSize: 15, fontWeight: 600, color: 'var(--text-primary)', lineHeight: 1.4 },
    listSummary: {
        margin: 0,
        fontSize: 13,
        color: 'var(--text-muted)',
        lineHeight: 1.5,
        display: '-webkit-box',
        WebkitLineClamp: 2,
        WebkitBoxOrient: 'vertical',
        overflow: 'hidden',
    },

    // Sidebar
    sidebar: {
        display: 'flex',
        flexDirection: 'column',
        gap: 14,
        position: 'sticky',
        top: 12,
    },
    sideCard: {
        background: 'var(--bg-card)',
        border: '1px solid var(--border-card)',
        borderRadius: 12,
        padding: 18,
    },
    sideTitle: {
        margin: 0,
        marginBottom: 12,
        fontSize: 14,
        fontWeight: 700,
        color: 'var(--text-primary)',
    },
    sideList: { display: 'flex', flexDirection: 'column', gap: 10 },
    moverRow: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: 6,
    },
    moverSymbol: { fontSize: 13, fontWeight: 700, color: 'var(--text-primary)' },
    moverName: { fontSize: 11, color: 'var(--text-muted)', marginTop: 1 },
    moverChange: { fontSize: 13, fontWeight: 700 },

    categoryList: { display: 'flex', flexDirection: 'column', gap: 5 },
    categoryBtn: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '9px 12px',
        background: 'transparent',
        border: '1px solid var(--border-card)',
        borderRadius: 8,
        color: 'var(--text-primary)',
        fontSize: 12,
        fontWeight: 500,
        cursor: 'pointer',
        textAlign: 'left',
        transition: 'all 0.15s',
    },
    categoryBtnActive: {
        background: 'var(--accent-hover-bg)',
        borderColor: 'var(--accent-solid)',
        color: 'var(--accent-solid)',
    },
    catCount: {
        fontSize: 10,
        fontWeight: 600,
        color: 'var(--text-muted)',
        background: 'var(--bg-panel)',
        padding: '2px 8px',
        borderRadius: 999,
    },

    ctaCard: {
        background:
            'linear-gradient(135deg, var(--accent-hover-bg) 0%, var(--bg-card) 100%)',
        border: '1px solid var(--accent-solid)',
        borderRadius: 12,
        padding: 18,
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
    },
    ctaIcon: { fontSize: 28 },
    ctaTitle: { margin: 0, fontSize: 15, fontWeight: 700, color: 'var(--text-primary)' },
    ctaText: { margin: 0, fontSize: 12, color: 'var(--text-muted)', lineHeight: 1.5 },
    ctaCardBtn: {
        marginTop: 6,
        padding: '10px 14px',
        background: 'var(--accent-solid)',
        color: '#000',
        border: 'none',
        borderRadius: 8,
        fontSize: 13,
        fontWeight: 700,
        cursor: 'pointer',
    },

    // Shared
    dot: { fontSize: 12, color: 'var(--text-muted)' },
    muted: { fontSize: 12, color: 'var(--text-muted)' },

    loadingState: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        padding: 60,
        color: 'var(--text-muted)',
        fontSize: 13,
    },
    spinner: {
        width: 36,
        height: 36,
        border: '3px solid var(--border-card)',
        borderTopColor: 'var(--accent-solid)',
        borderRadius: '50%',
        animation: 'spin 0.8s linear infinite',
    },
    emptyState: {
        padding: 40,
        textAlign: 'center',
        color: 'var(--text-muted)',
        fontSize: 13,
        background: 'var(--bg-card)',
        border: '1px solid var(--border-card)',
        borderRadius: 12,
    },
    scrollTopBtn: {
        position: 'fixed',
        bottom: 32,
        right: 32,
        width: 50,
        height: 50,
        borderRadius: '50%',
        background: 'var(--accent-solid)',
        color: '#000',
        border: 'none',
        fontSize: 22,
        fontWeight: 700,
        cursor: 'pointer',
        boxShadow: '0 4px 16px rgba(0,0,0,0.3)',
        zIndex: 1000,
    },
};

export default News;

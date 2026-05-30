import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    getNews,
    getNewsCategories,
    getMarketSummary,
} from '../api/portfolioApi';
import Pagination from '../components/common/Pagination';
import { useI18n } from '../contexts/I18nContext';

const NEWS_PAGE_SIZE = 10;

const CATEGORY_LABEL_KEYS = {
    'genel-ekonomi': 'news.catGeneral',
    'hisse': 'news.catStocks',
    'doviz': 'news.catFx',
    'tahvil': 'news.catBonds',
    'kripto': 'news.catCrypto',
    'emtia': 'news.catCommodities',
    'fonlar': 'news.catFunds',
    'borsa': 'news.catBist',
    'tcmb': 'news.catTcmb',
    'uluslararasi': 'news.catIntl',
};

const News = ({ keycloak }) => {
    const navigate = useNavigate();
    const { t, lang } = useI18n();
    const [news, setNews] = useState([]);
    const [categories, setCategories] = useState([]);
    const [selectedCategory, setSelectedCategory] = useState('');
    const [newsPage, setNewsPage] = useState(1);
    const [loading, setLoading] = useState(true);
    const [topMovers, setTopMovers] = useState([]);
    // Scroll-to-top is now mounted globally in App.jsx via <ScrollToTop />,
    // so the per-page state/listener has been removed.

    useEffect(() => {
        loadData();
    }, []);

    useEffect(() => {
        loadNews();
    }, [selectedCategory, lang]);

    // Reset to first page whenever the active category changes; staying
    // on page 4 of a category that only returned 2 pages looks broken.
    useEffect(() => { setNewsPage(1); }, [selectedCategory]);

    const loadData = async () => {
        try {
            const [cats, market] = await Promise.all([
                getNewsCategories(),
                getMarketSummary(),
            ]);
            setCategories(cats);
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
            const data = await getNews(selectedCategory || undefined, lang);
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
        if (diffMin < 1) return t('news.justNow');
        if (diffMin < 60) return t('news.minutesAgo', { N: diffMin });
        const diffH = Math.floor(diffMin / 60);
        if (diffH < 24) return t('news.hoursAgo', { N: diffH });
        const diffD = Math.floor(diffH / 24);
        if (diffD < 30) return t('news.daysAgo', { N: diffD });
        return date.toLocaleDateString(lang === 'en' ? 'en-US' : 'tr-TR');
    };

    const openArticle = (article) => navigate(`/news/${article.id}`);

    // Featured = the most recent article; the rest of the feed is paginated
    // so the page stays scannable when the backend returns 100+ articles.
    const featured = news[0];
    const restAll = news.slice(1);
    const totalRest = restAll.length;
    const restStart = (newsPage - 1) * NEWS_PAGE_SIZE;
    const rest = restAll.slice(restStart, restStart + NEWS_PAGE_SIZE);

    return (
        <div style={s.root} className="home-page">
            {/* Hero / Welcome */}
            <section style={s.hero} className="home-hero">
                <div style={s.heroLeft}>
                    <div style={s.heroBadge}>FİNANS PORTALI</div>
                    <h1 style={s.heroTitle}>{t('home.heroAltTitle')}</h1>
                    <p style={s.heroText}>
                        {t('home.heroAltLead')}
                    </p>
                    <div style={s.heroCtas}>
                        <button style={s.ctaPrimary} onClick={() => navigate('/stocks')}>
                            {t('home.ctaExplore')}
                        </button>
                        {!keycloak?.authenticated && (
                            <button
                                style={s.ctaSecondary}
                                onClick={() =>
                                    keycloak?.register({ redirectUri: window.location.href })
                                }
                            >
                                {t('home.ctaRegister')}
                            </button>
                        )}
                        {keycloak?.authenticated && (
                            <button
                                style={s.ctaSecondary}
                                onClick={() => navigate('/portfolio')}
                            >
                                {t('home.ctaPortfolio')}
                            </button>
                        )}
                    </div>
                </div>
                <div style={s.heroRight}>
                    <div style={s.heroStatsTitle}>{t('home.moversTitle')}</div>
                    {topMovers.length === 0 ? (
                        <div style={s.heroStatsEmpty}>{t('common.loading')}</div>
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


            {/* Main grid: news + sidebar */}
            <section style={s.mainGrid} className="home-main-grid fp-news-page">
                {/* Left: news feed */}
                <div style={s.newsCol}>
                    <div style={s.sectionHeader}>
                        <h2 style={s.sectionTitle}>{t('news.title')}</h2>
                        <span style={s.sectionMeta}>
                            {selectedCategory
                                ? (CATEGORY_LABEL_KEYS[selectedCategory] ? t(CATEGORY_LABEL_KEYS[selectedCategory]) : selectedCategory)
                                : t('news.all')}
                        </span>
                    </div>

                    {loading && news.length === 0 ? (
                        <div style={s.loadingState}>
                            <div style={s.spinner} />
                            <span>{t('news.loading')}</span>
                        </div>
                    ) : news.length === 0 ? (
                        <div style={s.emptyState}>{t('news.emptyCat')}</div>
                    ) : (
                        <>
                            {/* Featured news card — <button> wraps an <article>
                                for both interactive semantics (Sonar S6843)
                                and native keyboard/focus handling. */}
                            {featured && (
                                <button
                                    type="button"
                                    onClick={() => openArticle(featured)}
                                    style={{ all: "unset", display: "block", width: "100%", cursor: "pointer", textAlign: "left" }}
                                >
                                    <article style={s.featured}>
                                        <div style={s.featuredMeta}>
                                            <span style={s.featuredCategory}>
                                                {CATEGORY_LABEL_KEYS[featured.category] ? t(CATEGORY_LABEL_KEYS[featured.category]) : featured.category}
                                            </span>
                                            <span style={s.dot}>•</span>
                                            <span style={s.muted}>{featured.sourceName || t('news.markets')}</span>
                                            <span style={s.dot}>•</span>
                                            <span style={s.muted}>{formatRelativeTime(featured.publishedAt)}</span>
                                        </div>
                                        <h3 style={s.featuredTitle}>{featured.title}</h3>
                                        <p style={s.featuredSummary}>{featured.summary}</p>
                                        <span style={s.readMore}>{t('news.readMore')}</span>
                                    </article>
                                </button>
                            )}

                            {/* Other news — paginated below */}
                            <div style={s.list}>
                                {rest.map((a) => (
                                    <button
                                        key={a.id}
                                        type="button"
                                        onClick={() => openArticle(a)}
                                        style={{ all: "unset", display: "block", width: "100%", cursor: "pointer", textAlign: "left" }}
                                    >
                                        <article style={s.listItem}>
                                            <div style={s.listMeta}>
                                                <span style={s.listCategory}>
                                                    {CATEGORY_LABEL_KEYS[a.category] ? t(CATEGORY_LABEL_KEYS[a.category]) : a.category}
                                                </span>
                                                <span style={s.dot}>•</span>
                                                <span style={s.muted}>{a.sourceName || t('news.markets')}</span>
                                                <span style={s.dot}>•</span>
                                                <span style={s.muted}>{formatRelativeTime(a.publishedAt)}</span>
                                            </div>
                                            <h4 style={s.listTitle}>{a.title}</h4>
                                            <p style={s.listSummary}>{a.summary}</p>
                                        </article>
                                    </button>
                                ))}
                            </div>
                            {totalRest > NEWS_PAGE_SIZE && (
                                <Pagination
                                    page={newsPage}
                                    pageSize={NEWS_PAGE_SIZE}
                                    total={totalRest}
                                    onPageChange={setNewsPage}
                                />
                            )}
                        </>
                    )}
                </div>

                {/* Right: sidebar */}
                <aside style={s.sidebar} className="home-sidebar">
                    {/* Categories */}
                    <div style={s.sideCard}>
                        <h3 style={s.sideTitle}>{t('news.categoriesTitle')}</h3>
                        <div style={s.categoryList}>
                            <button
                                className="fp-category-btn"
                                style={{
                                    ...s.categoryBtn,
                                    ...(selectedCategory === '' ? s.categoryBtnActive : {}),
                                }}
                                onClick={() => setSelectedCategory('')}
                            >
                                {t('news.allNews')}
                            </button>
                            {categories.map((cat) => (
                                <button
                                    key={cat}
                                    className="fp-category-btn"
                                    style={{
                                        ...s.categoryBtn,
                                        ...(selectedCategory === cat ? s.categoryBtnActive : {}),
                                    }}
                                    onClick={() => setSelectedCategory(cat)}
                                >
                                    {CATEGORY_LABEL_KEYS[cat] ? t(CATEGORY_LABEL_KEYS[cat]) : cat}
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* CTA card for non-authenticated */}
                    {!keycloak?.authenticated && (
                        <div style={s.ctaCard}>
                            <div style={s.ctaIcon}>🔐</div>
                            <h3 style={s.ctaTitle}>{t('home.investorBadge')}</h3>
                            <p style={s.ctaText}>
                                {t('home.investorBadgeSub')}
                            </p>
                            <button
                                style={s.ctaCardBtn}
                                onClick={() =>
                                    keycloak?.register({ redirectUri: window.location.href })
                                }
                            >
                                {t('home.ctaRegister')}
                            </button>
                        </div>
                    )}
                </aside>
            </section>

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
        padding: '10px 12px',
        background: 'transparent',
        // No border at all — the active state expresses selection through
        // background colour and bold weight, which avoids any user-agent or
        // inherited border bleed.
        border: 'none',
        borderRadius: 8,
        color: 'var(--text-primary)',
        fontSize: 12,
        fontWeight: 500,
        cursor: 'pointer',
        textAlign: 'left',
        transition: 'all 0.15s',
        outline: 'none',
        boxShadow: 'none',
        WebkitTapHighlightColor: 'transparent',
    },
    categoryBtnActive: {
        background: 'var(--accent-hover-bg)',
        color: 'var(--accent-solid)',
        fontWeight: 700,
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

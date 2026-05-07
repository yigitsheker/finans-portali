import React, { useState, useEffect } from 'react';
import { getNews, getNewsCategories, getNewsCategoryCounts, getMarketSummary, type NewsArticle, type MarketSummaryItem } from '../api/portfolioApi';
import NewsDetail from './NewsDetail';

const News: React.FC = () => {
    const [news, setNews] = useState<NewsArticle[]>([]);
    const [categories, setCategories] = useState<string[]>([]);
    const [categoryCounts, setCategoryCounts] = useState<Record<string, number>>({});
    const [selectedCategory, setSelectedCategory] = useState<string>('');
    const [loading, setLoading] = useState(true);
    const [topMovers, setTopMovers] = useState<MarketSummaryItem[]>([]);
    const [selectedArticle, setSelectedArticle] = useState<NewsArticle | null>(null);
    const [showScrollTop, setShowScrollTop] = useState(false);

    useEffect(() => {
        loadData();
    }, []);

    useEffect(() => {
        loadNews();
    }, [selectedCategory]);

    // Scroll listener for scroll-to-top button
    useEffect(() => {
        const handleScroll = () => {
            setShowScrollTop(window.scrollY > 400);
        };

        window.addEventListener('scroll', handleScroll);
        return () => window.removeEventListener('scroll', handleScroll);
    }, []);

    const loadData = async () => {
        try {
            const [categoriesData, countsData, marketData] = await Promise.all([
                getNewsCategories(),
                getNewsCategoryCounts(),
                getMarketSummary()
            ]);
            setCategories(categoriesData);
            setCategoryCounts(countsData);
            
            // Get top 5 movers (by absolute change percentage)
            const movers = marketData
                .filter(item => item.type !== 'INDEX')
                .sort((a, b) => Math.abs(b.changePct) - Math.abs(a.changePct))
                .slice(0, 5);
            setTopMovers(movers);
        } catch (error) {
            console.error('Error loading categories:', error);
        }
    };

    const loadNews = async () => {
        try {
            setLoading(true);
            const newsData = await getNews(selectedCategory || undefined);
            setNews(newsData);
        } catch (error) {
            console.error('Error loading news:', error);
        } finally {
            setLoading(false);
        }
    };

    const formatDate = (dateString: string) => {
        const date = new Date(dateString);
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
        
        if (diffHours < 1) {
            const diffMins = Math.floor(diffMs / (1000 * 60));
            return `${diffMins} dakika önce`;
        } else if (diffHours < 24) {
            return `${diffHours} saat önce`;
        } else {
            const diffDays = Math.floor(diffHours / 24);
            return `${diffDays} gün önce`;
        }
    };

    const getCategoryDisplayName = (category: string) => {
        const categoryMap: { [key: string]: string } = {
            'genel-ekonomi': 'Genel Ekonomi',
            'hisse': 'Hisse Senetleri',
            'doviz': 'Döviz',
            'tahvil': 'Tahvil & Bono',
            'kripto': 'Kripto Para',
            'emtia': 'Emtia',
            'fonlar': 'Yatırım Fonları',
            'borsa': 'Borsa Haberleri',
            'tcmb': 'TCMB Kararları',
            'uluslararasi': 'Uluslararası Piyasalar'
        };
        return categoryMap[category] || category;
    };

    const getCategoryCount = (category: string) => {
        return categoryCounts[category] || 0;
    };

    const scrollToTop = () => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    };

    // Show detail view if an article is selected
    if (selectedArticle) {
        return (
            <NewsDetail
                article={selectedArticle}
                onBack={() => setSelectedArticle(null)}
            />
        );
    }

    if (loading && news.length === 0) {
        return (
            <div style={s.loading}>
                <div style={s.spinner}></div>
                <div style={{ color: "var(--text-muted)", marginTop: 12 }}>Haberler yükleniyor...</div>
            </div>
        );
    }

    const featuredNews = news[0];
    const otherNews = news.slice(1);

    return (
        <div style={s.root}>
            <div style={s.header}>
                <h1 style={s.title}>Finans Haberleri</h1>
                <p style={s.subtitle}>Piyasalar, ekonomi ve yatırım dünyasından son haberler</p>
            </div>

            <div style={s.mainLayout}>
                {/* Left: News Feed */}
                <div style={s.leftPanel}>
                    {/* Featured News */}
                    {featuredNews && (
                        <div style={s.featuredCard}>
                            <div style={s.featuredImagePlaceholder}>
                                <span style={{ fontSize: 48 }}>📰</span>
                            </div>
                            <div style={s.featuredContent}>
                                <div style={s.featuredMeta}>
                                    <span style={s.featuredSource}>{featuredNews.sourceName || 'Piyasalar'}</span>
                                    <span style={s.featuredDot}>•</span>
                                    <span style={s.featuredTime}>{formatDate(featuredNews.publishedAt)}</span>
                                </div>
                                <h2 style={s.featuredTitle}>{featuredNews.title}</h2>
                                <p style={s.featuredSummary}>{featuredNews.summary}</p>
                                <button
                                    onClick={() => setSelectedArticle(featuredNews)}
                                    style={s.readMore}
                                >
                                    <span>Devamını Oku</span>
                                    <span className="news-read-more-arrow" style={s.readMoreArrow}>→</span>
                                </button>
                            </div>
                        </div>
                    )}

                    {/* Other News */}
                    <div style={s.newsGrid}>
                        {otherNews.map((article) => (
                            <div key={article.id} style={s.newsCard}>
                                <div style={s.newsImagePlaceholder}>
                                    <span style={{ fontSize: 32 }}>📄</span>
                                </div>
                                <div style={s.newsContent}>
                                    <div style={s.newsMeta}>
                                        <span style={s.newsSource}>{article.sourceName || 'Piyasalar'}</span>
                                        <span style={s.newsDot}>•</span>
                                        <span style={s.newsTime}>{formatDate(article.publishedAt)}</span>
                                    </div>
                                    <h3 style={s.newsTitle}>{article.title}</h3>
                                    <p style={s.newsSummary}>{article.summary}</p>
                                    <button
                                        onClick={() => setSelectedArticle(article)}
                                        style={s.newsLink}
                                    >
                                        <span>Devamını Oku</span>
                                        <span className="news-read-more-arrow" style={s.newsLinkArrow}>→</span>
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Right: Sidebar */}
                <div style={s.rightPanel}>
                    {/* Top Movers */}
                    <div style={s.sidebarCard}>
                        <h3 style={s.sidebarTitle}>Günün En Çok Değişenleri</h3>
                        <div style={s.moversList}>
                            {topMovers.map((mover) => {
                                const positive = mover.changePct >= 0;
                                const color = positive ? "#10b981" : "#ef4444";
                                return (
                                    <div key={mover.symbol} style={s.moverItem}>
                                        <div style={s.moverLeft}>
                                            <div style={s.moverSymbol}>{mover.symbol}</div>
                                            <div style={s.moverName}>{mover.name}</div>
                                        </div>
                                        <div style={{ ...s.moverChange, color }}>
                                            {positive ? "▲" : "▼"} {positive ? "+" : ""}
                                            {mover.changePct.toFixed(2)}%
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>

                    {/* Categories */}
                    <div style={s.sidebarCard}>
                        <h3 style={s.sidebarTitle}>Kategoriler</h3>
                        <div style={s.categoriesList}>
                            <button
                                style={{
                                    ...s.categoryItem,
                                    ...(selectedCategory === '' ? s.categoryActive : {})
                                }}
                                onClick={() => setSelectedCategory('')}
                            >
                                <span>Tüm Haberler</span>
                                <span style={s.categoryCount}>{categoryCounts['all'] || 0}</span>
                            </button>
                            {categories.map((category) => {
                                const count = getCategoryCount(category);
                                return (
                                    <button
                                        key={category}
                                        style={{
                                            ...s.categoryItem,
                                            ...(selectedCategory === category ? s.categoryActive : {})
                                        }}
                                        onClick={() => setSelectedCategory(category)}
                                    >
                                        <span>{getCategoryDisplayName(category)}</span>
                                        <span style={s.categoryCount}>{count}</span>
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                </div>
            </div>

            {/* Scroll to Top Button */}
            {showScrollTop && (
                <button 
                    onClick={scrollToTop} 
                    style={s.scrollTopButton} 
                    className="news-scroll-top-button"
                    aria-label="Yukarı çık"
                >
                    <span style={s.scrollTopIcon}>↑</span>
                </button>
            )}
        </div>
    );
};

const s: Record<string, React.CSSProperties> = {
    root: { display: "flex", flexDirection: "column", gap: 20 },
    loading: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: 400,
    },
    spinner: {
        width: 40,
        height: 40,
        border: "3px solid var(--border)",
        borderTop: "3px solid #3b82f6",
        borderRadius: "50%",
        animation: "spin 0.8s linear infinite",
    },
    header: { marginBottom: 8 },
    title: { fontSize: 28, fontWeight: 700, color: "var(--text-primary)", marginBottom: 4 },
    subtitle: { fontSize: 14, color: "var(--text-muted)" },
    mainLayout: {
        display: "grid",
        gridTemplateColumns: "1fr 320px",
        gap: 20,
    },
    leftPanel: { display: "flex", flexDirection: "column", gap: 16 },
    featuredCard: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 12,
        overflow: "hidden",
    },
    featuredImagePlaceholder: {
        height: 280,
        background: "linear-gradient(135deg, #1a1f2e 0%, #2d3548 100%)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
    },
    featuredContent: { padding: 24 },
    featuredMeta: { display: "flex", alignItems: "center", gap: 8, marginBottom: 12 },
    featuredSource: { fontSize: 12, color: "#10b981", fontWeight: 600 },
    featuredDot: { fontSize: 12, color: "var(--text-muted)" },
    featuredTime: { fontSize: 12, color: "var(--text-muted)" },
    featuredTitle: { fontSize: 24, fontWeight: 700, color: "var(--text-primary)", marginBottom: 12, lineHeight: 1.3 },
    featuredSummary: { fontSize: 14, color: "var(--text-muted)", lineHeight: 1.6, marginBottom: 16 },
    readMore: {
        display: "inline-flex",
        alignItems: "center",
        gap: 4,
        fontSize: 14,
        fontWeight: 600,
        color: "#3b82f6",
        background: "transparent",
        border: "none",
        padding: 0,
        cursor: "pointer",
        textDecoration: "none",
        transition: "gap 0.2s",
    },
    readMoreArrow: {
        transition: "transform 0.2s",
    },
    newsGrid: { display: "flex", flexDirection: "column", gap: 12 },
    newsCard: {
        display: "grid",
        gridTemplateColumns: "120px 1fr",
        gap: 16,
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        padding: 16,
        cursor: "pointer",
        transition: "border-color 0.2s",
    },
    newsImagePlaceholder: {
        width: 120,
        height: 90,
        background: "linear-gradient(135deg, #1a1f2e 0%, #2d3548 100%)",
        borderRadius: 8,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
    },
    newsContent: { display: "flex", flexDirection: "column", gap: 6 },
    newsMeta: { display: "flex", alignItems: "center", gap: 6 },
    newsSource: { fontSize: 11, color: "#10b981", fontWeight: 600 },
    newsDot: { fontSize: 11, color: "var(--text-muted)" },
    newsTime: { fontSize: 11, color: "var(--text-muted)" },
    newsTitle: { fontSize: 15, fontWeight: 600, color: "var(--text-primary)", lineHeight: 1.4 },
    newsSummary: { fontSize: 13, color: "var(--text-muted)", lineHeight: 1.5, display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" },
    newsLink: { 
        fontSize: 12, 
        fontWeight: 600, 
        color: "#3b82f6", 
        background: "transparent",
        border: "none",
        padding: 0,
        cursor: "pointer",
        textDecoration: "none", 
        marginTop: 4,
        textAlign: "left",
        display: "inline-flex",
        alignItems: "center",
        gap: 4,
    },
    newsLinkArrow: {
        transition: "transform 0.2s",
    },
    rightPanel: { display: "flex", flexDirection: "column", gap: 16 },
    sidebarCard: {
        background: "var(--bg-card)",
        border: "1px solid var(--border-card)",
        borderRadius: 10,
        padding: 20,
    },
    sidebarTitle: { fontSize: 16, fontWeight: 700, color: "var(--text-primary)", marginBottom: 16 },
    moversList: { display: "flex", flexDirection: "column", gap: 12 },
    moverItem: { display: "flex", justifyContent: "space-between", alignItems: "center" },
    moverLeft: { display: "flex", flexDirection: "column", gap: 2 },
    moverSymbol: { fontSize: 13, fontWeight: 700, color: "var(--text-primary)" },
    moverName: { fontSize: 11, color: "var(--text-muted)" },
    moverChange: { fontSize: 13, fontWeight: 700 },
    categoriesList: { display: "flex", flexDirection: "column", gap: 6 },
    categoryItem: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        padding: "10px 12px",
        background: "transparent",
        border: "1px solid var(--border-card)",
        borderRadius: 8,
        color: "var(--text-primary)",
        fontSize: 13,
        fontWeight: 500,
        cursor: "pointer",
        transition: "all 0.2s",
        textAlign: "left",
    },
    categoryActive: {
        background: "rgba(59, 130, 246, 0.15)",
        borderColor: "#3b82f6",
    },
    categoryCount: {
        fontSize: 11,
        fontWeight: 600,
        color: "var(--text-muted)",
        background: "var(--bg-panel)",
        padding: "2px 8px",
        borderRadius: 12,
    },
    scrollTopButton: {
        position: "fixed",
        bottom: 32,
        right: 32,
        width: 56,
        height: 56,
        borderRadius: "50%",
        background: "#22c55e",
        border: "none",
        boxShadow: "0 4px 12px rgba(34, 197, 94, 0.4)",
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        transition: "all 0.3s",
        zIndex: 1000,
    },
    scrollTopIcon: {
        fontSize: 24,
        fontWeight: 700,
        color: "white",
    },
};

export default News;
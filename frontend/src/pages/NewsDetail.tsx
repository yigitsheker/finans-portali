import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import type { NewsArticle } from '../api/portfolioApi';
import { fetchNewsContent, getNewsById } from '../api/portfolioApi';

const NewsDetail: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [article, setArticle] = useState<NewsArticle | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!id) {
            setError('Geçersiz haber kimliği');
            setLoading(false);
            return;
        }
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
            setError('Geçersiz haber kimliği');
            setLoading(false);
            return;
        }

        let cancelled = false;
        setLoading(true);
        setError(null);

        getNewsById(numericId)
            .then(async (fetched) => {
                if (cancelled) return;
                // Stale or thin content? Try to enrich from source.
                const needsContent = !fetched.content
                    || fetched.content === fetched.summary
                    || fetched.content.length < 200;
                if (needsContent && fetched.sourceUrl) {
                    try {
                        const enriched = await fetchNewsContent(fetched.id);
                        if (!cancelled) setArticle(enriched);
                        return;
                    } catch (e) {
                        console.error('Failed to enrich article content:', e);
                    }
                }
                if (!cancelled) setArticle(fetched);
            })
            .catch((e) => {
                console.error('Failed to load article:', e);
                if (!cancelled) setError('Haber yüklenemedi');
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [id]);

    const handleBack = () => {
        // Prefer history back so the user lands on the news list with scroll preserved;
        // fall back to /news if there's no history.
        if (window.history.length > 1) navigate(-1);
        else navigate('/news');
    };

    const formatDate = (dateString: string) => {
        const date = new Date(dateString);
        return date.toLocaleDateString('tr-TR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
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

    if (loading) {
        return (
            <div style={s.root}>
                <button onClick={handleBack} style={s.backButton}>
                    <span style={s.backIcon} className="news-back-arrow">←</span>
                    <span>Haberlere Dön</span>
                </button>
                <div style={s.loadingContainer}>
                    <div style={s.spinner}></div>
                    <p style={s.loadingText}>Haber yükleniyor...</p>
                </div>
            </div>
        );
    }

    if (error || !article) {
        return (
            <div style={s.root}>
                <button onClick={handleBack} style={s.backButton}>
                    <span style={s.backIcon} className="news-back-arrow">←</span>
                    <span>Haberlere Dön</span>
                </button>
                <div style={s.articleContainer}>
                    <p style={{ color: 'var(--text-muted)' }}>{error ?? 'Haber bulunamadı'}</p>
                </div>
            </div>
        );
    }

    return (
        <div style={s.root}>
            {/* Back Button */}
            <button onClick={handleBack} style={s.backButton}>
                <span style={s.backIcon} className="news-back-arrow">←</span>
                <span>Haberlere Dön</span>
            </button>

            {/* Article Container */}
            <div style={s.articleContainer}>
                {/* Category Badge */}
                <div style={s.categoryBadge}>
                    {getCategoryDisplayName(article.category)}
                </div>

                {/* Title */}
                <h1 style={s.title}>{article.title}</h1>

                {/* Meta Info */}
                <div style={s.meta}>
                    <span style={s.source}>{article.sourceName || 'Piyasalar'}</span>
                    <span style={s.dot}>•</span>
                    <span style={s.date}>{formatDate(article.publishedAt)}</span>
                </div>

                {/* Content */}
                <div style={s.content}>
                    {/* Summary Section */}
                    <div style={s.summarySection}>
                        <h2 style={s.sectionTitle}>Özet</h2>
                        <p style={s.summary}>{article.summary}</p>
                    </div>

                    {/* Detail Section */}
                    <div style={s.detailSection}>
                        <h2 style={s.sectionTitle}>Haber Detayı</h2>
                        <div style={s.fullContent}>
                            {article.content && article.content !== article.summary && article.content.length > 200 ? (
                                article.content.split('\n\n').map((paragraph, index) => (
                                    <p key={index} style={s.contentParagraph}>
                                        {paragraph}
                                    </p>
                                ))
                            ) : (
                                <>
                                    <p style={s.contentParagraph}>
                                        {article.summary}
                                    </p>
                                    <p style={s.contentNote}>
                                        <strong>Not:</strong> Haberin tam içeriği için aşağıdaki "Kaynağa Git" butonunu kullanabilirsiniz.
                                    </p>
                                </>
                            )}
                        </div>
                    </div>
                </div>

                {/* Action Buttons */}
                <div style={s.actions}>
                    {article.sourceUrl && (
                        <a
                            href={article.sourceUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            style={s.sourceButton}
                        >
                            <span>Kaynağa Git</span>
                            <span style={s.externalIcon} className="news-external-icon">↗</span>
                        </a>
                    )}
                    <button onClick={handleBack} style={s.secondaryButton}>
                        Geri Dön
                    </button>
                </div>
            </div>
        </div>
    );
};

const s: Record<string, React.CSSProperties> = {
    root: {
        display: 'flex',
        flexDirection: 'column',
        gap: 20,
        maxWidth: 900,
        margin: '0 auto',
        padding: '0 20px',
    },
    backButton: {
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        padding: '10px 16px',
        background: 'var(--bg-card)',
        border: '1px solid var(--border-card)',
        borderRadius: 8,
        color: 'var(--text-primary)',
        fontSize: 14,
        fontWeight: 500,
        cursor: 'pointer',
        transition: 'all 0.2s',
        alignSelf: 'flex-start',
    },
    backIcon: {
        fontSize: 18,
        fontWeight: 700,
        transition: 'transform 0.2s',
    },
    articleContainer: {
        background: 'var(--bg-card)',
        border: '1px solid var(--border-card)',
        borderRadius: 12,
        padding: 40,
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
    },
    categoryBadge: {
        display: 'inline-flex',
        alignSelf: 'flex-start',
        padding: '6px 14px',
        background: 'rgba(59, 130, 246, 0.15)',
        border: '1px solid rgba(59, 130, 246, 0.3)',
        borderRadius: 20,
        fontSize: 12,
        fontWeight: 600,
        color: '#3b82f6',
    },
    title: {
        fontSize: 36,
        fontWeight: 700,
        color: 'var(--text-primary)',
        lineHeight: 1.3,
        margin: 0,
    },
    meta: {
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        paddingBottom: 24,
        borderBottom: '1px solid var(--border-card)',
    },
    source: {
        fontSize: 14,
        color: '#10b981',
        fontWeight: 600,
    },
    dot: {
        fontSize: 14,
        color: 'var(--text-muted)',
    },
    date: {
        fontSize: 14,
        color: 'var(--text-muted)',
    },
    content: {
        display: 'flex',
        flexDirection: 'column',
        gap: 32,
    },
    summarySection: {
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        paddingBottom: 32,
        borderBottom: '1px solid var(--border-card)',
    },
    detailSection: {
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
    },
    sectionTitle: {
        fontSize: 20,
        fontWeight: 700,
        color: 'var(--text-primary)',
        margin: 0,
    },
    summary: {
        fontSize: 18,
        fontWeight: 500,
        color: 'var(--text-primary)',
        lineHeight: 1.7,
        margin: 0,
    },
    fullContent: {
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
    },
    loadingContainer: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 40,
        gap: 16,
    },
    spinner: {
        width: 40,
        height: 40,
        border: '3px solid var(--border)',
        borderTop: '3px solid #3b82f6',
        borderRadius: '50%',
        animation: 'spin 0.8s linear infinite',
    },
    loadingText: {
        fontSize: 14,
        color: 'var(--text-muted)',
        margin: 0,
    },
    contentParagraph: {
        fontSize: 16,
        color: 'var(--text-secondary)',
        lineHeight: 1.8,
        margin: 0,
    },
    contentNote: {
        fontSize: 14,
        color: 'var(--text-muted)',
        lineHeight: 1.6,
        padding: 16,
        background: 'var(--bg-panel)',
        border: '1px solid var(--border-card)',
        borderRadius: 8,
        margin: 0,
    },
    actions: {
        display: 'flex',
        gap: 12,
        paddingTop: 24,
        borderTop: '1px solid var(--border-card)',
    },
    sourceButton: {
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        padding: '12px 24px',
        background: '#3b82f6',
        border: 'none',
        borderRadius: 8,
        color: 'white',
        fontSize: 14,
        fontWeight: 600,
        cursor: 'pointer',
        textDecoration: 'none',
        transition: 'all 0.2s',
    },
    externalIcon: {
        fontSize: 16,
        fontWeight: 700,
        transition: 'transform 0.2s',
    },
    secondaryButton: {
        display: 'inline-flex',
        alignItems: 'center',
        padding: '12px 24px',
        background: 'var(--bg-card)',
        border: '1px solid var(--border-card)',
        borderRadius: 8,
        color: 'var(--text-primary)',
        fontSize: 14,
        fontWeight: 600,
        cursor: 'pointer',
        transition: 'all 0.2s',
    },
};

export default NewsDetail;

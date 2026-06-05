import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { fetchNewsContent, getNewsById } from '../api/portfolioApi';
import { useI18n } from '../contexts/I18nContext';

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

const splitParagraphs = (text) => {
    if (!text) return [];
    // Prefer real blank-line breaks; fall back to single newlines if the
    // source did not preserve paragraph structure.
    const raw = text.includes('\n\n')
        ? text.split(/\n{2,}/)
        : text.split(/\n+/);
    return raw
        .map((p) => p.trim())
        .filter((p) => p.length > 0);
};

const estimateReadingMinutes = (text) => {
    if (!text) return 1;
    const words = text.trim().split(/\s+/).length;
    return Math.max(1, Math.round(words / 220));
};

const NewsDetail = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const { t, lang } = useI18n();
    const [article, setArticle] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!id) {
            setError(t('news.detailInvalid'));
            setLoading(false);
            return;
        }
        const numericId = Number(id);
        if (!Number.isFinite(numericId)) {
            setError(t('news.detailInvalid'));
            setLoading(false);
            return;
        }

        let cancelled = false;
        setLoading(true);
        setError(null);

        getNewsById(numericId, lang)
            .then(async (fetched) => {
                if (cancelled) return;
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
                if (!cancelled) setError(t('news.detailLoadError'));
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [id, lang]);

    const handleBack = () => {
        if (window.history.length > 1) navigate(-1);
        else navigate('/news');
    };

    const formatDate = (dateString) => {
        const date = new Date(dateString);
        return date.toLocaleDateString(lang === 'en' ? 'en-US' : 'tr-TR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        });
    };

    const paragraphs = useMemo(() => {
        if (!article?.content) return [];
        // If content just echoes summary, don't repeat it.
        if (article.summary && article.content.trim() === article.summary.trim()) return [];
        return splitParagraphs(article.content);
    }, [article]);

    const readingMinutes = useMemo(
        () => estimateReadingMinutes(article?.content || article?.summary || ''),
        [article]
    );

    if (loading) {
        return (
            <article style={s.page}>
                <button onClick={handleBack} style={s.backLink}>
                    <span style={s.backArrow}>←</span> {t('news.detailBack')}
                </button>
                <div style={s.loadingContainer}>
                    <div style={s.spinner}></div>
                    <p style={s.loadingText}>{t('news.detailLoading')}</p>
                </div>
            </article>
        );
    }

    if (error || !article) {
        return (
            <article style={s.page}>
                <button onClick={handleBack} style={s.backLink}>
                    <span style={s.backArrow}>←</span> {t('news.detailBack')}
                </button>
                <p style={s.errorText}>{error ?? t('news.detailNotFound')}</p>
            </article>
        );
    }

    const categoryLabel = CATEGORY_LABEL_KEYS[article.category]
        ? t(CATEGORY_LABEL_KEYS[article.category])
        : article.category;

    return (
        <article style={s.page}>
            {/* Breadcrumb / back link */}
            <button onClick={handleBack} style={s.backLink}>
                <span style={s.backArrow}>←</span> {t('news.detailBack')}
            </button>

            {/* Category kicker — newspaper style section label above headline */}
            {categoryLabel && (
                <div style={s.kicker}>{categoryLabel}</div>
            )}

            {/* Headline */}
            <h1 style={s.headline}>{article.title}</h1>

            {/* Standfirst / lead — the summary as italic deck */}
            {article.summary && (
                <p style={s.standfirst}>{article.summary}</p>
            )}

            {/* Byline / meta strip */}
            <div style={s.byline}>
                <span style={s.sourceName}>{article.sourceName || t('news.markets')}</span>
                <span style={s.bylineSep}>·</span>
                <time style={s.date}>{formatDate(article.publishedAt)}</time>
                <span style={s.bylineSep}>·</span>
                <span style={s.readTime}>{t('news.detailReadingTime', { N: readingMinutes })}</span>
            </div>

            {/* Divider under masthead */}
            <hr style={s.divider} />

            {/* Body */}
            <div style={s.body}>
                {paragraphs.length > 0 ? (
                    paragraphs.map((p, i) => (
                        <p
                            key={i}
                            style={i === 0 ? { ...s.bodyParagraph, ...s.firstParagraph } : s.bodyParagraph}
                        >
                            {p}
                        </p>
                    ))
                ) : (
                    <p style={s.bodyParagraph}>
                        {t('news.detailFooter')}
                    </p>
                )}
            </div>

            {/* Source attribution / CTA */}
            {article.sourceUrl && (
                <footer style={s.footer}>
                    <div style={s.footerLeft}>
                        <div style={s.footerLabel}>{t('news.detailSource')}</div>
                        <div style={s.footerSource}>{article.sourceName || t('news.detailSourceLabel')}</div>
                    </div>
                    <a
                        href={article.sourceUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        style={s.sourceCta}
                    >
                        {t('news.detailGoToOriginal')}
                        <span style={s.ctaArrow}>↗</span>
                    </a>
                </footer>
            )}
        </article>
    );
};

const s = {
    page: {
        maxWidth: 760,
        margin: '0 auto',
        padding: '24px 24px 80px',
        display: 'flex',
        flexDirection: 'column',
    },
    backLink: {
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        alignSelf: 'flex-start',
        marginBottom: 32,
        padding: 0,
        background: 'transparent',
        border: 'none',
        color: 'var(--text-muted)',
        fontSize: 14,
        fontWeight: 500,
        cursor: 'pointer',
        letterSpacing: '0.01em',
    },
    backArrow: {
        fontSize: 16,
        lineHeight: 1,
    },
    kicker: {
        display: 'inline-block',
        alignSelf: 'flex-start',
        marginBottom: 16,
        fontSize: 12,
        fontWeight: 700,
        letterSpacing: '0.14em',
        textTransform: 'uppercase',
        color: '#ef4444',
    },
    headline: {
        fontSize: 'clamp(28px, 4vw, 44px)',
        fontWeight: 800,
        lineHeight: 1.15,
        letterSpacing: '-0.02em',
        color: 'var(--text-primary)',
        margin: '0 0 20px',
        fontFamily: '"Georgia", "Times New Roman", serif',
    },
    standfirst: {
        fontSize: 'clamp(17px, 2vw, 20px)',
        lineHeight: 1.55,
        color: 'var(--text-secondary)',
        fontStyle: 'italic',
        margin: '0 0 24px',
        fontFamily: '"Georgia", "Times New Roman", serif',
    },
    byline: {
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        gap: 8,
        fontSize: 13,
        color: 'var(--text-muted)',
    },
    sourceName: {
        fontWeight: 700,
        color: 'var(--text-primary)',
        textTransform: 'uppercase',
        letterSpacing: '0.04em',
        fontSize: 12,
    },
    bylineSep: {
        color: 'var(--text-muted)',
        opacity: 0.5,
    },
    date: {
        color: 'var(--text-muted)',
    },
    readTime: {
        color: 'var(--text-muted)',
    },
    divider: {
        border: 'none',
        borderTop: '1px solid var(--border-card)',
        margin: '28px 0 36px',
        width: '100%',
    },
    body: {
        display: 'flex',
        flexDirection: 'column',
    },
    bodyParagraph: {
        fontSize: 18,
        lineHeight: 1.8,
        color: 'var(--text-primary)',
        margin: '0 0 22px',
        fontFamily: '"Georgia", "Times New Roman", serif',
    },
    firstParagraph: {
        // Small drop-cap-ish lead emphasis
        fontSize: 19,
    },
    footer: {
        marginTop: 24,
        paddingTop: 24,
        borderTop: '1px solid var(--border-card)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 16,
        flexWrap: 'wrap',
    },
    footerLeft: {
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
    },
    footerLabel: {
        fontSize: 11,
        fontWeight: 700,
        letterSpacing: '0.12em',
        color: 'var(--text-muted)',
    },
    footerSource: {
        fontSize: 15,
        fontWeight: 600,
        color: 'var(--text-primary)',
    },
    sourceCta: {
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        padding: '11px 20px',
        background: 'var(--text-primary)',
        color: 'var(--bg-page, #0a0a0a)',
        borderRadius: 999,
        textDecoration: 'none',
        fontSize: 14,
        fontWeight: 600,
        transition: 'transform 0.15s ease, opacity 0.15s ease',
    },
    ctaArrow: {
        fontSize: 14,
        fontWeight: 700,
    },
    loadingContainer: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 60,
        gap: 16,
    },
    spinner: {
        width: 36,
        height: 36,
        border: '3px solid var(--border-card)',
        borderTop: '3px solid var(--text-primary)',
        borderRadius: '50%',
        animation: 'spin 0.8s linear infinite',
    },
    loadingText: {
        fontSize: 14,
        color: 'var(--text-muted)',
        margin: 0,
    },
    errorText: {
        marginTop: 40,
        fontSize: 16,
        color: 'var(--text-muted)',
    },
};

export default NewsDetail;

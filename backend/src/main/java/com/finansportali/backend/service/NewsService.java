package com.finansportali.backend.service;

import com.finansportali.backend.entity.NewsArticle;
import com.finansportali.backend.entity.NewsFeed;
import com.finansportali.backend.repository.NewsArticleRepository;
import com.finansportali.backend.repository.NewsFeedRepository;
import com.finansportali.backend.service.client.news.LibreTranslateClient;
import com.finansportali.backend.service.client.news.NewsContentFetcher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aggregates financial news: fetches and parses RSS feeds on a schedule,
 * persists articles per category, prunes the backlog, and serves locale-aware
 * lists/details with on-demand and background LibreTranslate translation.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    // ── Category slugs ──────────────────────────────────────────────────────
    // These are the values stored in news_articles.category and used by the
    // /api/v1/news?category=... query param.
    private static final String CAT_GENEL_EKONOMI = "genel-ekonomi";
    private static final String CAT_HISSE = "hisse";
    private static final String CAT_DOVIZ = "doviz";
    private static final String CAT_TAHVIL = "tahvil";
    private static final String CAT_KRIPTO = "kripto";
    private static final String CAT_EMTIA = "emtia";
    private static final String CAT_FONLAR = "fonlar";
    private static final String CAT_BORSA = "borsa";
    private static final String CAT_TCMB = "tcmb";
    private static final String CAT_ULUSLARARASI = "uluslararasi";

    // ── Source display names (high-frequency only) ──────────────────────────
    private static final String SRC_DUNYA = "Dünya Gazetesi";
    private static final String SRC_BLOOMBERG_HT = "Bloomberg HT";
    private static final String SRC_INVESTING = "Investing.com";

    private static final List<String[]> FEEDS = List.of(
        // Genel Ekonomi
        new String[]{"https://www.aa.com.tr/tr/rss/default?cat=ekonomi", CAT_GENEL_EKONOMI, "Anadolu Ajansı"},
        new String[]{"https://www.hurriyet.com.tr/rss/ekonomi", CAT_GENEL_EKONOMI, "Hürriyet"},
        new String[]{"https://www.milliyet.com.tr/rss/rssNew/ekonomiRss.xml", CAT_GENEL_EKONOMI, "Milliyet"},
        new String[]{"https://www.sabah.com.tr/rss/ekonomi.xml", CAT_GENEL_EKONOMI, "Sabah"},
        new String[]{"https://www.dunya.com/rss/ekonomi.xml", CAT_GENEL_EKONOMI, SRC_DUNYA},
        
        // Hisse Senetleri
        new String[]{"https://www.bloomberght.com/rss", CAT_HISSE, SRC_BLOOMBERG_HT},
        new String[]{"https://www.foreks.com/rss/news", CAT_HISSE, "Foreks"},
        new String[]{"https://www.investing.com/rss/news_285.rss", CAT_HISSE, SRC_INVESTING},
        new String[]{"https://www.investing.com/rss/news_25.rss", CAT_HISSE, SRC_INVESTING},
        
        // Döviz
        new String[]{"https://www.dunya.com/rss/doviz.xml", CAT_DOVIZ, SRC_DUNYA},
        new String[]{"https://www.bloomberght.com/rss/doviz", CAT_DOVIZ, SRC_BLOOMBERG_HT},
        new String[]{"https://www.investing.com/rss/forex.rss", CAT_DOVIZ, SRC_INVESTING},
        
        // Tahvil & Bono
        new String[]{"https://www.bloomberght.com/rss/tahvil", CAT_TAHVIL, SRC_BLOOMBERG_HT},
        new String[]{"https://www.investing.com/rss/news_95.rss", CAT_TAHVIL, SRC_INVESTING},
        
        // Kripto Para
        new String[]{"https://cointelegraph.com/rss", CAT_KRIPTO, "Cointelegraph"},
        new String[]{"https://www.coindesk.com/arc/outboundfeeds/rss/", CAT_KRIPTO, "CoinDesk"},
        new String[]{"https://feeds.finance.yahoo.com/rss/2.0/headline?s=BTC-USD&region=US&lang=en-US", CAT_KRIPTO, "Yahoo Finance"},
        new String[]{"https://www.investing.com/rss/news_301.rss", CAT_KRIPTO, SRC_INVESTING},
        
        // Emtia
        new String[]{"https://www.bloomberght.com/rss/emtia", CAT_EMTIA, SRC_BLOOMBERG_HT},
        new String[]{"https://www.investing.com/rss/commodities.rss", CAT_EMTIA, SRC_INVESTING},
        new String[]{"https://www.dunya.com/rss/emtia.xml", CAT_EMTIA, SRC_DUNYA},
        
        // Yatırım Fonları
        new String[]{"https://www.bloomberght.com/rss/fonlar", CAT_FONLAR, SRC_BLOOMBERG_HT},
        new String[]{"https://www.dunya.com/rss/fonlar.xml", CAT_FONLAR, SRC_DUNYA},
        
        // Borsa Haberleri
        new String[]{"https://www.bloomberght.com/rss/borsa", CAT_BORSA, SRC_BLOOMBERG_HT},
        new String[]{"https://www.dunya.com/rss/borsa.xml", CAT_BORSA, SRC_DUNYA},
        new String[]{"https://www.investing.com/rss/stock_brokers.rss", CAT_BORSA, SRC_INVESTING},
        
        // TCMB Kararları
        new String[]{"https://www.tcmb.gov.tr/rss/tcmb.xml", CAT_TCMB, "TCMB"},
        new String[]{"https://www.bloomberght.com/rss/merkez-bankasi", CAT_TCMB, SRC_BLOOMBERG_HT},
        
        // Uluslararası Piyasalar
        new String[]{"https://feeds.finance.yahoo.com/rss/2.0/headline?s=AAPL,TSLA,NVDA&region=US&lang=en-US", CAT_ULUSLARARASI, "Yahoo Finance"},
        new String[]{"https://www.investing.com/rss/news_1.rss", CAT_ULUSLARARASI, SRC_INVESTING},
        new String[]{"https://www.bloomberght.com/rss/dunya", CAT_ULUSLARARASI, SRC_BLOOMBERG_HT}
    );

    private final NewsArticleRepository repo;
    private final NewsFeedRepository feedRepo;
    private final WebClient client;
    private final NewsContentFetcher contentFetcher;
    private final LibreTranslateClient translator;

    // Self-reference for proxy-routed @Transactional calls from inside
    // this same bean. Direct `this.cleanupOrphanArticles()` would skip
    // the proxy and the @Modifying DELETE inside would throw
    // "Executing an update/delete query" because no tx is active.
    // @Lazy breaks the constructor cycle (NewsService → NewsService).
    private final NewsService self;

    // Business metrics — refresh cycle stats and per-article saved counter.
    private final Counter refreshSuccessCounter;
    private final Counter refreshFailureCounter;
    private final Counter articlesSavedCounter;
    private final Counter feedFailureCounter;
    private final Timer refreshDurationTimer;

    public NewsService(NewsArticleRepository repo,
                       NewsFeedRepository feedRepo,
                       NewsContentFetcher contentFetcher,
                       LibreTranslateClient translator,
                       MeterRegistry meterRegistry,
                       @org.springframework.context.annotation.Lazy NewsService self) {
        this.repo = repo;
        this.feedRepo = feedRepo;
        this.contentFetcher = contentFetcher;
        this.translator = translator;
        this.self = self;
        this.client = WebClient.builder()
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; FinansPortali/1.0)")
                .build();

        this.refreshSuccessCounter = Counter.builder("news_refresh_success_total")
                .description("Total successful news refresh cycles (across all feeds)")
                .register(meterRegistry);
        this.refreshFailureCounter = Counter.builder("news_refresh_failure_total")
                .description("Total fully-failed news refresh cycles")
                .register(meterRegistry);
        this.articlesSavedCounter = Counter.builder("news_articles_saved_total")
                .description("Total news articles persisted")
                .register(meterRegistry);
        this.feedFailureCounter = Counter.builder("news_feed_failure_total")
                .description("Per-feed fetch failures (one cycle can produce many)")
                .register(meterRegistry);
        this.refreshDurationTimer = Timer.builder("news_refresh_duration_seconds")
                .description("Duration of a full news refresh cycle (all feeds)")
                .register(meterRegistry);
    }

    /**
     * One-time seed: if news_feeds is empty, copy the original hard-coded FEEDS
     * array into the database. This keeps fresh installs working out of the box
     * and lets the admin edit the list from then on without a code change.
     */
    @PostConstruct
    @Transactional
    void seedFeedsIfEmpty() {
        if (feedRepo.count() > 0) return;
        log.info("Seeding {} initial RSS feeds into news_feeds table", FEEDS.size());
        for (String[] f : FEEDS) {
            String url = f[0];
            String category = f[1];
            String source = f.length > 2 ? f[2] : "Unknown";
            feedRepo.save(new NewsFeed(url, category, source));
        }
    }

    /**
     * Schedules a one-shot background translation warmup ~30s after the
     * backend boots. The warmup pages through articles that don't yet have a
     * cross-language title cached, picks the OTHER language as the target,
     * and calls {@link #ensureTranslationCached(NewsArticle, String, boolean)}
     * one row at a time. LibreTranslate runs at ~3s per translate call, so
     * the whole backlog can take 5+ minutes for a fresh DB — running it off
     * the user request path is the only way to keep the news endpoint
     * responsive on the first call.
     */
    @PostConstruct
    void schedulePrewarmAfterStartup() {
        new Thread(() -> {
            try {
                Thread.sleep(30_000L);
                runTranslationPrewarm();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }, "news-translation-prewarm-startup").start();
    }

    /**
     * Walks the untranslated backlog in 50-row chunks until empty. Each
     * batch resolves the row's source language (we already set it at fetch
     * time from the feed URL, but legacy rows persist with it unset), then
     * translates title + summary to the opposite language. Content stays
     * deferred — the detail endpoint fills that on the first reader. The
     * prewarmRunning flag guards against overlapping runs (PostConstruct +
     * post-fetch trigger could otherwise race).
     */
    public void runTranslationPrewarm() {
        if (!translator.isEnabled()) {
            log.info("[Translate] Prewarm skipped — LibreTranslate disabled");
            return;
        }
        if (!prewarmRunning.compareAndSet(false, true)) {
            log.info("[Translate] Prewarm already running, skip");
            return;
        }
        try {
            // English-source first: this pool is ~10x smaller AND it's the
            // one Turkish readers stumble on most often, since Investing.com
            // / CoinDesk articles get top spots in the "latest" list. Doing
            // these first lands the biggest UX win in the first ~10 minutes.
            prewarmBySource("en", "tr");
            // Then Turkish-source so the English readers see their entire
            // feed in English too.
            prewarmBySource("tr", "en");
        } finally {
            prewarmRunning.set(false);
        }
    }

    private void prewarmBySource(String sourceLang, String target) {
        int processed = 0;
        while (true) {
            List<NewsArticle> batch = repo
                    .findTop50ByTitleTranslatedIsNullAndSourceLangOrderByPublishedAtDesc(sourceLang);
            if (batch.isEmpty()) break;
            for (NewsArticle a : batch) {
                try {
                    ensureTranslationCached(a, target, /*includeContent=*/false);
                } catch (RuntimeException e) {
                    log.warn("[Translate] Prewarm article {} failed: {}", a.getId(), e.getMessage());
                }
                processed++;
            }
            log.info("[Translate] Prewarm progress [{}→{}]: {} rows", sourceLang, target, processed);
            // If the same untranslated batch keeps coming back (every call
            // returned null), bail to avoid an infinite loop on a downed
            // LibreTranslate.
            if (batch.stream().allMatch(a -> a.getTitleTranslated() == null)) {
                log.warn("[Translate] Prewarm [{}→{}] stalled — translator unreachable, abort",
                        sourceLang, target);
                break;
            }
        }
        log.info("[Translate] Prewarm pass [{}→{}] finished, {} rows updated",
                sourceLang, target, processed);
    }

    // Host substrings that mark a feed as English-source. Anything else is
    // assumed Turkish — the seed list is overwhelmingly TR press, and the
    // background prewarmer corrects misclassifications by calling
    // LibreTranslate's /detect on rows whose source_lang stays unset.
    private static final Set<String> EN_FEED_HOST_HINTS = Set.of(
            "cointelegraph.com",
            "coindesk.com",
            "feeds.finance.yahoo.com",
            "www.investing.com"
    );

    private static String guessSourceLangFromUrl(String url) {
        if (url == null) return "tr";
        String lower = url.toLowerCase(Locale.ROOT);
        for (String hint : EN_FEED_HOST_HINTS) {
            if (lower.contains(hint)) return "en";
        }
        return "tr";
    }

    // Single-shot flag — prewarm should never run twice in parallel. Set on
    // the first @PostConstruct fire and after each fetchAndSaveNews cycle.
    private final AtomicBoolean prewarmRunning = new AtomicBoolean(false);

    /**
     * Latest 50 articles, optionally restricted to a single category slug.
     * Blank/null category returns the newest across all categories.
     */
    public List<NewsArticle> latest(String category) {
        if (category == null || category.isBlank()) {
            return repo.findTop50ByOrderByPublishedAtDesc();
        }
        return repo.findTop50ByCategoryOrderByPublishedAtDesc(category);
    }

    /**
     * Locale-aware overload: returns the same list as {@link #latest(String)}
     * but with each article rendered in {@code lang} ("tr" or "en"). Articles
     * already in the requested language pass through untouched; others are
     * lazily translated via LibreTranslate and cached on the row so the next
     * call hits the DB only.
     *
     * <p>The base {@code title/summary/content} fields on the returned
     * entities are SWAPPED to the translated text after the transactional
     * cache-fill exits — that swap happens on detached objects, so it never
     * flushes back to the row. The frontend keeps consuming the same JSON
     * shape it already does.
     */
    public List<NewsArticle> latest(String category, String lang) {
        List<NewsArticle> articles = latest(category);
        String target = normalizeLang(lang);
        if (target == null || !translator.isEnabled()) {
            return articles;
        }
        // List endpoint stays cache-only: each LibreTranslate round-trip is
        // ~3s, so synchronously translating 50 articles × (title + summary)
        // = 100 calls would block the response for 5 minutes — easily past
        // any browser timeout. Instead we only swap in already-cached
        // translations here and rely on the background prewarmer (kicked off
        // at startup and after every fetch cycle) to fill the gaps. The
        // detail endpoint `getById()` still translates inline because a
        // single article finishes in ~10s.
        for (NewsArticle a : articles) {
            applyTranslationToBaseFields(a, target);
        }
        return articles;
    }

    private static String normalizeLang(String lang) {
        if (lang == null) return null;
        String s = lang.toLowerCase(Locale.ROOT);
        if (s.startsWith("en")) return "en";
        if (s.startsWith("tr")) return "tr";
        return null;
    }

    /**
     * Persists the source-language detection (one-time, on first read of an
     * article) and the translated columns (one-time per row+target pair) so
     * subsequent reads are free. Runs inside its own short transaction; the
     * passed entity is re-attached, saved, and detaches again on exit — that
     * detachment is what lets the caller mutate base fields without a flush.
     *
     * <p>Lazily caches translation columns for the article. When
     * {@code includeContent} is false we skip the expensive content
     * translation (multi-KB body) — list views only display
     * title + summary, and the detail page makes a separate call that
     * back-fills content_translated on demand.
     *
     * <p>Idempotent: re-running on a row that already has a title_translated
     * but missing content_translated will just fill in the content if the
     * caller now asks for it.
     */
    @Transactional
    public void ensureTranslationCached(NewsArticle a, String target, boolean includeContent) {
        String sourceLang = a.getSourceLang();
        if (sourceLang == null) {
            String detected = translator.detect(a.getTitle());
            sourceLang = (detected != null) ? detected : "tr";
            a.setSourceLang(sourceLang);
            repo.save(a);
        }
        if (sourceLang.equals(target)) return;

        boolean needsTitleSummary = a.getTitleTranslated() == null;
        boolean needsContent = includeContent
                && a.getContent() != null
                && a.getContentTranslated() == null;

        if (!needsTitleSummary && !needsContent) return;

        if (needsTitleSummary) {
            String tt = translator.translate(a.getTitle(), sourceLang, target);
            String st = a.getSummary() != null
                    ? translator.translate(a.getSummary(), sourceLang, target) : null;
            // Only persist if at least the title translation succeeded —
            // partial failures (LibreTranslate down mid-call) shouldn't
            // poison the cache by leaving title_translated null while the
            // calling view falls back to original.
            if (tt != null) {
                a.setTitleTranslated(tt);
                a.setSummaryTranslated(st);
            }
        }
        if (needsContent) {
            String ct = translator.translate(a.getContent(), sourceLang, target);
            if (ct != null) a.setContentTranslated(ct);
        }
        repo.save(a);
    }

    private void applyTranslationToBaseFields(NewsArticle a, String target) {
        // Detached after ensureTranslationCached's transaction closed, so
        // these setters don't propagate to the DB.
        if (a.getSourceLang() == null || a.getSourceLang().equals(target)) return;
        if (a.getTitleTranslated() != null)   a.setTitle(a.getTitleTranslated());
        if (a.getSummaryTranslated() != null) a.setSummary(a.getSummaryTranslated());
        if (a.getContentTranslated() != null) a.setContent(a.getContentTranslated());
    }

    /** The fixed set of supported category slugs. */
    public List<String> getCategories() {
        return List.of(
            CAT_GENEL_EKONOMI, CAT_HISSE, CAT_DOVIZ, CAT_TAHVIL, CAT_KRIPTO, 
            CAT_EMTIA, CAT_FONLAR, CAT_BORSA, CAT_TCMB, CAT_ULUSLARARASI
        );
    }

    /** Article count per category plus an "all" total, for the category nav badges. */
    public Map<String, Long> getCategoryCounts() {
        Map<String, Long> counts = new HashMap<>();
        
        // Get all categories
        List<String> categories = getCategories();
        
        // Count articles for each category
        for (String category : categories) {
            long count = repo.countByCategory(category);
            counts.put(category, count);
        }
        
        // Add total count
        counts.put("all", repo.count());
        
        return counts;
    }

    /** Fetch a single article by id, or null if it doesn't exist. */
    public NewsArticle getById(Long id) {
        return repo.findById(id).orElse(null);
    }

    /**
     * Locale-aware variant of {@link #getById(Long)} — used by the detail
     * page so a single article opens in the reader's chosen language.
     */
    public NewsArticle getById(Long id, String lang) {
        NewsArticle a = repo.findById(id).orElse(null);
        if (a == null) return null;
        String target = normalizeLang(lang);
        if (target != null && translator.isEnabled()) {
            // Only translate the body inline when it's already substantial.
            // Placeholder/summary-length bodies get replaced by the on-demand
            // /fetch-content scrape, so translating them here is wasted work
            // (and would block the first detail open). fetch-content handles
            // the translation of the freshly scraped body instead.
            boolean substantialBody = a.getContent() != null
                    && a.getContent().length() > 200
                    && !a.getContent().equals(a.getSummary());
            ensureTranslationCached(a, target, /*includeContent=*/substantialBody);
            applyTranslationToBaseFields(a, target);
        }
        return a;
    }

    /**
     * Lazily back-fill an article's full body from its source URL when the
     * stored content is missing or too short, persisting the fetched text, then
     * translate the (possibly newly scraped) body to {@code lang} so the detail
     * reader gets it in their language. Translating here — rather than inline in
     * {@link #getById(Long, String)} — avoids translating a short placeholder
     * body that's about to be replaced.
     */
    public NewsArticle fetchContentForArticle(Long id, String lang) {
        NewsArticle article = repo.findById(id).orElse(null);
        if (article == null) {
            return null;
        }

        boolean substantial = article.getContent() != null && article.getContent().length() > 200;

        // Scrape the source only when we don't already have a real body.
        if (!substantial && article.getSourceUrl() != null && !article.getSourceUrl().isBlank()) {
            String content = contentFetcher.fetchArticleContent(article.getSourceUrl());
            if (content != null && !content.isBlank()) {
                article.setContent(content);
                // New body invalidates any translation cached for the old one.
                article.setContentTranslated(null);
                repo.save(article);
                log.info("Fetched and saved content for article: {}", article.getTitle());
            }
        }

        // Translate the body to the reader's language (cached on the row).
        String target = normalizeLang(lang);
        if (target != null && translator.isEnabled()) {
            ensureTranslationCached(article, target, /*includeContent=*/true);
            applyTranslationToBaseFields(article, target);
        }

        return article;
    }

    /**
     * Scheduled refresh cycle: pulls every enabled RSS feed, saves new articles,
     * prunes old ones, and kicks off background translation. Runs every 6 hours.
     */
    @Scheduled(initialDelay = 5_000, fixedDelay = 6 * 60 * 60 * 1000L)
    public void fetchAndSaveNews() {
        refreshDurationTimer.record(this::doFetchAndSaveNews);
    }

    /**
     * Fire-and-forget manual refresh for the admin UI. A full fetch can take
     * minutes; running it on the request thread would blow past the reverse
     * proxy's read timeout (and the browser would give up) before it finishes.
     * So we kick it off on a background daemon thread and return immediately.
     * Goes through the @Lazy self proxy so the timer/transaction wrappers apply.
     */
    public void triggerManualFetchAsync() {
        Thread t = new Thread(() -> {
            try {
                self.fetchAndSaveNews();
            } catch (RuntimeException e) {
                log.warn("Manual news fetch failed: {}", e.getMessage());
            }
        }, "manual-news-fetch");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Removes news_articles rows whose (sourceName, category) pair is not
     * covered by any ENABLED feed — articles left behind by a feed that was
     * deleted OR disabled. (A disabled feed keeps its row but its news must no
     * longer surface.) Returns the number of rows removed.
     */
    @Transactional
    public int cleanupOrphanArticles() {
        int removed = repo.deleteOrphanedArticles();
        if (removed > 0) {
            log.info("Orphan-article cleanup: removed {} rows not covered by an enabled feed", removed);
        }
        return removed;
    }

    /**
     * Daily scheduled orphan sweep. Belt-and-braces against future drift —
     * the per-feed delete already cascades, this just catches anything that
     * slipped through (e.g. an article whose source name changed, or
     * articles inserted before the cascade fix was deployed). 03:00 UTC
     * is well outside any market hour so the DELETE doesn't fight with
     * the heavier fetch cycle.
     *
     * Goes through the @Lazy self reference so Spring's @Transactional
     * proxy actually wraps the delete — calling `cleanupOrphanArticles()`
     * directly on `this` would self-invoke and bypass the proxy
     * (PriceRefreshScheduler uses the same pattern for the same reason).
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledOrphanCleanup() {
        try {
            self.cleanupOrphanArticles();
        } catch (RuntimeException e) {
            log.warn("Scheduled orphan cleanup failed: {}", e.getMessage());
        }
    }

    /**
     * One-shot orphan sweep on startup. Fires after the Spring context is
     * fully up so flyway is done and feedRepo is ready. Cheap — orphans
     * are rare under normal operation, so the DELETE typically removes 0
     * rows and returns in milliseconds.
     */
    @org.springframework.context.event.EventListener(
            org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onStartupCleanupOrphans() {
        try {
            int removed = self.cleanupOrphanArticles();
            if (removed > 0) {
                log.info("Startup orphan-article cleanup removed {} rows", removed);
            }
        } catch (RuntimeException e) {
            // Don't fail boot if the cleanup throws — the table is still
            // queryable, just contains stale rows.
            log.warn("Startup orphan cleanup failed: {}", e.getMessage());
        }
    }

    private void doFetchAndSaveNews() {
        // Pull the currently-enabled feed list from the admin-managed table.
        List<NewsFeed> feeds = feedRepo.findByEnabledTrueOrderByCategoryAscSourceAsc();
        log.info("Fetching news from {} active RSS feeds...", feeds.size());
        int feedFailures = 0;
        for (NewsFeed feed : feeds) {
            try {
                fetchRss(feed.getUrl(), feed.getCategory(), feed.getSource());
            } catch (Exception e) {
                log.warn("RSS fetch failed for {}: {}", feed.getUrl(), e.getMessage());
                feedFailureCounter.increment();
                feedFailures++;
            }
        }
        // Every feed failed -> cycle is a wash; otherwise treat as success.
        if (!feeds.isEmpty() && feedFailures == feeds.size()) {
            refreshFailureCounter.increment();
        } else {
            refreshSuccessCounter.increment();
        }
        // Her döngünün sonunda kategoriler 50 ile sınırlandırılsın; eski haberler silinir
        // ve içerikleri olmayanlar ayıklanır. Bu DB'nin sınırsız büyümesini engeller.
        try {
            cleanupOldNews();
        } catch (Exception e) {
            log.warn("Post-fetch cleanup failed: {}", e.getMessage());
        }
        // Fire-and-forget background translation. The scheduler thread that
        // owns fetchAndSaveNews shouldn't block on a few-minute LibreTranslate
        // pass; we hand the work off to a dedicated thread and let it finish
        // on its own.
        new Thread(this::runTranslationPrewarm, "news-translation-prewarm-postfetch").start();
    }

    private static final int MAX_PER_CATEGORY = 50;
    private static final int MIN_CONTENT_CHARS = 400;
    private static final int MAX_TITLE_CHARS = 295;
    private static final int MAX_SUMMARY_CHARS = 1990;
    private static final int MAX_CONTENT_CHARS = 10000;
    private static final Pattern ITEM_PATTERN = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);

    private void fetchRss(String url, String category, String sourceName) {
        // Bu kategori zaten dolduysa fetch'i atla — cleanup sonradan halletse de
        // gereksiz HTTP/parse maliyetinden kaçınalım.
        long existingCount = repo.countByCategory(category);
        if (existingCount >= MAX_PER_CATEGORY) {
            log.debug("Category {} already has {} articles, skipping {}", category, existingCount, url);
            return;
        }

        String xml = client.get().uri(url).retrieve().bodyToMono(String.class).block();
        if (xml == null || xml.isBlank()) return;

        // Duplicate check kategoriye özel — global top 50 ile karşılaştırmak yetmiyor.
        List<String> existingTitles = repo.findTop50ByCategoryOrderByPublishedAtDesc(category)
                .stream().map(NewsArticle::getTitle).toList();
        int budget = (int) (MAX_PER_CATEGORY - existingCount);

        Matcher itemMatcher = ITEM_PATTERN.matcher(xml);
        int saved = 0;
        while (itemMatcher.find() && saved < budget) {
            NewsArticle article = parseItem(itemMatcher.group(1), category, sourceName, existingTitles);
            if (article != null) {
                repo.save(article);
                saved++;
                articlesSavedCounter.increment();
            }
        }
        log.info("Saved {} new {} articles (only items with fetched full body)", saved, category);
    }

    /**
     * Parse one RSS <item> block into a NewsArticle, fetching the full body
     * from the source URL. Returns null (and logs at debug) for any item that
     * fails the title / link / fetched-body requirements — the loop in
     * fetchRss treats null as "skip and try the next one".
     */
    private NewsArticle parseItem(String item, String category, String sourceName, List<String> existingTitles) {
        String title = extractTag(item, "title");
        if (title == null || title.isBlank()) return null;
        title = stripCdata(title).trim();
        if (existingTitles.contains(title)) return null;

        String link = extractTag(item, "link");
        if (link == null || link.isBlank()) {
            log.debug("Skipping article without source URL: {}", title);
            return null;
        }

        // Fetch real body from source — without ≥ MIN_CONTENT_CHARS the detail
        // page would only echo the headline, so drop the article entirely.
        String fetchedContent = contentFetcher.fetchArticleContent(link);
        if (fetchedContent == null || fetchedContent.isBlank() || fetchedContent.length() < MIN_CONTENT_CHARS) {
            log.debug("Dropping article — no fetchable body (len={}): {}",
                    fetchedContent == null ? 0 : fetchedContent.length(), link);
            return null;
        }

        String summary = extractTag(item, "description");
        summary = (summary == null || summary.isBlank()) ? title : stripCdata(stripHtml(summary)).trim();

        if (title.length() > MAX_TITLE_CHARS) title = title.substring(0, MAX_TITLE_CHARS);
        if (summary.length() > MAX_SUMMARY_CHARS) summary = summary.substring(0, MAX_SUMMARY_CHARS);
        String content = fetchedContent.length() > MAX_CONTENT_CHARS
                ? fetchedContent.substring(0, MAX_CONTENT_CHARS)
                : fetchedContent;

        String pubDate = extractTag(item, "pubDate");
        NewsArticle article = new NewsArticle(title, summary, content, category, parseRssDate(pubDate), link, sourceName);
        article.setSourceLang(guessSourceLangFromUrl(link));
        return article;
    }

    private String extractTag(String xml, String tag) {
        Pattern p = Pattern.compile("<" + tag + "[^>]*>\\s*(.*?)\\s*</" + tag + ">", Pattern.DOTALL);
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private String stripCdata(String s) {
        if (s == null) return "";
        // Plain replace — these are literal markers, not regex.
        return s.replace("<![CDATA[", "").replace("]]>", "").trim();
    }

    private String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private Instant parseRssDate(String s) {
        if (s == null || s.isBlank()) return Instant.now();
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
            return ZonedDateTime.parse(s.trim(), fmt).toInstant();
        } catch (Exception e) {
            return Instant.now();
        }
    }

    /**
     * Ensure the table holds some readable articles: if none have real content,
     * clear stubs and seed a handful of sample Turkish articles across categories.
     */
    public void seedIfEmpty() {
        // Always ensure we have at least some articles with content
        long articlesWithContent = repo.findAll().stream()
                .filter(a -> a.getContent() != null && a.getContent().length() > 200)
                .count();
        
        if (articlesWithContent > 0) {
            log.info("Found {} articles with content, skipping seed", articlesWithContent);
            return;
        }
        
        // Delete old articles without content
        List<NewsArticle> oldArticles = repo.findAll();
        if (!oldArticles.isEmpty()) {
            log.info("Deleting {} old articles without content", oldArticles.size());
            repo.deleteAll(oldArticles);
        }
        
        Instant now = Instant.now();
        
        // Örnek Haber 1 - Borsa
        repo.save(new NewsArticle(
                "BIST 100 Endeksi Yeni Rekor Kırdı",
                "Borsa İstanbul'da BIST 100 endeksi, güçlü alıcı ilgisiyle tarihi zirvesini yeniledi. Bankacılık ve enerji hisseleri endeksi yukarı taşıdı.",
                "Borsa İstanbul'da BIST 100 endeksi, güçlü alıcı ilgisiyle tarihi zirvesini yeniledi. Bankacılık ve enerji hisseleri endeksi yukarı taşıdı.\n\n" +
                "Günün ilk yarısında yatay seyreden endeks, öğleden sonra alıcılı bir seyir izledi. Özellikle bankacılık hisselerindeki yükseliş endeksi destekledi.\n\n" +
                "Analistler, TCMB'nin faiz politikasındaki kararlı duruşun piyasalara güven verdiğini belirtiyor. Yabancı yatırımcıların da Türk varlıklarına ilgisinin arttığı gözlemleniyor.\n\n" +
                "Teknik analistler, endeksin 15.000 seviyesini test edebileceğini öngörüyor. Ancak kar realizasyonlarının da gündeme gelebileceği uyarısı yapılıyor.",
                CAT_BORSA,
                now.minusSeconds(3600),
                "https://example.com/bist-100-rekor",
                SRC_BLOOMBERG_HT
        ));
        
        // Örnek Haber 2 - Döviz
        repo.save(new NewsArticle(
                "Dolar/TL Kuru Düşüş Trendinde",
                "Merkez Bankası'nın sıkı para politikası ve rezerv artışı dolar/TL kurunda düşüş eğilimini güçlendirdi.",
                "Merkez Bankası'nın sıkı para politikası ve rezerv artışı dolar/TL kurunda düşüş eğilimini güçlendirdi.\n\n" +
                "TCMB'nin brüt döviz rezervleri son haftalarda önemli artış gösterdi. Bu durum, piyasalarda güven ortamının oluşmasına katkı sağladı.\n\n" +
                "Ekonomistler, enflasyonla mücadelede kararlı duruşun sürmesi halinde kurlarda daha fazla gerileme görülebileceğini ifade ediyor.\n\n" +
                "Öte yandan, küresel piyasalardaki gelişmeler ve Fed'in faiz politikası da döviz kurları üzerinde etkili olmaya devam ediyor.",
                CAT_DOVIZ,
                now.minusSeconds(7200),
                "https://example.com/dolar-tl-kuru",
                "Anadolu Ajansı"
        ));
        
        // Örnek Haber 3 - Kripto
        repo.save(new NewsArticle(
                "Bitcoin 75.000 Dolar Seviyesini Test Ediyor",
                "Kripto para piyasalarında Bitcoin, yeni bir yükseliş dalgasıyla 75.000 dolar seviyesine yaklaştı.",
                "Kripto para piyasalarında Bitcoin, yeni bir yükseliş dalgasıyla 75.000 dolar seviyesine yaklaştı.\n\n" +
                "Kurumsal yatırımcıların Bitcoin ETF'lerine olan ilgisi artmaya devam ediyor. Bu durum, Bitcoin fiyatını yukarı taşıyan en önemli faktörlerden biri.\n\n" +
                "Ethereum da Bitcoin'i takip ederek 2.400 dolar seviyesini aştı. Altcoin'lerde de genel olarak yükseliş trendi gözlemleniyor.\n\n" +
                "Analistler, Bitcoin'in 80.000 dolar seviyesine ulaşabileceğini öngörüyor. Ancak volatilitenin yüksek olduğu ve risk yönetiminin önemli olduğu vurgulanıyor.",
                CAT_KRIPTO,
                now.minusSeconds(10800),
                "https://example.com/bitcoin-75000",
                "Cointelegraph"
        ));
        
        // Örnek Haber 4 - Hisse
        repo.save(new NewsArticle(
                "Teknoloji Hisseleri Yükselişte",
                "Yerli teknoloji şirketlerinin hisseleri, güçlü bilanço açıklamalarının ardından yükseliş trendine girdi.",
                "Yerli teknoloji şirketlerinin hisseleri, güçlü bilanço açıklamalarının ardından yükseliş trendine girdi.\n\n" +
                "Özellikle yazılım ve e-ticaret şirketlerinin kar artışları yatırımcıların dikkatini çekti. Bu sektördeki şirketlerin hisseleri çift haneli getiriler sağladı.\n\n" +
                "Sektör temsilcileri, dijital dönüşümün hızlanmasıyla birlikte büyüme potansiyelinin devam edeceğini belirtiyor.\n\n" +
                "Analistler, teknoloji sektörünün uzun vadede yatırımcılar için cazip fırsatlar sunabileceğini değerlendiriyor.",
                CAT_HISSE,
                now.minusSeconds(14400),
                "https://example.com/teknoloji-hisseleri",
                SRC_INVESTING
        ));
        
        // Örnek Haber 5 - TCMB
        repo.save(new NewsArticle(
                "TCMB Faiz Kararını Açıkladı",
                "Türkiye Cumhuriyet Merkez Bankası, Para Politikası Kurulu toplantısında politika faizini sabit tutma kararı aldı.",
                "Türkiye Cumhuriyet Merkez Bankası, Para Politikası Kurulu toplantısında politika faizini sabit tutma kararı aldı.\n\n" +
                "Merkez Bankası'nın açıklamasında, enflasyonla mücadelede kararlı duruşun sürdürüleceği vurgulandı. Sıkı para politikasının enflasyon beklentilerini düşürmede etkili olduğu belirtildi.\n\n" +
                "Ekonomistler, faiz kararının beklentiler doğrultusunda olduğunu ve piyasalarda olumlu karşılandığını ifade ediyor.\n\n" +
                "TCMB, enflasyon görünümünde kalıcı bir iyileşme sağlanana kadar sıkı para politikası duruşunu koruyacağını açıkladı.",
                CAT_TCMB,
                now.minusSeconds(18000),
                "https://example.com/tcmb-faiz-karari",
                "TCMB"
        ));
        
        // Örnek Haber 6 - Tahvil & Bono
        repo.save(new NewsArticle(
                "Devlet Tahvili Faizleri Geriledi",
                "İç borçlanma piyasasında devlet tahvili faizlerinde düşüş yaşandı. 10 yıllık tahvil faizi yüzde 46 seviyesine indi.",
                "İç borçlanma piyasasında devlet tahvili faizlerinde düşüş yaşandı. 10 yıllık tahvil faizi yüzde 46 seviyesine indi.\n\n" +
                "Hazine'nin düzenli ihalelerinde güçlü talep görülüyor. Yabancı yatırımcıların Türk tahvillerine ilgisi artmaya devam ediyor.\n\n" +
                "Analistler, enflasyon beklentilerindeki iyileşmenin tahvil faizlerini aşağı çektiğini belirtiyor. Merkez Bankası'nın kararlı duruşu piyasalara güven veriyor.\n\n" +
                "Uzmanlar, tahvil faizlerinde daha fazla gerileme görülebileceğini, ancak küresel gelişmelerin de takip edilmesi gerektiğini vurguluyor.",
                CAT_TAHVIL,
                now.minusSeconds(21600),
                "https://example.com/tahvil-faizleri",
                SRC_BLOOMBERG_HT
        ));
        
        // Örnek Haber 7 - Emtia
        repo.save(new NewsArticle(
                "Altın Fiyatları Rekor Seviyede",
                "Küresel belirsizlikler ve merkez bankalarının alımları altın fiyatlarını rekor seviyelere taşıdı. Ons altın 2.400 doları aştı.",
                "Küresel belirsizlikler ve merkez bankalarının alımları altın fiyatlarını rekor seviyelere taşıdı. Ons altın 2.400 doları aştı.\n\n" +
                "Jeopolitik riskler ve enflasyon endişeleri yatırımcıları güvenli liman olarak görülen altına yönlendiriyor. Merkez bankaları da rezervlerini çeşitlendirmek için altın alımlarını artırıyor.\n\n" +
                "Türkiye'de gram altın fiyatları da yeni zirvelere ulaştı. Yatırımcılar altını hem değer koruma hem de getiri aracı olarak görüyor.\n\n" +
                "Analistler, altın fiyatlarının kısa vadede yüksek seyretmeye devam edebileceğini, ancak kar realizasyonlarının da gündeme gelebileceğini belirtiyor.",
                CAT_EMTIA,
                now.minusSeconds(25200),
                "https://example.com/altin-fiyatlari",
                SRC_INVESTING
        ));
        
        // Örnek Haber 8 - Yatırım Fonları
        repo.save(new NewsArticle(
                "Hisse Senedi Fonları Yüksek Getiri Sağladı",
                "2026 yılının ilk çeyreğinde hisse senedi fonları yatırımcılarına yüzde 15'in üzerinde getiri sağladı.",
                "2026 yılının ilk çeyreğinde hisse senedi fonları yatırımcılarına yüzde 15'in üzerinde getiri sağladı.\n\n" +
                "Borsa İstanbul'daki yükseliş trendi, hisse senedi fonlarının performansını olumlu etkiledi. Özellikle teknoloji ve finans sektörüne yatırım yapan fonlar öne çıktı.\n\n" +
                "Fon yöneticileri, portföylerini aktif olarak yöneterek piyasa ortalamasının üzerinde getiri elde ettiler. Yatırımcıların fonlara ilgisi artmaya devam ediyor.\n\n" +
                "Uzmanlar, uzun vadeli yatırımcılar için hisse senedi fonlarının cazip fırsatlar sunabileceğini, ancak risk yönetiminin önemli olduğunu vurguluyor.",
                CAT_FONLAR,
                now.minusSeconds(28800),
                "https://example.com/yatirim-fonlari",
                SRC_DUNYA
        ));
        
        log.info("Seeded {} sample news articles", 8);
    }

    /**
     * Keep only the newest 50 articles per category and drop rows in unknown
     * categories, bounding table growth. Returns a per-category and total tally.
     */
    @Transactional
    public Map<String, Object> cleanupOldNews() {
        log.info("Starting news cleanup...");

        Map<String, Object> result = new HashMap<>();
        int totalDeleted = 0;
        Map<String, Integer> deletedByCategory = new HashMap<>();

        // Her kategori için en yeni 50 haberi tut, geri kalanını sil.
        // 20k+ kayıt olduğunda findAll() yavaş ve OOM riskli; kategori bazlı query + bulk delete.
        for (String category : getCategories()) {
            long count = repo.countByCategory(category);
            if (count <= 50) {
                deletedByCategory.put(category, 0);
                continue;
            }
            // En yeni 50'nin ID'lerini al, bunların DIŞINDAKİLERİ tek query ile sil
            List<Long> keepIds = repo.findTop50ByCategoryOrderByPublishedAtDesc(category)
                    .stream().map(NewsArticle::getId).toList();
            int deleted = repo.deleteByCategoryAndIdNotIn(category, keepIds);
            deletedByCategory.put(category, deleted);
            totalDeleted += deleted;
            log.info("Cleanup: kategori={} silinen={} kalan={}", category, deleted, keepIds.size());
        }

        // Bilinmeyen / artık kullanılmayan kategorilerde de takılı kalmış kayıtlar olabilir;
        // bunları da temizle (10 resmi kategori dışındaki her şey).
        List<String> validCategories = getCategories();
        long stragglerCount = repo.findAll().stream()
                .filter(a -> a.getCategory() == null || !validCategories.contains(a.getCategory()))
                .peek(repo::delete)
                .count();
        if (stragglerCount > 0) {
            totalDeleted += (int) stragglerCount;
            log.info("Cleanup: {} kayıt geçersiz kategoride silindi", stragglerCount);
        }

        result.put("totalDeleted", totalDeleted);
        result.put("deletedByCategory", deletedByCategory);
        result.put("remainingTotal", repo.count());
        
        log.info("News cleanup completed. Deleted {} articles", totalDeleted);
        
        return result;
    }
}


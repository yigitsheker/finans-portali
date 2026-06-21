package com.finansportali.backend.service;

import com.finansportali.backend.entity.NewsArticle;
import com.finansportali.backend.entity.NewsFeed;
import com.finansportali.backend.repository.NewsArticleRepository;
import com.finansportali.backend.repository.NewsFeedRepository;
import com.finansportali.backend.service.client.news.LibreTranslateClient;
import com.finansportali.backend.service.client.news.NewsContentFetcher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewsServiceTest {

    @Mock private NewsArticleRepository repo;
    @Mock private NewsFeedRepository feedRepo;
    @Mock private NewsContentFetcher contentFetcher;
    @Mock private LibreTranslateClient translator;
    private NewsService service;

    @BeforeEach
    void setUp() {
        // self (constructor-injected @Lazy proxy in prod) is null here; the two
        // *OrphanCleanup tests rely on that null NPE being swallowed.
        service = new NewsService(repo, feedRepo, contentFetcher, translator, new SimpleMeterRegistry(), null);
    }

    private static NewsArticle article(Long id, String title, String content, String category) {
        NewsArticle a = new NewsArticle(title,
                "summary " + title,
                content,
                category,
                Instant.now(),
                "https://example.com/" + title,
                "TestSource");
        // entity has no setId() — we use ReflectionTestUtils
        if (id != null) ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Test
    void seedFeedsIfEmpty_skips_when_feeds_already_present() {
        when(feedRepo.count()).thenReturn(31L);
        service.seedFeedsIfEmpty();
        verify(feedRepo, never()).save(any());
    }

    @Test
    void seedFeedsIfEmpty_inserts_hardcoded_feeds_when_db_empty() {
        when(feedRepo.count()).thenReturn(0L);
        service.seedFeedsIfEmpty();
        // 31 feeds in the FEEDS list
        verify(feedRepo, times(31)).save(any(NewsFeed.class));
    }

    @Test
    void latest_returns_all_when_category_null() {
        when(repo.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        assertThat(service.latest(null)).isEmpty();
        verify(repo, never()).findTop50ByCategoryOrderByPublishedAtDesc(anyString());
    }

    @Test
    void latest_returns_all_when_category_blank() {
        when(repo.findTop50ByOrderByPublishedAtDesc()).thenReturn(List.of());
        assertThat(service.latest("  ")).isEmpty();
    }

    @Test
    void latest_filters_by_category_when_present() {
        when(repo.findTop50ByCategoryOrderByPublishedAtDesc("borsa")).thenReturn(List.of());
        service.latest("borsa");
        verify(repo).findTop50ByCategoryOrderByPublishedAtDesc("borsa");
    }

    @Test
    void getCategories_returns_ten_fixed_categories() {
        assertThat(service.getCategories())
                .containsExactly("genel-ekonomi", "hisse", "doviz", "tahvil", "kripto",
                                "emtia", "fonlar", "borsa", "tcmb", "uluslararasi");
    }

    @Test
    void getCategoryCounts_includes_all_plus_total() {
        when(repo.countByCategory(anyString())).thenReturn(3L);
        when(repo.count()).thenReturn(30L);
        Map<String, Long> counts = service.getCategoryCounts();
        assertThat(counts).hasSize(11);     // 10 categories + "all"
        assertThat(counts.get("all")).isEqualTo(30L);
        assertThat(counts.get("borsa")).isEqualTo(3L);
    }

    @Test
    void getById_returns_null_when_not_found() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThat(service.getById(99L)).isNull();
    }

    @Test
    void getById_returns_article_when_found() {
        NewsArticle a = article(1L, "T", "Some content", "borsa");
        when(repo.findById(1L)).thenReturn(Optional.of(a));
        assertThat(service.getById(1L)).isSameAs(a);
    }

    @Test
    void fetchContentForArticle_returns_null_when_article_missing() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThat(service.fetchContentForArticle(99L, null)).isNull();
    }

    @Test
    void fetchContentForArticle_skips_fetch_when_content_already_substantial() {
        // Article already has >200 chars of content → no fetch.
        String big = "x".repeat(500);
        NewsArticle a = article(1L, "T", big, "borsa");
        when(repo.findById(1L)).thenReturn(Optional.of(a));

        NewsArticle result = service.fetchContentForArticle(1L, null);
        assertThat(result).isSameAs(a);
        verify(contentFetcher, never()).fetchArticleContent(anyString());
    }

    @Test
    void fetchContentForArticle_skips_when_sourceUrl_blank() {
        NewsArticle a = article(1L, "T", "short", "borsa");
        ReflectionTestUtils.setField(a, "sourceUrl", "");
        when(repo.findById(1L)).thenReturn(Optional.of(a));

        service.fetchContentForArticle(1L, null);
        verify(contentFetcher, never()).fetchArticleContent(anyString());
    }

    @Test
    void fetchContentForArticle_fetches_and_persists_content() {
        NewsArticle a = article(1L, "T", "short", "borsa");
        when(repo.findById(1L)).thenReturn(Optional.of(a));
        when(contentFetcher.fetchArticleContent(anyString())).thenReturn("fresh body");

        service.fetchContentForArticle(1L, null);
        assertThat(a.getContent()).isEqualTo("fresh body");
        verify(repo).save(a);
    }

    @Test
    void fetchContentForArticle_does_not_persist_when_fetched_content_blank() {
        NewsArticle a = article(1L, "T", "short", "borsa");
        when(repo.findById(1L)).thenReturn(Optional.of(a));
        when(contentFetcher.fetchArticleContent(anyString())).thenReturn(" ");

        service.fetchContentForArticle(1L, null);
        verify(repo, never()).save(any());
    }

    @Test
    void fetchAndSaveNews_iterates_over_enabled_feeds_and_runs_cleanup() {
        // No feeds configured → empty loop, then cleanup runs.
        when(feedRepo.findByEnabledTrueOrderByCategoryAscSourceAsc()).thenReturn(List.of());
        when(repo.countByCategory(anyString())).thenReturn(0L);
        when(repo.findTop50ByCategoryOrderByPublishedAtDesc(anyString())).thenReturn(List.of());
        when(repo.findAll()).thenReturn(List.of());

        service.fetchAndSaveNews();
        verify(feedRepo).findByEnabledTrueOrderByCategoryAscSourceAsc();
    }

    @Test
    void cleanupOldNews_does_nothing_when_under_50_per_category() {
        when(repo.countByCategory(anyString())).thenReturn(5L);
        when(repo.findAll()).thenReturn(List.of());
        when(repo.count()).thenReturn(50L);

        Map<String, Object> res = service.cleanupOldNews();
        assertThat(res).containsKey("deletedByCategory");
        assertThat(res.get("totalDeleted")).isEqualTo(0);
        verify(repo, never()).deleteByCategoryAndIdNotIn(anyString(), any());
    }

    @Test
    void cleanupOldNews_deletes_overflow_per_category() {
        // anyString() must come FIRST so the specific "borsa" stub overrides it.
        when(repo.countByCategory(anyString())).thenReturn(0L);
        when(repo.countByCategory("borsa")).thenReturn(100L);
        when(repo.findTop50ByCategoryOrderByPublishedAtDesc("borsa"))
                .thenReturn(List.of(article(1L, "k", "x", "borsa")));
        when(repo.deleteByCategoryAndIdNotIn(anyString(), any())).thenReturn(50);
        when(repo.findAll()).thenReturn(List.of());
        when(repo.count()).thenReturn(50L);

        Map<String, Object> res = service.cleanupOldNews();
        assertThat((Integer) res.get("totalDeleted")).isGreaterThanOrEqualTo(50);
    }

    @Test
    void cleanupOldNews_deletes_articles_in_unknown_category() {
        when(repo.countByCategory(anyString())).thenReturn(0L);
        when(repo.count()).thenReturn(1L);

        NewsArticle straggler = article(99L, "X", "x", "UNKNOWN_CATEGORY");
        NewsArticle nullCat = article(100L, "Y", "y", null);
        when(repo.findAll()).thenReturn(List.of(straggler, nullCat));

        Map<String, Object> res = service.cleanupOldNews();
        verify(repo, times(2)).delete(any(NewsArticle.class));
        assertThat((Integer) res.get("totalDeleted")).isEqualTo(2);
    }

    @Test
    void seedIfEmpty_skips_when_articles_with_content_present() {
        NewsArticle a = article(1L, "T", "x".repeat(300), "borsa");
        when(repo.findAll()).thenReturn(List.of(a));
        service.seedIfEmpty();
        verify(repo, never()).save(any());
        verify(repo, never()).deleteAll(any());
    }

    @Test
    void seedIfEmpty_replaces_short_articles_with_sample_set() {
        NewsArticle short_ = article(1L, "T", "short", "borsa");
        when(repo.findAll()).thenReturn(List.of(short_));

        service.seedIfEmpty();
        verify(repo).deleteAll(List.of(short_));
        // 8 sample articles inserted
        verify(repo, times(8)).save(any(NewsArticle.class));
    }

    @Test
    void seedIfEmpty_inserts_samples_when_db_empty() {
        when(repo.findAll()).thenReturn(List.of());
        service.seedIfEmpty();
        verify(repo, times(8)).save(any(NewsArticle.class));
        verify(repo, never()).deleteAll(any());
    }

    // ── parseItem helper ────────────────────────────────────────────────
    // parseItem() is package-private's the wrong tier — it's `private`, so
    // we go through ReflectionTestUtils.invokeMethod. Each test pins one
    // branch of the helper so we don't have to drag the WebClient into the
    // suite just to exercise the per-<item> parsing.

    private NewsArticle invokeParseItem(String item, List<String> existingTitles) {
        return ReflectionTestUtils.invokeMethod(
                service, "parseItem", item, "borsa", "TestSrc", existingTitles);
    }

    @Test
    void parseItem_returns_null_when_title_missing() {
        // No <title> tag → extractTag returns null → early return.
        NewsArticle result = invokeParseItem(
                "<description>desc</description><link>http://x.com</link>", List.of());
        assertThat(result).isNull();
    }

    @Test
    void parseItem_returns_null_when_title_already_seen() {
        NewsArticle result = invokeParseItem(
                "<title>Existing</title><link>http://x.com</link>",
                List.of("Existing"));
        assertThat(result).isNull();
    }

    @Test
    void parseItem_returns_null_when_link_missing() {
        // Title present but no <link> → can't fetch body → drop.
        NewsArticle result = invokeParseItem(
                "<title>Fresh</title><description>summary</description>", List.of());
        assertThat(result).isNull();
    }

    @Test
    void parseItem_returns_null_when_fetched_content_too_short() {
        when(contentFetcher.fetchArticleContent("http://x.com")).thenReturn("too short");
        NewsArticle result = invokeParseItem(
                "<title>Fresh</title><link>http://x.com</link>", List.of());
        assertThat(result).isNull();
    }

    @Test
    void parseItem_returns_null_when_fetched_content_null() {
        when(contentFetcher.fetchArticleContent("http://x.com")).thenReturn(null);
        NewsArticle result = invokeParseItem(
                "<title>Fresh</title><link>http://x.com</link>", List.of());
        assertThat(result).isNull();
    }

    @Test
    void parseItem_returns_article_when_all_required_fields_present() {
        // 400+ char body satisfies MIN_CONTENT_CHARS.
        String body = "x".repeat(500);
        when(contentFetcher.fetchArticleContent("http://x.com")).thenReturn(body);

        NewsArticle result = invokeParseItem(
                "<title>Fresh Title</title>"
                        + "<link>http://x.com</link>"
                        + "<description>desc snippet</description>"
                        + "<pubDate>Mon, 01 Jan 2026 12:00:00 +0000</pubDate>",
                List.of());

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Fresh Title");
        assertThat(result.getCategory()).isEqualTo("borsa");
        assertThat(result.getSourceName()).isEqualTo("TestSrc");
        assertThat(result.getSourceUrl()).isEqualTo("http://x.com");
        assertThat(result.getContent()).isEqualTo(body);
    }

    @Test
    void parseItem_falls_back_to_title_when_description_blank() {
        // Blank description path → summary = stripped title.
        String body = "y".repeat(500);
        when(contentFetcher.fetchArticleContent("http://x.com")).thenReturn(body);

        NewsArticle result = invokeParseItem(
                "<title>Only Title</title><link>http://x.com</link>", List.of());

        assertThat(result).isNotNull();
        assertThat(result.getSummary()).isEqualTo("Only Title");
    }

    @Test
    void parseItem_truncates_overlong_title_and_content() {
        // Title > 295 chars and body > 10_000 chars exercise the trim branches.
        String longTitle = "T".repeat(400);
        String longBody = "B".repeat(15_000);
        when(contentFetcher.fetchArticleContent("http://x.com")).thenReturn(longBody);

        NewsArticle result = invokeParseItem(
                "<title>" + longTitle + "</title><link>http://x.com</link>", List.of());

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).hasSize(295);
        assertThat(result.getContent()).hasSize(10_000);
    }

    // ── latest(category, lang) overload ─────────────────────────────────

    @Test
    void latest_with_lang_returns_articles_when_translator_disabled() {
        // Translator off → no swap, but the method still executes the
        // normalizeLang + early-return branch.
        when(translator.isEnabled()).thenReturn(false);
        NewsArticle a = article(1L, "T1", "x".repeat(300), "hisse");
        NewsArticle b = article(2L, "T2", "y".repeat(300), "hisse");
        when(repo.findTop50ByCategoryOrderByPublishedAtDesc("hisse")).thenReturn(List.of(a, b));

        assertThat(service.latest("hisse", "tr")).hasSize(2);
        assertThat(service.latest("hisse", "en")).hasSize(2);
    }

    @Test
    void latest_with_lang_applies_translation_swap_when_enabled() {
        // Translator enabled + EN target + TR-source article that already
        // has a cached title_translated → base title swaps in.
        when(translator.isEnabled()).thenReturn(true);
        NewsArticle a = article(1L, "TR başlık", "z".repeat(300), "hisse");
        a.setSourceLang("tr");
        a.setTitleTranslated("English title");
        a.setSummaryTranslated("English summary");
        when(repo.findTop50ByCategoryOrderByPublishedAtDesc("hisse")).thenReturn(List.of(a));

        List<NewsArticle> out = service.latest("hisse", "en");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getTitle()).isEqualTo("English title");
        assertThat(out.get(0).getSummary()).isEqualTo("English summary");
    }

    @Test
    void latest_with_unknown_lang_short_circuits() {
        // "fr" normalizes to null → method returns before doing translation work.
        when(translator.isEnabled()).thenReturn(true);
        when(repo.findTop50ByCategoryOrderByPublishedAtDesc("hisse")).thenReturn(List.of());
        assertThat(service.latest("hisse", "fr")).isEmpty();
    }

    // ── getById(id, lang) overload ──────────────────────────────────────

    @Test
    void getById_with_lang_returns_null_when_not_found() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThat(service.getById(99L, "en")).isNull();
    }

    @Test
    void getById_with_lang_returns_article_when_translator_disabled() {
        when(translator.isEnabled()).thenReturn(false);
        NewsArticle a = article(1L, "T", "content", "hisse");
        when(repo.findById(1L)).thenReturn(Optional.of(a));
        assertThat(service.getById(1L, "en")).isSameAs(a);
    }

    @Test
    void getById_with_null_lang_skips_translation_path() {
        // Translator on but lang=null → normalizeLang returns null → no
        // translation calls happen.
        when(translator.isEnabled()).thenReturn(true);
        NewsArticle a = article(1L, "T", "content", "hisse");
        when(repo.findById(1L)).thenReturn(Optional.of(a));
        assertThat(service.getById(1L, null)).isSameAs(a);
        verify(translator, never()).translate(anyString(), anyString(), anyString());
    }

    // ── normalizeLang(...) private helper ───────────────────────────────

    @Test
    void normalizeLang_recognises_tr_and_en_variants() {
        // Every branch the helper has: null, empty, tr*, en*, unknown.
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "normalizeLang", "tr")).isEqualTo("tr");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "normalizeLang", "TR")).isEqualTo("tr");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "normalizeLang", "tr-TR")).isEqualTo("tr");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "normalizeLang", "en")).isEqualTo("en");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "normalizeLang", "EN")).isEqualTo("en");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "normalizeLang", "en-US")).isEqualTo("en");
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "normalizeLang", (Object) null)).isNull();
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "normalizeLang", "fr")).isNull();
        // Empty string → toLowerCase("") → doesn't startsWith tr/en → null.
        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "normalizeLang", "")).isNull();
    }

    // ── applyTranslationToBaseFields(...) private helper ────────────────

    @Test
    void applyTranslationToBaseFields_is_noop_when_source_equals_target() {
        NewsArticle a = article(1L, "Title", "Content", "hisse");
        a.setSourceLang("tr");
        a.setTitleTranslated("Should NOT swap in");
        ReflectionTestUtils.invokeMethod(service, "applyTranslationToBaseFields", a, "tr");
        // Same lang → no swap.
        assertThat(a.getTitle()).isEqualTo("Title");
    }

    @Test
    void applyTranslationToBaseFields_swaps_in_translated_fields() {
        NewsArticle a = article(1L, "TR title", "TR content", "hisse");
        a.setSourceLang("tr");
        a.setSummary("TR summary");
        a.setTitleTranslated("EN title");
        a.setSummaryTranslated("EN summary");
        a.setContentTranslated("EN content");
        ReflectionTestUtils.invokeMethod(service, "applyTranslationToBaseFields", a, "en");
        assertThat(a.getTitle()).isEqualTo("EN title");
        assertThat(a.getSummary()).isEqualTo("EN summary");
        assertThat(a.getContent()).isEqualTo("EN content");
    }

    @Test
    void applyTranslationToBaseFields_is_noop_when_sourceLang_null() {
        NewsArticle a = article(1L, "Title", "Content", "hisse");
        // sourceLang stays null → method bails out, no swap.
        a.setTitleTranslated("EN title");
        ReflectionTestUtils.invokeMethod(service, "applyTranslationToBaseFields", a, "en");
        assertThat(a.getTitle()).isEqualTo("Title");
    }

    // ── runTranslationPrewarm() ─────────────────────────────────────────

    @Test
    void runTranslationPrewarm_skips_when_translator_disabled() {
        when(translator.isEnabled()).thenReturn(false);
        // Should return quickly without touching the repo.
        service.runTranslationPrewarm();
        verify(repo, never()).findTop50ByTitleTranslatedIsNullAndSourceLangOrderByPublishedAtDesc(anyString());
    }

    @Test
    void runTranslationPrewarm_walks_empty_backlog_without_throwing() {
        when(translator.isEnabled()).thenReturn(true);
        when(repo.findTop50ByTitleTranslatedIsNullAndSourceLangOrderByPublishedAtDesc(anyString()))
                .thenReturn(List.of());
        // Two empty passes (en→tr and tr→en); method exits cleanly.
        service.runTranslationPrewarm();
        verify(repo, times(2)).findTop50ByTitleTranslatedIsNullAndSourceLangOrderByPublishedAtDesc(anyString());
    }

    @Test
    void cleanupOrphanArticles_delegates_to_repo() {
        // Removes news articles whose (sourceName, category) pair no
        // longer matches any feed — used by the admin "Yetim Haberleri
        // Temizle" button plus startup + daily schedulers.
        when(repo.deleteOrphanedArticles()).thenReturn(42);
        int removed = service.cleanupOrphanArticles();
        org.assertj.core.api.Assertions.assertThat(removed).isEqualTo(42);
        verify(repo).deleteOrphanedArticles();
    }

    @Test
    void cleanupOrphanArticles_returns_zero_when_no_orphans() {
        when(repo.deleteOrphanedArticles()).thenReturn(0);
        org.assertj.core.api.Assertions.assertThat(service.cleanupOrphanArticles()).isZero();
    }

    @Test
    void scheduledOrphanCleanup_swallows_exceptions() {
        // The @Lazy self proxy isn't injected in this unit-test setup
        // (no Spring context), so the call to self.cleanupOrphanArticles()
        // NPEs. The method's try/catch should keep it from bubbling —
        // the @Scheduled wrapper must never throw or the scheduler
        // thread dies.
        service.scheduledOrphanCleanup();
    }

    @Test
    void onStartupCleanupOrphans_swallows_exceptions() {
        // Same belt-and-braces as scheduledOrphanCleanup. The
        // ApplicationReadyEvent listener must never throw or the
        // backend boot fails.
        service.onStartupCleanupOrphans();
    }
}

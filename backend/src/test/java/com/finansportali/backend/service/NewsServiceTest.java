package com.finansportali.backend.service;

import com.finansportali.backend.entity.NewsArticle;
import com.finansportali.backend.entity.NewsFeed;
import com.finansportali.backend.repository.NewsArticleRepository;
import com.finansportali.backend.repository.NewsFeedRepository;
import com.finansportali.backend.service.client.news.NewsContentFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    @InjectMocks private NewsService service;

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
        assertThat(service.fetchContentForArticle(99L)).isNull();
    }

    @Test
    void fetchContentForArticle_skips_fetch_when_content_already_substantial() {
        // Article already has >200 chars of content → no fetch.
        String big = "x".repeat(500);
        NewsArticle a = article(1L, "T", big, "borsa");
        when(repo.findById(1L)).thenReturn(Optional.of(a));

        NewsArticle result = service.fetchContentForArticle(1L);
        assertThat(result).isSameAs(a);
        verify(contentFetcher, never()).fetchArticleContent(anyString());
    }

    @Test
    void fetchContentForArticle_skips_when_sourceUrl_blank() {
        NewsArticle a = article(1L, "T", "short", "borsa");
        ReflectionTestUtils.setField(a, "sourceUrl", "");
        when(repo.findById(1L)).thenReturn(Optional.of(a));

        service.fetchContentForArticle(1L);
        verify(contentFetcher, never()).fetchArticleContent(anyString());
    }

    @Test
    void fetchContentForArticle_fetches_and_persists_content() {
        NewsArticle a = article(1L, "T", "short", "borsa");
        when(repo.findById(1L)).thenReturn(Optional.of(a));
        when(contentFetcher.fetchArticleContent(anyString())).thenReturn("fresh body");

        service.fetchContentForArticle(1L);
        assertThat(a.getContent()).isEqualTo("fresh body");
        verify(repo).save(a);
    }

    @Test
    void fetchContentForArticle_does_not_persist_when_fetched_content_blank() {
        NewsArticle a = article(1L, "T", "short", "borsa");
        when(repo.findById(1L)).thenReturn(Optional.of(a));
        when(contentFetcher.fetchArticleContent(anyString())).thenReturn(" ");

        service.fetchContentForArticle(1L);
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
}

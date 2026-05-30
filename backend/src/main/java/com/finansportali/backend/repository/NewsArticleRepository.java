package com.finansportali.backend.repository;

import com.finansportali.backend.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    List<NewsArticle> findTop50ByOrderByPublishedAtDesc();

    List<NewsArticle> findTop50ByCategoryOrderByPublishedAtDesc(String category);

    long countByCategory(String category);

    List<NewsArticle> findByCategoryOrderByPublishedAtDesc(String category);

    /** Bir kategoride id'leri verilen ID listesinin DIŞINDA kalan tüm haberleri siler. */
    @Modifying
    @Query("delete from NewsArticle a where a.category = :category and a.id not in :keepIds")
    int deleteByCategoryAndIdNotIn(@Param("category") String category, @Param("keepIds") List<Long> keepIds);

    /**
     * Cascade-delete articles tied to a removed feed. Matched by
     * (sourceName, category) — same (source, category) pair the feed
     * carries — so removing one Investing.com feed (e.g. Hisse) leaves
     * the other Investing.com feeds (Döviz, Tahvil…) untouched.
     */
    @Modifying
    @Query("delete from NewsArticle a where a.sourceName = :sourceName and a.category = :category")
    int deleteBySourceNameAndCategory(@Param("sourceName") String sourceName, @Param("category") String category);

    /**
     * One-shot cleanup for articles whose (sourceName, category) pair no
     * longer matches any row in news_feeds. Used by the admin "Yetim
     * haberleri temizle" button to remove orphans left behind by feed
     * deletes that happened before the cascade-on-delete fix landed.
     */
    @Modifying
    @Query("""
        delete from NewsArticle a
        where not exists (
            select 1 from NewsFeed f
            where f.source = a.sourceName and f.category = a.category
        )
        """)
    int deleteOrphanedArticles();

    /**
     * Backlog used by the background translation prewarm — articles whose
     * cross-language title cache is still empty. We page in batches of 50
     * to bound memory while the warmer is grinding through a few thousand
     * rows.
     */
    List<NewsArticle> findTop50ByTitleTranslatedIsNullOrderByPublishedAtDesc();

    /**
     * Same backlog, scoped to a specific source language. Used by the
     * prewarm to prefer the smaller English-source pool first: TR readers
     * see English-source articles as "untranslated" most often, so filling
     * that 4k-row pool earns the biggest UX win.
     */
    List<NewsArticle> findTop50ByTitleTranslatedIsNullAndSourceLangOrderByPublishedAtDesc(String sourceLang);
}

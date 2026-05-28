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

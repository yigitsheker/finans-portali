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
}

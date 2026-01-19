package com.finansportali.backend.repo;

import com.finansportali.backend.domain.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    List<NewsArticle> findTop20ByOrderByPublishedAtDesc();

    List<NewsArticle> findTop20ByCategoryOrderByPublishedAtDesc(String category);
}

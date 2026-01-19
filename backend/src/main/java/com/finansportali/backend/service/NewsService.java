package com.finansportali.backend.service;

import com.finansportali.backend.domain.NewsArticle;
import com.finansportali.backend.repo.NewsArticleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class NewsService {

    private final NewsArticleRepository repo;

    public NewsService(NewsArticleRepository repo) {
        this.repo = repo;
    }

    public List<NewsArticle> latest(String category) {
        if (category == null || category.isBlank()) {
            return repo.findTop20ByOrderByPublishedAtDesc();
        }
        return repo.findTop20ByCategoryOrderByPublishedAtDesc(category);
    }

    public void seedIfEmpty() {
        if (repo.count() > 0) return;

        repo.save(new NewsArticle(
                "Market Opening: BIST positive start",
                "BIST opened higher with banking stocks leading.",
                "markets",
                Instant.now().minusSeconds(3600)
        ));

        repo.save(new NewsArticle(
                "USD/TRY volatility update",
                "USD/TRY saw increased volatility after macro data release.",
                "fx",
                Instant.now().minusSeconds(7200)
        ));
    }
}

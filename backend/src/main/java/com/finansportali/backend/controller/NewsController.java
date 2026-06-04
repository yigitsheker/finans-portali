package com.finansportali.backend.controller;

import com.finansportali.backend.entity.NewsArticle;
import com.finansportali.backend.service.NewsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for the financial news feed: latest articles, categories and
 * counts, single-article lookup, on-demand content fetch, and cleanup.
 */
@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final NewsService service;

    public NewsController(NewsService service) {
        this.service = service;
    }

    /** Latest articles, optionally filtered by category and translated to {@code lang}. */
    @GetMapping
    public List<NewsArticle> latest(@RequestParam(required = false) String category,
                                    @RequestParam(required = false) String lang) {
        return service.latest(category, lang);
    }

    /** Distinct news categories available for filtering. */
    @GetMapping("/categories")
    public List<String> getCategories() {
        return service.getCategories();
    }

    /** Article count per category, for sidebar badges. */
    @GetMapping("/category-counts")
    public Map<String, Long> getCategoryCounts() {
        return service.getCategoryCounts();
    }

    /** Single article by id, optionally translated to {@code lang}. */
    @GetMapping("/{id}")
    public NewsArticle getById(@PathVariable Long id,
                               @RequestParam(required = false) String lang) {
        return service.getById(id, lang);
    }

    /** Scrape and persist the full body for an article whose feed only gave a summary. */
    @PostMapping("/{id}/fetch-content")
    public NewsArticle fetchContent(@PathVariable Long id) {
        return service.fetchContentForArticle(id);
    }

    /** Remove stale articles; returns a summary of what was deleted. */
    @PostMapping("/cleanup")
    public Map<String, Object> cleanupOldNews() {
        return service.cleanupOldNews();
    }
}

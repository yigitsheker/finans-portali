package com.finansportali.backend.controller;

import com.finansportali.backend.entity.NewsArticle;
import com.finansportali.backend.service.NewsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final NewsService service;

    public NewsController(NewsService service) {
        this.service = service;
    }

    @GetMapping
    public List<NewsArticle> latest(@RequestParam(required = false) String category) {
        return service.latest(category);
    }

    @GetMapping("/categories")
    public List<String> getCategories() {
        return service.getCategories();
    }

    @GetMapping("/category-counts")
    public Map<String, Long> getCategoryCounts() {
        return service.getCategoryCounts();
    }

    @GetMapping("/{id}")
    public NewsArticle getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping("/{id}/fetch-content")
    public NewsArticle fetchContent(@PathVariable Long id) {
        return service.fetchContentForArticle(id);
    }

    @PostMapping("/cleanup")
    public Map<String, Object> cleanupOldNews() {
        return service.cleanupOldNews();
    }
}

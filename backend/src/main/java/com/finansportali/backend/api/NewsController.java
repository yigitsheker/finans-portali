package com.finansportali.backend.api;

import com.finansportali.backend.domain.NewsArticle;
import com.finansportali.backend.service.NewsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/{id}")
    public NewsArticle getById(@PathVariable Long id) {
        return service.getById(id);
    }
}

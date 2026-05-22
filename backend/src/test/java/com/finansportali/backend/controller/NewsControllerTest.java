package com.finansportali.backend.controller;

import com.finansportali.backend.entity.NewsArticle;
import com.finansportali.backend.service.NewsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class NewsControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private NewsService service;

    private NewsArticle article(String title, String cat) {
        return new NewsArticle(title, "Summary", cat, Instant.parse("2026-05-19T10:00:00Z"),
                "https://example.com/x", "Source A");
    }

    @Test
    void latest_with_no_category_returns_all() throws Exception {
        when(service.latest(null)).thenReturn(List.of(article("Headline", "genel-ekonomi")));

        mvc.perform(get("/api/v1/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Headline"))
                .andExpect(jsonPath("$[0].category").value("genel-ekonomi"));
    }

    @Test
    void latest_with_category_filter_forwards_value() throws Exception {
        when(service.latest("hisse")).thenReturn(List.of());

        mvc.perform(get("/api/v1/news").param("category", "hisse"))
                .andExpect(status().isOk());

        verify(service).latest("hisse");
    }

    @Test
    void categories_returns_string_list() throws Exception {
        when(service.getCategories()).thenReturn(List.of("hisse", "doviz", "kripto"));

        mvc.perform(get("/api/v1/news/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]").value("hisse"));
    }

    @Test
    void category_counts_returns_map() throws Exception {
        when(service.getCategoryCounts()).thenReturn(Map.of("hisse", 12L, "kripto", 5L));

        mvc.perform(get("/api/v1/news/category-counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hisse").value(12))
                .andExpect(jsonPath("$.kripto").value(5));
    }

    @Test
    void get_by_id_routes_path_var() throws Exception {
        when(service.getById(42L)).thenReturn(article("Detail", "tcmb"));

        mvc.perform(get("/api/v1/news/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Detail"));
    }

    @Test
    void fetch_content_routes_path_var() throws Exception {
        when(service.fetchContentForArticle(7L)).thenReturn(article("Fetched", "borsa"));

        mvc.perform(post("/api/v1/news/7/fetch-content").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Fetched"));

        verify(service).fetchContentForArticle(7L);
    }

    @Test
    void cleanup_returns_map_envelope() throws Exception {
        when(service.cleanupOldNews()).thenReturn(Map.of("deleted", 14, "kept", 100));

        mvc.perform(post("/api/v1/news/cleanup").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(14));
    }
}

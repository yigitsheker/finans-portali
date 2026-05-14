package com.finansportali.backend.controller;

import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.repository.NewsArticleRepository;
import com.finansportali.backend.service.MarketService;
import com.finansportali.backend.service.NewsService;
import com.finansportali.backend.service.UserService;
import com.finansportali.backend.service.scheduler.PriceRefreshScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin endpoints for data management and system administration.
 * All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final MarketCandleRepository candleRepo;
    private final MarketQuoteRepository quoteRepo;
    private final MarketInstrumentRepository instrumentRepo;
    private final MarketService marketService;
    private final PriceRefreshScheduler scheduler;
    private final NewsArticleRepository newsRepo;
    private final NewsService newsService;
    private final UserService userService;

    public AdminController(MarketCandleRepository candleRepo,
                           MarketQuoteRepository quoteRepo,
                           MarketInstrumentRepository instrumentRepo,
                           MarketService marketService,
                           PriceRefreshScheduler scheduler,
                           NewsArticleRepository newsRepo,
                           NewsService newsService,
                           UserService userService) {
        this.candleRepo = candleRepo;
        this.quoteRepo = quoteRepo;
        this.instrumentRepo = instrumentRepo;
        this.marketService = marketService;
        this.scheduler = scheduler;
        this.newsRepo = newsRepo;
        this.newsService = newsService;
        this.userService = userService;
    }

    /**
     * Get current admin user information.
     * Useful for testing JWT token claims and role extraction.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCurrentAdmin() {
        return ResponseEntity.ok(Map.of(
                "userId", userService.getCurrentUserId(),
                "username", userService.getCurrentUsername(),
                "email", userService.getCurrentUserEmail() != null ? userService.getCurrentUserEmail() : "",
                "fullName", userService.getCurrentUserFullName() != null ? userService.getCurrentUserFullName() : "",
                "roles", userService.getCurrentUserRoles(),
                "isAdmin", userService.isAdmin()
        ));
    }

    /** 
     * Clears all market data and re-seeds instruments with correct symbols.
     * Requires ADMIN role.
     */
    @PostMapping("/reset-market")
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<String> resetMarket() {
        // FK sırası: önce bağımlı tablolar
        candleRepo.deleteAll();
        quoteRepo.deleteAll();
        instrumentRepo.deleteAll();
        instrumentRepo.flush();
        marketService.seedIfEmpty();
        return ResponseEntity.ok("Market data cleared and re-seeded by " + userService.getCurrentUsername());
    }

    /** 
     * Triggers an immediate price refresh from Twelve Data.
     * Requires ADMIN role.
     */
    @PostMapping("/refresh-prices")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> refreshPrices() {
        scheduler.refreshAll();
        return ResponseEntity.ok("Price refresh triggered by " + userService.getCurrentUsername());
    }

    /** 
     * Clears old news and fetches fresh ones from RSS.
     * Requires ADMIN role.
     */
    @PostMapping("/reset-news")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> resetNews() {
        newsRepo.deleteAll();
        newsService.fetchAndSaveNews();
        return ResponseEntity.ok("News cleared and fetch triggered by " + userService.getCurrentUsername());
    }
}

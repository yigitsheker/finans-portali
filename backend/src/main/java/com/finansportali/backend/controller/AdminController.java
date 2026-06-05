package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.admin.KeycloakUserDto;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import com.finansportali.backend.entity.NewsFeed;
import com.finansportali.backend.repository.NewsArticleRepository;
import com.finansportali.backend.repository.NewsFeedRepository;
import com.finansportali.backend.service.InvestmentFundService;
import com.finansportali.backend.service.KeycloakAdminService;
import com.finansportali.backend.service.MarketService;
import com.finansportali.backend.service.NewsService;
import com.finansportali.backend.service.UserService;
import com.finansportali.backend.service.scheduler.PriceRefreshScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    private final KeycloakAdminService keycloakAdminService;
    private final InvestmentFundService investmentFundService;
    private final NewsFeedRepository feedRepo;

    public AdminController(MarketCandleRepository candleRepo,
                           MarketQuoteRepository quoteRepo,
                           MarketInstrumentRepository instrumentRepo,
                           MarketService marketService,
                           PriceRefreshScheduler scheduler,
                           NewsArticleRepository newsRepo,
                           NewsService newsService,
                           UserService userService,
                           KeycloakAdminService keycloakAdminService,
                           InvestmentFundService investmentFundService,
                           NewsFeedRepository feedRepo) {
        this.candleRepo = candleRepo;
        this.quoteRepo = quoteRepo;
        this.instrumentRepo = instrumentRepo;
        this.marketService = marketService;
        this.scheduler = scheduler;
        this.newsRepo = newsRepo;
        this.newsService = newsService;
        this.userService = userService;
        this.keycloakAdminService = keycloakAdminService;
        this.investmentFundService = investmentFundService;
        this.feedRepo = feedRepo;
    }

    // ── RSS feed management ─────────────────────────────────────────────────

    /** Lightweight view of a configured RSS news feed returned to the admin UI. */
    public record FeedDto(Long id, String url, String category, String source, boolean enabled) {
        static FeedDto from(NewsFeed f) {
            return new FeedDto(f.getId(), f.getUrl(), f.getCategory(), f.getSource(), f.isEnabled());
        }
    }

    /** Payload for registering a new RSS feed; category and source may be blank (defaults applied). */
    public record CreateFeedRequest(String url, String category, String source) {}

    /** List all configured RSS feeds, ordered by category then source. */
    @GetMapping("/feeds")
    @PreAuthorize("hasRole('ADMIN')")
    public List<FeedDto> listFeeds() {
        return feedRepo.findAllByOrderByCategoryAscSourceAsc().stream().map(FeedDto::from).toList();
    }

    /** Register a new feed; rejects blank URLs (400) and duplicates (409). */
    @PostMapping("/feeds")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeedDto> addFeed(@RequestBody CreateFeedRequest req) {
        if (req == null || req.url() == null || req.url().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String url = req.url().trim();
        if (feedRepo.findByUrl(url).isPresent()) {
            return ResponseEntity.status(409).build();   // duplicate
        }
        NewsFeed saved = feedRepo.save(new NewsFeed(
                url,
                blankToDefault(req.category(), "diger"),
                blankToDefault(req.source(), "Custom")
        ));
        return ResponseEntity.ok(FeedDto.from(saved));
    }

    /** Flip a feed's enabled flag; returns 404 if the feed does not exist. */
    @PostMapping("/feeds/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeedDto> toggleFeed(@PathVariable Long id) {
        return feedRepo.findById(id)
                .map(f -> {
                    f.setEnabled(!f.isEnabled());
                    f.setUpdatedAt(java.time.LocalDateTime.now());
                    NewsFeed saved = feedRepo.save(f);
                    // Feed turned OFF → immediately drop its already-fetched news
                    // (anything no longer covered by an enabled feed). Reuses the
                    // enabled-aware sweep so a (source, category) still served by
                    // another enabled feed is preserved.
                    if (!saved.isEnabled()) {
                        newsService.cleanupOrphanArticles();
                    }
                    return ResponseEntity.ok(FeedDto.from(saved));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/feeds/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> deleteFeed(@PathVariable Long id) {
        // Cascade through the articles before dropping the feed row. Without
        // this the news listing kept showing every article the removed feed
        // had ever produced — admin sees Investing.com pieces survive a
        // feed delete and assumes the toggle didn't take. Match on
        // (sourceName, category) so other feeds from the same source in
        // OTHER categories aren't collaterally wiped.
        var feedOpt = feedRepo.findById(id);
        if (feedOpt.isEmpty()) return ResponseEntity.notFound().build();
        var feed = feedOpt.get();
        int removed = newsRepo.deleteBySourceNameAndCategory(feed.getSource(), feed.getCategory());
        feedRepo.deleteById(id);
        if (removed > 0) {
            // Light audit so admins can see the cascade size in logs.
            org.slf4j.LoggerFactory.getLogger(AdminController.class)
                    .info("Deleted feed id={} (source={}, category={}) + cascaded {} articles",
                            id, feed.getSource(), feed.getCategory(), removed);
        }
        return ResponseEntity.noContent().build();
    }

    private static String blankToDefault(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    /**
     * Manual trigger for the orphan-article sweep. Logic lives in
     * NewsService (also wired to startup + a daily @Scheduled), this
     * endpoint just exposes a button for admins.
     */
    @PostMapping("/feeds/cleanup-orphans")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> cleanupOrphanArticles() {
        return Map.of("removed", newsService.cleanupOrphanArticles());
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
     * Wipes all news and re-fetches from every enabled RSS feed in the
     * background (a full fetch can exceed the proxy read timeout). Requires ADMIN.
     */
    @PostMapping("/reset-news")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> resetNews() {
        long removed = newsRepo.count();
        newsRepo.deleteAll();
        newsService.triggerManualFetchAsync();
        return ResponseEntity.ok(removed + " haber silindi. Baştan çekme arka planda başlatıldı; "
                + "haberler birkaç dakika içinde görünecek.");
    }

    /**
     * Manually triggers a news fetch WITHOUT wiping existing articles — fills
     * under-stocked categories (e.g. after re-enabling a feed). Runs in the
     * background and returns immediately. Requires ADMIN.
     */
    @PostMapping("/refresh-news")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> refreshNews() {
        newsService.triggerManualFetchAsync();
        return ResponseEntity.ok("Haber çekme arka planda başlatıldı; "
                + "yeni haberler birkaç dakika içinde görünecek.");
    }

    /**
     * Wipe all investment funds (including demo seed) and re-fetch from TEFAS public API.
     * Requires ADMIN role.
     */
    @PostMapping("/reset-funds")
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> resetFunds() {
        int wiped = investmentFundService.wipeAll();
        investmentFundService.updateFundPrices();
        long after = investmentFundService.getAllFunds().size();
        return ResponseEntity.ok(Map.of(
                "wiped", wiped,
                "fundsAfter", after,
                "actor", userService.getCurrentUsername(),
                "source", "TEFAS public API"
        ));
    }

    // ── User management (Keycloak) ────────────────────────────────────────

    /** Page through Keycloak users, optionally filtered by a search term. */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<KeycloakUserDto>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int first,
            @RequestParam(defaultValue = "100") int max) {
        return ResponseEntity.ok(keycloakAdminService.listUsers(search, first, max));
    }

    /** Disable a Keycloak user account, blocking further logins. */
    @PostMapping("/users/{id}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> banUser(@PathVariable String id) {
        keycloakAdminService.setUserEnabled(id, false);
        return ResponseEntity.ok(Map.of(
                "id", id,
                "enabled", false,
                "actor", userService.getCurrentUsername()
        ));
    }

    /** Re-enable a previously banned Keycloak user account. */
    @PostMapping("/users/{id}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> unbanUser(@PathVariable String id) {
        keycloakAdminService.setUserEnabled(id, true);
        return ResponseEntity.ok(Map.of(
                "id", id,
                "enabled", true,
                "actor", userService.getCurrentUsername()
        ));
    }

    /**
     * Force a user to configure TOTP on next login.
     */
    @PostMapping("/users/{id}/require-2fa")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> require2fa(@PathVariable String id) {
        keycloakAdminService.addRequiredAction(id, "CONFIGURE_TOTP");
        return ResponseEntity.ok(Map.of(
                "id", id,
                "requiredAction", "CONFIGURE_TOTP",
                "actor", userService.getCurrentUsername()
        ));
    }

    /**
     * Remove all OTP/TOTP credentials for a user (e.g. user lost their device).
     * After this, if 2FA was forced, you may also want to call /require-2fa again.
     */
    @PostMapping("/users/{id}/reset-2fa")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reset2fa(@PathVariable String id) {
        keycloakAdminService.removeTotpCredentials(id);
        return ResponseEntity.ok(Map.of(
                "id", id,
                "action", "TOTP credentials cleared",
                "actor", userService.getCurrentUsername()
        ));
    }
}

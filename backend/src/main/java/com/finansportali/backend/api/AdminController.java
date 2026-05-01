package com.finansportali.backend.api;

import com.finansportali.backend.repo.MarketCandleRepository;
import com.finansportali.backend.repo.MarketInstrumentRepository;
import com.finansportali.backend.repo.MarketQuoteRepository;
import com.finansportali.backend.repo.NewsArticleRepository;
import com.finansportali.backend.service.MarketService;
import com.finansportali.backend.service.NewsService;
import com.finansportali.backend.service.PriceRefreshScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Temporary admin endpoints for data management.
 * Only accessible locally - remove in production.
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

    public AdminController(MarketCandleRepository candleRepo,
                           MarketQuoteRepository quoteRepo,
                           MarketInstrumentRepository instrumentRepo,
                           MarketService marketService,
                           PriceRefreshScheduler scheduler,
                           NewsArticleRepository newsRepo,
                           NewsService newsService) {
        this.candleRepo = candleRepo;
        this.quoteRepo = quoteRepo;
        this.instrumentRepo = instrumentRepo;
        this.marketService = marketService;
        this.scheduler = scheduler;
        this.newsRepo = newsRepo;
        this.newsService = newsService;
    }

    /** Clears all market data and re-seeds instruments with correct symbols */
    @PostMapping("/reset-market")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<String> resetMarket() {
        // FK sırası: önce bağımlı tablolar
        candleRepo.deleteAll();
        quoteRepo.deleteAll();
        instrumentRepo.deleteAll();
        instrumentRepo.flush();
        marketService.seedIfEmpty();
        return ResponseEntity.ok("Market data cleared and re-seeded.");
    }

    /** Triggers an immediate price refresh from Twelve Data */
    @PostMapping("/refresh-prices")
    public ResponseEntity<String> refreshPrices() {
        scheduler.refreshAll();
        return ResponseEntity.ok("Price refresh triggered.");
    }

    /** Clears old news and fetches fresh ones from RSS */
    @PostMapping("/reset-news")
    public ResponseEntity<String> resetNews() {
        newsRepo.deleteAll();
        newsService.fetchAndSaveNews();
        return ResponseEntity.ok("News cleared and fetch triggered.");
    }
}

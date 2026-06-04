package com.finansportali.backend.service;

import com.finansportali.backend.entity.Watchlist;
import com.finansportali.backend.entity.WatchlistItem;
import com.finansportali.backend.dto.response.alert.WatchlistDto;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.WatchlistItemRepository;
import com.finansportali.backend.repository.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages user watchlists and their symbol membership. Owns CRUD over
 * watchlists/items, enforces per-user ownership on every operation, and maps
 * entities to {@link WatchlistDto} for the API layer.
 */
@Service
public class WatchlistService {
    
    private static final Logger log = LoggerFactory.getLogger(WatchlistService.class);
    
    private final WatchlistRepository watchlistRepo;
    private final WatchlistItemRepository itemRepo;
    private final MarketInstrumentRepository instrumentRepo;
    
    public WatchlistService(WatchlistRepository watchlistRepo,
                           WatchlistItemRepository itemRepo,
                           MarketInstrumentRepository instrumentRepo) {
        this.watchlistRepo = watchlistRepo;
        this.itemRepo = itemRepo;
        this.instrumentRepo = instrumentRepo;
    }
    
    /**
     * Get all watchlists for a user
     */
    public List<WatchlistDto> getUserWatchlists(String userId) {
        log.info("Fetching watchlists for user: {}", userId);
        List<Watchlist> watchlists = watchlistRepo.findByUserIdOrderByCreatedAtDesc(userId);
        
        return watchlists.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Get a specific watchlist
     */
    public WatchlistDto getWatchlist(String userId, Long watchlistId) {
        log.info("Fetching watchlist {} for user: {}", watchlistId, userId);
        Watchlist watchlist = watchlistRepo.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist not found: " + watchlistId));
        
        return toDto(watchlist);
    }
    
    /**
     * Create a new watchlist
     */
    @Transactional
    public WatchlistDto createWatchlist(String userId, String name) {
        log.info("Creating watchlist '{}' for user: {}", name, userId);
        
        Watchlist watchlist = new Watchlist(userId, name);
        watchlist = watchlistRepo.save(watchlist);
        
        return toDto(watchlist);
    }
    
    /**
     * Update watchlist name
     */
    @Transactional
    public WatchlistDto updateWatchlist(String userId, Long watchlistId, String newName) {
        log.info("Updating watchlist {} to '{}' for user: {}", watchlistId, newName, userId);
        
        Watchlist watchlist = watchlistRepo.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist not found: " + watchlistId));
        
        watchlist.setName(newName);
        watchlist = watchlistRepo.save(watchlist);
        
        return toDto(watchlist);
    }
    
    /**
     * Delete a watchlist
     */
    @Transactional
    public void deleteWatchlist(String userId, Long watchlistId) {
        log.info("Deleting watchlist {} for user: {}", watchlistId, userId);
        
        // First delete all items
        itemRepo.deleteByWatchlistId(watchlistId);
        
        // Then delete the watchlist
        long deleted = watchlistRepo.deleteByIdAndUserId(watchlistId, userId);
        if (deleted == 0) {
            throw new IllegalArgumentException("Watchlist not found: " + watchlistId);
        }
    }
    
    /**
     * Add a symbol to watchlist
     */
    @Transactional
    public void addToWatchlist(String userId, Long watchlistId, String symbol) {
        log.info("Adding {} to watchlist {} for user: {}", symbol, watchlistId, userId);
        
        // Verify watchlist belongs to user
        Watchlist watchlist = watchlistRepo.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist not found: " + watchlistId));
        
        // Verify symbol exists
        instrumentRepo.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + symbol));
        
        // Check if already exists
        if (itemRepo.findByWatchlistIdAndSymbol(watchlistId, symbol).isPresent()) {
            log.warn("Symbol {} already in watchlist {}", symbol, watchlistId);
            return;
        }
        
        // Add item
        WatchlistItem item = new WatchlistItem(watchlistId, symbol);
        itemRepo.save(item);
        
        log.info("Added {} to watchlist {}", symbol, watchlistId);
    }
    
    /**
     * Remove a symbol from watchlist
     */
    @Transactional
    public void removeFromWatchlist(String userId, Long watchlistId, String symbol) {
        log.info("Removing {} from watchlist {} for user: {}", symbol, watchlistId, userId);
        
        // Verify watchlist belongs to user
        watchlistRepo.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Watchlist not found: " + watchlistId));
        
        long deleted = itemRepo.deleteByWatchlistIdAndSymbol(watchlistId, symbol);
        if (deleted == 0) {
            log.warn("Symbol {} not found in watchlist {}", symbol, watchlistId);
        }
    }
    
    /**
     * Convert Watchlist entity to DTO
     */
    private WatchlistDto toDto(Watchlist watchlist) {
        List<String> symbols = itemRepo.findByWatchlistIdOrderByAddedAtDesc(watchlist.getId())
                .stream()
                .map(WatchlistItem::getSymbol)
                .collect(Collectors.toList());
        
        return new WatchlistDto(
                watchlist.getId(),
                watchlist.getName(),
                watchlist.getCreatedAt(),
                watchlist.getUpdatedAt(),
                symbols
        );
    }
}

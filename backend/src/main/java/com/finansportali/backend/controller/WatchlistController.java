package com.finansportali.backend.controller;

import com.finansportali.backend.dto.request.AddToWatchlistRequest;
import com.finansportali.backend.dto.request.CreateWatchlistRequest;
import com.finansportali.backend.dto.response.alert.WatchlistDto;
import com.finansportali.backend.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for managing the authenticated user's watchlists and the
 * symbols within them. The user is resolved from the JWT so callers can only
 * access their own watchlists.
 */
@RestController
@RequestMapping("/api/v1/watchlists")
public class WatchlistController {
    
    private final WatchlistService service;
    
    public WatchlistController(WatchlistService service) {
        this.service = service;
    }
    
    private String userId(Jwt jwt) {
        return jwt.getSubject();
    }
    
    /**
     * Get all watchlists for current user
     */
    @GetMapping
    public List<WatchlistDto> getUserWatchlists(@AuthenticationPrincipal Jwt jwt) {
        return service.getUserWatchlists(userId(jwt));
    }
    
    /**
     * Get a specific watchlist
     */
    @GetMapping("/{id}")
    public WatchlistDto getWatchlist(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable Long id) {
        return service.getWatchlist(userId(jwt), id);
    }
    
    /**
     * Create a new watchlist
     */
    @PostMapping
    public ResponseEntity<WatchlistDto> createWatchlist(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateWatchlistRequest request) {
        WatchlistDto watchlist = service.createWatchlist(userId(jwt), request.name());
        return ResponseEntity.ok(watchlist);
    }
    
    /**
     * Update watchlist name
     */
    @PutMapping("/{id}")
    public ResponseEntity<WatchlistDto> updateWatchlist(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody CreateWatchlistRequest request) {
        WatchlistDto watchlist = service.updateWatchlist(userId(jwt), id, request.name());
        return ResponseEntity.ok(watchlist);
    }
    
    /**
     * Delete a watchlist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWatchlist(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        service.deleteWatchlist(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Add a symbol to watchlist
     */
    @PostMapping("/items")
    public ResponseEntity<Void> addToWatchlist(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddToWatchlistRequest request) {
        service.addToWatchlist(userId(jwt), request.watchlistId(), request.symbol());
        return ResponseEntity.ok().build();
    }
    
    /**
     * Remove a symbol from watchlist
     */
    @DeleteMapping("/{watchlistId}/items/{symbol}")
    public ResponseEntity<Void> removeFromWatchlist(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long watchlistId,
            @PathVariable String symbol) {
        service.removeFromWatchlist(userId(jwt), watchlistId, symbol);
        return ResponseEntity.noContent().build();
    }
}

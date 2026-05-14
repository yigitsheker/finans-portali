package com.finansportali.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddToWatchlistRequest(
        @NotNull(message = "Watchlist ID is required")
        Long watchlistId,
        
        @NotBlank(message = "Symbol is required")
        String symbol
) {}

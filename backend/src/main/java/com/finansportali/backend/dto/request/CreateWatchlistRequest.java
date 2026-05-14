package com.finansportali.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateWatchlistRequest(
        @NotBlank(message = "Watchlist name is required")
        String name
) {}

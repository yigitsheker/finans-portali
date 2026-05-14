package com.finansportali.backend.dto.response.alert;

import java.time.LocalDateTime;
import java.util.List;

public record WatchlistDto(
        Long id,
        String name,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> symbols
) {}

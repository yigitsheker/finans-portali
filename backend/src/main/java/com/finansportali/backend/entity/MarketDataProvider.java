package com.finansportali.backend.entity;

public enum MarketDataProvider {
    YAHOO,        // finance.yahoo.com — birincil kaynak
    TWELVE_DATA,  // twelvedata.com — artık kullanılmıyor (legacy)
    NONE          // Veri kaynağı yok (seed/fallback)
}

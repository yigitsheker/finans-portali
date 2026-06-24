package com.finansportali.backend.entity;

public enum MarketDataProvider {
    YAHOO,        // finance.yahoo.com — birincil kaynak
    TWELVE_DATA,  // twelvedata.com — artık kullanılmıyor (legacy)
    TCMB,         // evds3.tcmb.gov.tr — TR devlet tahvili gösterge getirileri
    NONE          // Veri kaynağı yok (seed/fallback)
}

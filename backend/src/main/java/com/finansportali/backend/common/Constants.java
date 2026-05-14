package com.finansportali.backend.common;

/**
 * Application-wide constants
 */
public final class Constants {
    
    private Constants() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    // API Endpoints
    public static final String API_V1_BASE = "/api/v1";
    
    // Date/Time Formats
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    
    // Currency
    public static final String CURRENCY_TRY = "TRY";
    public static final String CURRENCY_USD = "USD";
    public static final String CURRENCY_EUR = "EUR";
    
    // Default Values
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    
    // Cache Names
    public static final String CACHE_MARKET_QUOTES = "marketQuotes";
    public static final String CACHE_EXCHANGE_RATES = "exchangeRates";
    public static final String CACHE_NEWS = "news";
    
    // Performance Ranges
    public static final String RANGE_1D = "1D";
    public static final String RANGE_5D = "5D";
    public static final String RANGE_1M = "1M";
    public static final String RANGE_3M = "3M";
    public static final String RANGE_1Y = "1Y";
    public static final String RANGE_ALL = "ALL";
}

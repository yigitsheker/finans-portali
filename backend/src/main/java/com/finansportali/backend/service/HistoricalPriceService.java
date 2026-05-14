package com.finansportali.backend.service;

import com.finansportali.backend.entity.HistoricalPrice;
import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.repository.HistoricalPriceRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.service.client.market.YahooPriceFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class HistoricalPriceService {
    
    private static final Logger log = LoggerFactory.getLogger(HistoricalPriceService.class);
    
    private final HistoricalPriceRepository historicalPriceRepo;
    private final MarketInstrumentRepository instrumentRepo;
    private final YahooPriceFetcher yahooPriceFetcher;
    private final MarketService marketService;
    
    public HistoricalPriceService(HistoricalPriceRepository historicalPriceRepo,
                                  MarketInstrumentRepository instrumentRepo,
                                  YahooPriceFetcher yahooPriceFetcher,
                                  MarketService marketService) {
        this.historicalPriceRepo = historicalPriceRepo;
        this.instrumentRepo = instrumentRepo;
        this.yahooPriceFetcher = yahooPriceFetcher;
        this.marketService = marketService;
    }
    
    /**
     * Get historical prices for a symbol between dates.
     * Fetches from database if available, otherwise fetches from Yahoo and caches.
     */
    @Transactional
    public List<HistoricalPrice> getHistoricalPrices(String symbol, LocalDate fromDate, LocalDate toDate) {
        log.info("Getting historical prices for symbol={} from={} to={}", symbol, fromDate, toDate);
        
        // Check if we have data in database
        List<HistoricalPrice> cached = historicalPriceRepo
                .findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(symbol, fromDate, toDate);
        
        long expectedDays = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        long cachedDays = cached.size();
        
        // If we have less than 50% of expected data, fetch from Yahoo
        if (cachedDays < expectedDays * 0.5) {
            log.info("Insufficient cached data ({}/{} days), fetching from Yahoo", cachedDays, expectedDays);
            fetchAndCacheFromYahoo(symbol, fromDate, toDate);
            cached = historicalPriceRepo
                    .findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(symbol, fromDate, toDate);
        }
        
        log.info("Returning {} historical price points for {}", cached.size(), symbol);
        return cached;
    }
    
    /**
     * Get the close price on or before a specific date.
     * Returns the most recent available price if exact date not found.
     */
    public Optional<BigDecimal> getClosePriceOnOrBefore(String symbol, LocalDate date) {
        return historicalPriceRepo.findLatestPriceOnOrBefore(symbol, date)
                .map(hp -> hp.getAdjustedClosePrice() != null ? 
                        hp.getAdjustedClosePrice() : hp.getClosePrice());
    }
    
    /**
     * Fetch historical prices from Yahoo Finance and cache them.
     */
    @Transactional
    public void fetchAndCacheFromYahoo(String symbol, LocalDate fromDate, LocalDate toDate) {
        try {
            log.info("Fetching historical data from Yahoo for symbol={} from={} to={}", 
                    symbol, fromDate, toDate);
            
            // Get instrument to find Yahoo symbol
            MarketInstrument instrument = instrumentRepo.findBySymbol(symbol)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown symbol: " + symbol));
            
            // Normalize symbol for Yahoo
            String yahooSymbol = marketService.normalizeSymbolForYahoo(
                    instrument.getSymbol(), instrument.getInstrumentType());
            
            log.info("Using Yahoo symbol: {} for app symbol: {}", yahooSymbol, symbol);
            
            // Calculate period for Yahoo API
            long days = java.time.temporal.ChronoUnit.DAYS.between(fromDate, LocalDate.now());
            String range;
            if (days <= 7) range = "7d";
            else if (days <= 30) range = "1mo";
            else if (days <= 90) range = "3mo";
            else if (days <= 180) range = "6mo";
            else if (days <= 365) range = "1y";
            else if (days <= 730) range = "2y";
            else range = "5y";
            
            String interval = "1d"; // Daily data
            
            // Fetch from Yahoo
            List<YahooPriceFetcher.DayClose> yahooData = yahooPriceFetcher.fetchHistory(
                    yahooSymbol, range, interval);
            
            if (yahooData.isEmpty()) {
                log.warn("No data received from Yahoo for {}", yahooSymbol);
                return;
            }
            
            log.info("Received {} data points from Yahoo", yahooData.size());
            
            // Convert and save to database
            int saved = 0;
            for (YahooPriceFetcher.DayClose dayClose : yahooData) {
                LocalDate priceDate = dayClose.day(); // Already a LocalDate
                
                // Skip if already exists
                if (historicalPriceRepo.existsBySymbolAndPriceDate(symbol, priceDate)) {
                    continue;
                }
                
                HistoricalPrice hp = new HistoricalPrice();
                hp.setSymbol(symbol);
                hp.setPriceDate(priceDate);
                hp.setClosePrice(dayClose.close());
                hp.setAdjustedClosePrice(dayClose.close()); // Yahoo already returns adjusted
                
                historicalPriceRepo.save(hp);
                saved++;
            }
            
            log.info("Saved {} new historical price records for {}", saved, symbol);
            
        } catch (Exception e) {
            log.error("Failed to fetch historical data from Yahoo for symbol={}: {}", 
                    symbol, e.getMessage(), e);
        }
    }
}

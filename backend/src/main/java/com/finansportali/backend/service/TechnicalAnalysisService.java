package com.finansportali.backend.service;

import com.finansportali.backend.entity.HistoricalPrice;
import com.finansportali.backend.dto.response.TechnicalAnalysisResponse;
import com.finansportali.backend.dto.response.TrendDto;
import com.finansportali.backend.dto.response.SeriesPointDto;
import com.finansportali.backend.dto.response.SummaryDto;
import com.finansportali.backend.repository.HistoricalPriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TechnicalAnalysisService {
    
    private static final Logger log = LoggerFactory.getLogger(TechnicalAnalysisService.class);
    
    private final HistoricalPriceRepository historicalPriceRepo;
    private final HistoricalPriceService historicalPriceService;
    
    public TechnicalAnalysisService(HistoricalPriceRepository historicalPriceRepo,
                                   HistoricalPriceService historicalPriceService) {
        this.historicalPriceRepo = historicalPriceRepo;
        this.historicalPriceService = historicalPriceService;
    }
    
    /**
     * Get comprehensive technical analysis for a symbol
     */
    public TechnicalAnalysisResponse getTechnicalAnalysis(String symbol, LocalDate fromDate, LocalDate toDate) {
        log.info("[TechnicalAnalysis] Requested for symbol={} from={} to={}", symbol, fromDate, toDate);
        
        // Fetch historical prices (will fetch from Yahoo if not cached)
        List<HistoricalPrice> prices = historicalPriceService.getHistoricalPrices(symbol, fromDate, toDate);
        
        if (prices.isEmpty()) {
            log.warn("[TechnicalAnalysis] No historical data found for symbol={}", symbol);
            return createInsufficientDataResponse(symbol, fromDate, toDate);
        }
        
        log.info("[TechnicalAnalysis] Processing {} data points for {}", prices.size(), symbol);
        
        // Calculate trend
        TrendDto trend = calculateTrend(prices);
        
        // Calculate summary statistics
        SummaryDto summary = calculateSummary(prices);
        
        // Calculate series with moving averages
        List<SeriesPointDto> series = calculateSeries(prices);
        
        return new TechnicalAnalysisResponse(
            symbol,
            fromDate.toString(),
            toDate.toString(),
            trend,
            summary,
            series
        );
    }
    
    /**
     * Calculate Simple Moving Average (SMA)
     */
    private Double calculateSMA(List<HistoricalPrice> prices, int index, int period) {
        if (index < period - 1) {
            return null; // Not enough data points
        }
        
        double sum = 0.0;
        for (int i = index - period + 1; i <= index; i++) {
            BigDecimal closePrice = getClosePrice(prices.get(i));
            sum += closePrice.doubleValue();
        }
        
        return sum / period;
    }
    
    /**
     * Calculate trend using linear regression
     */
    private TrendDto calculateTrend(List<HistoricalPrice> prices) {
        if (prices.size() < 2) {
            return new TrendDto("INSUFFICIENT_DATA", 0.0, 0.0, 
                "Trend analizi için yeterli veri yok.");
        }
        
        // Linear regression: y = mx + b
        int n = prices.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = getClosePrice(prices.get(i)).doubleValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        // Calculate slope (m)
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        
        // Calculate percentage change
        BigDecimal firstPrice = getClosePrice(prices.get(0));
        BigDecimal lastPrice = getClosePrice(prices.get(n - 1));
        double changePercent = ((lastPrice.doubleValue() - firstPrice.doubleValue()) / firstPrice.doubleValue()) * 100;
        
        // Determine trend direction
        String direction;
        String description;
        double slopeThreshold = 0.01; // Adjust based on price scale
        
        if (slope > slopeThreshold) {
            direction = "UPWARD";
            description = "Seçilen aralıkta yükselen trend gözlemleniyor.";
        } else if (slope < -slopeThreshold) {
            direction = "DOWNWARD";
            description = "Seçilen aralıkta düşen trend gözlemleniyor.";
        } else {
            direction = "SIDEWAYS";
            description = "Seçilen aralıkta yatay trend gözlemleniyor.";
        }
        
        log.info("[TechnicalAnalysis] Trend: direction={}, slope={}, changePercent={}", 
                 direction, slope, changePercent);
        
        return new TrendDto(direction, slope, changePercent, description);
    }
    
    /**
     * Calculate summary statistics
     */
    private SummaryDto calculateSummary(List<HistoricalPrice> prices) {
        if (prices.isEmpty()) {
            return new SummaryDto(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        
        double sum = 0.0;
        double highest = Double.MIN_VALUE;
        double lowest = Double.MAX_VALUE;
        
        for (HistoricalPrice price : prices) {
            double closeValue = getClosePrice(price).doubleValue();
            sum += closeValue;
            highest = Math.max(highest, closeValue);
            lowest = Math.min(lowest, closeValue);
        }
        
        double average = sum / prices.size();
        double latest = getClosePrice(prices.get(prices.size() - 1)).doubleValue();
        
        // Calculate volatility (standard deviation as percentage of mean)
        double sumSquaredDiff = 0.0;
        for (HistoricalPrice price : prices) {
            double diff = getClosePrice(price).doubleValue() - average;
            sumSquaredDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSquaredDiff / prices.size());
        double volatilityPercent = (stdDev / average) * 100;
        
        log.info("[TechnicalAnalysis] Summary: latest={}, high={}, low={}, avg={}, volatility={}%", 
                 latest, highest, lowest, average, volatilityPercent);
        
        return new SummaryDto(latest, highest, lowest, average, volatilityPercent);
    }
    
    /**
     * Calculate series with moving averages
     */
    private List<SeriesPointDto> calculateSeries(List<HistoricalPrice> prices) {
        List<SeriesPointDto> series = new ArrayList<>();
        
        for (int i = 0; i < prices.size(); i++) {
            HistoricalPrice price = prices.get(i);
            Double close = getClosePrice(price).doubleValue();
            Double sma7 = calculateSMA(prices, i, 7);
            Double sma20 = calculateSMA(prices, i, 20);
            Double sma50 = calculateSMA(prices, i, 50);
            
            series.add(new SeriesPointDto(
                price.getPriceDate().toString(),
                close,
                sma7,
                sma20,
                sma50
            ));
        }
        
        return series;
    }
    
    /**
     * Get close price (prefer adjusted close if available)
     */
    private BigDecimal getClosePrice(HistoricalPrice price) {
        return price.getAdjustedClosePrice() != null ? 
               price.getAdjustedClosePrice() : price.getClosePrice();
    }
    
    /**
     * Create response for insufficient data
     */
    private TechnicalAnalysisResponse createInsufficientDataResponse(String symbol, LocalDate fromDate, LocalDate toDate) {
        TrendDto trend = new TrendDto("INSUFFICIENT_DATA", 0.0, 0.0, 
            "Bu enstrüman için teknik analiz oluşturacak yeterli tarihsel veri bulunamadı.");
        SummaryDto summary = new SummaryDto(0.0, 0.0, 0.0, 0.0, 0.0);
        
        return new TechnicalAnalysisResponse(
            symbol,
            fromDate.toString(),
            toDate.toString(),
            trend,
            summary,
            new ArrayList<>()
        );
    }
    
    // Legacy methods for backward compatibility
    public Map<String, Object> calculateMovingAverages(String symbol, int period) {
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusMonths(3);
        TechnicalAnalysisResponse response = getTechnicalAnalysis(symbol, fromDate, toDate);
        return Map.of("data", response);
    }
    
    public Map<String, Object> analyzeTrend(String symbol) {
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusMonths(3);
        TechnicalAnalysisResponse response = getTechnicalAnalysis(symbol, fromDate, toDate);
        return Map.of("trend", response.getTrend());
    }
    
    public Map<String, Object> calculateSupportResistance(String symbol) {
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusMonths(3);
        TechnicalAnalysisResponse response = getTechnicalAnalysis(symbol, fromDate, toDate);
        return Map.of("support", response.getSummary().getLowestClose(), 
                     "resistance", response.getSummary().getHighestClose());
    }
    
    public Map<String, Object> calculateMomentum(String symbol, int period) {
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusMonths(3);
        TechnicalAnalysisResponse response = getTechnicalAnalysis(symbol, fromDate, toDate);
        return Map.of("momentum", response.getTrend().getChangePercent());
    }
}

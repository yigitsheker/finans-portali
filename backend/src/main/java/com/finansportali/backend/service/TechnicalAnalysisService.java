package com.finansportali.backend.service;

import com.finansportali.backend.domain.MarketCandle;
import com.finansportali.backend.repo.MarketCandleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TechnicalAnalysisService {

    private final MarketCandleRepository candleRepository;

    public TechnicalAnalysisService(MarketCandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    /**
     * Hareketli Ortalama (Moving Average) hesaplama
     */
    public Map<String, Object> calculateMovingAverages(String symbol, int period) {
        List<MarketCandle> candles = candleRepository.findTop100ByInstrument_SymbolOrderByDayDesc(symbol);
        
        if (candles.size() < period) {
            return Map.of("error", "Insufficient data for MA calculation");
        }

        List<BigDecimal> prices = candles.stream()
                .map(MarketCandle::getClose)
                .collect(Collectors.toList());

        List<BigDecimal> sma = calculateSMA(prices, period);
        List<BigDecimal> ema = calculateEMA(prices, period);

        return Map.of(
                "symbol", symbol,
                "period", period,
                "sma", sma.subList(0, Math.min(20, sma.size())), // Son 20 değer
                "ema", ema.subList(0, Math.min(20, ema.size())),
                "current_price", prices.get(0),
                "sma_current", sma.isEmpty() ? null : sma.get(0),
                "ema_current", ema.isEmpty() ? null : ema.get(0)
        );
    }

    /**
     * Basit trend analizi
     */
    public Map<String, Object> analyzeTrend(String symbol) {
        List<MarketCandle> candles = candleRepository.findTop50ByInstrument_SymbolOrderByDayDesc(symbol);
        
        if (candles.size() < 10) {
            return Map.of("error", "Insufficient data for trend analysis");
        }

        List<BigDecimal> prices = candles.stream()
                .map(MarketCandle::getClose)
                .collect(Collectors.toList());

        // Son 5 ve önceki 5 günün ortalamasını karşılaştır
        BigDecimal recent5Avg = prices.subList(0, 5).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(5), 4, RoundingMode.HALF_UP);

        BigDecimal previous5Avg = prices.subList(5, 10).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(5), 4, RoundingMode.HALF_UP);

        String trend;
        BigDecimal changePercent = recent5Avg.subtract(previous5Avg)
                .divide(previous5Avg, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (changePercent.compareTo(BigDecimal.valueOf(2)) > 0) {
            trend = "YUKSELEN";
        } else if (changePercent.compareTo(BigDecimal.valueOf(-2)) < 0) {
            trend = "DUSEN";
        } else {
            trend = "YATAY";
        }

        return Map.of(
                "symbol", symbol,
                "trend", trend,
                "change_percent", changePercent,
                "recent_avg", recent5Avg,
                "previous_avg", previous5Avg,
                "current_price", prices.get(0)
        );
    }

    /**
     * Basit destek ve direnç seviyeleri
     */
    public Map<String, Object> calculateSupportResistance(String symbol) {
        List<MarketCandle> candles = candleRepository.findTop100ByInstrument_SymbolOrderByDayDesc(symbol);
        
        if (candles.size() < 20) {
            return Map.of("error", "Insufficient data for support/resistance calculation");
        }

        List<BigDecimal> prices = candles.stream().map(MarketCandle::getClose).collect(Collectors.toList());

        // Son 20 günün en yüksek ve en düşük değerleri (close fiyatlarından)
        BigDecimal resistance = prices.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal support = prices.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        BigDecimal currentPrice = candles.get(0).getClose();
        
        // Fiyatın destek/direnç arasındaki konumu
        BigDecimal range = resistance.subtract(support);
        BigDecimal position = range.compareTo(BigDecimal.ZERO) > 0 
            ? currentPrice.subtract(support).divide(range, 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return Map.of(
                "symbol", symbol,
                "support", support,
                "resistance", resistance,
                "current_price", currentPrice,
                "position_in_range", position.multiply(BigDecimal.valueOf(100)), // Yüzde olarak
                "range", range
        );
    }

    /**
     * Basit momentum göstergesi
     */
    public Map<String, Object> calculateMomentum(String symbol, int period) {
        List<MarketCandle> candles = candleRepository.findTop100ByInstrument_SymbolOrderByDayDesc(symbol);
        
        if (candles.size() < period + 1) {
            return Map.of("error", "Insufficient data for momentum calculation");
        }

        List<BigDecimal> prices = candles.stream()
                .map(MarketCandle::getClose)
                .collect(Collectors.toList());

        List<BigDecimal> momentum = new ArrayList<>();
        
        for (int i = 0; i < prices.size() - period; i++) {
            BigDecimal current = prices.get(i);
            BigDecimal previous = prices.get(i + period);
            BigDecimal mom = current.subtract(previous);
            momentum.add(mom);
        }

        String signal = "NOTR";
        if (!momentum.isEmpty()) {
            BigDecimal currentMomentum = momentum.get(0);
            if (currentMomentum.compareTo(BigDecimal.ZERO) > 0) {
                signal = "POZITIF";
            } else if (currentMomentum.compareTo(BigDecimal.ZERO) < 0) {
                signal = "NEGATIF";
            }
        }

        return Map.of(
                "symbol", symbol,
                "period", period,
                "momentum", momentum.subList(0, Math.min(10, momentum.size())),
                "current_momentum", momentum.isEmpty() ? BigDecimal.ZERO : momentum.get(0),
                "signal", signal
        );
    }

    // Yardımcı metodlar
    private List<BigDecimal> calculateSMA(List<BigDecimal> prices, int period) {
        List<BigDecimal> sma = new ArrayList<>();
        
        for (int i = 0; i <= prices.size() - period; i++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = i; j < i + period; j++) {
                sum = sum.add(prices.get(j));
            }
            sma.add(sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP));
        }
        
        return sma;
    }

    private List<BigDecimal> calculateEMA(List<BigDecimal> prices, int period) {
        List<BigDecimal> ema = new ArrayList<>();
        
        if (prices.isEmpty()) return ema;
        
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));
        
        // İlk EMA değeri SMA ile başlar
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period && i < prices.size(); i++) {
            sum = sum.add(prices.get(i));
        }
        BigDecimal firstEma = sum.divide(BigDecimal.valueOf(Math.min(period, prices.size())), 4, RoundingMode.HALF_UP);
        ema.add(firstEma);
        
        // Sonraki EMA değerleri
        for (int i = 1; i < prices.size(); i++) {
            BigDecimal currentPrice = prices.get(i);
            BigDecimal previousEma = ema.get(i - 1);
            BigDecimal currentEma = currentPrice.multiply(multiplier)
                    .add(previousEma.multiply(BigDecimal.ONE.subtract(multiplier)));
            ema.add(currentEma);
        }
        
        Collections.reverse(ema); // En yeni değer başta olsun
        return ema;
    }
}
package com.finansportali.backend.mapper;

import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.PortfolioPosition;
import com.finansportali.backend.dto.response.portfolio.PortfolioPositionDetail;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Mapper for Portfolio entities and DTOs
 * Handles conversion between domain models and API responses
 */
@Component
public class PortfolioMapper {
    
    /**
     * Convert PortfolioPosition entity to PortfolioPositionDetail DTO
     */
    public PortfolioPositionDetail toPositionDetail(
            PortfolioPosition position,
            MarketInstrument instrument,
            BigDecimal currentPrice,
            BigDecimal dailyChangePercent) {
        
        String instrumentType = instrument.getInstrumentType() != null 
                ? instrument.getInstrumentType().name() 
                : "STOCK";
        
        String currency = determineCurrency(position.getSymbol(), instrument.getInstrumentType());
        
        BigDecimal buyPrice = position.getAvgCost() != null ? position.getAvgCost() : BigDecimal.ZERO;
        BigDecimal quantity = position.getQuantity();
        BigDecimal investedAmount = buyPrice.multiply(quantity);
        BigDecimal currentValue = currentPrice.multiply(quantity);
        BigDecimal totalChangeValue = currentValue.subtract(investedAmount);
        
        BigDecimal totalChangePercent = investedAmount.compareTo(BigDecimal.ZERO) > 0
                ? totalChangeValue.divide(investedAmount, 6, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        
        BigDecimal dailyChangeValue = currentValue.multiply(dailyChangePercent)
                .divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP);
        
        LocalDate buyDate = position.getPurchaseDate() != null 
                ? position.getPurchaseDate() 
                : LocalDate.now();
        
        return new PortfolioPositionDetail(
                position.getSymbol(),
                instrument.getName(),
                instrumentType,
                currency,
                quantity,
                buyDate,
                buyPrice,
                currentPrice,
                investedAmount,
                currentValue,
                totalChangeValue,
                totalChangePercent,
                dailyChangePercent,
                dailyChangeValue
        );
    }
    
    /**
     * Determine the currency of an instrument based on its symbol and type
     */
    private String determineCurrency(String symbol, InstrumentType type) {
        // BIST stocks: symbols ending with .IS or type is BIST
        if (symbol.endsWith(".IS") || (type != null && type.name().equals("BIST"))) {
            return "TRY";
        }
        
        // FX pairs containing TRY
        if (symbol.contains("TRY") && !symbol.equals("USDTRY")) {
            return "TRY";
        }
        
        // If type is STOCK (not BIST), it's international = USD
        if (type != null && type.name().equals("STOCK")) {
            return "USD";
        }
        
        // Crypto = USD
        if (type != null && type.name().equals("CRYPTO")) {
            return "USD";
        }
        
        // Turkish symbol pattern (3-5 uppercase letters, no dots)
        if (symbol.matches("^[A-Z]{3,5}$") && !symbol.contains("USD") 
                && !symbol.contains("BTC") && !symbol.contains("ETH")) {
            return "TRY";
        }
        
        // Default to USD
        return "USD";
    }
}

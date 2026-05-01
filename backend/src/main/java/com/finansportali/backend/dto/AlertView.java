package com.finansportali.backend.dto;

import com.finansportali.backend.domain.AlertType;
import java.math.BigDecimal;
import java.time.Instant;

public record AlertView(
        Long id,
        String symbol,
        String instrumentName,
        AlertType alertType,
        BigDecimal targetPrice,
        BigDecimal creationPrice,
        BigDecimal currentPrice,
        Boolean active,
        Instant createdAt,
        Instant triggeredAt,
        BigDecimal triggeredPrice,
        String note,
        String status,
        Double progressPercent
) {
    public static AlertView fromAlert(com.finansportali.backend.domain.PriceAlert alert, BigDecimal currentPrice) {
        String status = alert.getActive() ? "Aktif" : "Tetiklendi";
        Double progressPercent = calculateProgress(alert, currentPrice);
        
        return new AlertView(
                alert.getId(),
                alert.getSymbol(),
                alert.getInstrument().getName(),
                alert.getAlertType(),
                alert.getTargetPrice(),
                alert.getCreationPrice(),
                currentPrice,
                alert.getActive(),
                alert.getCreatedAt(),
                alert.getTriggeredAt(),
                alert.getTriggeredPrice(),
                alert.getNote(),
                status,
                progressPercent
        );
    }

    private static Double calculateProgress(com.finansportali.backend.domain.PriceAlert alert, BigDecimal currentPrice) {
        if (alert.getCreationPrice() == null || currentPrice == null) {
            return 0.0;
        }

        BigDecimal creation = alert.getCreationPrice();
        BigDecimal target = alert.getTargetPrice();
        BigDecimal current = currentPrice;

        return switch (alert.getAlertType()) {
            case PRICE_ABOVE -> {
                if (target.compareTo(creation) <= 0) yield 100.0;
                BigDecimal totalDistance = target.subtract(creation);
                BigDecimal currentDistance = current.subtract(creation);
                yield Math.min(100.0, Math.max(0.0, 
                    currentDistance.divide(totalDistance, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue()));
            }
            case PRICE_BELOW -> {
                if (target.compareTo(creation) >= 0) yield 100.0;
                BigDecimal totalDistance = creation.subtract(target);
                BigDecimal currentDistance = creation.subtract(current);
                yield Math.min(100.0, Math.max(0.0, 
                    currentDistance.divide(totalDistance, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue()));
            }
            case PERCENT_GAIN, PERCENT_LOSS -> {
                // Yüzde bazlı alarmlar için basit hesaplama
                BigDecimal change = current.subtract(creation);
                BigDecimal changePercent = change.divide(creation, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                yield Math.min(100.0, Math.abs(changePercent.doubleValue()));
            }
        };
    }
}
package com.finansportali.backend.service.viop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Moves matured open VİOP positions to EXPIRED once a day (settling realized P/L
 * at the last known price and recording an EXPIRE leg). Simulation only.
 */
@Component
public class ViopExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ViopExpiryScheduler.class);

    private final ViopPositionService positionService;

    public ViopExpiryScheduler(ViopPositionService positionService) {
        this.positionService = positionService;
    }

    /** Daily at 00:05 (configurable). */
    @Scheduled(cron = "${app.viop.expiry-cron:0 5 0 * * ?}")
    public void run() {
        try {
            int n = positionService.expireDuePositions();
            if (n > 0) log.info("[VIOP-EXPIRY] {} position(s) expired", n);
        } catch (RuntimeException e) {
            log.error("[VIOP-EXPIRY] failed", e);
        }
    }
}

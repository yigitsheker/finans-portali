package com.finansportali.backend.service.bond;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily job that posts due coupon payments and matures/redeems bond positions
 * whose maturity date has arrived. Simulation only.
 */
@Component
public class BondCouponMaturityScheduler {

    private static final Logger log = LoggerFactory.getLogger(BondCouponMaturityScheduler.class);

    private final BondPositionService bondPositionService;

    public BondCouponMaturityScheduler(BondPositionService bondPositionService) {
        this.bondPositionService = bondPositionService;
    }

    /** Daily at 00:10 (configurable). */
    @Scheduled(cron = "${app.bonds.coupon-cron:0 10 0 * * ?}")
    public void run() {
        try {
            int n = bondPositionService.processCouponsAndMaturities();
            if (n > 0) log.info("[BOND-COUPON] {} coupon/maturity event(s) processed", n);
        } catch (RuntimeException e) {
            log.error("[BOND-COUPON] failed", e);
        }
    }
}

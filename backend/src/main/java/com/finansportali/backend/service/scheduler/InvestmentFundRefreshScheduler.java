package com.finansportali.backend.service.scheduler;

import com.finansportali.backend.service.InvestmentFundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Yatırım fonu verilerini otomatik olarak güncelleyen zamanlayıcı.
 * 
 * - Uygulama başladığında ilk güncellemeyi yapar
 * - Sonrasında yapılandırılmış cron ifadesine göre periyodik güncelleme yapar
 */
@Service
public class InvestmentFundRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(InvestmentFundRefreshScheduler.class);

    private final InvestmentFundService investmentFundService;
    
    @Value("${app.funds.scheduler-enabled:true}")
    private boolean schedulerEnabled;

    public InvestmentFundRefreshScheduler(InvestmentFundService investmentFundService) {
        this.investmentFundService = investmentFundService;
    }

    /**
     * Uygulama başladığında ilk veri yüklemesini yapar.
     * Veritabanı boşsa TEFAS'tan veri çeker.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!schedulerEnabled) {
            log.info("[InvestmentFundScheduler] Scheduler is disabled, skipping initial data load");
            return;
        }

        log.info("[InvestmentFundScheduler] Application ready, checking if initial data load is needed...");
        
        try {
            // Veritabanı boşsa veri çek
            investmentFundService.seedIfEmpty();
        } catch (Exception e) {
            log.error("[InvestmentFundScheduler] Error during initial data load: {}", e.getMessage(), e);
        }
    }

    /**
     * Periyodik olarak fon verilerini günceller.
     * Cron ifadesi: 0 30 10,14,18 * * MON-FRI (Hafta içi 10:30, 14:30, 18:30)
     */
    @Scheduled(cron = "${app.funds.refresh-cron:0 30 10,14,18 * * MON-FRI}")
    public void scheduledRefresh() {
        if (!schedulerEnabled) {
            log.debug("[InvestmentFundScheduler] Scheduler is disabled, skipping scheduled refresh");
            return;
        }

        log.info("[InvestmentFundScheduler] Starting scheduled fund data refresh...");
        
        try {
            investmentFundService.updateFundPrices();
            log.info("[InvestmentFundScheduler] Scheduled refresh completed successfully");
        } catch (Exception e) {
            log.error("[InvestmentFundScheduler] Error during scheduled refresh: {}", e.getMessage(), e);
        }
    }
}

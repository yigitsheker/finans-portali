package com.finansportali.backend.service.scheduler;

import com.finansportali.backend.service.BondDataRefreshService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tahvil ve bono verilerini periyodik olarak güncelleyen scheduler.
 * Cron expression ile yapılandırılabilir.
 */
@Component
@ConditionalOnProperty(name = "app.bonds.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class BondDataRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(BondDataRefreshScheduler.class);

    private final BondDataRefreshService refreshService;
    private final Tracer tracer;

    @Value("${app.bonds.provider:DEMO}")
    private String providerName;

    public BondDataRefreshScheduler(BondDataRefreshService refreshService, Tracer tracer) {
        this.refreshService = refreshService;
        this.tracer = tracer;
    }

    /**
     * Periyodik tahvil verisi güncelleme.
     * Varsayılan: Her 2 saatte bir (0 0 0/2 * * ?)
     * 
     * Cron expression application.yml'den yapılandırılabilir:
     * app.bonds.refresh-cron
     */
    @Scheduled(cron = "${app.bonds.refresh-cron:0 0 0/2 * * ?}")
    public void scheduledRefresh() {
        Span span = tracer.spanBuilder("bond.data.refresh.scheduled").startSpan();
        try (Scope scope = span.makeCurrent()) {
            log.info("[BOND-SCHEDULER] Starting scheduled bond data refresh (provider: {})", providerName);
            
            span.setAttribute("provider", providerName);
            span.setAttribute("trigger", "scheduled");

            int updatedCount = refreshService.refreshBondData();
            
            span.setAttribute("instruments.updated", updatedCount);
            span.addEvent("Bond data refresh completed");

            log.info("[BOND-SCHEDULER] Scheduled refresh completed. Updated {} instruments", updatedCount);

        } catch (Exception e) {
            span.recordException(e);
            log.error("[BOND-SCHEDULER] Scheduled refresh failed", e);
        } finally {
            span.end();
        }
    }

    /**
     * Uygulama başlangıcında bir kez çalışır (5 saniye sonra).
     * İlk veri yüklemesi için.
     */
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void initialRefresh() {
        Span span = tracer.spanBuilder("bond.data.refresh.initial").startSpan();
        try (Scope scope = span.makeCurrent()) {
            log.info("[BOND-SCHEDULER] Starting initial bond data refresh");
            
            span.setAttribute("provider", providerName);
            span.setAttribute("trigger", "initial");

            int updatedCount = refreshService.refreshBondData();
            
            span.setAttribute("instruments.updated", updatedCount);
            span.addEvent("Initial bond data refresh completed");

            log.info("[BOND-SCHEDULER] Initial refresh completed. Loaded {} instruments", updatedCount);

        } catch (Exception e) {
            span.recordException(e);
            log.error("[BOND-SCHEDULER] Initial refresh failed", e);
        } finally {
            span.end();
        }
    }
}

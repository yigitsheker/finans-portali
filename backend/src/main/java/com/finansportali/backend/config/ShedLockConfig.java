package com.finansportali.backend.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Distributed scheduler locking. On GKE the backend runs as multiple replicas
 * (HPA 1–3), so without a lock every replica would fire each {@code @Scheduled}
 * job — double-paying coupons, double-expiring VİOP positions and hammering the
 * upstream price/news APIs. {@code @SchedulerLock} on a job acquires a row in
 * the {@code shedlock} table (V27) so only one replica runs it per window.
 *
 * <p>{@code usingDbTime()} makes the lock compare against the database clock, so
 * replica clock drift can't release a lock early.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}

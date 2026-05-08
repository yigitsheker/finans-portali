package com.finansportali.backend.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utility class for managing correlation IDs (request IDs) across the application.
 * Correlation IDs help trace requests through the system and correlate logs.
 */
public class CorrelationIdUtil {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    /**
     * Generate a new correlation ID (UUID).
     */
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Set correlation ID in MDC (Mapped Diagnostic Context).
     * This makes it available to all log statements in the current thread.
     */
    public static void setCorrelationId(String correlationId) {
        MDC.put(REQUEST_ID_MDC_KEY, correlationId);
    }

    /**
     * Get correlation ID from MDC.
     */
    public static String getCorrelationId() {
        return MDC.get(REQUEST_ID_MDC_KEY);
    }

    /**
     * Clear correlation ID from MDC.
     * Should be called at the end of request processing.
     */
    public static void clearCorrelationId() {
        MDC.remove(REQUEST_ID_MDC_KEY);
    }

    /**
     * Clear all MDC context.
     */
    public static void clearMDC() {
        MDC.clear();
    }
}

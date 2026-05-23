package com.finansportali.backend.filter;

import com.finansportali.backend.util.CorrelationIdUtil;
import com.finansportali.backend.util.LogSanitizer;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Filter for logging HTTP requests and responses with structured logging.
 * Adds correlation ID, user information, and request/response details to MDC.
 */
@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    // Endpoints to skip logging (health checks, static resources)
    private static final List<String> SKIP_PATHS = Arrays.asList(
            "/actuator/health",
            "/actuator/prometheus",
            "/swagger-ui",
            "/v3/api-docs"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip logging for certain paths
        String requestUri = request.getRequestURI();
        if (shouldSkipLogging(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap request and response for content caching
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            // Set up correlation ID
            String correlationId = request.getHeader(CorrelationIdUtil.REQUEST_ID_HEADER);
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = CorrelationIdUtil.generateCorrelationId();
            }
            CorrelationIdUtil.setCorrelationId(correlationId);

            // Add correlation ID to response header
            response.setHeader(CorrelationIdUtil.REQUEST_ID_HEADER, correlationId);

            // Set up MDC context
            setupMDCContext(wrappedRequest);

            // Log request
            logRequest(wrappedRequest);

            // Process request
            filterChain.doFilter(wrappedRequest, wrappedResponse);

            // Calculate duration
            long duration = System.currentTimeMillis() - startTime;

            // Log response
            logResponse(wrappedRequest, wrappedResponse, duration);

        } finally {
            // Copy response body back to original response
            wrappedResponse.copyBodyToResponse();

            // Clear MDC context
            CorrelationIdUtil.clearMDC();
        }
    }

    private boolean shouldSkipLogging(String requestUri) {
        return SKIP_PATHS.stream().anyMatch(requestUri::startsWith);
    }

    private void setupMDCContext(HttpServletRequest request) {
        // Add request details to MDC
        MDC.put("endpoint", request.getRequestURI());
        MDC.put("httpMethod", request.getMethod());
        MDC.put("clientIp", getClientIp(request));

        // Add user information if authenticated
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() 
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                
                // Extract user ID (sub claim)
                String userId = jwt.getSubject();
                if (userId != null) {
                    MDC.put("userId", userId);
                }
                
                // Extract username (preferred_username claim)
                String username = jwt.getClaimAsString("preferred_username");
                if (username != null) {
                    MDC.put("username", username);
                }
            }
        }
    }

    private void logRequest(HttpServletRequest request) {
        // Client IP is read from attacker-controlled X-Forwarded-For;
        // sanitize to defuse log-injection via CRLF (S5145).
        log.info("HTTP Request: {} {} from {}",
                request.getMethod(),
                request.getRequestURI(),
                LogSanitizer.sanitize(getClientIp(request)));
    }

    private void logResponse(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        int status = response.getStatus();
        MDC.put("statusCode", String.valueOf(status));
        MDC.put("durationMs", String.valueOf(durationMs));

        String logLevel = status >= 500 ? "ERROR" : status >= 400 ? "WARN" : "INFO";

        String message = String.format("HTTP Response: %s %s - Status: %d - Duration: %dms",
                request.getMethod(),
                request.getRequestURI(),
                status,
                durationMs);

        switch (logLevel) {
            case "ERROR" -> log.error(message);
            case "WARN" -> log.warn(message);
            default -> log.info(message);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}

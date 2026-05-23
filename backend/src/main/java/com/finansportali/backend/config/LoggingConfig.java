package com.finansportali.backend.config;

import com.finansportali.backend.util.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;

@Configuration
public class LoggingConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestLoggingInterceptor());
    }

    public static class RequestLoggingInterceptor implements HandlerInterceptor {
        private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            // Generate request ID for tracing
            String requestId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("requestId", requestId);
            
            long startTime = System.currentTimeMillis();
            request.setAttribute("startTime", startTime);
            
            // Log request details. User-Agent + client IP are attacker-
            // controlled headers — sanitize through LogSanitizer (S5145).
            log.info("REQUEST: {} {} | IP: {} | User-Agent: {} | RequestId: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    LogSanitizer.sanitize(getClientIpAddress(request)),
                    LogSanitizer.sanitize(request.getHeader("User-Agent")),
                    requestId);
            
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                  Object handler, Exception ex) {
            try {
                Long startTime = (Long) request.getAttribute("startTime");
                long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
                
                String requestId = MDC.get("requestId");
                
                if (ex != null) {
                    log.error("RESPONSE: {} {} | Status: {} | Duration: {}ms | RequestId: {} | Error: {}",
                            request.getMethod(),
                            request.getRequestURI(),
                            response.getStatus(),
                            duration,
                            requestId,
                            LogSanitizer.sanitize(ex.getMessage()));
                } else {
                    log.info("RESPONSE: {} {} | Status: {} | Duration: {}ms | RequestId: {}", 
                            request.getMethod(),
                            request.getRequestURI(),
                            response.getStatus(),
                            duration,
                            requestId);
                }
            } finally {
                // Clean up MDC
                MDC.clear();
            }
        }

        private String getClientIpAddress(HttpServletRequest request) {
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
}
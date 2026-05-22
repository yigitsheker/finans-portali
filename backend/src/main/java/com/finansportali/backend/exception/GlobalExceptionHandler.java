package com.finansportali.backend.exception;

import com.finansportali.backend.dto.ApiError;
import com.finansportali.backend.util.CorrelationIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("Bad request: {} - Endpoint: {} - RequestId: {}",
                ex.getMessage(),
                req.getRequestURI(),
                CorrelationIdUtil.getCorrelationId());

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                ex.getMessage(),
                req.getRequestURI(),
                null
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.put(fe.getField(), fe.getDefaultMessage()));

        log.warn("Validation error: {} fields failed - Endpoint: {} - RequestId: {}",
                fields.size(),
                req.getRequestURI(),
                CorrelationIdUtil.getCorrelationId());

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Request validation failed",
                req.getRequestURI(),
                fields
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Map common request-binding failures (required query param missing,
     * type mismatch like a malformed date) to 400 instead of letting them
     * fall through to the generic 500 catch-all. These are always client
     * errors, never server bugs.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBinding(Exception ex, HttpServletRequest req) {
        log.warn("Binding error: {} - Endpoint: {} - RequestId: {}",
                ex.getMessage(), req.getRequestURI(), CorrelationIdUtil.getCorrelationId());

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                ex.getMessage(),
                req.getRequestURI(),
                null);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied: {} - Endpoint: {} - RequestId: {}",
                ex.getMessage(),
                req.getRequestURI(),
                CorrelationIdUtil.getCorrelationId());

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                "ACCESS_DENIED",
                "You don't have permission to access this resource",
                req.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error occurred - Endpoint: {} - RequestId: {} - Error: {}",
                req.getRequestURI(),
                CorrelationIdUtil.getCorrelationId(),
                ex.getMessage(),
                ex);

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later.",
                req.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}


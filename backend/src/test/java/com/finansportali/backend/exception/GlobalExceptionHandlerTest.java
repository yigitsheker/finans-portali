package com.finansportali.backend.exception;

import com.finansportali.backend.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Direct-unit tests for {@link GlobalExceptionHandler} — exercise each
 * @ExceptionHandler without spinning up an MVC test slice.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest req;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/test");
    }

    @Test
    void illegal_argument_maps_to_400_with_message() {
        ResponseEntity<ApiError> r = handler.handleIllegalArgument(
                new IllegalArgumentException("symbol is required"), req);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().message()).isEqualTo("symbol is required");
        assertThat(r.getBody().path()).isEqualTo("/api/v1/test");
        assertThat(r.getBody().error()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void validation_error_collects_field_errors() throws NoSuchMethodException {
        // Building a real MethodArgumentNotValidException needs a binding
        // result attached to a method parameter. Use a fake target method
        // — the handler only reads the bindingResult, not the method.
        Method m = String.class.getMethod("toString");
        MethodParameter param = new MethodParameter(m, -1);
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "obj");
        br.addError(new FieldError("obj", "symbol", "must not be blank"));
        br.addError(new FieldError("obj", "quantity", "must be positive"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, br);

        ResponseEntity<ApiError> r = handler.handleValidation(ex, req);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().error()).isEqualTo("VALIDATION_ERROR");
        assertThat(r.getBody().fieldErrors())
                .containsEntry("symbol", "must not be blank")
                .containsEntry("quantity", "must be positive");
    }

    @Test
    void access_denied_maps_to_403() {
        ResponseEntity<ApiError> r = handler.handleAccessDenied(
                new AccessDeniedException("nope"), req);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().error()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void missing_query_param_maps_to_400() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("from", "LocalDate");

        ResponseEntity<ApiError> r = handler.handleBinding(ex, req);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().error()).isEqualTo("BAD_REQUEST");
        assertThat(r.getBody().message()).contains("from");
    }

    @Test
    void type_mismatch_maps_to_400() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getMessage()).thenReturn("Failed to convert value of type 'String' to required type 'LocalDate'");

        ResponseEntity<ApiError> r = handler.handleBinding(ex, req);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unexpected_exception_maps_to_500_with_generic_message() {
        ResponseEntity<ApiError> r = handler.handleGenericException(
                new RuntimeException("boom"), req);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody()).isNotNull();
        // The body must NOT leak the raw message to the caller — only a
        // generic prompt. This is intentional so internal stack details
        // never reach the browser.
        assertThat(r.getBody().message()).doesNotContain("boom");
        assertThat(r.getBody().error()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}

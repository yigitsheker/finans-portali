package com.finansportali.backend.controller;

import com.finansportali.backend.dto.response.alert.AlertView;
import com.finansportali.backend.entity.AlertType;
import com.finansportali.backend.service.PriceAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PriceAlertController.class)
@Import({TestSecurityConfig.class, com.finansportali.backend.exception.GlobalExceptionHandler.class})
@WithMockUser
class PriceAlertControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private PriceAlertService alertService;

    private AlertView sampleAlert() {
        return new AlertView(
                1L,
                "THYAO",
                "Türk Hava Yolları",
                AlertType.PRICE_ABOVE,
                new BigDecimal("310.00"),    // targetPrice
                new BigDecimal("294.50"),    // creationPrice
                new BigDecimal("298.00"),    // currentPrice
                true,                          // active
                java.time.Instant.parse("2026-05-01T10:00:00Z"),  // createdAt
                null,                          // triggeredAt
                null,                          // triggeredPrice
                "",                            // note
                "Aktif",                      // status
                25.0,                          // progressPercent
                "TRY");                       // currency
    }

    // Send a body that PARSES into a valid CreateAlertRequest record
    // (the compact constructor enforces non-null symbol/alertType and a
    // positive targetPrice); the controller is what we're testing.
    private static final String VALID_BODY =
            "{\"symbol\":\"THYAO\",\"alertType\":\"PRICE_ABOVE\",\"targetPrice\":310,\"note\":\"\"}";

    @Test
    void create_alert_returns_200_with_view() throws Exception {
        when(alertService.createAlert(any(), any(Authentication.class))).thenReturn(sampleAlert());

        mvc.perform(post("/api/v1/alerts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("THYAO"));
    }

    @Test
    void create_alert_returns_400_when_service_throws_iae() throws Exception {
        when(alertService.createAlert(any(), any(Authentication.class)))
                .thenThrow(new IllegalArgumentException("targetPrice must be positive"));

        mvc.perform(post("/api/v1/alerts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("targetPrice must be positive"));
    }

    @Test
    void create_alert_returns_500_on_unexpected_error() throws Exception {
        when(alertService.createAlert(any(), any(Authentication.class)))
                .thenThrow(new RuntimeException("boom"));

        mvc.perform(post("/api/v1/alerts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void get_user_alerts_returns_list() throws Exception {
        when(alertService.getUserAlerts(any(Authentication.class))).thenReturn(List.of(sampleAlert()));

        mvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void get_user_alerts_returns_500_on_service_error() throws Exception {
        when(alertService.getUserAlerts(any(Authentication.class))).thenThrow(new RuntimeException("boom"));

        mvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void delete_alert_returns_200_when_owner() throws Exception {
        mvc.perform(delete("/api/v1/alerts/5").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(alertService).deleteAlert(org.mockito.ArgumentMatchers.eq(5L), any(Authentication.class));
    }

    @Test
    void delete_alert_returns_403_on_security() throws Exception {
        doThrow(new SecurityException("not your alert"))
                .when(alertService).deleteAlert(anyLong(), any(Authentication.class));

        mvc.perform(delete("/api/v1/alerts/5").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_alert_returns_404_on_missing() throws Exception {
        doThrow(new IllegalArgumentException("missing"))
                .when(alertService).deleteAlert(anyLong(), any(Authentication.class));

        mvc.perform(delete("/api/v1/alerts/999").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void test_alert_returns_success_envelope() throws Exception {
        when(alertService.testAlert(any(), any(Authentication.class))).thenReturn(sampleAlert());

        mvc.perform(post("/api/v1/alerts/3/test").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.alert.symbol").value("THYAO"));
    }

    @Test
    void test_alert_returns_403_on_security() throws Exception {
        when(alertService.testAlert(any(), any(Authentication.class)))
                .thenThrow(new SecurityException("not your alert"));

        mvc.perform(post("/api/v1/alerts/3/test").with(csrf()))
                .andExpect(status().isForbidden());
    }
}

package com.finansportali.backend.controller;

import com.finansportali.backend.common.ApiResponse;
import com.finansportali.backend.dto.request.HistoricalPositionRequest;
import com.finansportali.backend.dto.response.HistoricalPositionResponse;
import com.finansportali.backend.service.HistoricalPositionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalPositionControllerTest {

    @Mock private HistoricalPositionService service;
    @InjectMocks private HistoricalPositionController controller;

    private Jwt jwt(String sub) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(sub);
        return jwt;
    }

    private static HistoricalPositionRequest req() {
        return new HistoricalPositionRequest("THYAO", "THY", LocalDate.of(2024, 1, 1),
                new BigDecimal("100"), new BigDecimal("5"), "₺");
    }

    private static HistoricalPositionResponse resp() {
        return new HistoricalPositionResponse(1L, "THYAO", "THY", LocalDate.of(2024, 1, 1),
                new BigDecimal("100"), new BigDecimal("5"), "₺");
    }

    @Test
    void getUserPositions_returns_ok_with_data() {
        when(service.getUserPositions("u")).thenReturn(List.of(resp()));

        ResponseEntity<ApiResponse<List<HistoricalPositionResponse>>> out =
                controller.getUserPositions(jwt("u"));

        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(out.getBody()).isNotNull();
        assertThat(out.getBody().getData()).hasSize(1);
    }

    @Test
    void addPosition_returns_created() {
        when(service.addPosition("u", req())).thenReturn(resp());

        ResponseEntity<ApiResponse<HistoricalPositionResponse>> out =
                controller.addPosition(jwt("u"), req());

        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(out.getBody().getData().symbol()).isEqualTo("THYAO");
    }

    @Test
    void updatePosition_returns_ok() {
        when(service.updatePosition("u", 1L, req())).thenReturn(resp());

        ResponseEntity<ApiResponse<HistoricalPositionResponse>> out =
                controller.updatePosition(jwt("u"), 1L, req());

        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(out.getBody().getData()).isNotNull();
    }

    @Test
    void deletePosition_delegates_and_returns_ok() {
        ResponseEntity<ApiResponse<Void>> out = controller.deletePosition(jwt("u"), 1L);

        verify(service).deletePosition("u", 1L);
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteAllPositions_delegates_and_returns_ok() {
        ResponseEntity<ApiResponse<Void>> out = controller.deleteAllPositions(jwt("u"));

        verify(service).deleteAllUserPositions("u");
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

package com.finansportali.backend.service;

import com.finansportali.backend.dto.request.HistoricalPositionRequest;
import com.finansportali.backend.dto.response.HistoricalPositionResponse;
import com.finansportali.backend.entity.HistoricalPosition;
import com.finansportali.backend.repository.HistoricalPositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalPositionServiceTest {

    @Mock private HistoricalPositionRepository repository;
    @InjectMocks private HistoricalPositionService service;

    private static HistoricalPositionRequest req(String currency) {
        return new HistoricalPositionRequest("THYAO", "Türk Hava Yolları",
                LocalDate.of(2024, 1, 15), new BigDecimal("250.50"), new BigDecimal("10"), currency);
    }

    private static HistoricalPosition pos(Long id, String userId, String isoCurrency) {
        HistoricalPosition p = new HistoricalPosition(userId, "THYAO", "Türk Hava Yolları",
                LocalDate.of(2024, 1, 15), new BigDecimal("250.50"), new BigDecimal("10"), isoCurrency);
        p.setId(id);
        return p;
    }

    // ---------- getUserPositions ----------

    @Test
    void getUserPositions_maps_rows_and_converts_currency_to_symbol() {
        when(repository.findByUserIdOrderByBuyDateDesc("u"))
                .thenReturn(List.of(pos(1L, "u", "TRY"), pos(2L, "u", "USD")));

        List<HistoricalPositionResponse> out = service.getUserPositions("u");

        assertThat(out).hasSize(2);
        assertThat(out.get(0).id()).isEqualTo(1L);
        assertThat(out.get(0).symbol()).isEqualTo("THYAO");
        assertThat(out.get(0).currency()).isEqualTo("₺");   // TRY → ₺
        assertThat(out.get(1).currency()).isEqualTo("$");   // USD → $
    }

    @Test
    void getUserPositions_empty_returns_empty_list() {
        when(repository.findByUserIdOrderByBuyDateDesc("u")).thenReturn(List.of());
        assertThat(service.getUserPositions("u")).isEmpty();
    }

    // ---------- addPosition ----------

    @Test
    void addPosition_persists_with_iso_currency_and_returns_symbol_currency() {
        when(repository.save(any(HistoricalPosition.class)))
                .thenAnswer(inv -> {
                    HistoricalPosition p = inv.getArgument(0);
                    p.setId(7L);
                    return p;
                });

        HistoricalPositionResponse out = service.addPosition("u", req("₺"));

        ArgumentCaptor<HistoricalPosition> cap = ArgumentCaptor.forClass(HistoricalPosition.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo("u");
        assertThat(cap.getValue().getCurrency()).isEqualTo("TRY");   // ₺ → TRY on write
        assertThat(out.id()).isEqualTo(7L);
        assertThat(out.currency()).isEqualTo("₺");                   // TRY → ₺ on read
    }

    @Test
    void addPosition_maps_dollar_symbol_to_usd() {
        when(repository.save(any(HistoricalPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        service.addPosition("u", req("$"));
        ArgumentCaptor<HistoricalPosition> cap = ArgumentCaptor.forClass(HistoricalPosition.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getCurrency()).isEqualTo("USD");
    }

    // ---------- updatePosition ----------

    @Test
    void updatePosition_updates_when_found_and_authorized() {
        HistoricalPosition existing = pos(3L, "u", "TRY");
        when(repository.findById(3L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        HistoricalPositionResponse out = service.updatePosition("u", 3L, req("$"));

        assertThat(existing.getCurrency()).isEqualTo("USD");   // updated + normalized
        assertThat(out.currency()).isEqualTo("$");
        verify(repository).save(existing);
    }

    @Test
    void updatePosition_throws_404_when_not_found() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updatePosition("u", 9L, req("₺")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
        verify(repository, never()).save(any());
    }

    @Test
    void updatePosition_throws_403_when_owned_by_another_user() {
        when(repository.findById(3L)).thenReturn(Optional.of(pos(3L, "other", "TRY")));
        assertThatThrownBy(() -> service.updatePosition("u", 3L, req("₺")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("authorized");
        verify(repository, never()).save(any());
    }

    // ---------- deletePosition ----------

    @Test
    void deletePosition_deletes_when_owned() {
        when(repository.existsByIdAndUserId(3L, "u")).thenReturn(true);
        service.deletePosition("u", 3L);
        verify(repository).deleteByIdAndUserId(3L, "u");
    }

    @Test
    void deletePosition_throws_when_not_owned_or_missing() {
        when(repository.existsByIdAndUserId(3L, "u")).thenReturn(false);
        assertThatThrownBy(() -> service.deletePosition("u", 3L))
                .isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).deleteByIdAndUserId(any(), any());
    }

    // ---------- deleteAllUserPositions ----------

    @Test
    void deleteAllUserPositions_deletes_all_rows() {
        List<HistoricalPosition> rows = List.of(pos(1L, "u", "TRY"), pos(2L, "u", "USD"));
        when(repository.findByUserIdOrderByBuyDateDesc("u")).thenReturn(rows);
        service.deleteAllUserPositions("u");
        verify(repository).deleteAll(rows);
    }
}

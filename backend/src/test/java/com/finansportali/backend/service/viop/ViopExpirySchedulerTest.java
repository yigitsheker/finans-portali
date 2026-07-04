package com.finansportali.backend.service.viop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViopExpirySchedulerTest {

    @Mock private ViopPositionService positionService;
    @InjectMocks private ViopExpiryScheduler scheduler;

    @Test
    void run_delegates_to_expireDuePositions() {
        when(positionService.expireDuePositions()).thenReturn(2);
        scheduler.run();
        verify(positionService).expireDuePositions();
    }

    @Test
    void run_swallows_runtime_exception() {
        when(positionService.expireDuePositions()).thenThrow(new RuntimeException("boom"));
        // Scheduled jobs must never propagate — a failure is logged, not thrown.
        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
    }
}

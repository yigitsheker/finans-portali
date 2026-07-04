package com.finansportali.backend.service.bond;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BondCouponMaturitySchedulerTest {

    @Mock private BondPositionService bondPositionService;
    @InjectMocks private BondCouponMaturityScheduler scheduler;

    @Test
    void run_delegates_to_processCouponsAndMaturities() {
        when(bondPositionService.processCouponsAndMaturities()).thenReturn(3);
        scheduler.run();
        verify(bondPositionService).processCouponsAndMaturities();
    }

    @Test
    void run_swallows_runtime_exception() {
        when(bondPositionService.processCouponsAndMaturities()).thenThrow(new RuntimeException("boom"));
        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
    }
}

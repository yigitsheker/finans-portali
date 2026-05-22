package com.finansportali.backend.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdUtilTest {

    @AfterEach
    void clear() {
        CorrelationIdUtil.clearMDC();
    }

    @Test
    void generated_id_is_a_valid_uuid() {
        String id = CorrelationIdUtil.generateCorrelationId();
        assertThat(id).isNotBlank();
        // throws if not a UUID
        UUID.fromString(id);
    }

    @Test
    void generated_ids_are_unique() {
        assertThat(CorrelationIdUtil.generateCorrelationId())
                .isNotEqualTo(CorrelationIdUtil.generateCorrelationId());
    }

    @Test
    void set_then_get_round_trips_through_MDC() {
        CorrelationIdUtil.setCorrelationId("req-123");
        assertThat(CorrelationIdUtil.getCorrelationId()).isEqualTo("req-123");
        // Underlying MDC also has it.
        assertThat(MDC.get(CorrelationIdUtil.REQUEST_ID_MDC_KEY)).isEqualTo("req-123");
    }

    @Test
    void clear_correlation_removes_id_but_leaves_other_keys() {
        MDC.put("other", "value");
        CorrelationIdUtil.setCorrelationId("req-7");
        CorrelationIdUtil.clearCorrelationId();
        assertThat(CorrelationIdUtil.getCorrelationId()).isNull();
        assertThat(MDC.get("other")).isEqualTo("value");
    }

    @Test
    void clear_mdc_drops_all_keys() {
        MDC.put("other", "value");
        CorrelationIdUtil.setCorrelationId("req-7");
        CorrelationIdUtil.clearMDC();
        assertThat(MDC.get("other")).isNull();
        assertThat(CorrelationIdUtil.getCorrelationId()).isNull();
    }

    @Test
    void get_when_not_set_returns_null() {
        assertThat(CorrelationIdUtil.getCorrelationId()).isNull();
    }

    @Test
    void constants_match_documented_header_and_mdc_key() {
        assertThat(CorrelationIdUtil.REQUEST_ID_HEADER).isEqualTo("X-Request-ID");
        assertThat(CorrelationIdUtil.REQUEST_ID_MDC_KEY).isEqualTo("requestId");
    }
}

package com.finansportali.backend.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void success_with_data_only_sets_flag_and_timestamp() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        ApiResponse<String> r = ApiResponse.success("payload");
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getMessage()).isNull();
        assertThat(r.getData()).isEqualTo("payload");
        assertThat(r.getTimestamp()).isBetween(before, after);
    }

    @Test
    void success_with_message_and_data_carries_both() {
        ApiResponse<Integer> r = ApiResponse.success("ok", 42);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getMessage()).isEqualTo("ok");
        assertThat(r.getData()).isEqualTo(42);
    }

    @Test
    void error_factory_flips_success_to_false() {
        ApiResponse<Object> r = ApiResponse.error("boom");
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).isEqualTo("boom");
        assertThat(r.getData()).isNull();
    }

    @Test
    void all_args_ctor_preserves_every_field() {
        LocalDateTime t = LocalDateTime.of(2026, 5, 19, 12, 0);
        ApiResponse<String> r = new ApiResponse<>(true, "msg", "data", t);
        assertThat(r.getMessage()).isEqualTo("msg");
        assertThat(r.getData()).isEqualTo("data");
        assertThat(r.getTimestamp()).isEqualTo(t);
    }
}

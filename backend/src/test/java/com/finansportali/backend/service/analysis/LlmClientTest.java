package com.finansportali.backend.service.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Constructor-level tests for {@link LlmClient}. The WebClient HTTP chain is
 * deliberately not exercised here — it's hard to mock cleanly and the only
 * behaviour worth pinning down is the enable/disable flip and the
 * disabled-path returning null from {@link LlmClient#complete}.
 */
class LlmClientTest {

    @Test
    void disabled_when_api_key_is_blank() {
        LlmClient client = new LlmClient(
                "https://api.example.com",
                "",
                "gpt-4o-mini",
                600,
                0.4,
                30);
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void disabled_when_api_key_is_null() {
        LlmClient client = new LlmClient(
                "https://api.example.com",
                null,
                "gpt-4o-mini",
                600,
                0.4,
                30);
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void disabled_when_base_url_is_blank_even_with_api_key() {
        LlmClient client = new LlmClient(
                "",
                "secret-key",
                "gpt-4o-mini",
                600,
                0.4,
                30);
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void disabled_when_base_url_is_null_even_with_api_key() {
        LlmClient client = new LlmClient(
                null,
                "secret-key",
                "gpt-4o-mini",
                600,
                0.4,
                30);
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void enabled_when_api_key_and_base_url_both_set() {
        LlmClient client = new LlmClient(
                "https://api.example.com",
                "secret-key",
                "gpt-4o-mini",
                600,
                0.4,
                30);
        assertThat(client.isEnabled()).isTrue();
    }

    @Test
    void complete_returns_null_when_disabled() {
        LlmClient client = new LlmClient("", "", "gpt-4o-mini", 600, 0.4, 30);
        assertThat(client.isEnabled()).isFalse();
        assertThat(client.complete("system", "hello")).isNull();
    }

    @Test
    void complete_does_not_throw_when_disabled_with_null_inputs() {
        LlmClient client = new LlmClient(null, null, "gpt-4o-mini", 600, 0.4, 30);
        assertThat(client.complete(null, null)).isNull();
        assertThat(client.complete("", "")).isNull();
    }
}

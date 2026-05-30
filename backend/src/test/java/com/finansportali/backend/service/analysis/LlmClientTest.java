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

    @Test
    void complete_returns_null_when_upstream_unreachable() {
        // Port 9 (discard) is closed on every modern host — the WebClient
        // call rejects fast, the catch branch swallows the throw, returns
        // null. Exercises the live-enabled error path without needing a
        // mock server. 1s timeout caps the test latency.
        LlmClient client = new LlmClient(
                "http://localhost:9",
                "fake-key",
                "gpt-4o-mini",
                100,
                0.5,
                1);
        assertThat(client.isEnabled()).isTrue();
        assertThat(client.complete("system", "user")).isNull();
    }

    @Test
    void complete_skips_blank_system_prompt() {
        // Build a client targeted at a closed port — we don't care about the
        // network outcome, only that the blank-system-prompt branch is taken
        // before the WebClient call. Method must still return null without
        // throwing.
        LlmClient client = new LlmClient(
                "http://localhost:9",
                "fake-key",
                "gpt-4o-mini",
                100,
                0.5,
                1);
        assertThat(client.complete(null, "hello")).isNull();
        assertThat(client.complete("", "hello")).isNull();
        assertThat(client.complete("  ", "hello")).isNull();
    }
}

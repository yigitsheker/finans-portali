package com.finansportali.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void sanitize_returns_null_for_null_input() {
        // Keep null pass-through so logger placeholders still render the
        // conventional "null" literal.
        assertThat(LogSanitizer.sanitize(null)).isNull();
    }

    @Test
    void sanitize_passes_clean_strings_through_unchanged() {
        // Plain ASCII + Turkish chars + URL-safe punctuation = no scrub.
        assertThat(LogSanitizer.sanitize("user@example.com")).isEqualTo("user@example.com");
        assertThat(LogSanitizer.sanitize("THYAO.IS")).isEqualTo("THYAO.IS");
        assertThat(LogSanitizer.sanitize("İstanbul")).isEqualTo("İstanbul");
        assertThat(LogSanitizer.sanitize("")).isEmpty();
    }

    @Test
    void sanitize_collapses_carriage_return_to_underscore() {
        // \r alone — the classic Mac line ending an attacker can inject.
        assertThat(LogSanitizer.sanitize("line1\rline2")).isEqualTo("line1_line2");
    }

    @Test
    void sanitize_collapses_line_feed_to_underscore() {
        // \n is the most common CRLF-injection vector for log forging.
        assertThat(LogSanitizer.sanitize("line1\nline2")).isEqualTo("line1_line2");
    }

    @Test
    void sanitize_collapses_crlf_pair_to_two_underscores() {
        // Real CRLF (Windows / HTTP header style) — each char gets its own
        // underscore so the original length stays meaningful in forensics.
        assertThat(LogSanitizer.sanitize("evil\r\nfake-log-line"))
                .isEqualTo("evil__fake-log-line");
    }

    @Test
    void sanitize_collapses_tab_to_underscore() {
        // Tabs are a common log-parser separator — strip them so a crafted
        // header can't pretend to be a structured-log field.
        assertThat(LogSanitizer.sanitize("col1\tcol2")).isEqualTo("col1_col2");
    }

    @Test
    void sanitize_handles_multiple_control_chars_in_sequence() {
        assertThat(LogSanitizer.sanitize("a\n\r\tb")).isEqualTo("a___b");
    }
}

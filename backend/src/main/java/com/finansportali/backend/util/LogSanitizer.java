package com.finansportali.backend.util;

/**
 * Strips CR/LF and other control characters from values before they hit the
 * logger. Defence against Sonar S5145 (log injection): if an attacker can
 * smuggle a value via a header (X-Forwarded-For, User-Agent, JWT email
 * claim, …) into a log line, raw newlines let them fake a second log
 * record. Replacing the offending chars with `_` keeps each log entry on
 * one line and preserves the original meaning enough for forensics.
 *
 * <p>Cost is one regex per call site — negligible for request-rate logging.
 */
public final class LogSanitizer {

    private LogSanitizer() {
        // utility class
    }

    /**
     * Returns the input with CR / LF / TAB collapsed to '_'. {@code null}
     * inputs pass through as {@code null} so logger placeholders still
     * render the conventional "null" literal.
     */
    public static String sanitize(String value) {
        if (value == null) return null;
        return value.replaceAll("[\\r\\n\\t]", "_");
    }
}

package com.finansportali.backend.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Tests for the {@link MarketQuote#fromPreviousClose} static factory and
 * the constructor overloads. These are tiny but they're the single
 * place where change% gets computed from a raw previousClose, so a bug
 * here would propagate to every quote on the home page.
 */
class MarketQuoteTest {

    private final MarketInstrument inst = new MarketInstrument();

    @Test
    void from_previous_close_computes_positive_change() {
        Instant now = Instant.parse("2026-05-19T10:00:00Z");
        MarketQuote q = MarketQuote.fromPreviousClose(
                inst,
                new BigDecimal("110.00"),
                new BigDecimal("100.00"),
                now,
                MarketDataProvider.YAHOO);

        assertThat(q.getLast()).isEqualByComparingTo("110.00");
        assertThat(q.getPreviousClose()).isEqualByComparingTo("100.00");
        assertThat(q.getChangeAbs()).isEqualByComparingTo("10.00");
        // (110-100)/100 × 100 = 10.0
        assertThat(q.getChangePct().doubleValue()).isCloseTo(10.0, offset(1e-6));
        assertThat(q.getProvider()).isEqualTo(MarketDataProvider.YAHOO);
        assertThat(q.getAsOf()).isEqualTo(now);
    }

    @Test
    void from_previous_close_computes_negative_change() {
        Instant now = Instant.parse("2026-05-19T10:00:00Z");
        MarketQuote q = MarketQuote.fromPreviousClose(
                inst, new BigDecimal("90.00"), new BigDecimal("100.00"), now, MarketDataProvider.YAHOO);

        assertThat(q.getChangeAbs()).isEqualByComparingTo("-10.00");
        assertThat(q.getChangePct().doubleValue()).isCloseTo(-10.0, offset(1e-6));
    }

    @Test
    void from_previous_close_returns_zero_change_when_previous_is_null() {
        // No previous reference → both deltas default to zero (not NaN /
        // ArithmeticException). This is the path the seed data goes
        // through before Yahoo backfills.
        Instant now = Instant.parse("2026-05-19T10:00:00Z");
        MarketQuote q = MarketQuote.fromPreviousClose(
                inst, new BigDecimal("100.00"), null, now, MarketDataProvider.YAHOO);

        assertThat(q.getChangeAbs()).isEqualByComparingTo("0");
        assertThat(q.getChangePct()).isEqualByComparingTo("0");
    }

    @Test
    void from_previous_close_returns_zero_change_when_previous_is_zero() {
        // Division-by-zero guard.
        Instant now = Instant.parse("2026-05-19T10:00:00Z");
        MarketQuote q = MarketQuote.fromPreviousClose(
                inst, new BigDecimal("50.00"), BigDecimal.ZERO, now, MarketDataProvider.YAHOO);

        assertThat(q.getChangeAbs()).isEqualByComparingTo("0");
        assertThat(q.getChangePct()).isEqualByComparingTo("0");
    }

    @Test
    void short_constructor_skips_previous_close_path() {
        // The 4-arg ctor explicitly drops previousClose and provider so
        // values come straight from the provider.
        Instant now = Instant.parse("2026-05-19T10:00:00Z");
        MarketQuote q = new MarketQuote(
                inst,
                new BigDecimal("123.45"),
                new BigDecimal("1.23"),
                new BigDecimal("1.01"),
                now);

        assertThat(q.getLast()).isEqualByComparingTo("123.45");
        assertThat(q.getPreviousClose()).isNull();
        assertThat(q.getChangeAbs()).isEqualByComparingTo("1.23");
        assertThat(q.getChangePct()).isEqualByComparingTo("1.01");
        assertThat(q.getAsOf()).isEqualTo(now);
        assertThat(q.getProvider()).isNull();
    }

    @Test
    void setters_update_fields() {
        MarketQuote q = new MarketQuote();
        q.setLast(new BigDecimal("200"));
        q.setChangeAbs(new BigDecimal("5"));
        q.setChangePct(new BigDecimal("2.5"));
        q.setProvider(MarketDataProvider.YAHOO);
        Instant t = Instant.parse("2026-05-19T10:00:00Z");
        q.setAsOf(t);

        assertThat(q.getLast()).isEqualByComparingTo("200");
        assertThat(q.getChangeAbs()).isEqualByComparingTo("5");
        assertThat(q.getChangePct()).isEqualByComparingTo("2.5");
        assertThat(q.getProvider()).isEqualTo(MarketDataProvider.YAHOO);
        assertThat(q.getAsOf()).isEqualTo(t);
    }
}

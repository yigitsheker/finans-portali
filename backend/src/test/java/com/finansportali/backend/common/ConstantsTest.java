package com.finansportali.backend.common;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Light constant + utility-class-shape tests. Catches accidental edits
 * that change the contract (e.g. someone renames API_V1_BASE → /v1 and
 * breaks every client) and verifies the no-instance guard.
 */
class ConstantsTest {

    @Test
    void api_base_path_is_v1() {
        assertThat(Constants.API_V1_BASE).isEqualTo("/api/v1");
    }

    @Test
    void currency_codes_match_iso_4217() {
        assertThat(Constants.CURRENCY_TRY).isEqualTo("TRY");
        assertThat(Constants.CURRENCY_USD).isEqualTo("USD");
        assertThat(Constants.CURRENCY_EUR).isEqualTo("EUR");
    }

    @Test
    void date_format_strings_are_iso() {
        assertThat(Constants.DATE_FORMAT).isEqualTo("yyyy-MM-dd");
        assertThat(Constants.DATETIME_FORMAT).isEqualTo("yyyy-MM-dd'T'HH:mm:ss");
    }

    @Test
    void paging_defaults_are_sane() {
        assertThat(Constants.DEFAULT_PAGE_SIZE).isPositive();
        assertThat(Constants.MAX_PAGE_SIZE).isGreaterThan(Constants.DEFAULT_PAGE_SIZE);
    }

    @Test
    void range_codes_cover_common_periods() {
        assertThat(Constants.RANGE_1D).isEqualTo("1D");
        assertThat(Constants.RANGE_5D).isEqualTo("5D");
        assertThat(Constants.RANGE_1M).isEqualTo("1M");
        assertThat(Constants.RANGE_3M).isEqualTo("3M");
        assertThat(Constants.RANGE_1Y).isEqualTo("1Y");
        assertThat(Constants.RANGE_ALL).isEqualTo("ALL");
    }

    @Test
    void utility_class_cannot_be_instantiated() throws NoSuchMethodException {
        Constructor<Constants> ctor = Constants.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThatThrownBy(ctor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}

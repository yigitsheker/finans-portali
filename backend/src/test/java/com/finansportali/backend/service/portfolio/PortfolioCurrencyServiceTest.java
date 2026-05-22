package com.finansportali.backend.service.portfolio;

import com.finansportali.backend.entity.ExchangeRate;
import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.MarketQuote;
import com.finansportali.backend.repository.ExchangeRateRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioCurrencyServiceTest {

    @Mock private MarketInstrumentRepository instrumentRepo;
    @Mock private MarketQuoteRepository quoteRepo;
    @Mock private ExchangeRateRepository exchangeRateRepo;
    @InjectMocks private PortfolioCurrencyService service;

    @BeforeEach
    void seedFallbackRate() {
        // Match the @Value default the prod app uses. Tests that want to assert
        // the static-fallback path do so against this exact value.
        ReflectionTestUtils.setField(service, "usdtryFallback", new BigDecimal("35.0"));
    }

    @Test
    void dot_is_suffix_resolves_to_try() {
        assertThat(service.getInstrumentCurrency("THYAO.IS", null)).isEqualTo("TRY");
    }

    @Test
    void bist_type_resolves_to_try() {
        assertThat(service.getInstrumentCurrency("THYAO", InstrumentType.BIST)).isEqualTo("TRY");
    }

    @Test
    void try_pair_other_than_usdtry_resolves_to_try() {
        assertThat(service.getInstrumentCurrency("EURTRY", InstrumentType.FX)).isEqualTo("TRY");
    }

    @Test
    void usdtry_itself_resolves_to_usd() {
        // Special-case carved out so the rate-fetching path stays in USD context.
        assertThat(service.getInstrumentCurrency("USDTRY", InstrumentType.FX)).isEqualTo("USD");
    }

    @Test
    void us_stock_resolves_to_usd() {
        assertThat(service.getInstrumentCurrency("AAPL", InstrumentType.STOCK)).isEqualTo("USD");
    }

    @Test
    void crypto_resolves_to_usd() {
        assertThat(service.getInstrumentCurrency("BTCUSD", InstrumentType.CRYPTO)).isEqualTo("USD");
    }

    @Test
    void short_uppercase_symbol_without_type_assumed_turkish() {
        // No type, 3-5 uppercase letters, no USD/BTC/ETH → assume BIST.
        assertThat(service.getInstrumentCurrency("GARAN", null)).isEqualTo("TRY");
    }

    @Test
    void unknown_pattern_defaults_to_usd() {
        assertThat(service.getInstrumentCurrency("BTC-USD", null)).isEqualTo("USD");
        // A longer symbol that's neither a TRY-pair nor a typed-stock.
        assertThat(service.getInstrumentCurrency("VERY_LONG_NAME", null)).isEqualTo("USD");
    }

    @Test
    void usd_try_rate_returns_quote_last_when_present() {
        MarketInstrument usdTry = new MarketInstrument();
        usdTry.setSymbol("USDTRY");
        usdTry.setInstrumentType(InstrumentType.FX);

        when(instrumentRepo.findBySymbol("USDTRY")).thenReturn(Optional.of(usdTry));

        MarketQuote q = new MarketQuote(usdTry, new BigDecimal("45.59"),
                null, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), null);
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(usdTry)).thenReturn(Optional.of(q));

        assertThat(service.getUsdTryRate()).isEqualByComparingTo("45.59");
    }

    @Test
    void usd_try_rate_falls_back_to_tcmb_when_market_quote_missing() {
        // Tier 1 misses → Tier 2 (TCMB) hits with buying=34, selling=35 → mid=34.5
        MarketInstrument usdTry = new MarketInstrument();
        usdTry.setSymbol("USDTRY");
        when(instrumentRepo.findBySymbol("USDTRY")).thenReturn(Optional.of(usdTry));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(usdTry)).thenReturn(Optional.empty());

        ExchangeRate tcmb = new ExchangeRate("USD", "US Dollar",
                new BigDecimal("34.00"), new BigDecimal("35.00"),
                new BigDecimal("34.00"), new BigDecimal("35.00"),
                LocalDate.now(), "TCMB");
        when(exchangeRateRepo.findByCurrencyCodeOrderByRateDateDesc("USD"))
                .thenReturn(List.of(tcmb));

        assertThat(service.getUsdTryRate()).isEqualByComparingTo("34.5");
    }

    @Test
    void usd_try_rate_falls_back_to_tcmb_when_instrument_missing() {
        when(instrumentRepo.findBySymbol(anyString())).thenReturn(Optional.empty());
        ExchangeRate tcmb = new ExchangeRate("USD", "US Dollar",
                new BigDecimal("36"), new BigDecimal("36"),
                new BigDecimal("36"), new BigDecimal("36"),
                LocalDate.now(), "TCMB");
        when(exchangeRateRepo.findByCurrencyCodeOrderByRateDateDesc("USD"))
                .thenReturn(List.of(tcmb));

        assertThat(service.getUsdTryRate()).isEqualByComparingTo("36");
    }

    @Test
    void usd_try_rate_falls_back_to_static_default_when_all_tiers_fail() {
        // Tier 1 misses (no instrument), Tier 2 misses (no TCMB row) → static
        when(instrumentRepo.findBySymbol(anyString())).thenReturn(Optional.empty());
        when(exchangeRateRepo.findByCurrencyCodeOrderByRateDateDesc("USD"))
                .thenReturn(List.of());

        // BeforeEach seeded 35.0 — that's the configured fallback we expect
        assertThat(service.getUsdTryRate()).isEqualByComparingTo("35.0");
    }

    @Test
    void usd_try_rate_falls_back_to_tcmb_when_market_quote_is_zero() {
        // Defensive: a Yahoo glitch returning last=0 must not poison USD valuations.
        MarketInstrument usdTry = new MarketInstrument();
        usdTry.setSymbol("USDTRY");
        when(instrumentRepo.findBySymbol("USDTRY")).thenReturn(Optional.of(usdTry));
        MarketQuote zeroQuote = new MarketQuote(usdTry, BigDecimal.ZERO,
                null, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), null);
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(usdTry)).thenReturn(Optional.of(zeroQuote));

        ExchangeRate tcmb = new ExchangeRate("USD", "US Dollar",
                new BigDecimal("38"), new BigDecimal("38"),
                new BigDecimal("38"), new BigDecimal("38"),
                LocalDate.now(), "TCMB");
        when(exchangeRateRepo.findByCurrencyCodeOrderByRateDateDesc("USD"))
                .thenReturn(List.of(tcmb));

        assertThat(service.getUsdTryRate()).isEqualByComparingTo("38");
    }
}

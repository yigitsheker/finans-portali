package com.finansportali.backend.service.portfolio;

import com.finansportali.backend.entity.InstrumentType;
import com.finansportali.backend.entity.MarketInstrument;
import com.finansportali.backend.entity.MarketQuote;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioCurrencyServiceTest {

    @Mock private MarketInstrumentRepository instrumentRepo;
    @Mock private MarketQuoteRepository quoteRepo;
    @InjectMocks private PortfolioCurrencyService service;

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
    void usd_try_rate_falls_back_to_one_when_quote_missing() {
        MarketInstrument usdTry = new MarketInstrument();
        usdTry.setSymbol("USDTRY");
        when(instrumentRepo.findBySymbol("USDTRY")).thenReturn(Optional.of(usdTry));
        when(quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(usdTry)).thenReturn(Optional.empty());

        assertThat(service.getUsdTryRate()).isEqualByComparingTo("1");
    }

    @Test
    void usd_try_rate_falls_back_to_one_when_instrument_missing() {
        when(instrumentRepo.findBySymbol("USDTRY")).thenReturn(Optional.empty());
        when(instrumentRepo.findBySymbol("USD/TRY")).thenReturn(Optional.empty());
        assertThat(service.getUsdTryRate()).isEqualByComparingTo("1");
    }
}

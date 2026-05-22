package com.finansportali.backend.service.market;

import com.finansportali.backend.entity.InstrumentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.finansportali.backend.repository.MarketInstrumentRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MarketInstrumentService#normalizeSymbolForYahoo}.
 *
 * This is the function that caused the DOGUSD = -99.99% bug — the
 * old logic naively split internal symbols on the 3rd character,
 * producing dead Yahoo tickers (DOG-USD, MAT-USD…) or — worse — names
 * that collided with unrelated low-volume tokens (UNI-USD is NOT
 * Uniswap; Uniswap is UNI7083-USD). The test matrix below pins every
 * special case so a regression breaks loudly.
 */
@ExtendWith(MockitoExtension.class)
class MarketInstrumentServiceTest {

    @Mock private MarketInstrumentRepository instrumentRepo;
    @InjectMocks private MarketInstrumentService service;

    @Test
    void fx_pairs_get_equals_x_suffix() {
        assertThat(service.normalizeSymbolForYahoo("USDTRY", InstrumentType.FX)).isEqualTo("USDTRY=X");
        assertThat(service.normalizeSymbolForYahoo("EURTRY", InstrumentType.FX)).isEqualTo("EURTRY=X");
        // Idempotent: already-normalised input stays the same.
        assertThat(service.normalizeSymbolForYahoo("USDTRY=X", InstrumentType.FX)).isEqualTo("USDTRY=X");
    }

    @Test
    void simple_crypto_splits_on_three_then_dash() {
        assertThat(service.normalizeSymbolForYahoo("BTCUSD", InstrumentType.CRYPTO)).isEqualTo("BTC-USD");
        assertThat(service.normalizeSymbolForYahoo("ETHUSD", InstrumentType.CRYPTO)).isEqualTo("ETH-USD");
        // Already-normalised: leave alone.
        assertThat(service.normalizeSymbolForYahoo("BTC-USD", InstrumentType.CRYPTO)).isEqualTo("BTC-USD");
    }

    /**
     * The whole reason this branch exists — explicit overrides for
     * tickers whose abbreviated app-symbol doesn't line up with Yahoo's.
     * Before the fix DOGUSD was rendered with the price of a worthless
     * "DOG-USD" token (~$0.000026) and showed -99.99% on the home page.
     */
    @Test
    void crypto_overrides_route_to_correct_yahoo_tickers() {
        assertThat(service.normalizeSymbolForYahoo("DOGUSD", InstrumentType.CRYPTO)).isEqualTo("DOGE-USD");
        assertThat(service.normalizeSymbolForYahoo("MATUSD", InstrumentType.CRYPTO)).isEqualTo("MATIC-USD");
        assertThat(service.normalizeSymbolForYahoo("AVXUSD", InstrumentType.CRYPTO)).isEqualTo("AVAX-USD");
        assertThat(service.normalizeSymbolForYahoo("LNKUSD", InstrumentType.CRYPTO)).isEqualTo("LINK-USD");
        // UNI-USD on Yahoo is a different token; Uniswap lives at UNI7083-USD.
        assertThat(service.normalizeSymbolForYahoo("UNIUSD", InstrumentType.CRYPTO)).isEqualTo("UNI7083-USD");
        assertThat(service.normalizeSymbolForYahoo("ALGOUSD", InstrumentType.CRYPTO)).isEqualTo("ALGO-USD");
        assertThat(service.normalizeSymbolForYahoo("ATOMUSD", InstrumentType.CRYPTO)).isEqualTo("ATOM-USD");
    }

    @Test
    void bist_stocks_get_dot_is_suffix() {
        assertThat(service.normalizeSymbolForYahoo("THYAO", InstrumentType.BIST)).isEqualTo("THYAO.IS");
        assertThat(service.normalizeSymbolForYahoo("GARAN.IS", InstrumentType.BIST)).isEqualTo("GARAN.IS");
    }

    @Test
    void indices_get_dot_is_suffix() {
        assertThat(service.normalizeSymbolForYahoo("XU100", InstrumentType.INDEX)).isEqualTo("XU100.IS");
        assertThat(service.normalizeSymbolForYahoo("XU030.IS", InstrumentType.INDEX)).isEqualTo("XU030.IS");
    }

    @Test
    void stock_passes_through_unchanged() {
        assertThat(service.normalizeSymbolForYahoo("AAPL", InstrumentType.STOCK)).isEqualTo("AAPL");
        assertThat(service.normalizeSymbolForYahoo("MSFT", InstrumentType.STOCK)).isEqualTo("MSFT");
    }

    @Test
    void commodity_aliases_map_to_yahoo_futures_tickers() {
        assertThat(service.normalizeSymbolForYahoo("XAUUSD", InstrumentType.COMMODITY)).isEqualTo("GC=F");
        assertThat(service.normalizeSymbolForYahoo("XAGUSD", InstrumentType.COMMODITY)).isEqualTo("SI=F");
        assertThat(service.normalizeSymbolForYahoo("WTI",    InstrumentType.COMMODITY)).isEqualTo("CL=F");
        assertThat(service.normalizeSymbolForYahoo("NGAS",   InstrumentType.COMMODITY)).isEqualTo("NG=F");
        assertThat(service.normalizeSymbolForYahoo("XCUUSD", InstrumentType.COMMODITY)).isEqualTo("HG=F");
        assertThat(service.normalizeSymbolForYahoo("XPTUSD", InstrumentType.COMMODITY)).isEqualTo("PL=F");
        // Unknown commodity returns the input unchanged.
        assertThat(service.normalizeSymbolForYahoo("OAT",    InstrumentType.COMMODITY)).isEqualTo("OAT");
    }

    @Test
    void viop_and_bond_and_fund_are_passthrough() {
        assertThat(service.normalizeSymbolForYahoo("XU030F", InstrumentType.VIOP)).isEqualTo("XU030F");
        assertThat(service.normalizeSymbolForYahoo("TR2YT",  InstrumentType.BOND)).isEqualTo("TR2YT");
        assertThat(service.normalizeSymbolForYahoo("AKB",    InstrumentType.FUND)).isEqualTo("AKB");
    }

    @Test
    void null_inputs_return_the_input_unchanged() {
        assertThat(service.normalizeSymbolForYahoo(null, InstrumentType.CRYPTO)).isNull();
        assertThat(service.normalizeSymbolForYahoo("BTCUSD", null)).isEqualTo("BTCUSD");
    }
}

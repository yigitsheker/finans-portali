package com.finansportali.backend.service.client.bond;

import com.finansportali.backend.dto.response.bond.BondQuoteDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Bond data provider backed exclusively by TCMB EVDS3 "Devlet İç Borçlanma
 * Senetlerinin Gösterge Niteliğindeki Değerleri" — every quote returned here
 * is a real, daily-updated value with a real TRT/TRD ISIN.
 *
 * <p>An earlier iteration of this class also produced synthetic policy-rate-
 * plus-spread quotes for missing maturities and seeded sukuk/eurobond rows.
 * That fallback was deliberately removed: a finance site is better off
 * surfacing fewer real numbers than many fake ones.
 *
 * <p>The heavy lifting lives in {@link EvdsBondYieldFetcher}; this class is a
 * thin {@link BondDataProvider} adapter so the existing
 * {@code BondDataRefreshService} pipeline keeps working unchanged.
 */
@Service
@ConditionalOnProperty(name = "app.bonds.provider", havingValue = "TCMB", matchIfMissing = false)
public class TcmbBondDataProvider implements BondDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TcmbBondDataProvider.class);

    private final EvdsBondYieldFetcher evdsBondYieldFetcher;

    public TcmbBondDataProvider(EvdsBondYieldFetcher evdsBondYieldFetcher) {
        this.evdsBondYieldFetcher = evdsBondYieldFetcher;
    }

    @Override
    public String getProviderName() {
        return "TCMB";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<BondQuoteDto> fetchLatestBondQuotes() {
        // Real bond data from TCMB EVDS3 only. The fetcher enumerates the whole
        // bie_pydibs datagroup at runtime (no hardcoded ISINs), keeps every
        // active fixed-coupon government bond, and derives a clean-price YTM
        // per issue. Source label "TCMB_EVDS3". The Investing.com yield-curve
        // source was dropped — too little usable detail.
        List<BondQuoteDto> quotes = new ArrayList<>(evdsBondYieldFetcher.fetchAll());
        log.info("[TCMB] Returned {} bond quote(s) from EVDS3", quotes.size());
        return quotes;
    }

    @Override
    public List<BondQuoteDto> fetchHistoricalYield(String symbol, LocalDate from, LocalDate to) {
        // Historical yield snapshots were derived from synthetic spreads and
        // produced misleading charts; intentionally removed.
        return Collections.emptyList();
    }

    @Override
    public Optional<BondQuoteDto> fetchBySymbol(String symbol) {
        return fetchLatestBondQuotes().stream()
                .filter(q -> symbol.equalsIgnoreCase(q.getSymbol()))
                .findFirst();
    }
}

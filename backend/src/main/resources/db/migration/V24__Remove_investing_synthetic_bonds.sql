-- Remove the retired Investing.com synthetic yield-curve bonds.
--
-- TCMB EVDS3 is now the sole bond source: the InvestingYieldCurveFetcher was
-- deleted and the EvdsBondYieldFetcher enumerates the whole bie_pydibs
-- datagroup. The Investing fetcher's old synthetic rows (TR3MO, TR6MO, TR9MO,
-- TR2YR, TR3YR, TR5YR, TR10YR — price 100, no coupon, made-up tenor yields)
-- still sit in the DB as active instruments and clash with the real EVDS
-- curve. The live refresh's stale-deactivation pass cannot reach them because
-- INVESTING_TR is no longer a registered provider, so purge them here.
--
-- Scope: only instruments whose quotes are ALL from INVESTING_TR (i.e. they
-- carry no quote from any surviving source). Quotes are removed by the
-- ON DELETE CASCADE on debt_instrument_quotes.instrument_id.
DELETE FROM debt_instruments i
WHERE EXISTS (
        SELECT 1 FROM debt_instrument_quotes q
        WHERE q.instrument_id = i.id AND q.source = 'INVESTING_TR')
  AND NOT EXISTS (
        SELECT 1 FROM debt_instrument_quotes q
        WHERE q.instrument_id = i.id AND q.source <> 'INVESTING_TR');

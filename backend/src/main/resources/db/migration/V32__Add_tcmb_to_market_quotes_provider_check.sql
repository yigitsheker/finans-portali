-- Hibernate auto-generates a CHECK constraint on market_quotes.provider from
-- the MarketDataProvider enum's declared constants. Adding TCMB (benchmark
-- TR2Y/TR5Y/TR10Y bond yields sourced from TCMB EVDS3) to the enum doesn't
-- retroactively widen an existing constraint under ddl-auto=update — it only
-- adds constraints for genuinely new columns. Recreate it explicitly with the
-- new value, in the same shape Hibernate generates, so future ddl-auto=update
-- runs see a match and leave it alone.
ALTER TABLE market_quotes DROP CONSTRAINT IF EXISTS market_quotes_provider_check;
ALTER TABLE market_quotes ADD CONSTRAINT market_quotes_provider_check
    CHECK (((provider)::text = ANY ((ARRAY['YAHOO'::character varying, 'TWELVE_DATA'::character varying, 'TCMB'::character varying, 'NONE'::character varying])::text[])));

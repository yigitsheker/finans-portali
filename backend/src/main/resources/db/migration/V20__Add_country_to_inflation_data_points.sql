-- Multi-country support for the inflation table. Existing rows are TCMB-
-- sourced Turkish CPI; new rows from FRED bring US CPI. The (period_date,
-- country) pair becomes the natural key — two countries can publish for
-- the same month independently.

ALTER TABLE inflation_data_points
    ADD COLUMN country VARCHAR(2) NOT NULL DEFAULT 'TR';

-- Drop the old single-column unique constraint. Its auto-generated name
-- depends on the Postgres version; the IF EXISTS form covers both cases
-- we've seen in the wild.
ALTER TABLE inflation_data_points
    DROP CONSTRAINT IF EXISTS inflation_data_points_period_date_key;

ALTER TABLE inflation_data_points
    ADD CONSTRAINT inflation_period_country_uk UNIQUE (period_date, country);

CREATE INDEX IF NOT EXISTS idx_inflation_country_period
    ON inflation_data_points(country, period_date DESC);

COMMENT ON COLUMN inflation_data_points.country IS
    'ISO 3166-1 alpha-2 country code. ''TR'' for TCMB CPI, ''US'' for FRED CPIAUCSL.';

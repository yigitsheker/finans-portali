-- Add 3-month, 6-month, 3-year, 5-year return columns to investment_funds.
-- Backs the new TEFAS getiri3a/getiri6a/getiri3y/getiri5y fields exposed by
-- /api/funds/fonGetiriBazliBilgiGetir.

ALTER TABLE investment_funds
    ADD COLUMN IF NOT EXISTS three_month_return NUMERIC(8, 4),
    ADD COLUMN IF NOT EXISTS six_month_return   NUMERIC(8, 4),
    ADD COLUMN IF NOT EXISTS three_year_return  NUMERIC(8, 4),
    ADD COLUMN IF NOT EXISTS five_year_return   NUMERIC(8, 4);

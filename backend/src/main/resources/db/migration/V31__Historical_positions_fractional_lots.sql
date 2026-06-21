-- Allow fractional quantities (e.g. 0.01 BTC). The lots column was INTEGER,
-- which rejected crypto/precious-metal positions bought in fractions.
ALTER TABLE historical_positions
    ALTER COLUMN lots TYPE DECIMAL(20, 8);

-- The chk_lots_positive (lots > 0) constraint stays valid for decimals.

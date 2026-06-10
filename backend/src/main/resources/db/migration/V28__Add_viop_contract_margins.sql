-- Per-contract initial / maintenance margin (teminat) on the VİOP catalog.
-- Lets requiredMargin use an authoritative, time-varying per-contract margin
-- (initialMargin × qty) when populated from a verified exchange source
-- (Takasbank VİOP margin parameters). NULL → category-based margin rate is used.
ALTER TABLE viop_contracts ADD COLUMN IF NOT EXISTS initial_margin     NUMERIC(19, 2);
ALTER TABLE viop_contracts ADD COLUMN IF NOT EXISTS maintenance_margin NUMERIC(19, 2);

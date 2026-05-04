-- Add purchase_date column to portfolio_positions table
ALTER TABLE portfolio_positions ADD COLUMN purchase_date DATE;

-- Set purchase_date to current date for existing positions
UPDATE portfolio_positions SET purchase_date = CURRENT_DATE WHERE purchase_date IS NULL;

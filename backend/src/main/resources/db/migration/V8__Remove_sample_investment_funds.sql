-- Remove sample/mock investment fund data
-- These were placeholder records and should be replaced with real TEFAS data

DELETE FROM investment_funds WHERE fund_code IN ('SAMPLE001', 'SAMPLE002');

-- Add comment to clarify this table should only contain real data
COMMENT ON TABLE investment_funds IS 'Turkish investment funds data from TEFAS with real performance metrics';

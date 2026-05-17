-- TEFAS'ta uzun vadeli (3y/5y) getiriler %10000+ olabilir (TL devalüasyonu nedeniyle).
-- NUMERIC(8, 4) max 9999.9999 sınırını aşıyor; tüm return kolonlarını NUMERIC(12, 4)'e genişlet.

ALTER TABLE investment_funds
    ALTER COLUMN daily_return       TYPE NUMERIC(12, 4),
    ALTER COLUMN weekly_return      TYPE NUMERIC(12, 4),
    ALTER COLUMN monthly_return     TYPE NUMERIC(12, 4),
    ALTER COLUMN three_month_return TYPE NUMERIC(12, 4),
    ALTER COLUMN six_month_return   TYPE NUMERIC(12, 4),
    ALTER COLUMN yearly_return      TYPE NUMERIC(12, 4),
    ALTER COLUMN three_year_return  TYPE NUMERIC(12, 4),
    ALTER COLUMN five_year_return   TYPE NUMERIC(12, 4);

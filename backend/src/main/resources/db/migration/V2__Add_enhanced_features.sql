-- Enhanced features for Finans Portali
-- Created: 2026-05-01
-- Adds: Exchange rates, Investment funds, Enhanced news

-- Exchange Rates Table (TCMB and other sources)
CREATE TABLE exchange_rates (
    id BIGSERIAL PRIMARY KEY,
    currency_code VARCHAR(10) NOT NULL,
    currency_name VARCHAR(50) NOT NULL,
    buying_rate DECIMAL(10,4) NOT NULL,
    selling_rate DECIMAL(10,4) NOT NULL,
    effective_buying_rate DECIMAL(10,4) NOT NULL,
    effective_selling_rate DECIMAL(10,4) NOT NULL,
    rate_date DATE NOT NULL,
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Investment Funds Table
CREATE TABLE investment_funds (
    id BIGSERIAL PRIMARY KEY,
    fund_code VARCHAR(20) NOT NULL UNIQUE,
    fund_name VARCHAR(200) NOT NULL,
    fund_type VARCHAR(100) NOT NULL,
    management_company VARCHAR(100) NOT NULL,
    unit_price DECIMAL(15,6) NOT NULL,
    total_value DECIMAL(15,6) NOT NULL,
    management_fee DECIMAL(8,4),
    performance_fee DECIMAL(8,4),
    price_date DATE NOT NULL,
    daily_return DECIMAL(8,4),
    weekly_return DECIMAL(8,4),
    monthly_return DECIMAL(8,4),
    yearly_return DECIMAL(8,4),
    risk_level VARCHAR(10),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Add new columns to news_articles for enhanced functionality
-- First, drop the unique constraint on url
ALTER TABLE news_articles DROP CONSTRAINT IF EXISTS news_articles_url_key;

-- Rename existing url column to source_url (reuse it instead of creating new column)
ALTER TABLE news_articles RENAME COLUMN url TO source_url;

-- Add source_name column
ALTER TABLE news_articles ADD COLUMN IF NOT EXISTS source_name VARCHAR(100);

-- Indexes for new tables
CREATE INDEX idx_exchange_rates_source_date ON exchange_rates(source, rate_date DESC);
CREATE INDEX idx_exchange_rates_currency_date ON exchange_rates(currency_code, rate_date DESC);
CREATE INDEX idx_exchange_rates_date ON exchange_rates(rate_date DESC);

CREATE INDEX idx_investment_funds_type ON investment_funds(fund_type);
CREATE INDEX idx_investment_funds_company ON investment_funds(management_company);
CREATE INDEX idx_investment_funds_performance ON investment_funds(yearly_return DESC NULLS LAST);
CREATE INDEX idx_investment_funds_size ON investment_funds(total_value DESC);
CREATE INDEX idx_investment_funds_price_date ON investment_funds(price_date DESC);

-- Add indexes for enhanced news functionality
CREATE INDEX idx_news_articles_source ON news_articles(source_name);
CREATE INDEX idx_news_articles_title_search ON news_articles USING gin(to_tsvector('turkish', title));
CREATE INDEX idx_news_articles_summary_search ON news_articles USING gin(to_tsvector('turkish', summary));

-- Comments for new tables
COMMENT ON TABLE exchange_rates IS 'Exchange rates from TCMB and other financial institutions';
COMMENT ON TABLE investment_funds IS 'Turkish investment funds data with performance metrics';

COMMENT ON COLUMN exchange_rates.buying_rate IS 'Bank buying rate for the currency';
COMMENT ON COLUMN exchange_rates.selling_rate IS 'Bank selling rate for the currency';
COMMENT ON COLUMN exchange_rates.effective_buying_rate IS 'Effective buying rate (forex)';
COMMENT ON COLUMN exchange_rates.effective_selling_rate IS 'Effective selling rate (forex)';
COMMENT ON COLUMN exchange_rates.source IS 'Data source: TCMB, Garanti, İş Bankası, etc.';

COMMENT ON COLUMN investment_funds.fund_type IS 'Fund type: Hisse Senedi Fonu, Borçlanma Araçları Fonu, etc.';
COMMENT ON COLUMN investment_funds.management_fee IS 'Annual management fee percentage';
COMMENT ON COLUMN investment_funds.performance_fee IS 'Performance fee percentage';
COMMENT ON COLUMN investment_funds.risk_level IS 'Risk level: DÜŞÜK, ORTA, YÜKSEK';

-- Add some sample data for fund types
INSERT INTO investment_funds (fund_code, fund_name, fund_type, management_company, unit_price, total_value, price_date) VALUES
('SAMPLE001', 'Örnek Hisse Senedi Fonu', 'Hisse Senedi Fonu', 'Örnek Portföy', 10.0000, 100000000.00, CURRENT_DATE),
('SAMPLE002', 'Örnek Borçlanma Araçları Fonu', 'Borçlanma Araçları Fonu', 'Örnek Portföy', 8.5000, 50000000.00, CURRENT_DATE)
ON CONFLICT (fund_code) DO NOTHING;

-- Add some sample exchange rates
INSERT INTO exchange_rates (currency_code, currency_name, buying_rate, selling_rate, effective_buying_rate, effective_selling_rate, rate_date, source) VALUES
('USD', 'US Dollar', 34.2500, 34.3500, 34.2000, 34.4000, CURRENT_DATE, 'TCMB'),
('EUR', 'Euro', 37.1500, 37.2500, 37.1000, 37.3000, CURRENT_DATE, 'TCMB'),
('GBP', 'British Pound', 43.5000, 43.6500, 43.4500, 43.7000, CURRENT_DATE, 'TCMB')
ON CONFLICT DO NOTHING;
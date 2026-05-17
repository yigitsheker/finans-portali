-- ============================================================================
-- V7: Tahvil ve Bono (Borçlanma Araçları) Modülü
-- ============================================================================
-- Türkiye finans piyasalarındaki devlet tahvilleri, hazine bonoları ve
-- diğer borçlanma araçlarını takip etmek için tablolar.
-- ============================================================================

-- Borçlanma araçları tablosu
CREATE TABLE debt_instruments (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL UNIQUE,
    isin VARCHAR(12),
    name VARCHAR(200) NOT NULL,
    type VARCHAR(30) NOT NULL,
    issuer VARCHAR(100),
    currency VARCHAR(3),
    maturity_date DATE,
    coupon_rate NUMERIC(10, 4),
    coupon_type VARCHAR(30),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- İndeksler
CREATE INDEX idx_debt_symbol ON debt_instruments(symbol);
CREATE INDEX idx_debt_isin ON debt_instruments(isin);
CREATE INDEX idx_debt_type ON debt_instruments(type);
CREATE INDEX idx_debt_maturity ON debt_instruments(maturity_date);

-- Borçlanma aracı fiyat/getiri verileri tablosu
CREATE TABLE debt_instrument_quotes (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES debt_instruments(id) ON DELETE CASCADE,
    quote_date DATE NOT NULL,
    price NUMERIC(12, 4),
    yield_rate NUMERIC(10, 4),
    clean_price NUMERIC(12, 4),
    dirty_price NUMERIC(12, 4),
    volume NUMERIC(18, 2),
    change_rate NUMERIC(10, 4),
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_debt_quote UNIQUE (instrument_id, quote_date, source)
);

-- İndeksler
CREATE INDEX idx_debt_quote_instrument ON debt_instrument_quotes(instrument_id);
CREATE INDEX idx_debt_quote_date ON debt_instrument_quotes(quote_date);
CREATE INDEX idx_debt_quote_source ON debt_instrument_quotes(source);

-- Yorum ekleyelim
COMMENT ON TABLE debt_instruments IS 'Tahvil, bono ve diğer borçlanma araçları';
COMMENT ON TABLE debt_instrument_quotes IS 'Borçlanma araçlarının günlük fiyat ve getiri verileri';

COMMENT ON COLUMN debt_instruments.symbol IS 'Uygulama içi sembol (örn: TR2YT, TR10YT)';
COMMENT ON COLUMN debt_instruments.isin IS 'Uluslararası menkul kıymet tanımlama numarası';
COMMENT ON COLUMN debt_instruments.type IS 'GOVERNMENT_BOND, TREASURY_BILL, LEASE_CERTIFICATE, EUROBOND, CORPORATE_BOND, OTHER';
COMMENT ON COLUMN debt_instruments.issuer IS 'İhraççı kurum (örn: Hazine ve Maliye Bakanlığı)';
COMMENT ON COLUMN debt_instruments.maturity_date IS 'Vade tarihi';
COMMENT ON COLUMN debt_instruments.coupon_rate IS 'Kupon oranı (yıllık %)';
COMMENT ON COLUMN debt_instruments.coupon_type IS 'Sabit, Değişken, Sıfır Kuponlu';

COMMENT ON COLUMN debt_instrument_quotes.price IS 'Fiyat (nominal değere göre %)';
COMMENT ON COLUMN debt_instrument_quotes.yield_rate IS 'Getiri oranı (%)';
COMMENT ON COLUMN debt_instrument_quotes.clean_price IS 'Temiz fiyat (tahakkuk eden faiz hariç)';
COMMENT ON COLUMN debt_instrument_quotes.dirty_price IS 'Kirli fiyat (tahakkuk eden faiz dahil)';
COMMENT ON COLUMN debt_instrument_quotes.volume IS 'İşlem hacmi (nominal değer)';
COMMENT ON COLUMN debt_instrument_quotes.change_rate IS 'Önceki güne göre değişim (%)';
COMMENT ON COLUMN debt_instrument_quotes.source IS 'Veri kaynağı (TCMB, BIST, DEMO)';

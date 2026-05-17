-- Türkiye enflasyon serileri (TCMB EVDS3 kaynaklı, aylık)
-- TÜFE: Tüketici Fiyat Endeksi (CPI), ÜFE: Yurt İçi Üretici Fiyat Endeksi (PPI)

CREATE TABLE inflation_data_points (
    id                  BIGSERIAL PRIMARY KEY,
    period_date         DATE NOT NULL UNIQUE,            -- ayın 1. günü, örn. "2024-01-01"
    cpi_index           NUMERIC(15,4),                   -- TÜFE genel endeks (2003=100)
    cpi_yearly_change   NUMERIC(8,4),                    -- Yıllık % değişim
    cpi_monthly_change  NUMERIC(8,4),                    -- Aylık % değişim (önceki aya göre)
    ppi_index           NUMERIC(15,4),                   -- Yİ-ÜFE genel endeks (opsiyonel)
    ppi_yearly_change   NUMERIC(8,4),                    -- Yİ-ÜFE Yıllık % değişim (opsiyonel)
    source              VARCHAR(40) NOT NULL DEFAULT 'TCMB_EVDS3',
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inflation_period_date ON inflation_data_points(period_date DESC);

COMMENT ON TABLE inflation_data_points IS
    'Aylık enflasyon verileri (TÜFE/ÜFE). Kaynak: TCMB EVDS3 - TP.FE.OKTG01, TP.FG.J0, TP.UFE.S01.';

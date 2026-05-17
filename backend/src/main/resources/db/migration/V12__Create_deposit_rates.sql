-- TCMB EVDS3 mevduat faizi serileri (TP.{TRY|USD|EUR}.MT{01..06})
-- Her ay için 3 döviz × 6 vade = 18 değer; tek satırda saklıyoruz (currency başına)

CREATE TABLE deposit_rate_points (
    id              BIGSERIAL PRIMARY KEY,
    period_date     DATE NOT NULL,                  -- ayın 1. günü
    currency        VARCHAR(3) NOT NULL,            -- TRY / USD / EUR
    rate_1m         NUMERIC(8,4),                   -- 1 aya kadar       (MT01)
    rate_3m         NUMERIC(8,4),                   -- 3 aya kadar       (MT02)
    rate_6m         NUMERIC(8,4),                   -- 6 aya kadar       (MT03)
    rate_12m        NUMERIC(8,4),                   -- 1 yıla kadar      (MT04)
    rate_over_12m   NUMERIC(8,4),                   -- 1 yıldan uzun     (MT05)
    rate_avg        NUMERIC(8,4),                   -- Ağırlıklı ortalama (MT06)
    source          VARCHAR(40) NOT NULL DEFAULT 'TCMB_EVDS3',
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_deposit_rate_period_currency UNIQUE (period_date, currency)
);

CREATE INDEX idx_deposit_rate_period ON deposit_rate_points(period_date DESC);
CREATE INDEX idx_deposit_rate_currency ON deposit_rate_points(currency);

COMMENT ON TABLE deposit_rate_points IS
    'Aylık bankalar ortalama mevduat faiz oranları. Kaynak: TCMB EVDS3 - TP.{currency}.MT{01-06}.';

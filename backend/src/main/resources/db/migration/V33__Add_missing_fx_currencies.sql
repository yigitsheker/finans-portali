-- Döviz sayfası (MarketData) tüm TCMB kurlarını gösterir, ancak market_instruments
-- FX kataloğunda yalnızca 8 ana parite vardı. Gösterilen ama katalogda bulunmayan
-- dövizler (DKK, NOK, SEK, SAR, KWD) portföye eklenirken "Unknown symbol" hatası
-- veriyordu. Bunları parite (CODETRY / Yahoo CODETRY=X) olarak kataloğa ekliyoruz;
-- fiyatları PriceRefreshScheduler tarafından otomatik doldurulur.
INSERT INTO market_instruments (symbol, name, type, finnhub_symbol, delayed, created_at, updated_at, provider, provider_symbol)
SELECT v.symbol, v.name, 'FX', '', false, now(), now(), 'YAHOO', v.provider_symbol
FROM (VALUES
    ('DKKTRY', 'DKK/TRY', 'DKKTRY=X'),
    ('NOKTRY', 'NOK/TRY', 'NOKTRY=X'),
    ('SEKTRY', 'SEK/TRY', 'SEKTRY=X'),
    ('SARTRY', 'SAR/TRY', 'SARTRY=X'),
    ('KWDTRY', 'KWD/TRY', 'KWDTRY=X')
) AS v(symbol, name, provider_symbol)
WHERE NOT EXISTS (
    SELECT 1 FROM market_instruments m WHERE m.symbol = v.symbol
);

-- V23: fix mojibake in market instrument names.
-- The seed names were committed as double-encoded UTF-8 (e.g. "TÃ¼rk Hava
-- YollarÄ±" instead of "Türk Hava Yolları"). The source file is now correct
-- UTF-8, but seedIfEmpty() only inserts when a symbol is missing — it never
-- updates existing rows, so already-seeded databases keep the garbled names.
-- This migration rewrites the name column for the affected instruments.
-- Idempotent: re-running sets the same correct values. Quotes/candles are
-- untouched (only the display name changes).

UPDATE market_instruments SET name = 'Altın (XAU/USD)' WHERE symbol = 'XAUUSD';
UPDATE market_instruments SET name = 'Gümüş (XAG/USD)' WHERE symbol = 'XAGUSD';
UPDATE market_instruments SET name = 'Doğalgaz (Henry Hub)' WHERE symbol = 'NGAS';
UPDATE market_instruments SET name = 'Bakır (XCU/USD)' WHERE symbol = 'XCUUSD';
UPDATE market_instruments SET name = 'Türk Hava Yolları' WHERE symbol = 'THYAO';
UPDATE market_instruments SET name = 'Şişe Cam' WHERE symbol = 'SISE';
UPDATE market_instruments SET name = 'Koç Holding' WHERE symbol = 'KCHOL';
UPDATE market_instruments SET name = 'Ereğli Demir Çelik' WHERE symbol = 'EREGL';
UPDATE market_instruments SET name = 'BİM Mağazalar' WHERE symbol = 'BIMAS';
UPDATE market_instruments SET name = 'İş Bankası' WHERE symbol = 'ISCTR';
UPDATE market_instruments SET name = 'Tüpraş' WHERE symbol = 'TUPRS';
UPDATE market_instruments SET name = 'Sabancı Holding' WHERE symbol = 'SAHOL';
UPDATE market_instruments SET name = 'Vakıfbank' WHERE symbol = 'VAKBN';
UPDATE market_instruments SET name = 'Enka İnşaat' WHERE symbol = 'ENKAI';
UPDATE market_instruments SET name = 'Koza Altın' WHERE symbol = 'KOZAL';
UPDATE market_instruments SET name = 'Türk Telekom' WHERE symbol = 'TTKOM';
UPDATE market_instruments SET name = 'Tofaş Oto' WHERE symbol = 'TOASO';
UPDATE market_instruments SET name = 'Arçelik' WHERE symbol = 'ARCLK';
UPDATE market_instruments SET name = 'TAV Havalimanları' WHERE symbol = 'TAVHL';
UPDATE market_instruments SET name = 'Yapı Kredi Bankası' WHERE symbol = 'YKBNK';
UPDATE market_instruments SET name = 'Doğan Holding' WHERE symbol = 'DOHOL';
UPDATE market_instruments SET name = 'Odaş Elektrik' WHERE symbol = 'ODAS';
UPDATE market_instruments SET name = 'Şok Marketler' WHERE symbol = 'SOKM';
UPDATE market_instruments SET name = 'Ülker Bisküvi' WHERE symbol = 'ULKER';
UPDATE market_instruments SET name = 'Coca Cola İçecek' WHERE symbol = 'CCOLA';
UPDATE market_instruments SET name = 'Çemtaş' WHERE symbol = 'CEMTS';
UPDATE market_instruments SET name = 'Çimsa' WHERE symbol = 'CIMSA';
UPDATE market_instruments SET name = 'Doğuş Otomotiv' WHERE symbol = 'DOAS';
UPDATE market_instruments SET name = 'Ege Endüstri' WHERE symbol = 'EGEEN';
UPDATE market_instruments SET name = 'Gen İlaç' WHERE symbol = 'GENIL';
UPDATE market_instruments SET name = 'Gübre Fabrikaları' WHERE symbol = 'GLYHO';
UPDATE market_instruments SET name = 'Gözde Girişim' WHERE symbol = 'GOZDE';
UPDATE market_instruments SET name = 'Gübre Fabrikaları' WHERE symbol = 'GUBRF';
UPDATE market_instruments SET name = 'Hektaş' WHERE symbol = 'HEKTS';
UPDATE market_instruments SET name = 'İpek Doğal Enerji' WHERE symbol = 'IPEKE';
UPDATE market_instruments SET name = 'İş GYO' WHERE symbol = 'ISGYO';
UPDATE market_instruments SET name = 'Logo Yazılım' WHERE symbol = 'LOGO';
UPDATE market_instruments SET name = 'MLP Sağlık' WHERE symbol = 'MPARK';
UPDATE market_instruments SET name = 'Netaş' WHERE symbol = 'NETAS';
UPDATE market_instruments SET name = 'Oyak Çimento' WHERE symbol = 'OYAKC';
UPDATE market_instruments SET name = 'Pınar Et ve Un' WHERE symbol = 'PETUN';
UPDATE market_instruments SET name = 'Pınar Süt' WHERE symbol = 'PNSUT';
UPDATE market_instruments SET name = 'Selçuk Ecza' WHERE symbol = 'SELEC';
UPDATE market_instruments SET name = 'Şekerbank' WHERE symbol = 'SKBNK';
UPDATE market_instruments SET name = 'Smart Güneş' WHERE symbol = 'SMART';
UPDATE market_instruments SET name = 'Tat Gıda' WHERE symbol = 'TATGD';
UPDATE market_instruments SET name = 'Teknik Yapı' WHERE symbol = 'TKNSA';
UPDATE market_instruments SET name = 'Tümosan Motor' WHERE symbol = 'TMSN';
UPDATE market_instruments SET name = 'Türk Traktör' WHERE symbol = 'TTRAK';
UPDATE market_instruments SET name = 'Yataş' WHERE symbol = 'YATAS';
UPDATE market_instruments SET name = 'Bagfaş' WHERE symbol = 'BAGFS';
UPDATE market_instruments SET name = 'Borusan Yatırım' WHERE symbol = 'BRYAT';
UPDATE market_instruments SET name = 'Bursa Çimento' WHERE symbol = 'BUCIM';
UPDATE market_instruments SET name = 'Çelebi Hava' WHERE symbol = 'CLEBI';
UPDATE market_instruments SET name = 'Doğan Şirketler' WHERE symbol = 'DGKLB';
UPDATE market_instruments SET name = 'Altın/TRY Vadeli' WHERE symbol = 'GOLDTRYF';
UPDATE market_instruments SET name = '2 Yıl Devlet Tahvili' WHERE symbol = 'TR2Y';
UPDATE market_instruments SET name = '5 Yıl Devlet Tahvili' WHERE symbol = 'TR5Y';
UPDATE market_instruments SET name = '10 Yıl Devlet Tahvili' WHERE symbol = 'TR10Y';
UPDATE market_instruments SET name = 'ABD 2 Yıl Tahvili' WHERE symbol = 'US2Y';
UPDATE market_instruments SET name = 'ABD 10 Yıl Tahvili' WHERE symbol = 'US10Y';
UPDATE market_instruments SET name = 'İş Bankası Altın Fonu' WHERE symbol = 'ISBALT';
UPDATE market_instruments SET name = 'Akbank Teknoloji Sektör Fonu' WHERE symbol = 'AKBTEK';
UPDATE market_instruments SET name = 'Garanti Para Piyasası Fonu' WHERE symbol = 'GARPAR';
UPDATE market_instruments SET name = 'Yapı Kredi Kira Sertifikası Fonu' WHERE symbol = 'YAPKRE';

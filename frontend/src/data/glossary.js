/**
 * Finance glossary — bilingual definitions surfaced via <TermInfo />.
 *
 * Keys are short, stable slugs (snake or kebab-case). Each entry holds two
 * objects keyed by language code (`tr`, `en`). A definition is one or two
 * short sentences — anything longer belongs in a help article, not a
 * tooltip.
 *
 * When the UI language changes the same termKey resolves to the right
 * translation. To add a new term: pick a slug, add tr + en blocks, then
 * drop `<TermInfo termKey="..." />` next to the place in the JSX where the
 * concept first appears.
 */
export const GLOSSARY = {
  // ── Inflation / macroeconomics ───────────────────────────────────────
  cpi: {
    tr: "TÜFE (Tüketici Fiyat Endeksi): Hanehalkının tükettiği mal ve hizmet sepetinin fiyat seviyesini ölçer. Enflasyonun ana göstergesidir.",
    en: "CPI (Consumer Price Index): Measures the price level of a basket of goods and services consumed by households. The primary inflation gauge.",
  },
  ppi: {
    tr: "ÜFE (Üretici Fiyat Endeksi): Üreticilerin sattığı malların fiyat seviyesini ölçer. Tüketici enflasyonunun erken göstergesidir.",
    en: "PPI (Producer Price Index): Measures the price level of goods sold by producers. Often leads consumer inflation by a few months.",
  },
  yearly_change: {
    tr: "Yıllık Değişim (YoY): Bir veri serisinin geçen yılın aynı dönemine göre yüzde değişimi. Mevsimsel etkileri ortadan kaldırır.",
    en: "Year-over-Year (YoY): Percentage change of a data point versus the same period last year. Strips out seasonal effects.",
  },
  monthly_change: {
    tr: "Aylık Değişim (MoM): Bir veri serisinin bir önceki aya göre yüzde değişimi. Kısa vadeli ivmenin göstergesidir.",
    en: "Month-over-Month (MoM): Percentage change versus the prior month. Captures short-term momentum.",
  },
  cumulative_inflation: {
    tr: "Birikimli Enflasyon: Belirli bir dönem boyunca biriken toplam enflasyon. (1+aylık_enf)'lerin çarpımından elde edilir, basit toplamından değil.",
    en: "Cumulative Inflation: Total inflation accrued over a window — the product of (1 + monthly), not a simple sum, so it compounds.",
  },
  nominal_return: {
    tr: "Nominal Getiri: Para biriminin satın alma gücü değişimi dikkate alınmadan ölçülen getiri.",
    en: "Nominal Return: Return measured in raw currency terms without adjusting for inflation's drag on purchasing power.",
  },
  real_return: {
    tr: "Reel Getiri: Enflasyonun etkisinden arındırılmış getiri. Formül: (1+nominal) / (1+enflasyon) − 1.",
    en: "Real Return: Return after stripping out inflation. Formula: (1+nominal) / (1+inflation) − 1.",
  },
  base_index: {
    tr: "Baz Endeks: TÜFE/ÜFE serilerinde 100 olarak alınan referans dönem. Türkiye serisi için 2003 = 100.",
    en: "Base Index: The reference period set equal to 100 in a CPI/PPI series. Turkey's series uses 2003 = 100.",
  },

  // ── Bonds & bills ────────────────────────────────────────────────────
  bond: {
    tr: "Tahvil: Vadesi 1 yıldan uzun olan borçlanma aracı. Tipik olarak periyodik kupon ödemesi yapar.",
    en: "Bond: A debt instrument with maturity over one year. Usually pays a periodic coupon to the holder.",
  },
  bill: {
    tr: "Bono: Vadesi 1 yıla kadar olan kısa vadeli borçlanma aracı. Genelde kuponsuzdur, iskontolu satılır.",
    en: "Bill: A short-term debt instrument maturing within one year. Typically zero-coupon, sold at a discount to face value.",
  },
  coupon: {
    tr: "Kupon: Tahvilin nominal değerine uygulanan yıllık faiz oranı. Tahvil yatırımcısına periyodik olarak ödenir.",
    en: "Coupon: The annual interest rate paid to a bondholder, expressed as a percent of face value.",
  },
  maturity: {
    tr: "Vade: Borçlanma aracının nominal değerinin geri ödendiği tarih.",
    en: "Maturity: The date on which the principal of a debt instrument is repaid in full.",
  },
  yield: {
    tr: "Getiri (Yield): Tahvilin alındığı fiyat üzerinden hesaplanan, vade sonuna kadar elde edilecek yıllık getiri oranı.",
    en: "Yield: The annual return earned on a bond if held to maturity, calculated from its current price.",
  },
  yield_curve: {
    tr: "Getiri Eğrisi: Aynı kredi kalitesinde, farklı vadelerdeki tahvillerin getirilerini gösteren eğri. Şeklinden makro beklenti okunur.",
    en: "Yield Curve: A plot of yields versus maturities for bonds of the same credit quality — its shape signals macro expectations.",
  },
  isin: {
    tr: "ISIN: International Securities Identification Number. 12 haneli, ülke kodu + dokuz karakter + kontrol basamağından oluşan eşsiz menkul kıymet tanımlayıcısı.",
    en: "ISIN: International Securities Identification Number — a 12-character code (country prefix + 9 chars + check digit) uniquely identifying a security.",
  },

  // ── Stocks ───────────────────────────────────────────────────────────
  stock: {
    tr: "Hisse Senedi: Şirketin sermayesinden pay temsil eden, sahibine ortaklık hakkı ve temettü hakkı veren menkul kıymet.",
    en: "Stock: A security representing partial ownership of a company, conferring voting and dividend rights to the holder.",
  },
  volume: {
    tr: "Hacim: Belirli bir dönemde işlem gören toplam lot/adet sayısı. Likiditenin ana göstergesidir.",
    en: "Volume: The total number of shares traded over a given period. The primary measure of an instrument's liquidity.",
  },
  market_cap: {
    tr: "Piyasa Değeri: Şirketin tüm hisselerinin güncel fiyatla çarpımı. Şirketin piyasaca biçilen büyüklüğüdür.",
    en: "Market Cap: Current share price multiplied by total shares outstanding — the market's valuation of the company.",
  },
  pe_ratio: {
    tr: "F/K (Fiyat / Kazanç): Hisse fiyatının hisse başına net karına oranı. Düşük F/K görece ucuz, yüksek F/K görece pahalı kabul edilir (sektör ortalaması ile karşılaştırarak).",
    en: "P/E (Price / Earnings): Share price divided by earnings per share. Lower is generally cheaper relative to peers, higher is more expensive.",
  },
  dividend: {
    tr: "Temettü: Şirketin kârının pay sahiplerine dağıttığı kısmı. Yıllık veya çeyreklik olarak ödenebilir.",
    en: "Dividend: The portion of a company's profit distributed to shareholders, paid annually or quarterly.",
  },

  // ── FX ───────────────────────────────────────────────────────────────
  forex_bid: {
    tr: "Alış (Bid): Bankanın dövizi sizden alacağı fiyat. Banka için maliyet, sizin için satış kuru.",
    en: "Bid: The price at which the dealer/bank will buy the currency from you. The lower side of the quote.",
  },
  forex_ask: {
    tr: "Satış (Ask): Bankanın size dövizi satacağı fiyat. Alış-satış farkı (spread) bankanın kârıdır.",
    en: "Ask: The price at which the dealer/bank will sell the currency to you. The higher side of the quote.",
  },
  forex_effective: {
    tr: "Efektif: Nakit (banknot) döviz işlemi. Genelde havale işleminden biraz daha geniş bir kur uygulanır.",
    en: "Effective Rate: The rate for physical banknote transactions, usually slightly wider than wire-transfer rates.",
  },
  cross_rate: {
    tr: "Çapraz Kur: İki para biriminin USD üzerinden hesaplanan kuru. EUR/TRY = (EUR/USD) × (USD/TRY).",
    en: "Cross Rate: The exchange rate between two currencies derived via USD. EUR/TRY = (EUR/USD) × (USD/TRY).",
  },

  // ── VIOP / derivatives ───────────────────────────────────────────────
  futures: {
    tr: "Vadeli İşlem (Futures): Önceden anlaşılan fiyat ve tarihte bir dayanak varlığı alma veya satma yükümlülüğü doğuran standart sözleşme.",
    en: "Futures: A standardised contract obliging the parties to buy/sell an underlying asset at a fixed price on a future date.",
  },
  option: {
    tr: "Opsiyon: Belirli bir tarihe kadar (veya o tarihte) bir dayanak varlığı önceden belirlenen fiyattan alma (call) veya satma (put) hakkı veren, ama yükümlülük doğurmayan sözleşme.",
    en: "Option: A contract giving the right — but not the obligation — to buy (call) or sell (put) an underlying at a strike price by/on a set date.",
  },
  underlying: {
    tr: "Dayanak Varlık: Türev sözleşmenin değerinin türetildiği hisse, endeks, döviz veya emtia.",
    en: "Underlying: The asset (stock, index, FX pair, commodity) whose price drives the value of a derivative contract.",
  },
  margin: {
    tr: "Marjin (Teminat): Vadeli ürün pozisyonu açmak için yatırılan teminat. Fiyat aleyhinize hareket ettiğinde marjin tamamlama (margin call) çağrısı gelebilir.",
    en: "Margin: Collateral posted to open a derivatives position. Adverse price moves can trigger a margin call to top it up.",
  },

  // ── Funds ────────────────────────────────────────────────────────────
  nav: {
    tr: "NAV / Fon Fiyatı: Fonun toplam varlığı eksi yükümlülükleri, dolaşımdaki pay sayısına bölündüğünde çıkan birim fiyat. Günlük hesaplanır.",
    en: "NAV (Net Asset Value): A fund's total assets minus liabilities, divided by units outstanding. Recalculated daily.",
  },
  aum: {
    tr: "Toplam Büyüklük (AUM): Fonun yönettiği toplam varlık değeri. Likidite ve operasyonel ölçeğin göstergesidir.",
    en: "AUM (Assets Under Management): The total market value of assets the fund manages. A proxy for liquidity and operational scale.",
  },
  expense_ratio: {
    tr: "Yönetim Ücreti / Gider Oranı: Fonun yıllık olarak NAV üzerinden tahsil ettiği işletim maliyetinin oranı.",
    en: "Expense Ratio: The fund's annual operating cost as a percent of NAV, automatically deducted from returns.",
  },
  risk_level: {
    tr: "Risk Düzeyi: SPK'nın 1-7 arası belirlediği skala. 1 = en düşük risk (para piyasası), 7 = en yüksek (kaldıraçlı/emtia).",
    en: "Risk Level: SPK's 1–7 scale. 1 is lowest (money market), 7 is highest (leveraged/commodity).",
  },

  // ── Commodities ──────────────────────────────────────────────────────
  commodity: {
    tr: "Emtia: Ham petrol, altın, gümüş, bakır, doğalgaz gibi standardize edilebilen ürünler. Global piyasalarda spot ve vadeli olarak işlem görür.",
    en: "Commodity: A standardised raw good — crude oil, gold, silver, copper, natural gas — traded on global markets via spot and futures contracts.",
  },

  // ── Crypto ───────────────────────────────────────────────────────────
  crypto_market_cap: {
    tr: "Kripto Piyasa Değeri: Coin fiyatı × dolaşımdaki arz. Hisse senedindeki piyasa değerinin kripto eşdeğeri.",
    en: "Crypto Market Cap: Coin price × circulating supply. The crypto-asset equivalent of a stock's market cap.",
  },
  circulating_supply: {
    tr: "Dolaşımdaki Arz: O an kamuya açık olarak alınıp satılan coin sayısı. Toplam arzdan kilitli / yakılmış miktarlar düşülerek bulunur.",
    en: "Circulating Supply: Coins currently available in the market, after subtracting locked or burned amounts from total supply.",
  },
  dominance: {
    tr: "Dominans: Bir kripto paranın piyasa değerinin toplam kripto piyasa değerine oranı. BTC dominansı piyasa rotasyonunun göstergesidir.",
    en: "Dominance: One asset's market cap as a share of the total crypto market cap. BTC dominance reflects market rotation.",
  },
  ath: {
    tr: "ATH (All-Time High): Varlığın tarihteki en yüksek fiyatı.",
    en: "ATH (All-Time High): The highest price the asset has ever traded at.",
  },

  // ── Portfolio ────────────────────────────────────────────────────────
  position: {
    tr: "Pozisyon: Belirli bir enstrümanda sahip olduğun miktar + ortalama maliyet.",
    en: "Position: Your holding in a specific instrument — quantity plus weighted average cost.",
  },
  avg_cost: {
    tr: "Ortalama Maliyet: Aynı enstrümanı farklı zamanlarda farklı fiyatlardan aldıysan, ağırlıklı ortalama alım fiyatın.",
    en: "Average Cost: Weighted average purchase price across all buy transactions of the same instrument.",
  },
  pnl: {
    tr: "Kar/Zarar (P&L): (Güncel fiyat − ortalama maliyet) × miktar. Pozisyonu satmadan görünen kâr veya zarar 'unrealize' / realize ettiğinde 'realize' olur.",
    en: "P&L (Profit & Loss): (Current price − avg cost) × quantity. Unrealised while open; becomes realised the moment you close the position.",
  },
};

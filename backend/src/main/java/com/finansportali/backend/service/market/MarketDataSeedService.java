package com.finansportali.backend.service.market;

import com.finansportali.backend.entity.*;
import com.finansportali.backend.repository.MarketCandleRepository;
import com.finansportali.backend.repository.MarketInstrumentRepository;
import com.finansportali.backend.repository.MarketQuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.security.SecureRandom;
import java.time.LocalDate;

/**
 * Seeds default market instruments, fallback quotes, and fallback candles.
 */
@Service
public class MarketDataSeedService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSeedService.class);
    // SecureRandom is constructed once and re-used (Sonar S2119). Instances are
    // thread-safe and the entropy gather is non-trivial — recreating it per
    // seedCandles() call would add startup cost for no benefit.
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MarketInstrumentRepository instrumentRepo;
    private final MarketQuoteRepository quoteRepo;
    private final MarketCandleRepository candleRepo;
    private final MarketInstrumentService instrumentService;

    public MarketDataSeedService(MarketInstrumentRepository instrumentRepo,
                                 MarketQuoteRepository quoteRepo,
                                 MarketCandleRepository candleRepo,
                                 MarketInstrumentService instrumentService) {
        this.instrumentRepo = instrumentRepo;
        this.quoteRepo = quoteRepo;
        this.candleRepo = candleRepo;
        this.instrumentService = instrumentService;
    }

    /**
     * Veritabanı boşsa enstrümanları ve fallback quote'ları oluşturur.
     * Gerçek veriler PriceRefreshScheduler tarafından doldurulur.
     */
    public void seedIfEmpty() {
        Instant now = Instant.now();

        // FX — Yahoo: "USDTRY=X", "EURTRY=X"
        var usdtry = upsert("USDTRY", "USD/TRY",              InstrumentType.FX,        "USDTRY=X",    false);
        var eurtry = upsert("EURTRY", "EUR/TRY",              InstrumentType.FX,        "EURTRY=X",    false);
        var gbptry = upsert("GBPTRY", "GBP/TRY",              InstrumentType.FX,        "GBPTRY=X",    false);
        var chftry = upsert("CHFTRY", "CHF/TRY",              InstrumentType.FX,        "CHFTRY=X",    false);
        var jpytry = upsert("JPYTRY", "JPY/TRY",              InstrumentType.FX,        "JPYTRY=X",    false);
        var audtry = upsert("AUDTRY", "AUD/TRY",              InstrumentType.FX,        "AUDTRY=X",    false);
        var cadtry = upsert("CADTRY", "CAD/TRY",              InstrumentType.FX,        "CADTRY=X",    false);
        var aedtry = upsert("AEDTRY", "AED/TRY",              InstrumentType.FX,        "AEDTRY=X",    false);
        // Emtia — Yahoo: futures tickers (GC/SI/CL/NG/HG/PL =F)
        var xauusd = upsert("XAUUSD", "Altın (XAU/USD)",      InstrumentType.COMMODITY, "GC=F",        false);
        var xagusd = upsert("XAGUSD", "Gümüş (XAG/USD)",      InstrumentType.COMMODITY, "SI=F",        false);
        var wti    = upsert("WTI",    "Ham Petrol (WTI)",     InstrumentType.COMMODITY, "CL=F",        false);
        var ngas   = upsert("NGAS",   "Doğalgaz (Henry Hub)", InstrumentType.COMMODITY, "NG=F",        false);
        var xcuusd = upsert("XCUUSD", "Bakır (XCU/USD)",      InstrumentType.COMMODITY, "HG=F",        false);
        var xptusd = upsert("XPTUSD", "Platin (XPT/USD)",     InstrumentType.COMMODITY, "PL=F",        false);
        // Endeks — BIST endeksleri gecikmeli
        var xu100  = upsert("XU100",  "BIST 100",             InstrumentType.INDEX,     "XU100.IS",    true);
        var xu050  = upsert("XU050",  "BIST 50",              InstrumentType.INDEX,     "XU050.IS",    true);
        var xu030  = upsert("XU030",  "BIST 30",              InstrumentType.INDEX,     "XU030.IS",    true);
        // Kripto — Yahoo: "BTC-USD"
        var btcusd = upsert("BTCUSD", "Bitcoin (BTC/USD)",    InstrumentType.CRYPTO,    "BTC-USD",     false);
        var ethusd = upsert("ETHUSD", "Ethereum (ETH/USD)",   InstrumentType.CRYPTO,    "ETH-USD",     false);
        var solusd = upsert("SOLUSD", "Solana (SOL/USD)",     InstrumentType.CRYPTO,    "SOL-USD",     false);
        var bnbusd = upsert("BNBUSD", "Binance Coin (BNB/USD)", InstrumentType.CRYPTO,  "BNB-USD",     false);
        var xrpusd = upsert("XRPUSD", "Ripple (XRP/USD)",     InstrumentType.CRYPTO,    "XRP-USD",     false);
        var adausd = upsert("ADAUSD", "Cardano (ADA/USD)",    InstrumentType.CRYPTO,    "ADA-USD",     false);
        var dogusd = upsert("DOGUSD", "Dogecoin (DOGE/USD)",  InstrumentType.CRYPTO,    "DOGE-USD",    false);
        var matusd = upsert("MATUSD", "Polygon (MATIC/USD)",  InstrumentType.CRYPTO,    "MATIC-USD",   false);
        var dotusd = upsert("DOTUSD", "Polkadot (DOT/USD)",   InstrumentType.CRYPTO,    "DOT-USD",     false);
        var avxusd = upsert("AVXUSD", "Avalanche (AVAX/USD)", InstrumentType.CRYPTO,    "AVAX-USD",    false);
        var lnkusd = upsert("LNKUSD", "Chainlink (LINK/USD)", InstrumentType.CRYPTO,    "LINK-USD",    false);
        var ltcusd = upsert("LTCUSD", "Litecoin (LTC/USD)",   InstrumentType.CRYPTO,    "LTC-USD",     false);
        var uniusd = upsert("UNIUSD", "Uniswap (UNI/USD)",    InstrumentType.CRYPTO,    "UNI-USD",     false);
        var atomusd= upsert("ATOMUSD","Cosmos (ATOM/USD)",    InstrumentType.CRYPTO,    "ATOM-USD",    false);
        var xlmusd = upsert("XLMUSD", "Stellar (XLM/USD)",    InstrumentType.CRYPTO,    "XLM-USD",     false);
        var algousd= upsert("ALGOUSD","Algorand (ALGO/USD)",  InstrumentType.CRYPTO,    "ALGO-USD",    false);
        // ABD Hisseleri — Yahoo: doğrudan ticker
        var aapl   = upsert("AAPL",   "Apple Inc.",           InstrumentType.STOCK,     "AAPL",        false);
        var msft   = upsert("MSFT",   "Microsoft Corp.",      InstrumentType.STOCK,     "MSFT",        false);
        var googl  = upsert("GOOGL",  "Alphabet Inc.",        InstrumentType.STOCK,     "GOOGL",       false);
        var amzn   = upsert("AMZN",   "Amazon.com Inc.",      InstrumentType.STOCK,     "AMZN",        false);
        var nvda   = upsert("NVDA",   "NVIDIA Corp.",         InstrumentType.STOCK,     "NVDA",        false);
        var tsla   = upsert("TSLA",   "Tesla Inc.",           InstrumentType.STOCK,     "TSLA",        false);
        var meta   = upsert("META",   "Meta Platforms Inc.",  InstrumentType.STOCK,     "META",        false);
        // BIST100 Hisseleri — Yahoo: ".IS" uzantısı, gecikmeli/EOD (Tüm BIST100)
        var thyao  = upsert("THYAO",  "Türk Hava Yolları",   InstrumentType.BIST,      "THYAO.IS",    true);
        var garan  = upsert("GARAN",  "Garanti BBVA",        InstrumentType.BIST,      "GARAN.IS",    true);
        var asels  = upsert("ASELS",  "Aselsan",             InstrumentType.BIST,      "ASELS.IS",    true);
        var sise   = upsert("SISE",   "Şişe Cam",            InstrumentType.BIST,      "SISE.IS",     true);
        var kchol  = upsert("KCHOL",  "Koç Holding",         InstrumentType.BIST,      "KCHOL.IS",    true);
        var eregl  = upsert("EREGL",  "Ereğli Demir Çelik",  InstrumentType.BIST,      "EREGL.IS",    true);
        var bimas  = upsert("BIMAS",  "BİM Mağazalar",       InstrumentType.BIST,      "BIMAS.IS",    true);
        var akbnk  = upsert("AKBNK",  "Akbank",              InstrumentType.BIST,      "AKBNK.IS",    true);
        var isctr  = upsert("ISCTR",  "İş Bankası",          InstrumentType.BIST,      "ISCTR.IS",    true);
        var tuprs  = upsert("TUPRS",  "Tüpraş",              InstrumentType.BIST,      "TUPRS.IS",    true);
        var sahol  = upsert("SAHOL",  "Sabancı Holding",     InstrumentType.BIST,      "SAHOL.IS",    true);
        var petkm  = upsert("PETKM",  "Petkim",              InstrumentType.BIST,      "PETKM.IS",    true);
        var tcell  = upsert("TCELL",  "Turkcell",            InstrumentType.BIST,      "TCELL.IS",    true);
        var vakbn  = upsert("VAKBN",  "Vakıfbank",           InstrumentType.BIST,      "VAKBN.IS",    true);
        var enkai  = upsert("ENKAI",  "Enka İnşaat",         InstrumentType.BIST,      "ENKAI.IS",    true);
        var kozal  = upsert("KOZAL",  "Koza Altın",          InstrumentType.BIST,      "KOZAL.IS",    true);
        var ttkom  = upsert("TTKOM",  "Türk Telekom",        InstrumentType.BIST,      "TTKOM.IS",    true);
        var pgsus  = upsert("PGSUS",  "Pegasus",             InstrumentType.BIST,      "PGSUS.IS",    true);
        var froto  = upsert("FROTO",  "Ford Otosan",         InstrumentType.BIST,      "FROTO.IS",    true);
        var toaso  = upsert("TOASO",  "Tofaş Oto",           InstrumentType.BIST,      "TOASO.IS",    true);
        var halkb  = upsert("HALKB",  "Halkbank",            InstrumentType.BIST,      "HALKB.IS",    true);
        var arclk  = upsert("ARCLK",  "Arçelik",             InstrumentType.BIST,      "ARCLK.IS",    true);
        var kozaa  = upsert("KOZAA",  "Koza Anadolu Metal",  InstrumentType.BIST,      "KOZAA.IS",    true);
        var tavhl  = upsert("TAVHL",  "TAV Havalimanları",   InstrumentType.BIST,      "TAVHL.IS",    true);
        var soda   = upsert("SODA",   "Soda Sanayii",        InstrumentType.BIST,      "SODA.IS",     true);
        
        // BIST100 - Devam Eden Hisseler
        var ekgyo  = upsert("EKGYO",  "Emlak Konut GYO",     InstrumentType.BIST,      "EKGYO.IS",    true);
        var ykbnk  = upsert("YKBNK",  "Yapı Kredi Bankası",  InstrumentType.BIST,      "YKBNK.IS",    true);
        var prkme  = upsert("PRKME",  "Park Elektrik",       InstrumentType.BIST,      "PRKME.IS",    true);
        var sasa   = upsert("SASA",   "Sasa Polyester",      InstrumentType.BIST,      "SASA.IS",     true);
        var dohol  = upsert("DOHOL",  "Doğan Holding",       InstrumentType.BIST,      "DOHOL.IS",    true);
        var odas   = upsert("ODAS",   "Odaş Elektrik",       InstrumentType.BIST,      "ODAS.IS",     true);
        var vestl  = upsert("VESTL",  "Vestel",              InstrumentType.BIST,      "VESTL.IS",    true);
        var mgros  = upsert("MGROS",  "Migros",              InstrumentType.BIST,      "MGROS.IS",    true);
        var sokm   = upsert("SOKM",   "Şok Marketler",       InstrumentType.BIST,      "SOKM.IS",     true);
        var aefes  = upsert("AEFES",  "Anadolu Efes",        InstrumentType.BIST,      "AEFES.IS",    true);
        var ulker  = upsert("ULKER",  "Ülker Bisküvi",       InstrumentType.BIST,      "ULKER.IS",    true);
        var ccola  = upsert("CCOLA",  "Coca Cola İçecek",    InstrumentType.BIST,      "CCOLA.IS",    true);
        var otkar  = upsert("OTKAR",  "Otokar",              InstrumentType.BIST,      "OTKAR.IS",    true);
        var krdmd  = upsert("KRDMD",  "Kardemir",            InstrumentType.BIST,      "KRDMD.IS",    true);
        var alark  = upsert("ALARK",  "Alarko Holding",      InstrumentType.BIST,      "ALARK.IS",    true);
        var aygaz  = upsert("AYGAZ",  "Aygaz",               InstrumentType.BIST,      "AYGAZ.IS",    true);
        var aksen  = upsert("AKSEN",  "Aksa Enerji",         InstrumentType.BIST,      "AKSEN.IS",    true);
        var aksa   = upsert("AKSA",   "Aksa Akrilik",        InstrumentType.BIST,      "AKSA.IS",     true);
        var brsan  = upsert("BRSAN",  "Borusan Mannesmann",  InstrumentType.BIST,      "BRSAN.IS",    true);
        var cemts  = upsert("CEMTS",  "Çemtaş",              InstrumentType.BIST,      "CEMTS.IS",    true);
        var cimsa  = upsert("CIMSA",  "Çimsa",               InstrumentType.BIST,      "CIMSA.IS",    true);
        var doas   = upsert("DOAS",   "Doğuş Otomotiv",      InstrumentType.BIST,      "DOAS.IS",     true);
        var egeen  = upsert("EGEEN",  "Ege Endüstri",        InstrumentType.BIST,      "EGEEN.IS",    true);
        var enjsa  = upsert("ENJSA",  "Enerjisa Enerji",     InstrumentType.BIST,      "ENJSA.IS",    true);
        var genil  = upsert("GENIL",  "Gen İlaç",            InstrumentType.BIST,      "GENIL.IS",    true);
        var glyho  = upsert("GLYHO",  "Gübre Fabrikaları",   InstrumentType.BIST,      "GLYHO.IS",    true);
        var goody  = upsert("GOODY",  "Good-Year",           InstrumentType.BIST,      "GOODY.IS",    true);
        var gozde  = upsert("GOZDE",  "Gözde Girişim",       InstrumentType.BIST,      "GOZDE.IS",    true);
        var gubrf  = upsert("GUBRF",  "Gübre Fabrikaları",   InstrumentType.BIST,      "GUBRF.IS",    true);
        var hekts  = upsert("HEKTS",  "Hektaş",              InstrumentType.BIST,      "HEKTS.IS",    true);
        var ipeke  = upsert("IPEKE",  "İpek Doğal Enerji",   InstrumentType.BIST,      "IPEKE.IS",    true);
        var isgyo  = upsert("ISGYO",  "İş GYO",              InstrumentType.BIST,      "ISGYO.IS",    true);
        var kartn  = upsert("KARTN",  "Kartonsan",           InstrumentType.BIST,      "KARTN.IS",    true);
        var klmsn  = upsert("KLMSN",  "Klimasan",            InstrumentType.BIST,      "KLMSN.IS",    true);
        var kontr  = upsert("KONTR",  "Kontrolmatik",        InstrumentType.BIST,      "KONTR.IS",    true);
        var kords  = upsert("KORDS",  "Kordsa",              InstrumentType.BIST,      "KORDS.IS",    true);
        var logo   = upsert("LOGO",   "Logo Yazılım",        InstrumentType.BIST,      "LOGO.IS",     true);
        var mavi   = upsert("MAVI",   "Mavi Giyim",          InstrumentType.BIST,      "MAVI.IS",     true);
        var mpark  = upsert("MPARK",  "MLP Sağlık",          InstrumentType.BIST,      "MPARK.IS",    true);
        var netas  = upsert("NETAS",  "Netaş",               InstrumentType.BIST,      "NETAS.IS",    true);
        var nthol  = upsert("NTHOL",  "Net Holding",         InstrumentType.BIST,      "NTHOL.IS",    true);
        var oyakc  = upsert("OYAKC",  "Oyak Çimento",        InstrumentType.BIST,      "OYAKC.IS",    true);
        var parsn  = upsert("PARSN",  "Parsan",              InstrumentType.BIST,      "PARSN.IS",    true);
        var penta  = upsert("PENTA",  "Penta Teknoloji",     InstrumentType.BIST,      "PENTA.IS",    true);
        var petun  = upsert("PETUN",  "Pınar Et ve Un",      InstrumentType.BIST,      "PETUN.IS",    true);
        var pnsut  = upsert("PNSUT",  "Pınar Süt",           InstrumentType.BIST,      "PNSUT.IS",    true);
        var quagr  = upsert("QUAGR",  "Qua Granite",         InstrumentType.BIST,      "QUAGR.IS",    true);
        var raysg  = upsert("RAYSG",  "Ray Sigorta",         InstrumentType.BIST,      "RAYSG.IS",    true);
        var selec  = upsert("SELEC",  "Selçuk Ecza",         InstrumentType.BIST,      "SELEC.IS",    true);
        var skbnk  = upsert("SKBNK",  "Şekerbank",           InstrumentType.BIST,      "SKBNK.IS",    true);
        var smart  = upsert("SMART",  "Smart Güneş",         InstrumentType.BIST,      "SMART.IS",    true);
        var tatgd  = upsert("TATGD",  "Tat Gıda",            InstrumentType.BIST,      "TATGD.IS",    true);
        var tkfen  = upsert("TKFEN",  "Tekfen Holding",      InstrumentType.BIST,      "TKFEN.IS",    true);
        var tknsa  = upsert("TKNSA",  "Teknik Yapı",         InstrumentType.BIST,      "TKNSA.IS",    true);
        var tmsn   = upsert("TMSN",   "Tümosan Motor",       InstrumentType.BIST,      "TMSN.IS",     true);
        var trgyo  = upsert("TRGYO",  "Torunlar GYO",        InstrumentType.BIST,      "TRGYO.IS",    true);
        var trkcm  = upsert("TRKCM",  "Trakya Cam",          InstrumentType.BIST,      "TRKCM.IS",    true);
        var ttrak  = upsert("TTRAK",  "Türk Traktör",        InstrumentType.BIST,      "TTRAK.IS",    true);
        var uluse  = upsert("ULUSE",  "Ulusoy Elektrik",     InstrumentType.BIST,      "ULUSE.IS",    true);
        var yatas  = upsert("YATAS",  "Yataş",               InstrumentType.BIST,      "YATAS.IS",    true);
        var aghol  = upsert("AGHOL",  "Anadolu Grubu Holding", InstrumentType.BIST,    "AGHOL.IS",    true);
        var anacm  = upsert("ANACM",  "Anadolu Cam",         InstrumentType.BIST,      "ANACM.IS",    true);
        var ansgr  = upsert("ANSGR",  "Anadolu Sigorta",     InstrumentType.BIST,      "ANSGR.IS",    true);
        var bagfs  = upsert("BAGFS",  "Bagfaş",              InstrumentType.BIST,      "BAGFS.IS",    true);
        var banvt  = upsert("BANVT",  "Banvit",              InstrumentType.BIST,      "BANVT.IS",    true);
        var bfren  = upsert("BFREN",  "Bosch Fren",          InstrumentType.BIST,      "BFREN.IS",    true);
        var bioen  = upsert("BIOEN",  "Biotrend Enerji",     InstrumentType.BIST,      "BIOEN.IS",    true);
        var bizim  = upsert("BIZIM",  "Bizim Toptan",        InstrumentType.BIST,      "BIZIM.IS",    true);
        var brisa  = upsert("BRISA",  "Brisa",               InstrumentType.BIST,      "BRISA.IS",    true);
        var bryat  = upsert("BRYAT",  "Borusan Yatırım",     InstrumentType.BIST,      "BRYAT.IS",    true);
        var bucim  = upsert("BUCIM",  "Bursa Çimento",       InstrumentType.BIST,      "BUCIM.IS",    true);
        var clebi  = upsert("CLEBI",  "Çelebi Hava",         InstrumentType.BIST,      "CLEBI.IS",    true);
        var crfsa  = upsert("CRFSA",  "Carrefoursa",         InstrumentType.BIST,      "CRFSA.IS",    true);
        var deva   = upsert("DEVA",   "Deva Holding",        InstrumentType.BIST,      "DEVA.IS",     true);
        var dgklb  = upsert("DGKLB",  "Doğan Şirketler",     InstrumentType.BIST,      "DGKLB.IS",    true);

        // VİOP Vadeli İşlem Sözleşmeleri
        var xu030f = upsert("XU030F", "BIST 30 Vadeli",      InstrumentType.VIOP,      "XU030F",      true);
        var xu100f = upsert("XU100F", "BIST 100 Vadeli",     InstrumentType.VIOP,      "XU100F",      true);
        var usdtryf= upsert("USDTRYF","USD/TRY Vadeli",      InstrumentType.VIOP,      "USDTRYF",     false);
        var eurtryf= upsert("EURTRYF","EUR/TRY Vadeli",      InstrumentType.VIOP,      "EURTRYF",     false);
        var goldtryf=upsert("GOLDTRYF","Altın/TRY Vadeli",   InstrumentType.VIOP,      "GOLDTRYF",    false);

        // Tahvil ve Bonolar
        var tr2y   = upsert("TR2Y",   "2 Yıl Devlet Tahvili",InstrumentType.BOND,      "TR2Y",        true);
        var tr5y   = upsert("TR5Y",   "5 Yıl Devlet Tahvili",InstrumentType.BOND,      "TR5Y",        true);
        var tr10y  = upsert("TR10Y",  "10 Yıl Devlet Tahvili",InstrumentType.BOND,     "TR10Y",       true);
        var us2y   = upsert("US2Y",   "ABD 2 Yıl Tahvili",   InstrumentType.BOND,      "^IRX",        false);
        var us10y  = upsert("US10Y",  "ABD 10 Yıl Tahvili",  InstrumentType.BOND,      "^TNX",        false);

        // Yatırım Fonları (Popüler Türk Fonları)
        var ykbhis = upsert("YKBHIS", "YKB A.B.D. Hisse Senedi Fonu", InstrumentType.FUND, "YKBHIS", true);
        var isbalt = upsert("ISBALT", "İş Bankası Altın Fonu",        InstrumentType.FUND, "ISBALT", true);
        var akbtek = upsert("AKBTEK", "Akbank Teknoloji Sektör Fonu", InstrumentType.FUND, "AKBTEK", true);
        var garpar = upsert("GARPAR", "Garanti Para Piyasası Fonu",   InstrumentType.FUND, "GARPAR", true);
        var yapkre = upsert("YAPKRE", "Yapı Kredi Kira Sertifikası Fonu", InstrumentType.FUND, "YAPKRE", true);
        var istek  = upsert("ISTEK",  "İş Portföy Teknoloji Hisse Senedi Fonu", InstrumentType.FUND, "ISTEK", true);
        var akhis  = upsert("AKHIS",  "Akbank Hisse Senedi Fonu",      InstrumentType.FUND, "AKHIS", true);
        var vakeur = upsert("VAKEUR", "VakıfBank Avrupa Hisse Senedi Fonu", InstrumentType.FUND, "VAKEUR", true);
        var zrtbor = upsert("ZRTBOR", "Ziraat Portföy Borçlanma Araçları Fonu", InstrumentType.FUND, "ZRTBOR", true);
        var halalt = upsert("HALALT", "Halk Portföy Altın Fonu",       InstrumentType.FUND, "HALALT", true);


        // Fallback quote'lar — test edilmiş gerçek değerlere yakın (Yahoo refresh gelene kadar)
        // Her enstrüman için ayrı kontrol yap
        seedQuoteIfMissing(usdtry, "44.75",    "0.05",   "0.11",  now);
        seedQuoteIfMissing(eurtry, "52.70",    "0.11",   "0.21",  now);
        seedQuoteIfMissing(gbptry, "62.18",    "0.14",   "0.23",  now);
        seedQuoteIfMissing(chftry, "58.37",    "0.09",   "0.15",  now);
        seedQuoteIfMissing(jpytry, "0.289",    "0.001",  "0.35",  now);
        seedQuoteIfMissing(audtry, "32.78",    "-0.06",  "-0.18", now);
        seedQuoteIfMissing(cadtry, "33.10",    "0.04",   "0.12",  now);
        seedQuoteIfMissing(aedtry, "12.60",    "0.01",   "0.08",  now);
        seedQuoteIfMissing(xauusd, "4561.00",  "-5.00",  "-0.10", now);
        seedQuoteIfMissing(xagusd, "77.50",    "0.20",   "0.26",  now);
        seedQuoteIfMissing(wti,    "101.00",   "-0.80",  "-0.79", now);
        seedQuoteIfMissing(ngas,   "2.96",     "0.04",   "1.37",  now);
        seedQuoteIfMissing(xcuusd, "6.30",     "0.02",   "0.32",  now);
        seedQuoteIfMissing(xptusd, "1990.00",  "8.00",   "0.40",  now);
        seedQuoteIfMissing(xu100,  "14200.00", "2.00",   "0.01",  now);
        seedQuoteIfMissing(xu050,  "9850.00",  "1.50",   "0.02",  now);
        seedQuoteIfMissing(xu030,  "8920.00",  "1.20",   "0.01",  now);
        seedQuoteIfMissing(btcusd, "73900.00", "-290.00","-0.39", now);
        seedQuoteIfMissing(ethusd, "2316.00",  "-7.00",  "-0.30", now);
        seedQuoteIfMissing(solusd, "83.00",    "-0.70",  "-0.84", now);
        seedQuoteIfMissing(bnbusd, "625.00",   "5.20",   "0.84",  now);
        seedQuoteIfMissing(xrpusd, "2.45",     "0.03",   "1.24",  now);
        seedQuoteIfMissing(adausd, "1.08",     "-0.01",  "-0.92", now);
        seedQuoteIfMissing(dogusd, "0.38",     "0.01",   "2.70",  now);
        seedQuoteIfMissing(matusd, "0.52",     "-0.01",  "-1.89", now);
        seedQuoteIfMissing(dotusd, "7.85",     "0.12",   "1.55",  now);
        seedQuoteIfMissing(avxusd, "42.30",    "-0.50",  "-1.17", now);
        seedQuoteIfMissing(lnkusd, "23.45",    "0.35",   "1.51",  now);
        seedQuoteIfMissing(ltcusd, "105.20",   "-1.20",  "-1.13", now);
        seedQuoteIfMissing(uniusd, "14.75",    "0.25",   "1.72",  now);
        seedQuoteIfMissing(atomusd,"11.90",    "-0.15",  "-1.24", now);
        seedQuoteIfMissing(xlmusd, "0.42",     "0.01",   "2.43",  now);
        seedQuoteIfMissing(algousd,"0.35",     "-0.01",  "-2.78", now);
        seedQuoteIfMissing(aapl,   "258.83",   "-1.65",  "-0.63", now);
        seedQuoteIfMissing(msft,   "393.00",   "22.00",  "5.93",  now);
        seedQuoteIfMissing(googl,  "165.00",   "0.80",   "0.49",  now);
        seedQuoteIfMissing(amzn,   "185.00",   "1.10",   "0.60",  now);
        seedQuoteIfMissing(nvda,   "196.50",   "7.87",   "4.17",  now);
        seedQuoteIfMissing(tsla,   "250.00",   "-5.00",  "-1.96", now);
        seedQuoteIfMissing(meta,   "560.00",   "3.00",   "0.54",  now);
        seedQuoteIfMissing(thyao,  "322.00",   "-2.00",  "-0.62", now);
        seedQuoteIfMissing(garan,  "139.50",   "-0.50",  "-0.36", now);
        seedQuoteIfMissing(asels,  "412.00",   "0.25",   "0.06",  now);
        seedQuoteIfMissing(sise,   "52.85",    "0.45",   "0.86",  now);
        seedQuoteIfMissing(kchol,  "198.30",   "1.80",   "0.92",  now);
        seedQuoteIfMissing(eregl,  "56.40",    "-0.60",  "-1.05", now);
        seedQuoteIfMissing(bimas,  "478.95",   "2.15",   "0.45",  now);
        seedQuoteIfMissing(akbnk,  "72.35",    "-0.25",  "-0.34", now);
        seedQuoteIfMissing(isctr,  "9.47",     "0.03",   "0.32",  now);
        seedQuoteIfMissing(tuprs,  "178.40",   "-1.27",  "-0.71", now);
        seedQuoteIfMissing(sahol,  "89.60",    "0.80",   "0.90",  now);
        seedQuoteIfMissing(petkm,  "72.25",    "-0.35",  "-0.48", now);
        seedQuoteIfMissing(tcell,  "101.50",   "0.50",   "0.49",  now);
        seedQuoteIfMissing(vakbn,  "40.12",    "-0.18",  "-0.45", now);
        seedQuoteIfMissing(enkai,  "134.70",   "1.20",   "0.90",  now);
        seedQuoteIfMissing(kozal,  "672.35",   "4.85",   "0.73",  now);
        seedQuoteIfMissing(ttkom,  "271.40",   "-2.60",  "-0.95", now);
        seedQuoteIfMissing(pgsus,  "382.00",   "3.00",   "0.79",  now);
        seedQuoteIfMissing(froto,  "1426.00",  "12.00",  "0.85",  now);
        seedQuoteIfMissing(toaso,  "284.50",   "-1.50",  "-0.52", now);
        seedQuoteIfMissing(halkb,  "18.93",    "0.07",   "0.37",  now);
        seedQuoteIfMissing(arclk,  "134.20",   "-0.80",  "-0.59", now);
        seedQuoteIfMissing(kozaa,  "89.45",    "0.65",   "0.73",  now);
        seedQuoteIfMissing(tavhl,  "1892.00",  "15.00",  "0.80",  now);
        seedQuoteIfMissing(soda,   "45.78",    "-0.22",  "-0.48", now);
        
        // BIST100 - Devam Eden Hisseler
        seedQuoteIfMissing(ekgyo,  "8.45",     "0.05",   "0.60",  now);
        seedQuoteIfMissing(ykbnk,  "28.50",    "-0.15",  "-0.52", now);
        seedQuoteIfMissing(prkme,  "156.00",   "1.20",   "0.78",  now);
        seedQuoteIfMissing(sasa,   "42.30",    "-0.30",  "-0.70", now);
        seedQuoteIfMissing(dohol,  "3.85",     "0.02",   "0.52",  now);
        seedQuoteIfMissing(odas,   "89.50",    "0.70",   "0.79",  now);
        seedQuoteIfMissing(vestl,  "38.20",    "-0.20",  "-0.52", now);
        seedQuoteIfMissing(mgros,  "245.00",   "2.00",   "0.82",  now);
        seedQuoteIfMissing(sokm,   "312.50",   "3.50",   "1.13",  now);
        seedQuoteIfMissing(aefes,  "68.40",    "0.40",   "0.59",  now);
        seedQuoteIfMissing(ulker,  "124.50",   "-0.50",  "-0.40", now);
        seedQuoteIfMissing(ccola,  "189.00",   "1.50",   "0.80",  now);
        seedQuoteIfMissing(otkar,  "1245.00",  "10.00",  "0.81",  now);
        seedQuoteIfMissing(krdmd,  "12.85",    "-0.10",  "-0.77", now);
        seedQuoteIfMissing(alark,  "45.60",    "0.30",   "0.66",  now);
        seedQuoteIfMissing(aygaz,  "178.50",   "1.50",   "0.85",  now);
        seedQuoteIfMissing(aksen,  "92.30",    "0.80",   "0.87",  now);
        seedQuoteIfMissing(aksa,   "67.20",    "-0.30",  "-0.44", now);
        seedQuoteIfMissing(brsan,  "234.00",   "2.00",   "0.86",  now);
        seedQuoteIfMissing(cemts,  "156.50",   "1.20",   "0.77",  now);
        seedQuoteIfMissing(cimsa,  "89.40",    "0.60",   "0.68",  now);
        seedQuoteIfMissing(doas,   "345.00",   "3.00",   "0.88",  now);
        seedQuoteIfMissing(egeen,  "78.90",    "0.50",   "0.64",  now);
        seedQuoteIfMissing(enjsa,  "34.50",    "0.25",   "0.73",  now);
        seedQuoteIfMissing(genil,  "123.00",   "1.00",   "0.82",  now);
        seedQuoteIfMissing(glyho,  "234.50",   "2.00",   "0.86",  now);
        seedQuoteIfMissing(goody,  "145.00",   "1.20",   "0.83",  now);
        seedQuoteIfMissing(gozde,  "23.40",    "0.15",   "0.65",  now);
        seedQuoteIfMissing(gubrf,  "189.00",   "1.50",   "0.80",  now);
        seedQuoteIfMissing(hekts,  "67.80",    "0.40",   "0.59",  now);
        seedQuoteIfMissing(ipeke,  "56.30",    "0.30",   "0.54",  now);
        seedQuoteIfMissing(isgyo,  "12.45",    "0.08",   "0.65",  now);
        seedQuoteIfMissing(kartn,  "98.50",    "0.70",   "0.72",  now);
        seedQuoteIfMissing(klmsn,  "134.00",   "1.00",   "0.75",  now);
        seedQuoteIfMissing(kontr,  "78.20",    "0.50",   "0.64",  now);
        seedQuoteIfMissing(kords,  "156.00",   "1.20",   "0.78",  now);
        seedQuoteIfMissing(logo,   "289.00",   "2.50",   "0.87",  now);
        seedQuoteIfMissing(mavi,   "234.50",   "2.00",   "0.86",  now);
        seedQuoteIfMissing(mpark,  "167.00",   "1.30",   "0.78",  now);
        seedQuoteIfMissing(netas,  "89.40",    "0.60",   "0.68",  now);
        seedQuoteIfMissing(nthol,  "45.60",    "0.30",   "0.66",  now);
        seedQuoteIfMissing(oyakc,  "123.50",   "1.00",   "0.82",  now);
        seedQuoteIfMissing(parsn,  "67.80",    "0.40",   "0.59",  now);
        seedQuoteIfMissing(penta,  "345.00",   "3.00",   "0.88",  now);
        seedQuoteIfMissing(petun,  "78.90",    "0.50",   "0.64",  now);
        seedQuoteIfMissing(pnsut,  "56.30",    "0.30",   "0.54",  now);
        seedQuoteIfMissing(quagr,  "23.40",    "0.15",   "0.65",  now);
        seedQuoteIfMissing(raysg,  "12.45",    "0.08",   "0.65",  now);
        seedQuoteIfMissing(selec,  "98.50",    "0.70",   "0.72",  now);
        seedQuoteIfMissing(skbnk,  "5.67",     "0.03",   "0.53",  now);
        seedQuoteIfMissing(smart,  "134.00",   "1.00",   "0.75",  now);
        seedQuoteIfMissing(tatgd,  "78.20",    "0.50",   "0.64",  now);
        seedQuoteIfMissing(tkfen,  "156.00",   "1.20",   "0.78",  now);
        seedQuoteIfMissing(tknsa,  "89.40",    "0.60",   "0.68",  now);
        seedQuoteIfMissing(tmsn,   "234.50",   "2.00",   "0.86",  now);
        seedQuoteIfMissing(trgyo,  "12.45",    "0.08",   "0.65",  now);
        seedQuoteIfMissing(trkcm,  "98.50",    "0.70",   "0.72",  now);
        seedQuoteIfMissing(ttrak,  "456.00",   "4.00",   "0.88",  now);
        seedQuoteIfMissing(uluse,  "67.80",    "0.40",   "0.59",  now);
        seedQuoteIfMissing(yatas,  "45.60",    "0.30",   "0.66",  now);
        seedQuoteIfMissing(aghol,  "234.50",   "2.00",   "0.86",  now);
        seedQuoteIfMissing(anacm,  "89.40",    "0.60",   "0.68",  now);
        seedQuoteIfMissing(ansgr,  "12.45",    "0.08",   "0.65",  now);
        seedQuoteIfMissing(bagfs,  "67.80",    "0.40",   "0.59",  now);
        seedQuoteIfMissing(banvt,  "123.50",   "1.00",   "0.82",  now);
        seedQuoteIfMissing(bfren,  "345.00",   "3.00",   "0.88",  now);
        seedQuoteIfMissing(bioen,  "78.90",    "0.50",   "0.64",  now);
        seedQuoteIfMissing(bizim,  "156.00",   "1.20",   "0.78",  now);
        seedQuoteIfMissing(brisa,  "234.50",   "2.00",   "0.86",  now);
        seedQuoteIfMissing(bryat,  "89.40",    "0.60",   "0.68",  now);
        seedQuoteIfMissing(bucim,  "45.60",    "0.30",   "0.66",  now);
        seedQuoteIfMissing(clebi,  "167.00",   "1.30",   "0.78",  now);
        seedQuoteIfMissing(crfsa,  "98.50",    "0.70",   "0.72",  now);
        seedQuoteIfMissing(deva,   "134.00",   "1.00",   "0.75",  now);
        seedQuoteIfMissing(dgklb,  "56.30",    "0.30",   "0.54",  now);
        
        // VİOP Vadeli İşlem Sözleşmeleri
        seedQuoteIfMissing(xu030f, "11850.00", "25.00",  "0.21",  now);
        seedQuoteIfMissing(xu100f, "14350.00", "30.00",  "0.21",  now);
        seedQuoteIfMissing(usdtryf,"44.85",    "0.10",   "0.22",  now);
        seedQuoteIfMissing(eurtryf,"52.85",    "0.15",   "0.28",  now);
        seedQuoteIfMissing(goldtryf,"4850.00", "10.00",  "0.21",  now);
        
        // Tahvil ve Bonolar (Getiri oranları %) — yalnızca BenchmarkBondQuoteService
        // ilk TCMB EVDS3 refresh'ini tamamlayana kadar (startup'tan ~100s) görünür kalır.
        seedQuoteIfMissing(tr2y,   "19.80",    "-0.10",  "-0.50", now);
        seedQuoteIfMissing(tr5y,   "18.40",    "-0.05",  "-0.27", now);
        seedQuoteIfMissing(tr10y,  "17.20",    "-0.03",  "-0.17", now);
        seedQuoteIfMissing(us2y,   "4.25",     "-0.05",  "-1.16", now);
        seedQuoteIfMissing(us10y,  "4.45",     "-0.03",  "-0.67", now);
        
        // Yatırım Fonları (Birim Pay Değeri)
        seedQuoteIfMissing(ykbhis, "0.285",    "0.002",  "0.71",  now);
        seedQuoteIfMissing(isbalt, "0.198",    "0.001",  "0.51",  now);
        seedQuoteIfMissing(akbtek, "0.342",    "0.003",  "0.89",  now);
        seedQuoteIfMissing(garpar, "0.156",    "0.000",  "0.00",  now);
        seedQuoteIfMissing(yapkre, "0.189",    "0.001",  "0.53",  now);
        seedQuoteIfMissing(istek,  "0.412",    "0.004",  "0.98",  now);
        seedQuoteIfMissing(akhis,  "0.327",    "0.002",  "0.61",  now);
        seedQuoteIfMissing(vakeur, "0.271",    "-0.001", "-0.37", now);
        seedQuoteIfMissing(zrtbor, "0.135",    "0.000",  "0.07",  now);
        seedQuoteIfMissing(halalt, "0.205",    "0.001",  "0.49",  now);

        // Fallback candle'lar — gerçek değerlere yakın başlangıç noktaları
        // Her enstrüman için ayrı kontrol yap
        seedCandlesIfMissing(usdtry, "44.00");   seedCandlesIfMissing(eurtry, "52.00");
        seedCandlesIfMissing(gbptry, "61.50");   seedCandlesIfMissing(chftry, "58.00");
        seedCandlesIfMissing(jpytry, "0.286");   seedCandlesIfMissing(audtry, "32.60");
        seedCandlesIfMissing(cadtry, "32.90");   seedCandlesIfMissing(aedtry, "12.55");
        seedCandlesIfMissing(xauusd, "4500.00"); seedCandlesIfMissing(xagusd, "76.00");
        seedCandlesIfMissing(wti,    "100.00");  seedCandlesIfMissing(ngas,   "2.85");
        seedCandlesIfMissing(xcuusd, "6.20");    seedCandlesIfMissing(xptusd, "1950.00");
        seedCandlesIfMissing(xu100,  "14000.00");
        seedCandlesIfMissing(xu050,  "9800.00"); seedCandlesIfMissing(xu030,  "8900.00");
        seedCandlesIfMissing(btcusd, "70000.00");seedCandlesIfMissing(ethusd, "2200.00");
        seedCandlesIfMissing(solusd, "80.00");   seedCandlesIfMissing(bnbusd, "600.00");
        seedCandlesIfMissing(xrpusd, "2.30");    seedCandlesIfMissing(adausd, "1.05");
        seedCandlesIfMissing(dogusd, "0.35");    seedCandlesIfMissing(matusd, "0.50");
        seedCandlesIfMissing(dotusd, "7.50");    seedCandlesIfMissing(avxusd, "40.00");
        seedCandlesIfMissing(lnkusd, "22.00");   seedCandlesIfMissing(ltcusd, "100.00");
        seedCandlesIfMissing(uniusd, "14.00");   seedCandlesIfMissing(atomusd,"11.50");
        seedCandlesIfMissing(xlmusd, "0.40");    seedCandlesIfMissing(algousd,"0.33");
        seedCandlesIfMissing(aapl,   "255.00");
        seedCandlesIfMissing(msft,   "380.00");  seedCandlesIfMissing(googl,  "160.00");
        seedCandlesIfMissing(amzn,   "180.00");  seedCandlesIfMissing(nvda,   "185.00");
        seedCandlesIfMissing(tsla,   "250.00");  seedCandlesIfMissing(meta,   "550.00");
        seedCandlesIfMissing(thyao,  "315.00");  seedCandlesIfMissing(garan,  "135.00");
        seedCandlesIfMissing(asels,  "405.00");  seedCandlesIfMissing(sise,   "46.00");
        seedCandlesIfMissing(kchol,  "180.00");  seedCandlesIfMissing(eregl,  "43.00");
        seedCandlesIfMissing(bimas,  "510.00");  seedCandlesIfMissing(akbnk,  "70.00");
        seedCandlesIfMissing(isctr,  "18.00");   seedCandlesIfMissing(tuprs,  "190.00");
        seedCandlesIfMissing(sahol,  "88.00");   seedCandlesIfMissing(petkm,  "71.00");
        seedCandlesIfMissing(tcell,  "100.00");  seedCandlesIfMissing(vakbn,  "39.50");
        seedCandlesIfMissing(enkai,  "132.00");  seedCandlesIfMissing(kozal,  "660.00");
        seedCandlesIfMissing(ttkom,  "268.00");  seedCandlesIfMissing(pgsus,  "375.00");
        seedCandlesIfMissing(froto,  "1400.00"); seedCandlesIfMissing(toaso,  "280.00");
        seedCandlesIfMissing(halkb,  "18.50");   seedCandlesIfMissing(arclk,  "132.00");
        seedCandlesIfMissing(kozaa,  "88.00");   seedCandlesIfMissing(tavhl,  "1850.00");
        seedCandlesIfMissing(soda,   "45.00");
        
        // BIST100 - Devam Eden Hisseler
        seedCandlesIfMissing(ekgyo,  "8.30");    seedCandlesIfMissing(ykbnk,  "28.00");
        seedCandlesIfMissing(prkme,  "152.00");  seedCandlesIfMissing(sasa,   "41.50");
        seedCandlesIfMissing(dohol,  "3.80");    seedCandlesIfMissing(odas,   "88.00");
        seedCandlesIfMissing(vestl,  "37.50");   seedCandlesIfMissing(mgros,  "240.00");
        seedCandlesIfMissing(sokm,   "305.00");  seedCandlesIfMissing(aefes,  "67.00");
        seedCandlesIfMissing(ulker,  "122.00");  seedCandlesIfMissing(ccola,  "185.00");
        seedCandlesIfMissing(otkar,  "1220.00"); seedCandlesIfMissing(krdmd,  "12.50");
        seedCandlesIfMissing(alark,  "44.50");   seedCandlesIfMissing(aygaz,  "175.00");
        seedCandlesIfMissing(aksen,  "90.00");   seedCandlesIfMissing(aksa,   "66.00");
        seedCandlesIfMissing(brsan,  "230.00");  seedCandlesIfMissing(cemts,  "153.00");
        seedCandlesIfMissing(cimsa,  "87.50");   seedCandlesIfMissing(doas,   "338.00");
        seedCandlesIfMissing(egeen,  "77.00");   seedCandlesIfMissing(enjsa,  "33.80");
        seedCandlesIfMissing(genil,  "120.00");  seedCandlesIfMissing(glyho,  "230.00");
        seedCandlesIfMissing(goody,  "142.00");  seedCandlesIfMissing(gozde,  "22.90");
        seedCandlesIfMissing(gubrf,  "185.00");  seedCandlesIfMissing(hekts,  "66.50");
        seedCandlesIfMissing(ipeke,  "55.00");   seedCandlesIfMissing(isgyo,  "12.20");
        seedCandlesIfMissing(kartn,  "96.50");   seedCandlesIfMissing(klmsn,  "131.00");
        seedCandlesIfMissing(kontr,  "76.50");   seedCandlesIfMissing(kords,  "153.00");
        seedCandlesIfMissing(logo,   "283.00");  seedCandlesIfMissing(mavi,   "230.00");
        seedCandlesIfMissing(mpark,  "163.00");  seedCandlesIfMissing(netas,  "87.50");
        seedCandlesIfMissing(nthol,  "44.50");   seedCandlesIfMissing(oyakc,  "121.00");
        seedCandlesIfMissing(parsn,  "66.50");   seedCandlesIfMissing(penta,  "338.00");
        seedCandlesIfMissing(petun,  "77.00");   seedCandlesIfMissing(pnsut,  "55.00");
        seedCandlesIfMissing(quagr,  "22.90");   seedCandlesIfMissing(raysg,  "12.20");
        seedCandlesIfMissing(selec,  "96.50");   seedCandlesIfMissing(skbnk,  "5.55");
        seedCandlesIfMissing(smart,  "131.00");  seedCandlesIfMissing(tatgd,  "76.50");
        seedCandlesIfMissing(tkfen,  "153.00");  seedCandlesIfMissing(tknsa,  "87.50");
        seedCandlesIfMissing(tmsn,   "230.00");  seedCandlesIfMissing(trgyo,  "12.20");
        seedCandlesIfMissing(ttrak,  "447.00");
        seedCandlesIfMissing(uluse,  "66.50");   seedCandlesIfMissing(yatas,  "44.50");
        seedCandlesIfMissing(aghol,  "230.00");  seedCandlesIfMissing(anacm,  "87.50");
        seedCandlesIfMissing(ansgr,  "12.20");   seedCandlesIfMissing(bagfs,  "66.50");
        seedCandlesIfMissing(banvt,  "121.00");  seedCandlesIfMissing(bfren,  "338.00");
        seedCandlesIfMissing(bioen,  "77.00");   seedCandlesIfMissing(bizim,  "153.00");
        seedCandlesIfMissing(brisa,  "230.00");  seedCandlesIfMissing(bryat,  "87.50");
        seedCandlesIfMissing(bucim,  "44.50");   seedCandlesIfMissing(clebi,  "163.00");
        seedCandlesIfMissing(crfsa,  "96.50");   seedCandlesIfMissing(deva,   "131.00");
        seedCandlesIfMissing(dgklb,  "55.00");
        
        // VİOP Vadeli İşlem Sözleşmeleri
        seedCandlesIfMissing(xu030f, "11800.00"); seedCandlesIfMissing(xu100f, "14300.00");
        seedCandlesIfMissing(usdtryf,"44.70");    seedCandlesIfMissing(eurtryf,"52.60");
        seedCandlesIfMissing(goldtryf,"4800.00");
        
        // Tahvil ve Bonolar
        seedCandlesIfMissing(tr2y,   "19.90");    seedCandlesIfMissing(tr5y,   "18.45");
        seedCandlesIfMissing(tr10y,  "17.23");    seedCandlesIfMissing(us2y,   "4.30");
        seedCandlesIfMissing(us10y,  "4.50");
        
        // Yatırım Fonları
        seedCandlesIfMissing(ykbhis, "0.280");    seedCandlesIfMissing(isbalt, "0.195");
        seedCandlesIfMissing(akbtek, "0.335");    seedCandlesIfMissing(garpar, "0.156");
        seedCandlesIfMissing(yapkre, "0.186");
        seedCandlesIfMissing(istek,  "0.405");    seedCandlesIfMissing(akhis,  "0.322");
        seedCandlesIfMissing(vakeur, "0.273");    seedCandlesIfMissing(zrtbor, "0.135");
        seedCandlesIfMissing(halalt, "0.203");
    }

    private MarketInstrument upsert(String symbol, String name, InstrumentType type,
                                    String providerSymbol, boolean delayed) {
        return instrumentRepo.findBySymbol(symbol).orElseGet(() -> {
            // Use centralized symbol normalization instead of hardcoded providerSymbol
            String normalizedSymbol = instrumentService.normalizeSymbolForYahoo(symbol, type);
            log.info("Creating instrument: symbol={} type={} yahooSymbol={} (provided: {})", 
                    symbol, type, normalizedSymbol, providerSymbol);
            
            return instrumentRepo.save(new MarketInstrument(
                    symbol, name, type,
                    MarketDataProvider.YAHOO, normalizedSymbol, delayed));
        });
    }

    private void seedQuote(MarketInstrument inst, String last, String changeAbs,
                           String changePct, Instant now) {
        quoteRepo.save(new MarketQuote(inst, bd(last), bd(changeAbs), bd(changePct), now));
    }

    /**
     * Sadece quote yoksa seed et (mevcut quote'ları korur)
     */
    private void seedQuoteIfMissing(MarketInstrument inst, String last, String changeAbs,
                                    String changePct, Instant now) {
        // Bu enstrüman için quote var mı kontrol et
        boolean hasQuote = quoteRepo.findTop1ByInstrumentOrderByAsOfDesc(inst).isPresent();
        if (!hasQuote) {
            log.info("Seeding missing quote for: {}", inst.getSymbol());
            seedQuote(inst, last, changeAbs, changePct, now);
        }
    }

    private void seedCandles(MarketInstrument inst, String base) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(30);
        BigDecimal basePrice = bd(base);

        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            double variation = (RANDOM.nextDouble() - 0.5) * 0.04;
            BigDecimal close = basePrice.multiply(BigDecimal.valueOf(1 + variation));
            candleRepo.save(new MarketCandle(inst, d, close));
        }
    }

    /**
     * Sadece candle yoksa seed et (mevcut candle'ları korur)
     */
    private void seedCandlesIfMissing(MarketInstrument inst, String base) {
        // Bu enstrüman için candle var mı kontrol et
        long candleCount = candleRepo.countByInstrument(inst);
        if (candleCount == 0) {
            log.info("Seeding missing candles for: {}", inst.getSymbol());
            seedCandles(inst, base);
        }
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}

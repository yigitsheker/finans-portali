# Finans Portalı — Implementation Guide (Neyi Nasıl Yaptık)

**Sürüm:** 1.0
**Tarih:** 2026-05-30
**Amaç:** Bu projeyi sıfırdan teslim alacak bir geliştirici için **her şeyin nerede ve nasıl yapıldığını** tek dökümanda anlatmak.

---

## İçindekiler

1. [Genel Yapı](#1-genel-yapı)
2. [Frontend Sayfa Sayfa](#2-frontend-sayfa-sayfa)
3. [Backend Servisleri](#3-backend-servisleri)
4. [Veri Kaynakları ve Çekme Mantığı](#4-veri-kaynakları-ve-çekme-mantığı)
5. [Kullanıcı Yönetimi (Keycloak)](#5-kullanıcı-yönetimi-keycloak)
6. [Authentication ve Authorization](#6-authentication-ve-authorization)
7. [Çok Dillilik ve Otomatik Çeviri](#7-çok-dillilik-ve-otomatik-çeviri)
8. [Log Pipeline (Log4j2 → Kafka → OpenSearch)](#8-log-pipeline)
9. [Observability (Prometheus + Grafana + Jaeger)](#9-observability)
10. [Cache Mekanizması](#10-cache-mekanizması)
11. [Cron Schedules ve Veri Yenileme](#11-cron-schedules)
12. [AI Chatbot Implementation](#12-ai-chatbot-implementation)
13. [E-posta ve Bildirim](#13-e-posta-ve-bildirim)
14. [Deployment](#14-deployment)
15. [Önemli Bug'lar ve Düzeltmeleri](#15-önemli-buglar-ve-düzeltmeleri)

---

## 1. Genel Yapı

```
finans-portali/
├── backend/                 # Spring Boot 3.5, Java 21
│   ├── src/main/java/com/finansportali/backend/
│   │   ├── BackendApplication.java         # Main class
│   │   ├── controller/                     # 17 REST controller
│   │   ├── service/                        # 49 service sınıfı
│   │   │   ├── client/                     # Dış servis fetcher'ları
│   │   │   │   ├── bond/                   # TCMB, İş Yatırım, Investing
│   │   │   │   ├── fund/                   # TEFAS
│   │   │   │   ├── inflation/              # TCMB EVDS3, FRED
│   │   │   │   ├── market/                 # Yahoo, Finnhub, TwelveData
│   │   │   │   ├── news/                   # LibreTranslate, content fetcher
│   │   │   │   └── viop/                   # İş Yatırım
│   │   │   ├── analysis/                   # Analiz sayfası 4 servis
│   │   │   ├── market/                     # Market data services
│   │   │   ├── portfolio/                  # Portföy hesaplama
│   │   │   └── scheduler/                  # @Scheduled wrappers
│   │   ├── repository/                     # 20 JPA repository
│   │   ├── entity/                         # 24 JPA entity
│   │   ├── dto/                            # Request/response DTO'lar
│   │   ├── config/                         # 8 @Configuration
│   │   ├── filter/                         # Logging filter (MDC)
│   │   ├── exception/                      # Global exception handler
│   │   ├── mapper/                         # Manual mapper'lar (no Mapstruct)
│   │   └── util/                           # Helper'lar
│   ├── src/main/resources/
│   │   ├── application.yml                 # Spring config
│   │   ├── log4j2-spring.xml              # Log4j2 config
│   │   ├── log4j2/kafka-log-event.json    # JSON layout template
│   │   └── db/migration/V1..V22__*.sql    # Flyway migrations
│   └── pom.xml
├── frontend/                # React 19 + Vite 7
│   ├── src/
│   │   ├── App.jsx                         # Router + RequireAuth
│   │   ├── main.jsx                        # Entry point + Keycloak init
│   │   ├── pages/                          # 16 sayfa
│   │   ├── components/
│   │   │   ├── features/                   # Domain-specific bileşenler
│   │   │   │   ├── analysis/               # InstrumentsTable, Chatbot, DetailCard
│   │   │   │   ├── portfolio/              # SummaryCards, PositionsTable
│   │   │   │   └── news/                   # NewsCard
│   │   │   ├── common/                     # ScrollToTop, TermInfo, DataFreshnessHeader
│   │   │   ├── InstrumentChartModal.jsx   # Ortak grafik modal
│   │   │   ├── FinexStyleMarket.jsx       # Stocks/Crypto/Commodities sarmalayıcı
│   │   │   ├── Topbar.jsx, Sidebar.jsx    # Navigation
│   │   │   └── Layout.jsx                  # Page shell
│   │   ├── contexts/                       # I18nContext, ThemeContext, etc.
│   │   ├── api/                            # Service layer (analysisApi, newsApi, etc.)
│   │   ├── data/glossary.js               # 36 finans terimi (TR + EN)
│   │   └── utils/
│   ├── public/keycloak.json               # Keycloak adapter config
│   ├── Dockerfile                          # Multi-stage build → nginx
│   ├── nginx.conf                          # Reverse proxy + history fallback
│   └── package.json
├── log-consumer/            # Spring Boot Kafka consumer → OpenSearch
├── keycloak/                # Realm import JSON + bootstrap script kaynağı
├── scripts/
│   └── keycloak-bootstrap.sh # Çalışan Keycloak'a runtime patch
├── monitoring/
│   └── prometheus.yml       # Scrape configs
├── grafana/
│   └── provisioning/
│       ├── datasources/datasources.yml
│       └── dashboards/      # 4 JSON dashboard
├── fluent-bit/              # Eski log shipper (artık kullanılmıyor)
├── docker-compose.yml       # 18 service tek dosyada
├── k8s/                     # Kustomize manifests
│   ├── base/
│   ├── overlays/dev/        # kind cluster için
│   └── overlays/prod/       # cloud cluster için
└── docs/                    # Bu dokümanlar
```

## 2. Frontend Sayfa Sayfa

### 2.1 Anasayfa — `pages/Home.jsx`

**Verileri nereden çeker:**
- `getMarketSummary()` → `GET /api/v1/market/summary` — anasayfada hero + tablo + sidecards için
- `getNews()` → `GET /api/v1/news` — son 4 haber

**Bileşenler:**
- Hero başlık + 2 CTA buton (`Piyasaları Keşfet` ve `Portföyümü Görüntüle` — login durumuna göre değişir)
- "Günün Hareketlileri" — `summary` listesinden `Math.abs(changePct)` ile sıralanmış ilk 5
- BIST 100 tablosu — `summary.filter(i => i.type === "BIST")` ile süzülmüş, max 8 satır, Tümü/Yükselenler/Düşenler/Hacim filtreleri
- 4 sidecard: Döviz / Kripto / Emtia / TCMB durumları
- 6 hızlı erişim kartı: Hisse / Kripto / Fonlar / Tahvil / Döviz / Haberler

**Auth:** Public — login gerekmiyor.

### 2.2 Hisseler — `pages/Stocks.jsx` (wrapper) + `components/FinexStyleMarket.jsx`

`Stocks.jsx` sadece 2 sekmeli tab navigation yapar (Piyasa / Takip Listem), her ikisi de `FinexStyleMarket` bileşenini render eder.

`FinexStyleMarket`:
- Props: `filterType="BIST"` veya `STOCK` veya `CRYPTO` veya `COMMODITY`
- Self-contained: kendi API çağrısı, kendi state'i, kendi modalları
- API: `GET /api/v1/market/summary` → useState'e atılır
- Filtreleme client-side
- Tablo satırına tıklama → `setSelected(item)` → `InstrumentChartModal` açılır

**Tablo state:**
- `search` — sembol/isim
- `categoryFilters` — multi-select chip'ler
- `sortField` + `sortDir` — kolon sort
- `page` + `pageSize` — sayfalama

### 2.3 Kripto, Emtia — Stocks'un ikiz wrapper'ları

- `pages/Crypto.jsx` → `<FinexStyleMarket filterType="CRYPTO" />`
- `pages/Commodities.jsx` → `<FinexStyleMarket filterType="COMMODITY" />`

### 2.4 Fonlar — `pages/Funds.jsx`

**Veri:** `getInvestmentFunds()` + `getFundTypes()` (`portfolioApi.js`)

**Akış:**
1. Mount'ta `loadData()` → fonlar + tipler çekilir
2. `selectedFundTypes` filtresi + `sortKey/sortDir` sort
3. Pagination 25/sayfa default

**Refresh butonu** (admin görür): `POST /api/v1/investment-funds/admin/refresh` → manuel TEFAS pull

### 2.5 Tahvil ve Bono — `pages/Bonds.jsx`

**Veri:**
- `GET /api/v1/bonds` → tahvil/bono listesi
- `GET /api/v1/bonds/summary` → 3 özet kart
- `getDepositRates()` → `DepositRatesCard` widget

**Filtre:** Tip (Devlet/Bono/Eurobond/Sukuk/Corp/Other) + Para birimi (TRY/USD/EUR/Tümü)

### 2.6 Döviz — `pages/MarketData.jsx`

**Veri:** `getExchangeRates()` → `GET /api/v1/exchange-rates`

Üstte `CurrencyConverter` widget (anlık dönüştürücü), altta sortable tablo.

### 2.7 VIOP — `pages/Viop.jsx`

**Veri:** `getViopContracts()` → `GET /api/v1/viop`

CheckboxFilterGroup ile asset class filtreleme, custom `ThSort` ile sortable kolonlar.

### 2.8 Enflasyon — `pages/Inflation.jsx`

**Veri:** `getInflationHistory(country)` → `GET /api/v1/inflation?country=TR` veya `US`

**Toggle yapısı:**
- Country: 🇹🇷 TR / 🇺🇸 US
- View: Aylık / Yıllık

Yıllık view'da custom aggregation:
```js
// Her yıl için son ayın YoY değerini al
const yearlyBars = (() => {
  const byYear = new Map();
  for (const r of rows) {
    if (r.cpiYearlyChange == null) continue;
    const year = r.periodDate.substring(0, 4);
    if (!byYear.get(year) || byYear.get(year).periodDate < r.periodDate) {
      byYear.set(year, r);
    }
  }
  return [...byYear.values()].sort(...);
})();
```

Custom `BarChart` bileşeni: yıllık modda her bar'ın üstüne `+%X.XX` yazar.

### 2.9 Haberler — `pages/News.jsx` + `pages/NewsDetail.jsx`

**Veri:**
- `getNewsCategories()` → kategori listesi
- `getNewsCategoryCounts()` → kategori başına haber sayısı
- `getNews(category, lang)` → haber listesi
- `getNewsById(id, lang)` → detay

**Dil parametresi:**
- Frontend `useI18n().lang` → API call'a parametre geçer
- Backend `NewsService.latest(category, lang)` cache'lenmiş çeviriyi swap eder

**Sayfa yapısı:**
- Featured (hero) + grid (4 sütun, 12 haber/sayfa)
- Sidebar: Kategoriler + Yatırımcı CTA + Tooltip
- Pagination

`NewsDetail.jsx`:
- Tam metin + kaynak link
- Reading time hesaplaması
- Sayfa-başı scroll

### 2.10 Analiz — `pages/Analysis.jsx`

**Auth:** RequireAuth wrapper.

**Veri:**
- `getAnalysisInstruments(keycloak)` → tüm enstrümanlar (stok + kripto + FX + emtia + fon + TCMB FX)
- Satır tıklanınca: `getAnalysisDetail(keycloak, symbol)` → detay kartı
- Chatbot: `sendAnalysisChat(keycloak, message, lang)`

**Layout:**
- Desktop: 2 kolonlu CSS grid (`minmax(0,1fr) minmax(0,360px)`)
- Mobile: tek kolon, chatbot tabloya alta düşer (`@media (max-width: 960px)`)
- Chatbot sticky `top: 12px` + `maxHeight: calc(100vh - 120px)`

**4 bileşen:**
- `InstrumentsTable.jsx` — context strip (TR + ABD enflasyon) + filtre chip'leri + "Enflasyonu yenenler" toggle + sortable tablo + pagination
- `InstrumentDetailCard.jsx` — Trend / Volatilite / Risk / Sinyal metric'leri + narrative notlar + "📈 Grafiği Göster" butonu
- `Chatbot.jsx` — mesaj balonları + scenario kartları + disclaimer + quick questions + composer (textarea + send)
- `InstrumentChartModal` — Stocks'taki ile aynı modal (paylaşılan kompanent)

### 2.11 Portföyüm — `pages/Portfolio.jsx`

**Auth:** RequireAuth.

**Veri:** `usePortfolioPage` hook → tüm logic burada toplanır
- `getPositions(keycloak)` → pozisyonlar
- Her pozisyon için latest fiyat (market summary'den)
- Stats hesaplama: total invested, current value, gain, gain%

**Bileşenler:**
- `SummaryCards` — 4 stat
- `PortfolioCharts` — donut allocation
- `PositionsTable` — Sortable, Sell butonu her satırda
- `AddPositionModal` — yeni pozisyon ekleme

### 2.12 Geçmişten — `pages/HistoricalComparison.jsx`

**Auth:** RequireAuth.

**Veri:** Backend'e gitmez — **localStorage** tabanlı
```js
useEffect(() => {
  const saved = localStorage.getItem("historicalPositions");
  if (saved) setPositions(JSON.parse(saved));
  setLoaded(true);
}, []);

useEffect(() => {
  if (!loaded) return;
  localStorage.setItem("historicalPositions", JSON.stringify(positions));
}, [positions, loaded]);
```

`loaded` flag race condition'ı önler — initial mount'ta boş array localStorage'u ezmesin diye.

### 2.13 Ayarlar — `pages/Settings.jsx`

**Auth:** RequireAuth.

Sekmeler:
- Profil — Keycloak `/users/me` endpoint'ine yazar
- Bildirimler — `userPreferencesApi` ile DB'ye
- Tema/Dil — Context state + localStorage

### 2.14 Admin — `pages/Admin.jsx`

**Auth:** RequireAuth + isAdmin guard (frontend) + `hasRole("ADMIN")` (backend `SecurityConfig`)

Manuel refresh butonları, kullanıcı yönetimi, cache temizleme.

## 3. Backend Servisleri

### 3.1 Controller → Service → Repository Katmanı

**Örnek: MarketController**
```java
@RestController
@RequestMapping("/api/v1/market")
public class MarketController {
    private final MarketService marketService;

    @GetMapping("/summary")
    public List<MarketSummaryItem> summary() {
        return marketService.summary();
    }
}
```

**Service** delegasyon yapar, business logic'i alt servislere böler:
```java
@Service
public class MarketService {
    public List<MarketSummaryItem> summary() {
        seedIfEmpty();
        return priceService.getMarketSummary();  // → MarketPriceService
    }
}
```

**Repository** Spring Data JPA derived query'leri:
```java
public interface MarketQuoteRepository extends JpaRepository<MarketQuote, Long> {
    Optional<MarketQuote> findFirstByInstrumentOrderByAsOfDesc(MarketInstrument inst);
}
```

### 3.2 Önemli Service Kümeleri

#### Market Domain
- `MarketService` — public API
- `market/MarketPriceService` — DB'den summary üretir
- `market/MarketHistoryService` — Yahoo'dan chart history
- `market/MarketInstrumentService` — CRUD
- `market/MarketDataSeedService` — initial seed (BIST 100 sembol listesi)

#### Yahoo Fetcher
- `client/market/YahooPriceFetcher` — `query1.finance.yahoo.com/v8/finance/chart/{symbol}`
  - `fetchQuote(symbol)` → `range=1d, interval=1d` → meta'dan regularMarketPrice + chartPreviousClose
  - `fetchHistory(symbol, range, interval)` → historical candles
- WebClient, 10s timeout, fail-silently fallback

#### Inflation
- `InflationService` — getLatest / getAllAscending, scheduled refresh
- `client/inflation/TcmbInflationFetcher` — EVDS3 API (`evds3.tcmb.gov.tr/igmevdsms-dis/`)
- `client/inflation/FredInflationFetcher` — FRED API (`api.stlouisfed.org/fred/series/observations`)

#### Funds
- `InvestmentFundService` — TEFAS data wrapper
- `client/fund/TefasFundFetcher` — `tefas.gov.tr` JSON endpoint, batch fetch, retry

#### Bonds
- `DebtInstrumentService` — public API
- `service/BondDataRefreshService` — orchestrator
- `client/bond/TcmbBondDataProvider` — TCMB politika faizi + DİBS scrape
- `client/bond/EvdsBondYieldFetcher` — EVDS3 yield data
- `client/bond/InvestingYieldCurveFetcher` — yield curve (manuel)
- `client/bond/DemoBondDataProvider` — fallback (test data)

#### News
- `NewsService` — fetch + storage + translation orchestrator
- `client/news/NewsContentFetcher` — Jsoup ile RSS item URL'sinin tam içeriği çekilir
- `client/news/LibreTranslateClient` — `/detect` + `/translate` POST

#### VIOP
- `ViopService` — DB facade
- `client/viop/IsYatirimViopFetcher` — İş Yatırım vadeli işlem sayfası HTML scrape

#### Analiz (Analysis)
- `analysis/InstrumentAnalysisService` — cross-asset aggregator
- `analysis/RiskProfileService` — category + volatilite → LOW/MEDIUM/HIGH
- `analysis/TechnicalAnalysisService` — shortTermSignal / longTermSignal / trend / volatility
- `analysis/AiAnalysisService` — intent matcher + LLM dispatcher
- `analysis/LlmClient` — OpenAI-uyumlu generic HTTP client

#### Portföy
- `portfolio/PortfolioCalculationService` — toplam, gain, allocation
- `portfolio/PortfolioPositionService` — CRUD
- `portfolio/PortfolioPerformanceService` — geçmiş performans
- `portfolio/PortfolioCurrencyService` — USD pozisyonları TRY'ye çevirme

#### Alerts
- `PriceAlertService` — alarm tetikleyici (her piyasa refresh'inde scan)
- `NotificationService` — in-app inbox

#### Email
- `NotificationService` + Spring Mail → Gmail SMTP

### 3.3 Scheduled Tasks

`@Scheduled` ile cron veya fixedDelay:

| Method | Çalışma | Açıklama |
|--------|---------|----------|
| `PriceRefreshScheduler.refreshOnStartup` | initialDelay 60s, fixedDelay Long.MAX | Bir kez startup'ta tüm 145 enstrüman için fiyat + 1y history |
| `PriceRefreshScheduler.refreshDaily` | cron 0 0 18 * * * | Günlük 18:00 UTC fiyat refresh |
| `NewsService.fetchAndSaveNews` | initialDelay 5s, fixedDelay 6h | Tüm RSS feed'leri pull |
| `ExchangeRateService.fetchTcmbRates` | initialDelay 10s, fixedDelay 4h | TCMB döviz kurları |
| `InflationService.scheduledRefresh` | cron 0 0 9 * * * | Günlük 09:00 TÜFE + CPI |
| `DepositRateService.fetchRates` | cron 0 5 9 * * * | Günlük 09:05 mevduat faizleri |
| `BondDataRefreshScheduler.refreshBonds` | cron 0 0 0/2 * * ? | 2 saatte bir tahvil |
| `InvestmentFundRefreshScheduler.refreshFunds` | cron 0 30 10,14,18 * * MON-FRI | Hafta içi 3 kez fon |
| `ViopService.refreshViop` | initialDelay 30s, fixedDelay 15m | 15 dakikada bir VIOP |

**Pool size:** `application.yml` → `spring.task.scheduling.pool.size: 4` (default 1 idi — news fetch tüm thread'i bloke ediyordu)

## 4. Veri Kaynakları ve Çekme Mantığı

### 4.1 Yahoo Finance (Hisse / Kripto / FX / Emtia)

**Endpoint:** `query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1d&interval=1d`

**Sembol mapping:**
- BIST → `THYAO.IS`, `GARAN.IS`, ...
- US stocks → `AAPL`, `MSFT`, ...
- Kripto → `BTC-USD`, `ETH-USD`
- Emtia → `GC=F` (altın), `CL=F` (petrol), `NG=F` (doğalgaz)
- FX → `USDTRY=X`, `EURTRY=X`

**Önemli detay:** `range=1d` kullanmak şart. `range=2d` Yahoo'nun `chartPreviousClose` field'ını "bugünün açılışı" olarak döner, change=0 çıkar. Bu BIST hisselerinde "değişim %0" bug'ına yol açmıştı.

### 4.2 TCMB EVDS3 (Enflasyon, Faiz, DİBS)

**Base URL:** `evds3.tcmb.gov.tr/igmevdsms-dis/`

**Series:**
- TÜFE: `TP.TUFE1YI`
- ÜFE: `TP.UFE1YI`
- Politika Faizi: `TP.APIFON1`

**Auth:** API key veya session cookies (JSESSIONID + TS cookie)

**Fetcher pattern:**
```java
JsonNode response = webClient.get()
    .uri(uri -> uri.queryParam("seriesPath", series).queryParam("type", "json"))
    .header("Cookie", cookies)
    .retrieve()
    .bodyToMono(JsonNode.class)
    .block();
```

### 4.3 TCMB Today.xml (Döviz)

**URL:** `https://www.tcmb.gov.tr/kurlar/today.xml`

**Parse:** Regex
```java
Pattern CURRENCY_PATTERN = Pattern.compile(
    "<Currency[^>]*CrossOrder=\"(\\d+)\"[^>]*CurrencyCode=\"([^\"]+)\"[^>]*>(.*?)</Currency>",
    Pattern.DOTALL
);
```

Her `<Currency>` bloğundan BanknoteBuying/Selling/ForexBuying/ForexSelling extract edilir.

### 4.4 TEFAS (Yatırım Fonları)

**URL:** `tefas.gov.tr` JSON API

**Strateji:** Batch fetch + retry. `max-funds-to-fetch=1500`, `rate-limit-delay-ms=200`, `retry-max-attempts=3`.

### 4.5 FRED (ABD CPI)

**Endpoint:** `api.stlouisfed.org/fred/series/observations?series_id=CPIAUCSL&api_key=...`

10 yıllık aylık veri çekilir, yıllık YoY hesaplanır in-memory.

### 4.6 RSS Feed'ler (Haber)

31 farklı kaynak, hepsi RSS XML. Her feed için:
1. WebClient ile GET
2. `<item>` blokları regex ile parse
3. Her item'ın `<link>` URL'sinden tam içerik fetch (`NewsContentFetcher` → Jsoup)
4. Jsoup başarısız olursa → curl fallback (sistem komutu)
5. Hala başarısız → Playwright service (headless Chromium)

**Dil tahmini:** Feed URL host'undan çıkarılır
```java
private static final Set<String> EN_FEED_HOST_HINTS = Set.of(
    "cointelegraph.com", "coindesk.com",
    "feeds.finance.yahoo.com", "www.investing.com"
);
```

### 4.7 İş Yatırım (Tahvil + VIOP)

HTML scrape — JSoup ile DOM parse. CSS selector'ler ile tablo satırları extract edilir.

## 5. Kullanıcı Yönetimi (Keycloak)

### 5.1 Realm Setup

**Realm dosyası:** `keycloak/finans-realm.json`

**Önemli ayarlar:**
- `realm: "finans"`
- `accessTokenLifespan: 300` (5 dk)
- `ssoSessionMaxLifespanRememberMe: 2592000` (30 gün)
- `registrationAllowed: true`
- `loginWithEmailAllowed: true`
- `resetPasswordAllowed: true`
- `otpPolicyType: "totp"`, `otpPolicyDigits: 6`, `otpPolicyPeriod: 30`

**Realm import:**
- Keycloak container `KEYCLOAK_IMPORT=/opt/keycloak/data/import/finans-realm.json` ile başlar
- ConfigMap olarak mount edilir (k8s'de)
- Sadece ilk başlangıçta import edilir; sonraki başlangıçlar zaten var olan realm'ı kullanır

### 5.2 Bootstrap Patcher

`scripts/keycloak-bootstrap.sh` her docker-compose up'ta çalışır:

1. Master realm'a admin/admin ile kcadm.sh login
2. Realm settings patch (`rememberMe`, `ssoSessionMaxLifespanRememberMe`, vs.)
3. **2FA enforce:**
   ```bash
   kcadm.sh update authentication/required-actions/CONFIGURE_TOTP -r finans \
     -s enabled=true -s defaultAction=true
   ```
4. `finans-backend-admin` client'ı ensure et (yoksa oluştur, varsa secret eşle)
5. `realm-management` role'lerini service account'a map et (manage-users, view-users, query-users, manage-realm)
6. LDAP user federation varsa "edit mode" attribute mapper'larını fix et

**İdempotent** — her başlangıçta tekrar çalışsa da state'i değiştirmez.

### 5.3 Kullanıcı Akışları

**Kayıt:**
1. Frontend `keycloak.login({redirectUri: ...})` çağırır
2. Keycloak login form → kullanıcı "Register" link tıklar
3. Username + email + password girer
4. Email verification (eğer aktif)
5. İlk login'de `CONFIGURE_TOTP` required action tetiklenir → Authenticator app QR + 6 haneli kod
6. Tamamlandı → redirect URI'ye dönüş + Authorization Code

**Login:**
1. Authorization Code → token exchange
2. JWT access_token frontend'e döner
3. Her API call'da `Authorization: Bearer {token}` header

**Logout:**
1. Frontend `keycloak.logout({redirectUri: ...})`
2. Keycloak session sonlandırılır
3. Refresh token revoke

**Şifre sıfırlama:**
1. Login form'da "Forgot password" link
2. Email girilir → reset email gönderilir (Keycloak SMTP üzerinden)
3. Link tıklanır → yeni şifre belirlenir

### 5.4 Kullanıcı Banlama

**Admin paneli üzerinden** veya **Keycloak admin console** (`http://localhost:8090/admin`):

**Disable:**
- Keycloak admin console → Users → kullanıcı seç → "Enabled" toggle off
- Veya REST: `PUT /admin/realms/finans/users/{id}` body: `{"enabled": false}`
- Mevcut access_token süre dolana kadar geçerli kalır (~5 dk)

**Force logout:**
- Admin console → Users → "Sessions" tab → "Logout"
- Veya REST: `POST /admin/realms/finans/users/{id}/logout`

**Delete:**
- Admin console → Users → "Delete"
- Veya REST: `DELETE /admin/realms/finans/users/{id}`
- Portfolio_positions, watchlists, price_alerts gibi backend tabloları yetim kalır (FK constraint yok) — admin manuel temizleyebilir

### 5.5 Rol Yönetimi

- **`user`** role: realm default role, register olan herkes alır
- **`admin`** role: manuel atama gerektirir (admin console → kullanıcı → Role Mappings)

JWT içindeki claim:
```json
{
  "realm_access": {
    "roles": ["user", "default-roles-finans"]
  },
  "preferred_username": "yigit",
  "email": "yigit@example.com"
}
```

`JwtRoleConverter.java` (custom) realm_access.roles → Spring `ROLE_user`, `ROLE_admin`

## 6. Authentication ve Authorization

### 6.1 Frontend Keycloak Adapter

`main.jsx`:
```js
import Keycloak from "keycloak-js";
const keycloak = new Keycloak({
  url: "http://localhost:8090",
  realm: "finans",
  clientId: "finans-frontend"
});

keycloak.init({
  onLoad: "check-sso",
  pkceMethod: "S256",
  silentCheckSsoRedirectUri: window.location.origin + "/silent-check-sso.html"
}).then(authenticated => {
  // Mount React
});
```

PKCE — public client, secret yok.

### 6.2 API Call Pattern

`api/portfolioApi.js`:
```js
async function authHeader(keycloak) {
    await keycloak.updateToken(30);  // refresh if <30s left
    if (!keycloak.token) throw new Error("No access token");
    return { Authorization: `Bearer ${keycloak.token}` };
}

export async function getPositions(keycloak) {
    const headers = await authHeader(keycloak);
    const res = await axios.get(`${API_BASE}/api/v1/portfolio/positions`, { headers });
    return res.data;
}
```

### 6.3 Backend JWT Validation

`SecurityConfig.java`:
```java
http.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
);
```

JWK Set URI: `http://keycloak:8080/realms/finans/protocol/openid-connect/certs` (cached, periodic refresh)

### 6.4 RequireAuth Component (Frontend)

```jsx
function RequireAuth({ keycloak, children }) {
    if (!keycloak?.authenticated) {
        keycloak?.login({ redirectUri: window.location.href });
        return null;
    }
    return children;
}
```

App.jsx'de protected route'larda kullanılır.

## 7. Çok Dillilik ve Otomatik Çeviri

### 7.1 i18n Context

`contexts/I18nContext.jsx`:
- `lang` state ("tr" | "en")
- `t(key, vars)` function — `i18nDict.js`'ten lookup
- localStorage'a persist

`i18nDict.js`:
```js
export const dict = {
  tr: {
    nav: { home: "Anasayfa", stocks: "Hisseler", ... },
    inflation: { yearlyTitle: "Son Yıllık Enflasyon (TÜFE)", ... },
    analysis: { tblColSymbol: "Sembol", ... },
    ...
  },
  en: {
    nav: { home: "Home", stocks: "Stocks", ... },
    ...
  }
};
```

Topbar TR/EN toggle → `setLang("en")` → re-render trigger.

### 7.2 TermInfo Glossary

`data/glossary.js` — 36 finans terimi:
```js
export const GLOSSARY = {
  cpi: {
    tr: "TÜFE (Tüketici Fiyat Endeksi): Hanehalkının tükettiği...",
    en: "CPI (Consumer Price Index): Measures the price level..."
  },
  ...
};
```

`TermInfo.jsx` — hover'da popup açar, key'e göre dil-uygun açıklama.

CSS — `index.css`:
```css
.fp-term-info__btn:hover + .fp-term-info__tip {
    opacity: 1;
    pointer-events: auto;
}
```

### 7.3 Haber Otomatik Çeviri (LibreTranslate)

**Docker servis:**
```yaml
libretranslate:
  image: libretranslate/libretranslate:latest
  environment:
    LT_LOAD_ONLY: "tr,en"           # sadece TR+EN modeli indir (~70MB)
    LT_DISABLE_WEB_UI: "true"
```

**Backend client:** `LibreTranslateClient.java`
- `POST /detect` → dil tahmini
- `POST /translate` → çeviri

**Şema:** `news_articles` tablosu V21 ile genişletildi:
- `source_lang` (VARCHAR(2)) — TR veya EN
- `title_translated`, `summary_translated`, `content_translated` — diğer dile çeviri

**Çeviri stratejisi:**
1. **Fetch time:** `parseItem()` her makaleyi kaydederken URL host'una göre `source_lang` set eder
2. **Background prewarm:** Bir thread tüm `title_translated IS NULL` satırları gezer, EN-source önce (kullanıcı UX'i için, en küçük havuz)
3. **Read time:** `latest(category, lang)` cache-only swap — eğer DB'de çeviri varsa swap eder, yoksa orijinal döner

Bu sayede ilk kullanıcı isteği SLOW olmaz, background katarsız doluyor.

**Lazy translation:** Detay sayfası için (`getById(id, lang)`) inline çevirir tek makale için ~10s, kabul edilebilir.

## 8. Log Pipeline (Log4j2 → Kafka → OpenSearch)

### 8.1 Backend'de Log4j2

**Dependency** `pom.xml`:
- Tüm Spring Boot starter'lardan `spring-boot-starter-logging` exclude
- `spring-boot-starter-log4j2` + `log4j-layout-template-json` added

**Config** `log4j2-spring.xml`:

```xml
<Appenders>
  <Console name="CONSOLE">
    <PatternLayout pattern="%d{...} [%t] %-5level [%X{requestId}] ..."/>
  </Console>

  <Kafka name="KAFKA" topic="finans-logs" syncSend="false" ignoreExceptions="true">
    <JsonTemplateLayout eventTemplateUri="classpath:log4j2/kafka-log-event.json"/>
    <Property name="bootstrap.servers">${kafkaBootstrap}</Property>
    <Property name="acks">0</Property>
    <Property name="linger.ms">1000</Property>
    <Property name="max.block.ms">0</Property>
  </Kafka>
</Appenders>

<Loggers>
  <Logger name="org.apache.kafka" level="WARN" additivity="false">
    <AppenderRef ref="CONSOLE"/>  <!-- recursion guard -->
  </Logger>
  <Root level="INFO">
    <AppenderRef ref="CONSOLE"/>
    <AppenderRef ref="KAFKA"/>
  </Root>
</Loggers>
```

**JSON template** `log4j2/kafka-log-event.json`:
```json
{
  "timestamp": {"$resolver": "timestamp", "pattern": {"format": "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "timeZone": "UTC"}},
  "level": {"$resolver": "level", "field": "name"},
  "logger_name": {"$resolver": "logger", "field": "name"},
  "thread_name": {"$resolver": "thread", "field": "name"},
  "message": {"$resolver": "message", "stringified": true},
  "stack_trace": {"$resolver": "exception", "field": "stackTrace", "stackTrace": {"stringified": true}},
  "service": "${serviceName}",
  "environment": "${environment}",
  "requestId":  {"$resolver": "mdc", "key": "requestId"},
  "userId":     {"$resolver": "mdc", "key": "userId"},
  "username":   {"$resolver": "mdc", "key": "username"},
  "endpoint":   {"$resolver": "mdc", "key": "endpoint"},
  "trace_id":   {"$resolver": "mdc", "key": "trace_id"},
  "span_id":    {"$resolver": "mdc", "key": "span_id"}
}
```

### 8.2 Log-Consumer Servisi

`log-consumer/src/main/java/...`:
```java
@Service
public class LogConsumerService {
    @KafkaListener(topics = "finans-logs", groupId = "log-consumer-group")
    public void consumeLog(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String logMessage = record.value();
        JsonNode logJson = objectMapper.readTree(logMessage);
        String indexName = "finans-logs-" + LocalDate.now().format(ISO_DATE);
        IndexRequest req = new IndexRequest(indexName).source(logMessage, XContentType.JSON);
        openSearchClient.index(req, RequestOptions.DEFAULT);
        ack.acknowledge();
    }
}
```

Manual ack — index başarılı olursa offset commit.

### 8.3 MDC Field'ları

`filter/LoggingFilter.java` her HTTP request'i için MDC'ye field'lar koyar:
- `requestId` — UUID, response header'a da yansır
- `userId`, `username` — JWT'den
- `endpoint`, `httpMethod`, `statusCode`, `durationMs`, `clientIp`
- `trace_id`, `span_id` — OTel auto-injected

Bunlar JsonTemplateLayout'ta `{"$resolver": "mdc", "key": "..."}` ile JSON event'e eklenir.

### 8.4 OpenSearch Dashboards

`http://localhost:5601`
- Index pattern: `finans-logs-*`
- Time field: `timestamp`
- Discover view + 4 visualization (Log Volume by Level donut, Top Loggers bar, Log Timeline line, Top Trace IDs table)
- Dashboard: "Finans Backend - Log Analytics"

## 9. Observability

### 9.1 OpenTelemetry

**Java Agent** (auto-instrument):
```yaml
backend env:
  JAVA_TOOL_OPTIONS: "-javaagent:/otel/opentelemetry-javaagent.jar"
  OTEL_SERVICE_NAME: finans-backend
  OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318
  OTEL_EXPORTER_OTLP_PROTOCOL: http/protobuf
  OTEL_TRACES_EXPORTER: otlp
  OTEL_METRICS_EXPORTER: otlp
  OTEL_TRACES_SAMPLER: always_on
  OTEL_INSTRUMENTATION_SPRING_WEBMVC_ENABLED: true
  OTEL_INSTRUMENTATION_JDBC_ENABLED: true
  OTEL_INSTRUMENTATION_HIBERNATE_ENABLED: true
  OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_SERVER_REQUEST: ""  # security
```

Agent her request için trace + span üretir, JDBC sorgularını otomatik instrument eder.

### 9.2 OTel Collector

Receives OTLP from backend, exports to Jaeger (traces) + Prometheus (metrics).

### 9.3 Prometheus

`monitoring/prometheus.yml`:
```yaml
scrape_configs:
  - job_name: 'prometheus'
    static_configs: [{targets: ['localhost:9090']}]
  - job_name: 'finans-backend'
    metrics_path: '/actuator/prometheus'
    static_configs: [{targets: ['backend:8080']}]
    scrape_interval: 10s
  - job_name: 'otel-collector'
    static_configs: [{targets: ['otel-collector:8888']}]
```

Backend `/actuator/prometheus` Micrometer registry üzerinden expose eder.

### 9.4 Grafana

`grafana/provisioning/`:
- `datasources/datasources.yml` — Prometheus (uid: `prometheus`) + Jaeger (uid: `jaeger`) datasource provisioning
- `dashboards/dashboards.yml` — `/etc/grafana/provisioning/dashboards` path'inden tüm `.json` dosyaları otomatik yükler
- 4 dashboard JSON: `finans-backend.json`, `finans-business.json`, `prometheus-health.json`, `otel-collector.json`

**Default login:** admin/admin (production'da değiştir!)

### 9.5 Jaeger

Trace UI port 16686. Service dropdown'dan `finans-backend` seç, trace listesi gelir. Trace ID'yi alıp OpenSearch Discover'da `trace_id: "..."` ile aratınca o request'in tüm log satırlarını görürsün.

### 9.6 Business Metrics

`ExchangeRateService`, `InflationService`, `NewsService`, `PriceRefreshScheduler` constructor'larında `MeterRegistry` enjekte:

```java
this.refreshSuccessCounter = Counter.builder("fx_refresh_success_total")
    .description("Total successful TCMB FX refreshes")
    .register(meterRegistry);
this.refreshDurationTimer = Timer.builder("fx_refresh_duration_seconds")
    .register(meterRegistry);
```

Service method body'sinde `counter.increment()` ve `timer.record(this::doRefresh)`.

## 10. Cache Mekanizması

### 10.1 Caffeine Config

`CacheConfig.java`:
```java
@EnableCaching
@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cm = new CaffeineCacheManager();
        cm.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(1000));
        cm.setCacheNames(List.of("market-summary", "exchange-rates", ...));
        return cm;
    }
}
```

### 10.2 Kullanım

```java
@Cacheable("market-summary")
public List<MarketSummaryItem> summary() { ... }

@CacheEvict(value = "inflation-all", allEntries = true)
public int refresh() { ... }
```

Refresh sonrası invalidate. Sonraki call recompute.

## 11. Cron Schedules

`backend/src/main/resources/application.yml`:

```yaml
spring:
  task:
    scheduling:
      pool:
        size: 4  # parallel scheduled tasks
```

**Önemli:** Default 1 idi. News fetch (~150s) tüm scheduling thread'i bloke ediyordu. Şimdi 4 thread paralel çalışır.

Her scheduler kendi `@Scheduled` annotation'ı ile (`PriceRefreshScheduler.java`, `NewsService.java`, vs.).

## 12. AI Chatbot Implementation

### 12.1 LLM Client (OpenAI-Compatible)

`analysis/LlmClient.java`:
- Generic HTTP client, base URL parametrized
- Default Groq (`https://api.groq.com/openai/v1`) + model `llama-3.3-70b-versatile`
- API key boşsa `isEnabled() = false` → fallback path

`.env`:
```
APP_LLM_BASE_URL=https://api.groq.com/openai/v1
APP_LLM_API_KEY=gsk_...
APP_LLM_MODEL=llama-3.3-70b-versatile
```

`docker-compose.yml`:
```yaml
backend:
  environment:
    APP_LLM_BASE_URL: ${APP_LLM_BASE_URL:-https://api.groq.com/openai/v1}
    APP_LLM_API_KEY: ${APP_LLM_API_KEY:-}
    APP_LLM_MODEL: ${APP_LLM_MODEL:-llama-3.3-70b-versatile}
```

### 12.2 Routing Strategy

`AiAnalysisService.generateReply(message, lang)`:

```java
// Yapısal intent'ler önce (deterministic JSON output)
ChatResponseDto out = tryBudgetIntent(text, l);      // "5000 TL" regex → 3 scenario
if (out != null) return finalize(out, l);

out = tryInstrumentIntent(text, l);                  // ticker regex → DB lookup
if (out != null) return finalize(out, l);

// Free-form → LLM
if (llm.isEnabled()) {
    ChatResponseDto live = tryLiveLlm(text, l);
    if (live != null) return finalize(live, l);
}

// Keyword fallback'ler
out = tryLowRiskIntent(text, l);
if (out != null) return finalize(out, l);

out = tryLongTermIntent(text, l);
if (out != null) return finalize(out, l);

return finalize(buildHelpResponse(l), l);  // ultimate fallback
```

### 12.3 System Prompt

`buildSystemPrompt(lang)`:
- Persona: "Sen Finans Portalı AI'sın..."
- **STRICT SCOPE:** Sadece finans/yatırım/ekonomi. Off-topic → tek cümleyle reddet
- **Safety rules:** Asla garanti getiri vaadi, asla "Al/Sat" emri, sadece risk dilinde senaryolar
- **Locale conventions:** TR için TL/BIST/TÜFE, ABD için USD
- **Format:** ~250 kelime altı, kısa paragraf, markdown bold'a izin var, tablo/code block yok

### 12.4 Market Context Injection

`appendMarketContext(message, lang)`:
- En son 12 enstrümanı (kategori + fiyat + günlük/yıllık %) snapshot olarak user message'a ekler
- LLM 2023 training data yerine bugünün rakamlarını kullanır

### 12.5 Frontend Chatbot Component

`components/features/analysis/Chatbot.jsx`:
- State: `messages` (history), `input`, `busy`, `error`
- Quick questions chip'leri (5 öneri)
- Composer: textarea + Enter ile send (Shift+Enter newline)
- Scrolla auto-scroll-to-bottom
- AI bubble'da `scenarios` varsa scenario kartları render edilir
- Her cevap altında disclaimer footer

## 13. E-posta ve Bildirim

### 13.1 SMTP Config

`docker-compose.yml`:
```yaml
backend:
  environment:
    SPRING_MAIL_HOST: smtp.gmail.com
    SPRING_MAIL_PORT: 587
    SPRING_MAIL_USERNAME: ${GMAIL_SMTP_USERNAME}
    SPRING_MAIL_PASSWORD: ${GMAIL_SMTP_APP_PASSWORD}
```

Gmail App Password (`myaccount.google.com/apppasswords`).

Dev için `mailpit` container (port 8025) — gerçek e-posta göndermez, web UI'da yakalar.

### 13.2 Bildirim Akışı

**Fiyat alarmı tetiklenince:**
1. `PriceAlertService.checkAllAlerts()` her piyasa refresh'inde çağrılır
2. Her aktif alarm için: latest fiyat vs threshold karşılaştırma
3. Tetiklenirse:
   - In-app: `NotificationService.create(...)` → DB'ye yazar, frontend bell ikonunda görünür
   - E-posta: kullanıcı tercih ettiyse → `JavaMailSender` ile gönderim
4. Alarm `triggered_at` set edilir, tekrar tetiklenmez (one-shot)

## 14. Deployment

### 14.1 Docker Compose

**Tek komut:**
```bash
cp .env.example .env  # ilk kez
docker compose up -d
```

18 servis paralel kalkar:
- postgres (PVC), keycloak (postgres backed), keycloak-bootstrap (one-shot Job)
- backend, frontend (nginx)
- kafka + zookeeper
- log-consumer + opensearch + opensearch-dashboards
- prometheus + grafana + jaeger + otel-collector
- libretranslate + mailpit
- playwright-service + openldap + phpldapadmin

Erişim:
- Frontend: http://localhost
- Backend API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui/
- Grafana: http://localhost:3001 (admin/admin)
- Keycloak admin: http://localhost:8090/admin (admin/admin)
- Jaeger: http://localhost:16686
- OpenSearch Dashboards: http://localhost:5601
- MailPit: http://localhost:8025

### 14.2 Kubernetes (kind)

```powershell
.\k8s\deploy-local.ps1
```

Script:
1. kind kurulu mu kontrol
2. `finans-portali` kind cluster oluştur (yoksa)
3. Backend + Frontend image build
4. `kind load docker-image` ile cluster'a push
5. Namespace + Secrets apply
6. `kubectl apply -k k8s/overlays/dev`
7. Rollout bekle
8. 3 port-forward background job (frontend:5173, backend:8080, keycloak:8090)

`k8s/base/`:
- Postgres StatefulSet + PVC + Service
- Keycloak Deployment + bootstrap Job (kcadm.sh script ConfigMap)
- Backend Deployment + Service + ConfigMap + envFrom Secret
- Frontend Deployment + Service + nginx ConfigMap
- Ingress (placeholder hostnames)

`k8s/overlays/dev/`:
- Strategic merge patches: `imagePullPolicy: Never`, NodePort services, küçük resource requests
- `kustomization.yaml` ile base + patches

`k8s/overlays/prod/`:
- Referans — production'da HPA + anti-affinity + cert-manager TLS + semver image tags

### 14.3 Production Notes

- Keycloak admin parolası değiştir
- Grafana admin parolası değiştir
- `APP_LLM_API_KEY`, `APP_FRED_API_KEY`, `GMAIL_SMTP_APP_PASSWORD` rotate et
- SSL/TLS termination Ingress'te (cert-manager)
- External Secrets Operator veya Sealed Secrets ile `.env`'i kill et
- PVC backup (Velero veya cloud-native)
- HPA backend için (CPU bazlı, 3-10 replica)

## 15. Önemli Bug'lar ve Düzeltmeleri

Proje boyunca düzeltilen kritik bug'lar — yeni geliştirici aynı tuzaklara düşmesin:

### 15.1 BIST Hisse Fiyat Değişimi 0% Bug'ı

**Symptom:** AAPL, MSFT, NVDA, TSLA gibi US ve THYAO, GARAN gibi BIST hisselerinde change_pct = 0.

**Root cause:** Yahoo Finance'a `range=2d` ile sorgu atınca `chartPreviousClose` = bugünün açılışı dönüyor (dünün kapanışı yerine). Hesap (current - prev) = 0.

**Fix:** `YahooPriceFetcher.fetchQuote()` → `range=1d` kullan.

### 15.2 @Transactional Self-Invocation Bypass

**Symptom:** `PriceRefreshScheduler.refreshInstrumentInTransaction()` `@Transactional` annotated ama runtime'da "No EntityManager with actual transaction" hatası.

**Root cause:** `refreshAll()` aynı sınıftan `refreshInstrumentInTransaction()` çağırıyor → Spring AOP proxy bypass → @Transactional silent ignore.

**Fix:** `@Lazy self` self-reference:
```java
@Autowired @Lazy
private PriceRefreshScheduler self;

public void refreshAll() {
    boolean ok = self.refreshInstrumentInTransaction(inst, yahooSym);  // proxy üzerinden
}
```

### 15.3 Candle Delete + Insert Race

**Symptom:** `duplicate key value violates unique constraint "market_candles_instrument_id_day_key"`.

**Root cause:** Hibernate flush order — delete ve insert aynı transaction'da, INSERT önce flush edilirse unique constraint çakışıyor.

**Fix:** Find-and-update upsert pattern:
```java
private void upsertCandle(MarketInstrument inst, LocalDate day, BigDecimal close) {
    MarketCandle candle = candleRepo.findByInstrumentAndDay(inst, day)
        .orElseGet(() -> new MarketCandle(inst, day, close));
    candle.setClose(close);
    candleRepo.save(candle);
}
```

### 15.4 Scheduler Pool Size Bottleneck

**Symptom:** Backend startup'tan sonra fiyat refresh hiç fire etmiyor, news fetch 150 saniye sürüyor.

**Root cause:** Default Spring task scheduler pool size = 1. News fetch (5 dk!) tek thread'i tutuyor, fiyat refresh bekliyor.

**Fix:** `application.yml` → `spring.task.scheduling.pool.size: 4`.

### 15.5 1y Candle Backfill Tetiklenmiyor

**Symptom:** Analiz sayfasında "Yıllık %" kolonu hep "—".

**Root cause:** `PriceRefreshScheduler` 1y history fetch'i sadece `if (existingCount < 30)` ile tetikliyor. ~50 candle "yeterli" sayılıyor, 1y bypass ediliyor.

**Fix:** Sayı yerine DERİNLİĞE bak:
```java
boolean hasYearOldCandle = !candleRepo
    .findByInstrumentAndDayBetweenOrderByDayAsc(
        inst, yearAgo.minusDays(30), yearAgo.plusDays(30))
    .isEmpty();
if (!hasYearOldCandle) {
    // fetch 1y
}
```

### 15.6 Yıllık Change Window Yanlış Yönde

**Symptom:** `changeOverDays` 365 gün için hep null döner.

**Root cause:** Window `[target - 7, target]` = 372 gün öncesi - 365 gün öncesi. Yahoo'nun 1y range'i bu aralığı KAPSAMIYOR.

**Fix:** Window'u FORWARD çevir + buffer artır:
```java
candleRepo.findByInstrumentAndDayBetweenOrderByDayAsc(
    inst, target, target.plusDays(14));  // hafta sonu/tatil için 14 gün
```

### 15.7 BIST/STOCK Currency Heuristic Yanlış

**Symptom:** AAPL'a TR enflasyonu, THYAO'ya... wait, hepsi TRY etiketlenmiş.

**Root cause:** `isBistSymbol()` 4-5 harfli upper case = BIST diyordu. AAPL de match ediyor.

**Fix:** Raw `item.type()` bazlı `currencyForType()`:
- BIST → TRY
- STOCK → USD
- INDEX XU* → TRY (BIST 100/50/30)
- CRYPTO/FX/COMMODITY → USD

### 15.8 Logback Duplicate Field

**Symptom:** OpenSearch log-consumer crash: `"Duplicate field 'environment'"`.

**Root cause:** Logback config'inde `<springProperty name="environment">` zaten context'e ekliyor; `customFields` da aynı key'i tekrar ekliyordu.

**Fix:** `customFields`'tan `environment` çıkar; sadece `service` bırak (springProperty `serviceName`, farklı key).

### 15.9 Frontend nginx DNS Cache

**Symptom:** Backend restart sonrası frontend'den `502 Bad Gateway`.

**Root cause:** Backend container IP değişti, nginx eski IP'yi cache'liyor (DNS startup'ta bir kez resolve eder).

**Fix:** `docker restart finans-frontend` — DNS yeniden resolve. Kalıcı çözüm: nginx config'e `resolver 127.0.0.11 valid=10s` ekleyebiliriz.

### 15.10 useMemo Deps Eksiklikleri

**Symptom:** "Enflasyonu yenenler" toggle'ı yapıyor ama filtre çalışmıyor.

**Root cause:** `filtered` useMemo'nun deps array'inde `beatsInflationOnly` eksikti — state değişiyor ama memo recompute etmiyordu.

**Fix:** Deps array tam: `[items, category, search, beatsInflationOnly, sortKey, sortDir]`.

### 15.11 Groq API Key Auto-Revoke

**Symptom:** Groq'tan ilk API key cevap dönmüyor: `"Organization has been restricted"`.

**Root cause:** Groq'un otomatik secret-leak detector'u, chat geçmişine yapıştırılan key'i taramış ve devre dışı bırakmış.

**Fix:** Yeni key oluştur, `.env`'e EDİTÖRDE yapıştır, asla chat'e gösterme. Mevcut leaked key'i hemen sil.

### 15.12 Historical Delete Persistence

**Symptom:** Geçmişten sayfasında delete butonu satırı siler ama refresh'te geri gelir.

**Root cause:** Save useEffect'inde `if (positions.length > 0)` guard'ı vardı. Liste boş kalınca localStorage update edilmiyordu.

**Fix:** Guard'ı kaldır + `loaded` flag ekle:
```js
useEffect(() => {
  if (!loaded) return;  // initial mount'ta save'i atla
  localStorage.setItem("historicalPositions", JSON.stringify(positions));
}, [positions, loaded]);
```

---

## Sonuç

Bu proje, tek bir geliştirici tarafından **3 aylık intensive geliştirme** süresinde tamamlanmıştır. Tüm tasarım kararları, bug fix'leri ve mimari seçimler git history'de görülebilir (148+ commit).

**Önemli git milestones:**
- `7faf1f5` — News translation pipeline + BIST refresh fixes + business metrics
- `7d50341` — /analysis sayfası + AI chatbot + glossary tooltips
- `99cafc1` — Tablo kolonlarında name sort özelliği
- `ae3906a` — Compliance audit fixes (Javadoc + Log4j2 + 2FA)

Yeni geliştirici için en hızlı onboarding yolu:
1. Bu dokümanı okumak
2. `docker compose up -d`
3. http://localhost'a girip her sayfayı tıklayıp gezmek
4. Bir feature ekle/değiştir → öğrendiklerini test et
5. Sorularını GitHub Issues'a aç

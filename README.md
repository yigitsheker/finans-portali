# 📈 Finans Portalı

> Türkiye odaklı, çok-varlıklı **finans portföy & piyasa takip platformu** — hisse, kripto, fon, tahvil/bono, döviz, emtia, VİOP vadeli işlemleri, enflasyon ve haberleri tek yerde toplar; portföy takibi, fiyat alarmları, teknik analiz ve AI sohbet sunar.
>
> ⚠️ **Tüm al-sat işlemleri (özellikle tahvil/bono ve VİOP) SİMÜLASYONDUR** — gerçek emir gönderilmez, yalnızca portföy takibi amaçlıdır. Yatırım tavsiyesi değildir.

Spring Boot 3.5.9 (Java 21) backend + React 19 / Vite frontend, tam bir self-hosted altyapı (Keycloak, Kafka, OpenSearch, Prometheus/Grafana, Jaeger, LDAP) ile Docker Compose üzerinde çalışır; Kubernetes/Kustomize (GKE) overlay'leri ve GitHub Actions CI/CD ile birlikte gelir.

---

## 📑 İçindekiler

- [Genel Bakış](#-genel-bakış)
- [Özellikler](#-özellikler)
- [Mimari](#️-mimari)
- [Teknoloji Stack](#️-teknoloji-stack)
- [Hızlı Başlangıç](#-hızlı-başlangıç)
- [Erişim Adresleri & Varsayılan Kimlikler](#-erişim-adresleri--varsayılan-kimlikler)
- [Geliştirme](#-geliştirme)
- [Proje Yapısı](#-proje-yapısı)
- [Backend — API & Domain](#-backend--api--domain)
- [Frontend](#-frontend)
- [Veri Kaynakları & Entegrasyonlar](#-veri-kaynakları--entegrasyonlar)
- [Gözlemlenebilirlik (Observability)](#-gözlemlenebilirlik-observability)
- [Güvenlik](#-güvenlik)
- [Konfigürasyon (Ortam Değişkenleri)](#️-konfigürasyon-ortam-değişkenleri)
- [CI/CD & Deployment](#-cicd--deployment)
- [Önemli Notlar (Gotchas)](#-önemli-notlar-gotchas)
- [Sorun Giderme](#-sorun-giderme)
- [Lisans](#-lisans)

---

## 🔭 Genel Bakış

Finans Portalı; piyasa verisini çeşitli kamuya açık kaynaklardan çekip normalize eden, kullanıcıya tek arayüzde sunan ve üzerine **portföy/işlem simülasyonu**, **fiyat alarmı**, **teknik analiz**, **AI finans asistanı** ve **haber akışı** ekleyen bir platformdur.

- **Backend:** `/api/v1/**` altında ~19 REST controller. PostgreSQL + Flyway (V1–V27), Caffeine cache, OAuth2 resource server (Keycloak JWT). Zamanlanmış işler dış kaynakları periyodik tazeler; çoklu-replika ortamda **ShedLock** ile tek-instance çalışır.
- **Frontend:** React 19 SPA (Vite + SWC). Keycloak OIDC (PKCE), TR/EN i18n, açık/koyu tema, çoklu para-birimi gösterimi, lightweight-charts / klinecharts / recharts grafikleri.
- **Altyapı:** Docker Compose ile 19 servis (auth, mesajlaşma, log, metrik, izleme). Kubernetes/Kustomize + GKE overlay.

> **Not:** Tahvil/bono ve VİOP al-sat uçları (`/api/v1/portfolio/bonds`, `/api/v1/portfolio/viop`) **yalnızca simülasyondur** — sanal pozisyon tutar, gerçek emir göndermez. Kullanıcı kimliği daima JWT `sub` claim'inden alınır; herkes sadece kendi verisine erişir.

---

## ✨ Özellikler

| Alan | Açıklama |
|---|---|
| **Hisse / Kripto / Emtia / Endeks** | Yahoo Finance'ten anlık fiyat + geçmiş + mum (OHLC); BIST gün-sonu (gecikmeli), kripto/ABD/endeks 15 dk intraday. |
| **Döviz (FX)** | TCMB günlük kur XML'i; kur tablosu + çevirici. |
| **Yatırım Fonları** | TEFAS'tan ~1000+ fon (tip/şirket/risk/getiri 1a/3a/6a/YTD/1y/3y/5y). |
| **Tahvil / Bono** | TCMB EVDS3 DİBS; temiz/kirli fiyat, kupon oranı, **YTM (iskonto ile hesaplanır)**, vade; sukuk/TÜFE-endeksli hariç. |
| **VİOP (Vadeli İşlemler)** | İş Yatırım'dan futures kontratları (15 dk gecikmeli); long/short pozisyon simülasyonu, teminat, kaldıraç, net-pozisyon, vade. |
| **Enflasyon** | TCMB TÜFE (TP.FG.J0) + ABD CPI (FRED CPIAUCSL); aylık/yıllık değişim, 5 yıllık kümülatif. |
| **Mevduat Faizleri** | TCMB EVDS3 — TRY/USD/EUR × 6 vade kovası. |
| **Haberler** | 10 kategori, 31 RSS kaynağı; tam içerik scrape + LibreTranslate ile TR↔EN çeviri. |
| **Portföy** | Hisse/kripto/fon alım-satım takibi (maliyet ortalaması), dağılım, performans, Excel import. |
| **Geçmişten Bugüne** | Geçmiş alımların enflasyona karşı reel getirisi; sembol+tarih ile o günkü fiyatı otomatik çeker. |
| **Listelerim (Watchlist)** | Çoklu takip listesi; navbar'dan ayrı sayfa. |
| **Fiyat Alarmları** | PRICE_ABOVE / PRICE_BELOW / PERCENT_GAIN / PERCENT_LOSS; e-posta bildirimi (TR/EN, TRY/USD snapshot). |
| **Analiz & AI Sohbet** | Çapraz-varlık analiz tablosu + sinyaller; OpenAI-uyumlu LLM (Groq) ile finans asistanı (anahtar yoksa lokal mock). |
| **Teknik Analiz** | Hareketli ortalama, trend, destek/direnç, momentum. |
| **Bildirimler** | Uygulama-içi bildirim merkezi + zil rozeti. |
| **Yönetim (Admin)** | RSS feed yönetimi, piyasa/fon/haber sıfırla-yenile, Keycloak kullanıcı yönetimi (ban/2FA). |
| **Kimlik** | Keycloak OIDC + zorunlu TOTP 2FA + LDAP federasyonu + özel login teması. |

---

## 🏗️ Mimari

```
                              ┌─────────────────────────────────────────────┐
   Tarayıcı ──▶ nginx :80 ───▶│  React 19 SPA (Vite)   /api ─▶ backend:8080  │
                              └─────────────────────────────────────────────┘
                                                │
                  ┌─────────────────────────────┼──────────────────────────────┐
                  ▼                             ▼                              ▼
        ┌──────────────────┐       ┌──────────────────────┐        ┌────────────────────┐
        │ Keycloak :8090   │       │  Backend (Spring)     │        │  PostgreSQL :5432   │
        │ OIDC + 2FA + LDAP │◀────▶│  :8080  /api/v1/**    │◀──────▶│  finans_db          │
        └──────────────────┘       │  Flyway V1–V27        │        │  + keycloak_db      │
                  ▲                 │  Caffeine • ShedLock  │        └────────────────────┘
                  │                 └──────────┬───────────┘
            OpenLDAP :389                       │ dış veri (fetcher'lar)
                                                ▼
   Yahoo • TCMB EVDS3 • TCMB XML • TEFAS • FRED • İş Yatırım • RSS • LibreTranslate • Groq LLM
                                                │
   Gözlemlenebilirlik:  Log4j2 ─▶ Kafka(finans-logs) ─▶ log-consumer ─▶ OpenSearch ─▶ Dashboards
                        Micrometer ─▶ Prometheus ─▶ Grafana     OTel agent ─▶ Jaeger
```

**Yardımcı servisler:** Playwright (headless Chromium — Cloudflare/Akamai bypass), LibreTranslate (TR↔EN), Mailpit (dev e-posta), phpLDAPadmin (LDAP UI), Kafka/Zookeeper (yalnız log taşıma).

---

## 🛠️ Teknoloji Stack

### Backend
- **Java 21**, **Spring Boot 3.5.9** (parent), Maven (wrapper 3.3.4 → Maven 3.9.12)
- Spring **Web MVC** + **WebFlux** (WebClient/reactor-netty — dış HTTP), **Data JPA/Hibernate**, **Security OAuth2 Resource Server** (Keycloak JWT/RS256), **Cache** (Caffeine), **Validation**, **Mail**, **Actuator**
- **Flyway** (core + flyway-database-postgresql) — V1–V27
- **Log4j2** (Logback yerine) + `log4j-layout-template-json` + Kafka appender; **Spring Kafka** (yalnız log)
- **Micrometer** (Prometheus registry) + **OpenTelemetry** (Java agent v2.0.0, OTLP exporter, tracing-bridge)
- **ShedLock 5.16.0** (jdbc-template provider) — dağıtık scheduler kilidi
- **springdoc-openapi 2.7.0** (Swagger UI), **Jsoup 1.17.2** (HTML scrape), **Apache POI 5.2.5** (Excel), **Lombok**
- Test: spring-boot-starter-test, spring-security-test, **H2**; **JaCoCo 0.8.12**

### Frontend
- **React 19.2** + **Vite 7.3** (`@vitejs/plugin-react-swc`), **Node 20**
- **react-router-dom 7.14**, **axios 1.13**, **keycloak-js 26.2**
- Grafikler: **lightweight-charts 5.2**, **klinecharts 9.8** (mum + çizim araçları + göstergeler), **recharts 3.8** (pasta)
- **Tailwind CSS 4.2**, **react-hot-toast 2.6**, **xlsx (SheetJS 0.20.3)** (Excel import, lazy-load)
- Özel **i18n** (TR/EN dict, react-i18next değil), CSS-değişkenli **tema** (`data-theme`), çoklu **para-birimi** gösterimi
- _Not: `zustand`, `react-window`, `react-sparklines` bağımlılıkları kuruludur ama kullanılmıyor (state React hook/context ile)._

### Altyapı & Gözlemlenebilirlik
- **Docker Compose** (19 servis), **Kubernetes + Kustomize** (base + dev/prod/gke overlay), **GKE** (Artifact Registry, WIF, GCE Ingress)
- **PostgreSQL 15** (compose) / 16 (k8s StatefulSet)
- **Keycloak 26.5.1** + **OpenLDAP 1.5.0** + phpLDAPadmin
- **Kafka + Zookeeper** (Confluent 7.5.0), **OpenSearch + Dashboards 2.11.1**
- **Prometheus v2.48.1**, **Grafana 10.2.3**, **Jaeger 1.53**, **otel-collector-contrib 0.93.0**
- **LibreTranslate**, **Mailpit**, **Playwright v1.47.2**, **nginx:alpine** (prod servis)
- CI/CD: **GitHub Actions** + **SonarCloud**

---

## 🚀 Hızlı Başlangıç

### Gereksinimler
- **Docker Desktop** (Compose v2) — 8GB+ RAM önerilir
- (Yalnız Docker'sız lokal geliştirme için) **JDK 21**, **Node 20**

### Tek Komutla Başlat
```bash
git clone <repo-url> && cd finans-portali

docker compose up -d          # veya:  make up   /   .\make.ps1 up   /   .\start-docker.ps1
```
İlk açılış imaj derlemesi + Keycloak/LDAP/OpenSearch boot'u nedeniyle birkaç dakika sürebilir. `start-docker.ps1` `.env` yoksa `.env.example`'dan kopyalar, sağlık kontrolü yapıp URL'leri yazar.

> ⚠️ `start.sh` / `start.bat` **eskidir** (kaldırılmış redis/logstash/filebeat servislerine ve yanlış portlara işaret eder). `docker compose up -d`, `make up`, `make.ps1` veya `start-docker.ps1` kullanın.

### İlk Giriş
Keycloak'a **insan kullanıcı seed edilmez** (yalnız servis hesabı vardır). Realm'de self-register açıktır:
1. http://localhost adresinden **Kayıt Ol** ile hesap oluştur, **veya** Keycloak admin konsolundan (http://localhost:8090, `admin/admin`) `finans` realm'inde kullanıcı oluştur.
2. İlk girişte **TOTP 2FA** kurulumu zorunludur (Google Authenticator / FreeOTP).

---

## 🌐 Erişim Adresleri & Varsayılan Kimlikler

| Servis | URL | Varsayılan Kimlik (dev) |
|---|---|---|
| **Frontend** | http://localhost | — |
| **Backend API** | http://localhost:8080 | (JWT) |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | — |
| **Backend Health** | http://localhost:8080/actuator/health | — |
| **Keycloak** | http://localhost:8090 | `admin` / `admin` |
| **PostgreSQL** | `localhost:5432` | `finans_user` / `finans_password` (db: `finans_db`) |
| **Grafana** | http://localhost:3100 | `admin` / `admin` |
| **Prometheus** | http://localhost:9090 | — |
| **Jaeger** | http://localhost:16686 | — |
| **OpenSearch Dashboards** | http://localhost:5601 | — (güvenlik eklentisi kapalı) |
| **Mailpit (dev e-posta)** | http://localhost:8025 | — |
| **phpLDAPadmin** | http://localhost:8089 | `cn=admin,dc=finance,dc=local` / `admin_password` |
| Kafka | `localhost:9092` | — |
| OpenSearch REST | `localhost:9200` | — |
| log-consumer | `localhost:8081` | — |

> **Grafana 3100'dedir** (3000 değil — Windows/Hyper-V 2954-3053 aralığını rezerve ettiği için). **Keycloak 8090'dadır** (8080/8081 değil). LDAP seed kullanıcıları (`ldap/init.ldif`): `john.doe`/`jane.smith` `password123`, `test.user` `test123`, `admin.user` `admin123` (LDAP federasyonu manuel kurulduğunda — bkz. `LDAP_SETUP.md`).
>
> 🔐 Bu kimlikler **yalnız geliştirme** içindir; üretimde değiştirin.

---

## 💻 Geliştirme

### Hot-Reload (HMR) modu
```bash
make dev          # veya:  .\make.ps1 dev
# docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
# frontend Vite dev server'a geçer; http://localhost yine çalışır (host 80 -> Vite 5173)
make dev-down
```

### Docker'sız lokal
```bash
# Backend (8080) — application.yml varsayılanı localhost Postgres + Keycloak
cd backend && ./mvnw spring-boot:run

# Frontend (5173)
cd frontend && npm install && npm run dev
```
> ⚠️ `application.yml` lokal varsayılanı **db `finans` / kullanıcı `finans` / şifre `finans`**'tır — Compose'un Postgres'i (`finans_db` / `finans_user` / `finans_password`) ile **farklıdır**. `./mvnw spring-boot:run`'ı Compose Postgres'ine bağlayacaksan `SPRING_DATASOURCE_URL/_USERNAME/_PASSWORD`'ü override et.

### Test & Build
```bash
cd backend && ./mvnw test          # H2 in-memory + JaCoCo -> target/site/jacoco/index.html
cd backend && ./mvnw clean package # jar (Docker imajı -DskipTests ile build alır)
cd frontend && npm run build        # vite build -> dist/
cd frontend && npm run lint         # eslint  (CI'da kapalı — bkz. Notlar)
```

### Faydalı Make / make.ps1 hedefleri
`up` · `down` · `restart` · `ps` · `logs` / `logs-backend` · `health` · `rebuild` / `rebuild-backend` / `rebuild-frontend` · `front` (hızlı frontend rebuild) · `dev` / `dev-down` · `backup` / `restore` (pg_dump/psql `finans_db`) · `shell-backend` / `shell-postgres` · `stats` · `clean` (down -v + prune) · `prod-up` / `prod-down` (docker-compose.prod.yml)

> Windows'ta GNU make yoksa **`make.ps1`** kullanın (`docker compose` v2 sözdizimi). Makefile `docker-compose` (v1) çağırır.

### Veritabanı Migration
Flyway uygulama açılışında çalışır (`classpath:db/migration`, `baseline-on-migrate=true`). Yeni migration: `backend/src/main/resources/db/migration/V28__...sql`. _Not: JPA `ddl-auto=update` ile Flyway aynı anda açık (bilinen anti-pattern); şema otoritesi migration'lardır._

---

## 📁 Proje Yapısı

```
finans-portali/
├── backend/              # Spring Boot (Java 21, Maven) — REST API, scheduler'lar, fetcher'lar
│   └── src/main/java/com/finansportali/backend/{controller,service,entity,dto,repository,config,filter,util}
│   └── src/main/resources/{application.yml, db/migration/V1..V27, log4j2-spring.xml}
├── frontend/            # React 19 + Vite SPA  (pages, components, api, contexts, hooks, utils)
├── log-consumer/        # Kafka(finans-logs) -> OpenSearch indexer (ayrı Spring Boot modülü)
├── playwright-service/  # Node/Express headless Chromium sidecar (POST /fetch)
├── keycloak/            # finans-realm.json (realm import)
├── keycloak-themes/     # finance-theme (özel login teması)
├── ldap/                # init.ldif (LDAP seed)
├── k8s/                 # Kustomize base + overlays (dev/prod/gke) + GKE dokümanları
├── grafana/ · prometheus.yml · otel-collector-config.yaml   # gözlemlenebilirlik config
├── monitoring/ · fluent-bit/   # ESKİ/alternatif log stack (aktif değil)
├── docker-compose.yml · .dev.yml · .prod.yml
├── Makefile · make.ps1 · start-docker.ps1
├── docs/                # ANALIZ_DOKUMANI, TEKNIK_ANALIZ_DOKUMANI, BONDS_MODULE, ...
└── sonar-project.properties
```

---

## 🔌 Backend — API & Domain

Tüm uçlar `/api/v1/**` altındadır. **Public GET** olanlar kimlik gerektirmez; **Authenticated** olanlar Keycloak JWT ister; **ADMIN** olanlar `ROLE_ADMIN` gerektirir.

### REST Controller'lar

| Controller | Base Path | Erişim | Öne çıkan uçlar |
|---|---|---|---|
| **MarketController** | `/api/v1/market` | Public GET | `/summary`, `/spot-rates`, `/instruments`, `/search`, `/price`, `/history`, `/candles`, `/history/fx`, `/history/batch` |
| **ExchangeRateController** | `/api/v1/exchange-rates` | Public GET | `/`, `/sources`, `/source/{s}`, `/currency/{c}/history` |
| **InvestmentFundController** | `/api/v1/investment-funds` | Public GET | `/`, `/types`, `/companies`, `/{code}`, `/top-performers`, `/search`; `POST /admin/refresh` (ADMIN) |
| **BondController** | `/api/v1/bonds` | Public GET | `/` (filtre), `/{id}`, `/{id}/history`, `/summary`; `POST /refresh` (ADMIN) |
| **BondTradeController** | `/api/v1/portfolio/bonds` | Auth · **SİMÜLASYON** | `POST /buy`, `/sell`, `GET /positions`, `/transactions`, `/summary`, `POST /preview/buy`, `/preview/sell` |
| **ViopController** | `/api/v1/viop` | Public GET | `GET /?category` (kontrat listesi) |
| **ViopTradeController** | `/api/v1/portfolio/viop` | Auth · **SİMÜLASYON** | `POST /positions/open`, `/positions/close`, `GET /positions`, `/transactions`, `/summary`, `POST /preview` |
| **PortfolioController** | `/api/v1/portfolio` | Auth | `GET /positions`, `/summary`, `/allocation`, `/allocation/by-type`, `/summary-detail`, `/performance`; `POST /positions` (al, maliyet ort.), `/positions/sell`; `DELETE /positions/{symbol}`, `/positions` |
| **PriceAlertController** | `/api/v1/alerts` | Auth | `POST /`, `GET /`, `DELETE /{id}`, `POST /{id}/test` |
| **WatchlistController** | `/api/v1/watchlists` | Auth | `GET /`, `GET/{id}`, `POST /`, `PUT /{id}`, `DELETE /{id}`, `POST /items`, `DELETE /{id}/items/{symbol}` |
| **InflationController** | `/api/v1/inflation` | Public GET | `/?country=TR\|US`, `/latest`, `/compare?from&to&nominalPct`; `POST /refresh` (ADMIN) |
| **DepositRateController** | `/api/v1/deposit-rates` | Public GET | `/?currency`, `/latest`, `/latest/{currency}`; `POST /refresh` (ADMIN) |
| **NewsController** | `/api/v1/news` | Public GET + `POST /{id}/fetch-content` | `GET /?category&lang`, `/categories`, `/category-counts`, `/{id}`; `POST /{id}/fetch-content`; `POST /cleanup` (auth) |
| **NotificationController** | `/api/v1/notifications` | Auth | `GET /`, `/unread-count`, `POST /{id}/read`, `/mark-all-read` |
| **AnalysisController** | `/api/v1/analysis` | Auth | `GET /instruments`, `/instruments/{symbol}`, `POST /chat` (AI) |
| **TechnicalAnalysisController** | `/api/v1/technical-analysis` | Public GET | `/{symbol}`, `/{symbol}/moving-averages`, `/trend`, `/support-resistance`, `/momentum` |
| **UserProfileController** | `/api/v1/users/me` | Auth | `PATCH /` (Keycloak Admin API), `GET /security`, `DELETE /2fa` |
| **UserPreferencesController** | `/api/v1/users/me/notification-prefs` | Auth | `GET /`, `PUT /` |
| **AdminController** | `/api/v1/admin` | ADMIN | RSS feed CRUD/toggle/cleanup; `reset-market`, `refresh-prices`, `reset-news`, `refresh-news`, `reset-funds`; Keycloak kullanıcı `ban`/`unban`/`require-2fa`/`reset-2fa` |

### Zamanlanmış İşler (Scheduler'lar)

| İş | Zamanlama | Ne yapar |
|---|---|---|
| `PriceRefreshScheduler` | Açılış+60sn (1 kez); günlük **18:00 UTC** tam; **15 dk** intraday (sadece gecikmesizler) | Yahoo fiyat/mum + fiyat-alarmı kontrolü, cache eviction |
| `ExchangeRateService` | Açılış+10sn, **4 saatte bir** | TCMB kur XML |
| `ViopService` | Açılış+30sn, **15 dk** | İş Yatırım VİOP kontratları |
| `NewsService` | Açılış+5sn, **6 saatte bir** (+ günlük 03:00 temizlik) | RSS çek + içerik scrape + çeviri prewarm |
| `InflationService` | Günlük **09:00** | TCMB TÜFE + FRED ABD CPI |
| `DepositRateService` | Günlük **09:05** | TCMB mevduat faizleri |
| `BondDataRefreshScheduler` | Açılış+5sn, **2 saatte bir** | TCMB EVDS3 DİBS |
| `InvestmentFundRefreshScheduler` | Açılışta seed + hafta içi **10:30/14:30/18:30** | TEFAS fonları |
| `ViopExpiryScheduler` 🔒 | Günlük **00:05** (ShedLock) | Vadesi gelen VİOP pozisyonlarını expire eder |
| `BondCouponMaturityScheduler` 🔒 | Günlük **00:10** (ShedLock) | Kupon öder / vade gelen tahvilleri itfa eder |

🔒 = `@SchedulerLock` ile korunur (GKE çoklu-replikada tek instance çalışır). Diğerleri idempotent upsert olduğu için kilitsizdir.

> Tüm cron'lar **konteynerin saat dilimine** göredir; konteynerlerde TZ ayarlanmadığından pratikte **UTC**'dir (TR yerel saati değil).

### Servis Katmanı
- **Market:** `MarketService` → `MarketInstrumentService` / `MarketPriceService` / `MarketHistoryService` / `MarketDataSeedService`
- **Portfolio:** `PortfolioService` → `PortfolioCalculationService` / `PortfolioCurrencyService` / `PortfolioPerformanceService` / `PortfolioPositionService`
- **VİOP:** `ViopCalculationService` (saf matematik) / `ViopPositionService` (long/short, net pozisyon, teminat) / `ViopExpiryScheduler`
- **Tahvil:** `BondCalculationService` (clean/dirty/accrued/kupon) / `BondPositionService` (nominal, ağırlıklı ortalama, itfa P&L) / `BondCouponMaturityScheduler` + `BondDataRefreshService`
- **Analiz:** `AiAnalysisService` / `InstrumentAnalysisService` / `LlmClient` / `RiskProfileService` / `TechnicalAnalysisService`
- Diğer: `NewsService`, `NotificationService`, `PriceAlertService`, `ExchangeRateService`, `InvestmentFundService`, `InflationService`, `DepositRateService`, `DebtInstrumentService`, `WatchlistService`, `UserService`, `KeycloakAdminService`

### Veritabanı Şeması (Flyway V1–V27 özeti)
- **V1:** `market_instruments`, `market_quotes`, `market_candles`, `portfolio_positions`, `price_alerts`, `news_articles`
- **V2–V12:** `exchange_rates`, `investment_funds` (+getiriler), `historical_prices`, `watchlists`/`watchlist_items`, `debt_instruments`(+quotes), `inflation_data_points`, `deposit_rate_points`
- **V13–V24:** `notifications`, `news_feeds`, `viop_contracts`, alarm dil/para-birimi, `user_preferences`, çeviri kolonları, mojibake düzeltmeleri
- **V25:** `viop_positions` + `viop_transactions` (UNIQUE user+kontrat)
- **V26:** `bond_positions` + `bond_transactions` (UNIQUE user+ISIN)
- **V27:** `shedlock` (dağıtık kilit tablosu)

### Cache (Caffeine)
maxSize 1000, **TTL 30 sn**. Cache'ler: `marketSummary`, `allInstrumentsWithPrices`, `marketHistory`, `yahooChart`, `technicalAnalysis`, `exchange-rates`(+by-source), `investment-funds`(+by-type), `inflation-all/latest`, `deposit-rates-all/latest`.

---

## 🎨 Frontend

React 19 SPA; tüm veri backend'den `/api/v1/*` ile gelir (dev'de Vite proxy, prod'da nginx reverse-proxy).

### Sayfalar & Route'lar

| Route | Sayfa | Erişim |
|---|---|---|
| `/` | Home (landing, hareketliler, BIST100, FX/kripto/emtia, son haberler) | Public |
| `/news` · `/news/:id` | Haber listesi · detay | Public |
| `/stocks` · `/crypto` · `/commodities` | `FinexStyleMarket` (filterType) | Public |
| `/market-data` | Döviz Kurları + çevirici | Public |
| `/funds` | Yatırım fonları | Public |
| `/bonds` | Tahvil/bono piyasası + al (simülasyon) + pozisyonlar | Public |
| `/viop` | VİOP kontratları + long/short (simülasyon) | Public |
| `/inflation` | TÜFE/CPI (TR/US) | Public |
| `/analysis` | Çapraz-varlık analiz + AI sohbet | 🔒 Auth |
| `/portfolio` | Portföy panosu (özet, dağılım, performans, türevler) | 🔒 Auth |
| `/historical` | Geçmişten Bugüne (enflasyona karşı reel getiri) | 🔒 Auth |
| `/lists` | **Listelerim** (watchlist yöneticisi) | 🔒 Auth |
| `/settings` | Profil, tema, bildirim tercihleri, 2FA | 🔒 Auth |
| `/admin` | Yönetim paneli (kullanıcı/veri/RSS) | 🔒 Auth + admin |
| `/chart` | Tam ekran KLineChart (ayrı sekme) | Public |

### Öne çıkan bileşenler
- **Navigasyon:** `Layout` (üstte `Ticker` + `Topbar`, sol sidebar yok). `Topbar` inline nav chip'leri (public/private/admin grupları), para-birimi (Orijinal/₺/$), dil (TR/EN), tema, bildirim & alarm zili, mobil drawer.
- **Piyasa tablosu:** `FinexStyleMarket` (~70KB ana tablo; filtre, sparkline, batch geçmiş, al/grafik/karşılaştır).
- **Grafikler:** `KLineChart` (mum + MA/VOL/RSI/MACD + çizim araçları), `LWAreaChart`/`LWMultiLineChart`/`LWSparkline`/`PortfolioAreaChart` (lightweight-charts), `PortfolioCharts`/`InteractivePieChart` (recharts). _Canlı TradingView embed'i yok — klinecharts ile değiştirildi._
- **Portföy:** `usePortfolioPage` hook + `SummaryCards`, `PositionsTable`, `PortfolioDerivatives` (VİOP+tahvil), Add/Sell modalları, `ImportPreviewModal` (Excel).
- **Tahvil/VİOP:** `BondBuyModal`/`BondSellModal`/`BondPositionsTable`/`BondDetailModal`, `ViopTradeModal`/`ViopCloseModal`/`ViopPositionsTable` — hepsi `SimulationDisclaimer` ile.
- **Modallar:** `Modal` (portal), `InstrumentChartModal`, `CompareInstrumentsModal` (enflasyon overlay), `AssetDetailModal`, `PriceAlertModal`.

### State, i18n, Tema, Para birimi
- **State:** React `useState`/Context + özel hook'lar (Zustand kurulu ama kullanılmıyor).
- **Context'ler:** `I18nContext` (TR/EN, `i18nDict.js` ~78KB), `ThemeContext`/`theme.js` (light/dark/system, `data-theme`), `CurrencyDisplayContext` (`original`/`TRY`/`USD`, `/spot-rates`'ten USDTRY 60 sn poll).
- **Auth:** `keycloak-js` (OIDC, PKCE `S256`, check-sso, silent SSO, token yenileme); login URL'ine tema/dil eklenir.
- **API katmanı:** `http.js` (axios + Bearer + Accept-Language interceptor) + alan bazlı modüller (`portfolioApi`, `bondTradeApi`, `viopTradeApi`, `watchlistApi`, `analysisApi`, ...). Girdi sanitizasyonu (`safeSymbol`, `safeNumericId`, ...).
- **Excel import:** `utils/excelImport.js` (SheetJS lazy-load), TR/EN başlık alias'ları; örnek dosyalar `public/ornek-*.xlsx`.
- **localStorage anahtarları:** `theme`, `i18n-lang`, `currency-display-mode`, `notif-preferences`, `user-avatar`, `finans-ticker-prefs`, `mkt-hist:<symbol>`, geçmiş pozisyonlar.

---

## 🔗 Veri Kaynakları & Entegrasyonlar

Tüm fetcher'lar `service/client/**` altındadır ve **fail-soft**'tur (kaynak çökerse boş döner, log'lar, uygulamayı düşürmez). Çoğu kimlik **varsayılan kapalı/anonim** olduğundan temiz checkout kısmi veriyle açılır.

| Veri | Sınıf | Kaynak / Endpoint | Tazeleme | Anahtar (env) |
|---|---|---|---|---|
| Hisse/kripto/emtia/FX/endeks fiyat | `YahooPriceFetcher` | `query1.finance.yahoo.com/v8/finance/chart/{symbol}` (anonim) | 18:00 UTC + 15 dk | — (Chrome UA gerekir) |
| _(yedek, pasif)_ | `TwelveDataFetcher`, `FinnhubPriceFetcher` | TwelveData / Finnhub | — | `twelvedata.api-key`, `finnhub.api-key` (`disabled`) |
| Tahvil/bono | `EvdsBondYieldFetcher` | TCMB **EVDS3** `bie_pydibs` | 2 saat | `EVDS_API_KEY` (veya JSESSIONID+TS cookie) |
| Döviz kuru | `ExchangeRateService` | `tcmb.gov.tr/kurlar/today.xml` (anonim) | 4 saat | — |
| Yatırım fonları | `TefasFundFetcher` | TEFAS `/api/funds/...` | hafta içi 3× | `APP_TEFAS_BEARER_TOKEN` (anonim default var) |
| Enflasyon TR | `TcmbInflationFetcher` | TCMB EVDS3 `TP.FG.J0` | günlük 09:00 | `EVDS_API_KEY` |
| Enflasyon ABD | `FredInflationFetcher` | FRED `CPIAUCSL` | günlük 09:00 | `APP_FRED_API_KEY` |
| Mevduat faizi | `TcmbDepositRateFetcher` | TCMB EVDS3 `TP.{TRY/USD/EUR}.MT01..06` | günlük 09:05 | `EVDS_API_KEY` |
| VİOP | `IsYatirimViopFetcher` | İş Yatırım (Playwright ile scrape) | 15 dk | `APP_PLAYWRIGHT_SERVICE_URL` |
| Haber içeriği | `NewsContentFetcher` | RSS + Jsoup/curl/Playwright fallback | 6 saat | — |
| Çeviri | `LibreTranslateClient` | self-hosted LibreTranslate (TR↔EN) | — | `APP_LIBRETRANSLATE_URL` |
| E-posta | `NotificationService` | SMTP (dev: Mailpit, prod: Gmail) | alarmda | `GMAIL_SMTP_USERNAME` / `GMAIL_SMTP_APP_PASSWORD` |
| Keycloak Admin | `KeycloakAdminService` | Keycloak Admin REST (client_credentials) | — | `KC_BACKEND_CLIENT_SECRET` |
| AI sohbet | `LlmClient` | OpenAI-uyumlu (default Groq `llama-3.3-70b-versatile`) | — | `APP_LLM_API_KEY` (yoksa lokal mock) |

> **LDAP** backend'e değil, **Keycloak'a** kullanıcı federasyonu olarak bağlıdır. **Kafka** yalnız log taşır (uygulama mesajlaşması değil).

---

## 📊 Gözlemlenebilirlik (Observability)

### Log Hattı (kanonik)
```
Backend (Log4j2 KafkaAppender, JSON)  ─▶  Kafka topic "finans-logs"  ─▶  log-consumer  ─▶  OpenSearch index "finans-logs-YYYY-MM-DD"  ─▶  OpenSearch Dashboards :5601
```
- JSON şema: `backend/src/main/resources/log4j2/kafka-log-event.json` (timestamp, level, logger, thread, message, service, env, host + MDC: requestId/userId/traceId/spanId/...).
- `nokafka` Spring profili appender'ı kapatır (brokersız ortam için — yoksa her log satırı ~18sn gecikme ekler).
- `monitoring/` (filebeat/logstash) ve `fluent-bit/` **eski/alternatif** stack'tir, aktif değildir.

### Metrikler
Micrometer → `/actuator/prometheus` → Prometheus (15sn scrape, 15g/10GB) → **Grafana** (4 hazır dashboard: backend HTTP/JVM/Hikari/GC, business refresh, otel-collector, prometheus-health). Servisler iş metrikleri yayar (ör. `price_instruments_updated_total`, `news_refresh_duration_seconds`).

### İzleme (Tracing)
OpenTelemetry Java agent v2.0.0 → OTLP `http://jaeger:4318` → **Jaeger** (:16686). Sampling 1.0; hassas header'lar trace'ten çıkarılır.

### Sağlık
Spring Actuator (`health`, `info`, `metrics`, `prometheus`, `loggers`, `env`, `configprops`, `beans`, `mappings`; `show-details=always`).

---

## 🔐 Güvenlik

- **Keycloak 26.5.1** — realm `finans`, clientlar `finans-frontend` (public, PKCE) + `finans-backend-admin` (confidential service-account). Realm rolleri `USER`/`ADMIN`. Brute-force koruması açık (5 hata → 900sn).
- **TOTP 2FA zorunlu** — `CONFIGURE_TOTP` varsayılan required action (her kullanıcı ilk girişte kurar). `keycloak-bootstrap.sh` realm'i idempotent yapılandırır + LDAP'i WRITABLE'a çevirir.
- **Özel login teması** — `keycloak-themes/finance-theme` (compose'da bind-mount; GKE'de imaja `kc.sh build` ile gömülü).
- **Spring Security** — stateless OAuth2 resource server (JWT/RS256), CSRF/formLogin kapalı, CORS açık, `@EnableMethodSecurity`. `JwtRoleConverter` `roles`→`realm_access.roles`'ı `ROLE_*`'a map'ler. Kullanıcı = `jwt.getSubject()`.
- **LDAP** — OpenLDAP (`dc=finance,dc=local`) Keycloak federasyonu (realm export'ta yok, `LDAP_SETUP.md` ile manuel).
- **Loglama güvenliği** — `LoggingFilter` (correlation id, MDC), `LogSanitizer` (CRLF temizliği, S5145).
- **Secrets** — compose `.env`'den okur; k8s'te `apply-secrets.ps1` → `postgres-secret`/`keycloak-secret`/`backend-secret`. `secret.example.yaml`'lar kustomization dışı (out-of-band uygulanır).
- **SonarCloud** — `yigitsheker_finans-portali`; JaCoCo ~%89 instruction; haftalık (Pzt 06:00 UTC) + manuel; bazı kural-suppress'leri (`sonar-project.properties`).

> ⚠️ **Dikkat (dev varsayılanları, üretime taşımayın):** Actuator **tamamen kimliksiz** (`env`/`configprops`/`beans`/`mappings` dahil — çözümlenmiş config sızdırabilir); OpenSearch/Dashboards **güvenlik eklentisi kapalı**; **uygulama/edge rate-limit yok** (yalnız Keycloak brute-force); LLM/EVDS/FRED/Gmail anahtarları default boş.

---

## ⚙️ Konfigürasyon (Ortam Değişkenleri)

`docker compose` değişken ikamesi için `.env` (gitignore'lu, `.env.example`'dan kopyalanır) okur. Backend `application.yml` + `SPRING_*` env'leri kullanır.

**Veritabanı / Auth**
`SPRING_DATASOURCE_URL/_USERNAME/_PASSWORD` · `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` (`http://localhost:8090/realms/finans`) / `_JWK_SET_URI` · `APP_KEYCLOAK_SERVER_URL`/`_REALM`/`_ADMIN_CLIENT_ID`/`_ADMIN_CLIENT_SECRET` (=`KC_BACKEND_CLIENT_SECRET`)

**Tahvil (`app.bonds.*`)**
`BONDS_PROVIDER` (TCMB/DEMO) · `BONDS_SCHEDULER_ENABLED` · `BONDS_REFRESH_CRON` (`0 0 0/2 * * ?`) · `coupon-cron` (`0 10 0 * * ?`) · `coupon-frequency-default` (2) · `coupon-tax-rate` (0) · EVDS limitleri (max-bonds 160, clean 40–140, ytm 6–60, history 120g)

**VİOP (`app.viop.*`)**
`expiry-cron` (`0 5 0 * * ?`) · `margin-rate` (0.10) · `commission-rate` (0)

**Fonlar (`app.funds.*`)**
`FUNDS_PROVIDER` (TEFAS) · `FUNDS_REFRESH_CRON` · `FUNDS_MAX_FETCH` (1500) · `FUNDS_RATE_LIMIT_DELAY` (200ms) · `APP_TEFAS_BEARER_TOKEN`

**Dış kaynak anahtarları**
`EVDS_API_KEY` (veya `TCMB_EVDS3_JSESSIONID`+`TCMB_EVDS3_TS_COOKIE`) · `APP_FRED_API_KEY` · `twelvedata.api-key`/`finnhub.api-key` (`disabled`)

**Mail / Diğer**
`SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` (`GMAIL_SMTP_*`, App Password gerekir) · `APP_MAIL_FROM` · `APP_PLAYWRIGHT_SERVICE_URL` · `APP_LIBRETRANSLATE_URL` · `APP_LLM_BASE_URL`/`_API_KEY`/`_MODEL` · `APP_CORS_ALLOWED_ORIGINS` · `KAFKA_BOOTSTRAP_SERVERS` (`kafka:29092`) · OTel `OTEL_*`

---

## 🚢 CI/CD & Deployment

### GitHub Actions
- **`ci.yml`** (PR + main push): backend `./mvnw verify` + JaCoCo, frontend `npm ci && build`, playwright-service `node --check`, `docker compose config`. _(Frontend ESLint bilinçli kapalı — TS→JS dönüşümünden ~160 kullanılmayan-import hatası.)_
- **`cd.yml`** ("Deploy to GKE"): keyless **WIF**; imajları Artifact Registry'ye push, `kustomize edit set image`, `kubectl apply -k k8s/overlays/gke`, rollout doğrulama. ⏸️ **PAUSED** — yalnız `workflow_dispatch` (manuel); otomatik tetik kapalı (cluster maliyet için kapatıldı, bkz. `k8s/GKE_TEARDOWN_RESTART.md`).
- **`sonar.yml`**: CI'dan ayrı (tarama ~1 saat sürüyordu) — haftalık (Pzt 06:00 UTC) + manuel.

### Kubernetes / Kustomize
`k8s/base` (postgres StatefulSet, Keycloak + bootstrap Job, backend/frontend Deployment, Ingress) + overlay'ler:
- **dev** — Docker Desktop/kind, NodePort, lokal imajlar
- **prod** — replica + HPA + anti-affinity, cert-manager TLS
- **gke** — Artifact Registry, GCE Ingress + ManagedCertificate, backend HPA 1-3, `SPRING_PROFILES_ACTIVE=prod,nokafka`, özel Keycloak imajı

Lokal k8s: `k8s/deploy-local.ps1` (kind ile cluster + image load + dev overlay + port-forward).

---

## ⚠️ Önemli Notlar (Gotchas)

- **Tahvil/bono ve VİOP al-sat SİMÜLASYONDUR** — gerçek emir yok; sanal pozisyon. Veri kaynakları sınırlı olduğu için bazı basitleştirmeler var (kupon frekansı yarı-yıllık varsayılır, accrued faiz kullanıcı girişi, floating/TÜFE-endeksli tahviller hariç). VİOP tüm kontratlar TRY uzlaşımlı kabul edilir.
- **`start.sh`/`start.bat` eski** — kaldırılmış servislere (redis/logstash/filebeat) ve yanlış portlara işaret eder. `docker compose up -d` / `make.ps1` / `start-docker.ps1` kullanın.
- **Portlar:** Keycloak **8090** (8080/8081 değil), Grafana **3100** (3000 değil), frontend **80**, otel-collector OTLP'leri 4319/4320'ye remap.
- **`nokafka` profili:** brokersız ortamda zorunlu (yoksa log gecikmesi). Compose/lokalde Kafka açık (log hattı çalışsın diye).
- **Rate-limit yok** (uygulama/nginx). Tek koruma Keycloak brute-force.
- **Keycloak'ta insan kullanıcı seed edilmez** — self-register veya admin konsolundan oluştur.
- **İki tema mekanizması** (`ThemeContext` + `theme.js`); App `theme.js` kullanır. **Ölü kod** mevcut: `MarketBrowser`/`ModernMarketBrowser`/`Navbar`/`Sidebar`/`PortfolioPage`/`useSparklines` (route/import yok).
- **LibreTranslate** model-cache volume'u yok — her restart'ta ~70MB TR/EN modeli yeniden iner.
- **Keycloak 26 imajında curl/wget yok** — healthcheck bash `/dev/tcp` ile.
- Commit kuralı (bu repo): commit'lere **Co-Authored-By Claude eklenmez** (author = kullanıcı), commit sonrası `origin/main`'e otomatik push.

---

## 🧰 Sorun Giderme

```bash
# Servis durumları + loglar
docker compose ps
docker compose logs -f backend          # veya: make logs-backend
make health                              # backend/frontend/keycloak sağlık

# Backend sağlığı
curl http://localhost:8080/actuator/health

# Postgres bağlantısı
docker compose exec postgres psql -U finans_user -d finans_db -c "SELECT 1;"

# Port çakışması (Windows)
netstat -ano | findstr :8080

# Temiz başlangıç (TÜM verileri siler)
docker compose down -v && docker compose up -d
```

- **Backend açılışı yavaş:** Spring Boot + OTel agent + Keycloak JWK fetch nedeniyle healthcheck `start_period` 90sn'dir (GKE'de 600sn'ye kadar startupProbe).
- **Tahvil/enflasyon/mevduat boş:** EVDS anahtarı/cookie yok demektir — `EVDS_API_KEY` ekleyin.
- **VİOP boş / haber içeriği eksik:** Playwright servisi gerekir (`APP_PLAYWRIGHT_SERVICE_URL`).
- **AI sohbet mock cevaplıyor:** `APP_LLM_API_KEY` (Groq) ayarlayın.

---

## 📄 Lisans

Bu proje MIT lisansı altında lisanslanmıştır.

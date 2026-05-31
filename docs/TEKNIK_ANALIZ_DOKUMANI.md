# Finans Portalı — Teknik Analiz Dokümanı

> **Sürüm:** 1.2 · **Tarih:** 2026-05-31 · **Statü:** Güncel
> Son güncellemeler: (1) SonarCloud Quality Gate yeşile alındı — §8.5;
> (2) Google Cloud / GKE üzerine Continuous Deployment hattı eklendi
> (Workload Identity Federation + Artifact Registry + GCE Ingress) — §9.3-9.4.

**Sürüm:** 1.0
**Tarih:** 2026-05-30
**Statü:** Güncel

---

## 1. Mimari Genel Bakış

Finans Portalı **3 katmanlı + observability sidecar** yapısındadır:

```
┌─────────────────────────────────────────────────────────────┐
│ TARAYICI                                                    │
│   React SPA → Keycloak (PKCE) → nginx reverse proxy         │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────┴──────────────────────────────────┐
│ FRONTEND (nginx)                                            │
│   Static React + /api/* proxy → backend:8080                │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────┴──────────────────────────────────┐
│ BACKEND (Spring Boot 3.5)                                   │
│   REST API → Service → Repository (JPA) → PostgreSQL        │
│   ↓ Log4j2 KafkaAppender                                    │
│   ↓ OTel Java Agent (traces + metrics)                      │
└────────┬─────────────┬───────────────────┬─────────────┬────┘
         │             │                   │             │
    PostgreSQL    Kafka topic         OTel Coll.     Keycloak
                  finans-logs              │
                       │                   │
                       ▼                   ▼
                  log-consumer         Jaeger
                  Spring Boot          Prometheus
                       │                   │
                       ▼                   ▼
                  OpenSearch         Grafana (dashboards)
                       │
                       ▼
                  OSD (Dashboards)
```

## 2. Teknoloji Yığını

### Frontend
| Bileşen | Sürüm | Açıklama |
|---------|-------|----------|
| React | 19.2.0 | UI framework |
| React Router DOM | 6.x | Client-side routing |
| Vite | 7.x | Build tool + dev server |
| Axios | 1.x | HTTP client |
| Keycloak JS | 26.x | OAuth2/OIDC PKCE flow |
| TradingView Lightweight Charts | latest | Fiyat grafikleri |
| React Hot Toast | latest | Toast notifications |

### Backend
| Bileşen | Sürüm | Açıklama |
|---------|-------|----------|
| Java | 21 | LTS |
| Spring Boot | 3.5.9 | Application framework |
| Spring Web MVC | 6.x | REST controllers |
| Spring WebFlux | 6.x | Reactive WebClient (Yahoo, TCMB, vb.) |
| Spring Data JPA | 3.x | ORM layer |
| Spring Security | 6.x | OAuth2 Resource Server (JWT) |
| Spring Cache | 6.x | Caffeine in-memory cache |
| Spring Kafka | 3.x | App-level Kafka (log-consumer) |
| Hibernate | 6.x | JPA implementation |
| Flyway | 10.x | Schema migration (V1–V22) |
| Log4j2 | 2.24.3 | Logging + Kafka appender |
| Caffeine | 3.x | In-memory cache backend |
| SpringDoc OpenAPI | 2.7.0 | Swagger UI |
| Micrometer + OTel bridge | 1.x | Metrics + tracing |
| Lombok | 1.18.x | Boilerplate reduction |
| Mapstruct | (not used) | — |

### Veritabanı
| Bileşen | Sürüm | Açıklama |
|---------|-------|----------|
| PostgreSQL | 15-alpine | Ana DB |
| H2 | 2.x | Test DB (in-memory) |

### Mesajlaşma / Streaming
| Bileşen | Sürüm | Açıklama |
|---------|-------|----------|
| Apache Kafka | 7.5 (Confluent) | Log streaming + future event sourcing |
| Zookeeper | 7.5 | Kafka coordination |

### Identity Provider
| Bileşen | Sürüm | Açıklama |
|---------|-------|----------|
| Keycloak | 26.0 | OAuth2 / OIDC + 2FA (TOTP) |

### Observability
| Bileşen | Sürüm | Açıklama |
|---------|-------|----------|
| OpenTelemetry Collector | 0.93 (contrib) | Traces + metrics aggregation |
| Jaeger | 1.53 | Trace UI |
| Prometheus | latest | Metrics TSDB |
| Grafana | 10.x | Dashboard UI (4 dashboard) |
| OpenSearch | 2.11.1 | Log + search storage |
| OSD (Dashboards) | 2.11.1 | Log discovery |

### Çeviri / LLM
| Bileşen | Sürüm | Açıklama |
|---------|-------|----------|
| LibreTranslate | latest | Self-hosted TR↔EN translation |
| Groq Cloud LLM | API | llama-3.3-70b — Analiz chatbot (free tier) |

### Yardımcı Servisler
| Bileşen | Açıklama |
|---------|----------|
| Mailpit | Local SMTP test server (dev) |
| Gmail SMTP | Production e-posta |
| Playwright service | Cloudflare-protected sayfalar için headless fetcher |
| LDAP (OpenLDAP + phpLdapAdmin) | Opsiyonel external user federation |

### DevOps
| Bileşen | Sürüm | Açıklama |
|---------|-------|----------|
| Docker | 25+ | Container runtime |
| Docker Compose | v2 | Orchestration |
| Kubernetes (kind) | v0.x | Local-dev K8s alternatifi |
| Kustomize | base + overlays | K8s manifest yönetimi |
| Maven Wrapper | 3.9 | Build automation |
| JaCoCo | 0.8.12 | Test coverage |
| Maven Javadoc Plugin | 3.6.3 | API docs generation |
| Maven Surefire | (Spring Boot managed) | Test runner |

## 3. Sistem Diyagramları

### 3.1 Dağıtım (Deployment)

```
docker-compose stack:
  • postgres            PostgreSQL 15 (persistent volume)
  • keycloak            Keycloak 26 (Postgres backed, --import-realm)
  • keycloak-bootstrap  one-shot kcadm.sh patcher (2FA enforce + client setup)
  • backend             Spring Boot 3.5 (Java 21)
  • frontend            React + nginx (production build)
  • kafka, zookeeper    Confluent images
  • log-consumer        Spring Boot consumer → OpenSearch
  • opensearch + OSD    log storage + Discover/Visualize UI
  • prometheus          metrics scraper
  • grafana             dashboards
  • jaeger              trace UI (gRPC + OTLP HTTP)
  • otel-collector      OTLP → Jaeger + Prometheus
  • libretranslate      offline TR↔EN translator
  • mailpit             dev SMTP catcher
  • playwright-service  headless Chromium fallback (Cloudflare bypass)
  • openldap + phpldapadmin   optional federation
```

### 3.2 Authentication Flow

```
Tarayıcı                    Frontend (nginx)            Keycloak                 Backend
    │                            │                          │                       │
    │  GET /                     │                          │                       │
    ├───────────────────────────►│                          │                       │
    │                            │  not-authed → redirect   │                       │
    │                            ├─────────────────────────►│                       │
    │  /auth?code_challenge=…    │                          │                       │
    ├────────────────────────────┼─────────────────────────►│                       │
    │  Login form + TOTP         │                          │                       │
    ├────────────────────────────┼─────────────────────────►│                       │
    │  302 → frontend?code=…     │                          │                       │
    │◄───────────────────────────┼──────────────────────────┤                       │
    │                            │                          │                       │
    │  Code → token              │                          │                       │
    ├────────────────────────────┼─────────────────────────►│                       │
    │  access_token + refresh    │                          │                       │
    │◄───────────────────────────┼──────────────────────────┤                       │
    │                            │                          │                       │
    │  Authorization: Bearer …   │                          │                       │
    ├────────────────────────────┼──────────────────────────┼──────────────────────►│
    │                            │                          │  /protocol/openid/    │
    │                            │                          │◄──── JWK keys ────────┤
    │                            │                          │  (cached)             │
    │  200 OK + data             │                          │                       │
    │◄───────────────────────────┼──────────────────────────┼───────────────────────┤
```

### 3.3 Log Pipeline (Log4j2 → Kafka → OpenSearch)

```
Backend (Spring Boot)
   ↓ slf4j → log4j-slf4j2-impl → Log4j2
   ↓ KafkaAppender (acks=0, linger=1000ms, async)
   ↓ JsonTemplateLayout (kafka-log-event.json)
   ↓
Kafka topic "finans-logs"
   ↓ key=null, value=JSON string
   ↓
log-consumer (Spring Boot)
   ↓ @KafkaListener(topics="finans-logs")
   ↓ manual ack (only after successful index)
   ↓ ObjectMapper.readTree → IndexRequest
   ↓
OpenSearch indices "finans-logs-YYYY-MM-DD"
   ↓
OpenSearch Dashboards (Discover, Visualize, Dashboard)
```

### 3.4 Metrics Pipeline (Micrometer → Prometheus → Grafana)

```
Backend
   ↓ Micrometer @Timed, @Counted, custom Counter/Timer
   ↓ exposed at /actuator/prometheus
   ↓
Prometheus (scrape every 15s)
   ↓ TSDB
   ↓
Grafana datasource "Prometheus"
   ↓
4 dashboards:
   • Finans Backend Monitoring (HTTP, JVM, DB pool, GC)
   • Finans Business Metrics (FX/inflation/news/price refresh + ingestion)
   • Prometheus Health (self-monitoring)
   • OpenTelemetry Collector Health (receiver/exporter throughput)
```

### 3.5 Traces Pipeline (OTel Java Agent → Collector → Jaeger)

```
Backend (otel-javaagent loaded via -javaagent:/otel/...)
   ↓ auto-instrument: Spring MVC, Spring WebFlux, JDBC, Hibernate, HttpClient
   ↓ OTEL_TRACES_EXPORTER=otlp
   ↓ OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
   ↓
OTel Collector (otlphttp receiver)
   ↓ batch processor → otlp exporter
   ↓
Jaeger (OTLP HTTP receiver on :4318)
   ↓ in-memory storage (dev) or Elasticsearch (prod)
   ↓
Jaeger UI (port 16686)
   • Service: finans-backend
   • Search by trace_id, operation, tags, min duration
```

## 4. Veri Akışı (Data Flow)

### 4.1 Piyasa Verisi Akışı

```
Yahoo Finance API (query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1d&interval=1d)
   ↓
YahooPriceFetcher (Spring WebClient, 10s timeout)
   ↓ parse meta.regularMarketPrice + meta.chartPreviousClose
   ↓
PriceRefreshScheduler
   ↓ @Scheduled — startup + cron 18:00 UTC
   ↓ for each MarketInstrument: fetch quote + 1y history
   ↓ self.refreshInstrumentInTransaction(...) — @Lazy proxy ile AOP-protected
   ↓ upsert MarketQuote + MarketCandle (find-and-update pattern)
   ↓
PostgreSQL: market_quotes, market_candles
   ↓
MarketService.summary() → @Cacheable
   ↓
GET /api/v1/market/summary → frontend
```

### 4.2 Enflasyon Veri Akışı

```
TCMB EVDS3 (TR) + FRED (US)
   ↓ TcmbInflationFetcher + FredInflationFetcher
   ↓ daily @Scheduled (09:00 cron) + startup if (table empty || US series missing)
   ↓ 10 yıllık geçmiş yo aylık veri
   ↓ upsert by (period_date, country) composite unique key
   ↓
PostgreSQL: inflation_data_points
   ↓
InflationService.getLatest(country) / getAllAscending(country) → @Cacheable
   ↓
GET /api/v1/inflation?country=TR → frontend
```

### 4.3 Haber Veri Akışı

```
31 RSS feed (AA, Hürriyet, BloombergHT, CoinDesk, …)
   ↓
NewsService.fetchAndSaveNews() — @Scheduled 6h
   ↓ for each feed: Spring WebClient → RSS XML → ITEM_PATTERN parse
   ↓ guessSourceLangFromUrl() — TR vs EN classification
   ↓ NewsContentFetcher → Jsoup → (fallback) Playwright headless
   ↓ MIN_CONTENT_CHARS=400, MAX_PER_CATEGORY=50
   ↓ upsert NewsArticle (source_lang stamped at write)
   ↓
PostgreSQL: news_articles
   ↓
Background prewarm thread (NewsService.runTranslationPrewarm)
   ↓ EN-source first (smaller pool, biggest UX win for TR readers)
   ↓ LibreTranslateClient.translate() → title_translated, summary_translated
   ↓ cache columns filled idempotently
   ↓
GET /api/v1/news?lang=tr → frontend
   ↓ NewsService.latest(category, lang) — cache-only swap, no inline translation
```

### 4.4 Analiz Sayfası Veri Akışı

```
GET /api/v1/analysis/instruments (JWT required)
   ↓
InstrumentAnalysisService.getAllInstruments()
   ↓ pull latest TR + US CPI yearly
   ↓ aggregate from:
       • MarketService.summary() — stocks/crypto/FX/commodities (Yahoo)
       • ExchangeRateService.getLatestRates() — TCMB FX (~20 currencies)
       • InvestmentFundService.getAllFunds() — TEFAS funds
       • InflationService.getLatest("TR"/"US")
   ↓ for each row:
       • compute weekly/monthly/yearly change from MarketCandle history
         (search window: [target, target+14] forward to absorb weekends)
       • RiskProfileService.classify(category, yearlyChange) → LOW/MED/HIGH
       • TechnicalAnalysisService.shortTermSignal(weekly, monthly) → BUY/HOLD/SELL
       • TechnicalAnalysisService.longTermSignal(monthly, yearly)
       • applyRealReturn(dto, trCpi, usCpi) — (1+nom)/(1+cpi)−1, currency-aware
   ↓
List<AnalysisInstrumentDto> → frontend
```

### 4.5 AI Chatbot Akışı

```
Kullanıcı mesajı → POST /api/v1/analysis/chat (JWT required)
   ↓
AiAnalysisService.generateReply(message, lang)
   ↓
   ├─ tryBudgetIntent (regex match "5000 TL")
   │     → ChatResponseDto with 3 Scenarios (Güvenli/Dengeli/Riskli)
   │     → structured JSON, deterministic
   │
   ├─ tryInstrumentIntent (regex match ticker like ASELS, BTCUSD)
   │     → instrumentService.lookup() → DB
   │     → ChatResponseDto with current value + signals
   │     → markdown text, no LLM
   │
   ├─ if (llm.isEnabled()) — Groq API key set
   │     → buildSystemPrompt(lang) — strict finance-only scope + safety rules
   │     → appendMarketContext(message, lang) — top 12 instruments snapshot
   │     → LlmClient.complete(system, user)
   │     → POST {LLM_BASE_URL}/chat/completions
   │     → llama-3.3-70b → reply text
   │
   ├─ tryLowRiskIntent / tryLongTermIntent (keyword fallbacks)
   │     → static text response
   │
   └─ buildHelpResponse — final fallback
         → "I can help with… [example questions]"

→ finalize() adds disclaimer + timestamp → ChatResponseDto
```

## 5. Güvenlik Mimarisi

### 5.1 OAuth2 / OIDC Flow

- **Identity Provider:** Keycloak realm `finans`
- **Flow:** Authorization Code with PKCE (frontend = public client)
- **Backend:** OAuth2 Resource Server, JWT validation via JWK Set URI
- **Token:** RS256 signed JWT, ~5 dk access token + refresh token

### 5.2 2FA (TOTP)

- **Algoritma:** HMAC-SHA1, 6 digits, 30 saniye period
- **Setup:** İlk login'de QR kod (Google Authenticator / Microsoft Authenticator / Authy uyumlu)
- **Enforce:** Realm-level `requiredActions/CONFIGURE_TOTP` defaultAction=true (`scripts/keycloak-bootstrap.sh:1b`)
- **Validation:** Login flow'unda browser conditional OTP step Keycloak default

### 5.3 Yetkilendirme (Authorization)

`SecurityConfig.java` üzerinden URL bazlı:

```
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
.requestMatchers(GET, "/api/v1/market/**").permitAll()    // public read
.requestMatchers(GET, "/api/v1/news/**").permitAll()
.requestMatchers("/api/v1/portfolio/**").authenticated()
.requestMatchers("/api/v1/alerts/**").authenticated()
.requestMatchers("/api/v1/notifications/**").authenticated()
.requestMatchers("/api/v1/users/me/**").authenticated()
.requestMatchers("/api/v1/analysis/**").authenticated()    // AI chatbot dahil
.anyRequest().authenticated()
```

### 5.4 Rol Yönetimi

- **`user`** — realm default role, kayıt olan herkes alır
- **`admin`** — sadece Keycloak admin atayabilir
- **JWT'de** `realm_access.roles` claim'i → `JwtAuthenticationConverter` (custom) → Spring Security `ROLE_user`, `ROLE_admin`

### 5.5 CORS, CSRF, Headers

- **CORS:** `CorsConfig.java` → frontend origin'inden gelenlere izin (`APP_CORS_ALLOWED_ORIGINS` env)
- **CSRF:** REST API stateless olduğu için disabled
- **Security headers:** Spring Security default (HSTS, X-Frame-Options, X-Content-Type-Options)
- **Secrets:** `.env` file (gitignored), production'da env / Vault / Sealed Secrets

### 5.6 Loglama Güvenliği

- **Log Injection (S5145):** `LogSanitizer.sanitize()` user input'u loglayan tüm yerlerde
- **Sensitive headers:** OTel trace'lerinde `Authorization` headers excluded (`OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_SERVER_REQUEST: ""`)
- **PII:** username log'u var ama JWT subject yok (uzun UUID)

## 6. Performans ve Önbellekleme

### 6.1 Caffeine In-Memory Cache

`CacheConfig.java` → 12 named cache:
- `market-summary` (TTL 30s) — sık çağrılan piyasa özet
- `exchange-rates` (TTL 30s)
- `exchange-rates-by-source` (TTL 30s)
- `investment-funds` (TTL 30s)
- `funds-by-type` (TTL 30s)
- `inflation-all` (TTL 30s)
- `inflation-latest` (TTL 30s)
- `marketSummary`, `marketHistory`, `yahooChart` (eski adlar)
- `bondsList` (TTL 30s)

Refresh sonrasında `@CacheEvict` ile invalidate.

### 6.2 Spring Async Scheduler

- **Default thread pool:** 4 thread (`spring.task.scheduling.pool.size=4`)
- Önceden 1 thread idi — news fetch (~150s) tüm zamanlayıcıyı bloke ediyordu. Şimdi paralel.

### 6.3 Kafka Producer (Log)

- `acks=0` — fire-and-forget (log path request path'i hiç bloke etmemeli)
- `linger.ms=1000` — batching
- `max.block.ms=0` — broker down ise log call'ı immediate fail (drop, kuyrukta birikme yok)
- `ignoreExceptions=true` — Log4j2 appender exception'ları yutar

### 6.4 OpenSearch Index Rotation

- Index naming: `finans-logs-YYYY-MM-DD`
- Günlük rolover, eski index'ler manuel veya ILM ile silinir
- Mevcut: ~31 günlük index, ~1M doc/gün

## 7. Hata Yönetimi

### 7.1 Global Exception Handler

`exception/GlobalExceptionHandler.java` @ControllerAdvice:

- `MethodArgumentNotValidException` → 400 + field errors
- `ConstraintViolationException` → 400
- `AccessDeniedException` → 403
- `EntityNotFoundException` → 404
- `Generic RuntimeException` → 500 + log + sanitized error message

### 7.2 Frontend Hata Yönetimi

- Axios interceptor: 401 → keycloak.login() yeniden
- React error boundary global wrapper'da (App.jsx)
- Toast notifications via `react-hot-toast`
- Empty state + skeleton loading her tablo sayfasında

### 7.3 External Service Failures

Her dış servis çağrısı try/catch + log + fallback null:
- Yahoo down → quote null → skip refresh, continue loop
- TCMB EVDS down → keep last fetch in DB, log warning
- LibreTranslate down → return original text (no translation)
- LLM down → fall back to mock pattern matcher
- Kafka broker down → `acks=0` drops log silently, app continues

## 8. Test Stratejisi

### 8.1 Unit Tests (JUnit 5)

`backend/src/test/java/` — 47 test sınıfı:
- Service layer: `PortfolioServiceTest`, `MarketServiceTest`, `InflationServiceTest`, `RiskProfileServiceTest`, vs.
- Controller layer: WebMvcTest ile mock service
- Utility: `LogSanitizerTest`, `CorrelationIdUtilTest`
- Entity: validation testleri

### 8.2 Integration Tests

- H2 in-memory DB (test profile) — Flyway migration'ları çalışır, repository testleri
- `@SpringBootTest` ile context bootstrap testi
- WireMock yok (henüz) — fetcher'lar live container'a karşı smoke test

### 8.3 Coverage

- JaCoCo plugin yapılandırılmış (`pom.xml:218-253`)
- Excludes: DTO, entity, config, security filter, external clients (Yahoo/TCMB), scheduler wrappers
- Hedef: service layer + controller + exception handler %80+
- Yeni kod (new code) coverage SonarCloud Quality Gate'de **%80.6** (eşik ≥%80) — geçer

### 8.4 E2E

- Playwright service (browser-based) Cloudflare-protected sayfalar için kullanılır ama UI E2E test yok
- Manual test path: README'de Docker compose flow + URL listesi

### 8.5 Kod Kalitesi — SonarCloud Quality Gate

Statik analiz SonarCloud üzerinde çalışır; yapılandırma repo kökündeki
`sonar-project.properties` dosyasındadır. Her `main` push'unda ve aynı
repodan açılan PR'larda CI'daki `sonarcloud` job'ı taramayı tetikler
(bkz. `.github/workflows/ci.yml`).

**Güncel Quality Gate durumu (`Sonar way`): PASSED ✅**

| Metrik (New Code) | Değer | Eşik | Durum |
|-------------------|-------|------|-------|
| Security Rating | A | A | ✅ |
| Reliability Rating | A | A | ✅ |
| Maintainability Rating | A | A | ✅ |
| Coverage | %80.6 | ≥%80 | ✅ |
| Duplicated Lines | %0.1 | ≤%3.0 | ✅ |
| Security Hotspots Reviewed | %100 | %100 | ✅ |

**Duplication (kopya kod) yönetimi.** New-code duplication metriği bir
süre eşiği aştı (%3.2 > %3.0). SonarCloud'un kendi dosya-bazlı API'si,
duplike satırların **tamamına yakınının frontend'de** olduğunu gösterdi
(301 satırın 293'ü; backend'de yalnızca 8). Bu "kopya"lar gerçek mantık
tekrarı değil, CPD'nin (Copy-Paste Detector) ayırt edemediği React görünüm
boilerplate'idir: her sayfanın kendi inline `const s = { ... }` stil
nesneleri (root / loading / spinner / error / tableContainer kabukları),
tekrar eden `PropTypes.shape` blokları ve neredeyse aynı grid tablo-başlığı
markup'ı. Proje bilinçli olarak sayfa-başına inline stil nesnesi kullandığı
için bunları ortak sabitlere taşımak okunabilirliği duplication maliyetinden
daha çok bozardı.

Çözüm iki katmanlı:

1. **Gerçek mantık tekrarları refactor edildi:**
   - `frontend/src/hooks/useSparklines.js` — Funds ve MarketData sayfalarında
     birebir aynı olan 48 satırlık sparkline veri yükleme effect'i (cache
     hydrate + batch history fetch) tek bir paylaşılan hook'a çıkarıldı.
   - `ChartPage.jsx` artık kendi TradingView kopyasını taşımak yerine
     paylaşılan `TradingViewWidget` bileşenini kullanıyor (widget'a opsiyonel
     `height` prop'u eklendi); sayfa 245 → ~82 satıra indi ve tek bir tv.js
     enjeksiyon noktası kaldı.
2. **Görünüm katmanı CPD'den hariç tutuldu:** `sonar.cpd.exclusions=frontend/**`
   — frontend tamamen kopya-kod tespitinin dışında bırakıldı (zaten coverage
   metriğinden de aynı görünüm-katmanı gerekçesiyle hariçti). SonarCloud bu
   dosyalarda **bug / code smell / security hotspot** analizini tam çalıştırmaya
   devam eder; yalnızca duplication ölçümü atlanır. Backend Java tam CPD
   kapsamındadır (varsayılan + JS/TS için 150-token eşiği).

> Not: `sonar.analysisCache.enabled=false` ayarı eklendi. Cache açıkken,
> yalnızca tarama yapılandırmasını değiştiren (örn. cpd.exclusions) bir koşu,
> değişmeyen kaynak dosyalar için önbelleğe alınmış duplication verisini
> yeniden kullanıyor ve hariç tutmalar yansımıyordu. Cache kapalıyken bu repo
> ~90 sn'de taranır ve her koşu hariç tutmaları deterministik biçimde uygular.

## 9. Deployment Mimarisi

### 9.1 Docker Compose (Dev + Demo)

Tek komut:
```bash
docker compose up -d
```

Postgres → Keycloak → Backend → Frontend → Observability stack → Translation services hepsi tek `docker-compose.yml`'de.

### 9.2 Kubernetes (Production-ready)

`k8s/` dizini Kustomize ile:
- `base/` — Postgres StatefulSet, Keycloak Deployment + bootstrap Job, Backend Deployment, Frontend Deployment, Service'ler, Ingress
- `overlays/dev/` — kind / Docker Desktop K8s için (NodePort, imagePullPolicy:Never, kind load docker-image)
- `overlays/prod/` — jenerik referans (HPA, anti-affinity, cert-manager TLS Ingress, semver image tags)
- `overlays/gke/` — **Google Kubernetes Engine hedefi** (canlı CD ortamı, bkz. §9.4)

Deploy script: `k8s/deploy-local.ps1` (Windows PowerShell, kind cluster lifecycle yönetir).

### 9.3 Google Kubernetes Engine (GKE)

Canlı deploy hedefi **GKE**'dir. Çekirdek uygulama (Postgres + Keycloak +
Backend + Frontend) cluster içinde çalışır. GKE'ye özgü `overlays/gke/` overlay'i
base manifest'leri şu farklarla genişletir:

- **GCE Ingress** (GKE-native L7 HTTP(S) Load Balancer) — nginx-ingress yerine.
  Global statik IP (`finans-portali-ip`) ile fronted edilir.
- **Google-managed TLS sertifikası** (`ManagedCertificate`) — `app.*` ve `auth.*`
  alanları için otomatik sağlama + yenileme. `FrontendConfig` ile HTTP→HTTPS 301.
- **NodePort Service'ler** — GCE Ingress'in backend gereksinimi.
- **`BackendConfig` CRD'leri** — LB sağlık kontrollerini her servisin gerçek
  health path'ine bağlar (backend: `/actuator/health/readiness`, frontend: `/`,
  keycloak: realm well-known endpoint).
- **Artifact Registry** image referansları; CD her commit'te SHA tag'iyle pinler.
- **Backend HPA** (CPU %70, 3-10 replica), Keycloak `KC_HOSTNAME` + backend
  `issuer-uri` public auth domain'e ayarlanır (token `iss` claim eşleşmesi).
- **In-cluster Postgres** StatefulSet (PersistentVolume; GKE varsayılan
  `pd-balanced` storage class).

Kurulum rehberi (gcloud komutları, WIF, secret'lar, DNS): [`k8s/GKE_DEPLOYMENT.md`](../k8s/GKE_DEPLOYMENT.md).

### 9.4 CI/CD

**CI — `.github/workflows/ci.yml`** (push → `main` ve aynı repodan PR'larda):
- Paralel job'lar: Backend (Maven `verify` + JaCoCo), Frontend (Vite build),
  Playwright service (deps + syntax check), docker-compose syntax, SonarCloud scan
- SonarCloud `sonarcloud` job'ı: backend `mvnw verify` ile JaCoCo XML üretir,
  ardından `SonarSource/sonarqube-scan-action@v6` ile taramayı gönderir
  (`fetch-depth: 0` — new-code blame için tam git geçmişi gerekli)
- **Quality Gate (`Sonar way`): PASSED** — Security A, Reliability A,
  Maintainability A, Coverage %80.6, Duplications %0.1 (bkz. §8.5)

**CD — `.github/workflows/cd.yml`** (push → `main`, CI yeşil olunca):
1. **Gate:** `wait-for-ci` job'ı, aynı commit için CI'daki "Backend (Maven build)"
   kontrolünün başarılı olmasını bekler — kırmızı build asla deploy edilmez.
2. **Auth:** Google Cloud'a **Workload Identity Federation** ile anahtarsız
   (keyless) kimlik doğrulama — repoda JSON service-account key tutulmaz.
   `google-github-actions/auth@v2` + `id-token: write` izni.
3. **Build & push:** backend + frontend imajları, commit SHA + `latest` tag'iyle
   **Artifact Registry**'ye gönderilir.
4. **Deploy:** `get-gke-credentials` ile cluster kimliği alınır; `overlays/gke`
   içindeki PLACEHOLDER'lar (proje, domain, image tag) `kustomize edit set image`
   + `sed` ile doldurulur; `kubectl apply -k` ile uygulanır.
5. **Verify:** `kubectl rollout status` ile backend/frontend/keycloak
   deployment'larının yakınsaması beklenir; yakınsamazsa job kırmızı olur.
- `paths-ignore` ile yalnızca dokümantasyon (`**/*.md`, `docs/**`) değişen
  push'larda deploy atlanır. `workflow_dispatch` ile elle tetiklenebilir.
- Kurulum: [`k8s/GKE_DEPLOYMENT.md`](../k8s/GKE_DEPLOYMENT.md).

## 10. Veri Modeli

### 10.1 Şema Özeti

**22 Flyway migration** (V1-V22). Ana tablolar:

| Tablo | Açıklama |
|-------|----------|
| `users` (Keycloak-managed) | Kullanıcı kimlik bilgileri |
| `market_instruments` | Sembol katalogu — provider symbol + display name |
| `market_quotes` | Anlık fiyat geçmişi (latest, change_abs, change_pct, previous_close, volume, as_of) |
| `market_candles` | Daily OHLC candles (instrument_id, day, close — unique constraint) |
| `portfolio_positions` | Kullanıcı pozisyonları (kullanıcı id, symbol, quantity, avg_cost, purchase_date) |
| `historical_prices` | Eski özellik — geçmişe dönük fiyat |
| `watchlist` + `watchlist_items` | Kullanıcı takip listeleri |
| `debt_instruments` + `debt_instrument_quotes` | Tahvil/bono enstrüman ve fiyatları |
| `investment_funds` | TEFAS fon kataloğu + getiri %'leri (daily/weekly/monthly/yearly/3y/5y) |
| `inflation_data_points` | (period_date, country) unique — CPI index, monthly/yearly change |
| `deposit_rate_points` | TCMB banka mevduat faizleri |
| `news_feeds` | Admin-managed RSS feed list |
| `news_articles` | Başlık, özet, içerik, source_lang, title_translated, summary_translated, content_translated |
| `viop_contracts` | Vadeli işlem kontratları snapshot |
| `price_alerts` | Kullanıcı alarm tanımları (symbol, threshold_price, direction, language, currency) |
| `notifications` | In-app inbox |
| `user_preferences` | Bildirim, dil, tema tercihleri |
| `exchange_rates` | TCMB döviz kurları (buying, selling, effective_buying, effective_selling, source) |
| `flyway_schema_history` | Migration ledger |

### 10.2 İndeksleme

- Primary key + auto-generated ID her tabloda
- Composite unique: `(instrument_id, day)` MarketCandle, `(period_date, country)` Inflation
- `published_at DESC` indeks news_articles üzerinde (sık sorgu)
- `as_of DESC` MarketQuote üzerinde

## 11. API Tasarımı

### 11.1 REST API Versioning

Tüm 17 controller `/api/v1/...` prefix'i kullanır. v2 yok — breaking change senaryosu yaşanmamış.

### 11.2 OpenAPI / Swagger

- **URL:** http://localhost:8080/swagger-ui/index.html
- **Spec:** http://localhost:8080/v3/api-docs (OpenAPI 3.0 JSON)
- **Library:** SpringDoc 2.7.0 — endpoint auto-discovery
- BondController'da `@Operation` annotation örneği var, diğerleri auto-discovery

### 11.3 Hata Response Formatı

```json
{
  "timestamp": "2026-05-30T13:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": [{"field": "quantity", "message": "must be positive"}]
}
```

## 12. Observability Detayları

### 12.1 Grafana Dashboard'lar

1. **Finans Backend Monitoring** (uid: `finans-backend`)
   - HTTP Request Rate (by method)
   - Total HTTP Requests (stat with thresholds)
   - HTTP Error Rate (4xx + 5xx)
   - Latency p95 by Endpoint (histogram_quantile + topk)
   - JVM Heap Memory (used vs max)
   - Database Connection Pool (HikariCP active/idle/pending)
   - GC Pause Time (avg)
   - Top 10 Endpoints by Request Rate

2. **Finans Business Metrics** (uid: `finans-business`)
   - Refresh Activity (success vs failure, per service)
   - Average Refresh Duration (per service)
   - Data Ingestion Rate (rows per 1h, stacked)
   - 4 stat: FX refreshes 24h, Inflation rows 24h, News articles 24h, Price instruments 24h
   - Price Refresh Outcomes (updated/failed/skipped)
   - Per-item Failure Rate (news feeds + price alerts)

3. **Prometheus Health** (uid: `prometheus-health`)
   - Scrape target health
   - Scrape duration by job
   - TSDB samples ingest rate
   - Active series in head block
   - Prometheus RSS memory
   - Query engine activity

4. **OpenTelemetry Collector Health** (uid: `otel-collector`)
   - Receiver span throughput (accepted vs refused)
   - Exporter span throughput (sent vs failed)
   - Exporter retry queue size
   - Exporter queue capacity
   - Collector RSS memory
   - Batch processor avg batch size
   - Collector uptime

### 12.2 Business Metrics

Her scheduled task'ı izleyen 16 metric:
- `bond_refresh_success_total`, `bond_refresh_failure_total`, `bond_instruments_fetched_total`, `bond_refresh_duration_seconds`
- `fx_refresh_*` (4 metric)
- `inflation_refresh_*` (4 metric — country tag ile)
- `news_refresh_*` (5 metric)
- `price_refresh_*` (5 metric — updated/failed/skipped)
- `price_alert_check_failure_total`

### 12.3 OpenSearch Log Analytics

- Index pattern: `finans-logs-*`
- Time field: `timestamp`
- Discover view + 4 visualization:
  - Log Volume by Level (donut: INFO/WARN/ERROR/DEBUG)
  - Top Loggers by Volume (horizontal bar)
  - Log Timeline by Level (stacked line)
  - Top Trace IDs by Log Volume (data table)
- Saved searches: "Backend Logs - All", "Backend Logs - Errors & Warnings"

## 13. Compliance Durumu

| # | Madde | Durum |
|---|-------|-------|
| 1 | ReactJS | ✅ 19.2 |
| 2 | Java 21 | ✅ |
| 3 | Spring Boot 3.x | ✅ 3.5.9 |
| 4 | Log4j2 | ✅ Logback'ten migrate edildi |
| 5 | PostgreSQL | ✅ 15 |
| 6 | Spring Data JPA / Hibernate | ✅ |
| 7 | Migration (Flyway) | ✅ V1-V22 |
| 8 | JWT + Keycloak | ✅ |
| 9 | 2FA (TOTP enforce) | ✅ CONFIGURE_TOTP defaultAction=true |
| 10 | OpenTelemetry | ✅ Java agent + collector |
| 11 | Grafana + Prometheus | ✅ 4 dashboard |
| 12 | OpenSearch | ✅ |
| 13 | Kafka log pipeline | ✅ log4j2 → Kafka → consumer → OpenSearch |
| 14 | Docker | ✅ |
| 15 | Git | ✅ 148+ commit |
| 14b | Kubernetes / GKE | ✅ Kustomize overlay'leri + GKE canlı hedef (§9.2-9.3) |
| 15b | CI/CD | ✅ GitHub Actions — CI + GKE'ye CD (WIF, Artifact Registry) (§9.4) |
| 16 | jBPM (Ticket Management) | ⊘ Kapsam dışı (yatırım odaklı proje) |
| 17 | Cache (Caffeine) | ✅ |
| 18 | REST API versioning (/api/v1) | ✅ 17 controller |
| 19 | OpenAPI/Swagger | ✅ SpringDoc 2.7.0 |
| 20 | Javadoc | ✅ Plugin + ~%60 coverage |
| 21 | README | ✅ Full setup guide |
| 22 | Unit Tests | ✅ 47 test sınıfı + JaCoCo |
| 23 | Error Handling | ✅ GlobalExceptionHandler |
| 24 | Layered Architecture / OOP | ✅ Controller/Service/Repository/Entity/DTO ayrımı |

## 14. Bilinen Sınırlamalar

- **Yahoo Finance rate limit:** Anonymous endpoint, sıkı rate limit yok ama IP bazlı abuse koruması var. Production'da paid alternatif (TwelveData, Finnhub) düşünülmeli.
- **TCMB EVDS3 oturum cookie'leri:** API key ile çalışıyor ama bazı endpoint'ler hala cookie tabanlı. JSESSIONID süresi bittiğinde manuel yenileme gerekir.
- **LibreTranslate kalite:** Hızlı ama finansal jargon için bazen tuhaf çeviriler. Production'da DeepL veya OpenAI translation alternatif.
- **In-memory cache:** Caffeine — multi-instance deployment'ta cache coherence yok. Redis'e geçiş opsiyonu hazır.
- **WebSocket / Real-time push:** Yok — polling tabanlı. Fiyat alarmları batch check.

## 15. Geliştirici Onboarding

`README.md` — yeni geliştirici için:
1. Prerequisites (Docker, Docker Compose, Git, ~8GB RAM)
2. `.env.example` kopyala → `.env`
3. `docker compose up -d`
4. Erişim URL'leri:
   - Frontend: http://localhost
   - Keycloak admin: http://localhost:8090/admin (admin/admin)
   - Backend API: http://localhost:8080/swagger-ui/
   - Grafana: http://localhost:3001 (admin/admin)
   - Jaeger: http://localhost:16686
   - OpenSearch Dashboards: http://localhost:5601
   - MailPit: http://localhost:8025
5. Frontend dev: `cd frontend && npm install && npm run dev`
6. Backend dev: `cd backend && ./mvnw spring-boot:run`
7. Test: `cd backend && ./mvnw test`
8. Javadoc: `cd backend && ./mvnw javadoc:javadoc` → `target/site/apidocs/`

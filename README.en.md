# 📈 Finans Portalı

[🇹🇷 Türkçe](README.md) · **🇬🇧 English**

> A Türkiye-focused, multi-asset **finance portfolio & market-tracking platform** — brings stocks, crypto, funds, bonds, FX, commodities, VİOP futures, inflation and news together in one place; offers portfolio tracking, price alerts, technical analysis and an AI chat assistant.
>
> ⚠️ **All buy/sell operations (especially bonds and VİOP) are a SIMULATION** — no real orders are placed; this is for portfolio tracking only. Not investment advice.

A Spring Boot 3.5.9 (Java 21) backend + React 19 / Vite frontend, running on Docker Compose with a full self-hosted stack (Keycloak, Kafka, OpenSearch, Prometheus/Grafana, Jaeger, LDAP); ships with Kubernetes/Kustomize (GKE) overlays and GitHub Actions CI/CD.

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Architecture](#️-architecture)
- [Tech Stack](#️-tech-stack)
- [Quick Start](#-quick-start)
- [Access URLs & Default Credentials](#-access-urls--default-credentials)
- [Development](#-development)
- [Project Structure](#-project-structure)
- [Backend — API & Domain](#-backend--api--domain)
- [Frontend](#-frontend)
- [Data Sources & Integrations](#-data-sources--integrations)
- [Observability](#-observability)
- [Security](#-security)
- [Configuration (Environment Variables)](#️-configuration-environment-variables)
- [CI/CD & Deployment](#-cicd--deployment)
- [Important Notes (Gotchas)](#️-important-notes-gotchas)
- [Troubleshooting](#-troubleshooting)
- [License](#-license)

---

## 🔭 Overview

Finans Portalı is a platform that pulls market data from various public sources, normalizes it, presents it through a single interface, and adds **portfolio/trade simulation**, **price alerts**, **technical analysis**, an **AI finance assistant** and a **news feed** on top.

- **Backend:** ~19 REST controllers under `/api/v1/**`. PostgreSQL + Flyway (V1–V27), Caffeine cache, OAuth2 resource server (Keycloak JWT). Scheduled jobs periodically refresh external sources; in a multi-replica environment they run single-instance via **ShedLock**.
- **Frontend:** React 19 SPA (Vite + SWC). Keycloak OIDC (PKCE), TR/EN i18n, light/dark theme, multi-currency display, lightweight-charts / klinecharts / recharts.
- **Infrastructure:** 19 services via Docker Compose (auth, messaging, logging, metrics, tracing). Kubernetes/Kustomize + GKE overlay.

> **Note:** The bond and VİOP buy/sell endpoints (`/api/v1/portfolio/bonds`, `/api/v1/portfolio/viop`) are **simulation only** — they hold virtual positions and never place real orders. The user identity always comes from the JWT `sub` claim; everyone can only access their own data.

---

## ✨ Features

| Area | Description |
|---|---|
| **Stocks / Crypto / Commodities / Indices** | Live price + history + candles (OHLC) from Yahoo Finance; BIST end-of-day (delayed), crypto/US/indices 15-min intraday. |
| **FX** | TCMB daily rate XML; rate table + converter. |
| **Investment Funds** | ~1000+ funds from TEFAS (type/company/risk/return 1m/3m/6m/YTD/1y/3y/5y). |
| **Bonds** | TCMB EVDS3 government debt securities; clean/dirty price, coupon rate, **YTM (computed by discounting)**, maturity; sukuk/CPI-indexed excluded. |
| **VİOP (Futures)** | Futures contracts from İş Yatırım (15-min delayed); long/short position simulation, margin, leverage, net-position, expiry. |
| **Inflation** | TCMB CPI (TP.FG.J0) + US CPI (FRED CPIAUCSL); monthly/yearly change, 5-year cumulative. |
| **Deposit Rates** | TCMB EVDS3 — TRY/USD/EUR × 6 maturity buckets. |
| **News** | 10 categories, 31 RSS sources; full-content scrape + TR↔EN translation via LibreTranslate. |
| **Portfolio** | Stock/crypto/fund buy-sell tracking (cost averaging), allocation, performance, Excel import. |
| **Past-to-Present** | Real (inflation-adjusted) return of past purchases; auto-fetches the price on a given date from symbol+date. |
| **Watchlists** | Multiple watchlists; dedicated page from the navbar. |
| **Price Alerts** | PRICE_ABOVE / PRICE_BELOW / PERCENT_GAIN / PERCENT_LOSS; email notification (TR/EN, TRY/USD snapshot). |
| **Analysis & AI Chat** | Cross-asset analysis table + signals; finance assistant via OpenAI-compatible LLM (Groq) (local mock if no key). |
| **Technical Analysis** | Moving averages, trend, support/resistance, momentum. |
| **Notifications** | In-app notification center + bell badge. |
| **Admin** | RSS feed management, market/fund/news reset-refresh, Keycloak user management (ban/2FA). |
| **Identity** | Keycloak OIDC + mandatory TOTP 2FA + LDAP federation + custom login theme. |

---

## 🏗️ Architecture

```
                              ┌─────────────────────────────────────────────┐
   Browser ──▶ nginx :80 ────▶│  React 19 SPA (Vite)   /api ─▶ backend:8080  │
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
            OpenLDAP :389                       │ external data (fetchers)
                                                ▼
   Yahoo • TCMB EVDS3 • TCMB XML • TEFAS • FRED • İş Yatırım • RSS • LibreTranslate • Groq LLM
                                                │
   Observability:  Log4j2 ─▶ Kafka(finans-logs) ─▶ log-consumer ─▶ OpenSearch ─▶ Dashboards
                   Micrometer ─▶ Prometheus ─▶ Grafana     OTel agent ─▶ Jaeger
```

**Helper services:** Playwright (headless Chromium — Cloudflare/Akamai bypass), LibreTranslate (TR↔EN), Mailpit (dev email), phpLDAPadmin (LDAP UI), Kafka/Zookeeper (log transport only).

---

## 🛠️ Tech Stack

### Backend
- **Java 21**, **Spring Boot 3.5.9** (parent), Maven (wrapper 3.3.4 → Maven 3.9.12)
- Spring **Web MVC** + **WebFlux** (WebClient/reactor-netty — external HTTP), **Data JPA/Hibernate**, **Security OAuth2 Resource Server** (Keycloak JWT/RS256), **Cache** (Caffeine), **Validation**, **Mail**, **Actuator**
- **Flyway** (core + flyway-database-postgresql) — V1–V27
- **Log4j2** (instead of Logback) + `log4j-layout-template-json` + Kafka appender; **Spring Kafka** (logs only)
- **Micrometer** (Prometheus registry) + **OpenTelemetry** (Java agent v2.0.0, OTLP exporter, tracing-bridge)
- **ShedLock 5.16.0** (jdbc-template provider) — distributed scheduler lock
- **springdoc-openapi 2.7.0** (Swagger UI), **Jsoup 1.17.2** (HTML scrape), **Apache POI 5.2.5** (Excel), **Lombok**
- Tests: spring-boot-starter-test, spring-security-test, **H2**; **JaCoCo 0.8.12**

### Frontend
- **React 19.2** + **Vite 7.3** (`@vitejs/plugin-react-swc`), **Node 20**
- **react-router-dom 7.14**, **axios 1.13**, **keycloak-js 26.2**
- Charts: **lightweight-charts 5.2**, **klinecharts 9.8** (candles + drawing tools + indicators), **recharts 3.8** (pie)
- **Tailwind CSS 4.2**, **react-hot-toast 2.6**, **xlsx (SheetJS 0.20.3)** (Excel import, lazy-load)
- Custom **i18n** (TR/EN dict, not react-i18next), CSS-variable **theme** (`data-theme`), multi-**currency** display
- _Note: `zustand`, `react-window`, `react-sparklines` are installed but unused (state via React hooks/context)._

### Infrastructure & Observability
- **Docker Compose** (19 services), **Kubernetes + Kustomize** (base + dev/prod/gke overlays), **GKE** (Artifact Registry, WIF, GCE Ingress)
- **PostgreSQL 15** (compose) / 16 (k8s StatefulSet)
- **Keycloak 26.5.1** + **OpenLDAP 1.5.0** + phpLDAPadmin
- **Kafka + Zookeeper** (Confluent 7.5.0), **OpenSearch + Dashboards 2.11.1**
- **Prometheus v2.48.1**, **Grafana 10.2.3**, **Jaeger 1.53**, **otel-collector-contrib 0.93.0**
- **LibreTranslate**, **Mailpit**, **Playwright v1.47.2**, **nginx:alpine** (prod service)
- CI/CD: **GitHub Actions** + **SonarCloud**

---

## 🚀 Quick Start

### Requirements
- **Docker Desktop** (Compose v2) — 8GB+ RAM recommended
- (Only for non-Docker local development) **JDK 21**, **Node 20**

### Start with One Command
```bash
git clone <repo-url> && cd finans-portali

cp .env.example .env          # REQUIRED first step   (Windows: Copy-Item .env.example .env)

docker compose up -d          # or:  make up   /   .\scripts\make.ps1 up   /   .\scripts\start-docker.ps1
```
> **Creating `.env` is required.** The stack comes up fine with the default values; without `.env`, compose prints "variable not set" warnings and the email/Keycloak-admin features stay empty. Filling in the **optional** keys (`EVDS_API_KEY`, `APP_FRED_API_KEY`, `GMAIL_SMTP_*`, `APP_LLM_API_KEY`) is optional — if left blank, those features are off and the app still runs.

The first launch may take a few minutes (image build + Keycloak/LDAP/OpenSearch boot). _Alternative:_ `scripts\start-docker.ps1` auto-copies `.env` from `.env.example` if missing, runs health checks, and prints the URLs.

### First Login
**No human user is seeded** into Keycloak (only a service account exists). Self-registration is open in the realm:
1. Create an account via **Sign Up** at http://localhost, **or** create a user in the `finans` realm from the Keycloak admin console (http://localhost:8090, `admin/admin`).
2. **TOTP 2FA** setup is mandatory on first login (Google Authenticator / FreeOTP).

---

## 🌐 Access URLs & Default Credentials

| Service | URL | Default Credentials (dev) |
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
| **OpenSearch Dashboards** | http://localhost:5601 | — (security plugin disabled) |
| **Mailpit (dev email)** | http://localhost:8025 | — |
| **phpLDAPadmin** | http://localhost:8089 | `cn=admin,dc=finance,dc=local` / `admin_password` |
| Kafka | `localhost:9092` | — |
| OpenSearch REST | `localhost:9200` | — |
| log-consumer | `localhost:8081` | — |

> **Grafana is on 3100** (not 3000 — because Windows/Hyper-V reserves the 2954–3053 range). **Keycloak is on 8090** (not 8080/8081). LDAP seed users (`ldap/init.ldif`): `john.doe`/`jane.smith` `password123`, `test.user` `test123`, `admin.user` `admin123` (once LDAP federation is set up manually).
>
> 🔐 These credentials are **for development only**; change them in production.

---

## 💻 Development

### Hot-Reload (HMR) mode
```bash
make dev          # or:  .\scripts\make.ps1 dev
# docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
# frontend switches to the Vite dev server; http://localhost still works (host 80 -> Vite 5173)
make dev-down
```

### Local without Docker
```bash
# Backend (8080) — application.yml defaults to localhost Postgres + Keycloak
cd backend && ./mvnw spring-boot:run

# Frontend (5173)
cd frontend && npm install && npm run dev
```
> ⚠️ The `application.yml` local default is **db `finans` / user `finans` / password `finans`** — which is **different** from Compose's Postgres (`finans_db` / `finans_user` / `finans_password`). If you point `./mvnw spring-boot:run` at the Compose Postgres, override `SPRING_DATASOURCE_URL/_USERNAME/_PASSWORD`.

### Test & Build
```bash
cd backend && ./mvnw test          # H2 in-memory + JaCoCo -> target/site/jacoco/index.html
cd backend && ./mvnw clean package # jar (the Docker image builds with -DskipTests)
cd frontend && npm run build        # vite build -> dist/
cd frontend && npm run lint         # eslint  (disabled in CI — see Notes)
```

### Useful Make / scripts\make.ps1 targets
`up` · `down` · `restart` · `ps` · `logs` / `logs-backend` · `health` · `rebuild` / `rebuild-backend` / `rebuild-frontend` · `front` (fast frontend rebuild) · `dev` / `dev-down` · `backup` / `restore` (pg_dump/psql `finans_db`) · `shell-backend` / `shell-postgres` · `stats` · `clean` (down -v + prune) · `prod-up` / `prod-down` (docker-compose.prod.yml)

> On Windows without GNU make, use **`scripts\make.ps1`** (`docker compose` v2 syntax). The Makefile calls `docker-compose` (v1).

### Database Migration
Flyway runs at application startup (`classpath:db/migration`, `baseline-on-migrate=true`). New migration: `backend/src/main/resources/db/migration/V28__...sql`. _Note: JPA `ddl-auto=update` and Flyway are both enabled at the same time (a known anti-pattern); the migrations are the schema authority._

---

## 📁 Project Structure

```
finans-portali/
├── backend/              # Spring Boot (Java 21, Maven) — REST API, schedulers, fetchers
│   └── src/main/java/com/finansportali/backend/{controller,service,entity,dto,repository,config,filter,util}
│   └── src/main/resources/{application.yml, db/migration/V1..V27, log4j2-spring.xml}
├── frontend/            # React 19 + Vite SPA  (pages, components, api, contexts, hooks, utils)
├── log-consumer/        # Kafka(finans-logs) -> OpenSearch indexer (separate Spring Boot module)
├── playwright-service/  # Node/Express headless Chromium sidecar (POST /fetch)
├── keycloak/            # finans-realm.json (realm import)
├── keycloak-themes/     # finance-theme (custom login theme)
├── ldap/                # init.ldif (LDAP seed)
├── k8s/                 # Kustomize base + overlays (dev/prod/gke)
├── grafana/ · prometheus.yml · otel-collector-config.yaml   # observability config
├── docker-compose.yml · .dev.yml · .prod.yml
├── Makefile               # Windows equivalent: scripts/make.ps1
├── scripts/               # make.ps1 · start-docker.ps1 · apply-secrets.ps1 · keycloak-bootstrap.sh
├── docs/pdf/              # Business & Technical analysis documents (PDF)
└── sonar-project.properties
```

---

## 🔌 Backend — API & Domain

All endpoints are under `/api/v1/**`. **Public GET** ones need no auth; **Authenticated** ones require a Keycloak JWT; **ADMIN** ones require `ROLE_ADMIN`.

### REST Controllers

| Controller | Base Path | Access | Notable endpoints |
|---|---|---|---|
| **MarketController** | `/api/v1/market` | Public GET | `/summary`, `/spot-rates`, `/instruments`, `/search`, `/price`, `/history`, `/candles`, `/history/fx`, `/history/batch` |
| **ExchangeRateController** | `/api/v1/exchange-rates` | Public GET | `/`, `/sources`, `/source/{s}`, `/currency/{c}/history` |
| **InvestmentFundController** | `/api/v1/investment-funds` | Public GET | `/`, `/types`, `/companies`, `/{code}`, `/top-performers`, `/search`; `POST /admin/refresh` (ADMIN) |
| **BondController** | `/api/v1/bonds` | Public GET | `/` (filter), `/{id}`, `/{id}/history`, `/summary`; `POST /refresh` (ADMIN) |
| **BondTradeController** | `/api/v1/portfolio/bonds` | Auth · **SIMULATION** | `POST /buy`, `/sell`, `GET /positions`, `/transactions`, `/summary`, `POST /preview/buy`, `/preview/sell` |
| **ViopController** | `/api/v1/viop` | Public GET | `GET /?category` (contract list) |
| **ViopTradeController** | `/api/v1/portfolio/viop` | Auth · **SIMULATION** | `POST /positions/open`, `/positions/close`, `GET /positions`, `/transactions`, `/summary`, `POST /preview` |
| **PortfolioController** | `/api/v1/portfolio` | Auth | `GET /positions`, `/summary`, `/allocation`, `/allocation/by-type`, `/summary-detail`, `/performance`; `POST /positions` (buy, cost avg.), `/positions/sell`; `DELETE /positions/{symbol}`, `/positions` |
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
| **AdminController** | `/api/v1/admin` | ADMIN | RSS feed CRUD/toggle/cleanup; `reset-market`, `refresh-prices`, `reset-news`, `refresh-news`, `reset-funds`; Keycloak user `ban`/`unban`/`require-2fa`/`reset-2fa` |

### Scheduled Jobs (Schedulers)

| Job | Schedule | What it does |
|---|---|---|
| `PriceRefreshScheduler` | Startup+60s (once); daily **18:00 UTC** full; **15 min** intraday (non-delayed only) | Yahoo price/candle + price-alert check, cache eviction |
| `ExchangeRateService` | Startup+10s, **every 4 hours** | TCMB rate XML |
| `ViopService` | Startup+30s, **15 min** | İş Yatırım VİOP contracts |
| `NewsService` | Startup+5s, **every 6 hours** (+ daily 03:00 cleanup) | RSS fetch + content scrape + translation prewarm |
| `InflationService` | Daily **09:00** | TCMB CPI + FRED US CPI |
| `DepositRateService` | Daily **09:05** | TCMB deposit rates |
| `BondDataRefreshScheduler` | Startup+5s, **every 2 hours** | TCMB EVDS3 debt securities |
| `InvestmentFundRefreshScheduler` | Seed at startup + weekdays **10:30/14:30/18:30** | TEFAS funds |
| `ViopExpiryScheduler` 🔒 | Daily **00:05** (ShedLock) | Expires VİOP positions that reached maturity |
| `BondCouponMaturityScheduler` 🔒 | Daily **00:10** (ShedLock) | Pays coupons / redeems matured bonds |

🔒 = protected by `@SchedulerLock` (runs single-instance on multi-replica GKE). The others are lock-free because they are idempotent upserts.

> All crons follow the **container's time zone**; since TZ is not set in the containers, in practice it is **UTC** (not Türkiye local time).

### Service Layer
- **Market:** `MarketService` → `MarketInstrumentService` / `MarketPriceService` / `MarketHistoryService` / `MarketDataSeedService`
- **Portfolio:** `PortfolioService` → `PortfolioCalculationService` / `PortfolioCurrencyService` / `PortfolioPerformanceService` / `PortfolioPositionService`
- **VİOP:** `ViopCalculationService` (pure math) / `ViopPositionService` (long/short, net position, margin) / `ViopExpiryScheduler`
- **Bonds:** `BondCalculationService` (clean/dirty/accrued/coupon) / `BondPositionService` (nominal, weighted average, redemption P&L) / `BondCouponMaturityScheduler` + `BondDataRefreshService`
- **Analysis:** `AiAnalysisService` / `InstrumentAnalysisService` / `LlmClient` / `RiskProfileService` / `TechnicalAnalysisService`
- Others: `NewsService`, `NotificationService`, `PriceAlertService`, `ExchangeRateService`, `InvestmentFundService`, `InflationService`, `DepositRateService`, `DebtInstrumentService`, `WatchlistService`, `UserService`, `KeycloakAdminService`

### Database Schema (Flyway V1–V27 summary)
- **V1:** `market_instruments`, `market_quotes`, `market_candles`, `portfolio_positions`, `price_alerts`, `news_articles`
- **V2–V12:** `exchange_rates`, `investment_funds` (+returns), `historical_prices`, `watchlists`/`watchlist_items`, `debt_instruments`(+quotes), `inflation_data_points`, `deposit_rate_points`
- **V13–V24:** `notifications`, `news_feeds`, `viop_contracts`, alert language/currency, `user_preferences`, translation columns, mojibake fixes
- **V25:** `viop_positions` + `viop_transactions` (UNIQUE user+contract)
- **V26:** `bond_positions` + `bond_transactions` (UNIQUE user+ISIN)
- **V27:** `shedlock` (distributed lock table)

### Cache (Caffeine)
maxSize 1000, **TTL 30s**. Caches: `marketSummary`, `allInstrumentsWithPrices`, `marketHistory`, `yahooChart`, `technicalAnalysis`, `exchange-rates`(+by-source), `investment-funds`(+by-type), `inflation-all/latest`, `deposit-rates-all/latest`.

---

## 🎨 Frontend

A React 19 SPA; all data comes from the backend via `/api/v1/*` (Vite proxy in dev, nginx reverse-proxy in prod).

### Pages & Routes

| Route | Page | Access |
|---|---|---|
| `/` | Home (landing, movers, BIST100, FX/crypto/commodities, latest news) | Public |
| `/news` · `/news/:id` | News list · detail | Public |
| `/stocks` · `/crypto` · `/commodities` | `FinexStyleMarket` (filterType) | Public |
| `/market-data` | FX rates + converter | Public |
| `/funds` | Investment funds | Public |
| `/bonds` | Bond market + buy (simulation) + positions | Public |
| `/viop` | VİOP contracts + long/short (simulation) | Public |
| `/inflation` | CPI (TR/US) | Public |
| `/analysis` | Cross-asset analysis + AI chat | 🔒 Auth |
| `/portfolio` | Portfolio dashboard (summary, allocation, performance, derivatives) | 🔒 Auth |
| `/historical` | Past-to-Present (real return vs inflation) | 🔒 Auth |
| `/lists` | **Watchlists** (watchlist manager) | 🔒 Auth |
| `/settings` | Profile, theme, notification prefs, 2FA | 🔒 Auth |
| `/admin` | Admin panel (users/data/RSS) | 🔒 Auth + admin |
| `/chart` | Full-screen KLineChart (separate tab) | Public |

### Notable components
- **Navigation:** `Layout` (top `Ticker` + `Topbar`, no left sidebar). `Topbar` has inline nav chips (public/private/admin groups), currency (Original/₺/$), language (TR/EN), theme, notification & alert bell, mobile drawer.
- **Market table:** `FinexStyleMarket` (~70KB main table; filter, sparkline, batch history, buy/chart/compare).
- **Charts:** `KLineChart` (candles + MA/VOL/RSI/MACD + drawing tools), `LWAreaChart`/`LWMultiLineChart`/`LWSparkline`/`PortfolioAreaChart` (lightweight-charts), `PortfolioCharts`/`InteractivePieChart` (recharts). _No live TradingView embed — replaced with klinecharts._
- **Portfolio:** `usePortfolioPage` hook + `SummaryCards`, `PositionsTable`, `PortfolioDerivatives` (VİOP+bonds), Add/Sell modals, `ImportPreviewModal` (Excel).
- **Bonds/VİOP:** `BondBuyModal`/`BondSellModal`/`BondPositionsTable`/`BondDetailModal`, `ViopTradeModal`/`ViopCloseModal`/`ViopPositionsTable` — all with `SimulationDisclaimer`.
- **Modals:** `Modal` (portal), `InstrumentChartModal`, `CompareInstrumentsModal` (inflation overlay), `AssetDetailModal`, `PriceAlertModal`.

### State, i18n, Theme, Currency
- **State:** React `useState`/Context + custom hooks (Zustand installed but unused).
- **Contexts:** `I18nContext` (TR/EN, `i18nDict.js` ~78KB), `ThemeContext`/`theme.js` (light/dark/system, `data-theme`), `CurrencyDisplayContext` (`original`/`TRY`/`USD`, polls USDTRY from `/spot-rates` every 60s).
- **Auth:** `keycloak-js` (OIDC, PKCE `S256`, check-sso, silent SSO, token refresh); theme/language appended to the login URL.
- **API layer:** `http.js` (axios + Bearer + Accept-Language interceptor) + domain modules (`portfolioApi`, `bondTradeApi`, `viopTradeApi`, `watchlistApi`, `analysisApi`, ...). Input sanitization (`safeSymbol`, `safeNumericId`, ...).
- **Excel import:** `utils/excelImport.js` (SheetJS lazy-load), TR/EN header aliases; sample files at `public/ornek-*.xlsx`.
- **localStorage keys:** `theme`, `i18n-lang`, `currency-display-mode`, `notif-preferences`, `user-avatar`, `finans-ticker-prefs`, `mkt-hist:<symbol>`, historical positions.

---

## 🔗 Data Sources & Integrations

All fetchers live under `service/client/**` and are **fail-soft** (if a source goes down they return empty, log, and never crash the app). Since most credentials are **disabled/anonymous by default**, a clean checkout boots with partial data.

| Data | Class | Source / Endpoint | Refresh | Key (env) |
|---|---|---|---|---|
| Stock/crypto/commodity/FX/index price | `YahooPriceFetcher` | `query1.finance.yahoo.com/v8/finance/chart/{symbol}` (anonymous) | 18:00 UTC + 15 min | — (needs Chrome UA) |
| _(backup, inactive)_ | `TwelveDataFetcher`, `FinnhubPriceFetcher` | TwelveData / Finnhub | — | `twelvedata.api-key`, `finnhub.api-key` (`disabled`) |
| Bonds | `EvdsBondYieldFetcher` | TCMB **EVDS3** `bie_pydibs` | 2 hours | `EVDS_API_KEY` (or JSESSIONID+TS cookie) |
| FX rate | `ExchangeRateService` | `tcmb.gov.tr/kurlar/today.xml` (anonymous) | 4 hours | — |
| Investment funds | `TefasFundFetcher` | TEFAS `/api/funds/...` | weekdays 3× | `APP_TEFAS_BEARER_TOKEN` (has anonymous default) |
| Inflation TR | `TcmbInflationFetcher` | TCMB EVDS3 `TP.FG.J0` | daily 09:00 | `EVDS_API_KEY` |
| Inflation US | `FredInflationFetcher` | FRED `CPIAUCSL` | daily 09:00 | `APP_FRED_API_KEY` |
| Deposit rate | `TcmbDepositRateFetcher` | TCMB EVDS3 `TP.{TRY/USD/EUR}.MT01..06` | daily 09:05 | `EVDS_API_KEY` |
| VİOP | `IsYatirimViopFetcher` | İş Yatırım (scraped via Playwright) | 15 min | `APP_PLAYWRIGHT_SERVICE_URL` |
| News content | `NewsContentFetcher` | RSS + Jsoup/curl/Playwright fallback | 6 hours | — |
| Translation | `LibreTranslateClient` | self-hosted LibreTranslate (TR↔EN) | — | `APP_LIBRETRANSLATE_URL` |
| Email | `NotificationService` | SMTP (dev: Mailpit, prod: Gmail) | on alert | `GMAIL_SMTP_USERNAME` / `GMAIL_SMTP_APP_PASSWORD` |
| Keycloak Admin | `KeycloakAdminService` | Keycloak Admin REST (client_credentials) | — | `KC_BACKEND_CLIENT_SECRET` |
| AI chat | `LlmClient` | OpenAI-compatible (default Groq `llama-3.3-70b-versatile`) | — | `APP_LLM_API_KEY` (local mock if absent) |

> **LDAP** is attached as a user federation to **Keycloak**, not to the backend. **Kafka** only transports logs (not application messaging).

---

## 📊 Observability

### Log Pipeline (canonical)
```
Backend (Log4j2 KafkaAppender, JSON)  ─▶  Kafka topic "finans-logs"  ─▶  log-consumer  ─▶  OpenSearch index "finans-logs-YYYY-MM-DD"  ─▶  OpenSearch Dashboards :5601
```
- JSON schema: `backend/src/main/resources/log4j2/kafka-log-event.json` (timestamp, level, logger, thread, message, service, env, host + MDC: requestId/userId/traceId/spanId/...).
- The `nokafka` Spring profile disables the appender (for broker-less environments — otherwise every log line adds ~18s latency).

### Metrics
Micrometer → `/actuator/prometheus` → Prometheus (15s scrape, 15d/10GB) → **Grafana** (4 prebuilt dashboards: backend HTTP/JVM/Hikari/GC, business refresh, otel-collector, prometheus-health). Services emit business metrics (e.g. `price_instruments_updated_total`, `news_refresh_duration_seconds`).

### Tracing
OpenTelemetry Java agent v2.0.0 → OTLP `http://jaeger:4318` → **Jaeger** (:16686). Sampling 1.0; sensitive headers are stripped from traces.

### Health
Spring Actuator (`health`, `info`, `metrics`, `prometheus`, `loggers`, `env`, `configprops`, `beans`, `mappings`; `show-details=always`).

---

## 🔐 Security

- **Keycloak 26.5.1** — realm `finans`, clients `finans-frontend` (public, PKCE) + `finans-backend-admin` (confidential service-account). Realm roles `USER`/`ADMIN`. Brute-force protection on (5 failures → 900s).
- **Mandatory TOTP 2FA** — `CONFIGURE_TOTP` is a default required action (every user sets it up on first login). `keycloak-bootstrap.sh` configures the realm idempotently + switches LDAP to WRITABLE.
- **Custom login theme** — `keycloak-themes/finance-theme` (bind-mounted in compose; baked into the image with `kc.sh build` on GKE).
- **Spring Security** — stateless OAuth2 resource server (JWT/RS256), CSRF/formLogin off, CORS on, `@EnableMethodSecurity`. `JwtRoleConverter` maps `roles`→`realm_access.roles` to `ROLE_*`. User = `jwt.getSubject()`.
- **LDAP** — OpenLDAP (`dc=finance,dc=local`) Keycloak federation (not in the realm export, set up manually).
- **Logging security** — `LoggingFilter` (correlation id, MDC), `LogSanitizer` (CRLF cleanup, S5145).
- **Secrets** — compose reads from `.env`; on k8s, `scripts\apply-secrets.ps1` → `postgres-secret`/`keycloak-secret`/`backend-secret`. The `secret.example.yaml` files are outside kustomization (applied out-of-band).
- **SonarCloud** — `yigitsheker_finans-portali`; JaCoCo ~89% instruction; weekly (Mon 06:00 UTC) + manual; some rule suppressions (`sonar-project.properties`).

> ⚠️ **Caution (dev defaults, do not carry to production):** Actuator is **fully unauthenticated** (including `env`/`configprops`/`beans`/`mappings` — may leak resolved config); OpenSearch/Dashboards have the **security plugin disabled**; there is **no app/edge rate limiting** (only Keycloak brute-force); LLM/EVDS/FRED/Gmail keys default to empty.

---

## ⚙️ Configuration (Environment Variables)

`docker compose` reads `.env` (gitignored, copied from `.env.example`) for variable substitution. The backend uses `application.yml` + `SPRING_*` env vars.

**Database / Auth**
`SPRING_DATASOURCE_URL/_USERNAME/_PASSWORD` · `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` (`http://localhost:8090/realms/finans`) / `_JWK_SET_URI` · `APP_KEYCLOAK_SERVER_URL`/`_REALM`/`_ADMIN_CLIENT_ID`/`_ADMIN_CLIENT_SECRET` (=`KC_BACKEND_CLIENT_SECRET`)

**Bonds (`app.bonds.*`)**
`BONDS_PROVIDER` (TCMB/DEMO) · `BONDS_SCHEDULER_ENABLED` · `BONDS_REFRESH_CRON` (`0 0 0/2 * * ?`) · `coupon-cron` (`0 10 0 * * ?`) · `coupon-frequency-default` (2) · `coupon-tax-rate` (0) · EVDS limits (max-bonds 160, clean 40–140, ytm 6–60, history 120d)

**VİOP (`app.viop.*`)**
`expiry-cron` (`0 5 0 * * ?`) · `margin-rate` (0.10) · `commission-rate` (0)

**Funds (`app.funds.*`)**
`FUNDS_PROVIDER` (TEFAS) · `FUNDS_REFRESH_CRON` · `FUNDS_MAX_FETCH` (1500) · `FUNDS_RATE_LIMIT_DELAY` (200ms) · `APP_TEFAS_BEARER_TOKEN`

**External source keys**
`EVDS_API_KEY` (or `TCMB_EVDS3_JSESSIONID`+`TCMB_EVDS3_TS_COOKIE`) · `APP_FRED_API_KEY` · `twelvedata.api-key`/`finnhub.api-key` (`disabled`)

**Mail / Other**
`SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` (`GMAIL_SMTP_*`, requires an App Password) · `APP_MAIL_FROM` · `APP_PLAYWRIGHT_SERVICE_URL` · `APP_LIBRETRANSLATE_URL` · `APP_LLM_BASE_URL`/`_API_KEY`/`_MODEL` · `APP_CORS_ALLOWED_ORIGINS` · `KAFKA_BOOTSTRAP_SERVERS` (`kafka:29092`) · OTel `OTEL_*`

---

## 🚢 CI/CD & Deployment

### GitHub Actions
- **`ci.yml`** (PR + main push): backend `./mvnw verify` + JaCoCo, frontend `npm ci && build`, playwright-service `node --check`, `docker compose config`. _(Frontend ESLint deliberately disabled — the TS→JS conversion left ~160 unused-import errors.)_
- **`cd.yml`** ("Deploy to GKE"): keyless **WIF**; pushes images to Artifact Registry, `kustomize edit set image`, `kubectl apply -k k8s/overlays/gke`, rollout verification. ⏸️ **PAUSED** — `workflow_dispatch` only (manual); automatic trigger off (cluster shut down to save cost).
- **`sonar.yml`**: separate from CI (the scan took ~1 hour) — weekly (Mon 06:00 UTC) + manual.

### Kubernetes / Kustomize
`k8s/base` (postgres StatefulSet, Keycloak + bootstrap Job, backend/frontend Deployment, Ingress) + overlays:
- **dev** — Docker Desktop/kind, NodePort, local images
- **prod** — replicas + HPA + anti-affinity, cert-manager TLS
- **gke** — Artifact Registry, GCE Ingress + ManagedCertificate, backend HPA 1-3, `SPRING_PROFILES_ACTIVE=prod,nokafka`, custom Keycloak image

Local k8s: `k8s/deploy-local.ps1` (kind cluster + image load + dev overlay + port-forward).

---

## ⚠️ Important Notes (Gotchas)

- **Bond and VİOP buy/sell are a SIMULATION** — no real orders; virtual positions. Because data sources are limited there are some simplifications (coupon frequency assumed semi-annual, accrued interest is user input, floating/CPI-indexed bonds excluded). All VİOP contracts are treated as TRY-settled.
- **Ports:** Keycloak **8090** (not 8080/8081), Grafana **3100** (not 3000), frontend **80**, otel-collector OTLP remapped to 4319/4320.
- **`nokafka` profile:** required in broker-less environments (otherwise log latency). Kafka is on in compose/local (so the log pipeline works).
- **No rate limiting** (app/nginx). The only protection is Keycloak brute-force.
- **No human user is seeded in Keycloak** — create one via self-register or the admin console.
- **Two theme mechanisms** (`ThemeContext` + `theme.js`); the App uses `theme.js`. **Dead code** exists: `MarketBrowser`/`ModernMarketBrowser`/`Navbar`/`Sidebar`/`PortfolioPage`/`useSparklines` (no route/import).
- **LibreTranslate** has no model-cache volume — it re-downloads the ~70MB TR/EN model on every restart.
- **The Keycloak 26 image has no curl/wget** — the healthcheck uses bash `/dev/tcp`.
- Commit convention (this repo): commits **do not include a Co-Authored-By Claude trailer** (author = the user), and auto-push to `origin/main` after committing.

---

## 🧰 Troubleshooting

```bash
# Service status + logs
docker compose ps
docker compose logs -f backend          # or: make logs-backend
make health                              # backend/frontend/keycloak health

# Backend health
curl http://localhost:8080/actuator/health

# Postgres connection
docker compose exec postgres psql -U finans_user -d finans_db -c "SELECT 1;"

# Port conflict (Windows)
netstat -ano | findstr :8080

# Clean start (DELETES ALL data)
docker compose down -v && docker compose up -d
```

- **Backend boots slowly:** due to Spring Boot + OTel agent + Keycloak JWK fetch, the healthcheck `start_period` is 90s (startupProbe up to 600s on GKE).
- **Bonds/inflation/deposits empty:** means there is no EVDS key/cookie — add `EVDS_API_KEY`.
- **VİOP empty / news content missing:** the Playwright service is required (`APP_PLAYWRIGHT_SERVICE_URL`).
- **AI chat returns mock answers:** set `APP_LLM_API_KEY` (Groq).

---

## 📄 License

This project is licensed under the MIT License.

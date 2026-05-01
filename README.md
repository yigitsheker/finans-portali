# 📈 Finans Portali

Modern, container tabanlı finans portföy yönetim uygulaması.

## 🚀 Özellikler

- **Gerçek Zamanlı Hisse Fiyatları**: Yahoo Finance entegrasyonu
- **Portföy Yönetimi**: Hisse alım/satım takibi
- **Fiyat Alarmları**: Otomatik bildirim sistemi
- **Hisse Karşılaştırma**: İnteraktif grafik analizi
- **Finans Haberleri**: RSS feed entegrasyonu
- **Güvenli Kimlik Doğrulama**: Keycloak OAuth2/JWT
- **Container Tabanlı**: Docker & Docker Compose

## 🏗️ Mimari

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend      │    │    Backend      │    │   PostgreSQL    │
│   (React/TS)    │◄──►│  (Spring Boot)  │◄──►│   (Database)    │
│   Port: 80      │    │   Port: 8080    │    │   Port: 5432    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │    Keycloak     │
                    │ (Authentication)│
                    │   Port: 8081    │
                    └─────────────────┘
```

## 🛠️ Teknoloji Stack

### Backend
- **Framework**: Spring Boot 3.5.9
- **Language**: Java 21
- **Database**: PostgreSQL 15
- **ORM**: Spring Data JPA / Hibernate
- **Security**: Spring Security + OAuth2 JWT
- **Migration**: Flyway
- **Caching**: Caffeine
- **Documentation**: OpenAPI/Swagger
- **Monitoring**: Spring Actuator + Micrometer

### Frontend
- **Framework**: React 18 + TypeScript
- **Build Tool**: Vite
- **Charts**: Recharts
- **HTTP Client**: Axios
- **Authentication**: Keycloak JS

### Infrastructure
- **Containerization**: Docker + Docker Compose
- **Web Server**: Nginx (Production)
- **Authentication**: Keycloak
- **Logging**: Logback + JSON format + ELK Stack
- **Monitoring**: Prometheus + Grafana + Jaeger
- **Health Checks**: Built-in health endpoints
- **Distributed Tracing**: OpenTelemetry + Jaeger

## 🚀 Hızlı Başlangıç

### Gereksinimler
- Docker Desktop
- Docker Compose
- 8GB+ RAM önerilir

### 1. Projeyi İndirin
```bash
git clone <repository-url>
cd finans-portali
```

### 2. Uygulamayı Başlatın

**Linux/macOS:**
```bash
./start.sh
```

**Windows:**
```cmd
start.bat
```

### 3. Erişim Adresleri
- **Frontend**: http://localhost
- **Backend API**: http://localhost:8080
- **Keycloak**: http://localhost:8081
- **API Docs**: http://localhost:8080/swagger-ui.html

### 4. Monitoring ve Observability
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Jaeger Tracing**: http://localhost:16686
- **OpenSearch Dashboards**: http://localhost:5601

## 📊 Monitoring ve Loglama

### Enterprise Observability Stack
- **Metrics**: Prometheus + Grafana dashboards
- **Logging**: ELK Stack (OpenSearch + Logstash + Filebeat)
- **Tracing**: Jaeger distributed tracing
- **Health Monitoring**: Spring Actuator endpoints

### Health Checks
```bash
# Genel sistem durumu
curl http://localhost:8080/actuator/health

# Detaylı metrikler
curl http://localhost:8080/actuator/metrics

# Prometheus formatında metrikler
curl http://localhost:8080/actuator/prometheus
```

### Monitoring Dashboards
```bash
# Grafana - Metrics visualization
http://localhost:3000 (admin/admin)

# Jaeger - Distributed tracing
http://localhost:16686

# OpenSearch Dashboards - Log analysis
http://localhost:5601
```

### Loglar
```bash
# Tüm servislerin logları
docker-compose logs -f

# Sadece backend logları
docker-compose logs -f backend

# JSON formatında log dosyası
tail -f logs/finans-backend.log

# Monitoring stack logları
docker-compose logs -f prometheus grafana jaeger
```

## 🔧 Geliştirme

### Local Development
```bash
# Backend (Port 8080)
cd backend
./mvnw spring-boot:run

# Frontend (Port 5173)
cd frontend
npm install
npm run dev

# PostgreSQL
docker run -d --name postgres \
  -e POSTGRES_DB=finans \
  -e POSTGRES_USER=finans \
  -e POSTGRES_PASSWORD=finans \
  -p 5432:5432 postgres:15-alpine

# Keycloak
docker run -d --name keycloak \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -p 8081:8080 \
  quay.io/keycloak/keycloak:23.0 start-dev
```

### Database Migration
```bash
# Yeni migration oluştur
./mvnw flyway:migrate

# Migration durumunu kontrol et
./mvnw flyway:info

# Migration'ı geri al
./mvnw flyway:undo
```

## 🐳 Docker Commands

```bash
# Servisleri başlat
docker-compose up -d

# Servisleri durdur
docker-compose down

# Logları izle
docker-compose logs -f [service-name]

# Servisleri yeniden başlat
docker-compose restart [service-name]

# Tüm verileri temizle
docker-compose down -v
docker system prune -f
```

## 📈 API Endpoints

### Market Data
- `GET /api/v1/market/summary` - Piyasa özeti
- `GET /api/v1/market/history` - Tarihsel veriler
- `GET /api/v1/market/instruments` - Enstrümanlar

### Portfolio
- `GET /api/v1/portfolio/positions` - Portföy pozisyonları
- `POST /api/v1/portfolio/positions` - Pozisyon ekle
- `DELETE /api/v1/portfolio/positions/{symbol}` - Pozisyon sil

### Price Alerts
- `GET /api/v1/alerts` - Kullanıcı alarmları
- `POST /api/v1/alerts` - Alarm oluştur
- `DELETE /api/v1/alerts/{id}` - Alarm sil

### News
- `GET /api/v1/news` - Finans haberleri

## 🔒 Güvenlik

- **Authentication**: Keycloak OAuth2/JWT
- **Authorization**: Role-based access control
- **HTTPS**: Production'da SSL/TLS
- **CORS**: Yapılandırılmış CORS politikaları
- **Security Headers**: XSS, CSRF koruması

## 📝 Loglama Standardı

Loglar JSON formatında standardize edilmiştir:

```json
{
  "timestamp": "2026-04-24T19:30:00.000+03:00",
  "level": "INFO",
  "message": "REQUEST: GET /api/v1/market/summary",
  "serviceName": "finans-backend",
  "thread": "http-nio-8080-exec-1",
  "logger": "com.finansportali.backend.config.LoggingConfig",
  "requestId": "abc12345",
  "traceId": "def67890",
  "spanId": "ghi11111"
}
```

## 🚨 Troubleshooting

### Port Çakışması
```bash
# Kullanılan portları kontrol et
netstat -tulpn | grep :8080

# Process'i sonlandır
kill -9 <PID>
```

### Container Sorunları
```bash
# Container durumunu kontrol et
docker-compose ps

# Container loglarını incele
docker-compose logs [service-name]

# Container'ı yeniden başlat
docker-compose restart [service-name]
```

### Database Bağlantı Sorunları
```bash
# PostgreSQL bağlantısını test et
docker-compose exec postgres psql -U finans -d finans -c "SELECT 1;"

# Migration durumunu kontrol et
./mvnw flyway:info
```

## 📞 Destek

Sorun yaşadığınızda:
1. Logları kontrol edin: `docker-compose logs -f`
2. Health endpoint'i kontrol edin: `curl http://localhost:8080/actuator/health`
3. Issue açın veya geliştirici ile iletişime geçin

## 📄 Lisans

Bu proje MIT lisansı altında lisanslanmıştır.
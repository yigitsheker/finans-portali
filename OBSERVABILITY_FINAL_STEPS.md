# 🎯 Observability Altyapısı - Son Adımlar

## ✅ Tamamlanan İşler

### 1. Grafana Dashboard
- ✅ Dashboard JSON oluşturuldu
- ✅ Provisioning yapılandırıldı
- ✅ Otomatik yükleme aktif
- 📁 Dosyalar:
  - `grafana/provisioning/dashboards/dashboards.yml`
  - `grafana/provisioning/dashboards/finans-backend.json`

### 2. OpenTelemetry Java Agent
- ✅ Dockerfile'da agent indirildi
- ✅ ENTRYPOINT'te `-javaagent` parametresi eklendi
- ✅ docker-compose.yml'de tüm OTEL env var'ları mevcut

### 3. Dokümantasyon
- ✅ `GRAFANA_DASHBOARD_READY.md` - Dashboard kullanım rehberi
- ✅ `OBSERVABILITY_QUICK_START.md` - Hızlı başlangıç
- ✅ `test-observability.ps1` - Test script'i

## 🔄 Docker Desktop Başladığında Yapılacaklar

### Adım 1: Servisleri Yeniden Başlat

Backend'in OpenTelemetry agent ile başlaması için:

```powershell
cd C:\Users\yigid\Desktop\finans-portali

# Tüm servisleri durdur
docker compose down

# Yeniden başlat
docker compose up -d

# Servislerin başlamasını bekle (2-3 dakika)
Start-Sleep -Seconds 120

# Durumu kontrol et
docker compose ps
```

### Adım 2: Backend Log'larını Kontrol Et

OpenTelemetry agent'ın yüklendiğini doğrula:

```powershell
# Backend log'larında OpenTelemetry'yi ara
docker logs finans-backend 2>&1 | Select-String -Pattern "OpenTelemetry|javaagent|otel" | Select-Object -First 20
```

**Beklenen çıktı:**
```
OpenTelemetry Javaagent enabled
OTLP exporter configured
```

### Adım 3: Traffic Oluştur

Metrics ve trace'ler için traffic:

```powershell
# 10 kez request gönder
for ($i=1; $i -le 10; $i++) { 
    Write-Host "Request $i/10"
    curl.exe "http://localhost:8080/api/market/instruments?page=0&size=10" -s | Out-Null
    curl.exe http://localhost:8080/api/exchange-rates -s | Out-Null
    curl.exe "http://localhost:8080/api/news?page=0&size=5" -s | Out-Null
    Start-Sleep -Milliseconds 500
}
Write-Host "Traffic generated!"
```

### Adım 4: Grafana Dashboard'u Kontrol Et

**URL:** http://localhost:3000/d/finans-backend/finans-backend-monitoring

**Login:**
- Username: `admin`
- Password: `admin`

**Göreceğin metrikler:**
1. ✅ HTTP Request Rate - Saniyedeki request sayısı
2. ✅ Total HTTP Requests - Toplam request
3. ✅ JVM Heap Memory - Memory kullanımı
4. ✅ Database Connections - Connection pool
5. ✅ HTTP Request Duration - Response süreleri

### Adım 5: Jaeger'da Trace'leri Kontrol Et

**URL:** http://localhost:16686

**Adımlar:**
1. Service dropdown'dan **finans-backend** seç
2. **Find Traces** butonuna tıkla
3. Bir trace'e tıkla ve span'leri incele

**Göreceğin span'ler:**
- HTTP request span'leri
- Database query span'leri
- HTTP client call span'leri

### Adım 6: Prometheus'ta Metrics Sorgula

**URL:** http://localhost:9090

**Test query'leri:**

```promql
# HTTP request count
http_server_requests_seconds_count{job="finans-backend"}

# Request rate (per second)
rate(http_server_requests_seconds_count{job="finans-backend"}[5m])

# JVM memory
jvm_memory_used_bytes{job="finans-backend",area="heap"}

# Database connections
hikaricp_connections_active{job="finans-backend"}

# Response time (95th percentile)
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job="finans-backend"}[5m])) by (le, uri))
```

## 🐛 Sorun Giderme

### Problem: Backend'de OpenTelemetry log'ları yok

**Çözüm:**
```powershell
# Backend'i yeniden build et
docker compose build backend --no-cache

# Yeniden başlat
docker compose up -d backend

# Log'ları kontrol et
docker logs finans-backend -f
```

### Problem: Jaeger'da trace'ler görünmüyor

**Kontrol edilecekler:**

1. **OTEL Collector çalışıyor mu?**
```powershell
docker logs finans-otel-collector --tail 50
```

2. **Backend OTEL endpoint'e ulaşabiliyor mu?**
```powershell
docker exec finans-backend curl -v http://otel-collector:4318/v1/traces
```

3. **Environment variable'lar doğru mu?**
```powershell
docker exec finans-backend env | Select-String "OTEL"
```

### Problem: Grafana dashboard'u görünmüyor

**Çözüm:**
```powershell
# Grafana'yı yeniden başlat
docker compose restart grafana

# Log'ları kontrol et
docker logs finans-grafana --tail 50

# Dashboard'u manuel kontrol et
curl.exe -u admin:admin http://localhost:3000/api/search?query=Finans
```

## 📊 Otomatik Test Script'i

Tüm kontrolleri otomatik yapmak için:

```powershell
.\test-observability.ps1
```

**Script şunları kontrol eder:**
- ✅ Tüm servislerin çalışıp çalışmadığı
- ✅ Traffic oluşturma
- ✅ Jaeger'da trace'lerin varlığı
- ✅ Prometheus'ta metrics'lerin varlığı
- ✅ Grafana datasource'larının durumu

## 🎯 Başarı Kriterleri

Tüm bunlar çalışıyorsa başarılı:

- [ ] Backend log'larında "OpenTelemetry Javaagent" görünüyor
- [ ] Grafana dashboard'u açılıyor ve metrikler görünüyor
- [ ] Jaeger'da finans-backend servisi ve trace'ler var
- [ ] Prometheus'ta http_server_requests_seconds_count > 0
- [ ] Test script'i tüm kontrolleri geçiyor

## 🚀 Phase 2: Custom Metrics & Spans

Altyapı çalıştıktan sonra, business-specific metrics ekleyebiliriz:

### 1. Portfolio Operations Metrics

```java
// PortfolioService.java
@Timed(value = "portfolio.operation", 
       description = "Portfolio operations",
       extraTags = {"operation", "add_position"})
public PortfolioPosition addPosition(...) {
    // ...
}
```

### 2. Market Data Refresh Metrics

```java
// PriceRefreshScheduler.java
@Timed(value = "market.refresh.duration", 
       description = "Market data refresh duration")
@Counted(value = "market.refresh.count", 
         description = "Market refresh count")
public void refreshMarketData() {
    // ...
}
```

### 3. Custom Spans

```java
// MarketService.java
@Autowired
private Tracer tracer;

public void complexOperation() {
    Span span = tracer.nextSpan().name("calculate-market-summary");
    try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
        span.tag("market", "BIST");
        // business logic
    } finally {
        span.end();
    }
}
```

### 4. Business Metrics

```java
@Component
public class BusinessMetrics {
    private final MeterRegistry registry;
    
    @Autowired
    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }
    
    public void recordPortfolioValue(String userId, double value) {
        registry.gauge("portfolio.total.value", 
            Tags.of("user", userId), value);
    }
    
    public void recordMarketDataFetch(String provider, boolean success) {
        registry.counter("market.data.fetch", 
            Tags.of("provider", provider, "success", String.valueOf(success)))
            .increment();
    }
}
```

## 📈 Gelişmiş Dashboard Panelleri

Phase 2'de eklenebilecek paneller:

### Portfolio Metrics
```promql
# Portfolio operations per second
rate(portfolio_operation_seconds_count[5m])

# Average portfolio operation duration
rate(portfolio_operation_seconds_sum[5m]) / rate(portfolio_operation_seconds_count[5m])
```

### Market Data Metrics
```promql
# Market refresh success rate
rate(market_refresh_count{success="true"}[5m]) / rate(market_refresh_count[5m])

# Market data fetch by provider
sum by (provider) (rate(market_data_fetch[5m]))
```

### Business Metrics
```promql
# Total portfolio value
sum(portfolio_total_value)

# Active users
count(portfolio_total_value > 0)
```

## 🔗 Faydalı Linkler

- **Grafana Dashboard:** http://localhost:3000/d/finans-backend/finans-backend-monitoring
- **Jaeger UI:** http://localhost:16686
- **Prometheus:** http://localhost:9090
- **OpenSearch Dashboards:** http://localhost:5601
- **Backend Health:** http://localhost:8080/actuator/health
- **Backend Metrics:** http://localhost:8080/actuator/prometheus

## 💡 İpuçları

1. **Dashboard'u özelleştir:** Grafana'da panel'leri edit edebilir, yeni panel ekleyebilirsin
2. **Alert'ler kur:** Yüksek response time, memory usage için alert'ler oluştur
3. **Log correlation:** Jaeger'dan trace ID kopyala, OpenSearch'te ara
4. **Variables kullan:** Dashboard'a environment, service gibi değişkenler ekle
5. **Annotations ekle:** Deployment'ları dashboard'da işaretle

## 📞 Yardım

Sorun yaşarsan:
1. `docker compose logs [service-name]` ile log'ları kontrol et
2. `docker compose ps` ile servis durumlarını kontrol et
3. `test-observability.ps1` script'ini çalıştır
4. Bu dokümandaki "Sorun Giderme" bölümüne bak

---

**Hazırladığım:** Kiro AI
**Tarih:** 2026-05-08
**Durum:** Altyapı hazır, Docker Desktop başladığında test edilmeli

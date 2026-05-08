# ✅ Grafana Dashboard Hazır!

Dashboard otomatik olarak yüklendi ve kullanıma hazır!

## 🎯 Dashboard'a Erişim

**URL:** http://localhost:3000/d/finans-backend/finans-backend-monitoring

**Login:**
- Username: `admin`
- Password: `admin`

## 📊 Dashboard İçeriği

Dashboard şu metrikleri gösteriyor:

### 1. HTTP Request Rate
- **Metrik:** `rate(http_server_requests_seconds_count{job="finans-backend"}[5m])`
- **Açıklama:** Saniyedeki request sayısı (5 dakikalık ortalama)
- **Tip:** Time Series

### 2. Total HTTP Requests
- **Metrik:** `sum(http_server_requests_seconds_count{job="finans-backend"})`
- **Açıklama:** Toplam request sayısı
- **Tip:** Gauge

### 3. JVM Heap Memory
- **Metrikler:**
  - Used: `jvm_memory_used_bytes{job="finans-backend",area="heap"}`
  - Max: `jvm_memory_max_bytes{job="finans-backend",area="heap"}`
- **Açıklama:** Java heap memory kullanımı
- **Tip:** Time Series

### 4. Database Connections
- **Metrikler:**
  - Active: `hikaricp_connections_active{job="finans-backend"}`
  - Idle: `hikaricp_connections_idle{job="finans-backend"}`
- **Açıklama:** HikariCP connection pool durumu
- **Tip:** Time Series

### 5. HTTP Request Duration (95th percentile)
- **Metrik:** `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job="finans-backend"}[5m])) by (le, uri))`
- **Açıklama:** Endpoint'lerin 95. yüzdelik dilim response süreleri
- **Tip:** Time Series

## 🔄 Dashboard Özellikleri

- **Auto Refresh:** 5 saniye
- **Time Range:** Son 15 dakika
- **Tags:** finans-portal, backend, spring-boot

## 🎨 Dashboard'u Özelleştirme

Dashboard'u istediğiniz gibi düzenleyebilirsiniz:

1. Dashboard'a git
2. Sağ üstteki **⚙️ Dashboard settings** tıkla
3. **Make editable** seç (eğer read-only ise)
4. Panel'lere tıklayarak **Edit** yapabilirsin
5. Yeni panel eklemek için **Add panel** kullan

## 📈 Faydalı Prometheus Query'leri

Dashboard'a ekleyebileceğin diğer metrikler:

### CPU Kullanımı
```promql
process_cpu_usage{job="finans-backend"}
```

### Thread Sayısı
```promql
jvm_threads_live{job="finans-backend"}
```

### GC Pause Time
```promql
rate(jvm_gc_pause_seconds_sum{job="finans-backend"}[5m])
```

### HTTP Error Rate
```promql
rate(http_server_requests_seconds_count{job="finans-backend",status=~"5.."}[5m])
```

### Database Query Duration
```promql
histogram_quantile(0.95, sum(rate(spring_data_repository_invocations_seconds_bucket{job="finans-backend"}[5m])) by (le, method))
```

### Logback Events
```promql
rate(logback_events_total{job="finans-backend"}[5m])
```

## 🚀 Sonraki Adımlar

### Phase 2: Custom Metrics & Spans

Şimdi business-specific metrics ekleyebiliriz:

#### 1. Portfolio Operations Metrics
```java
@Timed(value = "portfolio.operation", description = "Portfolio operations")
public void addPosition(...) {
    // ...
}
```

#### 2. Market Data Refresh Metrics
```java
@Timed(value = "market.refresh", description = "Market data refresh")
@Counted(value = "market.refresh.count", description = "Market refresh count")
public void refreshMarketData() {
    // ...
}
```

#### 3. Custom Spans
```java
Span span = tracer.nextSpan().name("calculate-portfolio-value");
try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
    // business logic
} finally {
    span.end();
}
```

#### 4. Business Metrics
```java
@Component
public class PortfolioMetrics {
    private final MeterRegistry registry;
    
    public void recordPortfolioValue(String userId, double value) {
        registry.gauge("portfolio.total.value", 
            Tags.of("user", userId), value);
    }
}
```

## 📊 Alert Kuralları (Opsiyonel)

Grafana'da alert'ler oluşturabilirsin:

### High Response Time Alert
```
Query: histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job="finans-backend"}[5m])) by (le))
Condition: WHEN last() OF query(A) IS ABOVE 1
```

### High Memory Usage Alert
```
Query: jvm_memory_used_bytes{job="finans-backend",area="heap"} / jvm_memory_max_bytes{job="finans-backend",area="heap"}
Condition: WHEN last() OF query(A) IS ABOVE 0.9
```

### Database Connection Pool Alert
```
Query: hikaricp_connections_active{job="finans-backend"}
Condition: WHEN last() OF query(A) IS ABOVE 8
```

## 🔗 İlgili Servisler

- **Jaeger (Traces):** http://localhost:16686
- **Prometheus (Metrics):** http://localhost:9090
- **OpenSearch Dashboards (Logs):** http://localhost:5601

## 💡 İpuçları

1. **Explore Kullan:** Grafana'nın Explore özelliği ile query'leri test edebilirsin
2. **Variables Ekle:** Dashboard'a değişkenler ekleyerek dinamik hale getirebilirsin
3. **Annotations:** Deployment'ları dashboard'da işaretleyebilirsin
4. **Templating:** Birden fazla environment için template kullanabilirsin

## 🎯 Test Et

Dashboard'un çalıştığını test etmek için:

```powershell
# Traffic oluştur
curl.exe "http://localhost:8080/api/market/instruments?page=0&size=10"
curl.exe http://localhost:8080/api/exchange-rates
curl.exe "http://localhost:8080/api/news?page=0&size=5"

# Dashboard'u yenile ve metrikleri gör
```

Dashboard'da değişiklikleri göreceksin! 🎉

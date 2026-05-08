# 📊 Kafka Log Pipeline - Dokümantasyon

## 🎯 Genel Bakış

Finans Portal'da log'lar şu akışla işlenir:

```
Backend (logback) → Kafka → Log Consumer Service → OpenSearch → OpenSearch Dashboards
```

## 🏗️ Mimari

### Bileşenler

1. **Backend (Spring Boot)**
   - Logback ile log üretir
   - Kafka Appender ile log'ları Kafka'ya gönderir
   - Topic: `finans-logs`

2. **Kafka + Zookeeper**
   - Message broker
   - Log'ları geçici olarak saklar
   - Asenkron iletişim sağlar

3. **Log Consumer Service (Spring Boot)**
   - Kafka'dan log'ları okur
   - JSON formatında parse eder
   - OpenSearch'e index eder

4. **OpenSearch**
   - Log'ları saklar
   - Full-text search sağlar
   - Index pattern: `finans-logs-YYYY-MM-DD`

5. **OpenSearch Dashboards**
   - Log'ları görselleştirir
   - Arama ve filtreleme
   - Dashboard'lar

## 📋 Log Formatı

Log'lar JSON formatında standardize edilmiştir:

```json
{
  "@timestamp": "2026-05-08T13:30:45.123Z",
  "level": "INFO",
  "service": "finans-backend",
  "environment": "prod",
  "message": "User logged in successfully",
  "logger_name": "com.finansportali.backend.api.AuthController",
  "thread_name": "http-nio-8080-exec-1",
  "trace_id": "068dce7de6349c885b87b0918f5baa11",
  "span_id": "b4117ff2cf514c99",
  "requestId": "abc123",
  "userId": "user@example.com",
  "endpoint": "/api/auth/login",
  "httpMethod": "POST",
  "statusCode": 200,
  "durationMs": 150,
  "clientIp": "192.168.1.100"
}
```

## 🚀 Başlangıç

### 1. Servisleri Başlat

```powershell
cd C:\Users\yigid\Desktop\finans-portali

# Tüm servisleri başlat
docker compose up -d

# Servislerin başlamasını bekle (2-3 dakika)
docker compose ps
```

### 2. Kafka Topic'i Kontrol Et

```powershell
# Topic'leri listele
docker exec finans-kafka kafka-topics --bootstrap-server localhost:9092 --list

# finans-logs topic'inin detaylarını gör
docker exec finans-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic finans-logs
```

### 3. Test Et

```powershell
# Test script'ini çalıştır
.\test-kafka-logs.ps1
```

## 🔍 Monitoring ve Debugging

### Backend Log'larını Kontrol Et

```powershell
# Backend log'larını izle
docker logs finans-backend -f

# Kafka appender log'larını filtrele
docker logs finans-backend 2>&1 | Select-String "kafka"
```

### Kafka Consumer Lag Kontrol Et

```powershell
# Consumer group durumunu kontrol et
docker exec finans-kafka kafka-consumer-groups `
  --bootstrap-server localhost:9092 `
  --group log-consumer-group `
  --describe
```

**Beklenen çıktı:**
```
GROUP              TOPIC        PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
log-consumer-group finans-logs  0          1234            1234            0
```

- **LAG = 0:** Tüm log'lar işlendi ✅
- **LAG > 0:** Bekleyen log'lar var ⚠️

### Log Consumer Service Kontrol Et

```powershell
# Log consumer log'larını izle
docker logs finans-log-consumer -f

# Health check
curl http://localhost:8081/actuator/health

# Metrics
curl http://localhost:8081/actuator/metrics
```

### OpenSearch'te Log'ları Kontrol Et

```powershell
# Index'leri listele
curl http://localhost:9200/_cat/indices/finans-logs-*?v

# Bugünkü log sayısını öğren
$today = Get-Date -Format "yyyy-MM-dd"
curl "http://localhost:9200/finans-logs-$today/_count"

# Son 10 log'u getir
$query = @{
    size = 10
    sort = @(@{ "@timestamp" = @{ order = "desc" } })
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:9200/finans-logs-$today/_search" `
  -Method POST -Body $query -ContentType "application/json"
```

## 📊 OpenSearch Dashboards Kullanımı

### 1. Index Pattern Oluştur

1. **OpenSearch Dashboards'a git:** http://localhost:5601
2. **Management → Stack Management → Index Patterns**
3. **Create index pattern** tıkla
4. **Index pattern name:** `finans-logs-*`
5. **Time field:** `@timestamp`
6. **Create index pattern** tıkla

### 2. Log'ları Keşfet

1. **Discover** sekmesine git
2. Sol taraftan `finans-logs-*` index pattern'ini seç
3. Time range'i ayarla (Last 15 minutes, Today, vb.)
4. Log'ları gör!

### 3. Faydalı Sorgular

**Sadece ERROR log'ları:**
```
level: "ERROR"
```

**Belirli bir endpoint:**
```
endpoint: "/api/market/instruments"
```

**Belirli bir kullanıcı:**
```
userId: "user@example.com"
```

**Yavaş request'ler (>1000ms):**
```
durationMs > 1000
```

**Trace ID ile arama:**
```
trace_id: "068dce7de6349c885b87b0918f5baa11"
```

## 🎨 Dashboard Örnekleri

### 1. Error Rate Dashboard

**Visualization:** Line Chart
**Query:**
```json
{
  "query": {
    "bool": {
      "filter": [
        { "term": { "level": "ERROR" } }
      ]
    }
  },
  "aggs": {
    "errors_over_time": {
      "date_histogram": {
        "field": "@timestamp",
        "interval": "1m"
      }
    }
  }
}
```

### 2. Top Endpoints

**Visualization:** Bar Chart
**Query:**
```json
{
  "aggs": {
    "top_endpoints": {
      "terms": {
        "field": "endpoint.keyword",
        "size": 10
      }
    }
  }
}
```

### 3. Response Time Distribution

**Visualization:** Histogram
**Query:**
```json
{
  "aggs": {
    "response_time_histogram": {
      "histogram": {
        "field": "durationMs",
        "interval": 100
      }
    }
  }
}
```

## 🔧 Konfigürasyon

### Backend (logback-spring.xml)

Kafka appender konfigürasyonu:

```xml
<appender name="KAFKA" class="com.github.danielwegener.logback.kafka.KafkaAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <!-- JSON formatında log -->
    </encoder>
    <topic>finans-logs</topic>
    <producerConfig>bootstrap.servers=kafka:29092</producerConfig>
    <producerConfig>acks=0</producerConfig>
    <producerConfig>linger.ms=1000</producerConfig>
</appender>
```

**Parametreler:**
- `acks=0`: Fire-and-forget (en hızlı, ama kayıp riski var)
- `linger.ms=1000`: 1 saniye bekle, batch gönder (performans için)
- `max.block.ms=0`: Kafka yoksa bloke olma

### Log Consumer (application.yml)

```yaml
spring:
  kafka:
    consumer:
      group-id: log-consumer-group
      auto-offset-reset: earliest  # İlk çalıştırmada en baştan oku
      enable-auto-commit: false    # Manuel commit
      max-poll-records: 100        # Batch size
    listener:
      ack-mode: manual             # Başarılı işlemden sonra commit

opensearch:
  index-prefix: finans-logs
```

## 🐛 Sorun Giderme

### Problem: Kafka'ya log gitmiyor

**Kontrol:**
```powershell
# Backend log'larında Kafka hatası var mı?
docker logs finans-backend 2>&1 | Select-String "kafka|error"

# Kafka çalışıyor mu?
docker ps | Select-String "kafka"

# Backend Kafka'ya ulaşabiliyor mu?
docker exec finans-backend ping -c 3 kafka
```

**Çözüm:**
```powershell
# Backend'i yeniden başlat
docker compose restart backend
```

### Problem: Log Consumer log'ları işlemiyor

**Kontrol:**
```powershell
# Log consumer log'larını kontrol et
docker logs finans-log-consumer --tail 50

# Consumer group lag'i kontrol et
docker exec finans-kafka kafka-consumer-groups `
  --bootstrap-server localhost:9092 `
  --group log-consumer-group `
  --describe
```

**Çözüm:**
```powershell
# Log consumer'ı yeniden başlat
docker compose restart log-consumer

# Eğer hala çalışmazsa, consumer group'u reset et
docker exec finans-kafka kafka-consumer-groups `
  --bootstrap-server localhost:9092 `
  --group log-consumer-group `
  --reset-offsets --to-earliest --topic finans-logs --execute
```

### Problem: OpenSearch'te log'lar görünmüyor

**Kontrol:**
```powershell
# OpenSearch çalışıyor mu?
curl http://localhost:9200/_cluster/health

# Index'ler var mı?
curl http://localhost:9200/_cat/indices/finans-logs-*?v

# Log consumer OpenSearch'e ulaşabiliyor mu?
docker exec finans-log-consumer curl -f http://opensearch:9200
```

**Çözüm:**
```powershell
# OpenSearch'ü yeniden başlat
docker compose restart opensearch

# Log consumer'ı yeniden başlat
docker compose restart log-consumer
```

### Problem: Kafka disk doldu

**Kontrol:**
```powershell
# Kafka volume boyutunu kontrol et
docker system df -v | Select-String "kafka"
```

**Çözüm:**
```powershell
# Eski log'ları temizle (Kafka retention policy)
# docker-compose.yml'de KAFKA_LOG_RETENTION_HOURS ayarla

# Veya volume'u temizle (DİKKAT: Tüm log'lar silinir!)
docker compose down
docker volume rm finans-portali_kafka_data
docker compose up -d
```

## 📈 Performans İyileştirme

### Backend Tarafı

**Batch gönderim:**
```xml
<producerConfig>linger.ms=1000</producerConfig>
<producerConfig>batch.size=16384</producerConfig>
```

**Compression:**
```xml
<producerConfig>compression.type=snappy</producerConfig>
```

### Log Consumer Tarafı

**Batch processing:**
```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 500  # Daha büyük batch
    listener:
      concurrency: 3         # 3 thread paralel işle
```

**Bulk indexing:**
```java
// LogConsumerService.java'da bulk API kullan
BulkRequest bulkRequest = new BulkRequest();
for (ConsumerRecord<String, String> record : records) {
    bulkRequest.add(new IndexRequest(indexName).source(record.value(), XContentType.JSON));
}
openSearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
```

## 🔗 İlgili Servisler

- **Kafka:** http://localhost:9092
- **Backend:** http://localhost:8080
- **Log Consumer:** http://localhost:8081
- **OpenSearch:** http://localhost:9200
- **OpenSearch Dashboards:** http://localhost:5601
- **Jaeger (Traces):** http://localhost:16686
- **Grafana (Metrics):** http://localhost:3000

## 📚 Ek Kaynaklar

- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [OpenSearch Documentation](https://opensearch.org/docs/latest/)
- [Logback Kafka Appender](https://github.com/danielwegener/logback-kafka-appender)
- [Spring Kafka](https://spring.io/projects/spring-kafka)

---

**Hazırladığım:** Kiro AI  
**Tarih:** 2026-05-08  
**Durum:** Kafka log pipeline hazır ve test edilmeye hazır

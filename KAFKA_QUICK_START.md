# 🚀 Kafka Log Pipeline - Hızlı Başlangıç

## ✅ Yapılanlar

Kafka log pipeline'ı başarıyla kuruldu:

```
Backend → Kafka → Log Consumer → OpenSearch
```

## 🎯 Başlat

```powershell
cd C:\Users\yigid\Desktop\finans-portali

# Servisleri başlat
docker compose up -d

# Durumu kontrol et (2-3 dakika bekle)
docker compose ps

# Test et
.\test-kafka-logs.ps1
```

## 📊 Kontrol Et

### 1. Kafka Topic
```powershell
docker exec finans-kafka kafka-topics --bootstrap-server localhost:9092 --list
```
**Beklenen:** `finans-logs` topic'i görünmeli

### 2. Consumer Lag
```powershell
docker exec finans-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group log-consumer-group --describe
```
**Beklenen:** LAG = 0 (tüm log'lar işlendi)

### 3. OpenSearch Index
```powershell
curl http://localhost:9200/_cat/indices/finans-logs-*?v
```
**Beklenen:** Bugünün index'i görünmeli (örn: `finans-logs-2026-05-08`)

### 4. Log Sayısı
```powershell
$today = Get-Date -Format "yyyy-MM-dd"
curl "http://localhost:9200/finans-logs-$today/_count"
```
**Beklenen:** `{"count": X}` (X > 0)

## 🎨 OpenSearch Dashboards

1. **Aç:** http://localhost:5601
2. **Management → Index Patterns → Create**
3. **Index pattern:** `finans-logs-*`
4. **Time field:** `@timestamp`
5. **Discover** sekmesinde log'ları gör!

## 🔍 Faydalı Sorgular

```
level: "ERROR"                              # Sadece hatalar
endpoint: "/api/market/instruments"         # Belirli endpoint
durationMs > 1000                           # Yavaş request'ler
trace_id: "abc123"                          # Trace ID ile arama
```

## 🐛 Sorun mu Var?

```powershell
# Backend log'ları
docker logs finans-backend --tail 50

# Log consumer log'ları
docker logs finans-log-consumer --tail 50

# Kafka consumer lag
docker exec finans-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group log-consumer-group --describe
```

## 📚 Detaylı Dokümantasyon

- **KAFKA_LOG_PIPELINE.md** - Tam dokümantasyon
- **test-kafka-logs.ps1** - Otomatik test script'i

## 🎉 Başarı Kriterleri

- [ ] Kafka ve Zookeeper çalışıyor
- [ ] Backend Kafka'ya log gönderiyor
- [ ] Log Consumer Kafka'dan okuyor
- [ ] OpenSearch'te log'lar görünüyor
- [ ] OpenSearch Dashboards'ta log'ları görebiliyorsun

Hepsi ✅ ise başarılı! 🎉

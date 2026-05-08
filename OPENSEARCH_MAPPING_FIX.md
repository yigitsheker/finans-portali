# OpenSearch Field Mapping Düzeltme Kılavuzu

## 🔴 Problem

`durationMs` field'ı **text** olarak mapping edilmiş, bu yüzden **Average aggregation** çalışmıyor.

```json
"durationMs": {
  "type": "text"  ❌ YANLIŞ - sayısal işlemler yapılamaz
}
```

Olması gereken:
```json
"durationMs": {
  "type": "long"  ✅ DOĞRU - Average, Sum, Max, Min yapılabilir
}
```

## 🎯 Çözüm

Index template oluşturup eski index'leri silmemiz gerekiyor. Yeni log'lar doğru mapping ile oluşacak.

## 🚀 Otomatik Düzeltme (Önerilen)

PowerShell script'i hazırladım. Tek komutla her şeyi halleder:

```powershell
cd C:\Users\yigid\Desktop\finans-portali
.\fix-opensearch-mappings.ps1
```

Script şunları yapar:
1. ✅ Eski index'leri siler (`finans-portal-logs-2026.05.07`, `finans-portal-logs-2026.05.08`)
2. ✅ Index template oluşturur (doğru mapping'lerle)
3. ✅ Backend'e test request'leri gönderir (yeni log'lar oluşur)
4. ✅ Mapping'lerin doğru olduğunu kontrol eder

**Süre:** ~1 dakika

## 🔧 Manuel Düzeltme (Alternatif)

Eğer script çalışmazsa manuel olarak yapabilirsin:

### Adım 1: Eski Index'leri Sil

```powershell
Invoke-WebRequest -Uri "http://localhost:9200/finans-portal-logs-2026.05.07" -Method DELETE -UseBasicParsing
Invoke-WebRequest -Uri "http://localhost:9200/finans-portal-logs-2026.05.08" -Method DELETE -UseBasicParsing
```

### Adım 2: Index Template Oluştur

Template JSON dosyasını oluştur (`opensearch-index-template.json`):

```json
{
  "index_patterns": ["finans-portal-logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0,
      "index.refresh_interval": "5s"
    },
    "mappings": {
      "properties": {
        "@timestamp": { "type": "date" },
        "level": { "type": "keyword" },
        "message": {
          "type": "text",
          "fields": {
            "keyword": { "type": "keyword", "ignore_above": 256 }
          }
        },
        "logger_name": { "type": "keyword" },
        "thread_name": { "type": "keyword" },
        "service": { "type": "keyword" },
        "environment": { "type": "keyword" },
        "requestId": { "type": "keyword" },
        "endpoint": { "type": "keyword" },
        "httpMethod": { "type": "keyword" },
        "statusCode": { "type": "integer" },
        "durationMs": { "type": "long" },
        "clientIp": { "type": "ip" },
        "userId": { "type": "keyword" },
        "username": { "type": "keyword" },
        "traceId": { "type": "keyword" },
        "spanId": { "type": "keyword" },
        "stack_trace": { "type": "text" }
      }
    }
  },
  "priority": 100
}
```

Template'i yükle:

```powershell
$body = Get-Content opensearch-index-template.json -Raw
Invoke-WebRequest -Uri "http://localhost:9200/_index_template/finans-portal-logs-template" -Method PUT -Body $body -ContentType "application/json" -UseBasicParsing
```

### Adım 3: Yeni Log'lar Oluştur

Backend'e birkaç request gönder:

```powershell
# 10 test request
for ($i = 1; $i -le 10; $i++) {
    Invoke-WebRequest -Uri "http://localhost:8080/api/v1/market/instruments" -Method GET -UseBasicParsing
    Start-Sleep -Milliseconds 500
}
```

### Adım 4: 30 Saniye Bekle

Log'ların Fluent Bit → OpenSearch'e ulaşması için:

```powershell
Start-Sleep -Seconds 30
```

### Adım 5: Mapping'i Kontrol Et

```powershell
Invoke-WebRequest -Uri "http://localhost:9200/finans-portal-logs-*/_mapping/field/durationMs?pretty" -UseBasicParsing | Select-Object -ExpandProperty Content
```

Çıktıda şunu görmeli sin:

```json
{
  "finans-portal-logs-2026.05.08": {
    "mappings": {
      "durationMs": {
        "full_name": "durationMs",
        "mapping": {
          "durationMs": {
            "type": "long"  ✅ DOĞRU!
          }
        }
      }
    }
  }
}
```

## 📊 OpenSearch Dashboards'da Index Pattern Refresh

Mapping değiştikten sonra index pattern'i refresh etmen gerekiyor:

1. **OpenSearch Dashboards'a git:** http://localhost:5601
2. **Management** → **Index Patterns**
3. **finans-portal-logs-*** seç
4. Sağ üstte **🔄 Refresh** butonuna tıkla
5. Field listesinde `durationMs` field'ını bul
6. **Type** sütununda **number** yazmalı (eskiden **string** yazıyordu)

## ✅ Doğrulama

### Test 1: Average Aggregation

1. **Visualize** → **Create visualization**
2. **Metric** seç
3. **Aggregation:** Average
4. **Field:** `durationMs` ← Artık bu field seçilebilir olmalı!
5. **Update** butonuna tıkla
6. Ortalama response time'ı görmeli sin (örn: 45.2 ms)

### Test 2: Dashboard Import

```powershell
# Dashboard'u import et
# Management → Saved Objects → Import → opensearch-dashboard-finans-portal.ndjson
```

Tüm visualization'lar çalışmalı, özellikle:
- ✅ **Ortalama Response Time** (Metric)
- ✅ **En Yavaş Endpoint'ler** (Table with Max aggregation)

## 🐛 Sorun Giderme

### "Field durationMs not found"

**Sorun:** Henüz durationMs field'ı yok

**Çözüm:**
1. Backend'in çalıştığından emin ol
2. Daha fazla request gönder (yukarıdaki for loop'u tekrar çalıştır)
3. 30 saniye bekle
4. Tekrar kontrol et

### "Type is still text"

**Sorun:** Mapping hala text

**Çözüm:**
1. Template'in doğru oluşturulduğunu kontrol et:
   ```powershell
   Invoke-WebRequest -Uri "http://localhost:9200/_index_template/finans-portal-logs-template" -UseBasicParsing
   ```
2. Eski index'lerin silindiğinden emin ol:
   ```powershell
   Invoke-WebRequest -Uri "http://localhost:9200/_cat/indices/finans-portal-logs-*?v" -UseBasicParsing
   ```
3. Eğer eski index'ler hala varsa, tekrar sil
4. Yeni log'lar oluştur

### "Cannot select field in Average aggregation"

**Sorun:** Field seçilemiyor

**Çözüm:**
1. Index pattern'i refresh et (yukarıdaki adımlar)
2. Browser cache'i temizle (Ctrl+Shift+Delete)
3. OpenSearch Dashboards'u yeniden yükle (F5)

## 📈 Beklenen Sonuç

Mapping düzeltildikten sonra:

✅ `durationMs` field'ı **number** tipinde
✅ Average, Sum, Max, Min aggregation'lar çalışıyor
✅ "Ortalama Response Time" visualization çalışıyor
✅ "En Yavaş Endpoint'ler" visualization çalışıyor
✅ Dashboard'daki tüm metrikler doğru

## 🎓 Neden Bu Oldu?

OpenSearch ilk log'u aldığında field tiplerini **otomatik tahmin eder** (dynamic mapping).

İlk log'da `durationMs` muhtemelen string olarak geldi:
```json
{
  "durationMs": "123"  ← String (tırnak içinde)
}
```

OpenSearch bunu text olarak mapping etti. Sonraki log'larda sayı olarak gelse bile mapping değişmez.

**Çözüm:** Index template ile mapping'i **önceden tanımla**. Böylece OpenSearch tahmin etmez, bizim belirlediğimiz tipi kullanır.

## 💡 Gelecekte Önleme

Index template artık var, yeni index'ler otomatik olarak doğru mapping ile oluşacak:

- `finans-portal-logs-2026.05.09` → ✅ durationMs: long
- `finans-portal-logs-2026.05.10` → ✅ durationMs: long
- `finans-portal-logs-2026.06.01` → ✅ durationMs: long

Template'i silmediğin sürece sorun tekrarlanmaz.

## 📞 Yardım

Sorun devam ederse:

1. Script çıktısını kontrol et (hata mesajları var mı?)
2. Backend log'larını kontrol et (`docker logs finans-backend`)
3. Fluent Bit log'larını kontrol et (`docker logs fluent-bit`)
4. OpenSearch log'larını kontrol et (`docker logs opensearch`)

---

**Hazır mısın?** Script'i çalıştır:

```powershell
cd C:\Users\yigid\Desktop\finans-portali
.\fix-opensearch-mappings.ps1
```

🚀 Başarılar!

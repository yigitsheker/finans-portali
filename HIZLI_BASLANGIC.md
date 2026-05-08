# 🚀 Hızlı Başlangıç - İlk Visualization'ı 2 Dakikada Oluştur

## ✅ Hazırlık Tamam

- ✅ OpenSearch çalışıyor
- ✅ Field mapping'ler düzeltildi (`durationMs` = long)
- ✅ 5451 log entry var
- ✅ Backend çalışıyor

## 📊 İlk Visualization: Ortalama Response Time

### Adım 1: OpenSearch Dashboards'a Git

```
http://localhost:5601
```

### Adım 2: Visualize Sayfasına Git

Sol menüden:
- **☰** (hamburger menü) tıkla
- **Visualize** seç
- **Create visualization** butonu

### Adım 3: Metric Seç

- **Metric** kutusuna tıkla (büyük sayı gösterir)

### Adım 4: Index Pattern Seç

- **finans-portal-logs-*** seç

### Adım 5: Metric Ayarla

Sağ tarafta **Metrics** bölümü:

1. **Aggregation:** Dropdown'dan `Average` seç
2. **Field:** Dropdown'dan `durationMs` seç
3. **Custom label:** `Ortalama Response Time (ms)` yaz

### Adım 6: Update

- **▶ Update** butonuna bas (sağ üstte)

### Adım 7: Sonuç

Büyük bir sayı göreceksin, örneğin:

```
45.2
Ortalama Response Time (ms)
```

Bu, tüm API request'lerinin ortalama yanıt süresi (milisaniye).

### Adım 8: Kaydet

- Üstte **Save** butonu
- **Title:** `Ortalama Response Time` yaz
- **Save** butonu

**Tebrikler!** İlk visualization'ını oluşturdun! 🎉

---

## 📊 İkinci Visualization: Log Seviyeleri (Pie Chart)

### Adım 1-4: Aynı (Visualize → Create → Pie → Index pattern)

### Adım 5: Bucket Ekle

Sağ tarafta **Buckets** bölümü:

1. **Add** butonu
2. **Split slices** seç

### Adım 6: Terms Aggregation

1. **Aggregation:** `Terms` seç
2. **Field:** `level.keyword` seç
3. **Size:** `10` (varsayılan)
4. **Order by:** `Metric: Count`
5. **Order:** `Descending`

### Adım 7: Update ve Kaydet

- **▶ Update**
- INFO, WARN, ERROR dilimleri göreceksin
- **Save** → `Log Seviyeleri`

---

## 📊 Üçüncü Visualization: API Endpoint Kullanımı (Bar Chart)

### Adım 1-4: Visualize → Create → Vertical Bar → Index pattern

### Adım 5: X-Axis Ekle

**Buckets** bölümü:

1. **Add** → **X-axis**
2. **Aggregation:** `Terms`
3. **Field:** `endpoint.keyword`
4. **Size:** `15`
5. **Order by:** `Metric: Count`
6. **Order:** `Descending`

### Adım 6: Update ve Kaydet

- **▶ Update**
- En çok kullanılan endpoint'leri göreceksin
- **Save** → `API Endpoint Kullanımı`

---

## 📊 Dashboard Oluştur

### Adım 1: Dashboard Sayfası

- **☰** → **Dashboard** → **Create dashboard**

### Adım 2: Visualization Ekle

- **Add** butonu (üstte)
- **Add from library** seç
- Oluşturduğun 3 visualization'ı seç:
  - ✅ Ortalama Response Time
  - ✅ Log Seviyeleri
  - ✅ API Endpoint Kullanımı

### Adım 3: Düzenle

- Visualization'ları sürükle-bırak ile yerleştir
- Köşelerden tutarak boyutlandır

### Adım 4: Kaydet

- **Save** butonu (üstte)
- **Title:** `Finans Portal Dashboard`
- **Save**

---

## 🎛️ Dashboard Kullanımı

### Zaman Filtresi

Sağ üstte:
- **Last 15 minutes** seç (canlı monitoring)

### Auto-Refresh

Sağ üstte saat ikonu:
- **Refresh every 30 seconds** seç

### Filter Ekleme

Üstte **+ Add filter:**

**Örnek: Sadece ERROR log'ları**
- Field: `level`
- Operator: `is`
- Value: `ERROR`
- **Save**

Dashboard sadece ERROR log'larını gösterecek.

---

## 🐛 Sorun mu Var?

### "No results found"

**Çözüm:** Zaman filtresini genişlet
- Sağ üstte zaman filtresi → **Last 24 hours**

### "Field durationMs not found"

**Çözüm:** Index pattern'i refresh et
1. **Management** → **Index Patterns**
2. **finans-portal-logs-*** seç
3. Sağ üstte **🔄 Refresh** butonu

### "Cannot select durationMs in Average aggregation"

**Çözüm:** Field type'ı kontrol et
1. **Management** → **Index Patterns** → **finans-portal-logs-***
2. Field listesinde `durationMs` ara
3. **Type** sütununda **number** yazmalı
4. Eğer **string** yazıyorsa:
   ```powershell
   cd C:\Users\yigid\Desktop\finans-portali
   .\fix-opensearch-mappings.ps1
   ```

---

## 📚 Daha Fazla Visualization

Detaylı kılavuz için:

```
C:\Users\yigid\Desktop\finans-portali\MANUEL_DASHBOARD_OLUSTURMA.md
```

Bu kılavuzda şunlar var:
- ✅ Zaman Bazlı Log Akışı (Line Chart)
- ✅ En Yavaş Endpoint'ler (Table)
- ✅ Kullanıcı Aktivitesi (Pie Chart)
- ✅ HTTP Status Code Dağılımı (Bar Chart)
- ✅ En Aktif Logger'lar (Horizontal Bar)

---

## 🎯 Özet

1. **Visualize → Create visualization**
2. **Type seç** (Metric, Pie, Bar, Line, Table)
3. **Index pattern:** `finans-portal-logs-*`
4. **Metrics ve Buckets ayarla**
5. **▶ Update**
6. **💾 Save**
7. **Dashboard'a ekle**

**Başarılar!** 🚀

---

## ⚠️ Not: /api/v1/market/quotes Endpoint'inde Hata Var

Test log'larında `/api/v1/market/quotes` endpoint'i **500 Internal Server Error** veriyor.

Bu endpoint'i düzeltmek ister misin? Dashboard'da bu hatayı görebilirsin:
- Filter: `endpoint: /api/v1/market/quotes AND statusCode: 500`

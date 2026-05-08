# 📊 Manuel Dashboard Oluşturma Kılavuzu

Import çalışmadığı için manuel olarak visualization'ları oluşturacağız. Adım adım takip et.

## 🎯 Oluşturacağımız Visualization'lar

1. ✅ **Ortalama Response Time** (Metric) - En basit, buradan başla
2. ✅ **Log Seviyeleri** (Pie Chart)
3. ✅ **Zaman Bazlı Log Akışı** (Line Chart)
4. ✅ **API Endpoint Kullanımı** (Bar Chart)
5. ✅ **En Yavaş Endpoint'ler** (Table)

---

## 📋 Başlamadan Önce

### Index Pattern'i Kontrol Et

1. **Management → Stack Management → Index Patterns**
2. **finans-portal-logs-*** var mı kontrol et
3. Yoksa oluştur:
   - **Create index pattern**
   - **Index pattern name:** `finans-portal-logs-*`
   - **Time field:** `@timestamp`
   - **Create**

### Field'ları Kontrol Et

1. Index pattern'e tıkla
2. Field listesinde şunları ara:
   - ✅ `durationMs` - Type: **number** (long)
   - ✅ `statusCode` - Type: **number** (integer)
   - ✅ `level` - Type: **string** (keyword)
   - ✅ `endpoint` - Type: **string** (keyword)
   - ✅ `message` - Type: **string** (text)

3. Eğer `durationMs` **string** görünüyorsa:
   - Sağ üstte **🔄 Refresh** butonuna bas
   - Browser cache'i temizle (Ctrl+Shift+Delete)
   - Sayfayı yenile (F5)

---

## 1️⃣ Ortalama Response Time (Metric)

**En basit visualization - buradan başla!**

### Adımlar:

1. **Sol menü → Visualize → Create visualization**

2. **Visualization type seç:**
   - **Metric** seç (büyük sayı gösterir)

3. **Index pattern seç:**
   - **finans-portal-logs-*** seç

4. **Sağ tarafta Metrics bölümü:**
   - **Aggregation:** `Average` seç
   - **Field:** `durationMs` seç
   - **Custom label:** `Ortalama Response Time (ms)` yaz

5. **▶ Update** butonuna bas

6. **Sonuç:**
   - Büyük bir sayı göreceksin (örn: 45.2)
   - Bu ortalama response time (ms)

7. **💾 Save:**
   - Üstte **Save** butonu
   - **Title:** `Ortalama Response Time`
   - **Save**

### Sorun Giderme:

❌ **"Field durationMs not found"**
- Index pattern'i refresh et
- Daha fazla log oluştur (backend'e request gönder)

❌ **"Cannot select durationMs"**
- Field type'ı kontrol et (number olmalı)
- Eğer string ise mapping düzeltme script'ini tekrar çalıştır

---

## 2️⃣ Log Seviyeleri (Pie Chart)

**INFO, WARN, ERROR dağılımını gösterir**

### Adımlar:

1. **Visualize → Create visualization**

2. **Type:** `Pie` seç

3. **Index pattern:** `finans-portal-logs-*`

4. **Sağ tarafta Buckets bölümü:**
   - **Add → Split slices** tıkla
   - **Aggregation:** `Terms` seç
   - **Field:** `level.keyword` seç
   - **Size:** `10` (varsayılan)
   - **Order by:** `Metric: Count`
   - **Order:** `Descending`

5. **▶ Update** butonuna bas

6. **Sonuç:**
   - Pie chart göreceksin
   - INFO (yeşil), WARN (sarı), ERROR (kırmızı) dilimleri

7. **Görünüm ayarları (Options):**
   - **Donut:** İstersen işaretle (ortası boş olur)
   - **Show labels:** İşaretle
   - **Show values:** İşaretle

8. **💾 Save:** `Log Seviyeleri`

---

## 3️⃣ Zaman Bazlı Log Akışı (Line Chart)

**Zamana göre log sayısını gösterir**

### Adımlar:

1. **Visualize → Create visualization**

2. **Type:** `Line` seç

3. **Index pattern:** `finans-portal-logs-*`

4. **X-Axis (Buckets):**
   - **Add → X-axis** tıkla
   - **Aggregation:** `Date Histogram` seç
   - **Field:** `@timestamp` seç
   - **Minimum interval:** `Auto` (varsayılan)

5. **Split series (Buckets):**
   - **Add → Split series** tıkla
   - **Sub aggregation:** `Terms` seç
   - **Field:** `level.keyword` seç
   - **Size:** `5`
   - **Order by:** `Metric: Count`

6. **▶ Update** butonuna bas

7. **Sonuç:**
   - Zaman çizgisi göreceksin
   - Her log seviyesi için ayrı çizgi (INFO, WARN, ERROR)

8. **💾 Save:** `Zaman Bazlı Log Akışı`

---

## 4️⃣ API Endpoint Kullanımı (Bar Chart)

**En çok kullanılan endpoint'leri gösterir**

### Adımlar:

1. **Visualize → Create visualization**

2. **Type:** `Vertical Bar` seç

3. **Index pattern:** `finans-portal-logs-*`

4. **X-Axis (Buckets):**
   - **Add → X-axis** tıkla
   - **Aggregation:** `Terms` seç
   - **Field:** `endpoint.keyword` seç
   - **Size:** `15`
   - **Order by:** `Metric: Count`
   - **Order:** `Descending`

5. **▶ Update** butonuna bas

6. **Sonuç:**
   - Bar chart göreceksin
   - En çok kullanılan endpoint'ler en yüksek barlar

7. **Görünüm ayarları (Options):**
   - **X-axis labels:** Rotate 45° (eğer endpoint isimleri uzunsa)

8. **💾 Save:** `API Endpoint Kullanımı`

---

## 5️⃣ En Yavaş Endpoint'ler (Table)

**Hangi endpoint'ler en yavaş?**

### Adımlar:

1. **Visualize → Create visualization**

2. **Type:** `Data Table` seç

3. **Index pattern:** `finans-portal-logs-*`

4. **Metrics:**
   - **Aggregation:** `Max` seç
   - **Field:** `durationMs` seç
   - **Custom label:** `Max Response Time (ms)`

5. **Buckets:**
   - **Add → Split rows** tıkla
   - **Aggregation:** `Terms` seç
   - **Field:** `endpoint.keyword` seç
   - **Size:** `10`
   - **Order by:** `Metric: Max durationMs`
   - **Order:** `Descending`

6. **▶ Update** butonuna bas

7. **Sonuç:**
   - Tablo göreceksin
   - Endpoint | Max Response Time
   - En yavaş endpoint'ler üstte

8. **💾 Save:** `En Yavaş Endpoint'ler`

---

## 6️⃣ Dashboard Oluştur

**Tüm visualization'ları bir araya getir**

### Adımlar:

1. **Sol menü → Dashboard → Create dashboard**

2. **Add → Add from library** tıkla

3. **Oluşturduğun visualization'ları seç:**
   - ✅ Ortalama Response Time
   - ✅ Log Seviyeleri
   - ✅ Zaman Bazlı Log Akışı
   - ✅ API Endpoint Kullanımı
   - ✅ En Yavaş Endpoint'ler

4. **Düzenle:**
   - Visualization'ları sürükle-bırak ile yerleştir
   - Köşelerden tutarak boyutlandır

5. **Önerilen düzen:**
   ```
   ┌─────────────────────────────────────────┐
   │  [Ortalama RT]  [Log Seviyeleri]       │
   ├─────────────────────────────────────────┤
   │  [Zaman Bazlı Log Akışı - Geniş]       │
   ├─────────────────────────────────────────┤
   │  [API Endpoint]  [En Yavaş Endpoint]   │
   └─────────────────────────────────────────┘
   ```

6. **💾 Save:**
   - **Title:** `Finans Portal - Log Dashboard`
   - **Save**

---

## 🎛️ Dashboard Ayarları

### Zaman Filtresi

Sağ üstte zaman filtresi:
- **Last 15 minutes** - Canlı monitoring
- **Last 1 hour** - Genel bakış
- **Last 24 hours** - Günlük analiz
- **Last 7 days** - Haftalık trend

### Auto-Refresh

Sağ üstte saat ikonu:
- **Refresh every 30 seconds** - Canlı monitoring
- **Refresh every 1 minute** - Normal kullanım
- **Off** - Manuel refresh

### Global Filter Ekleme

Üstte **+ Add filter:**

**Örnek 1: Sadece ERROR log'ları**
- Field: `level`
- Operator: `is`
- Value: `ERROR`

**Örnek 2: Belirli bir endpoint**
- Field: `endpoint`
- Operator: `is`
- Value: `/api/v1/market/instruments`

**Örnek 3: Yavaş request'ler**
- Field: `durationMs`
- Operator: `is greater than`
- Value: `100`

---

## 🔍 Ek Visualization'lar (İsteğe Bağlı)

### 6. Kullanıcı Aktivitesi (Pie Chart)

**Hangi kullanıcılar aktif?**

1. **Type:** Pie
2. **Buckets → Split slices:**
   - Aggregation: `Terms`
   - Field: `userId.keyword`
   - Size: `10`
3. **Filter ekle (Query bar):**
   ```
   userId: *
   ```
   (Sadece userId olan log'ları göster)

### 7. HTTP Status Code Dağılımı (Bar Chart)

**200, 404, 500 dağılımı**

1. **Type:** Vertical Bar
2. **X-Axis:**
   - Aggregation: `Terms`
   - Field: `statusCode`
   - Size: `10`
3. **Renklendirme:**
   - 200-299: Yeşil (başarılı)
   - 400-499: Sarı (client error)
   - 500-599: Kırmızı (server error)

### 8. En Aktif Logger'lar (Horizontal Bar)

**Hangi class'lar çok log üretiyor?**

1. **Type:** Horizontal Bar
2. **Y-Axis:**
   - Aggregation: `Terms`
   - Field: `logger_name.keyword`
   - Size: `10`

---

## 🐛 Sık Karşılaşılan Sorunlar

### "No results found"

**Sorun:** Visualization boş

**Çözümler:**
1. Zaman filtresini genişlet (Last 24 hours)
2. Query bar'da filter var mı kontrol et (temizle)
3. Index pattern'de veri var mı kontrol et:
   - **Discover** sayfasına git
   - Index pattern: `finans-portal-logs-*`
   - Veri görünüyor mu?

### "Field not found"

**Sorun:** Field seçilemiyor

**Çözümler:**
1. Index pattern'i refresh et
2. Field mapping'ini kontrol et (Management → Index Patterns)
3. Daha fazla log oluştur (backend'e request gönder)

### "Cannot aggregate on text field"

**Sorun:** Text field'a aggregation yapılamıyor

**Çözüm:**
- Text field yerine `.keyword` versiyonunu kullan
- Örnek: `message` yerine `message.keyword`
- Örnek: `endpoint` yerine `endpoint.keyword`

### Visualization yavaş yükleniyor

**Sorun:** Dashboard açılması uzun sürüyor

**Çözümler:**
1. Zaman filtresini daralt (Last 15 minutes)
2. Aggregation size'ı küçült (15 → 10)
3. Gereksiz visualization'ları kaldır

---

## ✅ Kontrol Listesi

Dashboard oluşturulduktan sonra kontrol et:

- [ ] Ortalama Response Time sayı gösteriyor
- [ ] Log Seviyeleri pie chart'ı dolu
- [ ] Zaman Bazlı Log Akışı çizgi gösteriyor
- [ ] API Endpoint bar chart'ı dolu
- [ ] En Yavaş Endpoint'ler tablosu dolu
- [ ] Zaman filtresi çalışıyor
- [ ] Auto-refresh çalışıyor
- [ ] Global filter eklenebiliyor
- [ ] Drill-down çalışıyor (bir değere tıkla → filter)

---

## 💡 İpuçları

### Hızlı Test

Dashboard'u test etmek için backend'e request gönder:

```powershell
for ($i = 1; $i -le 20; $i++) {
    Invoke-WebRequest -Uri "http://localhost:8080/api/v1/market/instruments" -UseBasicParsing
    Start-Sleep -Milliseconds 500
}
```

30 saniye bekle, dashboard'u refresh et.

### Renklendirme

Visualization'larda renk ayarları:
- **Options → Color schema:** Green to Red
- **Options → Color by:** Terms

### Export/Import

Visualization'ları yedekle:
1. **Management → Saved Objects**
2. Visualization'ı seç
3. **Export** tıkla
4. JSON dosyası indir

Başka bir ortama import et:
1. **Management → Saved Objects**
2. **Import** tıkla
3. JSON dosyasını seç

---

## 📞 Yardım

Sorun devam ederse:

1. **Discover sayfasında veri var mı kontrol et**
2. **Index pattern field listesini kontrol et**
3. **Browser console'da hata var mı bak** (F12)
4. **OpenSearch Dashboards log'larını kontrol et:**
   ```powershell
   docker logs opensearch-dashboards
   ```

---

**Başarılar!** Adım adım takip edersen 15 dakikada dashboard'un hazır olur. 🚀

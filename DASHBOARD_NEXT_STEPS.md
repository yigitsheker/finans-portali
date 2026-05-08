# ✅ Mapping Düzeltildi - Şimdi Ne Yapmalısın?

## 🎉 Başarılı!

Field mapping'ler düzeltildi:
- ✅ `durationMs` = **long** (artık Average aggregation çalışacak)
- ✅ `statusCode` = **integer** (artık sayısal filtreler çalışacak)
- ✅ Yeni index oluşturuldu: `finans-portal-logs-2026.05.08`
- ✅ 1339 log entry var

## 📋 Şimdi Yapılacaklar

### Seçenek 1: Dashboard'u Import Et (Önerilen - 2 dakika)

1. **OpenSearch Dashboards'a git:**
   ```
   http://localhost:5601
   ```

2. **Sol menüden Management'a git:**
   - ☰ (hamburger menü) → **Management** → **Saved Objects**

3. **Import butonuna tıkla:**
   - Sağ üstte **Import** butonu

4. **Dosyayı seç:**
   ```
   C:\Users\yigid\Desktop\finans-portali\opensearch-dashboard-finans-portal.ndjson
   ```

5. **Import'a tıkla**
   - "Successfully imported 11 objects" mesajını göreceksin

6. **Dashboard'u aç:**
   - ☰ → **Dashboards** → **Finans Portal - Ana Dashboard**

7. **Zaman filtresini ayarla:**
   - Sağ üstte **Last 15 minutes** seç

8. **Auto-refresh aç:**
   - Sağ üstte saat ikonu → **Refresh every 30 seconds**

**Bitti!** Dashboard'un hazır. 🎉

---

### Seçenek 2: Manuel Oluştur (Öğrenmek istersen - 15 dakika)

#### Adım 1: Index Pattern'i Refresh Et

1. **Management → Index Patterns**
2. **finans-portal-logs-*** seç
3. Sağ üstte **🔄 Refresh** butonuna tıkla
4. Field listesinde `durationMs` field'ını bul
5. **Type** sütununda **number** yazmalı ✅

#### Adım 2: İlk Visualization'ı Oluştur (Ortalama Response Time)

1. **☰ → Visualize → Create visualization**
2. **Metric** seç
3. **Index pattern:** `finans-portal-logs-*`
4. **Aggregation:** Average
5. **Field:** `durationMs` ← Artık seçilebilir!
6. **Update** butonuna tıkla
7. Ortalama response time'ı göreceksin (örn: 45.2 ms)
8. **Save** → İsim: "Ortalama Response Time"

#### Adım 3: İkinci Visualization (Log Seviyeleri)

1. **Create visualization → Pie**
2. **Buckets → Add → Split slices**
3. **Aggregation:** Terms
4. **Field:** `level.keyword`
5. **Update**
6. INFO, WARN, ERROR dağılımını göreceksin
7. **Save** → İsim: "Log Seviyeleri Dağılımı"

#### Adım 4: Üçüncü Visualization (API Endpoint Kullanımı)

1. **Create visualization → Vertical Bar**
2. **Buckets → Add → X-axis**
3. **Aggregation:** Terms
4. **Field:** `endpoint.keyword`
5. **Size:** 15
6. **Update**
7. En çok kullanılan endpoint'leri göreceksin
8. **Save** → İsim: "API Endpoint Kullanımı"

#### Adım 5: Dördüncü Visualization (En Yavaş Endpoint'ler)

1. **Create visualization → Data Table**
2. **Metrics → Aggregation:** Max
3. **Field:** `durationMs`
4. **Buckets → Add → Split rows**
5. **Aggregation:** Terms
6. **Field:** `endpoint.keyword`
7. **Order by:** Metric: Max durationMs
8. **Update**
9. En yavaş endpoint'leri göreceksin
10. **Save** → İsim: "En Yavaş Endpoint'ler"

#### Adım 6: Dashboard Oluştur

1. **☰ → Dashboards → Create dashboard**
2. **Add** butonuna tıkla
3. Oluşturduğun visualization'ları seç
4. Sürükle-bırak ile düzenle
5. **Save** → İsim: "Finans Portal Dashboard"

---

## 🎯 Test Et

### Test 1: Average Aggregation Çalışıyor mu?

1. **Visualize → Create visualization → Metric**
2. **Aggregation:** Average
3. **Field:** `durationMs` ← Seçilebiliyor mu? ✅
4. **Update** → Sayı görünüyor mu? ✅

### Test 2: Filter Çalışıyor mu?

1. Dashboard'da **+ Add filter**
2. **Field:** `endpoint`
3. **Operator:** `is`
4. **Value:** `/api/v1/market/instruments`
5. **Save**
6. Dashboard sadece o endpoint'i gösteriyor mu? ✅

### Test 3: Drill-down Çalışıyor mu?

1. "Log Seviyeleri" pie chart'ında ERROR dilimini tıkla
2. **Filter for value** seç
3. Dashboard sadece ERROR log'ları gösteriyor mu? ✅

---

## 📊 Dashboard'da Göreceğin Metrikler

### Üst Satır (Özet Kartlar)
- **Log Seviyeleri Dağılımı** (Pie) - INFO/WARN/ERROR oranları
- **Kullanıcı Aktivitesi** (Pie) - Hangi kullanıcılar aktif
- **Ortalama Response Time** (Metric) - Ortalama yanıt süresi (ms)
- **En Sık Hatalar** (Table) - ERROR log'ları

### Orta Satır (Zaman Serisi)
- **Zaman Bazlı Log Akışı** (Line) - Zamana göre log akışı (INFO/WARN/ERROR)

### Alt Satırlar (Detaylı Tablolar)
- **En Aktif Logger'lar** (Bar) - Hangi class'lar çok log üretiyor
- **API Endpoint Kullanımı** (Bar) - En çok kullanılan endpoint'ler
- **En Yavaş Endpoint'ler** (Table) - Performance sorunları
- **Portfolio İşlemleri** (Table) - Business event log'ları

---

## 💡 Kullanım İpuçları

### Canlı Monitoring İçin
- Zaman filtresi: **Last 15 minutes**
- Auto-refresh: **30 seconds**
- Tam ekran: **F11**

### Hata Analizi İçin
- Zaman filtresi: **Last 24 hours**
- Global filter: `level: ERROR`
- "En Sık Hatalar" tablosuna bak

### Performance Analizi İçin
- Zaman filtresi: **Last 1 hour**
- "Ortalama Response Time" metriğini kontrol et
- "En Yavaş Endpoint'ler" tablosuna bak
- Filter: `durationMs > 100` (100ms'den yavaş olanlar)

### Kullanıcı Aktivitesi İçin
- "Kullanıcı Aktivitesi" pie chart'ında bir kullanıcıya tıkla
- Filter for value
- "Portfolio İşlemleri" tablosunda o kullanıcının işlemlerini gör

---

## 🐛 Sorun mu Var?

### Dashboard boş görünüyor
**Çözüm:** Zaman filtresini **Last 24 hours** yap

### Field seçilemiyor
**Çözüm:** Index pattern'i refresh et (Management → Index Patterns → Refresh)

### "No results found"
**Çözüm:** Daha fazla log oluştur:
```powershell
for ($i = 1; $i -le 20; $i++) {
    Invoke-WebRequest -Uri "http://localhost:8080/api/v1/market/instruments" -UseBasicParsing
}
```

### Import başarısız
**Çözüm:** Index pattern'in var olduğundan emin ol:
- Management → Index Patterns → `finans-portal-logs-*` var mı?

---

## 📞 Yardım

Detaylı kılavuz için:
```
C:\Users\yigid\Desktop\finans-portali\OPENSEARCH_DASHBOARD_IMPORT.md
```

Mapping sorunları için:
```
C:\Users\yigid\Desktop\finans-portali\OPENSEARCH_MAPPING_FIX.md
```

---

**Hazır mısın?** Dashboard'u import et ve keyfini çıkar! 🚀

http://localhost:5601

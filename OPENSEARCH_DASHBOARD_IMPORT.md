# OpenSearch Dashboard Import Kılavuzu

## 📊 Dashboard İçeriği

Bu dashboard şunları içerir:

### Visualizations (Görselleştirmeler)
1. **Log Seviyeleri Dağılımı** - Pie chart (INFO, WARN, ERROR, DEBUG)
2. **Zaman Bazlı Log Akışı** - Line chart (zamana göre log akışı)
3. **En Aktif Logger'lar** - Horizontal bar chart
4. **En Sık Hatalar** - Data table (ERROR logları)
5. **API Endpoint Kullanımı** - Vertical bar chart
6. **Ortalama Response Time** - Metric (ms)
7. **En Yavaş Endpoint'ler** - Data table
8. **Kullanıcı Aktivitesi** - Pie chart
9. **Portfolio İşlemleri** - Data table

### Dashboard
- **Finans Portal - Ana Dashboard** - Tüm visualization'ları içeren ana dashboard

## 🚀 Import Adımları

### Adım 1: OpenSearch Dashboards'a Git
```
http://localhost:5601
```

### Adım 2: Management Sayfasına Git
1. Sol menüden **☰** (hamburger menü) tıkla
2. **Management** seçeneğine tıkla
3. **Stack Management** altında **Saved Objects** seç

### Adım 3: Import İşlemi
1. Sağ üstte **Import** butonuna tıkla
2. **Import** popup'ında **Select file** butonuna tıkla
3. Dosya seçicide şu dosyayı seç:
   ```
   C:\Users\yigid\Desktop\finans-portali\opensearch-dashboard-finans-portal.ndjson
   ```
4. **Import** butonuna tıkla

### Adım 4: Import Sonucu
Başarılı olursa şu mesajı göreceksin:
```
✓ Successfully imported 11 objects
```

**Not:** Eğer "Index pattern conflicts" hatası alırsan:
1. **Automatically overwrite conflicts** seçeneğini işaretle
2. **Import** butonuna tekrar tıkla

### Adım 5: Dashboard'u Aç
1. Sol menüden **☰** tıkla
2. **Dashboards** seç
3. **Finans Portal - Ana Dashboard** ismini bul ve tıkla

## 🎯 Dashboard Kullanımı

### Zaman Filtresi
Sağ üstte zaman filtresini ayarla:
- **Last 15 minutes** - Canlı monitoring
- **Last 1 hour** - Genel bakış
- **Last 24 hours** - Günlük analiz
- **Last 7 days** - Haftalık trend

### Auto-Refresh
Sağ üstte saat ikonuna tıkla:
- **Refresh every 30 seconds** - Canlı monitoring
- **Refresh every 1 minute** - Normal kullanım
- **Refresh every 5 minutes** - Arka plan monitoring

### Filtreleme
Dashboard üzerinde herhangi bir değere tıklayarak filtre ekleyebilirsin:
- Pie chart'ta bir dilime tıkla → o seviyeyi filtrele
- Bar chart'ta bir bara tıkla → o değeri filtrele
- Table'da bir satıra tıkla → detaylara in

### Global Filtre Ekleme
Üstte **+ Add filter** butonuna tıkla:
- **Field:** `level`
- **Operator:** `is`
- **Value:** `ERROR`
- **Save**

### Drill-down
Herhangi bir visualization'a tıklayarak:
- **Filter for value** - Bu değeri filtrele
- **Filter out value** - Bu değeri hariç tut
- **View in Discover** - Discover'da detaylara bak

## 📈 Dashboard Düzeni

```
┌─────────────────────────────────────────────────────────────┐
│  Finans Portal - Ana Dashboard              [Time] [Refresh]│
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ Log      │  │ Kullanıcı│  │ Avg      │  │ Hatalar  │   │
│  │ Seviyeleri│  │ Aktivite │  │ Response │  │          │   │
│  │ [Pie]    │  │ [Pie]    │  │ [Metric] │  │ [Table]  │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
│                                                               │
│  ┌──────────────────────────────────────────────┐           │
│  │ Zaman Bazlı Log Akışı                        │           │
│  │  [Line Chart - INFO/WARN/ERROR/DEBUG]        │           │
│  └──────────────────────────────────────────────┘           │
│                                                               │
│  ┌─────────────────────┐  ┌─────────────────────┐          │
│  │ En Aktif Logger'lar │  │ API Endpoint        │          │
│  │  [Horizontal Bar]   │  │ Kullanımı           │          │
│  │                     │  │  [Vertical Bar]     │          │
│  └─────────────────────┘  └─────────────────────┘          │
│                                                               │
│  ┌─────────────────────┐  ┌─────────────────────┐          │
│  │ En Yavaş            │  │ Portfolio           │          │
│  │ Endpoint'ler        │  │ İşlemleri           │          │
│  │  [Table]            │  │  [Table]            │          │
│  └─────────────────────┘  └─────────────────────┘          │
│                                                               │
│  ┌──────────────────────────────────────────────┐           │
│  │ En Sık Hatalar (ERROR level)                 │           │
│  │  [Data Table]                                │           │
│  └──────────────────────────────────────────────┘           │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

## 🔧 Özelleştirme

### Visualization Düzenleme
1. Dashboard'da bir visualization'ın sağ üst köşesindeki **⋮** (üç nokta) tıkla
2. **Edit visualization** seç
3. Değişiklikleri yap
4. **Save** tıkla

### Dashboard Düzeni Değiştirme
1. Dashboard'da sağ üstte **Edit** butonuna tıkla
2. Visualization'ları sürükle-bırak ile yeniden düzenle
3. Köşelerden tutarak boyutlandır
4. **Save** tıkla

### Yeni Visualization Ekleme
1. Dashboard'da **Edit** modunda
2. **Add** butonuna tıkla
3. **Create new** veya mevcut bir visualization seç
4. Dashboard'a ekle
5. **Save** tıkla

## 💡 Kullanım Senaryoları

### 1. Hata Takibi
- **Log Seviyeleri** pie chart'ında ERROR dilimini kontrol et
- **En Sık Hatalar** tablosunda hangi hataların çok olduğunu gör
- Bir hataya tıkla → **View in Discover** → detaylı stack trace'i gör

### 2. Performance Monitoring
- **Ortalama Response Time** metriğini kontrol et
- **En Yavaş Endpoint'ler** tablosunda hangi endpoint'lerin yavaş olduğunu gör
- Yavaş bir endpoint'e tıkla → filtrele → **Zaman Bazlı Log Akışı**'nda ne zaman yavaşladığını gör

### 3. Kullanıcı Aktivitesi
- **Kullanıcı Aktivitesi** pie chart'ında en aktif kullanıcıları gör
- Bir kullanıcıya tıkla → filtrele
- **Portfolio İşlemleri** tablosunda o kullanıcının ne yaptığını gör

### 4. API Kullanımı
- **API Endpoint Kullanımı** bar chart'ında en çok kullanılan endpoint'leri gör
- Bir endpoint'e tıkla → filtrele
- **Zaman Bazlı Log Akışı**'nda kullanım trendini gör

### 5. Canlı Monitoring
1. Zaman filtresini **Last 15 minutes** yap
2. Auto-refresh'i **30 seconds** yap
3. Dashboard'u tam ekran yap (F11)
4. Duvar ekranında göster

## 🐛 Sorun Giderme

### Dashboard Boş Görünüyor
**Sorun:** Hiçbir visualization'da veri yok

**Çözüm:**
1. Zaman filtresini genişlet (Last 24 hours)
2. Index pattern'in doğru olduğunu kontrol et: `finans-portal-logs-*`
3. Discover'da log var mı kontrol et

### "No results found" Hatası
**Sorun:** Bazı visualization'larda "No results found"

**Çözüm:**
1. O visualization için gerekli field'lar var mı kontrol et
   - **Ortalama Response Time:** `durationMs` field'ı gerekli
   - **Kullanıcı Aktivitesi:** `userId` field'ı gerekli
   - **API Endpoint:** `endpoint` field'ı gerekli
2. Eğer field yoksa, o visualization'ı sil veya düzenle

### Visualization Hatalı
**Sorun:** Bir visualization düzgün görünmüyor

**Çözüm:**
1. Visualization'ı sil
2. Yeniden oluştur (yukarıdaki adımları takip et)
3. Dashboard'a tekrar ekle

### Import Başarısız
**Sorun:** "Import failed" hatası

**Çözüm:**
1. OpenSearch Dashboards versiyonunu kontrol et (2.11.x olmalı)
2. Index pattern'in oluşturulduğundan emin ol: `finans-portal-logs-*`
3. Dosyanın bozulmadığından emin ol (yeniden indir)

## 📚 İleri Seviye

### Alert Oluşturma
1. **Management** → **Alerting** → **Monitors**
2. **Create monitor** tıkla
3. Query: `level: ERROR`
4. Trigger: `count > 10 in last 5 minutes`
5. Action: Email/Slack notification

### Custom Visualization
1. **Visualize** → **Create visualization**
2. **TSVB** (Time Series Visual Builder) seç
3. Karmaşık metrikler ve hesaplamalar yap
4. Dashboard'a ekle

### Saved Search
1. **Discover** sayfasında filtreler oluştur
2. **Save** → İsim ver
3. Dashboard'da **Add** → **Add from library** → Saved search'ü seç

## 🎓 Daha Fazla Bilgi

- [OpenSearch Dashboards Documentation](https://opensearch.org/docs/latest/dashboards/)
- [Visualization Types](https://opensearch.org/docs/latest/dashboards/visualize/viz-index/)
- [Dashboard Best Practices](https://opensearch.org/docs/latest/dashboards/dashboard/index/)

## ✅ Başarı Kontrol Listesi

- [ ] Dashboard başarıyla import edildi
- [ ] Tüm visualization'lar görünüyor
- [ ] Zaman filtresi çalışıyor
- [ ] Auto-refresh çalışıyor
- [ ] Filtreleme çalışıyor
- [ ] Drill-down çalışıyor
- [ ] Veriler doğru görünüyor

Tebrikler! Dashboard'un hazır. 🎉

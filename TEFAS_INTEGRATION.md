# TEFAS Yatırım Fonu Entegrasyonu

Bu dokümantasyon, finans portalının TEFAS (Türkiye Elektronik Fon Alım Satım Platformu) API'si ile entegrasyonunu açıklar.

## Genel Bakış

Uygulama, gerçek yatırım fonu verilerini TEFAS'ın resmi API'sinden çeker. Bu entegrasyon sayesinde:

- ✅ Gerçek zamanlı fon fiyatları
- ✅ Günlük, haftalık, aylık ve yıllık getiri oranları
- ✅ Fon portföy büyüklükleri
- ✅ Yönetim şirketi bilgileri
- ✅ Fon tipleri ve risk seviyeleri

## Teknik Detaylar

### TEFAS API Endpoints

```
Base URL: https://www.tefas.gov.tr/api/DB

1. Fon Listesi: /BindComparisonFundList
2. Fon Bilgileri: /BindHistoryInfo
3. Getiri Oranları: /BindHistoryAllocation
```

### Servis Mimarisi

#### TefasFundFetcher.java
TEFAS API'sine HTTP istekleri yapan ve JSON yanıtları parse eden servis.

**Ana Metodlar:**
- `fetchAllFunds()`: Tüm fonların listesini çeker (ilk 50 fon)
- `fetchFundDetails(String fundCode)`: Belirli bir fonun detaylı bilgilerini çeker
- `fetchFundReturns(InvestmentFund fund, String fundCode)`: Fonun getiri oranlarını çeker

#### InvestmentFundService.java
Fon verilerini yöneten ve veritabanı işlemlerini gerçekleştiren servis.

**Güncellenen Metodlar:**
- `seedIfEmpty()`: İlk başlatmada TEFAS'tan veri çeker, başarısız olursa örnek veri kullanır
- `updateFundPrices()`: Her 6 saatte bir TEFAS'tan güncel verileri çeker

## Veri Akışı

```
1. Uygulama Başlatma
   └─> DataSeeder.seedInvestmentFunds()
       └─> InvestmentFundService.seedIfEmpty()
           └─> TefasFundFetcher.fetchAllFunds()
               └─> Veritabanına kaydet

2. Periyodik Güncelleme (Her 6 saatte bir)
   └─> @Scheduled updateFundPrices()
       └─> TefasFundFetcher.fetchAllFunds()
           └─> Mevcut fonları güncelle / Yeni fonları ekle

3. Manuel Güncelleme
   └─> POST /api/v1/investment-funds/refresh
       └─> updateFundPrices() tetiklenir
```

## Fon Tipleri

TEFAS'tan gelen fon tipleri standart kategorilere dönüştürülür:

| TEFAS Tipi | Standart Tip | Risk Seviyesi |
|------------|--------------|---------------|
| Hisse Senedi | Hisse Senedi Fonu | YÜKSEK |
| Borçlanma Araçları / Tahvil | Borçlanma Araçları Fonu | DÜŞÜK |
| Karma | Karma Fon | ORTA |
| Altın / Emtia | Emtia Fonu | YÜKSEK |
| Para Piyasası / Likit | Para Piyasası Fonu | DÜŞÜK |
| Sektör | Sektör Fonu | YÜKSEK |
| Fon Sepeti | Fon Sepeti Fonu | ORTA |

## Rate Limiting

TEFAS API'sini yormamak için:
- Her fon detayı çekimi arasında 100ms bekleme
- İlk yüklemede maksimum 50 fon çekimi
- Güncellemeler 6 saatte bir

## Hata Yönetimi

### Fallback Mekanizması
TEFAS API'sine erişilemediğinde:
1. Hata loglanır
2. Örnek veri (sample data) kullanılır
3. Uygulama çalışmaya devam eder

### Retry Stratejisi
- Scheduled task her 6 saatte bir otomatik retry yapar
- Manuel refresh endpoint ile istendiğinde tetiklenebilir

## API Kullanımı

### Frontend'den Fon Verilerini Çekme

```typescript
// Tüm fonları getir
const funds = await getInvestmentFunds();

// Fon tiplerini getir
const types = await getFundTypes();

// Belirli bir fon tipini filtrele
const stockFunds = funds.filter(f => f.fundType === 'Hisse Senedi Fonu');
```

### Manuel Güncelleme Tetikleme

```bash
# Backend'e POST isteği
curl -X POST http://localhost:8080/api/v1/investment-funds/refresh
```

## Performans Optimizasyonu

### Caching
```java
@Cacheable("investment-funds")
public List<InvestmentFund> getAllFunds()

@Cacheable("funds-by-type")
public List<InvestmentFund> getFundsByType(String fundType)
```

### Database Indexing
- `fundCode` üzerinde unique index
- `fundType` ve `managementCompany` üzerinde index
- `totalValue` ve `yearlyReturn` üzerinde sıralama için index

## Test Etme

### 1. Backend'i Başlat
```bash
cd backend
./mvnw spring-boot:run
```

### 2. Logları İzle
```bash
tail -f backend/logs/finans-backend.log
```

Aşağıdaki logları göreceksiniz:
```
INFO  - Fetching fund list from TEFAS...
INFO  - Successfully fetched 50 funds from TEFAS
INFO  - Seeded 50 investment funds from TEFAS
```

### 3. API'yi Test Et
```bash
# Tüm fonları listele
curl http://localhost:8080/api/v1/investment-funds

# Fon tiplerini listele
curl http://localhost:8080/api/v1/investment-funds/types

# Manuel güncelleme tetikle
curl -X POST http://localhost:8080/api/v1/investment-funds/refresh
```

### 4. Frontend'i Test Et
1. Frontend'i başlat: `cd frontend && npm run dev`
2. Tarayıcıda `http://localhost:5173/funds` adresine git
3. Gerçek TEFAS verilerini görmelisiniz

## Sorun Giderme

### TEFAS API'sine Erişilemiyor
**Belirti:** "Could not fetch funds from TEFAS, using sample data instead" logu

**Çözümler:**
1. İnternet bağlantısını kontrol edin
2. TEFAS web sitesinin erişilebilir olduğunu doğrulayın: https://www.tefas.gov.tr
3. Firewall/proxy ayarlarını kontrol edin
4. RestTemplate timeout ayarlarını artırın

### Fonlar Yüklenmiyor
**Belirti:** Frontend'de "Henüz yatırım fonu verisi bulunmuyor" mesajı

**Çözümler:**
1. Backend loglarını kontrol edin
2. Database'de fon verisi olup olmadığını kontrol edin:
   ```sql
   SELECT COUNT(*) FROM investment_funds;
   ```
3. Manuel refresh endpoint'ini tetikleyin
4. Backend'i yeniden başlatın (seed işlemi tekrar çalışır)

### Yavaş Yükleme
**Belirti:** İlk başlatmada uzun süre bekliyor

**Neden:** 50 fon için TEFAS'a 50+ HTTP isteği yapılıyor (her biri 100ms bekleme ile)

**Çözüm:** Normal davranış. İlk yüklemeden sonra veriler cache'lenir ve database'den hızlıca gelir.

## Gelecek Geliştirmeler

- [ ] Daha fazla fon çekme (şu an 50 ile sınırlı)
- [ ] Fon detay sayfası (grafik, portföy dağılımı, vb.)
- [ ] Fon karşılaştırma özelliği
- [ ] Favori fonlar
- [ ] Fon alarm sistemi (belirli getiri oranlarında bildirim)
- [ ] Tarihsel fiyat grafiği
- [ ] Portföy simülasyonu

## Kaynaklar

- [TEFAS Resmi Web Sitesi](https://www.tefas.gov.tr)
- [TEFAS API Dokümantasyonu](https://www.tefas.gov.tr/api)
- [SPK (Sermaye Piyasası Kurulu)](https://www.spk.gov.tr)

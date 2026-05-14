# Yatırım Fonları ve Tahvil/Bono Veri Kaynakları Durumu

## 📊 Yatırım Fonları (TEFAS)

### Mevcut Durum: ⚠️ API Değişikliği Nedeniyle Çalışmıyor

TEFAS API'si yapısal değişiklik geçirmiş ve eski endpoint'ler artık çalışmıyor.

**Hata Mesajı:**
```
404 Not Found on GET request for "https://www.tefas.gov.tr/api/DB/BindComparisonFundList"
ApiProxy is not found for related URL( /uga/fonbilgilendirme/portal/service/BindComparisonFundList )!
```

### Yapılan İyileştirmeler

✅ **InvestmentFundRefreshScheduler** oluşturuldu:
- Uygulama başlangıcında otomatik veri yükleme
- Hafta içi 10:30, 14:30, 18:30'da otomatik güncelleme
- Yapılandırılabilir cron expression

✅ **Retry Logic** eklendi:
- Maksimum 3 deneme
- Exponential backoff
- Detaylı hata loglama

✅ **Configuration** iyileştirildi:
- `application.yml` üzerinden tam kontrol
- Rate limiting
- Maksimum fon sayısı limiti

### Çözüm Önerileri

#### Seçenek 1: TEFAS API Endpoint'ini Güncelle (Önerilen)

TEFAS'ın yeni API yapısını araştırıp `TefasFundFetcher.java` dosyasındaki endpoint'leri güncellemeniz gerekiyor:

```java
// Eski (çalışmıyor):
private static final String FUND_LIST_ENDPOINT = TEFAS_API_BASE + "/BindComparisonFundList";

// Yeni endpoint'i bulup güncellemeniz gerekiyor
```

**Araştırma Kaynakları:**
- TEFAS resmi web sitesi: https://www.tefas.gov.tr
- Browser Developer Tools ile network trafiğini inceleme
- TEFAS'ın yeni API dokümantasyonu (varsa)

#### Seçenek 2: Web Scraping Kullan

API yerine TEFAS web sitesini scrape edebilirsiniz:
- Jsoup veya HtmlUnit kullanarak
- Daha yavaş ama daha güvenilir olabilir
- Rate limiting önemli

#### Seçenek 3: Alternatif Veri Kaynağı

- Fintables API
- Bloomberg API
- Reuters API
- Investing.com API

### Dosya Konumları

- **Fetcher:** `backend/src/main/java/com/finansportali/backend/service/TefasFundFetcher.java`
- **Scheduler:** `backend/src/main/java/com/finansportali/backend/service/InvestmentFundRefreshScheduler.java`
- **Service:** `backend/src/main/java/com/finansportali/backend/service/InvestmentFundService.java`
- **Config:** `backend/src/main/resources/application.yml`

---

## 📈 Tahvil ve Bonolar (TCMB)

### Mevcut Durum: 🟡 DEMO Modunda Çalışıyor

Şu anda **DemoBondDataProvider** kullanılıyor ve 8 adet örnek tahvil/bono verisi gösteriliyor:
- 2 Devlet Tahvili
- 2 Hazine Bonosu
- 2 Kira Sertifikası
- 2 Eurobond

### Gerçek Veri İçin Gereksinimler

#### TCMB EVDS API Entegrasyonu

**Gerekli:**
1. TCMB EVDS API Key (https://evds2.tcmb.gov.tr/ adresinden ücretsiz alınabilir)
2. API key'i `.env` dosyasına ekleyin:
   ```
   TCMB_API_KEY=your_api_key_here
   ```

**API Endpoint'leri:**
```
Base URL: https://evds2.tcmb.gov.tr/service/evds/
Series Codes:
- TP.DK.USD.A.YTL: Gösterge Tahvil Faiz Oranları
- TP.DK.USD.S.2Y: 2 yıllık getiri
- TP.DK.USD.S.5Y: 5 yıllık getiri
- TP.DK.USD.S.10Y: 10 yıllık getiri
```

**Örnek API Çağrısı:**
```
GET https://evds2.tcmb.gov.tr/service/evds/
    ?series=TP.DK.USD.A.YTL
    &startDate=01-01-2026
    &endDate=31-12-2026
    &type=json
    &key=YOUR_API_KEY
```

### TCMB Provider'ı Aktifleştirme

1. **API Key Alın:**
   - https://evds2.tcmb.gov.tr/ adresine gidin
   - Kayıt olun (ücretsiz)
   - API key'inizi alın

2. **Environment Variable Ekleyin:**
   ```bash
   # .env dosyasına
   TCMB_API_KEY=your_actual_api_key_here
   ```

3. **application.yml'i Güncelleyin:**
   ```yaml
   app:
     bonds:
       provider: TCMB  # DEMO yerine TCMB
       tcmb:
         api-key: ${TCMB_API_KEY:}
   ```

4. **TcmbBondDataProvider.java'yı Implement Edin:**
   - Dosya: `backend/src/main/java/com/finansportali/backend/service/TcmbBondDataProvider.java`
   - TODO yorumlarını takip edin
   - RestTemplate ile API çağrıları yapın
   - Response'u parse edip BondQuoteDto'ya dönüştürün

5. **Backend'i Yeniden Başlatın:**
   ```bash
   docker restart finans-backend
   ```

### Dosya Konumları

- **DEMO Provider:** `backend/src/main/java/com/finansportali/backend/service/DemoBondDataProvider.java`
- **TCMB Provider:** `backend/src/main/java/com/finansportali/backend/service/TcmbBondDataProvider.java` ⚠️ IMPLEMENT EDİLMELİ
- **Interface:** `backend/src/main/java/com/finansportali/backend/service/BondDataProvider.java`
- **Scheduler:** `backend/src/main/java/com/finansportali/backend/service/BondDataRefreshScheduler.java`
- **Config:** `backend/src/main/resources/application.yml`

---

## 🔧 Yapılandırma

### application.yml

```yaml
app:
  # Yatırım Fonları
  funds:
    provider: TEFAS
    scheduler-enabled: true
    refresh-cron: "0 30 10,14,18 * * MON-FRI"  # Hafta içi 10:30, 14:30, 18:30
    max-funds-to-fetch: 100
    rate-limit-delay-ms: 100
    retry-max-attempts: 3
    retry-initial-delay-ms: 1000
    fallback-enabled: false
    tefas:
      base-url: https://www.tefas.gov.tr/api/DB

  # Tahvil ve Bonolar
  bonds:
    provider: DEMO  # TCMB için API key gerekli
    scheduler-enabled: true
    refresh-cron: "0 0 0/2 * * ?"  # Her 2 saatte bir
    fallback-enabled: true
    tcmb:
      api-key: ${TCMB_API_KEY:}
      base-url: https://evds2.tcmb.gov.tr/service/evds/
```

---

## 📝 Sonraki Adımlar

### Öncelik 1: TEFAS API'sini Düzelt
1. TEFAS'ın yeni API yapısını araştır
2. `TefasFundFetcher.java` dosyasındaki endpoint'leri güncelle
3. Test et ve doğrula

### Öncelik 2: TCMB Entegrasyonunu Tamamla
1. TCMB EVDS API key al
2. `TcmbBondDataProvider.java` dosyasını implement et
3. Test et ve doğrula

### Öncelik 3: Hata Yönetimi
1. Fallback mekanizmaları ekle
2. Kullanıcıya bilgilendirme mesajları göster
3. Admin panelinde veri kaynağı durumunu göster

---

## 🆘 Destek

Sorularınız için:
- Backend logs: `docker logs finans-backend`
- Database: `docker exec -it finans-postgres psql -U finans_user -d finans_db`
- API test: `curl http://localhost:8080/api/v1/investment-funds`

---

**Son Güncelleme:** 10 Mayıs 2026
**Durum:** TEFAS API değişikliği nedeniyle fonlar çalışmıyor, tahviller DEMO modunda çalışıyor

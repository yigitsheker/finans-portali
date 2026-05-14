# Tahvil ve Bono Modülü Dokümantasyonu

## Genel Bakış

Finans Portalı'na eklenen Tahvil ve Bono modülü, Türkiye finans piyasalarındaki devlet tahvilleri, hazine bonoları ve diğer borçlanma araçlarının verilerini takip etmeyi sağlar.

## Özellikler

- ✅ Devlet tahvilleri ve hazine bonoları listesi
- ✅ Getiri oranları ve fiyat bilgileri
- ✅ Vade tarihi ve kupon bilgileri
- ✅ Tarihsel getiri grafikleri
- ✅ Filtreleme ve arama
- ✅ Özet istatistikler
- ✅ Manuel veri güncelleme (ADMIN)
- ✅ Otomatik periyodik güncelleme
- ✅ Çoklu veri kaynağı desteği (TCMB, BIST, DEMO)

## Mimari

### Backend Katmanları

```
┌─────────────────────────────────────────┐
│         REST API Controller             │
│      (BondController.java)              │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│         Service Layer                    │
│  - DebtInstrumentService                │
│  - BondDataRefreshService               │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│      Data Provider Abstraction          │
│  - BondDataProvider (interface)         │
│  - TcmbBondDataProvider                 │
│  - DemoBondDataProvider                 │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│         Repository Layer                 │
│  - DebtInstrumentRepository             │
│  - DebtInstrumentQuoteRepository        │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│         Database (PostgreSQL)            │
│  - debt_instruments                     │
│  - debt_instrument_quotes               │
└─────────────────────────────────────────┘
```

### Frontend Katmanları

```
┌─────────────────────────────────────────┐
│         Pages                            │
│      (Bonds.tsx)                        │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│         Components                       │
│  - BondDetailModal                      │
│  - LWAreaChart                          │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│         API Layer                        │
│      (bondApi.ts)                       │
└─────────────────────────────────────────┘
```

## Database Schema

### debt_instruments

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| symbol | VARCHAR(30) | Uygulama içi sembol (örn: TR2YT) |
| isin | VARCHAR(12) | ISIN kodu |
| name | VARCHAR(200) | Görünen ad |
| type | VARCHAR(30) | Enstrüman türü (GOVERNMENT_BOND, TREASURY_BILL, vb.) |
| issuer | VARCHAR(100) | İhraççı kurum |
| currency | VARCHAR(3) | Para birimi (TRY, USD, EUR) |
| maturity_date | DATE | Vade tarihi |
| coupon_rate | NUMERIC(10,4) | Kupon oranı (%) |
| coupon_type | VARCHAR(30) | Kupon tipi |
| active | BOOLEAN | Aktif mi? |
| created_at | TIMESTAMP | Oluşturulma zamanı |
| updated_at | TIMESTAMP | Güncellenme zamanı |

### debt_instrument_quotes

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| instrument_id | BIGINT | İlişkili enstrüman (FK) |
| quote_date | DATE | Fiyat tarihi |
| price | NUMERIC(12,4) | Fiyat |
| yield_rate | NUMERIC(10,4) | Getiri oranı (%) |
| clean_price | NUMERIC(12,4) | Temiz fiyat |
| dirty_price | NUMERIC(12,4) | Kirli fiyat |
| volume | NUMERIC(18,2) | İşlem hacmi |
| change_rate | NUMERIC(10,4) | Değişim oranı (%) |
| source | VARCHAR(20) | Veri kaynağı |
| created_at | TIMESTAMP | Oluşturulma zamanı |

**Unique Constraint:** (instrument_id, quote_date, source)

## API Endpoints

### GET /api/v1/bonds

Tahvil ve bono listesini getirir.

**Query Parameters:**
- `type` (optional): DebtInstrumentType (GOVERNMENT_BOND, TREASURY_BILL, vb.)
- `currency` (optional): Para birimi (TRY, USD, EUR)
- `maturityFrom` (optional): Vade başlangıç tarihi (YYYY-MM-DD)
- `maturityTo` (optional): Vade bitiş tarihi (YYYY-MM-DD)
- `search` (optional): Arama terimi

**Response:** `BondListItemDto[]`

### GET /api/v1/bonds/{id}

Tahvil/bono detayını getirir.

**Response:** `BondDetailDto`

### GET /api/v1/bonds/{id}/history

Tarihsel getiri verilerini getirir.

**Query Parameters:**
- `from` (required): Başlangıç tarihi (YYYY-MM-DD)
- `to` (required): Bitiş tarihi (YYYY-MM-DD)

**Response:** `BondHistoryPointDto[]`

### GET /api/v1/bonds/summary

Özet istatistikleri getirir.

**Response:** `BondSummaryDto`

### POST /api/v1/bonds/refresh

Manuel veri güncelleme (ADMIN only).

**Authorization:** Bearer token with ADMIN role

**Response:** String (success message)

## Configuration

### application.yml

```yaml
app:
  bonds:
    provider: DEMO  # TCMB, BIST, DEMO
    scheduler-enabled: true
    refresh-cron: "0 0 */2 * * *"  # Her 2 saatte bir
    fallback-enabled: true
    tcmb:
      api-key: ""  # TCMB EVDS API key
      base-url: https://evds2.tcmb.gov.tr/service/evds/
    bist:
      base-url: https://www.borsaistanbul.com/
```

### Environment Variables

- `BONDS_PROVIDER`: Veri sağlayıcı (TCMB, BIST, DEMO)
- `BONDS_SCHEDULER_ENABLED`: Scheduler aktif mi? (true/false)
- `BONDS_REFRESH_CRON`: Cron expression
- `BONDS_FALLBACK_ENABLED`: Fallback aktif mi? (true/false)
- `TCMB_API_KEY`: TCMB EVDS API anahtarı

## Data Providers

### DemoBondDataProvider

Demo/test amaçlı veri sağlayıcı. Gerçek veriye benzer örnek veriler üretir.

**Kullanım:**
```yaml
app:
  bonds:
    provider: DEMO
```

### TcmbBondDataProvider

TCMB EVDS API kullanarak DİBS verilerini çeker.

**Gereksinimler:**
- TCMB EVDS API key (https://evds2.tcmb.gov.tr/)

**Kullanım:**
```yaml
app:
  bonds:
    provider: TCMB
    tcmb:
      api-key: "YOUR_API_KEY"
```

**Not:** Şu an placeholder olarak bırakıldı. Gerçek implementasyon için TCMB API entegrasyonu gerekli.

### BistBondDataProvider

Borsa İstanbul Borçlanma Araçları Piyasası verilerini çeker.

**Not:** Henüz implement edilmedi.

## Scheduler

### BondDataRefreshScheduler

Tahvil verilerini periyodik olarak günceller.

**Davranış:**
1. Uygulama başlangıcında 5 saniye sonra ilk güncelleme
2. Sonrasında cron expression'a göre periyodik güncelleme
3. OpenTelemetry span oluşturur
4. Hata durumunda uygulama çökmez, sadece log kaydeder

**Cron Examples:**
- `0 0 */2 * * *` - Her 2 saatte bir
- `0 0 18 * * *` - Her gün saat 18:00'de
- `0 0 9-17 * * MON-FRI` - Hafta içi 9-17 arası her saat

## Frontend Usage

### Bonds Page

Route: `/bonds`

**Features:**
- Tahvil/bono listesi
- Filtreleme (tür, para birimi, vade, arama)
- Özet kartlar
- Detay modal
- Manuel refresh (ADMIN)

### BondDetailModal

**Features:**
- Detaylı bilgiler
- Tarihsel getiri grafiği
- Periyot seçimi (30D, 90D, 1Y)

## Testing

### Backend Test Steps

1. Start services:
```bash
docker-compose up -d
```

2. Check logs:
```bash
docker logs -f finans-backend
```

3. Verify initial data load:
```
[BOND-SCHEDULER] Starting initial bond data refresh
[DEMO] Fetching demo bond data...
[DEMO] Generated 8 demo bond quotes
[BOND-REFRESH] Successfully updated 8 bond instruments
```

4. Test API endpoints:
```bash
# List bonds
curl http://localhost:8080/api/v1/bonds

# Get summary
curl http://localhost:8080/api/v1/bonds/summary

# Get bond detail
curl http://localhost:8080/api/v1/bonds/1

# Get history
curl "http://localhost:8080/api/v1/bonds/1/history?from=2026-04-01&to=2026-05-09"
```

5. Test manual refresh (ADMIN):
```bash
curl -X POST http://localhost:8080/api/v1/bonds/refresh \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### Frontend Test Steps

1. Open browser: http://localhost

2. Login with Keycloak

3. Navigate to "Tahvil ve Bono" in sidebar

4. Verify:
   - ✅ Summary cards show data
   - ✅ Table loads with bonds
   - ✅ Filters work
   - ✅ Search works
   - ✅ Click on bond opens detail modal
   - ✅ Chart loads in detail modal
   - ✅ ADMIN sees "Verileri Yenile" button
   - ✅ Normal users don't see refresh button

## Troubleshooting

### No bond data appears

**Cause:** Scheduler might be disabled or provider not configured.

**Solution:**
1. Check `app.bonds.scheduler-enabled=true`
2. Check `app.bonds.provider=DEMO`
3. Check backend logs for errors
4. Manually trigger refresh via API

### Provider endpoint changed

**Cause:** External API changed.

**Solution:**
1. Update provider implementation
2. Or switch to different provider in config

### Scheduler does not run

**Cause:** Cron expression invalid or scheduler disabled.

**Solution:**
1. Verify cron expression syntax
2. Check `@EnableScheduling` in BackendApplication
3. Check logs for scheduler initialization

### Admin refresh returns 403

**Cause:** User doesn't have ADMIN role.

**Solution:**
1. Verify user has ADMIN role in Keycloak
2. Check JWT token contains role
3. Verify SecurityConfig allows ADMIN access

### Chart breaks

**Cause:** No historical data available.

**Solution:**
1. Check if quotes exist in database
2. Verify date range is valid
3. Check provider returns historical data

## Metrics

### Prometheus Metrics

- `bond_refresh_success_total` - Başarılı güncelleme sayısı
- `bond_refresh_failure_total` - Başarısız güncelleme sayısı
- `bond_instruments_fetched_total` - Çekilen enstrüman sayısı
- `bond_refresh_duration_seconds` - Güncelleme süresi

### OpenTelemetry Traces

- `bond.data.refresh.scheduled` - Periyodik güncelleme
- `bond.data.refresh.initial` - İlk güncelleme
- `bond.data.refresh.manual` - Manuel güncelleme

## Production Notes

1. **Veri Kaynağı:** Resmi lisanslı veri kaynakları tercih edilmeli
2. **Scraping:** Agresif scraping yapılmamalı
3. **Cache:** Provider yanıtları cache'lenmeli
4. **Rate Limiting:** Çok sık güncelleme yapılmamalı
5. **Market Holidays:** Piyasa tatilleri dikkate alınmalı
6. **Monitoring:** Provider başarısızlıkları izlenmeli
7. **Licensing:** Veri lisanslama kontrol edilmeli

## Future Improvements

- [ ] TCMB EVDS API gerçek implementasyonu
- [ ] BIST data provider implementasyonu
- [ ] Karşılaştırma özelliği (birden fazla tahvil)
- [ ] Excel export
- [ ] Email alerts (getiri değişimleri için)
- [ ] Portföye tahvil ekleme
- [ ] Tahvil hesaplayıcı (yield to maturity, duration, vb.)

## Support

Sorular için: [GitHub Issues](https://github.com/your-repo/issues)

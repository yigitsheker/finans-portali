# Kripto Para Genişletmesi

## Özet
Sistemdeki kripto para sayısı 3'ten 18'e çıkarıldı. Artık daha fazla popüler kripto para takip edilebilir.

## Eklenen Kripto Paralar

### Önceki (3 adet):
1. **BTC** - Bitcoin
2. **ETH** - Ethereum  
3. **SOL** - Solana

### Yeni Eklenenler (15 adet):
4. **BNB** - Binance Coin (~$625)
5. **XRP** - Ripple (~$2.45)
6. **ADA** - Cardano (~$1.08)
7. **DOGE** - Dogecoin (~$0.38)
8. **MATIC** - Polygon (~$0.52)
9. **DOT** - Polkadot (~$7.85)
10. **AVAX** - Avalanche (~$42.30)
11. **LINK** - Chainlink (~$23.45)
12. **LTC** - Litecoin (~$105.20)
13. **UNI** - Uniswap (~$14.75)
14. **ATOM** - Cosmos (~$11.90)
15. **XLM** - Stellar (~$0.42)
16. **ALGO** - Algorand (~$0.35)

**TOPLAM: 18 Kripto Para**

## Teknik Detaylar

### Değişiklikler
**Dosya**: `backend/src/main/java/com/finansportali/backend/service/MarketService.java`

1. **Instrument Tanımları** (satır ~128-145)
   - Her kripto için `upsert()` çağrısı eklendi
   - Yahoo Finance formatı kullanıldı (örn: "BNB-USD")
   - InstrumentType.CRYPTO olarak işaretlendi

2. **Fallback Quote'lar** (satır ~286-302)
   - Her kripto için gerçekçi başlangıç fiyatları
   - Değişim miktarı ve yüzdesi
   - Pozitif ve negatif değişimler karışık

3. **Historical Candles** (satır ~437-445)
   - Her kripto için 30 günlük geçmiş veri
   - Başlangıç fiyatları fallback quote'larla uyumlu

### Yahoo Finance Sembol Formatı
```
Uygulama İçi → Yahoo Finance
BTCUSD       → BTC-USD
ETHUSD       → ETH-USD
BNBUSD       → BNB-USD
XRPUSD       → XRP-USD
... vb.
```

### Veri Kaynakları
- **Birincil**: Yahoo Finance API (otomatik güncelleme)
- **Yedek**: Fallback değerler (API başarısız olursa)

## Kullanım

### Frontend'de Görüntüleme
Kripto paralar "CRYPTO" filtresi altında görünür:
- Hisse Fiyatları sayfasında "CRYPTO" butonuna tıklayın
- 18 kripto para listelenecek
- Her biri için fiyat, değişim ve mini grafik gösterilir

### Portföye Ekleme
- Herhangi bir kripto parayı portföye ekleyebilirsiniz
- "Al/Sat" butonuna tıklayın
- Adet girin ve onaylayın

### Fiyat Alarmı
- Her kripto para için fiyat alarmı oluşturabilirsiniz
- Hedef fiyat belirleyin
- Email bildirimi alın

## Test Edildi
✅ Kod derleme hatası yok
✅ 18 kripto para tanımlandı
✅ Fallback quote'lar eklendi
✅ Historical candles eklendi
✅ Yahoo Finance formatı doğru

## Sonraki Adımlar
Backend'i yeniden başlatın:
```powershell
cd backend
.\START_BACKEND.ps1
```

Veya manuel:
```powershell
cd backend
mvn spring-boot:run
```

Sistem başladığında tüm kripto paralar otomatik olarak veritabanına eklenecek ve Yahoo Finance'den güncel fiyatlar çekilecektir.

# Finans Portalı — Analiz Dokümanı

**Sürüm:** 1.2
**Tarih:** 2026-05-31
**Statü:** Güncel

---

## 1. Projenin Amacı

Finans Portalı, bireysel yatırımcıların **Türkiye + global piyasaları tek ekrandan takip etmesini** ve karar verirken **AI destekli analiz** alabilmesini amaçlayan bir web uygulamasıdır.

Mevcut yatırım uygulamalarının ayrı ayrı sunduğu fonksiyonları konsolide eder:

- Çoklu varlık sınıfı izleme (hisse, kripto, döviz, emtia, fon, tahvil, vadeli işlem)
- Makro veri takibi (TÜFE, ÜFE, faiz, döviz kurları)
- Portföy yönetimi ve geçmiş performans karşılaştırma
- Haber akışı (TR + EN, otomatik çeviri)
- Yapay zeka destekli teknik analiz ve yatırım senaryoları
- Enflasyondan arındırılmış reel getiri karşılaştırması

## 2. Hedef Kitle

| Persona | İhtiyaçları |
|---------|-------------|
| **Bireysel yatırımcı** | Portföyünü tek ekrandan görmek, fiyat alarmı kurmak, geçmiş performansı analiz etmek |
| **Yeni başlayan** | Finans terimlerini hover ile öğrenmek (TermInfo glossary), AI'dan öneri almak, düşük riskli enstrüman keşfetmek |
| **Aktif takipçi** | BIST + ABD piyasaları + kripto + emtiayı eş zamanlı izlemek, alarmlar, real-time fiyat |
| **Uzun vadeli yatırımcı** | Enflasyon karşılaştırması (reel getiri), 5+ yıl tarihsel veri, fon performansları |

## 3. Sayfa Bazında Özellikler

### 3.1 Anasayfa (`/`)

**Amaç:** Genel piyasa görünümü + hızlı erişim.

**Bileşenler:**
- **Hero başlık** + CTA (Piyasaları Keşfet, Portföy Görüntüle)
- **Günün hareketlileri** kartı — gün içi en çok değişen 5 enstrüman
- **6 kategori kartı** (Hisse / Kripto / Fonlar / Tahvil / Döviz / Haberler) — tıklayınca ilgili sayfaya
- **BIST 100 en aktif hisseler tablosu** — Tümü / Yükselenler / Düşenler / Hacim filtreleri
- **Döviz şeridi** (TCMB güncel kurları)
- **Finans haberleri** widget (son 4 başlık)

**Veri kaynakları:**
- `GET /api/v1/market/summary` — Yahoo Finance üzerinden 145 enstrüman (BIST, US, kripto, emtia, döviz, endeks)
- `GET /api/v1/exchange-rates` — TCMB XML feed
- `GET /api/v1/news` — Kafka log pipeline değil, doğrudan DB'den (RSS feed cache)

### 3.2 Hisseler (`/stocks`)

**Amaç:** BIST + ABD hisse senedi tablosu, watchlist yönetimi.

**Bileşenler:**
- **2 sekme:** Piyasa / Takip Listem
- **Filtreler:** BIST / STOCK kategori, BIST 30/50/100 endeks chip'leri, çoklu seçim
- **Arama:** sembol veya isim
- **Sıralanabilir kolonlar:** Sembol (Türkçe locale-aware), Fiyat, Değişim %
- **Para birimi göstergesi:** Orijinal / TL / USD (topbar toggle)
- **Sparkline grafik** her satırda
- **Tablo satırına tıklama:** `InstrumentChartModal` açar — TradingView lightweight grafik (1G/5G/1A/1Y), Alarm Ekle, Watchlist'e Ekle, Portföye Ekle, Karşılaştır
- **Sayfalama:** 25/50/100 satır seçimi

### 3.3 Kripto (`/crypto`)

`Hisseler` sayfasının kripto-filtreli versiyonu. BTC/ETH/SOL/BNB/XRP/ADA/DOT/LTC/XLM gibi major coin'ler için Yahoo'dan veri çekilir.

### 3.4 Emtia (`/commodities`)

Aynı bileşen, altın (XAUUSD), gümüş, petrol (WTI), doğalgaz (NGAS), bakır, platin için filtrelidir.

### 3.5 Fonlar (`/funds`)

**Amaç:** Yatırım fonu performans karşılaştırma.

**Bileşenler:**
- **Fon tipi filtreleri** (Hisse, Karma, Borçlanma, Para Piyasası, vb.)
- **Sıralanabilir tablo:** Fon adı, Birim Fiyat (NAV), Günlük / 1A / 3A / 6A / 1Y / 3Y / 5Y getiri %, Risk Düzeyi (SPK 1-7)
- **Skeleton loading** ve **empty state**
- **Veri kaynağı:** TEFAS JSON API üzerinden ~1000+ aktif Türk yatırım fonu, günlük 3 kez yenilenir (10:30 / 14:30 / 18:30)

### 3.6 Tahvil ve Bono (`/bonds`)

**Amaç:** Borçlanma araçları + mevduat faizleri.

**Bileşenler:**
- **Mevduat faizleri kartı** (TCMB bankalardan)
- **3 özet kartı:** Toplam enstrüman, Ortalama Getiri, Maksimum Getiri
- **Filtreler:** Tür (Devlet tahvili, Hazine bonosu, Eurobond, Sukuk, Şirket tahvili), Para birimi
- **Sıralanabilir tablo:** İsim, Tür, ISIN, Vade, Kupon, Fiyat, Getiri, Değişim, Kaynak
- **Veri kaynakları:** TCMB EVDS3 (politika faizi + DİBS), İş Yatırım scrape (bond data), Investing.com yield curve

### 3.7 Döviz (`/market-data`)

**Amaç:** Tüm para birimi kurları + döviz çevirici.

**Bileşenler:**
- **Canlı kur çevirici** (üstte)
- **Sıralanabilir kur tablosu:** Para birimi (lokalize), Alış, Satış, Efektif Alış, Efektif Satış, Kaynak
- **Veri kaynağı:** TCMB `today.xml` XML feed (~20 para birimi), 4 saatte bir yenilenir

### 3.8 VIOP (`/viop`)

**Amaç:** Borsa İstanbul vadeli işlem kontratları.

**Bileşenler:**
- **Filtre chip'leri:** Endeks / Pay (Hisse) / Döviz TRY / Döviz USD / Kıymetli Maden TRY / Kıymetli Maden USD / Metal
- **Arama:** sembol veya dayanak (örn. AKBNK, XU030)
- **Sıralanabilir tablo:** Sembol, Dayanak, Vade, Son Fiyat, Değişim, Hacim (TL), Hacim (Adet)
- **Veri kaynağı:** İş Yatırım scrape (HTML page parsing), 15 dakikada bir yenilenir

### 3.9 Enflasyon (`/inflation`)

**Amaç:** TÜFE / CPI tarihsel veri + reel getiri hesabı.

**Bileşenler:**
- **Ülke toggle:** 🇹🇷 Türkiye / 🇺🇸 ABD
- **4 özet kart:** Son Yıllık Enflasyon, Son Aylık, TÜFE Endeksi, 5 Yıllık Birikimli
- **Bar grafik:** 24 ay (Aylık mod) veya yıl başına 1 bar (Yıllık mod, yıl-sonu YoY). Her bar üstüne yüzde değeri yazılır.
- **Veri kaynakları:**
  - TR → TCMB EVDS3 API (TÜFE_GEN, ÜFE_GEN serileri), API key ile
  - ABD → FRED API (CPIAUCSL serisi), API key ile
  - 10 yıllık geçmiş üzerinden upsert edilir, günlük 09:00'da yenilenir

### 3.10 Haberler (`/news`)

**Amaç:** Finans odaklı haber akışı, çok dilli.

**Bileşenler:**
- **Hero başlık + lead** ile öne çıkan haber
- **Kategori sidebar:** Tümü / Genel Ekonomi / Hisse / Döviz / Tahvil / Kripto / Emtia / Fonlar / Borsa / TCMB / Uluslararası
- **Sayfalı haber listesi**
- **Haber detay sayfası** (`/news/:id`) — başlık, kaynak, yayın zamanı, tam metin, kaynağa link
- **Veri kaynakları:**
  - 31 farklı RSS feed: AA, Hürriyet, Milliyet, Sabah, Dünya, BloombergHT, Foreks, Investing.com, CoinDesk, Cointelegraph, Yahoo Finance
  - 6 saatte bir tüm feed'ler çekilir
  - Her kategoride en yeni 50 haber tutulur, eskiler silinir
- **Otomatik çeviri:** LibreTranslate (self-hosted) üzerinden TR ↔ EN. Kullanıcı dil tercihine göre cache'lenmiş çeviri sunulur. İlk istek background thread'de prewarm edilir.

### 3.11 Analiz (`/analysis`) — Auth gerekli

**Amaç:** Çapraz varlık analizi + AI yatırım danışmanı.

**İki kolonlu yapı:**

**Sol — Çapraz varlık tablosu:**
- **Üst context strip:** TR Enflasyonu (yıllık) + ABD Enflasyonu (yıllık) — referans olarak
- **Filtreler:** Tümü / Hisse / Kripto / Döviz / Emtia / Fon / Endeks
- **"⚡ Enflasyonu yenenler" toggle** — sadece reel getirisi pozitif olanlar
- **Sıralanabilir kolonlar:** Sembol, İsim, Kategori, Değer, Günlük %, Haftalık %, Aylık %, Yıllık %, **Reel Yıllık %**, Risk (Düşük/Orta/Yüksek), Kısa Sinyal (Al/Tut/Sat/Nötr), Uzun Sinyal
- **Reel getiri hesabı:** Currency-aware — TRY enstrümanlar TR TÜFE'ye, USD enstrümanlar ABD CPI'ye karşı `(1+nominal)/(1+enf) − 1`
- **Sayfalama:** 25/50/100 satır
- **Satıra tıklama:** alt detay kartı açar (Trend, Volatilite, Risk, Kısa/Uzun Sinyal + narrative notlar)
- **"📈 Grafiği Göster" butonu:** Aynı `InstrumentChartModal`'ı açar (Alarm, Watchlist, Portföye Ekle, Karşılaştır)

**Sağ — Finans Portalı AI Chatbot:**
- **Selamlama mesajı** + 5 hızlı soru butonu
- **Sticky panel** — desktop'ta sağda kalır, mobile'da altta
- **Mesaj geçmişi** (her oturumda sıfırdan)
- **Yapısal yanıtlar** belirli soru kalıpları için (bütçe sorusu → Güvenli/Dengeli/Riskli senaryolar; sembol sorusu → güncel veri + sinyaller)
- **Serbest sorular** Groq LLM (llama-3.3-70b) üzerinden gerçek zamanlı
- **Güvenlik kuralları:** Asla "%X kazanırsın" garantisi, asla doğrudan "Al/Sat" emri, sadece risk dilinde senaryolar
- **Konu kapsamı:** Sadece finans/yatırım/ekonomi. Yemek tarifi, hava, kod yardımı vs. nazikçe reddedilir.
- **Disclaimer:** Her cevapta "Bu içerik yatırım tavsiyesi değildir..." footer'ı

### 3.12 Portföyüm (`/portfolio`) — Auth gerekli

**Amaç:** Kullanıcının kişisel portföyünü yönetme.

**Bileşenler:**
- **4 özet kart:** Toplam Portföy Değeri, Toplam Kazanç/Kayıp, Pozisyon Sayısı, Durum
- **Pozisyon tablosu:** Sembol, İsim, Adet, Alış Tarihi, Alış Fiyatı, Güncel Fiyat, Değer, Toplam Değişim, Günlük Değişim, Sat butonu
- **Pozisyon ekle modal'ı** (manuel veya market sayfasından gelen pre-fill)
- **Portföy dağılım grafiği** (donut, varlık sınıfı bazında)
- **Sat işlemi** — kısmi veya tam, ortalama maliyet otomatik güncellenir

### 3.13 Geçmişten (`/historical`) — Auth gerekli

**Amaç:** Geçmişte yapılmış varsayımsal alımları izleme — "X tarihinde Y alsaydım nasıl olurdu" senaryosu.

**Bileşenler:**
- **3 özet kart:** Toplam Yatırılan, Güncel Değer, Kar/Zarar
- **Tablo:** Sembol, İsim, Alış Tarihi, Lot, Alış Fiyatı, Güncel Fiyat, Yatırılan, Güncel Değer, Kar/Zarar, Nominal %, Enflasyon %, **Reel %**
- **Yeni satır ekle** (sembol seçici + tarih + adet)
- **localStorage** tabanlı kalıcılık (kullanıcı browser'ında, backend'e gitmez)

### 3.14 Ayarlar (`/settings`) — Auth gerekli

**Amaç:** Kullanıcı tercihleri.

**Bölümler:**
- **Profil:** Ad, soyad, e-posta (Keycloak'a yazar)
- **Bildirimler:** E-posta alarm tercihleri (in-app vs e-posta, fiyat alarmları, haber bildirimleri)
- **Dil + Tema** (TR/EN, açık/koyu)
- **Para birimi gösterimi** (Orijinal / TL / USD)

### 3.15 Admin (`/admin`) — ADMIN rolü gerekli

**Amaç:** Sistem yönetimi.

**Bileşenler:**
- **Kullanıcı listesi** (Keycloak'tan)
- **Manuel veri yenileme** butonları (fiyat, fon, tahvil, haber)
- **2FA yönetimi:** Kullanıcı bazında "Require 2FA reset" / "Reset 2FA"
- **Cache temizleme**

## 4. Kullanıcı Yönetimi

### 4.1 Kayıt ve Giriş

- **Kayıt:** `/realms/finans/protocol/openid-connect/auth?...` → Keycloak hosted login → kullanıcı adı/şifre + e-posta doğrulama
- **Giriş:** Aynı endpoint üzerinden Authorization Code flow (PKCE)
- **2FA zorunluluğu:** İlk giriş + her mevcut kullanıcı bir sonraki girişinde **CONFIGURE_TOTP** required action'a düşer:
  1. Authenticator app QR kodunu tarar (Google Authenticator, Microsoft Authenticator, Authy)
  2. 6 haneli kod girer
  3. TOTP credential kaydedilir, sonraki girişlerde her seferinde sorulur
- **Şifre sıfırlama:** "Şifremi unuttum" → e-posta link → reset flow

### 4.2 Rol Bazlı Erişim

| Rol | Erişim |
|-----|--------|
| `user` (default) | Tüm public sayfalar + Portföy/Analiz/Geçmişten/Ayarlar |
| `admin` | Yukarısı + `/admin` paneli + manuel veri refresh endpoint'leri |

### 4.3 Kullanıcı Banlama / Devre Dışı Bırakma

Admin paneli üzerinden:
- **Disable:** Kullanıcı varlığını siler değil, sadece login'i engeller. Mevcut JWT token süresi bitene kadar geçerli kalır (default 5 dk).
- **Delete:** Kullanıcı ve tüm credential'ları silinir. Portföy verisi PostgreSQL'de yetim kalır (admin manuel temizleyebilir).
- **Force logout:** Tüm aktif session'lar sonlanır.

## 5. Çok Dillilik (i18n)

- **Diller:** Türkçe (varsayılan), İngilizce
- **Toggle:** Topbar'da TR/EN butonu, anlık dil değişimi
- **Kapsam:** Tüm UI metni, tablo başlıkları, butonlar, hata mesajları, AI chatbot system prompt, TermInfo glossary
- **Storage:** localStorage (`i18n.lang`)
- **Tooltip glossary:** 36 finans terimi (TÜFE, ÜFE, NAV, AUM, ISIN, vadeli, opsiyon, kupon, vade, getiri, market cap, dominans, ATH, vs.) — her ikona hover ile açıklama, dil tercihine göre TR veya EN

## 6. Bildirim ve Alarm

### Fiyat Alarmları
- Kullanıcı bir enstrüman için **hedef fiyat** ve yön (üstüne çıkarsa / altına düşerse) tanımlar
- `PriceAlertService` her piyasa refresh'inde alarmları kontrol eder
- Tetiklendiğinde: hem in-app notification hem de (kullanıcı tercih ettiyse) e-posta gönderir
- E-posta: Gmail SMTP üzerinden (App Password)

### Haber Bildirimleri
- Kullanıcı belirli kategorilerde yeni haber geldiğinde bildirim alır
- `NotificationService` yeni haber kaydında abone kullanıcılara push

## 7. Veri Yenileme Zamanlamaları

| Veri | Sıklık | Kaynak |
|------|--------|--------|
| BIST + ABD hisse + kripto + emtia + endeks fiyat | Startup + günlük 18:00 UTC | Yahoo Finance |
| TCMB döviz kurları | 4 saatte bir | TCMB XML |
| Yatırım fonları (NAV + getiri) | Hafta içi 10:30 / 14:30 / 18:30 | TEFAS |
| Tahvil + bono | 2 saatte bir | TCMB EVDS + İş Yatırım scrape |
| TÜFE / CPI | Günlük 09:00 | TCMB EVDS3 + FRED |
| VIOP | 15 dakikada bir | İş Yatırım scrape |
| Haber RSS | 6 saatte bir | 31 RSS feed |
| Haber otomatik çeviri | Fetch sonrası background prewarm + read-time lazy | LibreTranslate |

## 8. Kabul Kriterleri (Tamamlananlar)

- [x] Çoklu varlık sınıfı (8+ kategori) tek tabloda
- [x] Real-time fiyat (Yahoo'dan günlük + intraday gecikmeli)
- [x] AI destekli analiz (Groq LLM + kural tabanlı yapısal yanıtlar)
- [x] 2FA zorunlu (TOTP / Authenticator app)
- [x] Çok dilli (TR + EN), haber otomatik çeviri
- [x] Enflasyondan arındırılmış reel getiri (TR + ABD CPI referansı)
- [x] Mobil responsive
- [x] Watchlist + Portföy + Geçmiş alımlar
- [x] Fiyat alarmları (in-app + e-posta)
- [x] Tooltip glossary (36 finans terimi)
- [x] Open-source LLM (Groq llama-3.3-70b free tier)
- [x] Kod kalitesi — SonarCloud Quality Gate PASSED (Security/Reliability/Maintainability A, coverage ≥%80, duplication ≤%3; teknik detay için Teknik Analiz Dokümanı §8.5)
- [x] Continuous Deployment — `main` push'ta Google Cloud / GKE'ye otomatik deploy (Workload Identity Federation + Artifact Registry; teknik detay için Teknik Analiz Dokümanı §9.3-9.4, kurulum için `k8s/GKE_DEPLOYMENT.md`)

## 9. Kapsam Dışı (Bilinçli Karar)

- **jBPM / Ticket Management** — IT servis süreçleri için iş akışı motoru; bu projenin yatırım odağı dışında
- **Otomatik alım/satım** — broker API entegrasyonu yok, sadece analiz ve takip
- **Sosyal özellikler** — yorum, takip, sosyal trade yok
- **Forex marjin trading** — sadece spot kur izleme

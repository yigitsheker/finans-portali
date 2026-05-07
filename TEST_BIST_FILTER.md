# 🧪 BIST Endeks Filtreleme Testi

## Sorun: Kartlara tıklayamıyorum

### Çözüm Adımları:

#### 1. Tarayıcıda Hard Refresh Yapın

**Windows/Linux:**
```
Ctrl + Shift + R
```

**Mac:**
```
Cmd + Shift + R
```

Veya:
```
Ctrl + F5
```

#### 2. Frontend'i Yeniden Başlatın

**Terminal'de (frontend çalışıyorsa):**
```
Ctrl + C  (durdur)
npm run dev  (tekrar başlat)
```

**Yeni terminal'de:**
```powershell
cd frontend
npm run dev
```

#### 3. Browser Cache'i Temizleyin

**Chrome/Edge:**
1. F12 (Developer Tools)
2. Network tab
3. "Disable cache" işaretle
4. Sayfayı yenile

**Veya:**
1. Ctrl + Shift + Delete
2. "Cached images and files" seç
3. Clear data

#### 4. Test Edin

1. **XU100 (BIST 100) kartına tıklayın:**
   - Kart yeşil border almalı
   - "✓ Filtrelendi" yazısı görünmeli
   - Banner: "📊 BIST 100 hisseleri gösteriliyor"
   - Aşağıda 100 hisse görünmeli

2. **XU050 (BIST 50) kartına tıklayın:**
   - XU100 filtresi kalkmalı
   - XU050 aktif olmalı
   - Aşağıda 50 hisse görünmeli

3. **XU030 (BIST 30) kartına tıklayın:**
   - XU050 filtresi kalkmalı
   - XU030 aktif olmalı
   - Aşağıda 30 hisse görünmeli

4. **Aynı karta tekrar tıklayın:**
   - Filtre kalkmalı
   - Tüm hisseler görünmeli

## Beklenen Davranış

### Tıklamadan Önce:
```
┌─────────────────────────────────────┐
│ XU100                               │
│ 14.917,43                           │
│ ▲ +2,91%                            │
└─────────────────────────────────────┘

Tüm hisseler gösteriliyor (100+)
```

### XU100'e Tıkladıktan Sonra:
```
┌─────────────────────────────────────┐
│ XU100                               │ ← Yeşil border
│ 14.917,43                           │
│ ▲ +2,91%                            │
│ ✓ Filtrelendi                       │ ← Yeni
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ 📊 BIST 100 hisseleri gösteriliyor  │ ← Banner
│                [✕ Filtreyi Kaldır]  │
└─────────────────────────────────────┘

Sadece BIST 100 hisseleri (100 adet)
```

## Sorun Giderme

### Hala tıklayamıyorum

**Kontrol 1: Frontend çalışıyor mu?**
```powershell
# Tarayıcıda
http://localhost:5173
```

**Kontrol 2: Console'da hata var mı?**
```
F12 → Console tab
Kırmızı hata mesajları var mı?
```

**Kontrol 3: Dosya kaydedildi mi?**
```powershell
cd frontend/src/components
Get-Content MarketBrowser.tsx | Select-String "indexFilter"
```

Çıktı görmelisiniz:
```
const [indexFilter, setIndexFilter] = useState<string | null>(null);
if (indexFilter === "XU030") {
...
```

### Console'da hata görüyorum

**Hata: "BIST30_STOCKS is not defined"**

Çözüm: MarketBrowser.tsx dosyasının başında tanımlı olmalı:
```typescript
const BIST30_STOCKS = [
    "THYAO", "GARAN", "AKBNK", ...
];
```

**Hata: "Cannot read property 'includes' of undefined"**

Çözüm: items yüklenmeden önce filtreleme yapılıyor. Kod zaten bunu handle ediyor, hard refresh yapın.

### Kartlar görünüyor ama tıklanmıyor

**Kontrol: onClick handler var mı?**

MarketBrowser.tsx'de şu kod olmalı:
```typescript
onClick={() => {
    if (indexFilter === idx.symbol) {
        setIndexFilter(null);
    } else {
        setIndexFilter(idx.symbol);
        setFilter("BIST");
    }
}}
```

**Kontrol: CSS cursor var mı?**

```typescript
tickerCard: {
    cursor: "pointer",
    ...
}
```

## Başarı Kriterleri

✅ Kartlara tıklayabiliyorum  
✅ Kart yeşil border alıyor  
✅ "✓ Filtrelendi" yazısı görünüyor  
✅ Banner gösteriliyor  
✅ Sadece o endeksteki hisseler listeleniyor  
✅ Tekrar tıklayınca filtre kalkıyor  
✅ "✕ Filtreyi Kaldır" butonu çalışıyor  

## Hızlı Test Komutu

```powershell
# Frontend'i yeniden başlat
cd frontend
npm run dev

# Tarayıcıda
# 1. http://localhost:5173 aç
# 2. Ctrl + Shift + R (hard refresh)
# 3. XU100 kartına tıkla
# 4. Hisseler filtrelenmeli!
```

## Hala Çalışmıyor mu?

Şu bilgileri paylaşın:
1. Frontend console'da ne yazıyor? (F12 → Console)
2. Network tab'da hata var mı? (F12 → Network)
3. Hangi tarayıcı kullanıyorsunuz?
4. Frontend hangi port'ta çalışıyor?

Başarılar! 🚀

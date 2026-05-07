# BIST Endeks Filtreleme Özelliği

## Özet
"Hisse Fiyatları" sayfasındaki üstteki 3 endeks kartına (XU100, XU050, XU030) tıklama özelliği eklendi. Kullanıcılar artık bu kartlara tıklayarak sadece o endeksteki hisseleri görebilir.

## Yapılan Değişiklikler

### 1. Endeks Kompozisyonları Tanımlandı
- **BIST30_STOCKS**: 30 hisse senedi
- **BIST50_STOCKS**: 50 hisse senedi (BIST30 + 20 ek hisse)
- **BIST100_STOCKS**: 100 hisse senedi (BIST50 + 50 ek hisse)

### 2. Filtreleme Mantığı
- `indexFilter` state'i eklendi
- `filtered` useMemo'da endeks filtreleme mantığı eklendi
- XU030 → BIST30 hisseleri
- XU050 → BIST50 hisseleri
- XU100 → BIST100 hisseleri

### 3. Görsel Geri Bildirim
- **Aktif Kart**: Yeşil kenarlık (#22c55e), açık yeşil arka plan, gölge efekti
- **"✓ Filtrelendi" İşareti**: Aktif kartın üzerinde gösterilir
- **Toggle Özelliği**: Aynı karta tekrar tıklanırsa filtre kaldırılır

### 4. Filtre Banner'ı
- Aktif filtre olduğunda sayfanın üstünde bilgi banner'ı gösterilir
- "📊 XU100 endeksi hisseleri gösteriliyor" mesajı
- "✕ Filtreyi Kaldır" butonu ile filtreyi kaldırma

## Kullanım

1. "Hisse Fiyatları" sekmesine gidin
2. Üstteki 3 endeks kartından birine tıklayın (XU100, XU050, XU030)
3. Kart yeşil kenarlıkla vurgulanır ve "✓ Filtrelendi" işareti görünür
4. Aşağıdaki hisse listesi sadece o endeksteki hisseleri gösterir
5. Filtreyi kaldırmak için:
   - Aynı karta tekrar tıklayın VEYA
   - "✕ Filtreyi Kaldır" butonuna tıklayın

## Teknik Detaylar

**Dosya**: `frontend/src/components/FinexStyleMarket.tsx`

**Yeni State**:
```typescript
const [indexFilter, setIndexFilter] = useState<string | null>(null);
```

**Filtreleme Mantığı**:
```typescript
if (indexFilter === "XU030") {
    list = list.filter((i) => BIST30_STOCKS.includes(i.symbol));
} else if (indexFilter === "XU050") {
    list = list.filter((i) => BIST50_STOCKS.includes(i.symbol));
} else if (indexFilter === "XU100") {
    list = list.filter((i) => BIST100_STOCKS.includes(i.symbol));
}
```

**Stil Değişiklikleri**:
- `indexCardActive`: Aktif kart stili (yeşil kenarlık, arka plan, gölge)
- `filterBanner`: Filtre bilgi banner'ı stili
- `clearFilterBtn`: Filtreyi kaldır butonu stili

## Test Edildi
✅ TypeScript derleme hatası yok
✅ Kartlara tıklama çalışıyor
✅ Filtreleme mantığı doğru
✅ Görsel geri bildirim aktif
✅ Toggle özelliği çalışıyor

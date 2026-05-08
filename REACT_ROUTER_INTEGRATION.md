# React Router Entegrasyonu

## ✅ Yapılan Değişiklikler

### 1. App.tsx - Route-Based Navigation
**Öncesi:** Tab-based navigation (`useState` ile `tab` değişkeni)
**Sonrası:** React Router ile route-based navigation

**Değişiklikler:**
- `useState<Tab>` kaldırıldı
- `Routes` ve `Route` componentleri eklendi
- `useLocation` hook'u ile aktif sayfa takibi
- Her sayfa için ayrı route tanımlandı

**Route Yapısı:**
```typescript
/ → /news (redirect)
/news → Finans Haberleri
/market → Hisse Fiyatları
/market-data → Piyasa Verileri
/portfolio → Yatırımlarım
/settings → Ayarlar
/admin → Yönetim Paneli (sadece admin kullanıcılar)
```

### 2. Sidebar.tsx - Navigation Links
**Öncesi:** `onTabChange` callback ile manuel state değişimi
**Sonrası:** `useNavigate` hook'u ile programmatic navigation

**Değişiklikler:**
- `tab` ve `onTabChange` props'ları kaldırıldı
- `useNavigate` ve `useLocation` hooks eklendi
- Her menü item'ına `path` property'si eklendi
- `onClick` handler'lar `navigate(path)` kullanıyor
- Aktif sayfa kontrolü `location.pathname` ile yapılıyor

### 3. main.tsx - Router Wrapper
**Değişiklik yok:** Zaten `BrowserRouter` ile sarılıydı

## 🎯 Faydalar

### 1. **URL-Based Navigation**
- Her sayfa için benzersiz URL
- Örnek: `http://localhost/portfolio`, `http://localhost/market`

### 2. **Browser History**
- Geri/İleri butonları çalışıyor
- Sayfa geçmişi korunuyor

### 3. **Deep Linking**
- Direkt sayfa linklerini paylaşabilme
- Örnek: Kullanıcıya `/portfolio` linki gönderebilirsiniz

### 4. **Sayfa Yenileme**
- Sayfa yenilendiğinde son görüntülenen sayfa korunuyor
- Önceden her zaman varsayılan tab'a dönüyordu

### 5. **SEO & Analytics**
- URL değişimleri analytics'te izlenebilir
- Daha iyi kullanıcı davranışı analizi

## 🔧 Teknik Detaylar

### Route Mapping
```typescript
const pageInfo: Record<string, { title: string; subtitle: string; hideTopbar?: boolean }> = {
    "/news": {
        title: "Finans Haberleri",
        subtitle: "Kategorize edilmiş finans haberleri ve analiz.",
    },
    "/market": {
        title: "Hisse Fiyatları",
        subtitle: "Gerçek zamanlı hisse fiyatları ve piyasa performansı.",
        hideTopbar: true,
    },
    // ... diğer sayfalar
};
```

### Navigation Items
```typescript
const NAV_ITEMS: { id: Tab; label: string; icon: string; path: string }[] = [
    { id: "news", label: "Finans Haberleri", icon: "📊", path: "/news" },
    { id: "market", label: "Hisseler", icon: "📈", path: "/market" },
    { id: "market-data", label: "Piyasa Verileri", icon: "💱", path: "/market-data" },
    { id: "portfolio", label: "Yatırımlar", icon: "💼", path: "/portfolio" },
];
```

### Active Route Detection
```typescript
const isActive = (path: string) => location.pathname === path;
```

## 📦 Kullanılan Paketler

- **react-router-dom**: ^7.14.2
  - `BrowserRouter`: Router wrapper
  - `Routes`: Route container
  - `Route`: Individual route definition
  - `Navigate`: Programmatic redirect
  - `useNavigate`: Navigation hook
  - `useLocation`: Current location hook

## 🚀 Deployment Notları

### Nginx Konfigürasyonu
React Router ile SPA routing kullanıldığında, tüm route'ların `index.html`'e yönlendirilmesi gerekir.

**nginx.conf** dosyasında şu satır olmalı:
```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

Bu sayede:
- `/portfolio` → `index.html` (React Router devreye girer)
- `/market` → `index.html` (React Router devreye girer)
- Statik dosyalar (CSS, JS) → Doğrudan serve edilir

## 🧪 Test

### Manuel Test Adımları:
1. Uygulamayı açın: http://localhost
2. Sidebar'dan farklı sayfalara gidin
3. URL'nin değiştiğini kontrol edin
4. Browser'ın geri/ileri butonlarını test edin
5. Bir sayfadayken F5 ile yenileyin - aynı sayfada kalmalısınız
6. Direkt URL ile erişimi test edin: http://localhost/portfolio

### Beklenen Davranış:
✅ URL her sayfa değişiminde güncelleniyor
✅ Geri/İleri butonları çalışıyor
✅ Sayfa yenileme durumunda state korunuyor
✅ Direkt URL erişimi çalışıyor
✅ Aktif sayfa sidebar'da highlight ediliyor

## 📝 Gelecek İyileştirmeler

### 1. Lazy Loading
Büyük componentleri lazy load etmek için:
```typescript
const Portfolio = lazy(() => import('./pages/Portfolio'));
const Market = lazy(() => import('./components/FinexStyleMarket'));
```

### 2. Route Guards
Admin sayfası için route guard:
```typescript
<Route 
    path="/admin" 
    element={
        <ProtectedRoute requiredRole="admin">
            <Admin keycloak={keycloak} />
        </ProtectedRoute>
    } 
/>
```

### 3. Nested Routes
Alt sayfalar için nested routing:
```typescript
<Route path="/portfolio" element={<Portfolio />}>
    <Route path="positions" element={<Positions />} />
    <Route path="history" element={<History />} />
</Route>
```

### 4. Query Parameters
Filtreleme için query params:
```typescript
// /market?symbol=AAPL&interval=1d
const [searchParams] = useSearchParams();
const symbol = searchParams.get('symbol');
```

## 🔄 Geri Alma (Rollback)

Eğer eski tab-based sisteme dönmek isterseniz:
```bash
git checkout HEAD~1 -- frontend/src/App.tsx frontend/src/components/Sidebar.tsx
```

## 📚 Kaynaklar

- [React Router Documentation](https://reactrouter.com/)
- [React Router Tutorial](https://reactrouter.com/en/main/start/tutorial)
- [SPA Routing Best Practices](https://reactrouter.com/en/main/guides/ssr)

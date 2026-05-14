# 🎨 Finans Portalı - Keycloak Özel Tema

Modern finansal dashboard stilinde özelleştirilmiş Keycloak login teması.

## 📁 Tema Yapısı

```
keycloak-themes/
└── finance-theme/
    └── login/
        ├── theme.properties          # Tema yapılandırması
        ├── login.ftl                 # Login sayfası template
        ├── template.ftl              # Ana sayfa template
        ├── messages/                 # Çoklu dil desteği
        │   ├── messages_en.properties
        │   └── messages_tr.properties
        └── resources/
            ├── css/
            │   └── login.css         # Özel stiller
            └── img/
                └── (logo dosyaları)
```

## 🎨 Tasarım Özellikleri

### Renkler
- **Ana Renk:** Yeşil (#22c55e)
- **Arka Plan:** Koyu gradient (#080b08 → #0f1410)
- **Metin:** Açık yeşil (#e8f5e8)
- **Border:** rgba(34, 197, 94, 0.15)

### Özellikler
- ✅ Modern finansal dashboard görünümü
- ✅ Animasyonlu arka plan
- ✅ Glassmorphism login kartı
- ✅ Responsive tasarım
- ✅ Türkçe ve İngilizce dil desteği
- ✅ Smooth animasyonlar
- ✅ Erişilebilirlik uyumlu
- ✅ Dark theme optimized

## 🚀 Kurulum

### 1. Tema Dosyalarını Kontrol Et

Tema dosyalarının doğru konumda olduğundan emin olun:
```
finans-portali/keycloak-themes/finance-theme/login/
```

### 2. Docker Compose ile Başlat

```bash
# Container'ları durdur
docker-compose down

# Keycloak'ı yeniden başlat (tema mount edilecek)
docker-compose up -d keycloak

# Logları kontrol et
docker-compose logs -f keycloak
```

### 3. Keycloak Admin Console'da Temayı Aktifleştir

1. Keycloak Admin Console'a giriş yap: http://localhost:8090
   - Username: `admin`
   - Password: `admin`

2. Sol menüden **Realm Settings** seçin

3. **Themes** sekmesine gidin

4. **Login Theme** dropdown'ından `finance-theme` seçin

5. **Save** butonuna tıklayın

### 4. Test Et

1. Frontend'e git: http://localhost
2. **Login** butonuna tıkla
3. Yeni özelleştirilmiş login sayfasını gör!

## 🔧 Geliştirme

### Tema Cache'ini Devre Dışı Bırakma

`docker-compose.yml` dosyasında zaten ayarlanmış:

```yaml
environment:
  KC_SPI_THEME_STATIC_MAX_AGE: -1
  KC_SPI_THEME_CACHE_THEMES: false
  KC_SPI_THEME_CACHE_TEMPLATES: false
```

### CSS Değişiklikleri

1. `resources/css/login.css` dosyasını düzenle
2. Tarayıcıda hard refresh yap (Ctrl + Shift + R)
3. Değişiklikler hemen görünmeli

### Template Değişiklikleri

1. `login.ftl` veya `template.ftl` dosyasını düzenle
2. Keycloak container'ını restart et:
   ```bash
   docker-compose restart keycloak
   ```
3. Tarayıcıda hard refresh yap

## 🌍 Dil Desteği

### Desteklenen Diller
- 🇬🇧 English (en)
- 🇹🇷 Türkçe (tr)

### Yeni Dil Ekleme

1. `messages/messages_XX.properties` dosyası oluştur (XX = dil kodu)
2. Tüm mesajları çevir
3. `theme.properties` dosyasına dil kodunu ekle:
   ```properties
   locales=en,tr,XX
   ```

## 🎯 Özelleştirme

### Logo Değiştirme

`login.ftl` dosyasındaki SVG logo'yu düzenle:

```html
<svg width="48" height="48" viewBox="0 0 48 48">
    <!-- Kendi logo SVG kodunuz -->
</svg>
```

### Renk Değiştirme

`resources/css/login.css` dosyasında CSS değişkenlerini düzenle:

```css
:root {
    --primary-color: #22c55e;  /* Ana renk */
    --bg-dark: #080b08;        /* Arka plan */
    --text-light: #e8f5e8;     /* Metin rengi */
}
```

### Başlık ve Alt Başlık

`messages_en.properties` ve `messages_tr.properties` dosyalarını düzenle:

```properties
loginTitleHtml=Your Company Name
loginSubtitle=Your custom subtitle
```

## 📱 Responsive Breakpoints

- **Desktop:** 1920px+
- **Laptop:** 1024px - 1919px
- **Tablet:** 768px - 1023px
- **Mobile:** < 768px

## 🐛 Sorun Giderme

### Tema Görünmüyor

**Çözüm 1:** Keycloak Admin Console'da tema seçildiğinden emin olun

**Çözüm 2:** Container'ı restart edin
```bash
docker-compose restart keycloak
```

**Çözüm 3:** Volume mount'u kontrol edin
```bash
docker exec -it finans-keycloak ls -la /opt/keycloak/themes/finance-theme
```

### CSS Değişiklikleri Görünmüyor

**Çözüm 1:** Tarayıcı cache'ini temizle (Ctrl + Shift + R)

**Çözüm 2:** Tema cache ayarlarını kontrol et (docker-compose.yml)

**Çözüm 3:** Keycloak'ı restart et

### Login Formu Çalışmıyor

**Çözüm:** FreeMarker değişkenlerini kontrol edin. Keycloak'ın gerektirdiği tüm form alanları mevcut olmalı:
- `${url.loginAction}`
- `name="username"`
- `name="password"`
- `name="login"`

### Türkçe Karakterler Bozuk

**Çözüm:** `.properties` dosyalarının UTF-8 encoding ile kaydedildiğinden emin olun.

## 📚 Keycloak Tema Dokümantasyonu

Resmi Keycloak tema dokümantasyonu:
https://www.keycloak.org/docs/latest/server_development/#_themes

## 🔒 Güvenlik Notları

- ✅ Tema değişiklikleri authentication flow'u etkilemez
- ✅ Tüm Keycloak güvenlik özellikleri korunur
- ✅ LDAP, OAuth2, OIDC entegrasyonları çalışmaya devam eder
- ✅ 2FA/OTP sayfaları da aynı tema ile stillendirilir

## 📝 Production Notları

Production ortamına geçerken:

1. **Tema cache'ini aktifleştir:**
   ```yaml
   KC_SPI_THEME_CACHE_THEMES: true
   KC_SPI_THEME_CACHE_TEMPLATES: true
   KC_SPI_THEME_STATIC_MAX_AGE: 2592000  # 30 gün
   ```

2. **CSS/JS dosyalarını minify et**

3. **Resim dosyalarını optimize et**

4. **HTTPS kullan**

5. **Keycloak'ı production mode'da çalıştır:**
   ```yaml
   command: start --optimized
   ```

## 🎉 Başarı!

Artık Finans Portalı için özelleştirilmiş, profesyonel bir Keycloak login sayfanız var!

---

**Not:** Bu tema Keycloak 26.5.1 ile test edilmiştir. Farklı versiyonlarda küçük uyarlamalar gerekebilir.

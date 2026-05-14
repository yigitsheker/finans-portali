# 🎨 Keycloak Tema Kurulum Rehberi

## ✅ Durum: Tema Dosyaları Hazır!

Tema dosyaları başarıyla oluşturuldu ve Keycloak container'ına mount edildi.

## 📋 Keycloak Admin Console'da Temayı Aktifleştirme

### Adım 1: Keycloak Admin Console'a Giriş

1. Tarayıcınızda şu adresi açın: **http://localhost:8090**

2. **Administration Console** linkine tıklayın

3. Giriş bilgileri:
   - **Username:** `admin`
   - **Password:** `admin`

### Adım 2: Finans Realm'ini Seç

1. Sol üst köşede realm seçici dropdown'ı bulun (varsayılan olarak "master" yazıyor olabilir)

2. **finans** realm'ini seçin

   > ⚠️ **Önemli:** Eğer "finans" realm'i yoksa, önce realm'i oluşturmanız veya import etmeniz gerekir.

### Adım 3: Realm Settings'e Git

1. Sol menüden **Realm Settings** seçeneğine tıklayın

2. Üst menüden **Themes** sekmesine geçin

### Adım 4: Login Theme'i Ayarla

1. **Login Theme** dropdown menüsünü bulun

2. Dropdown'ı açın ve **finance-theme** seçeneğini seçin

3. Sayfanın altındaki **Save** butonuna tıklayın

### Adım 5: Değişiklikleri Test Et

1. Yeni bir incognito/private tarayıcı penceresi açın

2. Frontend'e gidin: **http://localhost**

3. **Login** butonuna tıklayın

4. Keycloak login sayfasına yönlendirileceksiniz

5. **Yeni özelleştirilmiş Finance Portal temasını görmelisiniz!** 🎉

## 🎨 Beklenen Görünüm

### Özelleştirilmiş Login Sayfası Özellikleri:

✅ **Yeşil gradient logo** (üstte)  
✅ **"Finans Portalı" başlığı**  
✅ **"Finansal verilerinize güvenli erişim sağlayın" alt başlığı**  
✅ **Koyu, animasyonlu arka plan** (subtle grid pattern)  
✅ **Glassmorphism login kartı** (yarı saydam, blur efekti)  
✅ **Modern yeşil input alanları**  
✅ **Gradient yeşil "Giriş Yap" butonu**  
✅ **Smooth animasyonlar** (fade in, scale)  
✅ **Responsive tasarım** (mobil uyumlu)

## 🌍 Dil Değiştirme

Eğer Keycloak'ta dil seçeneği aktifse:

1. Login sayfasının altında dil seçici görünecek
2. **English** veya **Türkçe** seçebilirsiniz
3. Tüm metinler otomatik olarak çevrilecek

## 🔧 Sorun Giderme

### Tema Dropdown'da Görünmüyor

**Çözüm 1:** Keycloak container'ını restart edin
```bash
docker-compose restart keycloak
```

**Çözüm 2:** Tema dosyalarının mount edildiğini kontrol edin
```bash
docker exec finans-keycloak ls -la /opt/keycloak/themes/finance-theme/login/
```

**Çözüm 3:** Keycloak loglarını kontrol edin
```bash
docker-compose logs keycloak --tail=100
```

### Tema Seçildi Ama Görünmüyor

**Çözüm 1:** Tarayıcı cache'ini temizleyin
- Chrome/Edge: `Ctrl + Shift + Delete`
- Veya incognito/private mode kullanın

**Çözüm 2:** Hard refresh yapın
- `Ctrl + Shift + R` (Windows/Linux)
- `Cmd + Shift + R` (Mac)

**Çözüm 3:** Realm'in doğru seçildiğinden emin olun
- Admin Console'da **finans** realm'inde olmalısınız
- Master realm'de değil!

### CSS Yüklenmiyor

**Kontrol 1:** CSS dosyasının varlığını kontrol edin
```bash
docker exec finans-keycloak cat /opt/keycloak/themes/finance-theme/login/resources/css/login.css
```

**Kontrol 2:** theme.properties dosyasını kontrol edin
```bash
docker exec finans-keycloak cat /opt/keycloak/themes/finance-theme/login/theme.properties
```

Şu satırın olduğundan emin olun:
```
styles=css/login.css
```

### Login Formu Çalışmıyor

**Çözüm:** FreeMarker template hatası olabilir. Keycloak loglarını kontrol edin:
```bash
docker-compose logs keycloak | grep -i error
```

## 📱 Mobil Test

Mobil görünümü test etmek için:

1. Chrome DevTools'u açın (F12)
2. Device Toolbar'ı aktifleştirin (Ctrl + Shift + M)
3. Farklı cihaz boyutlarını test edin:
   - iPhone 12 Pro
   - iPad
   - Samsung Galaxy S20

## 🎯 Sonraki Adımlar

### Temayı Daha Fazla Özelleştirme

1. **Logo değiştirme:**
   - `login.ftl` dosyasındaki SVG logo'yu düzenleyin

2. **Renkleri değiştirme:**
   - `resources/css/login.css` dosyasındaki CSS değişkenlerini düzenleyin

3. **Metinleri değiştirme:**
   - `messages/messages_en.properties` (İngilizce)
   - `messages/messages_tr.properties` (Türkçe)

4. **Arka plan değiştirme:**
   - `login.css` dosyasındaki `.finance-background` sınıfını düzenleyin

### Diğer Sayfaları Özelleştirme

Aynı temayı diğer Keycloak sayfalarına da uygulayabilirsiniz:

- **OTP/2FA sayfası:** `login-otp.ftl` oluşturun
- **Şifre sıfırlama:** `login-reset-password.ftl` oluşturun
- **Kayıt sayfası:** `register.ftl` oluşturun
- **Hata sayfası:** `error.ftl` oluşturun

## 📚 Ek Kaynaklar

- [Keycloak Tema Dokümantasyonu](https://www.keycloak.org/docs/latest/server_development/#_themes)
- [FreeMarker Template Dili](https://freemarker.apache.org/docs/)
- [Keycloak GitHub](https://github.com/keycloak/keycloak)

## ✅ Başarı Kontrol Listesi

- [ ] Keycloak Admin Console'a giriş yapıldı
- [ ] Finans realm'i seçildi
- [ ] Realm Settings → Themes sekmesi açıldı
- [ ] Login Theme olarak "finance-theme" seçildi
- [ ] Save butonuna tıklandı
- [ ] Frontend'den login sayfasına gidildi
- [ ] Özelleştirilmiş tema görüldü
- [ ] Login işlemi başarıyla tamamlandı
- [ ] Frontend'e geri dönüldü

## 🎉 Tebrikler!

Artık Finans Portalı için profesyonel, özelleştirilmiş bir Keycloak login sayfanız var!

---

**Son Güncelleme:** 9 Mayıs 2026  
**Keycloak Versiyonu:** 26.5.1  
**Tema Versiyonu:** 1.0.0

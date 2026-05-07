# 🚀 Email Test - Hızlı Başlangıç

## 1️⃣ Gmail App Password Oluşturun (5 dakika)

### Adımlar:

1. **2-Step Verification Aktif Edin:**
   - https://myaccount.google.com/security
   - "2-Step Verification" → Aktif edin

2. **App Password Oluşturun:**
   - https://myaccount.google.com/apppasswords
   - App: **Mail**
   - Device: **Other** → "Finans Portali"
   - **Generate** tıklayın

3. **16 Karakterlik Şifreyi Kopyalayın:**
   ```
   Örnek: abcd efgh ijkl mnop
   Boşluksuz: abcdefghijklmnop
   ```

**Detaylı kılavuz:** `GMAIL_APP_PASSWORD_GUIDE.md`

## 2️⃣ Email Ayarlarını Kaydedin

`backend/.env.local` dosyasını açın ve düzenleyin:

```bash
MAIL_USERNAME=finansportali1@gmail.com
MAIL_PASSWORD=abcdefghijklmnop  # App Password buraya (boşluksuz!)
```

**Kaydedin!** ✅

## 3️⃣ Backend'i Başlatın

```powershell
./START_BACKEND.ps1
```

**Veya manuel:**

```powershell
cd backend
./mvnw spring-boot:run
```

**Başladığını kontrol edin:**
```
Started BackendApplication in X.XXX seconds
```

## 4️⃣ Keycloak'ta Email Kontrolü

1. http://localhost:8081 → Admin Console
2. **Username:** admin
3. **Password:** admin
4. Users → Kullanıcınızı seçin
5. **Email** alanı dolu mu?
   - Doluysa: ✅ Devam edin
   - Boşsa: Email ekleyin ve Save edin

## 5️⃣ Test Edin!

1. **Frontend'de alarm oluşturun:**
   - Portföy sayfası → "Fiyat Alarmları"
   - Sembol: THYAO (veya herhangi biri)
   - Alarm Tipi: Fiyat Üstü
   - Hedef Fiyat: 100
   - Not: "Test alarm"
   - **Alarm Oluştur**

2. **Test butonuna basın:**
   - Alarmın yanındaki **"🧪 Test Et"** butonuna tıklayın
   - Onaylayın

3. **Backend loglarını kontrol edin:**
   ```
   🔔 PRICE ALERT: THYAO fiyatı 100.00 seviyesini aştı!
   Found email in JWT token: your@email.com
   ✅ Email notification sent to your@email.com
   ```

4. **Email kutunuzu kontrol edin!** 📧
   - Inbox
   - Spam klasörü
   - 1-2 dakika bekleyin

## ✅ Başarı!

Email geldi mi? Tebrikler! 🎉

Email gelmediyse:
- Backend loglarını kontrol edin
- `TROUBLESHOOTING.md` dosyasına bakın
- App Password'ü kontrol edin

## 📋 Checklist

- [ ] Gmail App Password oluşturuldu
- [ ] .env.local dosyası düzenlendi
- [ ] Backend başlatıldı
- [ ] Keycloak'ta email var
- [ ] Alarm oluşturuldu
- [ ] Test butonuna basıldı
- [ ] Backend logları kontrol edildi
- [ ] Email geldi! 🎉

## 🆘 Sorun mu var?

### Backend başlamıyor
```powershell
cd backend
./mvnw clean install -DskipTests
./mvnw spring-boot:run
```

### "Invalid credentials" hatası
- App Password'ü kontrol edin (16 karakter, boşluksuz)
- .env.local dosyasını kaydettiğinizden emin olun
- Backend'i yeniden başlatın

### Email gelmiyor ama log "✅ Email sent" diyor
- Spam klasörünü kontrol edin
- 5 dakika bekleyin
- Gmail hesabının aktif olduğunu doğrulayın

### JWT'de email yok
- Keycloak'ta email ekleyin
- Çıkış yapıp tekrar giriş yapın
- Token'ı yenileyin

## 🎯 Sonraki Adımlar

Email çalışıyor mu? Harika! Şimdi:

1. ✅ Otomatik alarm kontrolü (scheduled task)
2. ✅ Email template özelleştirme
3. ✅ Production email servisi (SendGrid/AWS SES)
4. ✅ Email tercihleri (kullanıcı ayarları)

Başarılar! 🚀

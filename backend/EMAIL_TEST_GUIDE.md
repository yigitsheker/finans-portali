# Email Notification Test Guide

## Manuel Alarm Tetikleme ile Email Testi

Artık her alarmın yanında **"🧪 Test Et"** butonu var. Bu buton ile alarmı manuel olarak tetikleyip email gönderimini test edebilirsiniz.

## Test Adımları

### 1. Email Ayarlarını Yapılandırın

Uygulamayı başlatmadan önce bu environment variable'ları ayarlayın:

**Windows (PowerShell):**
```powershell
$env:MAIL_USERNAME="your-email@gmail.com"
$env:MAIL_PASSWORD="your-app-password"
$env:TEST_EMAIL="test-recipient@example.com"
```

**Windows (CMD):**
```cmd
set MAIL_USERNAME=your-email@gmail.com
set MAIL_PASSWORD=your-app-password
set TEST_EMAIL=test-recipient@example.com
```

**Linux/Mac:**
```bash
export MAIL_USERNAME="your-email@gmail.com"
export MAIL_PASSWORD="your-app-password"
export TEST_EMAIL="test-recipient@example.com"
```

### 2. Gmail App Password Oluşturun (Gmail kullanıyorsanız)

1. Google hesabınızda 2-Factor Authentication'ı aktif edin
2. https://myaccount.google.com/apppasswords adresine gidin
3. "Mail" ve cihazınızı seçin
4. Oluşturulan 16 karakterlik şifreyi kopyalayın
5. Bu şifreyi `MAIL_PASSWORD` olarak kullanın

### 3. Backend'i Başlatın

```bash
cd backend
./mvnw spring-boot:run
```

Backend başlarken şu logları göreceksiniz:
```
Using test email for user xxx: test-recipient@example.com
```

### 4. Frontend'i Başlatın

```bash
cd frontend
npm run dev
```

### 5. Alarm Oluşturun

1. Uygulamaya giriş yapın
2. Portföy sayfasına gidin
3. "Fiyat Alarmları" butonuna tıklayın
4. Yeni bir alarm oluşturun:
   - Sembol: AAPL (veya herhangi bir sembol)
   - Alarm Tipi: Fiyat Üstü
   - Hedef Fiyat: 200
   - Not: "Test alarm - email kontrolü"

### 6. Test Butonuna Basın

1. Oluşturduğunuz alarmın yanında **"🧪 Test Et"** butonunu göreceksiniz
2. Bu butona tıklayın
3. Onay mesajını kabul edin
4. Başarılı olursa şu mesajı göreceksiniz:
   ```
   ✅ Alarm manuel olarak tetiklendi ve email gönderildi
   
   Email gönderildi! (Eğer email ayarları yapılandırılmışsa)
   ```

### 7. Email'inizi Kontrol Edin

`TEST_EMAIL` adresine gönderilen email'i kontrol edin. Email şunları içerecek:

- **Konu:** 🔔 Fiyat Alarmı Tetiklendi: AAPL
- **İçerik:**
  - Sembol ve enstrüman adı
  - Alarm tipi (Fiyat Üstü, Fiyat Altı, vb.)
  - Hedef fiyat
  - Mevcut fiyat
  - Oluşturulma fiyatı
  - Kullanıcı notu (eğer eklediyseniz)

## Email Görünümü

Email şu şekilde görünecek:

```
┌─────────────────────────────────────┐
│  🔔 Fiyat Alarmı Tetiklendi         │
│  (Yeşil gradient header)            │
└─────────────────────────────────────┘

AAPL
Apple Inc.

AAPL (Apple Inc.) fiyatı 200.00 seviyesini aştı! 
Mevcut: 150.25, Hedef: 200.00

Alarm Detayları
─────────────────────────────────────
Alarm Tipi:        Fiyat Üstü
Hedef Fiyat:       200.00
Mevcut Fiyat:      150.25
Oluşturulma Fiyatı: 150.25

┌─────────────────────────────────────┐
│ 📝 Notunuz:                         │
│ Test alarm - email kontrolü         │
└─────────────────────────────────────┘

Bu alarm otomatik olarak devre dışı bırakıldı.
```

## Sorun Giderme

### Email Gönderilmiyor

**1. Environment variable'ları kontrol edin:**
```bash
echo $MAIL_USERNAME
echo $TEST_EMAIL
```

**2. Backend loglarını kontrol edin:**
```
Email notification sent to test-recipient@example.com for alert 123
```

veya hata varsa:
```
Failed to send email notification for alert 123: Authentication failed
```

**3. Gmail "Less secure app" hatası:**
- Normal şifre yerine App Password kullanın
- 2-Factor Authentication aktif olmalı

**4. Connection timeout:**
- Port 587'nin açık olduğundan emin olun
- Firewall ayarlarını kontrol edin
- Alternatif olarak port 465 (SSL) deneyin

**5. "No email found for user" hatası:**
- `TEST_EMAIL` environment variable'ının set edildiğinden emin olun
- Backend'i yeniden başlatın

### Test Butonu Görünmüyor

- Sadece **aktif** alarmlar için test butonu görünür
- Tetiklenmiş alarmlar için test butonu gösterilmez
- Sayfayı yenileyin

### Alarm Tetiklenmiyor

Backend loglarında şu hatayı görüyorsanız:
```
Mevcut fiyat alınamadı: SYMBOL
```

Bu durumda:
1. Sembolün doğru olduğundan emin olun
2. Market verilerinin yüklendiğini kontrol edin
3. Backend'in Yahoo Finance'e erişebildiğinden emin olun

## Diğer Email Sağlayıcıları

### Outlook/Hotmail

```yaml
spring:
  mail:
    host: smtp-mail.outlook.com
    port: 587
```

### Yahoo Mail

```yaml
spring:
  mail:
    host: smtp.mail.yahoo.com
    port: 587
```

### Custom SMTP

```yaml
spring:
  mail:
    host: your-smtp-server.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
```

## Production Kullanımı

Test tamamlandıktan sonra:

1. `TEST_EMAIL` environment variable'ını kaldırın
2. `KeycloakUserService.java` dosyasında gerçek email alma implementasyonunu yapın:
   - JWT token'dan email claim'i çıkarın
   - Veya Keycloak Admin API kullanın
   - Veya database'de user email'i saklayın

3. Test butonunu production'da gizleyin veya sadece admin kullanıcılara gösterin

## Güvenlik Notları

- Email credentials'ları asla git'e commit etmeyin
- Production'da secret management sistemi kullanın (AWS Secrets Manager, Azure Key Vault, vb.)
- Email gönderim rate limiting ekleyin
- Spam önleme mekanizmaları ekleyin
- Email queue sistemi kullanın (RabbitMQ, Redis, vb.)

## İleri Seviye Özellikler

### Email Template Özelleştirme

`NotificationService.java` dosyasındaki `buildAlertEmailHtml()` metodunu düzenleyerek email template'ini özelleştirebilirsiniz.

### Farklı Notification Türleri

Gelecekte eklenebilecek özellikler:
- Push notifications (WebSocket)
- SMS notifications
- Slack/Discord webhooks
- Telegram bot notifications

### Email Preferences

Kullanıcıların email tercihlerini database'de saklayın:
- Email almak isteyip istemedikleri
- Hangi alarm türleri için email alacakları
- Email sıklığı (immediate, daily digest, vb.)

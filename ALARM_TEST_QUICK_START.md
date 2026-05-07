# 🧪 Alarm Test - Hızlı Başlangıç

## Email Olmadan Test (Hemen Deneyin!)

Backend ve frontend çalışıyorsa, email ayarları olmadan da test edebilirsiniz:

1. ✅ Bir alarm oluşturun
2. ✅ **"🧪 Test Et"** butonuna basın
3. ✅ Alarm tetiklenir ve şu mesajı görürsünüz:

```
✅ Başarılı!

Alarm tetiklendi! Email göndermek için TEST_EMAIL ve 
MAIL_USERNAME environment variable'larını ayarlayın.
```

4. ✅ Backend loglarında şunu göreceksiniz:
```
🔔 PRICE ALERT: THYAO (Türk Hava Yolları) fiyatı 100.00 seviyesini aştı!
⚠️ No email found for user xxx, skipping email notification. 
   Set TEST_EMAIL environment variable for testing.
```

## Email ile Test (Gerçek Email Göndermek İçin)

### Adım 1: Gmail App Password Oluşturun

1. Google hesabınızda 2-Factor Authentication'ı aktif edin
2. https://myaccount.google.com/apppasswords adresine gidin
3. "Mail" seçin, cihazınızı seçin
4. 16 karakterlik şifreyi kopyalayın (örnek: `abcd efgh ijkl mnop`)

### Adım 2: Environment Variable'ları Ayarlayın

**PowerShell'de (Backend başlatmadan önce):**

```powershell
# Email gönderen hesap
$env:MAIL_USERNAME = "your-email@gmail.com"
$env:MAIL_PASSWORD = "abcd efgh ijkl mnop"  # App password (boşluksuz)

# Email alacak test hesabı
$env:TEST_EMAIL = "test-recipient@example.com"

# Kontrol edin
Write-Host "MAIL_USERNAME: $env:MAIL_USERNAME"
Write-Host "MAIL_PASSWORD: SET"
Write-Host "TEST_EMAIL: $env:TEST_EMAIL"
```

### Adım 3: Backend'i Başlatın

```powershell
cd backend
./mvnw spring-boot:run
```

Backend başladığında logları kontrol edin:
```
✅ Mail configuration loaded
✅ SMTP host: smtp.gmail.com
```

### Adım 4: Test Edin!

1. Frontend'de bir alarm oluşturun
2. **"🧪 Test Et"** butonuna basın
3. Şu mesajı göreceksiniz:

```
✅ Başarılı!

Alarm tetiklendi ve email gönderildi: test-recipient@example.com
```

4. Email kutunuzu kontrol edin! 📧

## Email İçeriği

Gelen email şöyle görünecek:

**Konu:** 🔔 Fiyat Alarmı Tetiklendi: THYAO

**İçerik:**
- Yeşil gradient header
- Sembol ve enstrüman adı
- Alarm detayları (tip, hedef, mevcut fiyat)
- Kullanıcı notu (eğer eklediyseniz)
- Profesyonel HTML tasarım

## Sorun Giderme

### "Alarm tetiklenemedi: Mevcut fiyat alınamadı"

**Çözüm:** Gerçek bir sembol kullanın (AAPL, THYAO, GARAN, vb.)

### Email gelmiyor

**Kontrol listesi:**
- [ ] Environment variable'lar doğru set edildi mi?
- [ ] Backend yeniden başlatıldı mı?
- [ ] Gmail App Password kullanılıyor mu? (normal şifre değil)
- [ ] 2-Factor Authentication aktif mi?
- [ ] Spam klasörünü kontrol ettiniz mi?

**Backend loglarına bakın:**
```
✅ Email notification sent to test@example.com for alert 123
```

veya

```
❌ Failed to send email: Authentication failed
```

### Port 587 blocked

Eğer connection timeout alıyorsanız:

```yaml
# application.yml'de port'u değiştirin
spring:
  mail:
    port: 465  # SSL port
    properties:
      mail:
        smtp:
          ssl:
            enable: true
```

## Diğer Email Sağlayıcıları

### Outlook/Hotmail

```powershell
$env:MAIL_USERNAME = "your-email@outlook.com"
$env:MAIL_PASSWORD = "your-password"
```

`application.yml`:
```yaml
spring:
  mail:
    host: smtp-mail.outlook.com
    port: 587
```

### Yahoo Mail

```powershell
$env:MAIL_USERNAME = "your-email@yahoo.com"
$env:MAIL_PASSWORD = "your-app-password"
```

`application.yml`:
```yaml
spring:
  mail:
    host: smtp.mail.yahoo.com
    port: 587
```

## Test Tamamlandıktan Sonra

1. ✅ Email gönderimi çalışıyor
2. ✅ Alarm sistemi çalışıyor
3. ✅ Frontend butonu çalışıyor

**Sonraki adımlar:**
- Gerçek kullanıcı email'lerini Keycloak'tan çekin
- Production email servisi kullanın (SendGrid, AWS SES, vb.)
- Email queue sistemi ekleyin
- Rate limiting ekleyin

## Hızlı Komutlar

**Environment variable'ları temizle:**
```powershell
Remove-Item Env:MAIL_USERNAME
Remove-Item Env:MAIL_PASSWORD
Remove-Item Env:TEST_EMAIL
```

**Backend'i environment variable'larla başlat:**
```powershell
$env:MAIL_USERNAME="your@email.com"; $env:MAIL_PASSWORD="password"; $env:TEST_EMAIL="test@email.com"; cd backend; ./mvnw spring-boot:run
```

**Logları takip et:**
```powershell
# Backend loglarını izle
Get-Content backend/logs/finans-backend.log -Wait -Tail 50
```

## Başarı Göstergeleri

✅ Test butonu görünüyor  
✅ Butona basınca onay mesajı çıkıyor  
✅ Backend'de "🔔 PRICE ALERT" logu görünüyor  
✅ Email ayarları varsa "✅ Email notification sent" logu görünüyor  
✅ Alarm "Tetiklendi" durumuna geçiyor  
✅ Test butonu kaybolup sadece "Sil" butonu kalıyor  

Tebrikler! Alarm sisteminiz çalışıyor! 🎉

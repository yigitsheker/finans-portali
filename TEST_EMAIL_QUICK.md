# 🚀 Hızlı Email Test Kılavuzu

## Adım 1: SMTP Ayarlarını Yapın

### Gmail Kullanıyorsanız:

1. **App Password Oluşturun:**
   - https://myaccount.google.com/apppasswords adresine gidin
   - "Mail" seçin
   - 16 karakterlik şifreyi kopyalayın (örnek: `abcd efgh ijkl mnop`)

2. **PowerShell'de Environment Variable'ları Ayarlayın:**

```powershell
# Boşlukları kaldırarak girin
$env:MAIL_USERNAME = "your-email@gmail.com"
$env:MAIL_PASSWORD = "abcdefghijklmnop"

# Kontrol edin
Write-Host "MAIL_USERNAME: $env:MAIL_USERNAME"
Write-Host "MAIL_PASSWORD: AYARLI"
```

### Outlook Kullanıyorsanız:

```powershell
$env:MAIL_USERNAME = "your-email@outlook.com"
$env:MAIL_PASSWORD = "your-password"
```

**application.yml'de değiştirin:**
```yaml
spring:
  mail:
    host: smtp-mail.outlook.com
    port: 587
```

## Adım 2: Backend'i Başlatın

```powershell
cd backend
./mvnw spring-boot:run
```

**Başlangıç loglarında arayın:**
```
Started BackendApplication in X seconds
```

## Adım 3: Keycloak'ta Email Kontrolü

1. Keycloak Admin Console'a girin: http://localhost:8081
2. Users → Kullanıcınızı seçin
3. **Email** alanının dolu olduğunu kontrol edin
4. **Email Verified** işaretli olmalı

**Email yoksa:**
- Email ekleyin
- Save edin
- Uygulamadan çıkış yapıp tekrar giriş yapın

## Adım 4: Test Edin!

1. Frontend'de alarm oluşturun
2. **"🧪 Test Et"** butonuna basın
3. Backend loglarını izleyin

## Backend Loglarını İzleme

**Yeni terminal açın:**

```powershell
cd backend
Get-Content logs/finans-backend.log -Wait -Tail 20 | Select-String -Pattern "PRICE ALERT|Email|JWT|⚠️|❌|✅"
```

## Beklenen Loglar

### ✅ Başarılı Durum:

```
🔔 PRICE ALERT: THYAO (Türk Hava Yolları) fiyatı 100.00 seviyesini aştı!
Found email in JWT token: user@example.com
Found username in JWT token: johndoe
✅ Email notification sent to user@example.com for alert 123
Alarm tetiklendi: userId=xxx symbol=THYAO type=PRICE_ABOVE
```

### ⚠️ Email Yok:

```
🔔 PRICE ALERT: THYAO (Türk Hava Yolları) fiyatı 100.00 seviyesini aştı!
⚠️ No email found in JWT token for user xxx, skipping email notification
```

**Çözüm:** Keycloak'ta email ekleyin

### ❌ SMTP Hatası:

```
🔔 PRICE ALERT: THYAO (Türk Hava Yolları) fiyatı 100.00 seviyesini aştı!
Found email in JWT token: user@example.com
❌ Failed to send email notification: Authentication failed
```

**Çözüm:** SMTP ayarlarını kontrol edin

## Sorun Giderme

### "Başarılı" mesajı geldi ama log yok

**Neden:** Backend'e istek gitmemiş

**Çözüm:**
1. Backend çalışıyor mu kontrol edin: http://localhost:8080/actuator/health
2. Browser console'da hata var mı kontrol edin (F12)
3. CORS hatası varsa backend'i yeniden başlatın

### "Authentication failed" hatası

**Gmail için:**
- Normal şifre değil App Password kullanın
- 16 karakteri boşluksuz girin: `abcdefghijklmnop`
- 2-Factor Authentication aktif olmalı

**Outlook için:**
- Normal şifrenizi kullanın
- application.yml'de host'u değiştirin: `smtp-mail.outlook.com`

### "Connection timeout" hatası

**Çözüm 1:** Port 465 deneyin

```yaml
spring:
  mail:
    port: 465
    properties:
      mail:
        smtp:
          ssl:
            enable: true
```

**Çözüm 2:** Firewall'u kontrol edin
- Port 587 veya 465 açık olmalı

### Email gelmiyor ama log "✅ Email sent" diyor

**Kontrol listesi:**
1. Spam klasörünü kontrol edin
2. Email adresini doğru yazdınız mı?
3. Gmail'de "Less secure apps" engeli var mı?
4. Email quota'nız dolmuş olabilir mi?

## Test Checklist

- [ ] SMTP ayarları yapıldı (`MAIL_USERNAME`, `MAIL_PASSWORD`)
- [ ] Backend başlatıldı
- [ ] Keycloak'ta email var
- [ ] Alarm oluşturuldu
- [ ] Test butonuna basıldı
- [ ] Backend logları kontrol edildi
- [ ] Email kutusu kontrol edildi (spam dahil)

## Hızlı Test Komutu

Tek komutla her şeyi test edin:

```powershell
# SMTP ayarlarını yapın
$env:MAIL_USERNAME="your@email.com"
$env:MAIL_PASSWORD="your-password"

# Backend'i başlatın
cd backend
./mvnw spring-boot:run

# Başka terminalde logları izleyin
Get-Content logs/finans-backend.log -Wait -Tail 20
```

## Başarı Kriterleri

✅ Backend başladı  
✅ SMTP ayarları yapıldı  
✅ Keycloak'ta email var  
✅ Test butonuna basıldı  
✅ Backend'de "🔔 PRICE ALERT" logu görüldü  
✅ Backend'de "✅ Email sent" logu görüldü  
✅ Email geldi!  

Tebrikler! Email sisteminiz çalışıyor! 🎉

# 📧 Gmail App Password Oluşturma Kılavuzu

## Neden App Password Gerekli?

Gmail, güvenlik nedeniyle normal şifre ile 3. parti uygulamalara izin vermiyor. App Password oluşturmanız gerekiyor.

## Adım Adım Kılavuz

### 1. 2-Step Verification Aktif Edin

1. https://myaccount.google.com/security adresine gidin
2. "2-Step Verification" bölümünü bulun
3. Eğer kapalıysa, "Get Started" tıklayın
4. Telefon numaranızı ekleyin ve doğrulayın
5. 2-Step Verification'ı aktif edin

**Ekran Görüntüsü:**
```
┌─────────────────────────────────────┐
│ 2-Step Verification                 │
│ ○ OFF  →  ● ON                      │
└─────────────────────────────────────┘
```

### 2. App Password Oluşturun

1. https://myaccount.google.com/apppasswords adresine gidin
2. Google şifrenizi girin (tekrar giriş isteyebilir)
3. "Select app" dropdown'ından **"Mail"** seçin
4. "Select device" dropdown'ından **"Other (Custom name)"** seçin
5. İsim girin: **"Finans Portali"**
6. **"Generate"** butonuna tıklayın

**Ekran Görüntüsü:**
```
┌─────────────────────────────────────┐
│ App passwords                        │
│                                      │
│ Select app:  [Mail ▼]               │
│ Select device: [Other ▼]            │
│ Name: Finans Portali                │
│                                      │
│ [Generate]                           │
└─────────────────────────────────────┘
```

### 3. App Password'ü Kopyalayın

Ekranda 16 karakterlik bir şifre görünecek:

```
┌─────────────────────────────────────┐
│ Your app password for your device   │
│                                      │
│   abcd efgh ijkl mnop               │
│                                      │
│ [Done]                               │
└─────────────────────────────────────┘
```

**ÖNEMLİ:** Boşlukları kaldırın!
- ❌ Yanlış: `abcd efgh ijkl mnop`
- ✅ Doğru: `abcdefghijklmnop`

### 4. .env.local Dosyasına Yazın

`backend/.env.local` dosyasını açın ve şifreyi yazın:

```bash
MAIL_USERNAME=finansportali1@gmail.com
MAIL_PASSWORD=abcdefghijklmnop
```

**Kaydedin!**

### 5. Backend'i Başlatın

```powershell
./START_BACKEND.ps1
```

## Sorun Giderme

### "2-Step Verification bulamıyorum"

**Çözüm:**
1. https://myaccount.google.com/security
2. Sayfayı aşağı kaydırın
3. "Signing in to Google" bölümünü bulun
4. "2-Step Verification" tıklayın

### "App passwords seçeneği yok"

**Nedenleri:**
1. 2-Step Verification aktif değil → Önce aktif edin
2. G Suite hesabı → Admin'den izin alın
3. Eski Gmail hesabı → Güvenlik ayarlarını güncelleyin

**Çözüm:**
1. 2-Step Verification'ı kontrol edin
2. https://myaccount.google.com/apppasswords direkt gidin
3. Hala görmüyorsanız, farklı bir Gmail hesabı deneyin

### "Invalid credentials" hatası

**Kontrol listesi:**
- [ ] App Password'ü doğru kopyaladınız mı?
- [ ] Boşlukları kaldırdınız mı?
- [ ] Normal şifre yerine App Password kullanıyor musunuz?
- [ ] .env.local dosyasını kaydettiniz mi?
- [ ] Backend'i yeniden başlattınız mı?

**Test:**
```powershell
# PowerShell'de kontrol edin
Write-Host "MAIL_USERNAME: $env:MAIL_USERNAME"
Write-Host "MAIL_PASSWORD length: $($env:MAIL_PASSWORD.Length)"
# 16 karakter olmalı
```

### "Less secure app access" hatası

**Çözüm:** App Password kullanın, "Less secure app" ayarını değiştirmeyin!

Gmail artık "Less secure app" seçeneğini kaldırdı. App Password kullanmalısınız.

## Alternatif: Outlook Kullanın

Gmail karmaşık geliyorsa, Outlook daha basit:

### Outlook Ayarları

```bash
# backend/.env.local
MAIL_USERNAME=your-email@outlook.com
MAIL_PASSWORD=your-normal-password
```

### application.yml Değişikliği

```yaml
spring:
  mail:
    host: smtp-mail.outlook.com
    port: 587
```

**Avantajlar:**
✅ Normal şifre kullanabilirsiniz
✅ App Password gerekmez
✅ Daha basit

**Dezavantajlar:**
❌ Daha az güvenli
❌ Spam filtreleri daha sıkı

## Test

### 1. Backend'i Başlatın

```powershell
./START_BACKEND.ps1
```

### 2. Logları Kontrol Edin

Başlangıçta şunu görmeli:
```
Started BackendApplication in X.XXX seconds
```

### 3. Test Email Gönderin

1. Frontend'de alarm oluşturun
2. "🧪 Test Et" butonuna basın
3. Backend loglarında arayın:

```
🔔 PRICE ALERT: ...
Found email in JWT token: ...
✅ Email notification sent to ...
```

### 4. Email Kutunuzu Kontrol Edin

- Inbox'ı kontrol edin
- Spam klasörünü kontrol edin
- 1-2 dakika bekleyin

## Başarı Kriterleri

✅ 2-Step Verification aktif  
✅ App Password oluşturuldu (16 karakter)  
✅ .env.local dosyasına yazıldı (boşluksuz)  
✅ Backend başladı  
✅ Test email gönderildi  
✅ Email geldi!  

## Güvenlik Notları

⚠️ **App Password'ü kimseyle paylaşmayın!**
⚠️ **.env.local dosyasını git'e commit etmeyin!**
⚠️ **Production'da environment variable kullanın!**

## Yardım

Hala sorun mu yaşıyorsunuz?

1. Backend loglarını kontrol edin
2. Gmail hesabının aktif olduğunu doğrulayın
3. Farklı bir Gmail hesabı deneyin
4. Outlook kullanmayı deneyin

Başarılar! 🎉

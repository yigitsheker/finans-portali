# Kullanıcı Bazlı SMTP Ayarları Tasarımı

## Konsept

Her kullanıcı kendi email hesabını kullanarak bildirim alır.

## Database Şeması

```sql
CREATE TABLE user_email_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    smtp_host VARCHAR(255) DEFAULT 'smtp.gmail.com',
    smtp_port INTEGER DEFAULT 587,
    smtp_username VARCHAR(255) NOT NULL,
    smtp_password VARCHAR(500) NOT NULL, -- Encrypted!
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

## Frontend: Email Ayarları Sayfası

```typescript
// EmailSettings.tsx
export default function EmailSettings() {
    const [settings, setSettings] = useState({
        email: '',
        smtpUsername: '',
        smtpPassword: '',
        smtpHost: 'smtp.gmail.com',
        smtpPort: 587
    });

    return (
        <div>
            <h2>Email Bildirimleri Ayarları</h2>
            
            <input 
                type="email" 
                placeholder="Email adresiniz"
                value={settings.email}
            />
            
            <input 
                type="text" 
                placeholder="SMTP Kullanıcı Adı (genelde email)"
                value={settings.smtpUsername}
            />
            
            <input 
                type="password" 
                placeholder="Gmail App Password"
                value={settings.smtpPassword}
            />
            
            <button onClick={saveSettings}>Kaydet</button>
            <button onClick={testEmail}>Test Email Gönder</button>
        </div>
    );
}
```

## Backend: Kullanıcı Bazlı Email Gönderimi

```java
@Service
public class UserEmailService {
    
    public void sendEmailWithUserSettings(String userId, String subject, String body) {
        // Kullanıcının SMTP ayarlarını al
        UserEmailSettings settings = userEmailSettingsRepo.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("Email ayarları bulunamadı"));
        
        // Kullanıcının kendi SMTP ayarları ile email gönder
        JavaMailSender mailSender = createMailSender(settings);
        
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom(settings.getSmtpUsername());
        helper.setTo(settings.getEmail());
        helper.setSubject(subject);
        helper.setText(body, true);
        
        mailSender.send(message);
    }
    
    private JavaMailSender createMailSender(UserEmailSettings settings) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(settings.getSmtpHost());
        mailSender.setPort(settings.getSmtpPort());
        mailSender.setUsername(settings.getSmtpUsername());
        mailSender.setPassword(decryptPassword(settings.getSmtpPassword()));
        
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        
        return mailSender;
    }
}
```

## Avantajlar

✅ Her kullanıcı kendi email hesabını kullanır
✅ Sistem email hesabına gerek yok
✅ Kullanıcı istediği email sağlayıcısını kullanabilir
✅ Email quota problemi yok

## Dezavantajlar

❌ Kullanıcı SMTP ayarlarını yapmalı (karmaşık)
❌ Gmail App Password oluşturmalı
❌ Şifre güvenliği riski (database'de saklanıyor)
❌ Her kullanıcı için ayrı SMTP connection

## Güvenlik

**Şifre Encryption:**
```java
@Service
public class EncryptionService {
    
    @Value("${encryption.secret-key}")
    private String secretKey;
    
    public String encrypt(String password) {
        // AES encryption
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] encrypted = cipher.doFinal(password.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }
    
    public String decrypt(String encryptedPassword) {
        // AES decryption
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getKey());
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword));
        return new String(decrypted);
    }
}
```

## Alternatif: Sistem Email Hesabı (Önerilen - Daha Basit)

```yaml
# application.yml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: noreply@finansportali.com
    password: ${MAIL_PASSWORD}
```

**Avantajlar:**
✅ Kullanıcı hiçbir şey yapmasına gerek yok
✅ Daha güvenli (tek merkezi şifre)
✅ Daha basit
✅ Professional görünüm (noreply@finansportali.com)

**Dezavantajlar:**
❌ Sistem email hesabı gerekli
❌ Email quota limiti

## Önerilen Çözüm

**Development/Test için:**
- Environment variable ile sistem email kullan
- Basit ve hızlı test

**Production için:**
- Professional email servisi kullan (SendGrid, AWS SES, Mailgun)
- Kullanıcı hiçbir şey yapmasına gerek yok
- Güvenilir ve ölçeklenebilir

## SendGrid Örneği (Production)

```yaml
spring:
  mail:
    host: smtp.sendgrid.net
    port: 587
    username: apikey
    password: ${SENDGRID_API_KEY}
```

**Avantajlar:**
✅ Günde 100 email ücretsiz
✅ Kullanıcı ayar yapmasına gerek yok
✅ Yüksek deliverability
✅ Email analytics
✅ Spam koruması

## Sonuç

**Test için:** Environment variable yeterli
**Production için:** SendGrid/AWS SES kullan
**Kullanıcı bazlı SMTP:** Karmaşık ve gereksiz

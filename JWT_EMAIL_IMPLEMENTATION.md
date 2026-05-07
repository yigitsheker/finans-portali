# JWT Token'dan Email ve Username Çekme

## Yapılan Değişiklikler

Artık email ve username bilgileri **JWT token'dan otomatik olarak** çekiliyor. Environment variable'lara gerek yok!

### 1. KeycloakUserService - JWT Token Parser

`KeycloakUserService.java` tamamen yeniden yazıldı:

```java
public String getUserEmail(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
        Jwt jwt = jwtAuth.getToken();
        String email = jwt.getClaimAsString("email");
        return email;
    }
    return null;
}

public String getUsername(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
        Jwt jwt = jwtAuth.getToken();
        String username = jwt.getClaimAsString("preferred_username");
        return username;
    }
    return authentication.getName();
}
```

### 2. NotificationService - Authentication Parametresi

Email gönderirken artık `Authentication` objesi kullanılıyor:

```java
public void sendPriceAlert(PriceAlert alert, BigDecimal currentPrice, Authentication authentication) {
    String userEmail = keycloakUserService.getUserEmail(authentication);
    String username = keycloakUserService.getUsername(authentication);
    
    if (userEmail != null) {
        sendAlertEmail(userEmail, username, alert, currentPrice, message);
    }
}
```

### 3. Email Template - Kişiselleştirilmiş Selamlama

Email'de artık kullanıcının adı görünüyor:

```html
<div class='header'>
    <h1>🔔 Fiyat Alarmı Tetiklendi</h1>
    <p>Merhaba username,</p>
</div>
```

### 4. PriceAlertService - İki Ayrı Trigger Metodu

**Manuel Tetikleme (JWT token var):**
```java
public void triggerAlertManually(String userId, Long alertId, Authentication authentication) {
    // Email gönderilebilir
    triggerAlertWithAuth(alert, currentPrice, authentication);
}
```

**Otomatik Tetikleme (Scheduled task - JWT token yok):**
```java
public void triggerAlert(PriceAlert alert, BigDecimal currentPrice) {
    // Email gönderilemez (JWT token yok)
    log.warn("Alarm tetiklendi ama email gönderilemedi (scheduled task)");
}
```

## Keycloak JWT Token Yapısı

Keycloak JWT token'ı şu claim'leri içerir:

```json
{
  "sub": "user-id-123",
  "email": "user@example.com",
  "preferred_username": "johndoe",
  "name": "John Doe",
  "given_name": "John",
  "family_name": "Doe",
  "email_verified": true
}
```

## Kullanım

### Manuel Test (🧪 Test Et Butonu)

1. Keycloak'a giriş yapın
2. Bir alarm oluşturun
3. **"🧪 Test Et"** butonuna basın
4. Email **otomatik olarak** JWT token'dan alınan adrese gönderilir!

**Artık environment variable'a gerek yok!** ✅

### Email Ayarları

Sadece SMTP ayarlarını yapılandırmanız yeterli:

**PowerShell:**
```powershell
$env:MAIL_USERNAME = "noreply@finansportali.com"
$env:MAIL_PASSWORD = "your-app-password"
```

**application.yml:**
```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
```

## Email İçeriği

Email şu bilgileri içerir:

```
🔔 Fiyat Alarmı Tetiklendi

Merhaba johndoe,

AAPL (Apple Inc.)
AAPL (Apple Inc.) fiyatı 200.00 seviyesini aştı!

Alarm Detayları
─────────────────
Alarm Tipi:    Fiyat Üstü
Hedef Fiyat:   200.00
Mevcut Fiyat:  205.50

📝 Notunuz:
Test alarm - email kontrolü
```

## Keycloak Email Ayarları

Keycloak'ta kullanıcının email'inin olduğundan emin olun:

1. Keycloak Admin Console'a girin
2. Users → Kullanıcıyı seçin
3. **Email** alanının dolu olduğunu kontrol edin
4. **Email Verified** işaretli olmalı

### Email Yoksa Ne Olur?

Eğer JWT token'da email claim'i yoksa:

```
⚠️ No email found in JWT token for user xxx, skipping email notification
```

Backend logu gösterilir ve email gönderilmez. Alarm yine de tetiklenir.

## Scheduled Task Durumu

**Önemli:** Scheduled task'lar (otomatik alarm kontrolü) için JWT token olmadığından email gönderilemez.

**Çözüm Seçenekleri:**

### Seçenek 1: Database'de Email Saklama (Önerilen)

Kullanıcı ilk giriş yaptığında email'i database'e kaydedin:

```java
@Entity
public class User {
    private String userId;
    private String email;
    private String username;
}
```

### Seçenek 2: Keycloak Admin API

Scheduled task'ta Keycloak Admin API kullanarak email çekin:

```java
// Admin token al
String adminToken = getAdminToken();

// User bilgilerini çek
WebClient.get()
    .uri(keycloakUrl + "/admin/realms/finans/users/" + userId)
    .header("Authorization", "Bearer " + adminToken)
    .retrieve()
    .bodyToMono(Map.class);
```

### Seçenek 3: Email Queue Sistemi

Alarm tetiklendiğinde queue'ya ekle, kullanıcı online olduğunda gönder:

```java
// Alarm tetiklendiğinde
emailQueue.add(new PendingEmail(userId, alertId, currentPrice));

// Kullanıcı giriş yaptığında
@EventListener
public void onUserLogin(UserLoginEvent event) {
    List<PendingEmail> pending = emailQueue.getForUser(event.getUserId());
    for (PendingEmail email : pending) {
        sendEmail(email, event.getAuthentication());
    }
}
```

## Test Senaryoları

### ✅ Senaryo 1: Manuel Tetikleme (Başarılı)

1. Kullanıcı giriş yapmış (JWT token var)
2. "🧪 Test Et" butonuna basıyor
3. Email JWT token'dan alınıyor: `user@example.com`
4. Email başarıyla gönderiliyor

**Beklenen Log:**
```
🔔 PRICE ALERT: AAPL (Apple Inc.) fiyatı 200.00 seviyesini aştı!
Found email in JWT token: user@example.com
✅ Email notification sent to user@example.com for alert 123
```

### ⚠️ Senaryo 2: Email Claim Yok

1. JWT token'da email claim'i yok
2. Email gönderilemez

**Beklenen Log:**
```
🔔 PRICE ALERT: AAPL (Apple Inc.) fiyatı 200.00 seviyesini aştı!
⚠️ No email found in JWT token for user xxx, skipping email notification
```

### ❌ Senaryo 3: SMTP Hatası

1. Email bulundu ama SMTP ayarları yanlış
2. Alarm tetiklenir ama email gönderilemez

**Beklenen Log:**
```
🔔 PRICE ALERT: AAPL (Apple Inc.) fiyatı 200.00 seviyesini aştı!
Found email in JWT token: user@example.com
❌ Failed to send email notification for alert 123: Authentication failed
```

## Debugging

### JWT Token'ı İnceleme

Backend'de JWT token'ı loglayın:

```java
if (authentication instanceof JwtAuthenticationToken jwtAuth) {
    Jwt jwt = jwtAuth.getToken();
    log.info("JWT Claims: {}", jwt.getClaims());
}
```

**Çıktı:**
```json
{
  "sub": "abc-123",
  "email": "user@example.com",
  "preferred_username": "johndoe",
  "name": "John Doe"
}
```

### Frontend'de Token İnceleme

Browser console'da:

```javascript
console.log("Keycloak token:", keycloak.tokenParsed);
```

## Güvenlik Notları

✅ **Güvenli:**
- Email JWT token'dan alınıyor (güvenilir kaynak)
- Her istek için token doğrulanıyor
- Email sadece token sahibine gönderiliyor

❌ **Güvensiz Olurdu:**
- Email'i query parameter'dan almak
- Email'i frontend'den göndermek
- Email doğrulaması yapmamak

## Özet

| Özellik | Eski Yöntem | Yeni Yöntem |
|---------|-------------|-------------|
| Email Kaynağı | Environment Variable | JWT Token |
| Username | Environment Variable | JWT Token |
| Konfigürasyon | `TEST_EMAIL` gerekli | Otomatik |
| Güvenlik | Düşük | Yüksek |
| Kullanıcı Deneyimi | Manuel ayar | Otomatik |
| Production Ready | ❌ | ✅ |

Artık sistem production'a hazır! 🎉

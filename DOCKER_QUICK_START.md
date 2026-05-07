# Docker Hızlı Başlangıç

## 🚀 Tek Komutla Başlat

```powershell
.\start-docker.ps1
```

Bu script otomatik olarak:
- ✅ Docker'ın çalıştığını kontrol eder
- ✅ .env dosyasını oluşturur
- ✅ Docker image'larını build eder
- ✅ Container'ları başlatır
- ✅ Servislerin sağlıklı olmasını bekler
- ✅ Erişim URL'lerini gösterir

## 📋 Gereksinimler

- Docker Desktop (Windows/Mac) veya Docker Engine (Linux)
- En az 4GB RAM
- En az 10GB disk alanı

## 🎯 Hızlı Komutlar

### Başlatma
```bash
docker-compose up -d
```

### Durdurma
```bash
docker-compose down
```

### Logları Görüntüleme
```bash
docker-compose logs -f
```

### Yeniden Başlatma
```bash
docker-compose restart
```

## 🌐 Erişim URL'leri

| Servis | URL | Kimlik Bilgileri |
|--------|-----|------------------|
| Frontend | http://localhost | - |
| Backend API | http://localhost:8080 | - |
| Keycloak Admin | http://localhost:8090 | admin/admin |
| PostgreSQL | localhost:5432 | finans_user/finans_password |

## ⚙️ Keycloak Konfigürasyonu

### 1. Realm Oluştur
1. http://localhost:8090 → admin/admin ile giriş
2. "Create Realm" → Name: `finans-realm`

### 2. Client Oluştur
1. Clients → "Create client"
2. Client ID: `finans-frontend`
3. Client authentication: OFF
4. Valid redirect URIs: `http://localhost/*`
5. Web origins: `http://localhost`

### 3. Test Kullanıcısı Oluştur
1. Users → "Add user"
2. Username: `testuser`
3. Email: `test@example.com`
4. Credentials → Password: `test123` (Temporary: OFF)

## 🔧 Makefile Komutları

```bash
make help          # Tüm komutları göster
make build         # Image'ları build et
make up            # Servisleri başlat
make down          # Servisleri durdur
make logs          # Logları göster
make ps            # Container durumunu göster
make health        # Health check yap
make backup        # Database backup al
make clean         # Tümünü temizle
```

## 📊 Container Durumu

```bash
# Tüm container'ları göster
docker-compose ps

# Resource kullanımı
docker stats

# Health check
curl http://localhost:8080/actuator/health
```

## 🐛 Sorun Giderme

### Container başlamıyor
```bash
docker-compose logs backend
docker-compose restart backend
```

### Port çakışması
`docker-compose.yml` dosyasında portları değiştir:
```yaml
ports:
  - "8081:8080"  # Backend için farklı port
```

### Database bağlantı hatası
```bash
docker-compose logs postgres
docker-compose exec postgres psql -U finans_user -d finans_db
```

## 📧 Email Konfigürasyonu (Opsiyonel)

`.env` dosyasında:
```env
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
```

Gmail App Password: [GMAIL_APP_PASSWORD_GUIDE.md](GMAIL_APP_PASSWORD_GUIDE.md)

## 🔒 Production Deployment

```bash
# Production ortamı için
docker-compose -f docker-compose.prod.yml up -d

# Veya Makefile ile
make prod-up
```

## 📚 Detaylı Dokümantasyon

Daha fazla bilgi için: [DOCKER_SETUP.md](DOCKER_SETUP.md)

## 🆘 Yardım

Sorun yaşarsanız:
1. `docker-compose logs -f` ile logları kontrol edin
2. `docker-compose ps` ile durumu kontrol edin
3. GitHub Issues'da sorun açın

## ✅ Başarılı Kurulum Kontrolü

Tüm servisler çalışıyorsa:
- ✅ Frontend: http://localhost açılıyor
- ✅ Backend: http://localhost:8080/actuator/health "UP" dönüyor
- ✅ Keycloak: http://localhost:8090 açılıyor
- ✅ PostgreSQL: `docker-compose exec postgres psql -U finans_user -d finans_db` çalışıyor

Tebrikler! 🎉 Finans Portali Docker ile çalışıyor!

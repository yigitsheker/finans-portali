# Docker Setup Guide

Bu dokümantasyon Finans Portali projesini Docker ile çalıştırmak için gerekli adımları içerir.

## Gereksinimler

- Docker Desktop (Windows/Mac) veya Docker Engine (Linux)
- Docker Compose v2.0+
- En az 4GB RAM
- En az 10GB disk alanı

## Hızlı Başlangıç

### 1. Projeyi Klonlayın

```bash
git clone https://github.com/yigitsheker/finans-portali.git
cd finans-portali
```

### 2. Environment Dosyasını Oluşturun

```bash
cp .env.example .env
```

`.env` dosyasını düzenleyin ve gerekli değişkenleri ayarlayın (özellikle email ayarları opsiyoneldir).

### 3. Docker Container'ları Başlatın

```bash
docker-compose up -d
```

Bu komut şu servisleri başlatır:
- PostgreSQL (Port 5432)
- Keycloak (Port 8090)
- Backend API (Port 8080)
- Frontend (Port 80)

### 4. Servislerin Durumunu Kontrol Edin

```bash
docker-compose ps
```

Tüm servisler "healthy" durumunda olmalı.

### 5. Uygulamaya Erişin

- **Frontend**: http://localhost
- **Backend API**: http://localhost:8080
- **Keycloak Admin**: http://localhost:8090
  - Username: `admin`
  - Password: `admin`

## Keycloak Konfigürasyonu

İlk çalıştırmada Keycloak'ı yapılandırmanız gerekir:

### 1. Keycloak Admin Console'a Giriş Yapın

http://localhost:8090 adresine gidin ve admin/admin ile giriş yapın.

### 2. Realm Oluşturun

1. Sol üst köşeden "Create Realm" butonuna tıklayın
2. Realm name: `finans-realm`
3. "Create" butonuna tıklayın

### 3. Client Oluşturun

1. Sol menüden "Clients" seçin
2. "Create client" butonuna tıklayın
3. Client ID: `finans-frontend`
4. Client Protocol: `openid-connect`
5. "Next" butonuna tıklayın
6. Client authentication: `OFF` (public client)
7. Authorization: `OFF`
8. Authentication flow:
   - ✅ Standard flow
   - ✅ Direct access grants
9. "Next" butonuna tıklayın
10. Valid redirect URIs: `http://localhost/*`
11. Web origins: `http://localhost`
12. "Save" butonuna tıklayın

### 4. Test Kullanıcısı Oluşturun

1. Sol menüden "Users" seçin
2. "Add user" butonuna tıklayın
3. Username: `testuser`
4. Email: `test@example.com`
5. First name: `Test`
6. Last name: `User`
7. Email verified: `ON`
8. "Create" butonuna tıklayın
9. "Credentials" tab'ına gidin
10. "Set password" butonuna tıklayın
11. Password: `test123`
12. Temporary: `OFF`
13. "Save" butonuna tıklayın

## Docker Komutları

### Container'ları Başlatma

```bash
# Tüm servisleri başlat
docker-compose up -d

# Sadece belirli bir servisi başlat
docker-compose up -d backend
```

### Container'ları Durdurma

```bash
# Tüm servisleri durdur
docker-compose down

# Volumeleri de sil (tüm verileri temizler)
docker-compose down -v
```

### Logları Görüntüleme

```bash
# Tüm servislerin logları
docker-compose logs -f

# Sadece backend logları
docker-compose logs -f backend

# Son 100 satır
docker-compose logs --tail=100 backend
```

### Container'a Bağlanma

```bash
# Backend container'ına bash ile bağlan
docker-compose exec backend bash

# PostgreSQL'e bağlan
docker-compose exec postgres psql -U finans_user -d finans_db
```

### Yeniden Build Etme

```bash
# Tüm image'ları yeniden build et
docker-compose build

# Sadece backend'i yeniden build et
docker-compose build backend

# Build et ve başlat
docker-compose up -d --build
```

### Container'ları Yeniden Başlatma

```bash
# Tüm servisleri yeniden başlat
docker-compose restart

# Sadece backend'i yeniden başlat
docker-compose restart backend
```

## Volume Yönetimi

### Volume'ları Listeleme

```bash
docker volume ls | grep finans
```

### Volume'ları Temizleme

```bash
# Tüm volume'ları sil (DİKKAT: Tüm veriler silinir!)
docker-compose down -v

# Sadece belirli bir volume'u sil
docker volume rm finans-portali_postgres_data
```

### Backup Alma

```bash
# PostgreSQL backup
docker-compose exec postgres pg_dump -U finans_user finans_db > backup.sql

# Restore
docker-compose exec -T postgres psql -U finans_user finans_db < backup.sql
```

## Troubleshooting

### Container Başlamıyor

```bash
# Container loglarını kontrol et
docker-compose logs backend

# Container durumunu kontrol et
docker-compose ps

# Health check durumunu kontrol et
docker inspect finans-backend | grep -A 10 Health
```

### Port Çakışması

Eğer portlar kullanımdaysa, `docker-compose.yml` dosyasında port mapping'leri değiştirin:

```yaml
ports:
  - "8081:8080"  # Backend için farklı port
```

### Database Bağlantı Hatası

```bash
# PostgreSQL container'ının çalıştığını kontrol et
docker-compose ps postgres

# PostgreSQL loglarını kontrol et
docker-compose logs postgres

# Database'e manuel bağlan
docker-compose exec postgres psql -U finans_user -d finans_db
```

### Keycloak Bağlantı Hatası

```bash
# Keycloak loglarını kontrol et
docker-compose logs keycloak

# Keycloak health check
curl http://localhost:8090/health/ready
```

### Frontend Build Hatası

```bash
# Frontend loglarını kontrol et
docker-compose logs frontend

# Frontend'i yeniden build et
docker-compose build --no-cache frontend
docker-compose up -d frontend
```

## Production Deployment

Production ortamı için:

1. `.env` dosyasında güçlü şifreler kullanın
2. `docker-compose.prod.yml` dosyası oluşturun
3. SSL/TLS sertifikaları ekleyin
4. Nginx'e reverse proxy ekleyin
5. Resource limits tanımlayın
6. Monitoring ve logging ekleyin

### Production Docker Compose Örneği

```yaml
version: '3.8'

services:
  backend:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
```

## Email Konfigürasyonu

Email bildirimleri için `.env` dosyasında şu değişkenleri ayarlayın:

```env
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
```

Gmail için App Password oluşturma: [GMAIL_APP_PASSWORD_GUIDE.md](GMAIL_APP_PASSWORD_GUIDE.md)

## Performans Optimizasyonu

### Resource Limits

```yaml
services:
  backend:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
```

### Caching

Backend'de Redis cache eklemek için:

```yaml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

## Monitoring

### Health Checks

```bash
# Backend health
curl http://localhost:8080/actuator/health

# Frontend health
curl http://localhost:80

# Keycloak health
curl http://localhost:8090/health/ready
```

### Resource Usage

```bash
# Container resource kullanımı
docker stats

# Disk kullanımı
docker system df
```

## Güvenlik

### Best Practices

1. ✅ Non-root user kullanın (Dockerfile'larda yapıldı)
2. ✅ Health checks ekleyin (Tüm servislerde var)
3. ✅ Resource limits tanımlayın
4. ✅ Secrets için environment variables kullanın
5. ✅ Multi-stage build kullanın (Dockerfile'larda yapıldı)
6. ✅ .dockerignore dosyaları ekleyin
7. ⚠️ Production'da güçlü şifreler kullanın
8. ⚠️ SSL/TLS sertifikaları ekleyin

## Destek

Sorun yaşarsanız:

1. Logları kontrol edin: `docker-compose logs -f`
2. Container durumunu kontrol edin: `docker-compose ps`
3. Health check'leri kontrol edin
4. GitHub Issues'da sorun açın

## Lisans

Bu proje MIT lisansı altında lisanslanmıştır.

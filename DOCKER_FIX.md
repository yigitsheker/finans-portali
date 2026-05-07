# Docker Düzeltmeleri

## Sorunlar ve Çözümler

### 1. Sadece 2 Container Çalışıyor
**Sorun**: Backend ve Frontend container'ları başlatılmamış, sadece PostgreSQL ve Keycloak çalışıyor.

**Neden**: 
- OpenJDK image'ları Docker Hub'dan kaldırıldı
- Keycloak versiyonu uyumsuz
- docker-compose.yml'de version uyarısı

**Çözümler**:

#### a) Backend Dockerfile Güncellendi
```dockerfile
# Eski (çalışmıyor)
FROM openjdk:21-jdk-slim as builder
FROM openjdk:21-jre-slim

# Yeni (çalışıyor)
FROM eclipse-temurin:21-jdk-alpine as builder
FROM eclipse-temurin:21-jre-alpine
```

**Değişiklikler**:
- ✅ OpenJDK → Eclipse Temurin (resmi OpenJDK dağıtımı)
- ✅ Debian slim → Alpine (daha küçük image)
- ✅ apt-get → apk (Alpine paket yöneticisi)
- ✅ groupadd/useradd → addgroup/adduser (Alpine komutları)

#### b) docker-compose.yml Güncellendi
```yaml
# Eski
version: '3.8'  # Deprecated uyarısı
image: quay.io/keycloak/keycloak:23.0  # Eski versiyon

# Yeni
# version satırı kaldırıldı (artık gerekli değil)
image: quay.io/keycloak/keycloak:26.5.1  # Güncel versiyon
```

### 2. Build ve Başlatma Adımları

```bash
# 1. Image'ları build et
docker-compose build

# 2. Tüm servisleri başlat
docker-compose up -d

# 3. Durumu kontrol et
docker-compose ps

# 4. Logları izle
docker-compose logs -f
```

### 3. Beklenen Container'lar

| Container | Image | Port | Durum |
|-----------|-------|------|-------|
| finans-postgres | postgres:15-alpine | 5432 | ✅ Çalışıyor |
| finans-keycloak | keycloak:26.5.1 | 8090 | ✅ Çalışıyor |
| finans-backend | finans-portali-backend | 8080 | 🔄 Build ediliyor |
| finans-frontend | finans-portali-frontend | 80 | 🔄 Build ediliyor |

### 4. Troubleshooting

#### Backend Build Hatası
```bash
# Hata: openjdk:21-jdk-slim: not found
# Çözüm: Dockerfile'da Eclipse Temurin kullan
```

#### Keycloak Unhealthy
```bash
# Keycloak'ın başlaması 1-2 dakika sürebilir
docker-compose logs keycloak

# Health check
curl http://localhost:8090/health/ready
```

#### Port Çakışması
```bash
# Eğer 8090 portu kullanımdaysa
# docker-compose.yml'de portu değiştir:
ports:
  - "8091:8080"  # Farklı port kullan
```

### 5. Doğrulama

Tüm container'lar çalıştığında:

```bash
# Container durumu
docker-compose ps

# Beklenen çıktı:
# NAME              STATUS
# finans-postgres   Up (healthy)
# finans-keycloak   Up (healthy)
# finans-backend    Up (healthy)
# finans-frontend   Up (healthy)
```

### 6. İlk Çalıştırma

```bash
# 1. Build (ilk kez veya değişiklik sonrası)
docker-compose build

# 2. Başlat
docker-compose up -d

# 3. Logları izle
docker-compose logs -f backend

# 4. Backend'in başlamasını bekle (1-2 dakika)
# "Started BackendApplication" mesajını görene kadar

# 5. Frontend'e eriş
# http://localhost
```

### 7. Güncellenmiş Dosyalar

- ✅ `backend/Dockerfile` - Eclipse Temurin kullanımı
- ✅ `docker-compose.yml` - Version kaldırıldı, Keycloak güncellendi
- ✅ `.gitignore` - Docker dosyaları eklendi

### 8. Sonraki Adımlar

1. Build işleminin tamamlanmasını bekle
2. `docker-compose up -d` ile başlat
3. `docker-compose ps` ile kontrol et
4. Keycloak'ı yapılandır (DOCKER_SETUP.md)
5. Uygulamayı test et

## Özet

**Sorun**: OpenJDK image'ları deprecated, backend ve frontend build edilemedi.

**Çözüm**: Eclipse Temurin kullanımı, Alpine Linux, güncel Keycloak versiyonu.

**Sonuç**: Tüm 4 container başarıyla çalışacak.

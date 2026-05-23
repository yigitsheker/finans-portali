#!/bin/bash

# Finans Portali - Başlatma Scripti
# Bu script tüm servisleri Docker Compose ile başlatır

set -e

echo "🚀 Finans Portali başlatılıyor..."

# Renk kodları
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Fonksiyonlar
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Docker kontrolü
if ! command -v docker &> /dev/null; then
    log_error "Docker yüklü değil. Lütfen Docker'ı yükleyin."
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    log_error "Docker Compose yüklü değil. Lütfen Docker Compose'u yükleyin."
    exit 1
fi

# Önceki container'ları temizle
log_info "Önceki container'lar temizleniyor..."
docker-compose down --remove-orphans

# Volume'ları temizle (opsiyonel)
if [ "$1" = "--clean" ]; then
    log_warning "Tüm veriler silinecek!"
    read -p "Devam etmek istediğinizden emin misiniz? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker-compose down -v
        docker system prune -f
        log_success "Veriler temizlendi."
    else
        log_info "Temizleme iptal edildi."
    fi
fi

# Servisleri başlat
log_info "Servisler başlatılıyor..."

# Core services first
log_info "Core servisler başlatılıyor (PostgreSQL, Keycloak)..."
docker-compose up -d postgres keycloak

# Wait for core services
log_info "Core servislerin başlaması bekleniyor..."
sleep 15

# Application services
log_info "Uygulama servisleri başlatılıyor (Backend, Frontend)..."
docker-compose up -d backend frontend redis

# Wait for application services
log_info "Uygulama servislerinin başlaması bekleniyor..."
sleep 10

# Monitoring services
log_info "Monitoring servisleri başlatılıyor..."
docker-compose up -d prometheus grafana jaeger opensearch opensearch-dashboards logstash filebeat

# Wait for monitoring services
log_info "Monitoring servislerinin başlaması bekleniyor..."
sleep 15

# Health check
log_info "Servis durumları kontrol ediliyor..."

services=("postgres" "keycloak" "backend" "frontend" "redis" "prometheus" "grafana" "jaeger" "opensearch")
for service in "${services[@]}"; do
    if docker-compose ps | grep -q "$service.*Up"; then
        log_success "$service servisi çalışıyor"
    else
        log_error "$service servisi başlatılamadı"
        docker-compose logs "$service" | tail -20
    fi
done

echo
log_success "🎉 Finans Portali başarıyla başlatıldı!"
echo
echo "📱 Erişim Adresleri:"
echo "   Frontend:  http://localhost"
echo "   Backend:   http://localhost:8080"
echo "   Keycloak:  http://localhost:8081"
echo "   Swagger:   http://localhost:8080/swagger-ui.html"
echo
echo "📊 Monitoring:"
echo "   Grafana:   http://localhost:3000 (admin/admin)"
echo "   Prometheus: http://localhost:9090"
echo "   Jaeger:    http://localhost:16686"
echo "   OpenSearch: http://localhost:5601"
echo
echo "🔍 Health Checks:"
echo "   Health:    http://localhost:8080/actuator/health"
echo "   Metrics:   http://localhost:8080/actuator/metrics"
echo
echo "🔧 Yönetim:"
echo "   Logları görmek için: docker-compose logs -f [service-name]"
echo "   Durdurmak için:      docker-compose down"
echo "   Yeniden başlatmak:   ./start.sh"
echo
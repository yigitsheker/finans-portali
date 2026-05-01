@echo off
REM Finans Portali - Windows Başlatma Scripti

echo 🚀 Finans Portali başlatılıyor...

REM Docker kontrolü
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker yüklü değil. Lütfen Docker Desktop'ı yükleyin.
    pause
    exit /b 1
)

docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Compose yüklü değil. Lütfen Docker Compose'u yükleyin.
    pause
    exit /b 1
)

REM Önceki container'ları temizle
echo [INFO] Önceki container'lar temizleniyor...
docker-compose down --remove-orphans

REM Servisleri başlat
echo [INFO] Core servisler başlatılıyor...
docker-compose up -d postgres keycloak

echo [INFO] Core servislerin başlaması bekleniyor...
timeout /t 15 /nobreak >nul

echo [INFO] Uygulama servisleri başlatılıyor...
docker-compose up -d backend frontend redis

echo [INFO] Uygulama servislerinin başlaması bekleniyor...
timeout /t 10 /nobreak >nul

echo [INFO] Monitoring servisleri başlatılıyor...
docker-compose up -d prometheus grafana jaeger opensearch opensearch-dashboards logstash filebeat

echo [INFO] Monitoring servislerinin başlaması bekleniyor...
timeout /t 15 /nobreak >nul

REM Başarı mesajı
echo.
echo 🎉 Finans Portali başarıyla başlatıldı!
echo.
echo 📱 Erişim Adresleri:
echo    Frontend:  http://localhost
echo    Backend:   http://localhost:8080
echo    Keycloak:  http://localhost:8081
echo    Swagger:   http://localhost:8080/swagger-ui.html
echo.
echo 📊 Monitoring:
echo    Grafana:   http://localhost:3000 (admin/admin)
echo    Prometheus: http://localhost:9090
echo    Jaeger:    http://localhost:16686
echo    OpenSearch: http://localhost:5601
echo.
echo 🔍 Health Checks:
echo    Health:    http://localhost:8080/actuator/health
echo    Metrics:   http://localhost:8080/actuator/metrics
echo.
echo 🔧 Yönetim:
echo    Logları görmek için: docker-compose logs -f [service-name]
echo    Durdurmak için:      docker-compose down
echo    Yeniden başlatmak:   start.bat
echo.
pause
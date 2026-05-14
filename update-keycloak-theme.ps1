# Keycloak Tema Güncelleme Script'i
# Bu script Keycloak container'ını yeniden başlatır ve realm'i günceller

Write-Host "🎨 Keycloak Tema Güncelleme Başlatılıyor..." -ForegroundColor Green
Write-Host ""

# 1. Keycloak'ı durdur
Write-Host "1️⃣ Keycloak container'ı durduruluyor..." -ForegroundColor Yellow
docker-compose stop keycloak
Start-Sleep -Seconds 2

# 2. Keycloak'ı başlat
Write-Host "2️⃣ Keycloak container'ı başlatılıyor..." -ForegroundColor Yellow
docker-compose start keycloak
Start-Sleep -Seconds 5

# 3. Keycloak'ın başlamasını bekle
Write-Host "3️⃣ Keycloak'ın başlaması bekleniyor (30 saniye)..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# 4. Tema dosyalarını kontrol et
Write-Host "4️⃣ Tema dosyaları kontrol ediliyor..." -ForegroundColor Yellow
docker exec finans-keycloak ls -la /opt/keycloak/themes/finance-theme/login/

Write-Host ""
Write-Host "✅ Keycloak yeniden başlatıldı!" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Sonraki Adımlar:" -ForegroundColor Cyan
Write-Host "1. Tarayıcınızda http://localhost:8090 adresine gidin" -ForegroundColor White
Write-Host "2. Admin Console'a giriş yapın (admin/admin)" -ForegroundColor White
Write-Host "3. 'finans' realm'ini seçin" -ForegroundColor White
Write-Host "4. Realm Settings → Themes sekmesine gidin" -ForegroundColor White
Write-Host "5. Login Theme: 'finance-theme' seçin" -ForegroundColor White
Write-Host "6. Save butonuna tıklayın" -ForegroundColor White
Write-Host ""
Write-Host "🎉 Ardından http://localhost adresinden Login'e tıklayarak yeni temayı görebilirsiniz!" -ForegroundColor Green

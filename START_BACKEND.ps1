# Finans Portalı Backend Başlatma Scripti
# Email ayarları ile backend'i başlatır

Write-Host "=== Finans Portalı Backend Başlatılıyor ===" -ForegroundColor Cyan
Write-Host ""

# .env.local dosyasından ayarları oku
$envFile = "backend\.env.local"

if (Test-Path $envFile) {
    Write-Host "✅ Email ayarları yükleniyor: $envFile" -ForegroundColor Green
    
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^([^#][^=]+)=(.+)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            Set-Item -Path "env:$name" -Value $value
            
            if ($name -eq "MAIL_PASSWORD") {
                Write-Host "  $name = ***" -ForegroundColor Gray
            } else {
                Write-Host "  $name = $value" -ForegroundColor Gray
            }
        }
    }
    Write-Host ""
} else {
    Write-Host "⚠️  .env.local dosyası bulunamadı!" -ForegroundColor Yellow
    Write-Host "   backend\.env.local dosyasını oluşturun" -ForegroundColor Yellow
    Write-Host ""
}

# Gmail App Password kontrolü
if ($env:MAIL_PASSWORD -and $env:MAIL_PASSWORD.Length -lt 16) {
    Write-Host "⚠️  UYARI: Gmail normal şifre ile çalışmaz!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Gmail App Password oluşturmak için:" -ForegroundColor Cyan
    Write-Host "1. https://myaccount.google.com/security" -ForegroundColor White
    Write-Host "2. '2-Step Verification' aktif edin" -ForegroundColor White
    Write-Host "3. https://myaccount.google.com/apppasswords" -ForegroundColor White
    Write-Host "4. 'Mail' seçin, 16 karakterlik şifreyi alın" -ForegroundColor White
    Write-Host "5. backend\.env.local dosyasına yazın (boşluksuz)" -ForegroundColor White
    Write-Host ""
    
    $continue = Read-Host "Devam etmek istiyor musunuz? (y/n)"
    if ($continue -ne "y") {
        exit
    }
}

Write-Host "Backend başlatılıyor..." -ForegroundColor Yellow
Write-Host ""
Write-Host "Loglarda arayın:" -ForegroundColor Cyan
Write-Host "  🔔 PRICE ALERT: ..." -ForegroundColor White
Write-Host "  Found email in JWT token: ..." -ForegroundColor White
Write-Host "  ✅ Email notification sent to ..." -ForegroundColor White
Write-Host ""
Write-Host "Backend başladıktan sonra:" -ForegroundColor Cyan
Write-Host "  1. Frontend'de alarm oluşturun" -ForegroundColor White
Write-Host "  2. '🧪 Test Et' butonuna basın" -ForegroundColor White
Write-Host "  3. Email kutunuzu kontrol edin!" -ForegroundColor White
Write-Host ""

Set-Location backend
./mvnw spring-boot:run

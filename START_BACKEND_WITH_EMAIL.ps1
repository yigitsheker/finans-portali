# Email Test için Backend Başlatma Scripti

Write-Host "=== Finans Portalı Backend - Email Test ===" -ForegroundColor Cyan
Write-Host ""

# SMTP Ayarları
Write-Host "SMTP Ayarları yapılandırılıyor..." -ForegroundColor Yellow
Write-Host ""
Write-Host "NOT: Gmail kullanıyorsanız:" -ForegroundColor Yellow
Write-Host "1. 2-Factor Authentication aktif olmalı" -ForegroundColor Yellow
Write-Host "2. App Password oluşturmalısınız: https://myaccount.google.com/apppasswords" -ForegroundColor Yellow
Write-Host ""

# Kullanıcıdan email bilgilerini al
$mailUsername = Read-Host "Email adresiniz (gönderen)"
$mailPassword = Read-Host "Email şifreniz veya App Password" -AsSecureString
$mailPasswordPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($mailPassword))

# Environment variable'ları ayarla
$env:MAIL_USERNAME = $mailUsername
$env:MAIL_PASSWORD = $mailPasswordPlain

Write-Host ""
Write-Host "✅ Email ayarları yapılandırıldı:" -ForegroundColor Green
Write-Host "   Gönderen: $mailUsername" -ForegroundColor Green
Write-Host ""

# Backend'i başlat
Write-Host "Backend başlatılıyor..." -ForegroundColor Yellow
Write-Host "Loglarda şunları arayın:" -ForegroundColor Cyan
Write-Host "  - 🔔 PRICE ALERT: ..." -ForegroundColor Cyan
Write-Host "  - Found email in JWT token: ..." -ForegroundColor Cyan
Write-Host "  - ✅ Email notification sent to ..." -ForegroundColor Cyan
Write-Host ""

Set-Location backend
./mvnw spring-boot:run

# Haber Temizleme Scripti
# Her kategoriden en yeni 50 haberi tutar, geri kalanını siler

Write-Host "=== Haber Temizleme ===" -ForegroundColor Cyan
Write-Host ""

# Backend çalışıyor mu kontrol et
try {
    $health = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 2
    Write-Host "✅ Backend çalışıyor" -ForegroundColor Green
} catch {
    Write-Host "❌ Backend çalışmıyor! Önce backend'i başlatın:" -ForegroundColor Red
    Write-Host "   cd backend" -ForegroundColor Yellow
    Write-Host "   ./mvnw spring-boot:run" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "Eski haberler temizleniyor..." -ForegroundColor Yellow
Write-Host "Her kategoriden en yeni 50 haber tutulacak" -ForegroundColor Gray
Write-Host ""

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/news/cleanup" -Method Post -ContentType "application/json"
    
    Write-Host "✅ Temizleme tamamlandı!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Sonuçlar:" -ForegroundColor Cyan
    Write-Host "  Toplam silinen: $($response.totalDeleted)" -ForegroundColor White
    Write-Host "  Kalan toplam: $($response.remainingTotal)" -ForegroundColor White
    Write-Host ""
    Write-Host "Kategorilere göre silinen:" -ForegroundColor Cyan
    
    $response.deletedByCategory.PSObject.Properties | ForEach-Object {
        $category = $_.Name
        $count = $_.Value
        if ($count -gt 0) {
            Write-Host "  $category : $count" -ForegroundColor Yellow
        } else {
            Write-Host "  $category : $count" -ForegroundColor Gray
        }
    }
    
    Write-Host ""
    Write-Host "Frontend'i yenileyin, haber sayıları güncellenecek!" -ForegroundColor Green
    
} catch {
    Write-Host "❌ Temizleme başarısız!" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
}

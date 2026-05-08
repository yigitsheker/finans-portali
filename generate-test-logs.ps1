# Generate Test Logs Script
# Sends requests to backend to generate logs for OpenSearch

Write-Host "=== Generating Test Logs ===" -ForegroundColor Green
Write-Host ""

$backendUrl = "http://localhost:8080"

# Test endpoints
$endpoints = @(
    "/api/v1/market/instruments",
    "/api/v1/market/quotes",
    "/api/v1/news",
    "/api/v1/exchange-rates",
    "/api/v1/investment-funds"
)

Write-Host "Sending 50 test requests to generate logs..." -ForegroundColor Cyan
Write-Host ""

$successCount = 0
$errorCount = 0

for ($i = 1; $i -le 50; $i++) {
    $endpoint = $endpoints | Get-Random
    
    try {
        $response = Invoke-WebRequest -Uri "$backendUrl$endpoint" -Method GET -UseBasicParsing -TimeoutSec 5
        $statusCode = $response.StatusCode
        Write-Host "[OK] $i/50: $endpoint (Status: $statusCode)" -ForegroundColor Green
        $successCount++
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode) {
            Write-Host "[WARN] $i/50: $endpoint (Status: $statusCode)" -ForegroundColor Yellow
        } else {
            Write-Host "[ERROR] $i/50: $endpoint (Error: $($_.Exception.Message))" -ForegroundColor Red
        }
        $errorCount++
    }
    
    # Random delay between 200-800ms
    $delay = Get-Random -Minimum 200 -Maximum 800
    Start-Sleep -Milliseconds $delay
}

Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Cyan
Write-Host "Total requests: 50" -ForegroundColor White
Write-Host "Successful: $successCount" -ForegroundColor Green
Write-Host "Failed: $errorCount" -ForegroundColor Red
Write-Host ""
Write-Host "Waiting 30 seconds for logs to reach OpenSearch..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

Write-Host ""
Write-Host "=== Checking OpenSearch ===" -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri "http://localhost:9200/finans-portal-logs-*/_count" -UseBasicParsing
    $count = ($response.Content | ConvertFrom-Json).count
    Write-Host "[OK] Total logs in OpenSearch: $count" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Could not check OpenSearch: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Next Steps ===" -ForegroundColor Cyan
Write-Host "1. Go to OpenSearch Dashboards: http://localhost:5601" -ForegroundColor White
Write-Host "2. Go to Discover page to see the logs" -ForegroundColor White
Write-Host "3. Create visualizations following the guide:" -ForegroundColor White
Write-Host "   C:\Users\yigid\Desktop\finans-portali\MANUEL_DASHBOARD_OLUSTURMA.md" -ForegroundColor White
Write-Host ""

# JWT Token Test Script
# This script helps debug JWT token issues

Write-Host "=== JWT Token Test ===" -ForegroundColor Cyan
Write-Host ""

# 1. Check backend environment
Write-Host "1. Backend Issuer URI Configuration:" -ForegroundColor Yellow
docker-compose exec backend env | Select-String -Pattern "ISSUER"
Write-Host ""

# 2. Check for JWT errors in logs
Write-Host "2. Recent JWT Errors in Backend Logs:" -ForegroundColor Yellow
$jwtErrors = docker-compose logs backend --tail 100 | Select-String -Pattern "jwt|401|iss claim" -CaseSensitive:$false
if ($jwtErrors) {
    $jwtErrors | Select-Object -Last 10
} else {
    Write-Host "No JWT errors found! ✅" -ForegroundColor Green
}
Write-Host ""

# 3. Check backend health
Write-Host "3. Backend Health Status:" -ForegroundColor Yellow
docker-compose ps backend
Write-Host ""

# 4. Test backend actuator endpoint
Write-Host "4. Testing Backend Actuator (should return 200):" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing
    Write-Host "Status: $($response.StatusCode) ✅" -ForegroundColor Green
    Write-Host "Response: $($response.Content)"
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 5. Test public endpoint (should work without auth)
Write-Host "5. Testing Public Market Endpoint (should return 200):" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/market/summary" -UseBasicParsing
    Write-Host "Status: $($response.StatusCode) ✅" -ForegroundColor Green
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# 6. Test protected endpoint without token (should return 401)
Write-Host "6. Testing Protected Portfolio Endpoint WITHOUT Token (should return 401):" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/portfolio/positions" -UseBasicParsing
    Write-Host "Status: $($response.StatusCode)" -ForegroundColor Yellow
} catch {
    if ($_.Exception.Response.StatusCode -eq 401) {
        Write-Host "Status: 401 Unauthorized ✅ (Expected)" -ForegroundColor Green
    } else {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    }
}
Write-Host ""

Write-Host "=== Test Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "1. Close your browser completely (to clear cache)"
Write-Host "2. Open browser and go to: http://localhost"
Write-Host "3. Login with: john.doe / password123"
Write-Host "4. Try to access Portfolio page"
Write-Host "5. Check browser console (F12) for any 401 errors"
Write-Host ""
Write-Host "If you still see 401 errors, please share:" -ForegroundColor Yellow
Write-Host "- Browser console error messages"
Write-Host "- Network tab showing the failed request"
Write-Host "- The JWT token from Application > Local Storage"

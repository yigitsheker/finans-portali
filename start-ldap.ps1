# Finance Portal with LDAP - Quick Start Script
# This script starts all services and provides helpful information

Write-Host "========================================" -ForegroundColor Green
Write-Host "  Finance Portal with LDAP Integration" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# Check if Docker is running
Write-Host "Checking Docker..." -ForegroundColor Yellow
try {
    docker ps | Out-Null
    Write-Host "✓ Docker is running" -ForegroundColor Green
} catch {
    Write-Host "✗ Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Starting all services..." -ForegroundColor Yellow
docker-compose up -d

Write-Host ""
Write-Host "Waiting for services to be healthy (60 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 60

Write-Host ""
Write-Host "Checking service status..." -ForegroundColor Yellow
docker-compose ps

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Services Started Successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

Write-Host "Access URLs:" -ForegroundColor Cyan
Write-Host "  Frontend:        http://localhost" -ForegroundColor White
Write-Host "  Backend API:     http://localhost:8080" -ForegroundColor White
Write-Host "  Keycloak Admin:  http://localhost:8090" -ForegroundColor White
Write-Host "  phpLDAPadmin:    http://localhost:8089" -ForegroundColor White
Write-Host ""

Write-Host "Test Users:" -ForegroundColor Cyan
Write-Host "  Regular User:    john.doe / password123" -ForegroundColor White
Write-Host "  Admin User:      admin.user / admin123" -ForegroundColor White
Write-Host ""

Write-Host "Keycloak Admin:" -ForegroundColor Cyan
Write-Host "  Username:        admin" -ForegroundColor White
Write-Host "  Password:        admin" -ForegroundColor White
Write-Host ""

Write-Host "IMPORTANT: Keycloak LDAP Configuration Required!" -ForegroundColor Yellow
Write-Host "  1. Open http://localhost:8090" -ForegroundColor White
Write-Host "  2. Login as admin / admin" -ForegroundColor White
Write-Host "  3. Select 'finans' realm" -ForegroundColor White
Write-Host "  4. Follow steps in LDAP_SETUP.md (sections 4-7)" -ForegroundColor White
Write-Host ""

Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "  1. Configure Keycloak LDAP (see LDAP_SETUP.md)" -ForegroundColor White
Write-Host "  2. Test authentication (see TEST_LDAP_INTEGRATION.md)" -ForegroundColor White
Write-Host "  3. Access frontend at http://localhost" -ForegroundColor White
Write-Host ""

Write-Host "View logs:" -ForegroundColor Cyan
Write-Host "  docker-compose logs -f" -ForegroundColor White
Write-Host ""

Write-Host "Stop services:" -ForegroundColor Cyan
Write-Host "  docker-compose down" -ForegroundColor White
Write-Host ""

Write-Host "========================================" -ForegroundColor Green
Write-Host "  Ready for Configuration!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

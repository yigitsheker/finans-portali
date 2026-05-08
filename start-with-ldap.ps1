# Finance Portal - Quick Start with LDAP
# This script starts all services and loads LDAP data

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Finance Portal - Quick Start" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Start all services
Write-Host "[1/4] Starting all Docker services..." -ForegroundColor Yellow
docker-compose up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Failed to start services!" -ForegroundColor Red
    exit 1
}

Write-Host "Services started!" -ForegroundColor Green
Write-Host ""

# Step 2: Wait for OpenLDAP to be ready
Write-Host "[2/4] Waiting for OpenLDAP to be ready (40 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 40

# Check if OpenLDAP is healthy
$ldapStatus = docker inspect finans-openldap --format='{{.State.Health.Status}}' 2>$null

if ($ldapStatus -eq "healthy") {
    Write-Host "OpenLDAP is healthy!" -ForegroundColor Green
} else {
    Write-Host "WARNING: OpenLDAP health check status: $ldapStatus" -ForegroundColor Yellow
    Write-Host "Continuing anyway..." -ForegroundColor Yellow
}
Write-Host ""

# Step 3: Load LDAP data
Write-Host "[3/4] Loading LDAP data..." -ForegroundColor Yellow

# Copy init.ldif to container
docker cp ldap/init.ldif finans-openldap:/tmp/init.ldif 2>$null

# Load data (ignore errors if data already exists)
docker exec finans-openldap ldapadd -x -H ldap://localhost -D "cn=admin,dc=finance,dc=local" -w admin_password -f /tmp/init.ldif 2>$null

# Verify data
$ldapData = docker exec finans-openldap ldapsearch -x -H ldap://localhost -b "dc=finance,dc=local" -D "cn=admin,dc=finance,dc=local" -w admin_password "(objectClass=*)" dn 2>$null

$userCount = ($ldapData | Select-String "uid=.*,ou=users").Count
$groupCount = ($ldapData | Select-String "cn=.*,ou=groups").Count

if ($userCount -ge 4 -and $groupCount -ge 2) {
    Write-Host "LDAP data loaded successfully!" -ForegroundColor Green
    Write-Host "  - Users: $userCount" -ForegroundColor Gray
    Write-Host "  - Groups: $groupCount" -ForegroundColor Gray
} else {
    Write-Host "WARNING: LDAP data may not be fully loaded!" -ForegroundColor Yellow
    Write-Host "  - Users found: $userCount (expected: 4)" -ForegroundColor Gray
    Write-Host "  - Groups found: $groupCount (expected: 2)" -ForegroundColor Gray
}
Write-Host ""

# Step 4: Show status
Write-Host "[4/4] Checking service status..." -ForegroundColor Yellow
Write-Host ""

docker-compose ps

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Services Ready!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Access URLs:" -ForegroundColor Yellow
Write-Host "  Frontend:        http://localhost" -ForegroundColor White
Write-Host "  Backend API:     http://localhost:8080" -ForegroundColor White
Write-Host "  Backend Health:  http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host "  Keycloak Admin:  http://localhost:8090 (admin/admin)" -ForegroundColor White
Write-Host "  phpLDAPadmin:    http://localhost:8089" -ForegroundColor White
Write-Host ""

Write-Host "Test Credentials:" -ForegroundColor Yellow
Write-Host "  Regular User:    john.doe / password123" -ForegroundColor White
Write-Host "  Admin User:      admin.user / admin123" -ForegroundColor White
Write-Host "  LDAP Admin:      cn=admin,dc=finance,dc=local / admin_password" -ForegroundColor White
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Next Steps" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Configure Keycloak LDAP User Federation:" -ForegroundColor Yellow
Write-Host "   - Open: http://localhost:8090" -ForegroundColor Gray
Write-Host "   - Login: admin / admin" -ForegroundColor Gray
Write-Host "   - Select realm: finans" -ForegroundColor Gray
Write-Host "   - Follow detailed steps in LDAP_SETUP.md" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Test authentication:" -ForegroundColor Yellow
Write-Host "   - Open: http://localhost:8090/realms/finans/account" -ForegroundColor Gray
Write-Host "   - Login with: john.doe / password123" -ForegroundColor Gray
Write-Host ""
Write-Host "3. Test frontend:" -ForegroundColor Yellow
Write-Host "   - Open: http://localhost" -ForegroundColor Gray
Write-Host "   - Login and verify role-based UI" -ForegroundColor Gray
Write-Host ""

Write-Host "For detailed instructions, see:" -ForegroundColor Yellow
Write-Host "  - LDAP_CURRENT_STATUS.md (Quick start guide)" -ForegroundColor Gray
Write-Host "  - LDAP_SETUP.md (Complete setup guide)" -ForegroundColor Gray
Write-Host "  - TEST_LDAP_INTEGRATION.md (Testing guide)" -ForegroundColor Gray
Write-Host ""

Write-Host "Done! 🎉" -ForegroundColor Green

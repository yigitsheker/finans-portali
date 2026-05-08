# LDAP Data Loader Script
# This script loads the LDAP directory structure and users into OpenLDAP

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  LDAP Data Loader" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if OpenLDAP container is running
Write-Host "Checking OpenLDAP container status..." -ForegroundColor Yellow
$containerStatus = docker ps --filter "name=finans-openldap" --format "{{.Status}}"

if (-not $containerStatus) {
    Write-Host "ERROR: OpenLDAP container is not running!" -ForegroundColor Red
    Write-Host "Please start it first: docker-compose up -d openldap" -ForegroundColor Red
    exit 1
}

Write-Host "OpenLDAP container is running: $containerStatus" -ForegroundColor Green
Write-Host ""

# Copy init.ldif to container
Write-Host "Copying init.ldif to container..." -ForegroundColor Yellow
docker cp ldap/init.ldif finans-openldap:/tmp/init.ldif

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Failed to copy init.ldif to container!" -ForegroundColor Red
    exit 1
}

Write-Host "File copied successfully!" -ForegroundColor Green
Write-Host ""

# Load LDAP data
Write-Host "Loading LDAP data..." -ForegroundColor Yellow
docker exec finans-openldap ldapadd -x -H ldap://localhost -D "cn=admin,dc=finance,dc=local" -w admin_password -f /tmp/init.ldif

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "WARNING: Some entries may already exist. This is normal if you've run this script before." -ForegroundColor Yellow
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "LDAP data loaded successfully!" -ForegroundColor Green
    Write-Host ""
}

# Verify data
Write-Host "Verifying LDAP data..." -ForegroundColor Yellow
Write-Host ""

$ldapData = docker exec finans-openldap ldapsearch -x -H ldap://localhost -b "dc=finance,dc=local" -D "cn=admin,dc=finance,dc=local" -w admin_password "(objectClass=*)" dn

# Count entries
$userCount = ($ldapData | Select-String "uid=.*,ou=users").Count
$groupCount = ($ldapData | Select-String "cn=.*,ou=groups").Count

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  LDAP Data Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Users loaded: $userCount" -ForegroundColor Green
Write-Host "Groups loaded: $groupCount" -ForegroundColor Green
Write-Host ""

Write-Host "Test Users:" -ForegroundColor Yellow
Write-Host "  - john.doe / password123 (USER)" -ForegroundColor White
Write-Host "  - jane.smith / password123 (USER)" -ForegroundColor White
Write-Host "  - test.user / test123 (USER)" -ForegroundColor White
Write-Host "  - admin.user / admin123 (ADMIN)" -ForegroundColor White
Write-Host ""

Write-Host "Groups:" -ForegroundColor Yellow
Write-Host "  - finance-users (john.doe, jane.smith, test.user)" -ForegroundColor White
Write-Host "  - finance-admins (admin.user)" -ForegroundColor White
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Next Steps" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "1. Access phpLDAPadmin: http://localhost:8089" -ForegroundColor White
Write-Host "   Login: cn=admin,dc=finance,dc=local / admin_password" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Configure Keycloak LDAP User Federation:" -ForegroundColor White
Write-Host "   - Open: http://localhost:8090" -ForegroundColor Gray
Write-Host "   - Login: admin / admin" -ForegroundColor Gray
Write-Host "   - Follow steps in LDAP_SETUP.md" -ForegroundColor Gray
Write-Host ""
Write-Host "3. Test authentication with LDAP users" -ForegroundColor White
Write-Host ""

Write-Host "Done!" -ForegroundColor Green

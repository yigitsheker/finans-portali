# LDAP Integration Testing Guide

This guide provides step-by-step instructions to test the complete LDAP integration with Keycloak and the Finance Portal application.

## 🎯 Test Objectives

1. ✅ Verify OpenLDAP is running and accessible
2. ✅ Verify Keycloak can connect to LDAP
3. ✅ Verify users can authenticate via LDAP
4. ✅ Verify roles are correctly mapped from LDAP groups
5. ✅ Verify JWT tokens contain correct role information
6. ✅ Verify backend enforces role-based authorization
7. ✅ Verify frontend shows/hides UI based on roles

## 📋 Prerequisites

- Docker and Docker Compose installed
- All services stopped: `docker-compose down`
- Clean state (optional): `docker-compose down -v` to remove volumes

## 🚀 Test Procedure

### Test 1: Start All Services

```bash
# Navigate to project directory
cd c:\Users\yigid\Desktop\finans-portali

# Start all services
docker-compose up -d

# Wait for all services to be healthy (60-90 seconds)
docker-compose ps

# Expected output: All services should show "Up" and "healthy" status
# - finans-openldap: healthy
# - finans-postgres: healthy
# - finans-keycloak: healthy (or unhealthy but running - this is OK)
# - finans-backend: healthy
# - finans-frontend: healthy
# - finans-phpldapadmin: Up
```

**✅ Success Criteria:**
- All containers are running
- No restart loops
- Backend and frontend are healthy

**❌ If Failed:**
- Check logs: `docker-compose logs -f`
- Check specific service: `docker-compose logs openldap`
- Verify ports are not in use: `netstat -ano | findstr "389 8080 8090"`

---

### Test 2: Verify OpenLDAP is Running

```bash
# Test LDAP connection
docker exec finans-openldap ldapsearch -x -H ldap://localhost \
  -b "dc=finance,dc=local" \
  -D "cn=admin,dc=finance,dc=local" \
  -w admin_password

# Expected output: Should list all LDAP entries including:
# - ou=users,dc=finance,dc=local
# - ou=groups,dc=finance,dc=local
# - uid=john.doe,ou=users,dc=finance,dc=local
# - uid=jane.smith,ou=users,dc=finance,dc=local
# - uid=admin.user,ou=users,dc=finance,dc=local
# - cn=finance-users,ou=groups,dc=finance,dc=local
# - cn=finance-admins,ou=groups,dc=finance,dc=local
```

**✅ Success Criteria:**
- Command returns LDAP entries
- All 4 users are present
- All 2 groups are present

**❌ If Failed:**
- Check OpenLDAP logs: `docker-compose logs openldap`
- Verify init.ldif was loaded: `docker-compose logs openldap | grep "init.ldif"`
- Restart OpenLDAP: `docker-compose restart openldap`

---

### Test 3: Access phpLDAPadmin (Optional)

1. Open browser: http://localhost:8089
2. Click "login"
3. Login DN: `cn=admin,dc=finance,dc=local`
4. Password: `admin_password`
5. Browse directory structure

**✅ Success Criteria:**
- Can login to phpLDAPadmin
- Can see dc=finance,dc=local
- Can see users and groups

---

### Test 4: Configure Keycloak LDAP User Federation

⚠️ **IMPORTANT**: This step must be done manually in Keycloak Admin Console.

1. Open browser: http://localhost:8090
2. Click "Administration Console"
3. Username: `admin`
4. Password: `admin`
5. Select realm: **finans** (top-left dropdown)
6. Follow the detailed steps in `LDAP_SETUP.md` sections 4-7:
   - Configure LDAP User Federation
   - Configure LDAP Group Mapping
   - Configure Role Mapping
   - Configure Client Role Mappers

**✅ Success Criteria:**
- LDAP provider is configured
- Test connection succeeds
- Test authentication succeeds
- Users are synchronized (4 users)
- Groups are synchronized (2 groups)
- Roles are created (USER, ADMIN)
- Groups are mapped to roles
- Client scope mappers are configured

**❌ If Failed:**
- See troubleshooting section in `LDAP_SETUP.md`
- Verify OpenLDAP is accessible from Keycloak container:
  ```bash
  docker exec finans-keycloak curl -v telnet://openldap:389
  ```

---

### Test 5: Test LDAP Authentication via Keycloak

1. Open new incognito/private browser window
2. Go to: http://localhost:8090/realms/finans/account
3. Click "Sign in"
4. Try logging in with regular user:
   - Username: `john.doe`
   - Password: `password123`
5. Should successfully log in
6. Logout
7. Try logging in with admin user:
   - Username: `admin.user`
   - Password: `admin123`
8. Should successfully log in

**✅ Success Criteria:**
- Both users can log in
- No authentication errors
- Redirected to account page after login

**❌ If Failed:**
- Check Keycloak logs: `docker-compose logs keycloak | grep -i "ldap\|auth"`
- Verify user exists in LDAP (Test 2)
- Verify LDAP provider is enabled in Keycloak
- Try re-syncing users in Keycloak

---

### Test 6: Inspect JWT Token

1. After logging in as `john.doe` (Test 5)
2. Open browser DevTools (F12)
3. Go to Application/Storage → Local Storage → http://localhost:8090
4. Find and copy the access token
5. Go to https://jwt.io
6. Paste the token in the "Encoded" section
7. Inspect the decoded payload

**Expected Claims:**
```json
{
  "sub": "...",
  "preferred_username": "john.doe",
  "email": "john.doe@finance.local",
  "given_name": "John",
  "family_name": "Doe",
  "name": "John Doe",
  "roles": ["USER"],
  "realm_access": {
    "roles": ["USER"]
  }
}
```

8. Logout and login as `admin.user`
9. Inspect token again

**Expected Claims for Admin:**
```json
{
  "sub": "...",
  "preferred_username": "admin.user",
  "email": "admin.user@finance.local",
  "roles": ["ADMIN"],
  "realm_access": {
    "roles": ["ADMIN"]
  }
}
```

**✅ Success Criteria:**
- Token contains `preferred_username`
- Token contains `email`
- Token contains `roles` array
- Regular user has `["USER"]` role
- Admin user has `["ADMIN"]` role

**❌ If Failed:**
- Check client scope mappers in Keycloak
- Verify role mapping in groups
- Re-login to get fresh token

---

### Test 7: Test Frontend Login

1. Open browser: http://localhost (or http://localhost:5173 for dev)
2. Should see login page or redirect to Keycloak
3. Login with `john.doe` / `password123`
4. Should redirect back to app
5. Check browser console for token
6. Verify sidebar shows:
   - ✅ Navigation section
   - ✅ Preferences section
   - ❌ Admin section (should NOT be visible for regular user)
   - ✅ User info at bottom (no admin badge)

7. Logout
8. Login with `admin.user` / `admin123`
9. Verify sidebar shows:
   - ✅ Navigation section
   - ✅ **Admin section** (should be visible)
   - ✅ Preferences section
   - ✅ User info with "👑 Admin" badge

**✅ Success Criteria:**
- Login works for both users
- Regular user doesn't see admin menu
- Admin user sees admin menu
- Admin badge appears for admin user

**❌ If Failed:**
- Check browser console for errors
- Verify Keycloak URL in `frontend/src/auth/keycloak.ts` is `http://localhost:8090`
- Check token contains roles (Test 6)
- Clear browser cache and retry

---

### Test 8: Test Backend API - Public Endpoints

```bash
# Test public market endpoint (no authentication required)
curl http://localhost:8080/api/v1/market/instruments

# Expected: 200 OK with JSON array of market instruments
```

**✅ Success Criteria:**
- Returns 200 OK
- Returns JSON data
- No authentication required

---

### Test 9: Test Backend API - Protected Endpoints (Regular User)

```bash
# 1. Login as john.doe and get token from browser (Test 7)
# 2. Copy the access token
# 3. Set token variable (replace with actual token)
$TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

# Test portfolio endpoint (requires authentication)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/portfolio/positions

# Expected: 200 OK with portfolio data (may be empty array)

# Test admin endpoint (should be FORBIDDEN for regular user)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/admin/me

# Expected: 403 Forbidden
```

**✅ Success Criteria:**
- Portfolio endpoint returns 200 OK
- Admin endpoint returns 403 Forbidden
- Regular user cannot access admin endpoints

**❌ If Failed:**
- Verify token is valid (not expired)
- Check backend logs: `docker-compose logs backend | grep -i "401\|403\|jwt"`
- Verify SecurityConfig has correct role requirements

---

### Test 10: Test Backend API - Admin Endpoints (Admin User)

```bash
# 1. Login as admin.user and get token from browser
# 2. Copy the access token
# 3. Set token variable (replace with actual token)
$ADMIN_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

# Test admin info endpoint
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/v1/admin/me

# Expected: 200 OK with JSON:
# {
#   "userId": "...",
#   "username": "admin.user",
#   "email": "admin.user@finance.local",
#   "fullName": "Admin User",
#   "roles": ["ADMIN"],
#   "isAdmin": true
# }

# Test admin action endpoint
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/v1/admin/refresh-prices

# Expected: 200 OK with message "Price refresh triggered by admin.user"
```

**✅ Success Criteria:**
- Admin endpoints return 200 OK
- Response contains correct user information
- Admin can execute admin actions

**❌ If Failed:**
- Verify token contains ADMIN role (Test 6)
- Check backend logs for authorization errors
- Verify JwtRoleConverter is working

---

### Test 11: Test Frontend Admin Page

1. Login as `admin.user` / `admin123`
2. Click "Yönetim" (Admin) in sidebar
3. Should see Admin Panel page
4. Click "Bilgilerimi Göster" (Show My Info)
5. Should see JSON with admin user information
6. Try clicking other admin actions:
   - "Piyasa Verilerini Sıfırla"
   - "Fiyatları Güncelle"
   - "Haberleri Sıfırla"
7. Should see success messages

8. Logout
9. Login as `john.doe` / `password123`
10. Try to access admin page by clicking sidebar (should not be visible)
11. Try direct URL: http://localhost/admin (if using routing)
12. Should see "Access Denied" message

**✅ Success Criteria:**
- Admin user can access admin page
- Admin actions work
- Regular user cannot access admin page
- Proper error message for unauthorized access

---

### Test 12: Test Role-Based Authorization End-to-End

**Scenario 1: Regular User**
1. Login as `john.doe`
2. ✅ Can view market data
3. ✅ Can view news
4. ✅ Can view portfolio
5. ✅ Can create price alerts
6. ❌ Cannot see admin menu
7. ❌ Cannot access admin endpoints

**Scenario 2: Admin User**
1. Login as `admin.user`
2. ✅ Can view market data
3. ✅ Can view news
4. ✅ Can view portfolio
5. ✅ Can create price alerts
6. ✅ Can see admin menu
7. ✅ Can access admin endpoints
8. ✅ Can execute admin actions

**✅ Success Criteria:**
- All scenarios pass
- Authorization is enforced consistently
- No security bypasses

---

## 🐛 Common Issues and Solutions

### Issue 1: OpenLDAP container fails to start

**Symptoms:**
- Container exits immediately
- Logs show "permission denied"

**Solution:**
```bash
# Remove volumes and restart
docker-compose down -v
docker-compose up -d openldap
docker-compose logs -f openldap
```

### Issue 2: Keycloak cannot connect to LDAP

**Symptoms:**
- "Connection refused" error
- "Unknown host" error

**Solution:**
- Verify OpenLDAP is running: `docker-compose ps openldap`
- Use Docker service name: `ldap://openldap:389` (NOT localhost)
- Check network: `docker network inspect finans-portali_finans-network`

### Issue 3: Users not syncing from LDAP

**Symptoms:**
- User list is empty after sync
- "No users found" message

**Solution:**
1. Verify LDAP data exists (Test 2)
2. Check Users DN: `ou=users,dc=finance,dc=local`
3. Check User Object Classes: `inetOrgPerson, organizationalPerson, person, top`
4. Click "Synchronize all users" again
5. Check Keycloak logs: `docker-compose logs keycloak | grep -i sync`

### Issue 4: Roles not in JWT token

**Symptoms:**
- Token doesn't contain `roles` claim
- Authorization fails in backend

**Solution:**
1. Verify client scope mappers (Test 4, Step 7)
2. Check group-to-role mapping (Test 4, Step 6.2)
3. Re-login to get fresh token
4. Inspect token at jwt.io (Test 6)

### Issue 5: Backend returns 401 Unauthorized

**Symptoms:**
- All protected endpoints return 401
- Valid token but still unauthorized

**Solution:**
1. Check backend logs: `docker-compose logs backend | grep -i jwt`
2. Verify issuer URI matches:
   - Backend: `http://keycloak:8080/realms/finans`
   - Token `iss` claim should match
3. Check JWK set URI is accessible:
   ```bash
   docker exec finans-backend curl http://keycloak:8080/realms/finans/protocol/openid-connect/certs
   ```
4. Verify token is not expired

### Issue 6: Backend returns 403 Forbidden

**Symptoms:**
- Authentication works (200 on some endpoints)
- Admin endpoints return 403

**Solution:**
1. Verify token contains correct roles (Test 6)
2. Check SecurityConfig role requirements
3. Verify JwtRoleConverter is configured
4. Check role prefix (ROLE_ vs no prefix)

### Issue 7: Frontend doesn't show admin menu

**Symptoms:**
- Admin user logged in
- Admin menu not visible

**Solution:**
1. Check token contains ADMIN role (Test 6)
2. Verify roleUtils.ts is correctly extracting roles
3. Check browser console for errors
4. Clear browser cache and re-login

---

## 📊 Test Results Checklist

Use this checklist to track your testing progress:

- [ ] Test 1: All services started successfully
- [ ] Test 2: OpenLDAP is accessible and contains data
- [ ] Test 3: phpLDAPadmin is accessible (optional)
- [ ] Test 4: Keycloak LDAP federation configured
- [ ] Test 5: LDAP authentication works for both users
- [ ] Test 6: JWT tokens contain correct roles
- [ ] Test 7: Frontend login works, admin menu visibility correct
- [ ] Test 8: Public endpoints accessible without auth
- [ ] Test 9: Regular user can access user endpoints, not admin endpoints
- [ ] Test 10: Admin user can access admin endpoints
- [ ] Test 11: Frontend admin page works correctly
- [ ] Test 12: End-to-end role-based authorization works

---

## 🎓 Learning Points

After completing these tests, you should understand:

1. **LDAP Structure**: How users and groups are organized in LDAP
2. **Keycloak Integration**: How Keycloak connects to LDAP for authentication
3. **Role Mapping**: How LDAP groups map to Keycloak roles
4. **JWT Tokens**: How roles are included in JWT tokens
5. **Backend Authorization**: How Spring Security validates JWT and enforces roles
6. **Frontend Authorization**: How React shows/hides UI based on roles
7. **Security Flow**: Complete authentication and authorization flow

---

## 📚 Next Steps

After successful testing:

1. **Production Preparation**:
   - Change all default passwords
   - Enable TLS/SSL for LDAP
   - Enable HTTPS for Keycloak
   - Use secrets management (Kubernetes secrets, Vault)
   - Disable phpLDAPadmin

2. **Additional Features**:
   - Add more LDAP users
   - Create additional roles
   - Implement fine-grained permissions
   - Add audit logging
   - Implement password policies

3. **Monitoring**:
   - Set up LDAP monitoring
   - Monitor Keycloak performance
   - Track authentication failures
   - Monitor JWT token usage

---

**Testing Date**: _____________
**Tester**: _____________
**Result**: ☐ Pass ☐ Fail
**Notes**: _____________________________________________

# Authentication Test Guide

## Problem Fixed
✅ **Backend JWT validation issue resolved**

### What was wrong:
- Backend `application.yml` had wrong Keycloak port (8081 instead of 8090)
- JWT issuer claim validation was failing with error: "The iss claim is not valid"
- All protected endpoints returned 401 Unauthorized

### What was fixed:
1. Updated `application.yml` default profile:
   - Changed `jwk-set-uri` from `http://localhost:8081/...` to `http://localhost:8090/...`
   - Added `issuer-uri: http://localhost:8090/realms/finans`

2. Updated `application.yml` docker profile:
   - Kept `jwk-set-uri: http://keycloak:8080/...` (internal Docker network)
   - Added `issuer-uri: http://localhost:8090/realms/finans` (external URL for JWT validation)

3. Rebuilt and restarted backend container

## Test Steps

### 1. Login Test
1. Open browser: `http://localhost`
2. You should be automatically redirected to Keycloak login
3. Login with test user:
   - **Regular user**: `john.doe` / `password123`
   - **Admin user**: `admin.user` / `admin123`
4. After login, you should be redirected back to the application

### 2. Portfolio Access Test (Regular User)
1. Login as `john.doe` / `password123`
2. Click on "Portföy" tab in sidebar
3. You should see your portfolio positions (no 401 error)
4. Check browser console (F12) - should see successful API calls:
   ```
   GET http://localhost:8080/api/v1/portfolio/positions 200 OK
   ```

### 3. Admin Menu Test
1. Login as `john.doe` (regular user)
2. Check sidebar - **Admin menu should NOT be visible**
3. Logout (click user icon → Logout)
4. Login as `admin.user` / `admin123`
5. Check sidebar - **Admin menu SHOULD be visible** ✅

### 4. Admin Access Test
1. Login as `admin.user` / `admin123`
2. Click on "Admin" tab in sidebar
3. You should see admin panel (no 401 error)
4. Try admin operations (should work)

### 5. Role-Based Access Test
1. Login as `john.doe` (regular user)
2. Try to access admin endpoint directly:
   ```
   http://localhost:8080/api/v1/admin/users
   ```
3. Should get 403 Forbidden (correct - user doesn't have ADMIN role)
4. Logout and login as `admin.user`
5. Try same endpoint - should work (200 OK)

## Expected Results

### ✅ Success Indicators:
- No "401 Unauthorized" errors in browser console
- Portfolio page loads successfully for authenticated users
- Admin menu visible only for admin users
- Admin endpoints accessible only for admin users
- JWT tokens validated correctly by backend

### ❌ If Still Failing:
1. Check backend logs:
   ```powershell
   docker-compose logs backend --tail 50
   ```
2. Look for JWT errors:
   ```powershell
   docker-compose logs backend | Select-String "jwt|401|iss claim"
   ```
3. Verify Keycloak is running:
   ```powershell
   docker-compose ps keycloak
   ```
4. Check JWT token in browser:
   - Open browser DevTools (F12)
   - Go to Application → Local Storage → `http://localhost`
   - Look for Keycloak token
   - Copy token and decode at https://jwt.io
   - Verify `iss` claim is: `http://localhost:8090/realms/finans`

## Test Users

| Username | Password | Role | Can Access Portfolio | Can Access Admin |
|----------|----------|------|---------------------|------------------|
| john.doe | password123 | USER | ✅ Yes | ❌ No |
| jane.smith | password123 | USER | ✅ Yes | ❌ No |
| test.user | test123 | USER | ✅ Yes | ❌ No |
| admin.user | admin123 | ADMIN | ✅ Yes | ✅ Yes |

## Quick Verification Commands

```powershell
# Check all containers are running
docker-compose ps

# Check backend logs for errors
docker-compose logs backend --tail 50

# Check if backend is healthy
docker-compose ps backend

# Restart backend if needed
docker-compose restart backend

# View real-time backend logs
docker-compose logs -f backend
```

## Next Steps After Successful Test

Once authentication is working:
1. ✅ Users can login via Keycloak
2. ✅ JWT tokens are validated correctly
3. ✅ Role-based access control works
4. ✅ Admin menu shows only for admin users
5. ✅ Portfolio and admin endpoints are accessible

You can now:
- Add more users in LDAP (via phpLDAPadmin at http://localhost:8089)
- Assign roles in Keycloak (at http://localhost:8090)
- Develop new features with proper authentication
- Test role-based authorization for new endpoints

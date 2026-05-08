# LDAP Integration Setup Guide

## 🏗️ Architecture Overview

```
┌─────────────┐
│   Browser   │
│  (React)    │
└──────┬──────┘
       │ 1. Login redirect
       ▼
┌─────────────┐
│  Keycloak   │◄──────────┐
│  Port 8090  │           │ 3. Authenticate user
└──────┬──────┘           │    Check password
       │ 2. Query user    │
       ▼                  │
┌─────────────┐           │
│  OpenLDAP   │───────────┘
│  Port 389   │
│             │
│ Users:      │
│ - john.doe  │
│ - jane.smith│
│ - admin.user│
│             │
│ Groups:     │
│ - finance-  │
│   users     │
│ - finance-  │
│   admins    │
└─────────────┘
       
       ┌──────────────┐
       │ JWT Token    │
       │ - sub        │
       │ - email      │
       │ - roles:     │
       │   [USER] or  │
       │   [ADMIN]    │
       └──────┬───────┘
              │ 4. API calls with Bearer token
              ▼
       ┌─────────────┐
       │ Spring Boot │
       │ Backend     │
       │ Port 8080   │
       │             │
       │ - Validates │
       │   JWT       │
       │ - Checks    │
       │   roles     │
       └─────────────┘
```

## 📋 LDAP Directory Structure

```
dc=finance,dc=local
├── ou=users
│   ├── uid=john.doe (Regular User)
│   ├── uid=jane.smith (Regular User)
│   ├── uid=test.user (Test User)
│   └── uid=admin.user (Administrator)
└── ou=groups
    ├── cn=finance-users (Regular users group)
    └── cn=finance-admins (Administrators group)
```

## 🔐 Test Credentials (DEVELOPMENT ONLY)

### LDAP Admin
- **DN**: `cn=admin,dc=finance,dc=local`
- **Password**: `admin_password`
- **Usage**: LDAP administration, Keycloak bind

### Regular Users
| Username | Password | Email | Role | Group |
|----------|----------|-------|------|-------|
| john.doe | password123 | john.doe@finance.local | USER | finance-users |
| jane.smith | password123 | jane.smith@finance.local | USER | finance-users |
| test.user | test123 | test.user@finance.local | USER | finance-users |

### Admin User
| Username | Password | Email | Role | Group |
|----------|----------|-------|------|-------|
| admin.user | admin123 | admin.user@finance.local | ADMIN | finance-admins |

### Keycloak Admin
- **Username**: `admin`
- **Password**: `admin`
- **URL**: http://localhost:8090

### phpLDAPadmin
- **URL**: http://localhost:8089
- **Login DN**: `cn=admin,dc=finance,dc=local`
- **Password**: `admin_password`

## 🚀 Quick Start

### 1. Start All Services

```bash
# Start all containers
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f openldap
docker-compose logs -f keycloak
```

### 2. Verify OpenLDAP is Running

```bash
# Test LDAP connection
docker exec finans-openldap ldapsearch -x -H ldap://localhost -b dc=finance,dc=local -D "cn=admin,dc=finance,dc=local" -w admin_password

# Should return all users and groups
```

### 3. Access phpLDAPadmin (Optional)

1. Open http://localhost:8089
2. Click "login"
3. Login DN: `cn=admin,dc=finance,dc=local`
4. Password: `admin_password`
5. Browse the directory structure

### 4. Configure Keycloak LDAP User Federation

⚠️ **IMPORTANT**: These steps must be done manually in Keycloak Admin Console.

#### Step 4.1: Access Keycloak Admin Console

1. Open http://localhost:8090
2. Click "Administration Console"
3. Username: `admin`
4. Password: `admin`

#### Step 4.2: Navigate to User Federation

1. Select realm: **finans** (top-left dropdown)
2. Click **User federation** in left menu
3. Click **Add LDAP provider**

#### Step 4.3: Configure LDAP Connection

**Required Settings:**

| Setting | Value | Description |
|---------|-------|-------------|
| **Vendor** | `Other` | OpenLDAP vendor |
| **Connection URL** | `ldap://openldap:389` | LDAP server URL (use Docker service name) |
| **Bind DN** | `cn=admin,dc=finance,dc=local` | Admin DN for binding |
| **Bind Credential** | `admin_password` | Admin password |

**User Settings:**

| Setting | Value | Description |
|---------|-------|-------------|
| **Edit Mode** | `READ_ONLY` | Users managed in LDAP only |
| **Users DN** | `ou=users,dc=finance,dc=local` | Where users are stored |
| **Username LDAP attribute** | `uid` | Username field |
| **RDN LDAP attribute** | `uid` | Relative DN attribute |
| **UUID LDAP attribute** | `entryUUID` | Unique identifier |
| **User Object Classes** | `inetOrgPerson, organizationalPerson, person, top` | LDAP object classes |

**Search Settings:**

| Setting | Value | Description |
|---------|-------|-------------|
| **Search Scope** | `Subtree` | Search all sub-levels |

**Synchronization Settings:**

| Setting | Value | Description |
|---------|-------|-------------|
| **Import Users** | `ON` | Import existing LDAP users |
| **Sync Registrations** | `OFF` | Don't sync new registrations to LDAP |
| **Trust Email** | `ON` | Trust email from LDAP |
| **Periodic Full Sync** | `ON` (optional) | Sync all users periodically |
| **Periodic Changed Users Sync** | `ON` (optional) | Sync changed users |

#### Step 4.4: Test Connection

1. Click **Test connection** button
2. Should show: "Success! LDAP connection successful"
3. Click **Test authentication** button
4. Should show: "Success! LDAP authentication successful"

#### Step 4.5: Save Configuration

1. Click **Save** button at bottom
2. Wait for confirmation message

#### Step 4.6: Synchronize Users

1. Scroll to bottom of LDAP provider page
2. Click **Synchronize all users** button
3. Should show: "Success! Sync of users finished successfully. X imported users, Y updated users"
4. Go to **Users** in left menu
5. You should see: john.doe, jane.smith, test.user, admin.user

### 5. Configure LDAP Group Mapping

#### Step 5.1: Add Group Mapper

1. In LDAP provider page, click **Mappers** tab
2. Click **Add mapper**
3. Configure:

| Setting | Value |
|---------|-------|
| **Name** | `group-mapper` |
| **Mapper Type** | `group-ldap-mapper` |
| **LDAP Groups DN** | `ou=groups,dc=finance,dc=local` |
| **Group Name LDAP Attribute** | `cn` |
| **Group Object Classes** | `groupOfNames` |
| **Membership LDAP Attribute** | `member` |
| **Membership Attribute Type** | `DN` |
| **Mode** | `READ_ONLY` |
| **User Groups Retrieve Strategy** | `LOAD_GROUPS_BY_MEMBER_ATTRIBUTE` |
| **Member-Of LDAP Attribute** | `memberOf` |
| **Drop non-existing groups during sync** | `OFF` |

4. Click **Save**
5. Click **Sync LDAP groups to Keycloak** button

#### Step 5.2: Verify Groups

1. Go to **Groups** in left menu
2. You should see:
   - `finance-users`
   - `finance-admins`
3. Click on each group to see members

### 6. Configure Role Mapping

#### Step 6.1: Create Realm Roles

1. Go to **Realm roles** in left menu
2. Click **Create role**
3. Create **USER** role:
   - Role name: `USER`
   - Description: `Regular finance portal user`
   - Click **Save**
4. Create **ADMIN** role:
   - Role name: `ADMIN`
   - Description: `Finance portal administrator`
   - Click **Save**

#### Step 6.2: Map Groups to Roles

1. Go to **Groups** in left menu
2. Click **finance-users** group
3. Click **Role mapping** tab
4. Click **Assign role**
5. Select **USER** role
6. Click **Assign**

7. Go back to **Groups**
8. Click **finance-admins** group
9. Click **Role mapping** tab
10. Click **Assign role**
11. Select **ADMIN** role
12. Click **Assign**

### 7. Configure Client Role Mappers

#### Step 7.1: Add Realm Roles to Token

1. Go to **Clients** in left menu
2. Click **finans-frontend** client
3. Click **Client scopes** tab
4. Click **finans-frontend-dedicated** scope
5. Click **Add mapper** → **By configuration**
6. Select **User Realm Role**
7. Configure:

| Setting | Value |
|---------|-------|
| **Name** | `realm-roles` |
| **Mapper Type** | `User Realm Role` |
| **Token Claim Name** | `roles` |
| **Claim JSON Type** | `String` |
| **Add to ID token** | `ON` |
| **Add to access token** | `ON` |
| **Add to userinfo** | `ON` |

8. Click **Save**

#### Step 7.2: Add User Attributes to Token

1. In same client scope, click **Add mapper** → **By configuration**
2. Select **User Property**
3. Configure for email:

| Setting | Value |
|---------|-------|
| **Name** | `email` |
| **Mapper Type** | `User Property` |
| **Property** | `email` |
| **Token Claim Name** | `email` |
| **Claim JSON Type** | `String` |
| **Add to ID token** | `ON` |
| **Add to access token** | `ON` |
| **Add to userinfo** | `ON` |

4. Click **Save**

### 8. Test LDAP Authentication

#### Step 8.1: Test Login via Keycloak

1. Open new incognito/private browser window
2. Go to http://localhost:8090/realms/finans/account
3. Click **Sign in**
4. Try logging in with:
   - Username: `john.doe`
   - Password: `password123`
5. Should successfully log in

#### Step 8.2: Inspect JWT Token

1. After login, open browser DevTools (F12)
2. Go to Application/Storage → Local Storage
3. Find Keycloak token
4. Copy access token
5. Go to https://jwt.io
6. Paste token
7. Verify token contains:
   - `sub`: User ID
   - `preferred_username`: `john.doe`
   - `email`: `john.doe@finance.local`
   - `roles`: `["USER"]`

#### Step 8.3: Test Admin User

1. Logout
2. Login with:
   - Username: `admin.user`
   - Password: `admin123`
3. Inspect token
4. Verify `roles`: `["ADMIN"]`

### 9. Test Frontend Login

1. Start frontend (if not using Docker):
   ```bash
   cd frontend
   npm run dev
   ```

2. Open http://localhost:5173 (or http://localhost if using Docker)
3. Click login
4. Login with `john.doe` / `password123`
5. Should redirect back to app
6. Check browser console for token

### 10. Test Backend API

#### Test with Regular User

```bash
# 1. Get access token (replace with actual token from browser)
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

# 2. Test public endpoint (should work without token)
curl http://localhost:8080/api/v1/market/instruments

# 3. Test protected endpoint (requires authentication)
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/v1/portfolio/positions

# Should return 200 OK with portfolio data
```

#### Test with Admin User

```bash
# 1. Login as admin.user and get token
ADMIN_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

# 2. Test admin endpoint
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
     http://localhost:8080/api/v1/admin/users

# Should return 200 OK (after we implement admin endpoints)
```

## 🔧 Troubleshooting

### Issue: Keycloak cannot connect to LDAP

**Symptoms:**
- "Error! Unable to connect to LDAP server"
- Connection timeout

**Solutions:**
1. Check OpenLDAP is running:
   ```bash
   docker-compose ps openldap
   ```

2. Check OpenLDAP logs:
   ```bash
   docker-compose logs openldap
   ```

3. Verify connection URL uses Docker service name:
   - ✅ Correct: `ldap://openldap:389`
   - ❌ Wrong: `ldap://localhost:389`

4. Test LDAP from Keycloak container:
   ```bash
   docker exec finans-keycloak curl -v telnet://openldap:389
   ```

### Issue: Users not found in LDAP

**Symptoms:**
- "User not found" error
- Empty user list after sync

**Solutions:**
1. Verify LDAP data:
   ```bash
   docker exec finans-openldap ldapsearch -x -H ldap://localhost \
     -b "ou=users,dc=finance,dc=local" \
     -D "cn=admin,dc=finance,dc=local" \
     -w admin_password
   ```

2. Check Users DN in Keycloak:
   - Should be: `ou=users,dc=finance,dc=local`

3. Re-sync users:
   - Go to User Federation → LDAP provider
   - Click "Synchronize all users"

### Issue: Password login fails

**Symptoms:**
- "Invalid username or password"
- Authentication fails for known users

**Solutions:**
1. Test authentication directly:
   ```bash
   docker exec finans-openldap ldapwhoami -x -H ldap://localhost \
     -D "uid=john.doe,ou=users,dc=finance,dc=local" \
     -w password123
   ```

2. Check Bind DN and credential in Keycloak

3. Verify Edit Mode is READ_ONLY (not UNSYNCED)

4. Check user password in LDAP:
   ```bash
   docker exec finans-openldap ldapsearch -x -H ldap://localhost \
     -b "uid=john.doe,ou=users,dc=finance,dc=local" \
     -D "cn=admin,dc=finance,dc=local" \
     -w admin_password \
     userPassword
   ```

### Issue: Roles not visible in JWT

**Symptoms:**
- Token doesn't contain `roles` claim
- Authorization fails in backend

**Solutions:**
1. Check role mappers in client scope:
   - Clients → finans-frontend → Client scopes → finans-frontend-dedicated
   - Should have "realm-roles" mapper

2. Verify group-to-role mapping:
   - Groups → finance-users → Role mapping
   - Should have USER role assigned

3. Re-login to get new token with roles

4. Inspect token at https://jwt.io

### Issue: Spring Boot returns 401 Unauthorized

**Symptoms:**
- All protected endpoints return 401
- Valid token but still unauthorized

**Solutions:**
1. Check backend logs:
   ```bash
   docker-compose logs backend | grep -i "jwt\|oauth2\|security"
   ```

2. Verify issuer URI matches:
   - Backend: `http://keycloak:8080/realms/finans`
   - Token `iss` claim should match

3. Check JWK set URI is accessible from backend:
   ```bash
   docker exec finans-backend curl http://keycloak:8080/realms/finans/protocol/openid-connect/certs
   ```

4. Verify token is not expired

### Issue: Spring Boot returns 403 Forbidden

**Symptoms:**
- Authentication works (200 on some endpoints)
- Admin endpoints return 403

**Solutions:**
1. Check user roles in token

2. Verify SecurityConfig role requirements

3. Check role prefix (ROLE_ vs no prefix)

4. Verify JWT role converter is configured

### Issue: Frontend token missing or expired

**Symptoms:**
- API calls fail with 401
- Token not found in localStorage

**Solutions:**
1. Check Keycloak initialization:
   ```javascript
   console.log('Keycloak authenticated:', keycloak.authenticated);
   console.log('Token:', keycloak.token);
   ```

2. Verify token refresh:
   - Keycloak should auto-refresh tokens
   - Check `keycloak.updateToken(30)`

3. Check redirect URIs in Keycloak client:
   - Should include your frontend URL

4. Clear browser cache and re-login

## 📊 Monitoring

### Check LDAP Health

```bash
# Check LDAP service
docker-compose ps openldap

# Check LDAP logs
docker-compose logs -f openldap

# Test LDAP search
docker exec finans-openldap ldapsearch -x -H ldap://localhost \
  -b "dc=finance,dc=local" \
  -D "cn=admin,dc=finance,dc=local" \
  -w admin_password
```

### Check Keycloak Health

```bash
# Check Keycloak service
docker-compose ps keycloak

# Check Keycloak logs
docker-compose logs -f keycloak

# Test Keycloak health endpoint
curl http://localhost:8090/health
```

### Check User Sync Status

1. Go to Keycloak Admin Console
2. User Federation → LDAP provider
3. Check "Last sync" timestamp
4. Click "Synchronize all users" to force sync

## 🔒 Security Best Practices

### For Development:
- ✅ Use simple passwords for testing
- ✅ Use HTTP (not HTTPS) for local development
- ✅ Use phpLDAPadmin for easy management
- ✅ Keep credentials in documentation

### For Production:
- ❌ **NEVER** use default passwords
- ✅ Use strong, randomly generated passwords
- ✅ Enable TLS/SSL for LDAP (ldaps://)
- ✅ Enable HTTPS for Keycloak
- ✅ Use Kubernetes secrets or HashiCorp Vault
- ✅ Disable phpLDAPadmin
- ✅ Use READ_ONLY mode for LDAP
- ✅ Enable LDAP connection pooling
- ✅ Implement password policies
- ✅ Enable audit logging
- ✅ Regular security updates
- ✅ Network segmentation
- ✅ Implement rate limiting
- ✅ Use service accounts with minimal permissions

## 📚 Additional Resources

- [OpenLDAP Documentation](https://www.openldap.org/doc/)
- [Keycloak LDAP User Federation](https://www.keycloak.org/docs/latest/server_admin/#_ldap)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [JWT.io - Token Inspector](https://jwt.io)

## 🆘 Support

If you encounter issues not covered in this guide:

1. Check Docker logs: `docker-compose logs -f`
2. Check backend logs: `docker-compose logs backend`
3. Check Keycloak admin console for errors
4. Verify all services are healthy: `docker-compose ps`
5. Review this documentation carefully
6. Check network connectivity between containers

---

**Last Updated**: May 7, 2026
**Version**: 1.0.0


---

## ✅ FIXED: Backend 401 Unauthorized Issue (2026-05-07)

### Problem
Backend was returning 401 Unauthorized for all protected endpoints after Keycloak login.

### Root Cause
JWT issuer validation was failing because:
- Backend `application.yml` had wrong Keycloak port (8081 instead of 8090)
- JWT tokens from Keycloak had issuer: `http://localhost:8090/realms/finans`
- Backend was trying to validate against: `http://localhost:8081/realms/finans`
- Error in logs: "The iss claim is not valid"

### Solution
Updated `backend/src/main/resources/application.yml`:

**Default profile:**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:8090/realms/finans/protocol/openid-connect/certs
          issuer-uri: http://localhost:8090/realms/finans
```

**Docker profile:**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://keycloak:8080/realms/finans/protocol/openid-connect/certs
          issuer-uri: http://localhost:8090/realms/finans
```

### Verification
```powershell
# Rebuild and restart backend
docker-compose up -d --build backend

# Check backend is healthy
docker-compose ps backend

# Check for JWT errors (should be none)
docker-compose logs backend | Select-String "jwt|401|iss claim"
```

### Test
See `TEST_AUTH.md` for complete testing guide.

---

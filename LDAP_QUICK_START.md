# LDAP Integration - Quick Start Guide

**Status**: ✅ **WORKING** - OpenLDAP is running with all users and groups loaded!

---

## 🚀 Quick Start (3 Steps)

### Option A: Automated Start (Recommended)

```powershell
# Start everything with one command
.\start-with-ldap.ps1
```

This script will:
1. Start all Docker services
2. Wait for OpenLDAP to be ready
3. Load LDAP users and groups
4. Show service status and access URLs

### Option B: Manual Start

```powershell
# 1. Start all services
docker-compose up -d

# 2. Wait for OpenLDAP (40 seconds)
Start-Sleep -Seconds 40

# 3. Load LDAP data
.\load-ldap-data.ps1
```

---

## ✅ Current Status

All services are running:

| Service | Status | Port | URL |
|---------|--------|------|-----|
| **PostgreSQL** | ✅ Healthy | 5432 | - |
| **Keycloak** | ✅ Running | 8090 | http://localhost:8090 |
| **Backend** | ✅ Healthy | 8080 | http://localhost:8080 |
| **Frontend** | ✅ Healthy | 80 | http://localhost |
| **OpenLDAP** | ✅ Healthy | 389 | - |
| **phpLDAPadmin** | ✅ Running | 8089 | http://localhost:8089 |

### LDAP Data Loaded:
- ✅ 4 users (john.doe, jane.smith, test.user, admin.user)
- ✅ 2 groups (finance-users, finance-admins)
- ✅ All passwords configured
- ✅ Group memberships set

---

## 🔐 Test Credentials

### Regular Users (USER Role)
```
Username: john.doe
Password: password123
Email: john.doe@finance.local
Group: finance-users
```

```
Username: jane.smith
Password: password123
Email: jane.smith@finance.local
Group: finance-users
```

```
Username: test.user
Password: test123
Email: test.user@finance.local
Group: finance-users
```

### Admin User (ADMIN Role)
```
Username: admin.user
Password: admin123
Email: admin.user@finance.local
Group: finance-admins
```

### System Accounts
```
Keycloak Admin:
  Username: admin
  Password: admin
  URL: http://localhost:8090

LDAP Admin:
  DN: cn=admin,dc=finance,dc=local
  Password: admin_password
  URL: http://localhost:8089
```

---

## 📋 Next Steps

### Step 1: Verify LDAP Data (Optional)

Access phpLDAPadmin to browse LDAP directory:

1. Open: http://localhost:8089
2. Click "login"
3. Login DN: `cn=admin,dc=finance,dc=local`
4. Password: `admin_password`
5. Browse: `dc=finance,dc=local` → `ou=users` and `ou=groups`

### Step 2: Configure Keycloak LDAP User Federation ⚠️ **REQUIRED**

This step **MUST** be done manually in Keycloak Admin Console:

1. Open: http://localhost:8090
2. Click "Administration Console"
3. Login: `admin` / `admin`
4. Select realm: **finans** (top-left dropdown)
5. Click **User federation** in left menu
6. Click **Add LDAP provider**

**Configuration Values:**

| Setting | Value |
|---------|-------|
| Vendor | `Other` |
| Connection URL | `ldap://openldap:389` |
| Bind DN | `cn=admin,dc=finance,dc=local` |
| Bind Credential | `admin_password` |
| Users DN | `ou=users,dc=finance,dc=local` |
| Username LDAP attribute | `uid` |
| RDN LDAP attribute | `uid` |
| UUID LDAP attribute | `entryUUID` |
| User Object Classes | `inetOrgPerson, organizationalPerson, person, top` |
| Edit Mode | `READ_ONLY` |
| Import Users | `ON` |

7. Click **Test connection** (should succeed)
8. Click **Test authentication** (should succeed)
9. Click **Save**
10. Click **Synchronize all users** button

**For complete step-by-step instructions with screenshots, see `LDAP_SETUP.md` sections 4-7.**

### Step 3: Configure Role Mapping

After LDAP User Federation is configured:

1. Create realm roles: **USER** and **ADMIN**
2. Map LDAP groups to roles:
   - `finance-users` → `USER` role
   - `finance-admins` → `ADMIN` role
3. Configure client scope mappers to include roles in JWT

**Detailed instructions in `LDAP_SETUP.md` sections 6-7.**

### Step 4: Test Authentication

1. Open: http://localhost:8090/realms/finans/account
2. Click "Sign in"
3. Login with: `john.doe` / `password123`
4. Should successfully log in
5. Logout and try: `admin.user` / `admin123`

### Step 5: Test Frontend

1. Open: http://localhost
2. Login with `john.doe` / `password123`
3. Verify:
   - ❌ Admin menu NOT visible
   - ✅ Can view market data
   - ✅ Can view portfolio
4. Logout and login with `admin.user` / `admin123`
5. Verify:
   - ✅ Admin menu IS visible
   - ✅ Admin badge shows "👑 Admin"
   - ✅ Can access admin page

---

## 🧪 Testing Commands

### Test LDAP Connection
```powershell
docker exec finans-openldap ldapsearch -x -H ldap://localhost `
  -b "dc=finance,dc=local" `
  -D "cn=admin,dc=finance,dc=local" `
  -w admin_password
```

### Test User Authentication
```powershell
docker exec finans-openldap ldapwhoami -x -H ldap://localhost `
  -D "uid=john.doe,ou=users,dc=finance,dc=local" `
  -w password123
```

### Test Backend API
```powershell
# Public endpoint (no auth required)
curl http://localhost:8080/api/v1/market/instruments

# Protected endpoint (requires token)
$TOKEN = "your-jwt-token-here"
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/portfolio/positions

# Admin endpoint (requires ADMIN role)
$ADMIN_TOKEN = "admin-jwt-token-here"
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/v1/admin/me
```

---

## 🔧 Troubleshooting

### OpenLDAP Not Starting

```powershell
# Stop and clean
docker-compose stop openldap
docker rm finans-openldap
docker volume rm finans-portali_ldap_data finans-portali_ldap_config

# Start fresh
docker-compose up -d openldap

# Wait and load data
Start-Sleep -Seconds 40
.\load-ldap-data.ps1
```

### LDAP Data Not Loaded

```powershell
# Reload data
.\load-ldap-data.ps1
```

### Keycloak Cannot Connect to LDAP

- ✅ Use `ldap://openldap:389` (NOT `localhost`)
- ✅ Verify OpenLDAP is running: `docker-compose ps openldap`
- ✅ Test from Keycloak container:
  ```powershell
  docker exec finans-keycloak curl -v telnet://openldap:389
  ```

### Users Not Syncing

1. Verify LDAP data exists (see testing commands above)
2. Check Users DN: `ou=users,dc=finance,dc=local`
3. Click "Synchronize all users" in Keycloak
4. Check Keycloak logs: `docker-compose logs keycloak | Select-String "ldap"`

---

## 📚 Documentation

### Quick Reference (This File)
- **LDAP_QUICK_START.md** - You are here!

### Detailed Guides
- **LDAP_SETUP.md** - Complete setup with Keycloak configuration (800+ lines)
- **TEST_LDAP_INTEGRATION.md** - Comprehensive testing guide (900+ lines)
- **LDAP_IMPLEMENTATION_SUMMARY.md** - Architecture overview (600+ lines)

### Scripts
- **start-with-ldap.ps1** - Automated start script
- **load-ldap-data.ps1** - Load LDAP data only

---

## 🎯 Success Checklist

- [x] All Docker services running
- [x] OpenLDAP healthy
- [x] LDAP users loaded (4 users)
- [x] LDAP groups loaded (2 groups)
- [x] phpLDAPadmin accessible
- [ ] **MANUAL**: Keycloak LDAP User Federation configured
- [ ] **MANUAL**: Users synchronized from LDAP
- [ ] **MANUAL**: Groups mapped to roles
- [ ] **MANUAL**: Client scope mappers configured
- [ ] **MANUAL**: Authentication tested
- [ ] **MANUAL**: Frontend role-based UI tested

---

## 🆘 Need Help?

1. **Check logs**: `docker-compose logs -f [service-name]`
2. **Review documentation**: See `LDAP_SETUP.md` for detailed instructions
3. **Check troubleshooting**: See `TEST_LDAP_INTEGRATION.md` section
4. **Verify configuration**: Compare with this guide

---

## 🎉 What's Working Now

✅ **Infrastructure**
- OpenLDAP running and healthy
- All users and groups loaded
- phpLDAPadmin accessible

✅ **Backend**
- JWT role converter implemented
- Security configuration with role-based authorization
- Admin endpoints protected

✅ **Frontend**
- Role-based UI (admin menu visibility)
- Admin panel page
- Admin badge for admin users

⚠️ **Requires Manual Configuration**
- Keycloak LDAP User Federation (see Step 2 above)
- Role mapping (see Step 3 above)

---

**Last Updated**: May 7, 2026  
**Status**: ✅ OpenLDAP Running with Data Loaded  
**Next Step**: Configure Keycloak LDAP User Federation (see `LDAP_SETUP.md`)


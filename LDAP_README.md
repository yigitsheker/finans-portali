# LDAP Integration - Quick Reference

## 🚀 Quick Start

```powershell
# Start all services (including LDAP)
.\start-ldap.ps1

# Or manually:
docker-compose up -d
```

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| **LDAP_SETUP.md** | Complete setup guide with Keycloak configuration |
| **TEST_LDAP_INTEGRATION.md** | Step-by-step testing procedures |
| **LDAP_IMPLEMENTATION_SUMMARY.md** | Implementation overview and architecture |

## 🔑 Test Credentials

### LDAP Users
- **Regular User**: `john.doe` / `password123`
- **Admin User**: `admin.user` / `admin123`

### System Accounts
- **Keycloak Admin**: `admin` / `admin`
- **LDAP Admin**: `cn=admin,dc=finance,dc=local` / `admin_password`

## 🌐 Access URLs

- **Frontend**: http://localhost
- **Backend**: http://localhost:8080
- **Keycloak**: http://localhost:8090
- **phpLDAPadmin**: http://localhost:8089

## ⚙️ Configuration Required

After starting services, you MUST configure Keycloak LDAP User Federation:

1. Open http://localhost:8090
2. Login as `admin` / `admin`
3. Select `finans` realm
4. Follow **LDAP_SETUP.md** sections 4-7

## 🧪 Testing

Follow **TEST_LDAP_INTEGRATION.md** for complete testing procedures.

Quick test:
1. Configure Keycloak LDAP (see above)
2. Open http://localhost
3. Login with `john.doe` / `password123`
4. Verify you can access portfolio
5. Verify you CANNOT see admin menu
6. Logout and login with `admin.user` / `admin123`
7. Verify you CAN see admin menu

## 🔒 Security

⚠️ **WARNING**: Current credentials are for DEVELOPMENT ONLY!

For production:
- Change ALL default passwords
- Enable TLS/SSL for LDAP
- Enable HTTPS for all services
- Use secrets management
- Disable phpLDAPadmin
- Follow security checklist in LDAP_IMPLEMENTATION_SUMMARY.md

## 🐛 Troubleshooting

### Services won't start
```powershell
docker-compose down -v
docker-compose up -d
```

### Keycloak can't connect to LDAP
- Verify OpenLDAP is running: `docker-compose ps openldap`
- Check logs: `docker-compose logs openldap`
- Use Docker service name: `ldap://openldap:389` (NOT localhost)

### Users not syncing
- Verify LDAP data: See TEST_LDAP_INTEGRATION.md Test 2
- Check Keycloak configuration
- Click "Synchronize all users" in Keycloak

### Roles not in JWT
- Verify client scope mappers in Keycloak
- Check group-to-role mapping
- Re-login to get fresh token

## 📊 Architecture

```
Browser → Keycloak → OpenLDAP (authentication)
   ↓
Backend (JWT validation + role check)
```

## 🎯 Features

- ✅ LDAP user storage
- ✅ Keycloak authentication
- ✅ JWT tokens with roles
- ✅ Role-based backend authorization
- ✅ Role-based frontend UI
- ✅ Admin panel for administrators
- ✅ User/Admin role separation

## 📞 Support

1. Check documentation (LDAP_SETUP.md, TEST_LDAP_INTEGRATION.md)
2. Check logs: `docker-compose logs -f`
3. Verify configuration against LDAP_IMPLEMENTATION_SUMMARY.md

---

**Version**: 1.0.0
**Last Updated**: May 7, 2026

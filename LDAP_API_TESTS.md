# API Testing Guide for LDAP Integration

This guide provides curl commands and Postman examples for testing the LDAP-integrated Finance Portal API.

## 📋 Prerequisites

1. All services running: `docker-compose up -d`
2. Keycloak LDAP configured (see LDAP_SETUP.md)
3. Users synchronized from LDAP

## 🔑 Getting Access Tokens

### Method 1: Via Browser (Recommended)

1. Open http://localhost (frontend)
2. Login with test user
3. Open DevTools (F12)
4. Go to Application → Local Storage → http://localhost:8090
5. Copy the access token value
6. Use in API calls as `Authorization: Bearer <token>`

### Method 2: Via Keycloak Direct Grant (Password Flow)

⚠️ **Note**: This requires enabling "Direct Access Grants" in Keycloak client settings.

```bash
# Get token for regular user
curl -X POST "http://localhost:8090/realms/finans/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=finans-frontend" \
  -d "username=john.doe" \
  -d "password=password123" \
  -d "grant_type=password"

# Response will contain access_token
# Copy the access_token value for use in API calls
```

```bash
# Get token for admin user
curl -X POST "http://localhost:8090/realms/finans/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=finans-frontend" \
  -d "username=admin.user" \
  -d "password=admin123" \
  -d "grant_type=password"
```

## 🧪 API Test Cases

### Test 1: Public Endpoints (No Authentication)

These endpoints should work without any token.

```bash
# Get market instruments
curl http://localhost:8080/api/v1/market/instruments

# Expected: 200 OK with JSON array of instruments

# Get news articles
curl http://localhost:8080/api/v1/news/articles

# Expected: 200 OK with JSON array of news

# Get exchange rates
curl http://localhost:8080/api/v1/exchange-rates

# Expected: 200 OK with JSON array of rates

# Get investment funds
curl http://localhost:8080/api/v1/funds

# Expected: 200 OK with JSON array of funds

# Health check
curl http://localhost:8080/actuator/health

# Expected: 200 OK with health status
```

### Test 2: Protected Endpoints - Regular User

Replace `<USER_TOKEN>` with actual token from john.doe.

```bash
# Set token variable (PowerShell)
$USER_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

# Or (Bash)
export USER_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

```bash
# Get portfolio positions
curl -H "Authorization: Bearer $USER_TOKEN" \
  http://localhost:8080/api/v1/portfolio/positions

# Expected: 200 OK with portfolio data (may be empty array)

# Get portfolio summary
curl -H "Authorization: Bearer $USER_TOKEN" \
  http://localhost:8080/api/v1/portfolio/summary

# Expected: 200 OK with summary data

# Get price alerts
curl -H "Authorization: Bearer $USER_TOKEN" \
  http://localhost:8080/api/v1/alerts

# Expected: 200 OK with alerts array

# Create price alert
curl -X POST \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "THYAO.IS",
    "targetPrice": 100.0,
    "alertType": "ABOVE",
    "note": "Test alert"
  }' \
  http://localhost:8080/api/v1/alerts

# Expected: 201 Created with alert data
```

### Test 3: Admin Endpoints - Regular User (Should Fail)

Regular user should NOT be able to access admin endpoints.

```bash
# Try to access admin info (should fail)
curl -H "Authorization: Bearer $USER_TOKEN" \
  http://localhost:8080/api/v1/admin/me

# Expected: 403 Forbidden

# Try to reset market data (should fail)
curl -X POST \
  -H "Authorization: Bearer $USER_TOKEN" \
  http://localhost:8080/api/v1/admin/reset-market

# Expected: 403 Forbidden

# Try to refresh prices (should fail)
curl -X POST \
  -H "Authorization: Bearer $USER_TOKEN" \
  http://localhost:8080/api/v1/admin/refresh-prices

# Expected: 403 Forbidden
```

### Test 4: Admin Endpoints - Admin User (Should Succeed)

Replace `<ADMIN_TOKEN>` with actual token from admin.user.

```bash
# Set admin token variable (PowerShell)
$ADMIN_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."

# Or (Bash)
export ADMIN_TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

```bash
# Get admin user info
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/admin/me

# Expected: 200 OK with JSON:
# {
#   "userId": "...",
#   "username": "admin.user",
#   "email": "admin.user@finance.local",
#   "fullName": "Admin User",
#   "roles": ["ADMIN"],
#   "isAdmin": true
# }

# Reset market data
curl -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/admin/reset-market

# Expected: 200 OK with message "Market data cleared and re-seeded by admin.user"

# Refresh prices
curl -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/admin/refresh-prices

# Expected: 200 OK with message "Price refresh triggered by admin.user"

# Reset news
curl -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/admin/reset-news

# Expected: 200 OK with message "News cleared and fetch triggered by admin.user"
```

### Test 5: Unauthorized Access (No Token)

Protected endpoints should return 401 without token.

```bash
# Try to access portfolio without token
curl http://localhost:8080/api/v1/portfolio/positions

# Expected: 401 Unauthorized

# Try to access admin endpoint without token
curl http://localhost:8080/api/v1/admin/me

# Expected: 401 Unauthorized
```

### Test 6: Expired Token

```bash
# Use an old/expired token
curl -H "Authorization: Bearer expired_token_here" \
  http://localhost:8080/api/v1/portfolio/positions

# Expected: 401 Unauthorized with message about invalid/expired token
```

## 📦 Postman Collection

### Import into Postman

Create a new collection with these requests:

#### Environment Variables

Create a Postman environment with:

```json
{
  "base_url": "http://localhost:8080",
  "keycloak_url": "http://localhost:8090",
  "user_token": "",
  "admin_token": ""
}
```

#### Collection Structure

```
Finance Portal API Tests
├── Auth
│   ├── Get User Token (john.doe)
│   └── Get Admin Token (admin.user)
├── Public Endpoints
│   ├── GET Market Instruments
│   ├── GET News Articles
│   ├── GET Exchange Rates
│   └── GET Health Check
├── User Endpoints (Regular User)
│   ├── GET Portfolio Positions
│   ├── GET Portfolio Summary
│   ├── GET Price Alerts
│   └── POST Create Price Alert
├── Admin Endpoints (Regular User - Should Fail)
│   ├── GET Admin Info (403)
│   ├── POST Reset Market (403)
│   └── POST Refresh Prices (403)
└── Admin Endpoints (Admin User - Should Succeed)
    ├── GET Admin Info
    ├── POST Reset Market
    ├── POST Refresh Prices
    └── POST Reset News
```

### Sample Postman Request: Get User Token

```
POST {{keycloak_url}}/realms/finans/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

Body (x-www-form-urlencoded):
client_id: finans-frontend
username: john.doe
password: password123
grant_type: password

Tests (JavaScript):
var jsonData = pm.response.json();
pm.environment.set("user_token", jsonData.access_token);
```

### Sample Postman Request: Get Admin Info

```
GET {{base_url}}/api/v1/admin/me
Authorization: Bearer {{admin_token}}

Tests (JavaScript):
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("User is admin", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.isAdmin).to.eql(true);
    pm.expect(jsonData.roles).to.include("ADMIN");
});
```

## 🔍 Token Inspection

### Decode JWT Token

1. Copy your access token
2. Go to https://jwt.io
3. Paste token in "Encoded" section
4. Inspect decoded payload

### Expected Token Structure

**Regular User (john.doe):**
```json
{
  "exp": 1715123456,
  "iat": 1715123156,
  "jti": "...",
  "iss": "http://localhost:8090/realms/finans",
  "aud": "account",
  "sub": "user-uuid",
  "typ": "Bearer",
  "azp": "finans-frontend",
  "session_state": "...",
  "acr": "1",
  "realm_access": {
    "roles": ["USER"]
  },
  "scope": "openid profile email",
  "sid": "...",
  "email_verified": false,
  "name": "John Doe",
  "preferred_username": "john.doe",
  "given_name": "John",
  "family_name": "Doe",
  "email": "john.doe@finance.local",
  "roles": ["USER"]
}
```

**Admin User (admin.user):**
```json
{
  "exp": 1715123456,
  "iat": 1715123156,
  "jti": "...",
  "iss": "http://localhost:8090/realms/finans",
  "aud": "account",
  "sub": "admin-uuid",
  "typ": "Bearer",
  "azp": "finans-frontend",
  "session_state": "...",
  "acr": "1",
  "realm_access": {
    "roles": ["ADMIN"]
  },
  "scope": "openid profile email",
  "sid": "...",
  "email_verified": false,
  "name": "Admin User",
  "preferred_username": "admin.user",
  "given_name": "Admin",
  "family_name": "User",
  "email": "admin.user@finance.local",
  "roles": ["ADMIN"]
}
```

## 📊 Test Results Template

Use this template to document your test results:

```
API Testing Results
Date: __________
Tester: __________

Public Endpoints:
[ ] GET /api/v1/market/instruments - Status: ___
[ ] GET /api/v1/news/articles - Status: ___
[ ] GET /api/v1/exchange-rates - Status: ___
[ ] GET /actuator/health - Status: ___

User Endpoints (john.doe):
[ ] GET /api/v1/portfolio/positions - Status: ___
[ ] GET /api/v1/portfolio/summary - Status: ___
[ ] GET /api/v1/alerts - Status: ___
[ ] POST /api/v1/alerts - Status: ___

Admin Endpoints (john.doe - should fail):
[ ] GET /api/v1/admin/me - Status: ___ (Expected: 403)
[ ] POST /api/v1/admin/reset-market - Status: ___ (Expected: 403)
[ ] POST /api/v1/admin/refresh-prices - Status: ___ (Expected: 403)

Admin Endpoints (admin.user - should succeed):
[ ] GET /api/v1/admin/me - Status: ___ (Expected: 200)
[ ] POST /api/v1/admin/reset-market - Status: ___ (Expected: 200)
[ ] POST /api/v1/admin/refresh-prices - Status: ___ (Expected: 200)
[ ] POST /api/v1/admin/reset-news - Status: ___ (Expected: 200)

Unauthorized Access:
[ ] GET /api/v1/portfolio/positions (no token) - Status: ___ (Expected: 401)
[ ] GET /api/v1/admin/me (no token) - Status: ___ (Expected: 401)

Overall Result: [ ] PASS [ ] FAIL
Notes: _______________________________________________
```

## 🐛 Troubleshooting

### 401 Unauthorized

**Possible Causes:**
- Token is missing
- Token is expired
- Token is invalid
- Issuer URI mismatch

**Solutions:**
1. Get a fresh token
2. Verify token at jwt.io
3. Check backend logs: `docker-compose logs backend | grep -i jwt`
4. Verify issuer URI in backend matches token `iss` claim

### 403 Forbidden

**Possible Causes:**
- User doesn't have required role
- Role not in JWT token
- Role prefix mismatch

**Solutions:**
1. Verify token contains correct roles at jwt.io
2. Check Keycloak group-to-role mapping
3. Check client scope mappers
4. Re-login to get fresh token with roles

### 500 Internal Server Error

**Possible Causes:**
- Backend error
- Database connection issue
- Configuration error

**Solutions:**
1. Check backend logs: `docker-compose logs backend`
2. Verify database is running: `docker-compose ps postgres`
3. Check backend health: `curl http://localhost:8080/actuator/health`

## 📚 Additional Resources

- **Keycloak Token Endpoint**: http://localhost:8090/realms/finans/protocol/openid-connect/token
- **Keycloak Certs Endpoint**: http://localhost:8090/realms/finans/protocol/openid-connect/certs
- **Backend Swagger**: http://localhost:8080/swagger-ui.html
- **JWT Decoder**: https://jwt.io

---

**Version**: 1.0.0
**Last Updated**: May 7, 2026

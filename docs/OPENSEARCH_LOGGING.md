# OpenSearch Centralized Logging

## 📊 Architecture Overview

```
┌─────────────────────┐
│  Spring Boot        │
│  Backend            │
│  (Logback JSON)     │
└──────────┬──────────┘
           │ writes to
           ▼
┌─────────────────────┐
│  /app/logs/         │
│  application.json   │
└──────────┬──────────┘
           │ reads from
           ▼
┌─────────────────────┐
│  Fluent Bit         │
│  (Log Shipper)      │
└──────────┬──────────┘
           │ ships to
           ▼
┌─────────────────────┐
│  OpenSearch         │
│  (Log Storage)      │
└──────────┬──────────┘
           │ visualize in
           ▼
┌─────────────────────┐
│  OpenSearch         │
│  Dashboards         │
└─────────────────────┘
```

## 🚀 Quick Start

### 1. Start All Services

```powershell
# Start all services including OpenSearch
docker-compose up -d

# Check all services are running
docker-compose ps

# Expected services:
# - finans-backend (port 8080)
# - finans-opensearch (port 9200)
# - finans-opensearch-dashboards (port 5601)
# - finans-fluent-bit (no exposed port)
# - finans-postgres, finans-keycloak, etc.
```

### 2. Verify OpenSearch

```powershell
# Check OpenSearch health
curl http://localhost:9200/_cluster/health

# Expected response:
# {"cluster_name":"docker-cluster","status":"green",...}
```

### 3. Access OpenSearch Dashboards

1. Open browser: **http://localhost:5601**
2. Wait for Dashboards to load (may take 30-60 seconds on first start)

### 4. Create Index Pattern

1. Go to **Management** → **Stack Management** → **Index Patterns**
2. Click **Create index pattern**
3. Index pattern name: `finans-portal-logs-*`
4. Click **Next step**
5. Time field: `@timestamp`
6. Click **Create index pattern**

### 5. View Logs

1. Go to **Discover** (left sidebar)
2. Select index pattern: `finans-portal-logs-*`
3. You should see logs flowing in!

---

## 📝 Log Format

### JSON Log Structure

Each log entry is a JSON object with the following fields:

```json
{
  "@timestamp": "2026-05-08T10:30:45.123Z",
  "level": "INFO",
  "logger_name": "com.finansportali.backend.service.PortfolioService",
  "thread_name": "http-nio-8080-exec-5",
  "message": "Portfolio operation completed - Action: UPSERT - Symbol: AAPL - NewQuantity: 10 - AvgCost: 150.50 - UserId: user-123",
  "service": "finans-backend",
  "environment": "prod",
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "userId": "user-uuid-123",
  "username": "john.doe",
  "endpoint": "/api/v1/portfolio/positions",
  "httpMethod": "POST",
  "statusCode": "200",
  "durationMs": "45",
  "clientIp": "172.18.0.1",
  "traceId": "trace-abc123",
  "spanId": "span-def456"
}
```

### Key Fields Explained

| Field | Description | Example |
|-------|-------------|---------|
| `@timestamp` | Log timestamp (ISO 8601) | `2026-05-08T10:30:45.123Z` |
| `level` | Log level | `INFO`, `WARN`, `ERROR` |
| `logger_name` | Java class that logged | `com.finansportali.backend.service.PortfolioService` |
| `message` | Log message | `Portfolio operation completed...` |
| `service` | Service name | `finans-backend` |
| `environment` | Environment | `dev`, `prod` |
| `requestId` | Correlation ID (X-Request-ID) | `a1b2c3d4-...` |
| `userId` | Authenticated user ID (from JWT) | `user-uuid-123` |
| `username` | Username (from JWT) | `john.doe` |
| `endpoint` | API endpoint | `/api/v1/portfolio/positions` |
| `httpMethod` | HTTP method | `GET`, `POST`, `DELETE` |
| `statusCode` | HTTP status code | `200`, `401`, `500` |
| `durationMs` | Request duration in milliseconds | `45` |
| `clientIp` | Client IP address | `172.18.0.1` |
| `traceId` | Distributed tracing ID | `trace-abc123` |
| `spanId` | Span ID for tracing | `span-def456` |

---

## 🔍 Searching Logs

### Basic Searches

#### 1. Find all ERROR logs
```
level: ERROR
```

#### 2. Find logs for specific user
```
username: "john.doe"
```

#### 3. Find logs for specific endpoint
```
endpoint: "/api/v1/portfolio/positions"
```

#### 4. Find slow requests (> 1000ms)
```
durationMs: >1000
```

#### 5. Find logs by correlation ID
```
requestId: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

### Advanced Searches

#### 1. Portfolio operations by user
```
message: "Portfolio operation" AND username: "john.doe"
```

#### 2. Failed requests (4xx, 5xx)
```
statusCode: [400 TO 599]
```

#### 3. Authentication errors
```
level: ERROR AND (message: "Access denied" OR message: "Unauthorized")
```

#### 4. Market data refresh operations
```
message: "Market data" OR message: "Price refresh"
```

#### 5. Requests from specific IP
```
clientIp: "172.18.0.1"
```

---

## 📊 Business Events Logged

### Portfolio Operations

#### Position Added/Updated
```json
{
  "level": "INFO",
  "message": "Portfolio operation started - Action: UPSERT - Symbol: AAPL - Quantity: 10 - UserId: user-123"
}
```

```json
{
  "level": "INFO",
  "message": "Portfolio operation completed - Action: UPSERT - Symbol: AAPL - NewQuantity: 10 - AvgCost: 150.50 - UserId: user-123"
}
```

#### Position Deleted
```json
{
  "level": "INFO",
  "message": "Portfolio operation started - Action: DELETE - Symbol: AAPL - UserId: user-123"
}
```

```json
{
  "level": "INFO",
  "message": "Portfolio operation completed - Action: DELETE - Symbol: AAPL - UserId: user-123"
}
```

#### Position Sold
```json
{
  "level": "INFO",
  "message": "Portfolio operation started - Action: SELL - Symbol: AAPL - Quantity: 5 - UserId: user-123"
}
```

```json
{
  "level": "INFO",
  "message": "Portfolio operation completed - Action: SELL - Symbol: AAPL - Quantity: 5 - Proceeds: 755.00 - RemainingQuantity: 5 - UserId: user-123"
}
```

### HTTP Requests

#### Successful Request
```json
{
  "level": "INFO",
  "message": "HTTP Request: POST /api/v1/portfolio/positions from 172.18.0.1"
}
```

```json
{
  "level": "INFO",
  "message": "HTTP Response: POST /api/v1/portfolio/positions - Status: 200 - Duration: 45ms"
}
```

#### Failed Request
```json
{
  "level": "WARN",
  "message": "HTTP Response: GET /api/v1/portfolio/positions - Status: 401 - Duration: 12ms"
}
```

### Exceptions

#### Validation Error
```json
{
  "level": "WARN",
  "message": "Validation error: 2 fields failed - Endpoint: /api/v1/portfolio/positions - RequestId: a1b2c3d4-..."
}
```

#### Access Denied
```json
{
  "level": "WARN",
  "message": "Access denied: Access is denied - Endpoint: /api/v1/admin/users - RequestId: a1b2c3d4-..."
}
```

#### Unexpected Error
```json
{
  "level": "ERROR",
  "message": "Unexpected error occurred - Endpoint: /api/v1/portfolio/positions - RequestId: a1b2c3d4-... - Error: NullPointerException",
  "stack_trace": "java.lang.NullPointerException: ...\n\tat com.finansportali..."
}
```

---

## 🛠️ Configuration

### Environment Variables

Backend supports these logging-related environment variables:

```yaml
# docker-compose.yml
environment:
  - SPRING_APPLICATION_NAME=finans-backend
  - SPRING_PROFILES_ACTIVE=prod
  - LOGGING_LEVEL_ROOT=INFO
  - LOGGING_LEVEL_COM_FINANSPORTALI=INFO
```

### Log Levels

Available log levels (from most to least verbose):
- `TRACE` - Very detailed, usually only for debugging
- `DEBUG` - Detailed information for debugging
- `INFO` - General informational messages (default)
- `WARN` - Warning messages
- `ERROR` - Error messages

### Changing Log Levels

#### Temporarily (via Actuator)
```powershell
# Set root level to DEBUG
curl -X POST http://localhost:8080/actuator/loggers/ROOT \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'

# Set specific package to DEBUG
curl -X POST http://localhost:8080/actuator/loggers/com.finansportali.backend.service \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'
```

#### Permanently (via application.yml)
```yaml
logging:
  level:
    root: INFO
    com.finansportali: DEBUG
    org.springframework.web: DEBUG
```

---

## 🔐 Security & Privacy

### What We DON'T Log

To protect user privacy and security:

❌ **Never logged:**
- Passwords
- JWT tokens (full token)
- Authorization headers
- Refresh tokens
- Credit card numbers
- Social security numbers
- Private user data (unless explicitly needed)

✅ **What we DO log:**
- User ID (UUID from JWT)
- Username (from JWT)
- Request paths
- HTTP methods
- Status codes
- Durations
- Error messages (sanitized)

### Sensitive Data Handling

If you need to log potentially sensitive data:

```java
// ❌ BAD - logs full token
log.info("Token: {}", jwtToken);

// ✅ GOOD - logs only user ID
log.info("User authenticated: userId={}", userId);

// ❌ BAD - logs password
log.info("Login attempt: username={}, password={}", username, password);

// ✅ GOOD - logs only username
log.info("Login attempt: username={}", username);
```

---

## 📈 Monitoring & Alerts

### Useful Dashboards

#### 1. Error Rate Dashboard
- Query: `level: ERROR`
- Visualization: Line chart over time
- Alert: If error count > 10 in 5 minutes

#### 2. Slow Requests Dashboard
- Query: `durationMs: >1000`
- Visualization: Table with endpoint, duration, user
- Alert: If slow request count > 5 in 5 minutes

#### 3. User Activity Dashboard
- Query: `username: *`
- Visualization: Pie chart by username
- Shows most active users

#### 4. Endpoint Usage Dashboard
- Query: `endpoint: *`
- Visualization: Bar chart by endpoint
- Shows most used endpoints

### Creating Alerts (Future Enhancement)

OpenSearch supports alerting via plugins. To set up alerts:

1. Install OpenSearch Alerting plugin
2. Create monitors for specific conditions
3. Configure destinations (email, Slack, webhook)
4. Set up alert triggers

---

## 🐛 Troubleshooting

### Problem: OpenSearch container exits immediately

**Symptoms:**
```powershell
docker-compose ps
# finans-opensearch   Exit 78
```

**Cause:** Insufficient memory or vm.max_map_count too low

**Solution (Windows with WSL2):**
```powershell
# In WSL2 terminal
wsl -d docker-desktop
sysctl -w vm.max_map_count=262144

# Or add to /etc/sysctl.conf permanently
echo "vm.max_map_count=262144" >> /etc/sysctl.conf
```

**Solution (Linux):**
```bash
sudo sysctl -w vm.max_map_count=262144
```

---

### Problem: Backend can't connect to OpenSearch

**Symptoms:**
```
Backend logs: Connection refused: opensearch:9200
```

**Cause:** Backend trying to connect before OpenSearch is ready

**Solution:**
```powershell
# Check OpenSearch health
curl http://localhost:9200/_cluster/health

# Restart backend after OpenSearch is healthy
docker-compose restart backend
```

---

### Problem: No logs appearing in OpenSearch

**Symptoms:**
- OpenSearch Dashboards shows no data
- Index pattern not found

**Checklist:**
1. ✅ Check backend is writing logs:
   ```powershell
   docker-compose exec backend ls -la /app/logs/
   # Should see application.json
   ```

2. ✅ Check Fluent Bit is running:
   ```powershell
   docker-compose ps fluent-bit
   # Should be "Up"
   ```

3. ✅ Check Fluent Bit logs:
   ```powershell
   docker-compose logs fluent-bit
   # Should see "output:opensearch" messages
   ```

4. ✅ Check OpenSearch indices:
   ```powershell
   curl http://localhost:9200/_cat/indices?v
   # Should see finans-portal-logs-YYYY.MM.DD
   ```

5. ✅ Trigger some backend activity:
   ```powershell
   curl http://localhost:8080/api/v1/market/summary
   ```

---

### Problem: JSON logs are malformed

**Symptoms:**
```
Fluent Bit logs: [error] [parser] cannot parse log
```

**Cause:** Logback encoder not producing valid JSON

**Solution:**
1. Check `logback-spring.xml` has `LogstashEncoder`
2. Verify `logstash-logback-encoder` dependency in `pom.xml`
3. Check log file manually:
   ```powershell
   docker-compose exec backend cat /app/logs/application.json | head -1
   # Should be valid JSON
   ```

---

### Problem: Duplicate logs appearing

**Symptoms:**
- Same log appears multiple times in OpenSearch

**Cause:** Multiple appenders or Fluent Bit reading same file twice

**Solution:**
1. Check `logback-spring.xml` - should have only one JSON_FILE appender
2. Check Fluent Bit config - should have only one INPUT section
3. Restart Fluent Bit:
   ```powershell
   docker-compose restart fluent-bit
   ```

---

### Problem: Time field not recognized in Dashboards

**Symptoms:**
- Can't create index pattern
- Error: "No time field found"

**Cause:** `@timestamp` field missing or wrong format

**Solution:**
1. Check log format includes `@timestamp`
2. Verify Fluent Bit is adding timestamp:
   ```powershell
   curl http://localhost:9200/finans-portal-logs-*/_search?size=1
   # Check if @timestamp exists
   ```

3. If missing, update Fluent Bit config to add timestamp

---

### Problem: Can't access OpenSearch Dashboards

**Symptoms:**
```
Browser: ERR_CONNECTION_REFUSED at http://localhost:5601
```

**Checklist:**
1. ✅ Check Dashboards container is running:
   ```powershell
   docker-compose ps opensearch-dashboards
   ```

2. ✅ Check Dashboards logs:
   ```powershell
   docker-compose logs opensearch-dashboards
   # Look for "Server running at http://0.0.0.0:5601"
   ```

3. ✅ Wait longer - Dashboards takes 30-60 seconds to start

4. ✅ Check OpenSearch is healthy first:
   ```powershell
   curl http://localhost:9200/_cluster/health
   ```

---

## 🚀 Production Considerations

### 1. Enable OpenSearch Security

**Development (current):**
```yaml
environment:
  - DISABLE_SECURITY_PLUGIN=true
```

**Production:**
```yaml
environment:
  - DISABLE_SECURITY_PLUGIN=false
  - OPENSEARCH_INITIAL_ADMIN_PASSWORD=StrongPassword123!
```

Then configure authentication in backend and Fluent Bit.

---

### 2. Use HTTPS

**Production:**
```yaml
opensearch:
  environment:
    - plugins.security.ssl.http.enabled=true
    - plugins.security.ssl.http.pemcert_filepath=certs/opensearch.pem
    - plugins.security.ssl.http.pemkey_filepath=certs/opensearch-key.pem
```

---

### 3. Configure Index Lifecycle Management (ILM)

**Purpose:** Automatically delete old logs to save disk space

**Example Policy:**
- Keep logs for 30 days
- Delete indices older than 30 days
- Rollover daily

**Setup:**
1. Create ILM policy in OpenSearch
2. Apply policy to index template
3. Logs will be automatically managed

---

### 4. Adjust Log Levels

**Production:**
```yaml
logging:
  level:
    root: WARN
    com.finansportali: INFO
    org.springframework.web: WARN
    org.hibernate.SQL: WARN  # Don't log SQL in production
```

---

### 5. Use Persistent Volumes

**Production docker-compose.yml:**
```yaml
volumes:
  opensearch_data:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /data/opensearch  # Persistent storage path
```

---

### 6. Resource Limits

**Production:**
```yaml
opensearch:
  deploy:
    resources:
      limits:
        memory: 2G
      reservations:
        memory: 1G
  environment:
    - "OPENSEARCH_JAVA_OPTS=-Xms1g -Xmx1g"
```

---

### 7. Consider Kafka for High Volume

If you have **high log volume** (>10,000 logs/second):

```
Backend → Kafka → Log Consumer → OpenSearch
```

**Benefits:**
- Buffering during OpenSearch downtime
- Horizontal scaling
- Multiple consumers

---

### 8. Mask Sensitive Fields

Add a filter in Fluent Bit to mask sensitive data:

```conf
[FILTER]
    Name    modify
    Match   finans.*
    Condition Key_value_matches message password
    Set     message [REDACTED]
```

---

## 📚 Additional Resources

### OpenSearch Documentation
- [OpenSearch Official Docs](https://opensearch.org/docs/latest/)
- [OpenSearch Dashboards Guide](https://opensearch.org/docs/latest/dashboards/)
- [Query DSL Reference](https://opensearch.org/docs/latest/query-dsl/)

### Fluent Bit Documentation
- [Fluent Bit Official Docs](https://docs.fluentbit.io/)
- [OpenSearch Output Plugin](https://docs.fluentbit.io/manual/pipeline/outputs/opensearch)
- [Tail Input Plugin](https://docs.fluentbit.io/manual/pipeline/inputs/tail)

### Logback Documentation
- [Logback Official Docs](https://logback.qos.ch/documentation.html)
- [Logstash Encoder](https://github.com/logfellow/logstash-logback-encoder)

---

## 🎯 Summary

### What We Implemented

✅ **Infrastructure:**
- OpenSearch for log storage
- OpenSearch Dashboards for visualization
- Fluent Bit for log shipping

✅ **Backend Logging:**
- JSON structured logging
- Correlation IDs (X-Request-ID)
- Request/Response logging
- Business event logging
- Enhanced exception logging

✅ **Security:**
- No sensitive data logged
- User privacy protected
- Sanitized error messages

✅ **Monitoring:**
- Searchable logs
- Filterable by user, endpoint, status
- Traceable requests via correlation ID

### Access Points

| Service | URL | Purpose |
|---------|-----|---------|
| Backend | http://localhost:8080 | API endpoints |
| OpenSearch | http://localhost:9200 | Log storage API |
| OpenSearch Dashboards | http://localhost:5601 | Log visualization |

### Quick Commands

```powershell
# Start everything
docker-compose up -d

# View backend logs (console)
docker-compose logs -f backend

# View Fluent Bit logs
docker-compose logs -f fluent-bit

# Check OpenSearch health
curl http://localhost:9200/_cluster/health

# List indices
curl http://localhost:9200/_cat/indices?v

# Search logs via API
curl "http://localhost:9200/finans-portal-logs-*/_search?q=level:ERROR&size=10"

# Stop everything
docker-compose down
```

---

**Status:** ✅ **COMPLETE**
**Version:** 1.0.0
**Last Updated:** 2026-05-08

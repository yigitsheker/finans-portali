# 🚀 OpenTelemetry Observability - Quick Start

## ⚡ 5-Minute Setup

### Step 1: Start All Services (2 minutes)

```powershell
cd C:\Users\yigid\Desktop\finans-portali
docker compose up -d
```

**Wait for services to be healthy:**
```powershell
docker compose ps
```

All services should show `healthy` status.

### Step 2: Run Test Script (1 minute)

```powershell
.\test-observability.ps1
```

This script will:
- ✅ Check if all services are running
- ✅ Generate test traffic
- ✅ Verify traces in Jaeger
- ✅ Verify metrics in Prometheus
- ✅ Check Grafana datasources

### Step 3: View Results (2 minutes)

**Jaeger (Traces):**
```
http://localhost:16686
```
1. Select Service: `finans-backend`
2. Click "Find Traces"
3. Click on a trace to see details

**Prometheus (Metrics):**
```
http://localhost:9090
```
1. Query: `http_server_requests_seconds_count{job="finans-backend"}`
2. Click "Execute"
3. See request counts

**Grafana (Dashboards):**
```
http://localhost:3000
```
- Username: `admin`
- Password: `admin`

1. Go to "Explore"
2. Select "Prometheus"
3. Run queries

---

## 🎯 What You Get

### Distributed Tracing

**Every HTTP request creates a trace showing:**
- Request flow (HTTP → Database → External APIs)
- Duration of each operation
- Errors and exceptions
- Database queries
- External API calls

**Example Trace:**
```
GET /api/v1/market/instruments [200ms]
  ├─ SELECT from market_instrument [50ms]
  ├─ SELECT from market_quote [30ms]
  └─ HTTP GET yahoo-finance [100ms]
```

### Metrics

**Automatic metrics collected:**
- HTTP request count & duration
- HTTP error rate
- JVM memory usage
- JVM garbage collection
- Database connection pool
- System CPU usage

**Example Queries:**
```promql
# Total HTTP requests
http_server_requests_seconds_count{job="finans-backend"}

# 95th percentile response time
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
```

### Log Correlation

**Logs now include trace context:**
```json
{
  "timestamp": "2026-05-08T12:00:00.000Z",
  "level": "INFO",
  "trace_id": "a1b2c3d4e5f6...",
  "span_id": "1234567890ab...",
  "message": "Processing request"
}
```

**Find logs for a specific trace:**
1. Copy `trace_id` from Jaeger
2. Open OpenSearch Dashboards: http://localhost:5601
3. Search: `trace_id: "your-trace-id"`

---

## 🔍 Common Use Cases

### Use Case 1: Debug Slow API Response

**Problem:** User reports slow `/api/v1/portfolio` endpoint

**Solution:**
1. Open Jaeger: http://localhost:16686
2. Search for service: `finans-backend`
3. Filter by operation: `GET /api/v1/portfolio`
4. Sort by duration (longest first)
5. Click on slow trace
6. Identify which span is slow (database? external API?)

### Use Case 2: Monitor Error Rate

**Problem:** Need to track API errors

**Solution:**
1. Open Prometheus: http://localhost:9090
2. Query:
   ```promql
   rate(http_server_requests_seconds_count{status=~"5.."}[5m])
   ```
3. See error rate over time
4. Create alert if error rate > threshold

### Use Case 3: Investigate Production Issue

**Problem:** User reports error at specific time

**Solution:**
1. Open OpenSearch Dashboards
2. Filter by time range
3. Filter by userId or endpoint
4. Copy `trace_id` from error log
5. Open Jaeger and search by `trace_id`
6. See complete request flow
7. Identify root cause

---

## 📊 Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| **Backend** | http://localhost:8080 | - |
| **Jaeger UI** | http://localhost:16686 | - |
| **Prometheus** | http://localhost:9090 | - |
| **Grafana** | http://localhost:3000 | admin/admin |
| **OpenSearch Dashboards** | http://localhost:5601 | - |

---

## 🛠️ Troubleshooting

### No Traces in Jaeger?

**Check backend logs:**
```powershell
docker logs finans-backend | Select-String "otel"
```

Should see: `OpenTelemetry Javaagent enabled`

**Check OTel Collector:**
```powershell
docker logs finans-otel-collector
```

Should see: `Everything is ready`

### Prometheus Target Down?

**Check actuator endpoint:**
```powershell
Invoke-WebRequest -Uri "http://localhost:8080/actuator/prometheus" -UseBasicParsing
```

Should return metrics in Prometheus format.

### Logs Missing trace_id?

**Generate traffic and check:**
```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/market/instruments" -UseBasicParsing
docker logs finans-backend --tail 50
```

Should see `trace_id=...` in logs.

---

## 📚 Documentation

**Detailed Setup Guide:**
```
docs/OPENTELEMETRY_SETUP.md
```

**Implementation Plan:**
```
docs/OPENTELEMETRY_KAFKA_IMPLEMENTATION_PLAN.md
```

**Phase 1 Summary:**
```
docs/PHASE1_SUMMARY.md
```

---

## 🎯 Next Steps

1. **Explore Jaeger** - View traces for different endpoints
2. **Create Grafana Dashboards** - Visualize metrics
3. **Set Up Alerts** - Get notified of issues
4. **Add Custom Metrics** - Track business metrics
5. **Add Custom Spans** - Trace important operations

---

## 💡 Pro Tips

**Tip 1: Use Trace ID for Debugging**
- Every request has a unique trace ID
- Use it to correlate logs, traces, and metrics
- Share trace ID with team members for collaboration

**Tip 2: Monitor Key Metrics**
- Response time (p95, p99)
- Error rate
- Request volume
- Database connection pool

**Tip 3: Create Dashboards**
- API Performance Dashboard
- Business Metrics Dashboard
- System Health Dashboard
- Error Tracking Dashboard

**Tip 4: Set Up Alerts**
- High error rate (> 1%)
- Slow response time (p95 > 1s)
- High memory usage (> 80%)
- Service down

---

**Questions?** Check `docs/OPENTELEMETRY_SETUP.md` for detailed documentation! 🚀

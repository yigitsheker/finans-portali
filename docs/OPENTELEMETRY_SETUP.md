# 🔭 OpenTelemetry Observability Setup

## 📊 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     Finans Portal Backend                        │
│                  (Spring Boot + OTel Java Agent)                 │
└────────────┬────────────────────────────────────┬────────────────┘
             │                                    │
             │ OTLP/HTTP (4318)                  │ Prometheus
             │ Traces + Metrics                  │ /actuator/prometheus
             ▼                                    ▼
┌────────────────────────────┐      ┌────────────────────────────┐
│  OpenTelemetry Collector   │      │        Prometheus          │
│  - Receives OTLP data      │◄─────┤  - Scrapes metrics         │
│  - Processes & filters     │      │  - Stores time-series      │
│  - Exports to backends     │      └────────────┬───────────────┘
└────────┬──────────┬────────┘                   │
         │          │                            │
         │ Traces   │ Metrics                    │ PromQL
         │          │                            │
         ▼          ▼                            ▼
┌─────────────┐  ┌──────────────┐      ┌────────────────────────┐
│   Jaeger    │  │  Prometheus  │      │       Grafana          │
│  - Trace UI │  │  - Exporter  │      │  - Dashboards          │
│  - Search   │  └──────────────┘      │  - Visualization       │
└─────────────┘                        └────────────────────────┘
```

## 🎯 What's Included

### Services

| Service | Port | Purpose | URL |
|---------|------|---------|-----|
| **Jaeger** | 16686 | Distributed tracing UI | http://localhost:16686 |
| **OpenTelemetry Collector** | 4317, 4318 | Telemetry data pipeline | - |
| **Prometheus** | 9090 | Metrics storage & querying | http://localhost:9090 |
| **Grafana** | 3000 | Metrics visualization | http://localhost:3000 |

### Instrumentation

**Automatic (via OpenTelemetry Java Agent):**
- ✅ HTTP requests (Spring MVC/WebFlux)
- ✅ Database queries (JDBC/Hibernate)
- ✅ HTTP client calls (RestTemplate/WebClient)
- ✅ Scheduled tasks
- ✅ Async operations

**Manual (via Micrometer):**
- ✅ Custom business metrics
- ✅ Custom spans for important operations
- ✅ Actuator endpoints

## 🚀 Quick Start

### 1. Start All Services

```bash
cd C:\Users\yigid\Desktop\finans-portali
docker compose up -d
```

### 2. Wait for Services to be Healthy

```bash
# Check all services
docker compose ps

# Check backend logs
docker logs finans-backend -f

# Check OTel Collector logs
docker logs finans-otel-collector -f
```

### 3. Verify Services

**Jaeger UI:**
```
http://localhost:16686
```
- Should show "finans-backend" in service dropdown

**Prometheus:**
```
http://localhost:9090
```
- Go to Status → Targets
- Should see "finans-backend" and "otel-collector" as UP

**Grafana:**
```
http://localhost:3000
```
- Username: `admin`
- Password: `admin`
- Should have Prometheus and Jaeger datasources configured

## 🧪 Testing Tracing

### Step 1: Generate Some Traffic

```powershell
# Call market instruments endpoint
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/market/instruments" -UseBasicParsing

# Call news endpoint
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/news" -UseBasicParsing

# Call exchange rates endpoint
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/exchange-rates" -UseBasicParsing
```

### Step 2: View Traces in Jaeger

1. Open Jaeger UI: http://localhost:16686
2. Select Service: **finans-backend**
3. Click **Find Traces**
4. Click on a trace to see details

**What to Look For:**
- ✅ HTTP request span (e.g., `GET /api/v1/market/instruments`)
- ✅ Database query spans (e.g., `SELECT from market_instrument`)
- ✅ Span duration
- ✅ Tags: `http.method`, `http.status_code`, `http.url`

### Step 3: Correlate Logs with Traces

1. Copy `trace_id` from Jaeger trace
2. Open OpenSearch Dashboards: http://localhost:5601
3. Go to Discover
4. Search: `trace_id: "your-trace-id-here"`
5. Should see all logs for that request

## 📊 Testing Metrics

### Step 1: Check Prometheus Targets

1. Open Prometheus: http://localhost:9090
2. Go to **Status → Targets**
3. Verify targets are UP:
   - `finans-backend` (scraping `/actuator/prometheus`)
   - `otel-collector` (scraping metrics exporter)

### Step 2: Query Metrics

Open Prometheus and try these queries:

**HTTP Request Count:**
```promql
http_server_requests_seconds_count{job="finans-backend"}
```

**HTTP Request Duration (95th percentile):**
```promql
histogram_quantile(0.95, 
  rate(http_server_requests_seconds_bucket{job="finans-backend"}[5m])
)
```

**HTTP Error Rate:**
```promql
rate(http_server_requests_seconds_count{job="finans-backend",status=~"5.."}[5m])
```

**JVM Memory Usage:**
```promql
jvm_memory_used_bytes{job="finans-backend",area="heap"}
```

**Database Connection Pool:**
```promql
hikaricp_connections_active{job="finans-backend"}
```

### Step 3: View in Grafana

1. Open Grafana: http://localhost:3000
2. Login: admin/admin
3. Go to **Explore**
4. Select **Prometheus** datasource
5. Run the same queries above
6. Create visualizations

## 🎨 Available Metrics

### HTTP Metrics (Spring Boot Actuator)

| Metric | Description |
|--------|-------------|
| `http_server_requests_seconds_count` | Total HTTP requests |
| `http_server_requests_seconds_sum` | Total HTTP request duration |
| `http_server_requests_seconds_max` | Max HTTP request duration |

**Tags:** `method`, `uri`, `status`, `outcome`

### JVM Metrics

| Metric | Description |
|--------|-------------|
| `jvm_memory_used_bytes` | JVM memory usage |
| `jvm_memory_max_bytes` | JVM max memory |
| `jvm_gc_pause_seconds_count` | GC pause count |
| `jvm_threads_live` | Live threads |

### Database Metrics (HikariCP)

| Metric | Description |
|--------|-------------|
| `hikaricp_connections_active` | Active connections |
| `hikaricp_connections_idle` | Idle connections |
| `hikaricp_connections_pending` | Pending connections |
| `hikaricp_connections_timeout_total` | Connection timeouts |

### System Metrics

| Metric | Description |
|--------|-------------|
| `system_cpu_usage` | System CPU usage |
| `process_cpu_usage` | Process CPU usage |
| `system_load_average_1m` | System load average |

## 🔍 Trace Context Propagation

### How It Works

1. **Frontend Request** → Backend receives request
2. **OpenTelemetry Java Agent** creates trace context:
   - `trace_id`: Unique ID for entire request flow
   - `span_id`: Unique ID for this operation
3. **Context Propagation**:
   - Passed to database queries
   - Passed to external API calls
   - Added to MDC for logging
4. **Logs Include Trace Context**:
   ```json
   {
     "trace_id": "a1b2c3d4e5f6...",
     "span_id": "1234567890ab...",
     "message": "Processing request"
   }
   ```

### Viewing Correlated Data

**Scenario:** User reports slow API response

1. **Find Request in Logs** (OpenSearch):
   ```
   endpoint: "/api/v1/portfolio" AND userId: "user-123"
   ```

2. **Copy trace_id** from log entry

3. **View Trace in Jaeger**:
   - Paste `trace_id` in search
   - See complete request flow
   - Identify slow span

4. **View Related Logs**:
   - Search OpenSearch with `trace_id`
   - See all logs for that request

## 🛠️ Configuration

### OpenTelemetry Java Agent

Configured via environment variables in `docker-compose.yml`:

```yaml
OTEL_SERVICE_NAME: finans-backend
OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318
OTEL_TRACES_EXPORTER: otlp
OTEL_METRICS_EXPORTER: otlp
OTEL_LOGS_EXPORTER: none
```

### Sampling

**Current:** 100% sampling (all traces captured)

```yaml
OTEL_TRACES_SAMPLER: always_on
```

**Production:** Use probability sampling

```yaml
OTEL_TRACES_SAMPLER: traceidratio
OTEL_TRACES_SAMPLER_ARG: 0.1  # 10% sampling
```

### Instrumentation Control

**Disable specific instrumentation:**

```yaml
OTEL_INSTRUMENTATION_JDBC_ENABLED: false
OTEL_INSTRUMENTATION_HIBERNATE_ENABLED: false
```

## 🐛 Troubleshooting

### No Traces in Jaeger

**Check 1: Backend logs**
```bash
docker logs finans-backend | grep -i otel
```
Should see: `OpenTelemetry Javaagent enabled`

**Check 2: OTel Collector logs**
```bash
docker logs finans-otel-collector
```
Should see: `Everything is ready. Begin running and processing data.`

**Check 3: Network connectivity**
```bash
docker exec finans-backend wget -O- http://otel-collector:4318
```
Should connect successfully.

**Check 4: Jaeger health**
```bash
curl http://localhost:16686
```
Should return Jaeger UI HTML.

### No Metrics in Prometheus

**Check 1: Prometheus targets**
```
http://localhost:9090/targets
```
Should show `finans-backend` as UP.

**Check 2: Actuator endpoint**
```bash
curl http://localhost:8080/actuator/prometheus
```
Should return Prometheus metrics.

**Check 3: Prometheus config**
```bash
docker exec finans-prometheus cat /etc/prometheus/prometheus.yml
```
Should have `finans-backend` scrape config.

### Logs Missing trace_id

**Check 1: MDC context**
- OpenTelemetry Java Agent automatically adds `trace_id` and `span_id` to MDC
- Check logback-spring.xml includes `%X{trace_id}` and `%X{span_id}`

**Check 2: Trace context**
```bash
# Check if traces are being created
curl -v http://localhost:8080/api/v1/market/instruments
# Look for traceparent header in response
```

### High Memory Usage

**Check 1: Trace retention**
- Jaeger uses in-memory storage
- Default: 10,000 traces
- Reduce if needed:
  ```yaml
  MEMORY_MAX_TRACES: 5000
  ```

**Check 2: Prometheus retention**
- Default: 15 days
- Reduce if needed:
  ```yaml
  --storage.tsdb.retention.time=7d
  ```

## 📈 Performance Impact

### OpenTelemetry Java Agent

**CPU Overhead:** ~1-3%
**Memory Overhead:** ~50-100MB
**Latency Overhead:** ~1-5ms per request

### Mitigation Strategies

1. **Use Sampling in Production:**
   ```yaml
   OTEL_TRACES_SAMPLER: traceidratio
   OTEL_TRACES_SAMPLER_ARG: 0.1  # 10%
   ```

2. **Disable Unnecessary Instrumentation:**
   ```yaml
   OTEL_INSTRUMENTATION_LOGBACK_APPENDER_ENABLED: false
   ```

3. **Batch Export:**
   - OTel Collector batches data before export
   - Reduces network overhead

## 🔒 Security Considerations

### Sensitive Data

**Automatically Excluded:**
- ❌ `Authorization` header
- ❌ `Cookie` header
- ❌ `Set-Cookie` header

**Configuration:**
```yaml
OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_SERVER_REQUEST: ""
OTEL_INSTRUMENTATION_HTTP_CAPTURE_HEADERS_SERVER_RESPONSE: ""
```

### Actuator Endpoints

**Development:** All endpoints exposed

**Production:** Restrict endpoints
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

## 📚 Next Steps

1. **Create Grafana Dashboards** - Visualize metrics
2. **Add Custom Metrics** - Business-specific metrics
3. **Add Custom Spans** - Important operations
4. **Set Up Alerts** - Prometheus alerting rules
5. **Add Kafka Observability** - Trace Kafka events

## 🔗 Useful Links

- [OpenTelemetry Java Agent](https://github.com/open-telemetry/opentelemetry-java-instrumentation)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

---

**Questions?** Check the troubleshooting section or review the logs! 🚀

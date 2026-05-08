# 🎯 OpenTelemetry + Kafka Implementation Plan

## 📊 Current Project State Analysis

### ✅ What's Already Working

**Infrastructure:**
- ✅ PostgreSQL database (port 5432)
- ✅ Keycloak authentication (port 8090)
- ✅ OpenLDAP user management (port 389)
- ✅ OpenSearch logging (port 9200)
- ✅ OpenSearch Dashboards (port 5601)
- ✅ Fluent Bit log shipper
- ✅ Spring Boot backend (port 8080)
- ✅ React frontend (port 80)

**Backend Dependencies (pom.xml):**
- ✅ Spring Boot 3.5.9 + Java 21
- ✅ `spring-boot-starter-actuator` - Already present
- ✅ `micrometer-tracing-bridge-otel` - Already present
- ✅ `opentelemetry-exporter-otlp` - Already present
- ✅ `micrometer-registry-prometheus` - Already present
- ✅ `logstash-logback-encoder` - Already present for JSON logging

**Configuration (application.yml):**
- ✅ Actuator endpoints exposed: health, info, metrics, prometheus
- ✅ Prometheus metrics enabled
- ✅ Tracing enabled with sampling probability 1.0
- ✅ OpenTelemetry config present (but pointing to wrong endpoint)
- ✅ MDC context includes traceId and spanId in logs

**Logging (logback-spring.xml):**
- ✅ JSON logging to `/app/logs/application.json`
- ✅ MDC fields: requestId, userId, username, endpoint, httpMethod, statusCode, durationMs, clientIp, traceId, spanId
- ✅ Fluent Bit ships logs to OpenSearch

**Security:**
- ✅ OAuth2 Resource Server with Keycloak JWT
- ✅ Role-based access control (ADMIN, USER)
- ✅ Public endpoints: /actuator/**, /api/v1/market/**, /api/v1/news/**
- ✅ Protected endpoints: /api/v1/portfolio/**, /api/v1/alerts/**

**Business Modules:**
- ✅ Market data (instruments, quotes, candles)
- ✅ News articles
- ✅ Portfolio management
- ✅ Price alerts
- ✅ Exchange rates
- ✅ Investment funds
- ✅ Technical analysis
- ✅ Scheduled jobs (PriceRefreshScheduler)

**Observability:**
- ✅ ObservabilityConfig with TimedAspect and ObservedAspect
- ✅ Custom health indicator
- ✅ Custom info contributor
- ✅ LoggingFilter for HTTP request/response logging

### ❌ What's Missing

**OpenTelemetry:**
- ❌ OpenTelemetry Collector service
- ❌ Jaeger tracing backend
- ❌ Correct OTLP endpoint configuration (currently points to jaeger:14250 which doesn't exist)
- ❌ OpenTelemetry Java Agent in Docker container
- ❌ Custom spans for business operations
- ❌ Custom metrics for business events

**Metrics & Dashboards:**
- ❌ Prometheus service
- ❌ Grafana service
- ❌ Grafana dashboards

**Kafka:**
- ❌ Kafka service
- ❌ Kafka UI (optional but useful)
- ❌ Spring Kafka dependencies
- ❌ Kafka producer configuration
- ❌ Kafka consumer configuration
- ❌ Event DTOs
- ❌ Kafka topics
- ❌ Business event publishing

**Documentation:**
- ❌ OpenTelemetry setup guide
- ❌ Kafka architecture guide
- ❌ Testing instructions

---

## 🎯 Implementation Strategy

### Phase 1: OpenTelemetry Infrastructure (Priority: HIGH)
1. Add Jaeger to docker-compose.yml
2. Add OpenTelemetry Collector to docker-compose.yml
3. Add Prometheus to docker-compose.yml
4. Add Grafana to docker-compose.yml
5. Create otel-collector-config.yaml
6. Create prometheus.yml
7. Update backend Dockerfile to include OpenTelemetry Java Agent
8. Fix application.yml OTLP endpoint configuration
9. Test tracing end-to-end

### Phase 2: Custom Metrics & Spans (Priority: MEDIUM)
1. Add custom metrics to business services
2. Add custom spans to important operations
3. Update ObservabilityConfig with metric beans
4. Test metrics in Prometheus

### Phase 3: Grafana Dashboards (Priority: MEDIUM)
1. Create Grafana datasource provisioning
2. Create dashboard JSON files
3. Import dashboards to Grafana
4. Test dashboard visualization

### Phase 4: Kafka Infrastructure (Priority: HIGH)
1. Add Kafka to docker-compose.yml
2. Add Kafka UI to docker-compose.yml
3. Add Spring Kafka dependencies to pom.xml
4. Create Kafka configuration
5. Define event DTOs
6. Test Kafka connectivity

### Phase 5: Kafka Producers (Priority: MEDIUM)
1. Create KafkaProducerService
2. Add event publishing to PortfolioService
3. Add event publishing to PriceRefreshScheduler
4. Add event publishing to PriceAlertService
5. Test event publishing

### Phase 6: Kafka Consumers (Priority: LOW)
1. Create KafkaConsumerService
2. Add audit event consumer
3. Add notification event consumer
4. Test event consumption

### Phase 7: Documentation & Testing (Priority: HIGH)
1. Create OpenTelemetry documentation
2. Create Kafka documentation
3. Create testing guide
4. Create troubleshooting guide

---

## 📦 Services to Add

### Docker Compose Services

| Service | Port | Purpose | Dependencies |
|---------|------|---------|--------------|
| jaeger | 16686 (UI), 4317 (OTLP gRPC), 4318 (OTLP HTTP) | Distributed tracing backend | - |
| otel-collector | 4317 (OTLP gRPC), 4318 (OTLP HTTP), 8888 (metrics) | Telemetry data collector | jaeger |
| prometheus | 9090 | Metrics storage & querying | otel-collector, backend |
| grafana | 3000 | Metrics visualization | prometheus |
| kafka | 9092 (internal), 9093 (external) | Event streaming platform | - |
| kafka-ui | 8085 | Kafka management UI | kafka |

---

## 🔧 Configuration Changes

### application.yml Changes

**Current (WRONG):**
```yaml
otel:
  exporter:
    otlp:
      endpoint: http://jaeger:14250  # ❌ Wrong port, Jaeger doesn't expose OTLP on 14250
```

**Fixed (CORRECT):**
```yaml
management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces  # ✅ Correct OTLP HTTP endpoint
```

**Or use Java Agent environment variables (RECOMMENDED):**
```yaml
# Remove otel.* config from application.yml
# Use environment variables in docker-compose.yml instead
OTEL_SERVICE_NAME: finans-backend
OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318
OTEL_EXPORTER_OTLP_PROTOCOL: http/protobuf
OTEL_TRACES_EXPORTER: otlp
OTEL_METRICS_EXPORTER: otlp
OTEL_LOGS_EXPORTER: none
```

### Dockerfile Changes

**Add OpenTelemetry Java Agent:**
```dockerfile
# Download OpenTelemetry Java Agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
RUN chmod 644 /otel/opentelemetry-javaagent.jar

# Update ENTRYPOINT
ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-jar", "app.jar"]
```

---

## 📊 Kafka Topics Design

### Topic Naming Convention
`finance.<domain>.<event-type>`

### Proposed Topics

| Topic | Purpose | Producer | Consumer | Retention |
|-------|---------|----------|----------|-----------|
| `finance.portfolio.events` | Portfolio position changes | PortfolioService | AuditConsumer | 30 days |
| `finance.market.events` | Market data refresh events | PriceRefreshScheduler | LogConsumer | 7 days |
| `finance.alert.events` | Price alert triggers | PriceAlertService | NotificationConsumer | 30 days |
| `finance.audit.events` | Audit trail events | All services | AuditConsumer | 90 days |
| `finance.notification.events` | User notifications | All services | NotificationConsumer | 30 days |

### Event Schema Example

```json
{
  "eventId": "uuid",
  "eventType": "PORTFOLIO_POSITION_CREATED",
  "occurredAt": "2026-05-08T12:00:00Z",
  "serviceName": "finans-backend",
  "userId": "user-uuid",
  "traceId": "trace-id-from-otel",
  "spanId": "span-id-from-otel",
  "entityType": "PortfolioPosition",
  "entityId": "position-id",
  "data": {
    "symbol": "AAPL",
    "quantity": 10,
    "purchasePrice": 150.00
  },
  "metadata": {
    "source": "web-ui",
    "ipAddress": "192.168.1.1"
  }
}
```

---

## 🎨 Custom Metrics to Add

### Business Metrics

```java
// Market Data
Counter marketDataRefreshTotal = Counter.builder("market.data.refresh.total")
    .description("Total market data refresh operations")
    .tag("status", "success|failure")
    .register(meterRegistry);

Timer marketDataRefreshDuration = Timer.builder("market.data.refresh.duration")
    .description("Market data refresh duration")
    .register(meterRegistry);

// Portfolio
Counter portfolioPositionCreated = Counter.builder("portfolio.position.created.total")
    .description("Total portfolio positions created")
    .register(meterRegistry);

Counter portfolioPositionDeleted = Counter.builder("portfolio.position.deleted.total")
    .description("Total portfolio positions deleted")
    .register(meterRegistry);

// Price Alerts
Counter priceAlertTriggered = Counter.builder("price.alert.triggered.total")
    .description("Total price alerts triggered")
    .tag("alertType", "ABOVE|BELOW")
    .register(meterRegistry);

// External APIs
Timer externalApiDuration = Timer.builder("external.api.request.duration")
    .description("External API request duration")
    .tag("provider", "yahoo|finnhub|twelvedata")
    .tag("status", "success|failure")
    .register(meterRegistry);

// Kafka
Counter kafkaEventsPublished = Counter.builder("kafka.events.published.total")
    .description("Total Kafka events published")
    .tag("topic", "topic-name")
    .tag("status", "success|failure")
    .register(meterRegistry);

Counter kafkaEventsConsumed = Counter.builder("kafka.events.consumed.total")
    .description("Total Kafka events consumed")
    .tag("topic", "topic-name")
    .tag("status", "success|failure")
    .register(meterRegistry);
```

---

## 🔍 Custom Spans to Add

### Important Business Operations

```java
// Market Data Refresh
@WithSpan("market.data.refresh")
public void refreshAll() {
    Span span = Span.current();
    span.setAttribute("job.name", "market-data-refresh");
    span.setAttribute("instruments.count", instruments.size());
    // ... business logic
}

// Portfolio Operations
@WithSpan("portfolio.position.create")
public PortfolioPosition createPosition(UpsertPositionRequest request) {
    Span span = Span.current();
    span.setAttribute("instrument.symbol", request.getSymbol());
    span.setAttribute("quantity", request.getQuantity());
    // ... business logic
}

// External API Calls
@WithSpan("external.api.yahoo.fetch")
public Optional<QuoteData> fetchQuote(String symbol) {
    Span span = Span.current();
    span.setAttribute("external.api.name", "yahoo-finance");
    span.setAttribute("symbol", symbol);
    // ... API call
}

// Kafka Producer
@WithSpan("kafka.publish")
public void publishEvent(String topic, Object event) {
    Span span = Span.current();
    span.setAttribute("kafka.topic", topic);
    span.setAttribute("event.type", event.getClass().getSimpleName());
    // ... publish logic
}
```

---

## 🔒 Security Considerations

### Actuator Endpoints

**Development (current):**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,loggers,env,configprops,beans,mappings
```

**Production (recommended):**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
```

### Sensitive Data

**DO NOT log/trace/publish:**
- ❌ JWT tokens
- ❌ Authorization headers
- ❌ Passwords
- ❌ Refresh tokens
- ❌ Personal financial data (account numbers, balances)
- ❌ Email addresses in traces
- ❌ IP addresses in public traces

**SAFE to log/trace:**
- ✅ User ID (UUID)
- ✅ Request ID
- ✅ Trace ID / Span ID
- ✅ HTTP method
- ✅ Endpoint path
- ✅ Status code
- ✅ Duration
- ✅ Instrument symbols
- ✅ Operation names

---

## 📈 Expected Outcomes

### Observability

**Traces (Jaeger):**
- View complete request flow from frontend → backend → database → external APIs
- Identify slow operations
- Debug errors with full context
- Correlate logs with traces using traceId

**Metrics (Prometheus + Grafana):**
- Monitor API response times
- Track error rates
- Monitor JVM memory/CPU
- Track business metrics (portfolio operations, market data refreshes)
- Monitor Kafka producer/consumer health

**Logs (OpenSearch):**
- Centralized log storage
- Correlation with traces via traceId/spanId
- Business event logging
- Error tracking

### Kafka

**Event Streaming:**
- Decouple business logic from side effects
- Audit trail for all important operations
- Asynchronous notification system
- Event-driven architecture foundation

**Observability:**
- Kafka events appear in traces
- Kafka metrics in Prometheus
- Event flow visualization

---

## 🧪 Testing Strategy

### Phase 1: Infrastructure Testing
1. Start all services: `docker compose up -d`
2. Check service health:
   - Jaeger UI: http://localhost:16686
   - Prometheus: http://localhost:9090
   - Grafana: http://localhost:3000
   - Kafka UI: http://localhost:8085
3. Verify backend connects to all services

### Phase 2: Tracing Testing
1. Call API endpoint: `GET /api/v1/market/instruments`
2. Open Jaeger UI
3. Search for service: `finans-backend`
4. Verify trace appears with spans
5. Check logs in OpenSearch for matching traceId

### Phase 3: Metrics Testing
1. Open Prometheus: http://localhost:9090
2. Query: `http_server_requests_seconds_count`
3. Verify metrics appear
4. Open Grafana and check dashboards

### Phase 4: Kafka Testing
1. Open Kafka UI: http://localhost:8085
2. Verify topics exist
3. Create portfolio position via API
4. Check Kafka UI for event in `finance.portfolio.events`
5. Verify consumer processes event

---

## 📝 Implementation Order

### Week 1: OpenTelemetry Foundation
- [ ] Day 1-2: Add Jaeger, OTel Collector, Prometheus, Grafana to docker-compose
- [ ] Day 3: Update backend Dockerfile with Java Agent
- [ ] Day 4: Fix application.yml configuration
- [ ] Day 5: Test tracing end-to-end

### Week 2: Metrics & Dashboards
- [ ] Day 1-2: Add custom metrics to services
- [ ] Day 3: Add custom spans to operations
- [ ] Day 4-5: Create and test Grafana dashboards

### Week 3: Kafka Foundation
- [ ] Day 1-2: Add Kafka and Kafka UI to docker-compose
- [ ] Day 3: Add Spring Kafka dependencies
- [ ] Day 4: Create Kafka configuration and event DTOs
- [ ] Day 5: Test Kafka connectivity

### Week 4: Kafka Integration
- [ ] Day 1-2: Implement Kafka producers in services
- [ ] Day 3: Implement Kafka consumers
- [ ] Day 4: Add Kafka observability
- [ ] Day 5: End-to-end testing

### Week 5: Documentation & Polish
- [ ] Day 1-2: Write comprehensive documentation
- [ ] Day 3: Create testing guides
- [ ] Day 4: Create troubleshooting guides
- [ ] Day 5: Final review and cleanup

---

## 🎯 Success Criteria

### OpenTelemetry
- ✅ All HTTP requests create traces in Jaeger
- ✅ Database queries appear as spans
- ✅ External API calls appear as spans
- ✅ Logs include traceId and spanId
- ✅ Metrics available in Prometheus
- ✅ Grafana dashboards show real-time data

### Kafka
- ✅ Kafka cluster healthy
- ✅ Topics created automatically
- ✅ Events published successfully
- ✅ Events consumed successfully
- ✅ Kafka operations appear in traces
- ✅ Kafka metrics in Prometheus

### Integration
- ✅ No existing features broken
- ✅ Keycloak authentication still works
- ✅ OpenSearch logging still works
- ✅ All tests pass
- ✅ Documentation complete

---

## 🚨 Risks & Mitigation

### Risk 1: Port Conflicts
**Mitigation:** Use non-standard ports for new services
- Jaeger UI: 16686 (standard)
- Prometheus: 9090 (standard)
- Grafana: 3000 (standard)
- Kafka UI: 8085 (non-standard, 8080 taken by backend)

### Risk 2: Resource Consumption
**Mitigation:** 
- Use single-node Kafka (no Zookeeper)
- Limit Prometheus retention to 15 days
- Limit Jaeger trace retention to 7 days
- Use sampling in production (currently 100%)

### Risk 3: Breaking Existing Features
**Mitigation:**
- Test all existing endpoints after each change
- Keep existing logging intact
- Don't modify security configuration
- Use feature flags if needed

### Risk 4: Complex Debugging
**Mitigation:**
- Add comprehensive logging
- Create troubleshooting guide
- Test each component independently
- Document common issues

---

## 📚 Next Steps

1. **Review this plan** - Confirm approach is correct
2. **Start Phase 1** - Add OpenTelemetry infrastructure
3. **Test incrementally** - Don't add everything at once
4. **Document as you go** - Update docs with actual findings
5. **Iterate** - Adjust plan based on real-world results

---

**Ready to proceed?** Let me know and I'll start implementing Phase 1! 🚀

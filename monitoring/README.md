# 📊 Finans Portali - Enterprise Monitoring Stack

Bu dizin, Finans Portali uygulaması için enterprise-grade monitoring, logging ve observability altyapısını içerir.

## 🏗️ Monitoring Mimarisi

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Application   │───►│   Prometheus    │───►│    Grafana      │
│   (Metrics)     │    │   (Collection)  │    │ (Visualization) │
└─────────────────┘    └─────────────────┘    └─────────────────┘

┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Application   │───►│    Filebeat     │───►│    Logstash     │
│     (Logs)      │    │  (Log Shipper)  │    │ (Log Processor) │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                       │
                                                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Application   │───►│     Jaeger      │    │   OpenSearch    │
│    (Traces)     │    │   (Tracing)     │    │ (Log Storage)   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                       │
                                                       ▼
                                               ┌─────────────────┐
                                               │ OpenSearch      │
                                               │  Dashboards     │
                                               └─────────────────┘
```

## 🚀 Servisler ve Portlar

| Servis | Port | Açıklama |
|--------|------|----------|
| **Prometheus** | 9090 | Metrics collection ve alerting |
| **Grafana** | 3000 | Metrics visualization ve dashboards |
| **Jaeger** | 16686 | Distributed tracing UI |
| **OpenSearch** | 9200 | Log storage ve search |
| **OpenSearch Dashboards** | 5601 | Log visualization |
| **Logstash** | 5044 | Log processing pipeline |

## 📈 Metrics ve Monitoring

### Prometheus Metrics
- **HTTP Request Metrics**: Request rate, response time, error rate
- **JVM Metrics**: Memory usage, GC performance, thread count
- **Database Metrics**: Connection pool, query performance
- **Custom Business Metrics**: Portfolio operations, price alerts

### Grafana Dashboards
- **Application Overview**: Genel sistem durumu
- **Performance Metrics**: Response time, throughput
- **Infrastructure Metrics**: CPU, memory, disk usage
- **Business Metrics**: User activity, financial operations

### Key Performance Indicators (KPIs)
```
- Request Rate: > 100 req/sec
- Response Time P95: < 500ms
- Error Rate: < 1%
- Memory Usage: < 80%
- Database Connections: < 80% of pool
```

## 📋 Logging Strategy

### Log Levels
- **ERROR**: System errors, exceptions
- **WARN**: Performance issues, deprecated usage
- **INFO**: Business operations, request/response
- **DEBUG**: Detailed application flow
- **TRACE**: Fine-grained debugging

### Log Format (JSON)
```json
{
  "timestamp": "2026-05-01T15:22:00.000+03:00",
  "level": "INFO",
  "message": "REQUEST: GET /api/v1/market/summary",
  "serviceName": "finans-backend",
  "thread": "http-nio-8080-exec-1",
  "logger": "com.finansportali.backend.config.LoggingConfig",
  "requestId": "abc12345",
  "traceId": "def67890",
  "spanId": "ghi11111",
  "userId": "user123",
  "operation": "market-data-fetch"
}
```

### Log Aggregation Pipeline
1. **Application** → JSON logs to file
2. **Filebeat** → Ships logs to Logstash
3. **Logstash** → Processes and enriches logs
4. **OpenSearch** → Stores and indexes logs
5. **OpenSearch Dashboards** → Visualizes logs

## 🔍 Distributed Tracing

### Jaeger Integration
- **Trace Collection**: OpenTelemetry instrumentation
- **Service Map**: Visualize service dependencies
- **Performance Analysis**: Identify bottlenecks
- **Error Tracking**: Trace error propagation

### Trace Context
```
TraceId: 4bf92f3577b34da6a3ce929d0e0e4736
SpanId: 00f067aa0ba902b7
Operation: /api/v1/portfolio/positions
Duration: 45ms
Tags: http.method=GET, http.status_code=200
```

## 🚨 Alerting Rules

### Critical Alerts
- **High Error Rate**: > 5% in 5 minutes
- **High Response Time**: P95 > 1s for 5 minutes
- **Memory Usage**: > 90% for 2 minutes
- **Database Connections**: > 90% of pool

### Warning Alerts
- **Moderate Error Rate**: > 2% in 10 minutes
- **Slow Response Time**: P95 > 500ms for 10 minutes
- **Memory Usage**: > 80% for 5 minutes

## 🔧 Configuration Files

### Prometheus (`prometheus.yml`)
- Scrape configurations for all services
- Alerting rules
- Service discovery

### Grafana
- **Datasources**: Prometheus, Jaeger, OpenSearch
- **Dashboards**: Pre-configured monitoring dashboards
- **Provisioning**: Automated setup

### Logstash (`logstash.conf`)
- Input: Filebeat, direct file input
- Filters: JSON parsing, field extraction
- Output: OpenSearch indexing

### Filebeat (`filebeat.yml`)
- Log file monitoring
- Docker container log collection
- Metadata enrichment

## 📊 Dashboard Access

### Grafana Dashboards
```bash
# Access Grafana
http://localhost:3000
Username: admin
Password: admin

# Key Dashboards:
- Finans Backend Monitoring
- Infrastructure Overview
- Business Metrics
- Error Analysis
```

### Jaeger Tracing
```bash
# Access Jaeger UI
http://localhost:16686

# Search traces by:
- Service: finans-backend
- Operation: HTTP requests
- Tags: error=true
```

### OpenSearch Dashboards
```bash
# Access OpenSearch Dashboards
http://localhost:5601

# Index Patterns:
- finans-logs-*
- Application logs with full-text search
```

## 🔍 Troubleshooting

### Common Issues

#### High Memory Usage
```bash
# Check JVM metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Grafana: JVM Memory Usage panel
# Look for memory leaks or GC issues
```

#### Slow Database Queries
```bash
# Check connection pool
curl http://localhost:8080/actuator/metrics/hikaricp.connections

# OpenSearch: Search for slow query logs
# Jaeger: Trace database operations
```

#### High Error Rate
```bash
# Check error logs
curl http://localhost:9200/finans-logs-*/_search?q=level:ERROR

# Grafana: Error Rate panel
# Jaeger: Filter traces with error=true tag
```

### Log Analysis Queries

#### Find Errors in Last Hour
```json
GET finans-logs-*/_search
{
  "query": {
    "bool": {
      "must": [
        {"term": {"level": "ERROR"}},
        {"range": {"timestamp": {"gte": "now-1h"}}}
      ]
    }
  }
}
```

#### Performance Analysis
```json
GET finans-logs-*/_search
{
  "query": {
    "bool": {
      "must": [
        {"exists": {"field": "duration"}},
        {"range": {"duration": {"gte": 1000}}}
      ]
    }
  }
}
```

## 🚀 Deployment Commands

### Start Monitoring Stack
```bash
# Start all monitoring services
docker-compose up -d prometheus grafana jaeger opensearch opensearch-dashboards logstash filebeat

# Check service status
docker-compose ps

# View logs
docker-compose logs -f [service-name]
```

### Health Checks
```bash
# Prometheus
curl http://localhost:9090/-/healthy

# Grafana
curl http://localhost:3000/api/health

# Jaeger
curl http://localhost:16686/

# OpenSearch
curl http://localhost:9200/_cluster/health
```

## 📈 Performance Tuning

### JVM Tuning
```yaml
# In docker-compose.yml
environment:
  - "JAVA_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC"
```

### Database Tuning
```yaml
# Connection pool settings
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

### Monitoring Resource Usage
```bash
# Monitor container resources
docker stats

# Check disk usage
df -h

# Monitor network
netstat -i
```

## 🔒 Security Considerations

- **Log Sanitization**: Sensitive data excluded from logs
- **Access Control**: Monitoring tools behind authentication
- **Network Security**: Internal network communication
- **Data Retention**: Logs rotated and archived

## 📚 Additional Resources

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [OpenSearch Documentation](https://opensearch.org/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
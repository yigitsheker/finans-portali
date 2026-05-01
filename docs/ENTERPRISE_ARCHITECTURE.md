# 🏢 Finans Portali - Enterprise Architecture

## 📋 Executive Summary

Finans Portali, modern enterprise standartlarına uygun olarak tasarlanmış, container tabanlı bir finans portföy yönetim sistemidir. Mikroservis mimarisi, comprehensive monitoring, distributed tracing ve enterprise-grade security özellikleri ile production-ready bir çözüm sunar.

## 🏗️ System Architecture

### High-Level Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│                        Load Balancer / CDN                      │
└─────────────────────────┬───────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│                     Frontend Layer                              │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐   │
│  │   React SPA     │ │   Nginx Proxy   │ │  Static Assets  │   │
│  │   (Port 80)     │ │   (Reverse)     │ │   (Caching)     │   │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘   │
└─────────────────────────┬───────────────────────────────────────┘
                          │ HTTPS/API Calls
┌─────────────────────────▼───────────────────────────────────────┐
│                    API Gateway Layer                            │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐   │
│  │  Spring Boot    │ │   Rate Limiting │ │  CORS Handling  │   │
│  │   (Port 8080)   │ │   & Throttling  │ │   & Security    │   │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘   │
└─────────────────────────┬───────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│                   Business Logic Layer                          │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐   │
│  │ Market Service  │ │Portfolio Service│ │ Alert Service   │   │
│  │ News Service    │ │ Auth Service    │ │ Notification    │   │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘   │
└─────────────────────────┬───────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│                     Data Layer                                  │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐   │
│  │   PostgreSQL    │ │     Redis       │ │  External APIs  │   │
│  │   (Primary DB)  │ │   (Caching)     │ │ (Yahoo Finance) │   │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                 Cross-Cutting Concerns                          │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐   │
│  │   Monitoring    │ │    Security     │ │    Logging      │   │
│  │ (Prometheus)    │ │   (Keycloak)    │ │ (ELK Stack)     │   │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## 🔧 Technology Stack

### Frontend Technologies
| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| **Framework** | React | 18.x | UI Framework |
| **Language** | TypeScript | 5.x | Type Safety |
| **Build Tool** | Vite | 5.x | Fast Development |
| **Charts** | Recharts | 2.x | Data Visualization |
| **HTTP Client** | Axios | 1.x | API Communication |
| **Authentication** | Keycloak JS | 23.x | OAuth2/OIDC |

### Backend Technologies
| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| **Framework** | Spring Boot | 3.5.9 | Application Framework |
| **Language** | Java | 21 | Programming Language |
| **Security** | Spring Security | 6.x | Authentication/Authorization |
| **ORM** | Hibernate/JPA | 6.x | Object-Relational Mapping |
| **Migration** | Flyway | 10.x | Database Versioning |
| **Caching** | Caffeine | 3.x | In-Memory Caching |
| **Documentation** | OpenAPI 3 | 3.x | API Documentation |

### Infrastructure Technologies
| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| **Database** | PostgreSQL | 15 | Primary Data Store |
| **Cache** | Redis | 7 | Distributed Caching |
| **Auth Server** | Keycloak | 23 | Identity Management |
| **Container** | Docker | 24.x | Containerization |
| **Orchestration** | Docker Compose | 2.x | Multi-Container Apps |
| **Web Server** | Nginx | 1.25 | Reverse Proxy |

### Monitoring & Observability
| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| **Metrics** | Prometheus | Latest | Metrics Collection |
| **Visualization** | Grafana | Latest | Dashboards |
| **Tracing** | Jaeger | Latest | Distributed Tracing |
| **Log Storage** | OpenSearch | 2.11 | Log Aggregation |
| **Log Processing** | Logstash | 8.11 | Log Pipeline |
| **Log Shipping** | Filebeat | 8.11 | Log Collection |

## 🔒 Security Architecture

### Authentication & Authorization
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend      │───►│    Keycloak     │───►│    Backend      │
│   (React SPA)   │    │  (Auth Server)  │    │ (Resource API)  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │ 1. Login Request      │ 3. JWT Token          │
         │ 2. Redirect to KC     │ 4. API Calls          │
         │                       │    with Bearer Token  │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │   JWT Validation│
                    │   & Role Check  │
                    └─────────────────┘
```

### Security Features
- **OAuth2/OIDC**: Industry standard authentication
- **JWT Tokens**: Stateless authentication
- **Role-Based Access Control (RBAC)**: Fine-grained permissions
- **CORS Protection**: Cross-origin request security
- **HTTPS Enforcement**: Encrypted communication
- **Security Headers**: XSS, CSRF protection
- **Input Validation**: Request sanitization
- **SQL Injection Prevention**: Parameterized queries

## 📊 Monitoring & Observability

### Three Pillars of Observability

#### 1. Metrics (Prometheus + Grafana)
```
Application Metrics:
├── Business Metrics
│   ├── Portfolio Operations/sec
│   ├── Price Alert Triggers
│   ├── User Login Rate
│   └── API Usage by Endpoint
├── Performance Metrics
│   ├── Request Rate (RPS)
│   ├── Response Time (P50, P95, P99)
│   ├── Error Rate (4xx, 5xx)
│   └── Throughput (MB/sec)
├── Infrastructure Metrics
│   ├── JVM Memory Usage
│   ├── GC Performance
│   ├── Thread Pool Usage
│   └── Database Connection Pool
└── External Dependencies
    ├── Yahoo Finance API Latency
    ├── Database Query Performance
    └── Cache Hit/Miss Ratio
```

#### 2. Logging (ELK Stack)
```
Log Pipeline:
Application → Filebeat → Logstash → OpenSearch → Dashboards

Log Levels:
├── ERROR: System failures, exceptions
├── WARN: Performance issues, deprecated usage
├── INFO: Business operations, request/response
├── DEBUG: Application flow details
└── TRACE: Fine-grained debugging

Log Format: Structured JSON
├── Timestamp (ISO8601)
├── Log Level
├── Message
├── Service Name
├── Request ID (Correlation)
├── Trace ID (Distributed Tracing)
├── User Context
└── Custom Fields
```

#### 3. Tracing (Jaeger)
```
Distributed Tracing:
Frontend Request → API Gateway → Business Logic → Database
      │               │              │              │
      └─── Span ──────┴─── Span ─────┴─── Span ─────┘
                            │
                    ┌───────▼───────┐
                    │  Trace Context │
                    │  - Trace ID    │
                    │  - Span ID     │
                    │  - Baggage     │
                    └────────────────┘
```

### Alerting Strategy
```yaml
Critical Alerts (PagerDuty):
  - Error Rate > 5% (5 minutes)
  - Response Time P95 > 2s (5 minutes)
  - Memory Usage > 90% (2 minutes)
  - Database Connections > 90%
  - Service Down

Warning Alerts (Slack):
  - Error Rate > 2% (10 minutes)
  - Response Time P95 > 1s (10 minutes)
  - Memory Usage > 80% (5 minutes)
  - Disk Usage > 85%
  - Cache Miss Rate > 50%
```

## 🚀 Deployment Architecture

### Container Strategy
```
Multi-Stage Docker Builds:
├── Backend (Java)
│   ├── Builder Stage: Maven build
│   └── Runtime Stage: JRE + JAR
├── Frontend (React)
│   ├── Builder Stage: Node.js build
│   └── Runtime Stage: Nginx + static files
└── Infrastructure
    ├── PostgreSQL: Official image
    ├── Redis: Alpine variant
    ├── Keycloak: Official image
    └── Monitoring: Official images
```

### Environment Configuration
```yaml
Development:
  - Local Docker Compose
  - Hot reload enabled
  - Debug logging
  - Test data seeding

Staging:
  - Docker Swarm / Kubernetes
  - Production-like data
  - Performance testing
  - Security scanning

Production:
  - Kubernetes cluster
  - High availability
  - Auto-scaling
  - Backup strategies
```

## 📈 Performance & Scalability

### Performance Targets
| Metric | Target | Measurement |
|--------|--------|-------------|
| **Response Time** | P95 < 500ms | API endpoints |
| **Throughput** | 1000+ RPS | Peak load |
| **Availability** | 99.9% | Monthly uptime |
| **Error Rate** | < 0.1% | 4xx/5xx responses |
| **Memory Usage** | < 80% | JVM heap |
| **CPU Usage** | < 70% | Average load |

### Scalability Strategy
```
Horizontal Scaling:
├── Frontend: CDN + Multiple instances
├── Backend: Load balancer + Auto-scaling
├── Database: Read replicas + Connection pooling
└── Cache: Redis cluster

Vertical Scaling:
├── JVM Tuning: G1GC, heap sizing
├── Database: Query optimization, indexing
└── Connection Pools: HikariCP tuning
```

### Caching Strategy
```
Multi-Level Caching:
├── Browser Cache (Static assets)
├── CDN Cache (Global distribution)
├── Application Cache (Caffeine)
├── Database Cache (Redis)
└── Query Cache (Hibernate L2)
```

## 🔄 Data Flow Architecture

### Real-Time Data Pipeline
```
External APIs → Rate Limiter → Cache → Database → WebSocket → Frontend
     │              │           │         │          │           │
Yahoo Finance → Scheduler → Redis → PostgreSQL → SSE → React Components
```

### Batch Processing
```
Scheduled Jobs:
├── Price Refresh (Every 5 minutes)
├── News Aggregation (Every 15 minutes)
├── Alert Processing (Every 1 minute)
├── Portfolio Calculations (Every hour)
└── Data Cleanup (Daily)
```

## 🛡️ Disaster Recovery

### Backup Strategy
```yaml
Database Backups:
  - Full backup: Daily
  - Incremental: Every 4 hours
  - Point-in-time recovery: 7 days
  - Cross-region replication: Enabled

Application Backups:
  - Configuration: Version controlled
  - Secrets: Encrypted vault
  - Logs: 30-day retention
  - Metrics: 1-year retention
```

### High Availability
```
Multi-AZ Deployment:
├── Load Balancer: Multi-region
├── Application: 3+ instances
├── Database: Master-slave replication
├── Cache: Redis Sentinel
└── Monitoring: Redundant collectors
```

## 📋 Compliance & Governance

### Data Protection
- **GDPR Compliance**: User data anonymization
- **Data Encryption**: At rest and in transit
- **Access Logging**: Audit trail
- **Data Retention**: Configurable policies

### Security Standards
- **OWASP Top 10**: Vulnerability mitigation
- **Security Scanning**: Automated SAST/DAST
- **Dependency Scanning**: CVE monitoring
- **Penetration Testing**: Regular assessments

### Operational Excellence
- **Infrastructure as Code**: Terraform/Helm
- **GitOps**: Automated deployments
- **Change Management**: Approval workflows
- **Incident Response**: Runbooks and procedures

## 🔮 Future Roadmap

### Phase 1: Enhanced Analytics
- Advanced portfolio analytics
- Machine learning predictions
- Custom dashboard builder
- Mobile application

### Phase 2: Multi-Tenancy
- Organization support
- Team collaboration
- Advanced permissions
- White-label solutions

### Phase 3: AI Integration
- Intelligent alerts
- Market sentiment analysis
- Automated trading suggestions
- Natural language queries

## 📚 Documentation Standards

### API Documentation
- OpenAPI 3.0 specification
- Interactive Swagger UI
- Code examples
- Authentication guides

### Architecture Documentation
- C4 model diagrams
- Sequence diagrams
- Database ERD
- Deployment diagrams

### Operational Documentation
- Runbooks
- Troubleshooting guides
- Performance tuning
- Security procedures

---

*This document serves as the authoritative reference for the Finans Portali enterprise architecture. It should be updated as the system evolves and new requirements emerge.*
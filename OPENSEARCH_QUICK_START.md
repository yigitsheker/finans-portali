# 🚀 OpenSearch Logging - Quick Start Guide

## Prerequisites

- Docker and Docker Compose installed
- At least 4GB RAM available for Docker
- Ports available: 9200, 5601

## Step-by-Step Setup

### 1. Configure vm.max_map_count (Windows with WSL2)

```powershell
# Open PowerShell as Administrator
wsl -d docker-desktop

# Inside WSL, run:
sysctl -w vm.max_map_count=262144

# Exit WSL
exit
```

### 2. Start All Services

```powershell
# Navigate to project directory
cd c:\Users\yigid\Desktop\finans-portali

# Start all services
docker-compose up -d

# Wait for services to start (30-60 seconds)
```

### 3. Verify Services

```powershell
# Check all containers are running
docker-compose ps

# Expected output:
# finans-backend              Up (healthy)
# finans-opensearch           Up (healthy)
# finans-opensearch-dashboards Up (healthy)
# finans-fluent-bit           Up
# ... (other services)
```

### 4. Check OpenSearch

```powershell
# Test OpenSearch API
curl http://localhost:9200

# Expected response:
# {
#   "name" : "...",
#   "cluster_name" : "docker-cluster",
#   "version" : { ... }
# }

# Check cluster health
curl http://localhost:9200/_cluster/health

# Expected: "status":"green" or "yellow"
```

### 5. Generate Some Logs

```powershell
# Make API requests to generate logs
curl http://localhost:8080/api/v1/market/summary
curl http://localhost:8080/api/v1/news
curl http://localhost:8080/api/v1/exchange-rates
```

### 6. Check Logs Are Being Written

```powershell
# Check backend log file exists
docker-compose exec backend ls -la /app/logs/

# Should see: application.json

# View a few log lines
docker-compose exec backend head -5 /app/logs/application.json
```

### 7. Verify Fluent Bit Is Shipping Logs

```powershell
# Check Fluent Bit logs
docker-compose logs fluent-bit --tail 20

# Should see messages like:
# [output:opensearch:opensearch.0] finans-portal-logs-2026.05.08, HTTP status=200
```

### 8. Check Logs in OpenSearch

```powershell
# List indices
curl http://localhost:9200/_cat/indices?v

# Should see: finans-portal-logs-2026.05.08

# Search logs
curl "http://localhost:9200/finans-portal-logs-*/_search?size=5&pretty"
```

### 9. Access OpenSearch Dashboards

1. Open browser: **http://localhost:5601**
2. Wait for page to load (may take 30-60 seconds)
3. Click **"Explore on my own"** if prompted

### 10. Create Index Pattern

1. Click hamburger menu (☰) → **Management** → **Stack Management**
2. Under **Kibana**, click **Index Patterns**
3. Click **Create index pattern**
4. Index pattern name: `finans-portal-logs-*`
5. Click **Next step**
6. Time field: Select `@timestamp`
7. Click **Create index pattern**

### 11. View Logs in Discover

1. Click hamburger menu (☰) → **Discover**
2. Select index pattern: `finans-portal-logs-*`
3. You should see logs! 🎉

### 12. Try Some Searches

In the search bar, try:

```
level: INFO
level: ERROR
username: "john.doe"
endpoint: "/api/v1/portfolio/positions"
durationMs: >100
```

---

## 🎯 Success Checklist

- ✅ All containers running
- ✅ OpenSearch responding on port 9200
- ✅ OpenSearch Dashboards accessible on port 5601
- ✅ Backend writing logs to `/app/logs/application.json`
- ✅ Fluent Bit shipping logs to OpenSearch
- ✅ Logs visible in OpenSearch Dashboards
- ✅ Index pattern created
- ✅ Can search and filter logs

---

## 🐛 Common Issues

### Issue: OpenSearch container exits

**Solution:**
```powershell
# Set vm.max_map_count
wsl -d docker-desktop
sysctl -w vm.max_map_count=262144
exit

# Restart
docker-compose restart opensearch
```

### Issue: No logs in OpenSearch

**Solution:**
```powershell
# 1. Check backend is writing logs
docker-compose exec backend cat /app/logs/application.json | head -1

# 2. Check Fluent Bit is running
docker-compose ps fluent-bit

# 3. Check Fluent Bit logs
docker-compose logs fluent-bit --tail 50

# 4. Restart Fluent Bit
docker-compose restart fluent-bit
```

### Issue: Can't access Dashboards

**Solution:**
```powershell
# 1. Check container is running
docker-compose ps opensearch-dashboards

# 2. Check logs
docker-compose logs opensearch-dashboards --tail 50

# 3. Wait longer (can take 60 seconds)

# 4. Restart if needed
docker-compose restart opensearch-dashboards
```

---

## 📚 Next Steps

1. **Read full documentation:** `docs/OPENSEARCH_LOGGING.md`
2. **Create custom dashboards** in OpenSearch Dashboards
3. **Set up alerts** for errors and slow requests
4. **Explore log patterns** to understand application behavior

---

## 🛑 Stopping Services

```powershell
# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: deletes all logs!)
docker-compose down -v
```

---

**Need Help?** Check `docs/OPENSEARCH_LOGGING.md` for detailed troubleshooting.

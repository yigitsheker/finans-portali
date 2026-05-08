# Kafka Log Pipeline Test Script

Write-Host "=== Kafka Log Pipeline Test ===" -ForegroundColor Cyan
Write-Host ""

# 1. Check if services are running
Write-Host "1. Checking if services are running..." -ForegroundColor Yellow
$services = @("kafka", "backend", "log-consumer", "opensearch")
$allRunning = $true

foreach ($service in $services) {
    try {
        $status = docker inspect -f '{{.State.Status}}' "finans-$service" 2>$null
        if ($status -eq "running") {
            Write-Host "   [OK] $service is running" -ForegroundColor Green
        } else {
            Write-Host "   [ERROR] $service is not running (status: $status)" -ForegroundColor Red
            $allRunning = $false
        }
    } catch {
        Write-Host "   [ERROR] $service container not found" -ForegroundColor Red
        $allRunning = $false
    }
}

if (-not $allRunning) {
    Write-Host ""
    Write-Host "Some services are not running. Please start them with:" -ForegroundColor Red
    Write-Host "docker compose up -d" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# 2. Check Kafka topic
Write-Host "2. Checking Kafka topic..." -ForegroundColor Yellow
try {
    $topics = docker exec finans-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>$null
    if ($topics -match "finans-logs") {
        Write-Host "   [OK] finans-logs topic exists" -ForegroundColor Green
    } else {
        Write-Host "   [INFO] finans-logs topic will be auto-created" -ForegroundColor Cyan
    }
} catch {
    Write-Host "   [ERROR] Could not check Kafka topics" -ForegroundColor Red
}

Write-Host ""

# 3. Generate test traffic
Write-Host "3. Generating test traffic..." -ForegroundColor Yellow
$endpoints = @(
    "http://localhost:8080/api/market/instruments?page=0&size=10",
    "http://localhost:8080/api/exchange-rates",
    "http://localhost:8080/api/news?page=0&size=5"
)

foreach ($endpoint in $endpoints) {
    try {
        $response = Invoke-WebRequest -Uri $endpoint -Method GET -UseBasicParsing -ErrorAction Stop
        Write-Host "   [OK] $endpoint (Status: $($response.StatusCode))" -ForegroundColor Green
    } catch {
        Write-Host "   [ERROR] $endpoint (Error: $($_.Exception.Message))" -ForegroundColor Red
    }
}

Write-Host ""

# 4. Wait for logs to be processed
Write-Host "4. Waiting for logs to be processed (10 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# 5. Check Kafka consumer lag
Write-Host "5. Checking Kafka consumer lag..." -ForegroundColor Yellow
try {
    $consumerGroup = docker exec finans-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group log-consumer-group --describe 2>$null
    if ($consumerGroup) {
        Write-Host "   [OK] Consumer group is active" -ForegroundColor Green
        Write-Host $consumerGroup
    }
} catch {
    Write-Host "   [WARN] Could not check consumer group" -ForegroundColor Yellow
}

Write-Host ""

# 6. Check OpenSearch indices
Write-Host "6. Checking OpenSearch indices..." -ForegroundColor Yellow
try {
    $indices = Invoke-RestMethod -Uri "http://localhost:9200/_cat/indices/finans-logs-*?v" -Method GET
    if ($indices) {
        Write-Host "   [OK] Found log indices in OpenSearch" -ForegroundColor Green
        Write-Host $indices
    }
} catch {
    Write-Host "   [ERROR] Could not check OpenSearch indices" -ForegroundColor Red
}

Write-Host ""

# 7. Count logs in OpenSearch
Write-Host "7. Counting logs in OpenSearch..." -ForegroundColor Yellow
try {
    $today = Get-Date -Format "yyyy-MM-dd"
    $indexName = "finans-logs-$today"
    $count = Invoke-RestMethod -Uri "http://localhost:9200/$indexName/_count" -Method GET
    Write-Host "   [OK] Found $($count.count) logs in $indexName" -ForegroundColor Green
} catch {
    Write-Host "   [WARN] Could not count logs (index may not exist yet)" -ForegroundColor Yellow
}

Write-Host ""

# 8. Sample log query
Write-Host "8. Fetching sample logs..." -ForegroundColor Yellow
try {
    $today = Get-Date -Format "yyyy-MM-dd"
    $indexName = "finans-logs-$today"
    $query = @{
        size = 3
        sort = @(@{ "@timestamp" = @{ order = "desc" } })
    } | ConvertTo-Json -Depth 10
    
    $results = Invoke-RestMethod -Uri "http://localhost:9200/$indexName/_search" -Method POST -Body $query -ContentType "application/json"
    
    if ($results.hits.total.value -gt 0) {
        Write-Host "   [OK] Sample logs:" -ForegroundColor Green
        foreach ($hit in $results.hits.hits) {
            $source = $hit._source
            Write-Host "      - [$($source.level)] $($source.message)" -ForegroundColor Cyan
        }
    }
} catch {
    Write-Host "   [WARN] Could not fetch sample logs" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Test Summary ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Pipeline: Backend → Kafka → Log Consumer → OpenSearch" -ForegroundColor White
Write-Host ""
Write-Host "Services:" -ForegroundColor White
Write-Host "  - Kafka UI:              http://localhost:9092" -ForegroundColor Gray
Write-Host "  - Backend:               http://localhost:8080" -ForegroundColor Gray
Write-Host "  - Log Consumer:          http://localhost:8081/actuator/health" -ForegroundColor Gray
Write-Host "  - OpenSearch:            http://localhost:9200" -ForegroundColor Gray
Write-Host "  - OpenSearch Dashboards: http://localhost:5601" -ForegroundColor Gray
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor White
Write-Host "  1. Open OpenSearch Dashboards: http://localhost:5601" -ForegroundColor Gray
Write-Host "  2. Create index pattern: finans-logs-*" -ForegroundColor Gray
Write-Host "  3. Explore logs in Discover tab" -ForegroundColor Gray
Write-Host ""

# OpenTelemetry Observability Testing Script
# Tests tracing, metrics, and log correlation

Write-Host "=== OpenTelemetry Observability Test ===" -ForegroundColor Green
Write-Host ""

$backendUrl = "http://localhost:8080"
$jaegerUrl = "http://localhost:16686"
$prometheusUrl = "http://localhost:9090"
$grafanaUrl = "http://localhost:3000"

# Step 1: Check if services are running
Write-Host "1. Checking if services are running..." -ForegroundColor Yellow

$services = @(
    @{Name="Backend"; Url="$backendUrl/actuator/health"},
    @{Name="Jaeger"; Url="$jaegerUrl"},
    @{Name="Prometheus"; Url="$prometheusUrl/-/healthy"},
    @{Name="Grafana"; Url="$grafanaUrl/api/health"}
)

$allHealthy = $true
foreach ($service in $services) {
    try {
        $response = Invoke-WebRequest -Uri $service.Url -UseBasicParsing -TimeoutSec 5
        Write-Host "   [OK] $($service.Name) is running" -ForegroundColor Green
    } catch {
        Write-Host "   [ERROR] $($service.Name) is not accessible: $($_.Exception.Message)" -ForegroundColor Red
        $allHealthy = $false
    }
}

if (-not $allHealthy) {
    Write-Host ""
    Write-Host "Some services are not running. Please start them with:" -ForegroundColor Red
    Write-Host "docker compose up -d" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# Step 2: Generate test traffic
Write-Host "2. Generating test traffic..." -ForegroundColor Yellow

$endpoints = @(
    "/api/v1/market/instruments",
    "/api/v1/news",
    "/api/v1/exchange-rates",
    "/api/v1/investment-funds"
)

$traceIds = @()

foreach ($endpoint in $endpoints) {
    try {
        $response = Invoke-WebRequest -Uri "$backendUrl$endpoint" -UseBasicParsing -TimeoutSec 10
        Write-Host "   [OK] $endpoint (Status: $($response.StatusCode))" -ForegroundColor Green
        
        # Try to extract trace ID from response headers
        if ($response.Headers.ContainsKey("traceparent")) {
            $traceparent = $response.Headers["traceparent"]
            # traceparent format: 00-<trace-id>-<span-id>-<flags>
            if ($traceparent -match "00-([0-9a-f]{32})-") {
                $traceIds += $matches[1]
            }
        }
    } catch {
        Write-Host "   [WARN] $endpoint failed: $($_.Exception.Message)" -ForegroundColor Yellow
    }
    Start-Sleep -Milliseconds 500
}

Write-Host ""

# Step 3: Wait for traces to be exported
Write-Host "3. Waiting for traces to be exported (10 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

Write-Host ""

# Step 4: Check Jaeger for traces
Write-Host "4. Checking Jaeger for traces..." -ForegroundColor Yellow

try {
    $jaegerApiUrl = "$jaegerUrl/api/services"
    $response = Invoke-WebRequest -Uri $jaegerApiUrl -UseBasicParsing
    $services = ($response.Content | ConvertFrom-Json).data
    
    if ($services -contains "finans-backend") {
        Write-Host "   [OK] finans-backend service found in Jaeger" -ForegroundColor Green
        
        # Try to get traces
        $tracesUrl = "$jaegerUrl/api/traces?service=finans-backend&limit=10"
        $tracesResponse = Invoke-WebRequest -Uri $tracesUrl -UseBasicParsing
        $traces = ($tracesResponse.Content | ConvertFrom-Json).data
        
        if ($traces.Count -gt 0) {
            Write-Host "   [OK] Found $($traces.Count) traces" -ForegroundColor Green
            Write-Host "   Sample trace ID: $($traces[0].traceID)" -ForegroundColor Cyan
        } else {
            Write-Host "   [WARN] No traces found yet. Try generating more traffic." -ForegroundColor Yellow
        }
    } else {
        Write-Host "   [WARN] finans-backend service not found in Jaeger" -ForegroundColor Yellow
        Write-Host "   Available services: $($services -join ', ')" -ForegroundColor Gray
    }
} catch {
    Write-Host "   [ERROR] Could not check Jaeger: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""

# Step 5: Check Prometheus for metrics
Write-Host "5. Checking Prometheus for metrics..." -ForegroundColor Yellow

try {
    # Check if backend target is up
    $targetsUrl = "$prometheusUrl/api/v1/targets"
    $response = Invoke-WebRequest -Uri $targetsUrl -UseBasicParsing
    $targets = ($response.Content | ConvertFrom-Json).data.activeTargets
    
    $backendTarget = $targets | Where-Object { $_.labels.job -eq "finans-backend" }
    
    if ($backendTarget -and $backendTarget.health -eq "up") {
        Write-Host "   [OK] finans-backend target is UP in Prometheus" -ForegroundColor Green
        Write-Host "   Last scrape: $($backendTarget.lastScrape)" -ForegroundColor Gray
    } else {
        Write-Host "   [WARN] finans-backend target is not UP" -ForegroundColor Yellow
    }
    
    # Query for HTTP metrics
    $queryUrl = "$prometheusUrl/api/v1/query?query=http_server_requests_seconds_count"
    $response = Invoke-WebRequest -Uri $queryUrl -UseBasicParsing
    $result = ($response.Content | ConvertFrom-Json).data.result
    
    if ($result.Count -gt 0) {
        Write-Host "   [OK] Found HTTP request metrics" -ForegroundColor Green
        $totalRequests = ($result | Measure-Object -Property {[double]$_.value[1]} -Sum).Sum
        Write-Host "   Total HTTP requests: $totalRequests" -ForegroundColor Cyan
    } else {
        Write-Host "   [WARN] No HTTP metrics found yet" -ForegroundColor Yellow
    }
} catch {
    Write-Host "   [ERROR] Could not check Prometheus: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""

# Step 6: Check Grafana datasources
Write-Host "6. Checking Grafana datasources..." -ForegroundColor Yellow

try {
    $datasourcesUrl = "$grafanaUrl/api/datasources"
    $response = Invoke-WebRequest -Uri $datasourcesUrl -UseBasicParsing -Headers @{Authorization="Basic YWRtaW46YWRtaW4="}
    $datasources = $response.Content | ConvertFrom-Json
    
    $prometheus = $datasources | Where-Object { $_.type -eq "prometheus" }
    $jaeger = $datasources | Where-Object { $_.type -eq "jaeger" }
    
    if ($prometheus) {
        Write-Host "   [OK] Prometheus datasource configured" -ForegroundColor Green
    } else {
        Write-Host "   [WARN] Prometheus datasource not found" -ForegroundColor Yellow
    }
    
    if ($jaeger) {
        Write-Host "   [OK] Jaeger datasource configured" -ForegroundColor Green
    } else {
        Write-Host "   [WARN] Jaeger datasource not found" -ForegroundColor Yellow
    }
} catch {
    Write-Host "   [WARN] Could not check Grafana datasources: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host ""

# Step 7: Summary and next steps
Write-Host "=== Test Summary ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Services:" -ForegroundColor White
Write-Host "  - Jaeger UI:    $jaegerUrl" -ForegroundColor Cyan
Write-Host "  - Prometheus:   $prometheusUrl" -ForegroundColor Cyan
Write-Host "  - Grafana:      $grafanaUrl (admin/admin)" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor White
Write-Host "  1. Open Jaeger UI and search for 'finans-backend' service" -ForegroundColor Gray
Write-Host "  2. Open Prometheus and query: http_server_requests_seconds_count" -ForegroundColor Gray
Write-Host "  3. Open Grafana and explore metrics" -ForegroundColor Gray
Write-Host "  4. Check OpenSearch Dashboards for logs with trace_id" -ForegroundColor Gray
Write-Host ""
Write-Host "Example Prometheus Queries:" -ForegroundColor White
Write-Host "  - HTTP request count: http_server_requests_seconds_count{job=\"finans-backend\"}" -ForegroundColor Gray
Write-Host "  - HTTP 95th percentile: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))" -ForegroundColor Gray
Write-Host "  - JVM memory: jvm_memory_used_bytes{job=\"finans-backend\",area=\"heap\"}" -ForegroundColor Gray
Write-Host ""

if ($traceIds.Count -gt 0) {
    Write-Host "Sample Trace IDs (search in Jaeger):" -ForegroundColor White
    $traceIds | Select-Object -First 3 | ForEach-Object {
        Write-Host "  - $_" -ForegroundColor Cyan
    }
    Write-Host ""
}

Write-Host "For detailed documentation, see:" -ForegroundColor White
Write-Host "  docs/OPENTELEMETRY_SETUP.md" -ForegroundColor Cyan
Write-Host ""

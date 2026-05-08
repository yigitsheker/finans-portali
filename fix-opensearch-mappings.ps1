# OpenSearch Field Mapping Fix Script
# This script fixes durationMs and statusCode field mappings

Write-Host "=== OpenSearch Field Mapping Fix ===" -ForegroundColor Green
Write-Host ""

# OpenSearch URL
$opensearchUrl = "http://localhost:9200"

# 1. Delete existing indices
Write-Host "1. Deleting existing indices..." -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri "$opensearchUrl/finans-portal-logs-2026.05.07" -Method DELETE -UseBasicParsing | Out-Null
    Write-Host "   [OK] finans-portal-logs-2026.05.07 deleted" -ForegroundColor Green
} catch {
    Write-Host "   [SKIP] finans-portal-logs-2026.05.07 not found (already deleted)" -ForegroundColor Gray
}

try {
    Invoke-WebRequest -Uri "$opensearchUrl/finans-portal-logs-2026.05.08" -Method DELETE -UseBasicParsing | Out-Null
    Write-Host "   [OK] finans-portal-logs-2026.05.08 deleted" -ForegroundColor Green
} catch {
    Write-Host "   [SKIP] finans-portal-logs-2026.05.08 not found (already deleted)" -ForegroundColor Gray
}

Write-Host ""

# 2. Create index template
Write-Host "2. Creating index template..." -ForegroundColor Yellow

$templateBody = @{
    index_patterns = @("finans-portal-logs-*")
    template = @{
        settings = @{
            number_of_shards = 1
            number_of_replicas = 0
            "index.refresh_interval" = "5s"
        }
        mappings = @{
            properties = @{
                "@timestamp" = @{
                    type = "date"
                }
                level = @{
                    type = "keyword"
                }
                message = @{
                    type = "text"
                    fields = @{
                        keyword = @{
                            type = "keyword"
                            ignore_above = 256
                        }
                    }
                }
                logger_name = @{
                    type = "keyword"
                }
                thread_name = @{
                    type = "keyword"
                }
                service = @{
                    type = "keyword"
                }
                environment = @{
                    type = "keyword"
                }
                # HTTP Request/Response Fields
                requestId = @{
                    type = "keyword"
                }
                endpoint = @{
                    type = "keyword"
                }
                httpMethod = @{
                    type = "keyword"
                }
                statusCode = @{
                    type = "integer"
                }
                durationMs = @{
                    type = "long"
                }
                clientIp = @{
                    type = "ip"
                }
                # User Fields
                userId = @{
                    type = "keyword"
                }
                username = @{
                    type = "keyword"
                }
                # Tracing Fields
                traceId = @{
                    type = "keyword"
                }
                spanId = @{
                    type = "keyword"
                }
                # Stack Trace
                stack_trace = @{
                    type = "text"
                }
            }
        }
    }
    priority = 100
} | ConvertTo-Json -Depth 10

try {
    $response = Invoke-WebRequest -Uri "$opensearchUrl/_index_template/finans-portal-logs-template" `
        -Method PUT `
        -Body $templateBody `
        -ContentType "application/json" `
        -UseBasicParsing
    
    Write-Host "   [OK] Index template created" -ForegroundColor Green
} catch {
    Write-Host "   [ERROR] Failed to create index template: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 3. Verify template
Write-Host "3. Verifying template..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$opensearchUrl/_index_template/finans-portal-logs-template" `
        -Method GET `
        -UseBasicParsing
    
    Write-Host "   [OK] Template verified successfully" -ForegroundColor Green
} catch {
    Write-Host "   [ERROR] Template verification failed" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 4. Generate new logs
Write-Host "4. Generating new logs (sending requests to backend)..." -ForegroundColor Yellow
Write-Host "   Make sure backend is running!" -ForegroundColor Cyan

$backendUrl = "http://localhost:8080"

# Test endpoints
$testEndpoints = @(
    "/api/v1/market/instruments",
    "/api/v1/market/quotes",
    "/api/v1/news",
    "/api/v1/exchange-rates",
    "/api/v1/investment-funds"
)

Write-Host "   Sending 10 test requests..." -ForegroundColor Cyan

for ($i = 1; $i -le 10; $i++) {
    $endpoint = $testEndpoints | Get-Random
    try {
        Invoke-WebRequest -Uri "$backendUrl$endpoint" -Method GET -UseBasicParsing -TimeoutSec 5 | Out-Null
        Write-Host "   [OK] Request $i/10: $endpoint" -ForegroundColor Green
    } catch {
        Write-Host "   [WARN] Request $i/10: $endpoint (error: $($_.Exception.Message))" -ForegroundColor Yellow
    }
    Start-Sleep -Milliseconds 500
}

Write-Host ""

# 5. Wait for logs to reach OpenSearch
Write-Host "5. Waiting for logs to reach OpenSearch (30 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

Write-Host ""

# 6. Check new indices
Write-Host "6. Checking new indices..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$opensearchUrl/_cat/indices/finans-portal-logs-*?v" `
        -Method GET `
        -UseBasicParsing
    
    Write-Host $response.Content
} catch {
    Write-Host "   [WARN] No indices created yet (normal, wait a bit more)" -ForegroundColor Yellow
}

Write-Host ""

# 7. Check durationMs field mapping
Write-Host "7. Checking durationMs field mapping..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$opensearchUrl/finans-portal-logs-*/_mapping/field/durationMs?pretty" `
        -Method GET `
        -UseBasicParsing
    
    $mapping = $response.Content | ConvertFrom-Json
    
    # Check mapping for each index
    $allCorrect = $true
    foreach ($indexName in $mapping.PSObject.Properties.Name) {
        $indexMapping = $mapping.$indexName.mappings.durationMs.mapping.durationMs
        $fieldType = $indexMapping.type
        
        if ($fieldType -eq "long") {
            Write-Host "   [OK] $indexName : durationMs = long (CORRECT)" -ForegroundColor Green
        } else {
            Write-Host "   [ERROR] $indexName : durationMs = $fieldType (WRONG)" -ForegroundColor Red
            $allCorrect = $false
        }
    }
    
    if ($allCorrect) {
        Write-Host ""
        Write-Host "=== SUCCESS! ===" -ForegroundColor Green
        Write-Host "Field mappings fixed. Average aggregation will now work." -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "=== ERROR! ===" -ForegroundColor Red
        Write-Host "Field mappings are still wrong. Make sure template is applied correctly." -ForegroundColor Red
    }
    
} catch {
    Write-Host "   [WARN] No durationMs field yet (generate more logs)" -ForegroundColor Yellow
}

Write-Host ""

# 8. Check statusCode field mapping
Write-Host "8. Checking statusCode field mapping..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$opensearchUrl/finans-portal-logs-*/_mapping/field/statusCode?pretty" `
        -Method GET `
        -UseBasicParsing
    
    $mapping = $response.Content | ConvertFrom-Json
    
    # Check mapping for each index
    foreach ($indexName in $mapping.PSObject.Properties.Name) {
        $indexMapping = $mapping.$indexName.mappings.statusCode.mapping.statusCode
        $fieldType = $indexMapping.type
        
        if ($fieldType -eq "integer") {
            Write-Host "   [OK] $indexName : statusCode = integer (CORRECT)" -ForegroundColor Green
        } else {
            Write-Host "   [ERROR] $indexName : statusCode = $fieldType (WRONG)" -ForegroundColor Red
        }
    }
    
} catch {
    Write-Host "   [WARN] No statusCode field yet (generate more logs)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Next Steps ===" -ForegroundColor Cyan
Write-Host "1. Go to OpenSearch Dashboards: http://localhost:5601" -ForegroundColor White
Write-Host "2. Management -> Index Patterns -> finans-portal-logs-* -> Refresh (top right)" -ForegroundColor White
Write-Host "3. Visualize -> Create visualization -> Average aggregation -> select durationMs field" -ForegroundColor White
Write-Host "4. Or import opensearch-dashboard-finans-portal.ndjson file" -ForegroundColor White
Write-Host ""

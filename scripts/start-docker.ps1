# Finans Portali Docker Quick Start Script

Write-Host "==================================" -ForegroundColor Cyan
Write-Host "Finans Portali Docker Setup" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host ""

# Check if Docker is running
Write-Host "Checking Docker..." -ForegroundColor Yellow
try {
    docker info | Out-Null
    Write-Host "✓ Docker is running" -ForegroundColor Green
} catch {
    Write-Host "✗ Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

# Check if .env file exists
if (-not (Test-Path ".env")) {
    Write-Host ""
    Write-Host "Creating .env file from .env.example..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
    Write-Host "✓ .env file created" -ForegroundColor Green
    Write-Host ""
    Write-Host "Please edit .env file and configure your settings (especially email if needed)" -ForegroundColor Yellow
    Write-Host "Press any key to continue..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}

# Stop existing containers
Write-Host ""
Write-Host "Stopping existing containers..." -ForegroundColor Yellow
docker-compose down 2>$null

# Build images
Write-Host ""
Write-Host "Building Docker images (this may take a few minutes)..." -ForegroundColor Yellow
docker-compose build

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Build failed" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Build completed" -ForegroundColor Green

# Start containers
Write-Host ""
Write-Host "Starting containers..." -ForegroundColor Yellow
docker-compose up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Failed to start containers" -ForegroundColor Red
    exit 1
}

# Wait for services to be healthy
Write-Host ""
Write-Host "Waiting for services to be healthy..." -ForegroundColor Yellow
Write-Host "(This may take 1-2 minutes)" -ForegroundColor Gray

$maxAttempts = 60
$attempt = 0
$allHealthy = $false

while ($attempt -lt $maxAttempts -and -not $allHealthy) {
    $attempt++
    Start-Sleep -Seconds 5
    
    $containers = docker-compose ps --format json | ConvertFrom-Json
    $unhealthy = $containers | Where-Object { $_.Health -ne "healthy" -and $_.State -eq "running" }
    
    if ($unhealthy.Count -eq 0) {
        $allHealthy = $true
    } else {
        Write-Host "." -NoNewline -ForegroundColor Gray
    }
}

Write-Host ""

if ($allHealthy) {
    Write-Host "✓ All services are healthy" -ForegroundColor Green
} else {
    Write-Host "⚠ Some services may not be fully ready yet" -ForegroundColor Yellow
}

# Show container status
Write-Host ""
Write-Host "Container Status:" -ForegroundColor Cyan
docker-compose ps

# Show access URLs
Write-Host ""
Write-Host "==================================" -ForegroundColor Cyan
Write-Host "Application URLs:" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host "Frontend:        http://localhost" -ForegroundColor White
Write-Host "Backend API:     http://localhost:8080" -ForegroundColor White
Write-Host "Keycloak Admin:  http://localhost:8090" -ForegroundColor White
Write-Host "                 (admin/admin)" -ForegroundColor Gray
Write-Host ""
Write-Host "==================================" -ForegroundColor Cyan
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host "1. Configure Keycloak (see DOCKER_SETUP.md)" -ForegroundColor White
Write-Host "2. Create a test user in Keycloak" -ForegroundColor White
Write-Host "3. Access the application at http://localhost" -ForegroundColor White
Write-Host ""
Write-Host "Useful Commands:" -ForegroundColor Cyan
Write-Host "  docker-compose logs -f          # View logs" -ForegroundColor Gray
Write-Host "  docker-compose ps               # Check status" -ForegroundColor Gray
Write-Host "  docker-compose down             # Stop all" -ForegroundColor Gray
Write-Host "  docker-compose restart backend  # Restart backend" -ForegroundColor Gray
Write-Host ""
Write-Host "For detailed documentation, see DOCKER_SETUP.md" -ForegroundColor Yellow
Write-Host ""

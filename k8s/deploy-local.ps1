# Local Docker Desktop Kubernetes deploy helper.
#
# Walks the full cycle:
#   1. Verify Docker Desktop K8s is up.
#   2. Build backend + frontend images with the tags the dev overlay expects.
#   3. Provision the namespace + Secret objects (one-time, idempotent).
#   4. Apply the dev overlay.
#   5. Wait for rollouts.
#   6. Start three port-forwards (frontend, keycloak, backend) in background.
#
# Usage:
#   .\k8s\deploy-local.ps1                 # full cycle
#   .\k8s\deploy-local.ps1 -SkipBuild      # apply only (after code changes
#                                          #   that don't need a rebuild)
#   .\k8s\deploy-local.ps1 -Clean          # tear everything down first
#
# ASCII-only output / comments so Windows PowerShell 5.1 parses cleanly
# without requiring a UTF-8 BOM on the file.

[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$nsDev = 'finans-portali-dev'

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

# 1. Sanity checks ------------------------------------------------------
Write-Step '1/6  Cluster reachable?'
try {
    $ctx = kubectl config current-context 2>$null
    if (-not $ctx) { throw 'no context' }
    kubectl get nodes --request-timeout=5s | Out-Null
    Write-Host "OK - context: $ctx"
} catch {
    Write-Host 'Cluster unreachable. Enable Docker Desktop K8s:' -ForegroundColor Red
    Write-Host '  Docker Desktop -> Settings -> Kubernetes -> "Enable Kubernetes"'
    Write-Host '  Wait until the bottom-left K8s status turns green, then re-run.'
    exit 1
}

# 2. Optional teardown --------------------------------------------------
if ($Clean) {
    Write-Step 'Tearing down previous deploy'
    kubectl delete -k "$repoRoot\k8s\overlays\dev" --ignore-not-found
    kubectl delete pvc --all -n $nsDev --ignore-not-found
    Start-Sleep 2
}

# 3. Build images -------------------------------------------------------
if (-not $SkipBuild) {
    Write-Step '2/6  Building backend image'
    docker build -t finans-backend:local "$repoRoot\backend"
    if ($LASTEXITCODE -ne 0) { throw 'backend build failed' }

    Write-Step '3/6  Building frontend image'
    docker build -t finans-frontend:local "$repoRoot\frontend"
    if ($LASTEXITCODE -ne 0) { throw 'frontend build failed' }
} else {
    Write-Step '2-3/6  (Skipping image builds, -SkipBuild passed)'
}

# 4. Namespace + Secrets ------------------------------------------------
Write-Step '4/6  Namespace + Secrets (idempotent, skipped if already there)'
kubectl create namespace $nsDev --dry-run=client -o yaml | kubectl apply -f -

# Each Secret is applied from its example file as-is. For real deployments
# you would copy these, edit values, and apply manually (see README). For
# pure local dev the example defaults are fine to round-trip with.
$secretFiles = @(
    "$repoRoot\k8s\base\postgres\secret.example.yaml",
    "$repoRoot\k8s\base\keycloak\secret.example.yaml",
    "$repoRoot\k8s\base\backend\secret.example.yaml"
)
foreach ($f in $secretFiles) {
    kubectl apply -n $nsDev -f $f
}

# 5. Apply overlay ------------------------------------------------------
Write-Step '5/6  Applying dev overlay'
kubectl apply -k "$repoRoot\k8s\overlays\dev"

Write-Step 'Waiting for postgres + keycloak + backend rollouts (up to 5 min)'
kubectl rollout status statefulset/postgres -n $nsDev --timeout=120s
kubectl rollout status deploy/keycloak      -n $nsDev --timeout=180s
kubectl rollout status deploy/backend       -n $nsDev --timeout=180s
kubectl rollout status deploy/frontend      -n $nsDev --timeout=60s

# 6. Port-forward (background) ------------------------------------------
Write-Step '6/6  Starting port-forwards (Ctrl+C to stop)'
Get-Job | Where-Object { $_.Name -like 'fp-*' } | Stop-Job -ErrorAction SilentlyContinue
Get-Job | Where-Object { $_.Name -like 'fp-*' } | Remove-Job -ErrorAction SilentlyContinue

Start-Job -Name 'fp-frontend' -ScriptBlock {
    kubectl -n $using:nsDev port-forward svc/frontend 5173:80
} | Out-Null
Start-Job -Name 'fp-keycloak' -ScriptBlock {
    kubectl -n $using:nsDev port-forward svc/keycloak 8090:8080
} | Out-Null
Start-Job -Name 'fp-backend' -ScriptBlock {
    kubectl -n $using:nsDev port-forward svc/backend 8080:8080
} | Out-Null

Start-Sleep 2
Write-Host ''
Write-Host 'Ready. Open:' -ForegroundColor Green
Write-Host '  Frontend       -> http://localhost:5173'
Write-Host '  Keycloak admin -> http://localhost:8090/admin   (password from keycloak-secret)'
Write-Host '  Backend API    -> http://localhost:8080/actuator/health'
Write-Host ''
Write-Host 'Useful follow-ups:'
Write-Host '  kubectl -n finans-portali-dev logs deploy/backend --tail 100 -f'
Write-Host '  kubectl -n finans-portali-dev get pods -w'
Write-Host '  Get-Job fp-*                                          # port-forward job status'
Write-Host '  Get-Job fp-* | Stop-Job; Get-Job fp-* | Remove-Job    # stop forwards'

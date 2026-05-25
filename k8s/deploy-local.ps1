# Local Kubernetes deploy helper using `kind` (Kubernetes IN Docker).
#
# kind runs the cluster as a Docker container and ships with the
# `kind load docker-image` command that explicitly pushes a local
# image into the cluster's containerd cache. That sidesteps the
# Docker-Desktop-K8s image-visibility headache: docker build and
# the cluster always see the same images because we load them by hand.
#
# Why kind instead of Docker Desktop K8s for this project:
#   - Docker Desktop K8s' containerd is a separate store from the
#     docker daemon's, even with "Use containerd for pulling and
#     storing images" enabled. Locally built tags don't show up in
#     the cluster and pods fail with ErrImageNeverPull.
#   - kind makes that explicit with `kind load docker-image`. Same
#     docker build cache, same image, no namespace games.
#
# Walks the full cycle:
#   1. Verify kind is installed.
#   2. Create the `finans-portali` kind cluster if it doesn't exist.
#   3. Build backend + frontend images.
#   4. Load both into the kind cluster's containerd.
#   5. Provision namespace + Secrets (idempotent).
#   6. Apply the dev overlay.
#   7. Wait for rollouts.
#   8. Start three port-forwards in background.
#
# Usage:
#   .\k8s\deploy-local.ps1                 # full cycle
#   .\k8s\deploy-local.ps1 -SkipBuild      # apply only (no rebuild)
#   .\k8s\deploy-local.ps1 -Clean          # delete cluster + redeploy
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
$cluster  = 'finans-portali'
$nsDev    = 'finans-portali-dev'

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

# 1. kind installed? ----------------------------------------------------
Write-Step '1/8  Checking kind is installed'
if (-not (Get-Command kind -ErrorAction SilentlyContinue)) {
    Write-Host 'kind is not on PATH.' -ForegroundColor Red
    Write-Host ''
    Write-Host 'Install with one of:' -ForegroundColor Cyan
    Write-Host '  winget install Kubernetes.kind' -ForegroundColor Cyan
    Write-Host '  choco install kind' -ForegroundColor Cyan
    Write-Host ''
    Write-Host 'After installing, open a new PowerShell window and re-run this script.'
    exit 1
}
Write-Host 'OK - kind is available'

# 2. Cluster exists? ----------------------------------------------------
# `kind get clusters` writes "No kind clusters found." to stderr when the
# list is empty. Under $ErrorActionPreference = 'Stop' PowerShell 5.1
# treats that as a terminating error, so we briefly relax the policy
# around the call and filter the merged output by hand.
Write-Step '2/8  Ensuring kind cluster exists'
$prevErr = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$kindClustersOutput = & kind get clusters 2>&1 | Out-String
$ErrorActionPreference = $prevErr

$existing = $kindClustersOutput -split "`r?`n" |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -and $_ -notmatch '^No kind clusters' }

if ($Clean -and ($existing -contains $cluster)) {
    Write-Host 'Tearing down previous cluster (-Clean passed)'
    kind delete cluster --name $cluster
    $existing = @()
}
if ($existing -notcontains $cluster) {
    Write-Host "Creating kind cluster '$cluster' (first time can take ~1 min, ~150MB node image download)..."
    kind create cluster --name $cluster
    if ($LASTEXITCODE -ne 0) { throw 'kind create cluster failed' }
} else {
    Write-Host "Cluster '$cluster' already exists, re-using"
}
kubectl config use-context "kind-$cluster" | Out-Null
Write-Host "Active context: $(kubectl config current-context)"

# 3. Build images -------------------------------------------------------
if (-not $SkipBuild) {
    Write-Step '3/8  Building backend image'
    docker build -t finans-backend:local "$repoRoot\backend"
    if ($LASTEXITCODE -ne 0) { throw 'backend build failed' }

    Write-Step '4/8  Building frontend image'
    docker build -t finans-frontend:local "$repoRoot\frontend"
    if ($LASTEXITCODE -ne 0) { throw 'frontend build failed' }
} else {
    Write-Step '3-4/8  (Skipping image builds, -SkipBuild passed)'
}

# 4. Load images into kind ----------------------------------------------
# `kind load docker-image` reads from the host docker daemon and writes
# directly into the kind node's containerd image cache. After this the
# cluster sees the images at the same name/tag, so imagePullPolicy:Never
# works.
Write-Step '5/8  Loading images into the kind cluster'
kind load docker-image finans-backend:local --name $cluster
if ($LASTEXITCODE -ne 0) { throw 'failed to load finans-backend:local into kind' }
kind load docker-image finans-frontend:local --name $cluster
if ($LASTEXITCODE -ne 0) { throw 'failed to load finans-frontend:local into kind' }
Write-Host 'OK - both images available to the cluster'

# 5. Namespace + Secrets ------------------------------------------------
Write-Step '6/8  Namespace + Secrets (idempotent, skipped if already there)'
kubectl create namespace $nsDev --dry-run=client -o yaml | kubectl apply -f -

$secretFiles = @(
    "$repoRoot\k8s\base\postgres\secret.example.yaml",
    "$repoRoot\k8s\base\keycloak\secret.example.yaml",
    "$repoRoot\k8s\base\backend\secret.example.yaml"
)
foreach ($f in $secretFiles) {
    kubectl apply -n $nsDev -f $f
}

# 6. Apply overlay ------------------------------------------------------
Write-Step '7/8  Applying dev overlay'
kubectl apply -k "$repoRoot\k8s\overlays\dev"

Write-Step 'Waiting for postgres + keycloak + backend rollouts (up to 5 min)'
kubectl rollout status statefulset/postgres -n $nsDev --timeout=120s
kubectl rollout status deploy/keycloak      -n $nsDev --timeout=180s
kubectl rollout status deploy/backend       -n $nsDev --timeout=180s
kubectl rollout status deploy/frontend      -n $nsDev --timeout=60s

# 7. Port-forward (background) ------------------------------------------
Write-Step '8/8  Starting port-forwards'
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
Write-Host ''
Write-Host 'To switch back to Docker Desktop K8s:'
Write-Host '  kubectl config use-context docker-desktop'

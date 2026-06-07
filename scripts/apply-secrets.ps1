# apply-secrets.ps1 — create/update the 3 GKE app secrets from .secrets.local.env
# in one command, so you don't hand-edit secret YAMLs each time the cluster
# comes back up.
#
#   1) cp .secrets.local.env.example .secrets.local.env  (once)
#   2) fill real values in .secrets.local.env            (once)
#   3) .\apply-secrets.ps1                                (every fresh cluster)
#
# Idempotent: re-running updates the secrets in place (create --dry-run | apply).
# Requires: kubectl already pointed at the cluster
#   (gcloud container clusters get-credentials finans-portali --location europe-west1)

$ErrorActionPreference = "Stop"
$ns = "finans-portali"
$envFile = Join-Path (Split-Path -Parent $PSScriptRoot) ".secrets.local.env"

if (-not (Test-Path $envFile)) {
    Write-Error ".secrets.local.env not found. Copy .secrets.local.env.example to it and fill the values first."
    exit 1
}

# Parse KEY=value lines (ignore blanks/comments). Values may contain '=' (split on first only).
$cfg = @{}
foreach ($line in Get-Content $envFile) {
    $t = $line.Trim()
    if ($t -eq "" -or $t.StartsWith("#")) { continue }
    $i = $t.IndexOf("=")
    if ($i -lt 1) { continue }
    $key = $t.Substring(0, $i).Trim()
    $val = $t.Substring($i + 1).Trim()
    $cfg[$key] = $val
}

function Need($k) {
    if (-not $cfg.ContainsKey($k) -or [string]::IsNullOrWhiteSpace($cfg[$k]) -or $cfg[$k] -like "change-me*") {
        Write-Error "Missing/placeholder value for '$k' in .secrets.local.env"
        exit 1
    }
    return $cfg[$k]
}
function Opt($k) { if ($cfg.ContainsKey($k)) { return $cfg[$k] } else { return "" } }

# Guard the classic footgun: Keycloak's DB password must match Postgres'.
if ($cfg["KC_DB_PASSWORD"] -ne $cfg["POSTGRES_PASSWORD"]) {
    Write-Error "KC_DB_PASSWORD must equal POSTGRES_PASSWORD (Keycloak connects to the same DB user)."
    exit 1
}

Write-Host "Ensuring namespace '$ns'..."
kubectl create namespace $ns --dry-run=client -o yaml | kubectl apply -f -

# Helper: create-or-update one Opaque secret from literal key=value pairs.
function Apply-Secret($name, [string[]]$pairs) {
    $args = @("create", "secret", "generic", $name, "-n", $ns, "--dry-run=client", "-o", "yaml")
    foreach ($p in $pairs) { $args += "--from-literal=$p" }
    & kubectl @args | kubectl apply -f -
}

Write-Host "Applying postgres-secret..."
Apply-Secret "postgres-secret" @(
    "POSTGRES_DB=$(Need 'POSTGRES_DB')",
    "POSTGRES_USER=$(Need 'POSTGRES_USER')",
    "POSTGRES_PASSWORD=$(Need 'POSTGRES_PASSWORD')"
)

Write-Host "Applying keycloak-secret..."
Apply-Secret "keycloak-secret" @(
    "KC_DB_USERNAME=$(Need 'KC_DB_USERNAME')",
    "KC_DB_PASSWORD=$(Need 'KC_DB_PASSWORD')",
    "KEYCLOAK_ADMIN=$(Need 'KEYCLOAK_ADMIN')",
    "KEYCLOAK_ADMIN_PASSWORD=$(Need 'KEYCLOAK_ADMIN_PASSWORD')",
    "KC_BACKEND_CLIENT_SECRET=$(Need 'KC_BACKEND_CLIENT_SECRET')"
)

Write-Host "Applying backend-secret..."
Apply-Secret "backend-secret" @(
    "APP_EVDS_API_KEY=$(Opt 'APP_EVDS_API_KEY')",
    "APP_FRED_API_KEY=$(Opt 'APP_FRED_API_KEY')",
    "APP_TEFAS_BEARER_TOKEN=$(Opt 'APP_TEFAS_BEARER_TOKEN')",
    "SPRING_MAIL_USERNAME=$(Opt 'SPRING_MAIL_USERNAME')",
    "SPRING_MAIL_PASSWORD=$(Opt 'SPRING_MAIL_PASSWORD')",
    "APP_LLM_API_KEY=$(Opt 'APP_LLM_API_KEY')"
)

Write-Host ""
Write-Host "Done. Secrets in namespace '$ns':"
kubectl -n $ns get secrets postgres-secret keycloak-secret backend-secret

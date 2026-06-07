# PowerShell wrapper for the Makefile so Windows users without GNU make
# can use the same commands. Mirrors the most-used targets; for the rest,
# either install make (winget install GnuWin32.Make) or run the underlying
# docker commands directly.
#
# Usage:
#   .\make.ps1 dev          # HMR dev mode
#   .\make.ps1 front        # fast frontend rebuild
#   .\make.ps1 up           # start prod stack
#   .\make.ps1 down         # stop everything
#   .\make.ps1 logs-frontend
#   .\make.ps1 help

param(
    [Parameter(Position = 0)]
    [string]$Target = "help"
)

$ErrorActionPreference = "Stop"
# Resolve compose file paths from the repo root regardless of the caller's CWD.
Set-Location (Split-Path -Parent $PSScriptRoot)

function Invoke-Compose {
    param([string[]]$ComposeArgs)
    & docker compose @ComposeArgs
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed (exit $LASTEXITCODE)" }
}

switch ($Target) {
    "help" {
        @'
Available targets:
  dev               HMR dev mode - code changes auto-reload, no rebuild needed
  dev-down          Stop the dev stack
  front             Fast frontend rebuild (uses npm cache, ~20-40s)
  rebuild-frontend  Full frontend rebuild (--no-cache, slow)
  up                Start the prod stack
  down              Stop everything
  restart           Restart all services
  ps                Show running containers
  logs              Follow logs for all services
  logs-frontend     Follow frontend logs
  logs-backend      Follow backend logs
  health            Check service health endpoints
  rebuild           Full rebuild of every service

  For anything not listed, install GNU make (winget install GnuWin32.Make)
  or read the Makefile and run the underlying docker compose command.
'@
    }

    "dev" {
        Write-Host "Starting dev stack - frontend served by Vite with HMR"
        Write-Host "(backend, postgres, keycloak all keep their prod images)"
        # --build forces compose to build the Dockerfile.dev image; without
        # it the cached nginx prod image gets reused silently.
        Invoke-Compose @("-f", "docker-compose.yml", "-f", "docker-compose.dev.yml", "up", "-d", "--build")
        Write-Host ""
        Write-Host "Dev stack up. Open http://localhost  (same URL as prod, HMR on top)"
        Write-Host "Frontend logs: .\make.ps1 logs-frontend"
        Write-Host "Switch back to prod: .\make.ps1 dev-down; .\make.ps1 up"
    }

    "dev-down" {
        Invoke-Compose @("-f", "docker-compose.yml", "-f", "docker-compose.dev.yml", "down")
    }

    "front" {
        Invoke-Compose @("build", "frontend")
        Invoke-Compose @("up", "-d", "frontend")
        Write-Host ""
        Write-Host "Frontend rebuilt. Hard refresh the browser (Ctrl+Shift+R) to bust the asset cache."
    }

    "rebuild-frontend" {
        Invoke-Compose @("stop", "frontend")
        Invoke-Compose @("build", "--no-cache", "frontend")
        Invoke-Compose @("up", "-d", "frontend")
    }

    "up"      { Invoke-Compose @("up", "-d") }
    "down"    { Invoke-Compose @("down") }
    "restart" { Invoke-Compose @("restart") }
    "ps"      { Invoke-Compose @("ps") }
    "build"   { Invoke-Compose @("build") }
    "rebuild" {
        Invoke-Compose @("down")
        Invoke-Compose @("build", "--no-cache")
        Invoke-Compose @("up", "-d")
    }

    "logs"          { Invoke-Compose @("logs", "-f") }
    "logs-frontend" { Invoke-Compose @("logs", "-f", "frontend") }
    "logs-backend"  { Invoke-Compose @("logs", "-f", "backend") }
    "logs-postgres" { Invoke-Compose @("logs", "-f", "postgres") }
    "logs-keycloak" { Invoke-Compose @("logs", "-f", "keycloak") }

    "health" {
        Write-Host "Checking service health..."
        try { Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health | Out-Null; Write-Host "Backend: OK" }
        catch { Write-Host "Backend: UNHEALTHY" }
        try { Invoke-WebRequest -UseBasicParsing http://localhost:80              | Out-Null; Write-Host "Frontend: OK" }
        catch { Write-Host "Frontend: UNHEALTHY" }
        try { Invoke-WebRequest -UseBasicParsing http://localhost:8090/health/ready | Out-Null; Write-Host "Keycloak: OK" }
        catch { Write-Host "Keycloak: UNHEALTHY" }
    }

    default {
        Write-Host "Unknown target: $Target"
        Write-Host "Run .\make.ps1 help for the list."
        exit 1
    }
}

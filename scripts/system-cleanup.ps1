# Haftalık sistem temizlik scripti
# Windows Scheduled Task "FinansPortali-SystemCleanup" tarafından her Pazar 04:00'da çalıştırılır.
#
# Temizlenenler:
#   - Docker build cache, dangling image, anonim volume (168h+)
#   - npm / pip / NuGet HTTP cache
#   - Windows Temp (7+ gün eski dosyalar)
#   - Windows Update indirme önbelleği (servisi durdurulup başlatılır)
#   - WSL + Docker VHDX sıkıştırması (wsl --shutdown sonrası Optimize-VHD)
#
# Sıkıştırma notu: VHDX dosyaları WSL/Docker içinde silinen dosyalar
# için alan geri VERMEz — sadece Optimize-VHD ile compaction yapılınca küçülür.
# Bu yüzden wsl --shutdown tek başına yeterli değildi; Optimize-VHD eklendi.

$ErrorActionPreference = "SilentlyContinue"
$log = "$PSScriptRoot\cleanup.log"
function Log($msg) { "$(Get-Date -f 'yyyy-MM-dd HH:mm:ss') $msg" | Tee-Object -FilePath $log -Append }

Log "=== Temizlik başlıyor ==="
$startFree = (Get-PSDrive C).Free

# ── 1. Docker ──────────────────────────────────────────────────────────────────
Log "Docker builder prune..."
docker builder prune -af --filter "until=168h" 2>&1 | Out-Null
Log "Docker image prune..."
docker image prune -af --filter "until=168h" 2>&1 | Out-Null
Log "Docker volume prune..."
docker volume prune -f 2>&1 | Out-Null

# ── 2. npm cache ───────────────────────────────────────────────────────────────
if (Get-Command npm -EA SilentlyContinue) {
    Log "npm cache clean..."
    npm cache clean --force 2>&1 | Out-Null
}

# ── 3. pip cache ───────────────────────────────────────────────────────────────
if (Get-Command pip -EA SilentlyContinue) {
    Log "pip cache purge..."
    pip cache purge 2>&1 | Out-Null
}

# ── 4. NuGet geçici cache ──────────────────────────────────────────────────────
if (Get-Command dotnet -EA SilentlyContinue) {
    Log "NuGet temp + http cache temizleniyor..."
    dotnet nuget locals temp --clear 2>&1 | Out-Null
    dotnet nuget locals http-cache --clear 2>&1 | Out-Null
}

# ── 5. Windows Temp (7+ gün eski) ─────────────────────────────────────────────
Log "Windows Temp temizleniyor (7+ gün eski)..."
$cutoff = (Get-Date).AddDays(-7)
Get-ChildItem $env:TEMP -EA SilentlyContinue |
    Where-Object { $_.LastWriteTime -lt $cutoff } |
    Remove-Item -Recurse -Force -EA SilentlyContinue

Get-ChildItem "C:\Windows\Temp" -EA SilentlyContinue |
    Where-Object { $_.LastWriteTime -lt $cutoff } |
    Remove-Item -Recurse -Force -EA SilentlyContinue

# ── 6. Windows Update indirme önbelleği ────────────────────────────────────────
Log "Windows Update önbelleği temizleniyor..."
Stop-Service wuauserv -Force -EA SilentlyContinue
Remove-Item "C:\Windows\SoftwareDistribution\Download\*" -Recurse -Force -EA SilentlyContinue
Start-Service wuauserv -EA SilentlyContinue

# ── 7. WSL kapat → VHDX sıkıştır ──────────────────────────────────────────────
Log "WSL kapatılıyor..."
wsl --shutdown 2>&1 | Out-Null
Start-Sleep -Seconds 5

# Docker VHDX
$dockerVhdx = "$env:LOCALAPPDATA\Docker\wsl\disk\docker_data.vhdx"
if ((Test-Path $dockerVhdx) -and (Get-Command Optimize-VHD -EA SilentlyContinue)) {
    Log "Docker VHDX sıkıştırılıyor ($([math]::Round((Get-Item $dockerVhdx).Length/1GB,2)) GB)..."
    Optimize-VHD -Path $dockerVhdx -Mode Full 2>&1 | Out-Null
    Log "Docker VHDX sonra: $([math]::Round((Get-Item $dockerVhdx).Length/1GB,2)) GB"
}

# Ubuntu WSL VHDX
$ubuntuVhdx = (Get-ChildItem "$env:LOCALAPPDATA\wsl" -Recurse -Filter "ext4.vhdx" -EA SilentlyContinue | Select-Object -First 1).FullName
if ($ubuntuVhdx -and (Get-Command Optimize-VHD -EA SilentlyContinue)) {
    Log "Ubuntu WSL VHDX sıkıştırılıyor ($([math]::Round((Get-Item $ubuntuVhdx).Length/1GB,2)) GB)..."
    Optimize-VHD -Path $ubuntuVhdx -Mode Full 2>&1 | Out-Null
    Log "Ubuntu WSL VHDX sonra: $([math]::Round((Get-Item $ubuntuVhdx).Length/1GB,2)) GB"
}

# ── Sonuç ──────────────────────────────────────────────────────────────────────
$endFree  = (Get-PSDrive C).Free
$gained   = [math]::Round(($endFree - $startFree) / 1GB, 2)
$totalFree = [math]::Round($endFree / 1GB, 2)
Log "=== Temizlik tamamlandı | Kazanılan: +$gained GB | Toplam boş: $totalFree GB ==="

# Docker disk cleanup helper for finans-portali.
# Çalışmakta olan container'lara veya volume'lardaki DB verilerine dokunmaz.
# Sadece dangling image'leri, kullanılmayan image'leri ve build cache'i siler.
#
# Kullanım:
#   .\docker-cleanup.ps1            # Önce mevcut kullanımı göster, sonra temizle
#   .\docker-cleanup.ps1 -All       # Tüm reachable olmayan image'leri de sil (daha agresif)
#   .\docker-cleanup.ps1 -DryRun    # Sadece raporla, silme

param(
    [switch]$All,
    [switch]$DryRun
)

Write-Host "`n=== Docker disk usage (öncesi) ===" -ForegroundColor Cyan
docker system df

if ($DryRun) {
    Write-Host "`n[DRY-RUN] Hiçbir şey silinmeyecek." -ForegroundColor Yellow
    exit 0
}

Write-Host "`n=== 1/4 Dangling image'ler siliniyor ===" -ForegroundColor Green
docker image prune -f

Write-Host "`n=== 2/4 Build cache temizleniyor ===" -ForegroundColor Green
docker builder prune -af

Write-Host "`n=== 3/4 Sahipsiz (dangling) volume'lar siliniyor ===" -ForegroundColor Green
# Sadece hiçbir container'ın referans etmediği volume'lar — aktif veriye dokunmaz
docker volume prune -f

if ($All) {
    Write-Host "`n=== 4/4 Kullanılmayan tüm image'ler siliniyor (agresif) ===" -ForegroundColor Green
    docker image prune -af
} else {
    Write-Host "`n=== 4/4 24 saatten eski kullanılmayan image'ler siliniyor ===" -ForegroundColor Green
    docker image prune -af --filter "until=24h"
}

Write-Host "`n=== Docker disk usage (sonrası) ===" -ForegroundColor Cyan
docker system df

Write-Host "`n[OK] Temizlik tamamlandı. Volume'lara ve aktif container'lara dokunulmadı." -ForegroundColor Green

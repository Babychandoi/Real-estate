param([switch]$ConfirmReset)
$ErrorActionPreference = 'Stop'

if (-not $ConfirmReset) {
    throw 'Lệnh này xóa dữ liệu Docker demo. Chạy lại với -ConfirmReset nếu đúng chủ đích.'
}
if (-not (Test-Path -LiteralPath '.env')) { throw 'Thiếu .env.' }
$modeLine = Get-Content -LiteralPath '.env' | Where-Object { $_ -match '^APP_MODE=' } | Select-Object -First 1
if ($modeLine -and $modeLine.Split('=', 2)[1].Trim() -eq 'production') {
    throw 'Từ chối reset khi APP_MODE=production.'
}

docker compose down --volumes --remove-orphans
if ($LASTEXITCODE -ne 0) { throw 'Không thể dừng cụm demo.' }
docker compose up --build -d
if ($LASTEXITCODE -ne 0) { throw 'Không thể khởi động lại cụm demo.' }
Write-Host 'Đã reset dữ liệu và khởi động lại cụm demo.'

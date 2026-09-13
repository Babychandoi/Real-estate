param([string]$EnvironmentFile = '.env')
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $EnvironmentFile)) {
    throw "Không tìm thấy $EnvironmentFile. Hãy sao chép .env.example thành .env và điền secret."
}

$values = @{}
Get-Content -LiteralPath $EnvironmentFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
        $key, $value = $line.Split('=', 2)
        $values[$key.Trim()] = $value.Trim()
    }
}

foreach ($required in @('POSTGRES_PASSWORD', 'REDIS_PASSWORD', 'DEMO_ACCOUNT_PASSWORD', 'MINIO_ROOT_PASSWORD')) {
    $value = $values[$required]
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Length -lt 12 -or $value -match 'change|example|password') {
        throw "$required phải là secret riêng, dài ít nhất 12 ký tự."
    }
}
if ([string]::IsNullOrWhiteSpace($values['MINIO_ROOT_USER']) -or $values['MINIO_ROOT_USER'].Length -lt 3) {
    throw 'MINIO_ROOT_USER phải có ít nhất 3 ký tự.'
}

if ($values['APP_MODE'] -eq 'production') {
    foreach ($required in @('PII_ENCRYPTION_KEY', 'PII_INDEX_KEY', 'APP_ADMIN_MFA_SECRET_BASE64')) {
        if ([string]::IsNullOrWhiteSpace($values[$required])) { throw "Production thiếu $required." }
    }
    if ($values['APP_ALLOWED_ORIGINS'] -notmatch '^https://') {
        throw 'Production chỉ chấp nhận APP_ALLOWED_ORIGINS dùng HTTPS.'
    }
}

docker compose --env-file $EnvironmentFile config --quiet
if ($LASTEXITCODE -ne 0) { throw 'docker compose config không hợp lệ.' }
Write-Host 'Preflight passed: secrets cơ bản và Docker Compose hợp lệ.'

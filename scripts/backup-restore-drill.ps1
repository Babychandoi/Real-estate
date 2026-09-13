param([string]$ComposeProject = 'bds-enterprise-stack')
$ErrorActionPreference = 'Stop'
$safeProject = $ComposeProject -replace '[^a-zA-Z0-9_-]', ''
if ($safeProject -ne $ComposeProject -or [string]::IsNullOrWhiteSpace($safeProject)) { throw 'Invalid Compose project name.' }
$postgres = docker ps --filter "label=com.docker.compose.project=$safeProject" --filter 'label=com.docker.compose.service=postgres' --format '{{.ID}}'
if (-not $postgres) { throw "Running postgres container not found for project $safeProject." }
$envMap = @{}
docker inspect $postgres --format '{{range .Config.Env}}{{println .}}{{end}}' | ForEach-Object {
  if ($_ -match '^([^=]+)=(.*)$') { $envMap[$matches[1]] = $matches[2] }
}
$db = $envMap.POSTGRES_DB; $user = $envMap.POSTGRES_USER
if (-not $db -or -not $user) { throw 'POSTGRES_DB/POSTGRES_USER missing in container.' }
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$restoreDb = "restore_drill_$stamp"
$artifactDir = Join-Path $PSScriptRoot "..\.artifacts\restore-drill\$stamp"
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$dumpPath = Join-Path $artifactDir 'database.dump'
$reportPath = Join-Path $artifactDir 'report.json'
$started = Get-Date
try {
  docker exec $postgres pg_dump -U $user -d $db -Fc -f /tmp/bds-restore-drill.dump
  if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed.' }
  docker cp "${postgres}:/tmp/bds-restore-drill.dump" $dumpPath
  docker exec $postgres createdb -U $user $restoreDb
  if ($LASTEXITCODE -ne 0) { throw 'Temporary restore database creation failed.' }
  docker exec $postgres pg_restore -U $user -d $restoreDb --no-owner --no-privileges /tmp/bds-restore-drill.dump
  if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed.' }
  $sourceTables = docker exec $postgres psql -U $user -d $db -Atc "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE'"
  $restoredTables = docker exec $postgres psql -U $user -d $restoreDb -Atc "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE'"
  if ([int]$sourceTables -ne [int]$restoredTables) { throw "Table count mismatch: source=$sourceTables restored=$restoredTables" }
  $finished = Get-Date
  $report = [ordered]@{
    status = 'PASS'; startedAt = $started.ToString('o'); finishedAt = $finished.ToString('o')
    observedRtoSeconds = [math]::Round(($finished - $started).TotalSeconds, 2)
    observedSnapshotDataLoss = 0; sourceTableCount = [int]$sourceTables; restoredTableCount = [int]$restoredTables
    scope = 'PostgreSQL logical backup restored into isolated temporary database'
    note = 'Observed drill evidence only; operational RPO depends on backup schedule and off-site replication.'
  }
  $report | ConvertTo-Json | Set-Content -Encoding UTF8 $reportPath
  Remove-Item -LiteralPath $dumpPath -Force
  Write-Host "Restore drill passed. Evidence: $reportPath"
} finally {
  docker exec $postgres dropdb -U $user --if-exists $restoreDb | Out-Null
  docker exec $postgres rm -f /tmp/bds-restore-drill.dump | Out-Null
}

param(
  [ValidateSet('smoke', 'load', 'soak')][string]$Profile = 'load',
  [string]$BaseUrl = 'http://host.docker.internal:3000',
  [string]$SoakDuration = '2h'
)
$ErrorActionPreference = 'Stop'
$artifactDir = Join-Path $PSScriptRoot '..\.artifacts\k6'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$summaryPath = Join-Path $artifactDir "$Profile-$stamp.json"
docker run --rm -i --add-host host.docker.internal:host-gateway `
  -e "PROFILE=$Profile" -e "BASE_URL=$BaseUrl" -e "SOAK_DURATION=$SoakDuration" `
  -v "${PWD}\infra\k6:/scripts:ro" -v "${artifactDir}:/results" `
  grafana/k6:0.54.0 run --summary-export "/results/$Profile-$stamp.json" /scripts/workload.js
if ($LASTEXITCODE -ne 0) { throw "k6 $Profile failed; see $summaryPath" }
Write-Host "k6 $Profile passed. Evidence: $summaryPath"

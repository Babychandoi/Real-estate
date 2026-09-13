param([string]$BaseUrl = 'http://localhost:3000')
$ErrorActionPreference = 'Stop'
$backend = Invoke-RestMethod -Uri "$BaseUrl/backend-health" -TimeoutSec 10
if ($backend.status -ne 'UP') { throw "Backend readiness is $($backend.status)" }
$frontend = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/healthz" -TimeoutSec 10
if ($frontend.StatusCode -ne 200) { throw 'Frontend health check failed' }
$search = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/v1/listings/search?page=0&size=1" -TimeoutSec 10
if ($search.StatusCode -ne 200) { throw 'Public search failed' }
Write-Host 'Smoke test passed: readiness, frontend, public search.'

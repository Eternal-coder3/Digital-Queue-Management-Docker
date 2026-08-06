$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
docker compose --env-file (Join-Path $ProjectRoot '.env.local') -p smartqueue -f (Join-Path $ProjectRoot 'compose.yml') stop
Write-Output 'SmartQueue containers are stopped. Database and Redis data are preserved.'

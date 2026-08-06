$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot

& (Join-Path $PSScriptRoot 'stop-local.ps1')
docker compose --env-file (Join-Path $ProjectRoot '.env.local') -p smartqueue-local -f (Join-Path $ProjectRoot 'compose.local.yml') down --volumes --remove-orphans
Write-Output 'SmartQueue containers and their PostgreSQL data volume were removed.'
Write-Output 'Delete the project tmp folder to remove its Maven, .NET, logs, and dependency caches.'

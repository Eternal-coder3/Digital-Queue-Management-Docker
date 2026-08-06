$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $ProjectRoot '.env.local'

if (-not (Test-Path $EnvFile)) {
    throw 'Missing .env.local. Copy .env.example to .env.local and replace the placeholder secrets.'
}

docker compose --env-file $EnvFile -p smartqueue -f (Join-Path $ProjectRoot 'compose.yml') up -d --build --wait
Write-Output 'SmartQueue is running at http://localhost:8080/'
Write-Output 'Notification API is running at http://localhost:5050/swagger'

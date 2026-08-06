param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [switch]$Force
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ResolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
if (-not $Force) { throw 'Restore replaces current SmartQueue database objects. Re-run with -Force after creating a fresh backup.' }
$EnvFile = Join-Path $ProjectRoot '.env.local'

Get-Content $EnvFile | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

$container = docker compose --env-file (Join-Path $ProjectRoot '.env.local') -p smartqueue -f (Join-Path $ProjectRoot 'compose.yml') ps -q postgres
if (-not $container) { throw 'The SmartQueue PostgreSQL container is not running.' }
$ContainerFile = '/tmp/smartqueue-restore.dump'
docker cp $ResolvedBackup "${container}:$ContainerFile"
docker exec $container pg_restore -U $env:POSTGRES_USER -d $env:POSTGRES_DB --clean --if-exists --no-owner $ContainerFile
if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed.' }
docker exec $container rm -f $ContainerFile
Write-Output 'Database restore completed. Restart the backend and verify /actuator/health.'

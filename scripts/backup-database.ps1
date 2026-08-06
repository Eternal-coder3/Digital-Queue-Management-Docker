param([int]$RetentionDays = 14)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$BackupDir = Join-Path $ProjectRoot 'backups'
$Timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$BackupFile = Join-Path $BackupDir "smartqueue-$Timestamp.dump"
$ContainerFile = "/tmp/smartqueue-$Timestamp.dump"
$EnvFile = Join-Path $ProjectRoot '.env.local'

Get-Content $EnvFile | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$container = docker compose --env-file (Join-Path $ProjectRoot '.env.local') -p smartqueue -f (Join-Path $ProjectRoot 'compose.yml') ps -q postgres
if (-not $container) { throw 'The SmartQueue PostgreSQL container is not running.' }

docker exec $container pg_dump -U $env:POSTGRES_USER -d $env:POSTGRES_DB -Fc -f $ContainerFile
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed.' }
docker cp "${container}:$ContainerFile" $BackupFile
if ($LASTEXITCODE -ne 0) { throw 'Could not copy the backup from PostgreSQL.' }
docker exec $container rm -f $ContainerFile

if (-not (Test-Path $BackupFile) -or (Get-Item $BackupFile).Length -eq 0) { throw 'Backup verification failed.' }
Get-ChildItem $BackupDir -Filter 'smartqueue-*.dump' | Where-Object LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) | Remove-Item -Force
Write-Output "Verified backup created: $BackupFile"

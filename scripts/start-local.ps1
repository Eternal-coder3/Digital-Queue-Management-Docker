$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$RuntimeDir = Join-Path $ProjectRoot 'tmp'
$LogDir = Join-Path $RuntimeDir 'logs'
$Maven = Join-Path $RuntimeDir 'maven\apache-maven-3.9.16\bin\mvn.cmd'
$Dotnet = Join-Path $RuntimeDir 'dotnet8\dotnet.exe'
$NotificationDll = Join-Path $ProjectRoot 'notification-service\bin\Debug\net8.0\SmartQueue.NotificationService.dll'
$EnvFile = Join-Path $ProjectRoot '.env.local'

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

if (-not (Test-Path $EnvFile)) {
    $bytes = New-Object byte[] 48
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    $generator.GetBytes($bytes)
    $generator.Dispose()
    $random = [Convert]::ToBase64String($bytes)
    @(
        'POSTGRES_DB=smartqueue'
        'POSTGRES_USER=smartqueue'
        "POSTGRES_PASSWORD=$random"
        "JWT_SECRET=$random"
        'REFRESH_COOKIE_SECURE=false'
        'SMARTQUEUE_ALLOWED_ORIGINS=http://localhost:8080'
        'TWILIOSETTINGS__ENABLEMOCKMODE=true'
        'TWILIOSETTINGS__FROMEMAIL=noreply@smartqueue.local'
        'TWILIOSETTINGS__FROMNAME=SmartQueue Virtual Manager'
    ) | Set-Content -Encoding UTF8 $EnvFile
}

Get-Content $EnvFile | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

if (-not (Test-Path $Maven)) {
    throw "Project-local Maven is missing: $Maven"
}
if (-not (Test-Path $Dotnet)) {
    throw "Project-local .NET 8 is missing: $Dotnet"
}
if (-not (Test-Path $NotificationDll)) {
    throw "Built notification service is missing: $NotificationDll"
}

docker compose --env-file $EnvFile -p smartqueue-local -f (Join-Path $ProjectRoot 'compose.local.yml') up -d --wait

$env:ASPNETCORE_ENVIRONMENT = 'Development'
$notification = Start-Process -FilePath $Dotnet `
    -ArgumentList ('"' + $NotificationDll + '"') `
    -WorkingDirectory $ProjectRoot `
    -RedirectStandardOutput (Join-Path $LogDir 'notification.out.log') `
    -RedirectStandardError (Join-Path $LogDir 'notification.err.log') `
    -PassThru -WindowStyle Hidden

$env:DB_URL = "jdbc:postgresql://localhost:15432/$env:POSTGRES_DB"
$env:DB_USERNAME = $env:POSTGRES_USER
$env:DB_PASSWORD = $env:POSTGRES_PASSWORD
$env:REDIS_HOST = 'localhost'
$env:REDIS_PORT = '56379'
$env:NOTIFICATION_SERVICE_URL = 'http://localhost:5050'

$backend = Start-Process -FilePath $Maven `
    -ArgumentList @('-Dmaven.repo.local=tmp/m2', '-pl', 'backend', 'spring-boot:run') `
    -WorkingDirectory $ProjectRoot `
    -RedirectStandardOutput (Join-Path $LogDir 'backend.out.log') `
    -RedirectStandardError (Join-Path $LogDir 'backend.err.log') `
    -PassThru -WindowStyle Hidden

@{
    notificationPid = $notification.Id
    backendPid = $backend.Id
} | ConvertTo-Json | Set-Content (Join-Path $RuntimeDir 'smartqueue-processes.json')

Write-Output 'SmartQueue is starting.'
Write-Output 'Application: http://localhost:8080/'
Write-Output 'Health:      http://localhost:8080/api/v1/health'
Write-Output 'Swagger:     http://localhost:8080/swagger-ui.html'

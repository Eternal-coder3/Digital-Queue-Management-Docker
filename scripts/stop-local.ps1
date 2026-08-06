$ErrorActionPreference = 'Continue'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$PidFile = Join-Path $ProjectRoot 'tmp\smartqueue-processes.json'

if (Test-Path $PidFile) {
    $processes = Get-Content -Raw $PidFile | ConvertFrom-Json
    foreach ($processId in @($processes.backendPid, $processes.notificationPid)) {
        if ($processId) {
            taskkill.exe /PID $processId /T /F 2>$null | Out-Null
        }
    }
    Remove-Item -LiteralPath $PidFile -ErrorAction SilentlyContinue
}

# Also stop stale SmartQueue processes left by an interrupted start or an older PID file.
$projectProcesses = Get-CimInstance Win32_Process | Where-Object {
    $_.CommandLine -and $_.CommandLine.Contains($ProjectRoot) -and $_.Name -in @('java.exe', 'dotnet.exe')
}
foreach ($projectProcess in $projectProcesses) {
    taskkill.exe /PID $projectProcess.ProcessId /T /F 2>$null | Out-Null
}

docker compose --env-file (Join-Path $ProjectRoot '.env.local') -p smartqueue-local -f (Join-Path $ProjectRoot 'compose.local.yml') stop
Write-Output 'SmartQueue application and containers are stopped. Data is preserved.'

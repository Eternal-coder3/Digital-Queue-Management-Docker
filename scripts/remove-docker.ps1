param([switch]$DeleteData)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$arguments = @('compose', '--env-file', (Join-Path $ProjectRoot '.env.local'), '-p', 'smartqueue', '-f', (Join-Path $ProjectRoot 'compose.yml'), 'down', '--remove-orphans')
if ($DeleteData) { $arguments += '--volumes' }
& docker @arguments
if ($DeleteData) {
    Write-Output 'SmartQueue containers and data volumes were permanently removed.'
} else {
    Write-Output 'SmartQueue containers were removed. Database and Redis volumes were preserved.'
}

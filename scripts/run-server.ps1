param([int]$Port = 5000)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if (-not (Test-Path -LiteralPath 'out/server/ServerMain.class')) {
        & "$PSScriptRoot/build.ps1"
    }
    & java -cp out server.ServerMain $Port
    if ($LASTEXITCODE -ne 0) { throw 'Server exited with an error.' }
} finally {
    Pop-Location
}

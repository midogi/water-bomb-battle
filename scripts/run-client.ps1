param([string]$ServerHost = 'localhost', [int]$Port = 5000, [string]$Nickname = 'player')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if (-not (Test-Path -LiteralPath 'out/client/ClientMain.class')) {
        & "$PSScriptRoot/build.ps1"
    }
    & java -cp out client.ClientMain $ServerHost $Port $Nickname
    if ($LASTEXITCODE -ne 0) { throw 'Client exited with an error.' }
} finally {
    Pop-Location
}

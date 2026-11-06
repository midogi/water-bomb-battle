$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    New-Item -ItemType Directory -Path out -Force | Out-Null
    $sources = @(Get-ChildItem -LiteralPath src -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -d out $sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    Write-Host 'Build OK (Java 17 compatible).'
} finally {
    Pop-Location
}

$ErrorActionPreference = 'Stop'
& "$PSScriptRoot/build.ps1"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $sources = @(Get-ChildItem -LiteralPath tests -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -cp out -d out $sources
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    & java -ea -cp out tests.ProtocolTest
    if ($LASTEXITCODE -ne 0) { throw 'Protocol tests failed.' }
} finally {
    Pop-Location
}

param(
    [int]$ApiPort = 8000,
    [int]$FrontendPort = 5173,
    [string]$DataRoot = "",
    [switch]$SkipBootstrap
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$startArgs = @(
    "-ExecutionPolicy", "Bypass",
    "-File", (Join-Path $Root "scripts\start-beta.ps1"),
    "-ApiPort", "$ApiPort",
    "-FrontendPort", "$FrontendPort",
    "-Detached",
    "-NoBrowser"
)
if ($DataRoot) {
    $startArgs += @("-DataRoot", $DataRoot)
}
if ($SkipBootstrap) {
    $startArgs += "-SkipBootstrap"
}

& powershell.exe @startArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$url = "http://127.0.0.1:$FrontendPort/bite?widget=1"
Write-Host ""
Write-Host "Opening Daily Bite widget:"
Write-Host "  $url"
Write-Host ""
Write-Host "Stop services later with: .\scripts\stop-dev.ps1"
Start-Process $url

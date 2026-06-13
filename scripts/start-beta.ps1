param(
    [int]$ApiPort = 8000,
    [int]$FrontendPort = 5173,
    [string]$DataRoot = "",
    [switch]$NoBrowser,
    [switch]$Detached
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$DefaultDataRoot = Join-Path $env:USERPROFILE "CSLearningOS"
$ResolvedDataRoot = if ($DataRoot) { $DataRoot } else { $DefaultDataRoot }
$ContentDir = Join-Path $ResolvedDataRoot "content"
$DbPath = Join-Path $ResolvedDataRoot "knowledge.db"
$GeneratedRoot = Join-Path $ResolvedDataRoot "generated"
$ExportRoot = Join-Path $ResolvedDataRoot "exports"

New-Item -ItemType Directory -Force -Path $ResolvedDataRoot | Out-Null
New-Item -ItemType Directory -Force -Path $GeneratedRoot | Out-Null
New-Item -ItemType Directory -Force -Path $ExportRoot | Out-Null
if (-not (Test-Path $ContentDir)) {
    Copy-Item -Path (Join-Path $Root "content-demo") -Destination $ContentDir -Recurse
}

$env:CS_LEARNING_PROFILE = "friend-beta"
$env:CS_LEARNING_BETA = "true"
$env:CS_LEARNING_AI_ENABLED = "false"
$env:CS_LEARNING_DATA_ROOT = (Resolve-Path $ResolvedDataRoot).Path
$env:CS_LEARNING_GENERATED_ROOT = (Resolve-Path $GeneratedRoot).Path
$env:CS_LEARNING_EXPORT_ROOT = (Resolve-Path $ExportRoot).Path

Write-Host "Starting CS Learning OS beta profile"
Write-Host "  Data root: $env:CS_LEARNING_DATA_ROOT"
Write-Host "  Content:   $ContentDir"
Write-Host "  DB:        $DbPath"
Write-Host "  Exports:   $env:CS_LEARNING_EXPORT_ROOT"
Write-Host "  Generated: $env:CS_LEARNING_GENERATED_ROOT"
Write-Host "  AI:        disabled"
Write-Host ""

$devArgs = @(
    "-ExecutionPolicy", "Bypass",
    "-File", (Join-Path $Root "scripts\dev.ps1"),
    "-ApiPort", "$ApiPort",
    "-FrontendPort", "$FrontendPort",
    "-ContentDir", $ContentDir,
    "-DbPath", $DbPath
)
if ($NoBrowser) {
    $devArgs += "-NoBrowser"
}
if ($Detached) {
    $devArgs += "-Detached"
}

& powershell.exe @devArgs

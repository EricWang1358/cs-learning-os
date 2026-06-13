param(
    [int]$ApiPort = 8000,
    [int]$FrontendPort = 5173,
    [string]$DataRoot = "",
    [switch]$Json,
    [switch]$Strict
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$AppDir = Join-Path $Root "app"
$Python = Join-Path $Root ".venv\Scripts\python.exe"
$DefaultDataRoot = Join-Path $env:USERPROFILE "CSLearningOS"
$ResolvedDataRoot = if ($DataRoot) { $DataRoot } else { $DefaultDataRoot }
$ContentDir = Join-Path $ResolvedDataRoot "content"
$DbPath = Join-Path $ResolvedDataRoot "knowledge.db"
$GeneratedRoot = Join-Path $ResolvedDataRoot "generated"
$ExportRoot = Join-Path $ResolvedDataRoot "exports"
$Checks = New-Object System.Collections.Generic.List[object]

function Add-Check {
    param(
        [string]$Id,
        [bool]$Ok,
        [string]$Severity,
        [string]$Message,
        [string]$Remediation = ""
    )

    $Checks.Add([pscustomobject]@{
        id = $Id
        ok = $Ok
        severity = $Severity
        message = $Message
        remediation = $Remediation
    }) | Out-Null
}

function Get-CommandPath {
    param([string]$Name)
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    return ""
}

function Test-PortAvailable {
    param([int]$Port)
    $lines = netstat -ano | Select-String "127\.0\.0\.1:$Port\s"
    foreach ($line in $lines) {
        $parts = ($line.ToString() -split "\s+") | Where-Object { $_ }
        if ($parts.Length -ge 5 -and $parts[3] -eq "LISTENING") {
            return $false
        }
    }
    return $true
}

$pythonCommand = Get-CommandPath "python.exe"
$pyLauncher = Get-CommandPath "py.exe"
$npmCommand = Get-CommandPath "npm.cmd"

Add-Check "project-root" (Test-Path (Join-Path $Root "backend\api.py")) "error" "Project root resolved to $Root." "Run this script from the checked-out CS Learning OS folder."
Add-Check "python-command" ([bool]($pythonCommand -or $pyLauncher -or (Test-Path $Python))) "error" "Python command: $pythonCommand; py launcher: $pyLauncher; project venv: $Python." "Install Python 3.11+ from python.org or Microsoft Store, then rerun bootstrap."
Add-Check "project-venv" (Test-Path $Python) "warning" "Project virtual environment path: $Python." "Run .\scripts\bootstrap-beta.ps1 to create the venv."
Add-Check "npm-command" ([bool]$npmCommand) "error" "npm command: $npmCommand." "Install Node.js LTS from nodejs.org, then rerun bootstrap."
Add-Check "package-lock" (Test-Path (Join-Path $AppDir "package-lock.json")) "error" "Frontend package lock is present." "Restore app/package-lock.json before packaging beta."
Add-Check "node-modules" (Test-Path (Join-Path $AppDir "node_modules")) "warning" "Frontend node_modules path: $(Join-Path $AppDir "node_modules")." "Run .\scripts\bootstrap-beta.ps1 to install frontend dependencies."
Add-Check "content-demo" (Test-Path (Join-Path $Root "content-demo")) "error" "Demo content is available for first-run copy." "Restore content-demo before packaging beta."
Add-Check "data-root" ([bool]$ResolvedDataRoot) "error" "Beta data root: $ResolvedDataRoot." "Set -DataRoot to a writable folder."
Add-Check "api-port" (Test-PortAvailable $ApiPort) "warning" "API port $ApiPort availability checked." "Close the process using the port or let dev.ps1 stop the local listener."
Add-Check "frontend-port" (Test-PortAvailable $FrontendPort) "warning" "Frontend port $FrontendPort availability checked." "Close the process using the port or let dev.ps1 stop the local listener."

if (Test-Path $Python) {
    $dependencyProbe = & $Python -c "import fastapi, uvicorn, openai; print('ok')" 2>&1
    Add-Check "backend-deps" ($LASTEXITCODE -eq 0) "error" "Backend dependency probe: $dependencyProbe." "Run .\scripts\bootstrap-beta.ps1 to install backend requirements."
}

$payload = [pscustomobject]@{
    ok = -not ($Checks | Where-Object { -not $_.ok -and $_.severity -eq "error" })
    strict_ok = -not ($Checks | Where-Object { -not $_.ok })
    root = $Root
    data = [pscustomobject]@{
        root = $ResolvedDataRoot
        content = $ContentDir
        db = $DbPath
        generated = $GeneratedRoot
        exports = $ExportRoot
    }
    checks = $Checks
}

if ($Json) {
    $payload | ConvertTo-Json -Depth 6
} else {
    Write-Host "CS Learning OS beta verification"
    Write-Host "  Root:      $Root"
    Write-Host "  Data root: $ResolvedDataRoot"
    Write-Host ""
    foreach ($check in $Checks) {
        $mark = if ($check.ok) { "PASS" } elseif ($check.severity -eq "error") { "FAIL" } else { "WARN" }
        Write-Host "[$mark] $($check.id): $($check.message)"
        if (-not $check.ok -and $check.remediation) {
            Write-Host "       Fix: $($check.remediation)"
        }
    }
}

if (-not $payload.ok -or ($Strict -and -not $payload.strict_ok)) {
    if ($Strict) {
        $blockingWarnings = $Checks | Where-Object {
            -not $_.ok -and $_.severity -eq "warning" -and $_.id -notin @("api-port", "frontend-port")
        }
        if (-not $blockingWarnings -and $payload.ok) {
            exit 0
        }
    }
    exit 1
}

param(
    [string]$DataRoot = "",
    [switch]$SkipNpm,
    [switch]$SkipSmoke
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$AppDir = Join-Path $Root "app"
$Python = Join-Path $Root ".venv\Scripts\python.exe"
$Requirements = Join-Path $Root "backend\requirements.txt"

function Resolve-BasePython {
    $python = Get-Command "python.exe" -ErrorAction SilentlyContinue
    if ($python) { return $python.Source }
    $py = Get-Command "py.exe" -ErrorAction SilentlyContinue
    if ($py) { return $py.Source }
    throw "Python 3.11+ was not found. Install Python, then rerun bootstrap."
}

function Invoke-Python {
    param(
        [string]$BasePython,
        [string[]]$Arguments
    )

    if ((Split-Path -Leaf $BasePython).ToLowerInvariant() -eq "py.exe") {
        & $BasePython -3 @Arguments
    } else {
        & $BasePython @Arguments
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Python command failed: $BasePython $($Arguments -join ' ')"
    }
}

Write-Host "Bootstrapping CS Learning OS beta..."
Write-Host "  Project: $Root"

$basePython = Resolve-BasePython
Write-Host "  Python:  $basePython"

if (-not (Test-Path $Python)) {
    Write-Host "Creating project virtual environment..."
    Invoke-Python $basePython @("-m", "venv", (Join-Path $Root ".venv"))
}

Write-Host "Upgrading pip..."
& $Python -m pip install --upgrade pip
if ($LASTEXITCODE -ne 0) { throw "pip upgrade failed" }

Write-Host "Installing backend requirements..."
& $Python -m pip install -r $Requirements
if ($LASTEXITCODE -ne 0) { throw "backend dependency install failed" }

if (-not $SkipNpm) {
    $npm = Get-Command "npm.cmd" -ErrorAction SilentlyContinue
    if (-not $npm) {
        throw "npm was not found. Install Node.js LTS, then rerun bootstrap."
    }

    Push-Location $AppDir
    try {
        if (Test-Path (Join-Path $AppDir "package-lock.json")) {
            Write-Host "Installing frontend dependencies with npm ci..."
            & $npm.Source ci
        } else {
            Write-Host "Installing frontend dependencies with npm install..."
            & $npm.Source install
        }
        if ($LASTEXITCODE -ne 0) { throw "frontend dependency install failed" }
    } finally {
        Pop-Location
    }
}

if (-not $SkipSmoke) {
    Write-Host "Running beta migration smoke..."
    & $Python (Join-Path $Root "backend\beta_migration_smoke.py")
    if ($LASTEXITCODE -ne 0) { throw "beta migration smoke failed" }
}

$verifyArgs = @("-ExecutionPolicy", "Bypass", "-File", (Join-Path $Root "scripts\verify-beta.ps1"))
if ($DataRoot) {
    $verifyArgs += @("-DataRoot", $DataRoot)
}
Write-Host "Running final beta verification..."
& powershell.exe @verifyArgs
if ($LASTEXITCODE -ne 0) { throw "beta verification failed" }

Write-Host ""
Write-Host "Bootstrap complete. Start the beta with:"
Write-Host "  .\scripts\start-beta.ps1"

param(
    [int]$ApiPort = 8000,
    [int]$FrontendPort = 5173,
    [string]$ContentDir = "",
    [string]$DbPath = "",
    [switch]$NoIngest,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$AppDir = Join-Path $Root "app"
$Python = Join-Path $Root ".venv\Scripts\python.exe"
$Npm = (Get-Command "npm.cmd" -ErrorAction Stop).Source
$DefaultDataRoot = (Resolve-Path (Join-Path $Root "..\\cs-learning-data") -ErrorAction SilentlyContinue)
$DefaultContentDir = if ($DefaultDataRoot) { Join-Path $DefaultDataRoot.Path "content" } else { Join-Path $Root "content-demo" }
$DefaultDbPath = if ($DefaultDataRoot) { Join-Path $DefaultDataRoot.Path "knowledge.db" } else { Join-Path $Root "var\knowledge.db" }
$ResolvedContentDir = if ($ContentDir) { (Resolve-Path $ContentDir).Path } else { $DefaultContentDir }
$ResolvedDbPath = if ($DbPath) { $DbPath } else { $DefaultDbPath }
$GeneratedDir = Join-Path $Root "generated\dev"
$ApiOutLog = Join-Path $GeneratedDir "api-$ApiPort.out.log"
$ApiErrLog = Join-Path $GeneratedDir "api-$ApiPort.err.log"
$FrontendOutLog = Join-Path $GeneratedDir "frontend-$FrontendPort.out.log"
$FrontendErrLog = Join-Path $GeneratedDir "frontend-$FrontendPort.err.log"

function Stop-PortOwner {
    param([int]$Port)

    $lines = netstat -ano | Select-String "127\.0\.0\.1:$Port\s"
    $pids = @()

    foreach ($line in $lines) {
        $parts = ($line.ToString() -split "\s+") | Where-Object { $_ }
        if ($parts.Length -ge 5 -and $parts[3] -eq "LISTENING") {
            $pids += [int]$parts[4]
        }
    }

    $pids = $pids | Sort-Object -Unique
    foreach ($processId in $pids) {
        try {
            $process = Get-Process -Id $processId -ErrorAction Stop
            Write-Host "Stopping port $Port owner: PID $processId ($($process.ProcessName))"
            Stop-Process -Id $processId -Force
        }
        catch {
            Write-Warning "Could not stop PID ${processId} for port ${Port}: $($_.Exception.Message)"
        }
    }
}

if (-not (Test-Path $Python)) {
    throw "Project virtual environment not found at $Python"
}

New-Item -ItemType Directory -Force -Path $GeneratedDir | Out-Null

Stop-PortOwner -Port $ApiPort
Stop-PortOwner -Port $FrontendPort

if (-not $NoIngest) {
    Write-Host "Rebuilding SQLite index..."
    & $Python -m backend.ingest --content $ResolvedContentDir --db $ResolvedDbPath
}

Write-Host "Starting API on http://127.0.0.1:$ApiPort"
$PreviousContentEnv = $env:CS_LEARNING_CONTENT
$PreviousDbEnv = $env:CS_LEARNING_DB
$env:CS_LEARNING_CONTENT = $ResolvedContentDir
$env:CS_LEARNING_DB = $ResolvedDbPath
$api = Start-Process `
    -WindowStyle Hidden `
    -FilePath $Python `
    -ArgumentList @("-m", "uvicorn", "backend.api:app", "--host", "127.0.0.1", "--port", "$ApiPort") `
    -WorkingDirectory $Root `
    -RedirectStandardOutput $ApiOutLog `
    -RedirectStandardError $ApiErrLog `
    -PassThru
$env:CS_LEARNING_CONTENT = $PreviousContentEnv
$env:CS_LEARNING_DB = $PreviousDbEnv

Start-Sleep -Seconds 2

Write-Host "Starting frontend on http://127.0.0.1:$FrontendPort"
$frontend = Start-Process `
    -WindowStyle Hidden `
    -FilePath $Npm `
    -ArgumentList @("run", "dev", "--", "--host", "127.0.0.1", "--port", "$FrontendPort") `
    -WorkingDirectory $AppDir `
    -RedirectStandardOutput $FrontendOutLog `
    -RedirectStandardError $FrontendErrLog `
    -PassThru

Write-Host ""
Write-Host "CS Learning OS is starting:"
Write-Host "  API:      http://127.0.0.1:$ApiPort"
Write-Host "  Frontend: http://127.0.0.1:$FrontendPort"
Write-Host "  Content:  $ResolvedContentDir"
Write-Host "  DB:       $ResolvedDbPath"
Write-Host "  API logs: $ApiOutLog"
Write-Host "            $ApiErrLog"
Write-Host "  UI logs:  $FrontendOutLog"
Write-Host "            $FrontendErrLog"
Write-Host "  API PID:  $($api.Id)"
Write-Host "  UI PID:   $($frontend.Id)"

if (-not $NoBrowser) {
    Start-Process "http://127.0.0.1:$FrontendPort"
}

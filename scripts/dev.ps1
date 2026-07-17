param(
    [int]$ApiPort = 8000,
    [int]$FrontendPort = 5173,
    [string]$ContentDir = "",
    [string]$DbPath = "",
    [string]$ApiHost = "127.0.0.1",
    [switch]$NoIngest,
    [switch]$NoBrowser,
    [switch]$Detached
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$AppDir = Join-Path $Root "app"
$Python = Join-Path $Root ".venv\Scripts\python.exe"
$Npm = (Get-Command "npm.cmd" -ErrorAction Stop).Source
$RepoDataRoot = Join-Path $Root "data"
$SiblingDataRoot = Join-Path $Root "..\\cs-learning-data"
$DataRootCandidate = if ($env:CS_LEARNING_DATA_ROOT) {
    $env:CS_LEARNING_DATA_ROOT
} elseif (Test-Path $RepoDataRoot) {
    $RepoDataRoot
} else {
    $SiblingDataRoot
}
$DefaultDataRoot = (Resolve-Path $DataRootCandidate -ErrorAction SilentlyContinue)
$DefaultContentDir = if ($DefaultDataRoot) { Join-Path $DefaultDataRoot.Path "content" } else { Join-Path $Root "content-demo" }
$DefaultDbPath = if ($DefaultDataRoot) { Join-Path $DefaultDataRoot.Path "knowledge.db" } else { Join-Path $Root "var\knowledge.db" }
$ResolvedContentDir = if ($ContentDir) { (Resolve-Path $ContentDir).Path } else { $DefaultContentDir }
$ResolvedDbPath = if ($DbPath) { $DbPath } else { $DefaultDbPath }
$GeneratedRoot = if ($env:CS_LEARNING_GENERATED_ROOT) { $env:CS_LEARNING_GENERATED_ROOT } else { Join-Path $Root "generated" }
$GeneratedDir = Join-Path $GeneratedRoot "dev"
$ApiOutLog = Join-Path $GeneratedDir "api-$ApiPort.out.log"
$ApiErrLog = Join-Path $GeneratedDir "api-$ApiPort.err.log"
$FrontendOutLog = Join-Path $GeneratedDir "frontend-$FrontendPort.out.log"
$FrontendErrLog = Join-Path $GeneratedDir "frontend-$FrontendPort.err.log"

function Stop-PortOwner {
    param([int]$Port)

    $lines = netstat -ano | Select-String ":$Port\s"
    $pids = @()

    foreach ($line in $lines) {
        $parts = ($line.ToString() -split "\s+") | Where-Object { $_ }
        if ($parts.Length -ge 5 -and $parts[1] -like "*:$Port" -and $parts[3] -eq "LISTENING") {
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

function Stop-StartedProcess {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Name
    )

    if ($null -eq $Process) {
        return
    }

    try {
        $Process.Refresh()
        if (-not $Process.HasExited) {
            Write-Host "Stopping $Name PID $($Process.Id)"
            Stop-Process -Id $Process.Id -Force
        }
    }
    catch {
        Write-Warning "Could not stop ${Name}: $($_.Exception.Message)"
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

Write-Host "Starting API on http://${ApiHost}:$ApiPort"
$PreviousContentEnv = $env:CS_LEARNING_CONTENT
$PreviousDbEnv = $env:CS_LEARNING_DB
$PreviousHostEnv = $env:CS_LEARNING_HOST
$env:CS_LEARNING_CONTENT = $ResolvedContentDir
$env:CS_LEARNING_DB = $ResolvedDbPath
$env:CS_LEARNING_HOST = $ApiHost
$api = Start-Process `
    -WindowStyle Hidden `
    -FilePath $Python `
    -ArgumentList @("-m", "uvicorn", "backend.api:app", "--host", "$ApiHost", "--port", "$ApiPort") `
    -WorkingDirectory $Root `
    -RedirectStandardOutput $ApiOutLog `
    -RedirectStandardError $ApiErrLog `
    -PassThru
$env:CS_LEARNING_CONTENT = $PreviousContentEnv
$env:CS_LEARNING_DB = $PreviousDbEnv
$env:CS_LEARNING_HOST = $PreviousHostEnv

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
Write-Host "  API:      http://${ApiHost}:$ApiPort"
Write-Host "  Frontend: http://127.0.0.1:$FrontendPort"
Write-Host "  Content:  $ResolvedContentDir"
Write-Host "  DB:       $ResolvedDbPath"
Write-Host "  API logs: $ApiOutLog"
Write-Host "            $ApiErrLog"
Write-Host "  UI logs:  $FrontendOutLog"
Write-Host "            $FrontendErrLog"
Write-Host "  API PID:  $($api.Id)"
Write-Host "  UI PID:   $($frontend.Id)"
if ($ApiHost -ne "127.0.0.1" -and $ApiHost -ne "localhost" -and $ApiHost -ne "::1") {
    Write-Host ""
    Write-Warning "API is exposed on $ApiHost. Sync endpoints require paired-device credentials; pairing tokens can only be created from this machine."

if (-not $NoBrowser) {
    Start-Process "http://127.0.0.1:$FrontendPort"
}

if ($Detached) {
    Write-Host ""
    Write-Host "Detached mode: services keep running after this script exits."
    Write-Host "Stop them later with: .\scripts\stop-dev.ps1"
    return
}

Write-Host ""
Write-Host "Foreground supervisor mode is active."
Write-Host "Press Ctrl+C in this terminal to stop both dev servers."

$exitReason = ""
try {
    while ($true) {
        Start-Sleep -Seconds 1
        $api.Refresh()
        $frontend.Refresh()

        if ($api.HasExited) {
            $exitReason = "API process exited with code $($api.ExitCode). Check $ApiErrLog"
            break
        }
        if ($frontend.HasExited) {
            $exitReason = "Frontend process exited with code $($frontend.ExitCode). Check $FrontendErrLog"
            break
        }
    }
}
finally {
    Stop-StartedProcess -Process $frontend -Name "frontend"
    Stop-StartedProcess -Process $api -Name "API"
}

if ($exitReason) {
    throw $exitReason
}

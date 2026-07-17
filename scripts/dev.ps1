param(
    [int]$ApiPort = 8000,
    [int]$SyncPort = 8001,
    [int]$FrontendPort = 5173,
    [string]$ContentDir = "",
    [string]$DbPath = "",
    [string]$ApiHost = "127.0.0.1",
    [switch]$EnableLanSync,
    [switch]$NoIngest,
    [switch]$NoBrowser,
    [switch]$Detached
)

# Interactive launcher: when no arguments are supplied, offer a 2-second
# choice between daily mode (LAN sync enabled) and a debug configuration menu.
if ($PSBoundParameters.Count -eq 0) {
    Write-Host ""
    Write-Host "=============================================="
    Write-Host "  CS Learning OS Launcher"
    Write-Host "=============================================="
    Write-Host "  Daily mode: LAN sync enabled, browser opened"
    Write-Host "  Debug mode: choose host, ports, ingest, detached"
    Write-Host "=============================================="
    Write-Host ""
    Write-Host "Press D for debug menu, or wait 2 seconds for daily mode..."

    $pressedD = $false
    $timeout = [DateTime]::Now.AddSeconds(2)
    while ([DateTime]::Now -lt $timeout) {
        try {
            if ([System.Console]::KeyAvailable) {
                $key = [System.Console]::ReadKey($true).KeyChar
                if ($key -eq 'D' -or $key -eq 'd') {
                    $pressedD = $true
                }
                break
            }
        }
        catch {
            # Non-interactive host (e.g. background task): fall through to daily mode.
            break
        }
        Start-Sleep -Milliseconds 100
    }

    if ($pressedD) {
        Write-Host ""
        Write-Host "--- Debug menu ---"

        $hostInput = Read-Host "API host [127.0.0.1]"
        if (-not [string]::IsNullOrWhiteSpace($hostInput)) { $ApiHost = $hostInput }

        $lanInput = Read-Host "Enable LAN sync for phone pairing? (Y/n)"
        if (-not [string]::IsNullOrWhiteSpace($lanInput) -and $lanInput.Trim().ToLower() -eq 'n') {
            $EnableLanSync = $false
        } else {
            $EnableLanSync = $true
        }

        $portInput = Read-Host "API port [8000]"
        if (-not [string]::IsNullOrWhiteSpace($portInput)) { $ApiPort = [int]$portInput }

        $syncPortInput = Read-Host "Sync LAN port [8001]"
        if (-not [string]::IsNullOrWhiteSpace($syncPortInput)) { $SyncPort = [int]$syncPortInput }

        $frontendPortInput = Read-Host "Frontend port [5173]"
        if (-not [string]::IsNullOrWhiteSpace($frontendPortInput)) { $FrontendPort = [int]$frontendPortInput }

        $ingestInput = Read-Host "Skip initial ingest? (y/N)"
        if (-not [string]::IsNullOrWhiteSpace($ingestInput) -and $ingestInput.Trim().ToLower() -eq 'y') { $NoIngest = $true }

        $browserInput = Read-Host "Skip opening browser? (y/N)"
        if (-not [string]::IsNullOrWhiteSpace($browserInput) -and $browserInput.Trim().ToLower() -eq 'y') { $NoBrowser = $true }

        $detachedInput = Read-Host "Detached mode? (y/N)"
        if (-not [string]::IsNullOrWhiteSpace($detachedInput) -and $detachedInput.Trim().ToLower() -eq 'y') { $Detached = $true }

        Write-Host ""
        Write-Host "Starting with debug options..."
    } else {
        Write-Host ""
        Write-Host "Starting daily mode with LAN sync enabled..."
        $EnableLanSync = $true
    }
}

$ErrorActionPreference = "Stop"

function Get-PreferredLanAddress {
    $route = Get-NetRoute -AddressFamily IPv4 -DestinationPrefix "0.0.0.0/0" |
        Where-Object { $_.NextHop -ne "0.0.0.0" } |
        Sort-Object -Property RouteMetric, InterfaceMetric |
        Select-Object -First 1
    if ($null -eq $route) {
        throw "Could not identify the active IPv4 network route for LAN sync. Use -ApiHost with your Wi-Fi IPv4 address."
    }
    $address = Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $route.InterfaceIndex |
        Where-Object { $_.IPAddress -notmatch '^(127\\.|169\\.254\\.)' } |
        Select-Object -First 1
    if ($null -eq $address) {
        throw "Could not identify a LAN IPv4 address for the active network route."
    }
    return $address.IPAddress
}

$LanSyncAddress = ""
if ($EnableLanSync) {
    $LanSyncAddress = Get-PreferredLanAddress
    if ($SyncPort -eq $ApiPort) { throw "SyncPort must differ from ApiPort." }
}

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
$SyncOutLog = Join-Path $GeneratedDir "sync-$SyncPort.out.log"
$SyncErrLog = Join-Path $GeneratedDir "sync-$SyncPort.err.log"
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
if ($EnableLanSync) { Stop-PortOwner -Port $SyncPort }

if (-not $NoIngest) {
    Write-Host "Rebuilding SQLite index..."
    & $Python -m backend.ingest --content $ResolvedContentDir --db $ResolvedDbPath
}

Write-Host "Starting API on http://${ApiHost}:$ApiPort"
$PreviousContentEnv = $env:CS_LEARNING_CONTENT
$PreviousDbEnv = $env:CS_LEARNING_DB
$PreviousHostEnv = $env:CS_LEARNING_HOST
$PreviousSyncPublicUrlEnv = $env:CS_LEARNING_SYNC_PUBLIC_URL
$env:CS_LEARNING_CONTENT = $ResolvedContentDir
$env:CS_LEARNING_DB = $ResolvedDbPath
$env:CS_LEARNING_HOST = $ApiHost
if ($EnableLanSync) {
    $env:CS_LEARNING_SYNC_PUBLIC_URL = "http://${LanSyncAddress}:$SyncPort"
}
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
$env:CS_LEARNING_SYNC_PUBLIC_URL = $PreviousSyncPublicUrlEnv

$syncApi = $null
if ($EnableLanSync) {
    $env:CS_LEARNING_CONTENT = $ResolvedContentDir
    $env:CS_LEARNING_DB = $ResolvedDbPath
    $env:CS_LEARNING_SYNC_PUBLIC_URL = "http://${LanSyncAddress}:$SyncPort"
    $syncApi = Start-Process `
        -WindowStyle Hidden `
        -FilePath $Python `
        -ArgumentList @("-m", "uvicorn", "backend.sync_api:app", "--host", "0.0.0.0", "--port", "$SyncPort") `
        -WorkingDirectory $Root `
        -RedirectStandardOutput $SyncOutLog `
        -RedirectStandardError $SyncErrLog `
        -PassThru
    $env:CS_LEARNING_CONTENT = $PreviousContentEnv
    $env:CS_LEARNING_DB = $PreviousDbEnv
    $env:CS_LEARNING_SYNC_PUBLIC_URL = $PreviousSyncPublicUrlEnv
}

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
if ($EnableLanSync) {
    Write-Host ""
    Write-Host "  Sync LAN: http://${LanSyncAddress}:$SyncPort"
    Write-Host "  Sync PID: $($syncApi.Id)"
}

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
        if ($null -ne $syncApi) { $syncApi.Refresh() }

        if ($api.HasExited) {
            $exitReason = "API process exited with code $($api.ExitCode). Check $ApiErrLog"
            break
        }
        if ($frontend.HasExited) {
            $exitReason = "Frontend process exited with code $($frontend.ExitCode). Check $FrontendErrLog"
            break
        }
        if ($null -ne $syncApi -and $syncApi.HasExited) {
            $exitReason = "Sync gateway process exited with code $($syncApi.ExitCode). Check $SyncErrLog"
            break
        }
    }
}
finally {
    Stop-StartedProcess -Process $frontend -Name "frontend"
    Stop-StartedProcess -Process $syncApi -Name "sync gateway"
    Stop-StartedProcess -Process $api -Name "API"
}

if ($exitReason) {
    throw $exitReason
}

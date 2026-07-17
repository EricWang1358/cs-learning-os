param(
    [int[]]$Ports = @(8000, 8001, 5173)
)

$ErrorActionPreference = "Stop"

foreach ($port in $Ports) {
    $lines = netstat -ano | Select-String ":$port\s"
    $pids = @()

    foreach ($line in $lines) {
        $parts = ($line.ToString() -split "\s+") | Where-Object { $_ }
        if ($parts.Length -ge 5 -and $parts[3] -eq "LISTENING") {
            $pids += [int]$parts[4]
        }
    }

    $pids = $pids | Sort-Object -Unique
    if (-not $pids) {
        Write-Host "No listener on port $port"
        continue
    }

    foreach ($processId in $pids) {
        try {
            $process = Get-Process -Id $processId -ErrorAction Stop
            Write-Host "Stopping port $port owner: PID $processId ($($process.ProcessName))"
            Stop-Process -Id $processId -Force
        }
        catch {
            Write-Warning "Could not stop PID ${processId} for port ${port}: $($_.Exception.Message)"
        }
    }
}

param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$requiredRoots = @("app", "backend", "android-app", "content-demo", "docs", "scripts", "experimental")
$missingRoots = @(
    $requiredRoots |
        Where-Object { -not (Test-Path (Join-Path $RepositoryRoot $_) -PathType Container) }
)

if ($missingRoots.Count -gt 0) {
    throw "Missing canonical repository roots: $($missingRoots -join ', ')"
}

$trackedRuntime = @(
    ".playwright-cli",
    ".superpowers",
    ".claude",
    ".pytest_cache",
    "tmp",
    "data",
    "var",
    "generated"
)

$trackedFiles = @(git -C $RepositoryRoot ls-files -- $trackedRuntime)
if ($trackedFiles.Count -gt 0) {
    throw "Runtime paths are tracked: $($trackedFiles -join ', ')"
}

$ignoredProbe = Join-Path $RepositoryRoot ".playwright-cli"
$ignoreResult = git -C $RepositoryRoot check-ignore $ignoredProbe 2>$null
if (-not $ignoreResult) {
    throw "Expected local automation state to be ignored: $ignoredProbe"
}

$privateInstruction = Join-Path $RepositoryRoot "Instruction"
if (-not (git -C $RepositoryRoot check-ignore $privateInstruction 2>$null)) {
    throw "Expected private project instructions to remain ignored: $privateInstruction"
}

Write-Host "Repository layout verified: $RepositoryRoot"
Write-Host "Canonical roots: $($requiredRoots -join ', ')"
Write-Host "Runtime boundaries are ignored and untracked."

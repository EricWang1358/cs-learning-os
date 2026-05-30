param(
    [string]$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$ErrorActionPreference = "Stop"

$workspace = Resolve-Path $WorkspaceRoot
$knownAppDirs = @("cs-learning-os")
$suggestedCourseDirs = @("A1", "LAB2", "WA1", "WA2", "tmp")
$suggestedArchiveDirs = @("algorithm-writing-coach", "cs-knowledge-map", "personal-knowledge-map")

Write-Host "Workspace: $($workspace.Path)"
Write-Host ""
Write-Host "This is a dry-run plan only. It does not move files."
Write-Host ""

Write-Host "Keep as app roots:"
foreach ($name in $knownAppDirs) {
    $path = Join-Path $workspace.Path $name
    if (Test-Path $path) {
        Write-Host "  - $name"
    }
}

Write-Host ""
Write-Host "Candidate coursework folders:"
foreach ($name in $suggestedCourseDirs) {
    $path = Join-Path $workspace.Path $name
    if (Test-Path $path) {
        Write-Host "  - $name -> coursework\$name"
    }
}

Write-Host ""
Write-Host "Candidate old project/archive folders:"
foreach ($name in $suggestedArchiveDirs) {
    $path = Join-Path $workspace.Path $name
    if (Test-Path $path) {
        Write-Host "  - $name -> archive\$name"
    }
}

Write-Host ""
Write-Host "Repo-local private data should live under cs-learning-os\data and stay gitignored."
Write-Host "If you decide to move data elsewhere, set CS_LEARNING_DATA_ROOT before running scripts\dev.ps1."

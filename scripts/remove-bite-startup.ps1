$ErrorActionPreference = "Stop"

$StartupDir = [Environment]::GetFolderPath("Startup")
$ShortcutPath = Join-Path $StartupDir "CS Learning OS Daily Bite.lnk"

if (Test-Path $ShortcutPath) {
    Remove-Item -LiteralPath $ShortcutPath
    Write-Host "Removed Daily Bite startup shortcut:"
    Write-Host "  $ShortcutPath"
} else {
    Write-Host "Daily Bite startup shortcut was not installed."
}

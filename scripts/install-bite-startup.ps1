param(
    [string]$DataRoot = ""
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$StartupDir = [Environment]::GetFolderPath("Startup")
$ShortcutPath = Join-Path $StartupDir "CS Learning OS Daily Bite.lnk"
$TargetPath = Join-Path $Root "启动 Daily Bite.cmd"

if (-not (Test-Path $TargetPath)) {
    throw "Daily Bite launcher not found: $TargetPath"
}

$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($ShortcutPath)
$shortcut.TargetPath = $TargetPath
$shortcut.WorkingDirectory = $Root
$shortcut.Description = "Open CS Learning OS Daily Bite on Windows startup."
if ($DataRoot) {
    $shortcut.Arguments = $DataRoot
}
$shortcut.IconLocation = "$env:SystemRoot\System32\shell32.dll,44"
$shortcut.Save()

Write-Host "Installed Daily Bite startup shortcut:"
Write-Host "  $ShortcutPath"

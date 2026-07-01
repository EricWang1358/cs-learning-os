param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$DeviceSerial = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$androidRoot = Join-Path $ProjectRoot "android-app"
$gradleWrapper = Join-Path $androidRoot "gradlew.bat"
$apkPath = Join-Path $androidRoot "app\build\outputs\apk\debug\app-debug.apk"

function Find-Adb {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) {
        return $adbCommand.Source
    }

    $sdkRoot = $env:ANDROID_HOME
    if (-not $sdkRoot) {
        $sdkRoot = $env:ANDROID_SDK_ROOT
    }
    if (-not $sdkRoot) {
        $defaultSdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
        if (Test-Path $defaultSdkRoot) {
            $sdkRoot = $defaultSdkRoot
        }
    }

    if ($sdkRoot) {
        $candidate = Join-Path $sdkRoot "platform-tools\adb.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "adb.exe not found. Install Android platform-tools or set ANDROID_HOME/ANDROID_SDK_ROOT."
}

if (-not (Test-Path $gradleWrapper)) {
    throw "Gradle wrapper not found: $gradleWrapper"
}

if (-not $SkipBuild) {
    Push-Location $androidRoot
    try {
        & $gradleWrapper assembleDebug --console plain
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $apkPath)) {
    throw "Debug APK not found: $apkPath"
}

$adb = Find-Adb
$adbArgs = @()
if ($DeviceSerial) {
    $adbArgs += @("-s", $DeviceSerial)
}

Write-Host "Installing over the existing app with adb install -r."
Write-Host "This preserves local Room data when applicationId and signing key stay the same."
Write-Host "APK: $apkPath"

& $adb @adbArgs install -r $apkPath

Write-Host ""
Write-Host "Installed. If Android reports a signature mismatch, uninstalling would erase app data; export a backup first."

param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [switch]$Json
)

$ErrorActionPreference = "Stop"

$checks = New-Object System.Collections.Generic.List[object]

function Add-Check {
    param(
        [string]$Name,
        [bool]$Ok,
        [string]$Detail,
        [string]$Severity = "required"
    )

    $checks.Add([pscustomobject]@{
        name = $Name
        ok = $Ok
        severity = $Severity
        detail = $Detail
    }) | Out-Null

    return $Ok
}

$androidRoot = Join-Path $ProjectRoot "android-app"
$assetRoot = Join-Path $androidRoot "app\src\main\assets"

Add-Check "android-app directory" (Test-Path $androidRoot) $androidRoot | Out-Null
Add-Check "Android manifest" (Test-Path (Join-Path $androidRoot "app\src\main\AndroidManifest.xml")) "app/src/main/AndroidManifest.xml" | Out-Null
Add-Check "MainActivity" (Test-Path (Join-Path $androidRoot "app\src\main\java\com\cslearningos\mobile\MainActivity.java")) "WebView shell entry" | Out-Null
Add-Check "Fallback asset" (Test-Path (Join-Path $assetRoot "www\index.html")) "app/src/main/assets/www/index.html" | Out-Null
Add-Check "No private content in Android assets" (-not (Test-Path (Join-Path $assetRoot "data")) -and -not (Test-Path (Join-Path $assetRoot "knowledge.db"))) "assets must not contain data/content or knowledge.db" | Out-Null

try {
    $javaOutput = (& java -version 2>&1 | Out-String).Trim()
    $javaFirstLine = ($javaOutput -split "`r?`n" | Select-Object -First 1)
    $javaOk = $javaOutput -match 'version "((17|18|19|20|21|22|23|24|25|26)\.|[2-9][0-9]\.)'
    Add-Check "JDK 17+" $javaOk $javaFirstLine | Out-Null
} catch {
    Add-Check "JDK 17+" $false "java not found" | Out-Null
}

$gradleCmd = Get-Command gradle -ErrorAction SilentlyContinue
$gradleWrapper = Join-Path $androidRoot "gradlew.bat"
$gradleOk = $null -ne $gradleCmd -or (Test-Path $gradleWrapper)
$gradleDetail = if ($gradleCmd) { $gradleCmd.Source } elseif (Test-Path $gradleWrapper) { $gradleWrapper } else { "gradle or gradlew.bat not found" }
Add-Check "Gradle" $gradleOk $gradleDetail | Out-Null

$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) {
    $sdkRoot = $env:ANDROID_SDK_ROOT
}
$sdkOk = [bool]($sdkRoot -and (Test-Path $sdkRoot))
Add-Check "Android SDK" $sdkOk ($(if ($sdkRoot) { $sdkRoot } else { "ANDROID_HOME/ANDROID_SDK_ROOT not set" })) | Out-Null

$allRequiredOk = -not ($checks | Where-Object { $_.severity -eq "required" -and -not $_.ok })

if ($Json) {
    [pscustomobject]@{
        ok = $allRequiredOk
        projectRoot = $ProjectRoot
        androidRoot = $androidRoot
        checks = $checks
    } | ConvertTo-Json -Depth 5
} else {
    foreach ($check in $checks) {
        $status = if ($check.ok) { "OK" } else { "MISSING" }
        Write-Host "[$status] $($check.name) - $($check.detail)"
    }

    Write-Host ""
    if ($allRequiredOk) {
        Write-Host "Android doctor passed. Next: open android-app in Android Studio or run assembleDebug."
    } else {
        Write-Host "Android doctor found missing prerequisites. This is expected until the Android build chain is installed."
    }
}

if ($allRequiredOk) {
    exit 0
}
exit 1

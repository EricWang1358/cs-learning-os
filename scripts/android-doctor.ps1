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

function Get-JavaVersionOutput {
    param([string]$JavaExe)

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        return (& cmd.exe /d /c "`"$JavaExe`" -version 2>&1" | Out-String).Trim()
    } catch {
        return ""
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Test-JavaVersion {
    param([string]$JavaOutput)

    return $JavaOutput -match 'version "((17|18|19|20|21|22|23|24|25|26)\.|[2-9][0-9]\.)'
}

$androidRoot = Join-Path $ProjectRoot "android-app"
$assetRoot = Join-Path $androidRoot "app\src\main\assets"
$gradleWrapper = Join-Path $androidRoot "gradlew.bat"
$gradleWrapperJar = Join-Path $androidRoot "gradle\wrapper\gradle-wrapper.jar"
$gradleWrapperProperties = Join-Path $androidRoot "gradle\wrapper\gradle-wrapper.properties"
$localProperties = Join-Path $androidRoot "local.properties"

Add-Check "android-app directory" (Test-Path $androidRoot) $androidRoot | Out-Null
$manifestPath = Join-Path $androidRoot "app\src\main\AndroidManifest.xml"
$manifestExists = Test-Path $manifestPath
Add-Check "Android manifest" $manifestExists "app/src/main/AndroidManifest.xml" | Out-Null
Add-Check "Native MainActivity" (Test-Path (Join-Path $androidRoot "app\src\main\java\com\cslearningos\mobile\MainActivity.kt")) "Compose native entry" | Out-Null

$legacyFallbackAsset = Join-Path $assetRoot "www\index.html"
Add-Check "No WebView fallback asset" (-not (Test-Path $legacyFallbackAsset)) "app/src/main/assets/www/index.html absent" | Out-Null

if ($manifestExists) {
    $manifestText = Get-Content -Raw $manifestPath
    $networkBoundaryOk = $manifestText -match "android.permission.INTERNET" -and
        $manifestText -notmatch "usesCleartextTraffic" -and
        $manifestText -notmatch "networkSecurityConfig"
    Add-Check "Network permission boundary" $networkBoundaryOk "INTERNET allowed for user-configured AI; no cleartext or custom network security config" | Out-Null
    Add-Check "Automatic backup disabled" ($manifestText -match 'android:allowBackup="false"') 'android:allowBackup="false"' | Out-Null
} else {
    Add-Check "Network permission boundary" $false "manifest not found" | Out-Null
    Add-Check "Automatic backup disabled" $false "manifest not found" | Out-Null
}

$privateAssetFindings = @()
if (Test-Path $assetRoot) {
    $privateAssetFindings = Get-ChildItem $assetRoot -Recurse -Force -ErrorAction SilentlyContinue |
        Where-Object {
            $relativePath = $_.FullName.Substring($assetRoot.Length).TrimStart("\", "/")
            $normalizedPath = $relativePath -replace "\\", "/"
            $_.Name -eq "knowledge.db" -or
                $normalizedPath -like "data/content/*" -or
                $normalizedPath -like "generated/*" -or
                $normalizedPath -like ".venv/*"
        } |
        ForEach-Object { $_.FullName.Substring($assetRoot.Length).TrimStart("\", "/") }
}
$privateAssetDetail = if ($privateAssetFindings.Count -gt 0) { $privateAssetFindings -join ", " } else { "assets contain no data/content, knowledge.db, generated, or .venv payloads" }
Add-Check "No private content in Android assets" ($privateAssetFindings.Count -eq 0) $privateAssetDetail | Out-Null
Add-Check "Gradle wrapper script" (Test-Path $gradleWrapper) "android-app/gradlew.bat" | Out-Null
Add-Check "Gradle wrapper jar" (Test-Path $gradleWrapperJar) "android-app/gradle/wrapper/gradle-wrapper.jar" | Out-Null
Add-Check "Gradle wrapper properties" (Test-Path $gradleWrapperProperties) "android-app/gradle/wrapper/gradle-wrapper.properties" | Out-Null
if (Test-Path $gradleWrapperProperties) {
    $wrapperPropertiesText = Get-Content -Raw $gradleWrapperProperties
    Add-Check "Gradle wrapper distribution checksum" ($wrapperPropertiesText -match "(?m)^distributionSha256Sum=") "distributionSha256Sum present" | Out-Null
} else {
    Add-Check "Gradle wrapper distribution checksum" $false "gradle-wrapper.properties not found" | Out-Null
}

$javaCandidates = @()
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if ($javaCmd) {
    $javaCandidates += $javaCmd.Source
}
if ($env:JAVA_HOME) {
    $javaCandidates += (Join-Path $env:JAVA_HOME "bin\java.exe")
}
$javaCandidates += @(
    "C:\Program Files\Java\jdk-21\bin\java.exe",
    "C:\Program Files\Eclipse Adoptium\jdk-21\bin\java.exe",
    "C:\Program Files\Microsoft\jdk-21\bin\java.exe",
    "C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
)
$javaCandidates = $javaCandidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique

$javaOutput = ""
foreach ($candidate in $javaCandidates) {
    $candidateOutput = Get-JavaVersionOutput -JavaExe $candidate
    if (Test-JavaVersion -JavaOutput $candidateOutput) {
        $javaOutput = $candidateOutput
        break
    }
    if (-not $javaOutput) {
        $javaOutput = $candidateOutput
    }
}
$javaFirstLine = if ($javaOutput) { ($javaOutput -split "`r?`n" | Select-Object -First 1) } else { "java not found" }
$javaOk = Test-JavaVersion -JavaOutput $javaOutput
Add-Check "JDK 17+" $javaOk $javaFirstLine | Out-Null

$gradleJavaOutput = ""
$gradleJavaDetail = "Set JAVA_HOME to JDK 17+ or put JDK 17+ first on PATH"
if ($env:JAVA_HOME) {
    $gradleJavaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $gradleJavaExe) {
        $gradleJavaOutput = Get-JavaVersionOutput -JavaExe $gradleJavaExe
        $gradleJavaDetail = if ($gradleJavaOutput) { ($gradleJavaOutput -split "`r?`n" | Select-Object -First 1) } else { $gradleJavaExe }
    } else {
        $gradleJavaDetail = "JAVA_HOME is set but java.exe was not found: $gradleJavaExe"
    }
} elseif ($javaCmd) {
    $gradleJavaOutput = Get-JavaVersionOutput -JavaExe $javaCmd.Source
    $gradleJavaDetail = if ($gradleJavaOutput) { ($gradleJavaOutput -split "`r?`n" | Select-Object -First 1) } else { $javaCmd.Source }
}
Add-Check "Gradle runtime JDK 17+" (Test-JavaVersion -JavaOutput $gradleJavaOutput) $gradleJavaDetail | Out-Null

$gradleCmd = Get-Command gradle -ErrorAction SilentlyContinue
$gradleOk = $null -ne $gradleCmd -or (Test-Path $gradleWrapper)
$gradleDetail = if ($gradleCmd) { $gradleCmd.Source } elseif (Test-Path $gradleWrapper) { $gradleWrapper } else { "gradle or gradlew.bat not found" }
Add-Check "Gradle" $gradleOk $gradleDetail | Out-Null

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
$sdkOk = [bool]($sdkRoot -and (Test-Path $sdkRoot))
Add-Check "Android SDK" $sdkOk ($(if ($sdkRoot) { $sdkRoot } else { "ANDROID_HOME/ANDROID_SDK_ROOT not set" })) | Out-Null

$localPropertiesHasSdk = $false
if (Test-Path $localProperties) {
    $localPropertiesText = Get-Content -Raw $localProperties
    $localPropertiesHasSdk = $localPropertiesText -match "(?m)^sdk\.dir="
}
$sdkBindingOk = [bool](($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) -or ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) -or $localPropertiesHasSdk)
$sdkBindingDetail = if ($env:ANDROID_HOME) {
    "ANDROID_HOME=$env:ANDROID_HOME"
} elseif ($env:ANDROID_SDK_ROOT) {
    "ANDROID_SDK_ROOT=$env:ANDROID_SDK_ROOT"
} elseif ($localPropertiesHasSdk) {
    "android-app/local.properties contains sdk.dir"
} else {
    "Set ANDROID_HOME/ANDROID_SDK_ROOT or create android-app/local.properties with sdk.dir"
}
Add-Check "Gradle Android SDK binding" $sdkBindingOk $sdkBindingDetail | Out-Null

if ($sdkRoot) {
    Add-Check "Android SDK platform 35" (Test-Path (Join-Path $sdkRoot "platforms\android-35")) "platforms/android-35" | Out-Null
    Add-Check "Android build-tools 35.0.0" (Test-Path (Join-Path $sdkRoot "build-tools\35.0.0")) "build-tools/35.0.0" | Out-Null
} else {
    Add-Check "Android SDK platform 35" $false "SDK root unavailable" | Out-Null
    Add-Check "Android build-tools 35.0.0" $false "SDK root unavailable" | Out-Null
}

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

param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$requiredPaths = @(
    "android-app/app/src/main/java/com/cslearningos/mobile/data",
    "android-app/app/src/main/java/com/cslearningos/mobile/domain",
    "android-app/app/src/main/java/com/cslearningos/mobile/ui",
    "android-app/app/src/main/java/com/cslearningos/mobile/core/common/AndroidArchitectureConstants.kt",
    "android-app/app/src/main/java/com/cslearningos/mobile/appshell",
    "android-app/app/src/main/java/com/cslearningos/mobile/core",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/settings",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/backup",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/library",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/capture",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/review",
    "android-app/core/kernel",
    "android-app/domain/assistant",
    "android-app/feature/assistant/api",
    "android-app/feature/assistant/impl",
    "android-app/adapter/model-openai"
)

$requiredProjects = @(
    ":core:kernel",
    ":domain:assistant",
    ":feature:assistant:api",
    ":feature:assistant:impl",
    ":adapter:model-openai"
)

$forbiddenPureReferences = @(
    "import android.",
    "import androidx.",
    "import com.cslearningos.mobile.ui.",
    "import com.cslearningos.mobile.data.",
    "import org.json."
)

$moduleDependencyAllowlist = [ordered]@{
    "android-app/core/kernel" = @()
    "android-app/domain/assistant" = @(':core:kernel')
    "android-app/feature/assistant/api" = @()
    "android-app/feature/assistant/impl" = @(':feature:assistant:api')
    "android-app/adapter/model-openai" = @(':domain:assistant')
    "android-app/app" = @(
        ':domain:assistant',
        ':feature:assistant:api',
        ':feature:assistant:impl',
        ':adapter:model-openai'
    )
}

$pureSourceRoots = @(
    "android-app/core/kernel/src/main",
    "android-app/domain/assistant/src/main",
    "android-app/feature/assistant/api/src/main"
)

$violations = [System.Collections.Generic.List[string]]::new()

function Get-RequiredFileText {
    param(
        [string]$RelativePath,
        [string]$Description
    )

    $path = Join-Path $ProjectRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $script:violations.Add("Missing ${Description}: $RelativePath")
        return $null
    }
    return Get-Content -Raw -LiteralPath $path
}

function Get-ModuleBuildText {
    param([string]$ModulePath)

    foreach ($fileName in @("build.gradle", "build.gradle.kts")) {
        $relativePath = "$ModulePath/$fileName"
        $path = Join-Path $ProjectRoot $relativePath
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            return Get-Content -Raw -LiteralPath $path
        }
    }
    $script:violations.Add("Missing module build file: $ModulePath/build.gradle(.kts)")
    return $null
}

function Test-LegacyFileSize {
    param(
        [string]$RelativePath,
        [int]$MaxBytes
    )

    $path = Join-Path $ProjectRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $script:violations.Add("Expected legacy file is missing: $RelativePath")
        return
    }
    $size = (Get-Item -LiteralPath $path).Length
    if ($size -gt $MaxBytes) {
        Write-Warning "Legacy architecture file remains above its migration target: $RelativePath ($size bytes > $MaxBytes bytes)"
    }
}

foreach ($relativePath in $requiredPaths) {
    if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot $relativePath))) {
        $violations.Add("Required Android architecture path is missing: $relativePath")
    }
}

$settings = Get-RequiredFileText "android-app/settings.gradle" "Android settings file"
if ($null -ne $settings) {
    foreach ($projectPath in $requiredProjects) {
        $escapedProjectPath = [regex]::Escape($projectPath)
        $includePattern = '(?m)^\s*include\s*\(?\s*[''"]' + $escapedProjectPath + '[''"]'
        if ($settings -notmatch $includePattern) {
            $violations.Add("Android settings omit required project: $projectPath")
        }
    }
}

$appBuild = Get-RequiredFileText "android-app/app/build.gradle" "app build file"
if ($null -ne $appBuild -and $appBuild.Contains("../content-demo")) {
    $violations.Add("App build input escapes android-app: android-app/app/build.gradle contains ../content-demo")
}

$projectDependencyPattern = 'project\s*\(\s*(?:path\s*=\s*)?["''](?<path>:[^"'']+)["'']\s*\)'
foreach ($modulePath in $moduleDependencyAllowlist.Keys) {
    $buildText = Get-ModuleBuildText $modulePath
    if ($null -eq $buildText) { continue }
    $actualDependencies = @(
        [regex]::Matches($buildText, $projectDependencyPattern) |
            ForEach-Object { $_.Groups["path"].Value } |
            Sort-Object -Unique
    )
    $allowedDependencies = @($moduleDependencyAllowlist[$modulePath])
    foreach ($dependency in $actualDependencies) {
        if ($dependency -notin $allowedDependencies) {
            $violations.Add("Forbidden project dependency: $modulePath -> $dependency")
        }
    }
    foreach ($dependency in $allowedDependencies) {
        if ($dependency -notin $actualDependencies) {
            $violations.Add("Missing required project dependency: $modulePath -> $dependency")
        }
    }
}

foreach ($sourceRoot in $pureSourceRoots) {
    $fullSourceRoot = Join-Path $ProjectRoot $sourceRoot
    if (-not (Test-Path -LiteralPath $fullSourceRoot -PathType Container)) {
        $violations.Add("Pure Kotlin source root is missing: $sourceRoot")
        continue
    }
    foreach ($file in Get-ChildItem -LiteralPath $fullSourceRoot -Recurse -File -Filter "*.kt") {
        $rootPrefix = $ProjectRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
        $relativeFile = $file.FullName.Substring($rootPrefix.Length)
        foreach ($match in Select-String -LiteralPath $file.FullName -SimpleMatch -Pattern $forbiddenPureReferences) {
            $violations.Add("Forbidden pure-module reference: ${relativeFile}:$($match.LineNumber): $($match.Line.Trim())")
        }
    }
}

$androidRoot = Join-Path $ProjectRoot "android-app"
$gradleFiles = Get-ChildItem -LiteralPath $androidRoot -Recurse -File | Where-Object {
    $_.Name -in @("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts") -and
    $_.FullName -notmatch '[\\/]build[\\/]' -and
    $_.FullName -notmatch '[\\/]\.gradle[\\/]'
}
foreach ($file in $gradleFiles) {
    $relativeFile = $file.FullName.Substring(($ProjectRoot.TrimEnd('\') + '\').Length)
    foreach ($match in Select-String -LiteralPath $file.FullName -SimpleMatch -Pattern @("../", "..\", "parentFile")) {
        $violations.Add("Gradle input may escape android-app: ${relativeFile}:$($match.LineNumber): $($match.Line.Trim())")
    }
    foreach ($match in Select-String -LiteralPath $file.FullName -Pattern '(?i)(from|file|files|includeBuild)\s*\(\s*["''](?:[A-Za-z]:[\\/]|[\\/]{2})') {
        $violations.Add("Absolute local Gradle input is forbidden: ${relativeFile}:$($match.LineNumber): $($match.Line.Trim())")
    }
}

Test-LegacyFileSize "android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt" 38000
Test-LegacyFileSize "android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt" 30000

if ($violations.Count -gt 0) {
    throw ("Android architecture verification failed:`n - " + ($violations -join "`n - "))
}

Write-Host "Android architecture verification passed. Standalone modular Assistant slice detected."

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
    "android-app/core/database",
    "android-app/domain/assistant",
    "android-app/domain/content",
    "android-app/application/content",
    "android-app/data/content-room",
    "android-app/feature/assistant/api",
    "android-app/feature/assistant/impl",
    "android-app/adapter/model-openai"
)

$requiredProjects = @(
    ":core:kernel",
    ":core:database",
    ":domain:assistant",
    ":domain:content",
    ":application:content",
    ":data:content-room",
    ":feature:assistant:api",
    ":feature:assistant:impl",
    ":adapter:model-openai"
)

$forbiddenPureReferences = @(
    "android.",
    "androidx.",
    "com.cslearningos.mobile.ui.",
    "com.cslearningos.mobile.data.",
    "org.json."
)

$moduleDependencyAllowlist = [ordered]@{
    "android-app/core/kernel" = @()
    "android-app/core/database" = @()
    "android-app/domain/assistant" = @(':core:kernel')
    "android-app/domain/content" = @(':core:kernel')
    "android-app/application/content" = @(':core:kernel', ':domain:content')
    "android-app/data/content-room" = @(':core:kernel', ':core:database', ':domain:content', ':application:content')
    "android-app/feature/assistant/api" = @()
    "android-app/feature/assistant/impl" = @(':feature:assistant:api')
    "android-app/adapter/model-openai" = @(':domain:assistant')
    "android-app/app" = @(
        ':core:kernel',
        ':core:database',
        ':domain:content',
        ':application:content',
        ':data:content-room',
        ':domain:assistant',
        ':feature:assistant:api',
        ':feature:assistant:impl',
        ':adapter:model-openai'
    )
}

$sourceBoundaryRules = [ordered]@{
    "android-app/core/kernel/src/main" = $forbiddenPureReferences
    "android-app/domain/assistant/src/main" = $forbiddenPureReferences
    "android-app/feature/assistant/api/src/main" = $forbiddenPureReferences
    "android-app/domain/content/src/main" = @(
        "android.", "androidx.", "com.cslearningos.mobile.ui.",
        "com.cslearningos.mobile.data.", "org.json.", "androidx.room."
    )
    "android-app/application/content/src/main" = @(
        "com.cslearningos.mobile.data.", "com.cslearningos.mobile.ui."
    )
    "android-app/core/database/src/main" = @(
        "com.cslearningos.mobile.ui.", "com.cslearningos.mobile.app."
    )
    "android-app/data/content-room/src/main" = @(
        "com.cslearningos.mobile.ui.", "androidx.compose."
    )
}

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

$projectDependencyPattern = 'project\s*\(\s*(?:path\s*[:=]\s*)?["''](?<path>:[^"'']+)["'']\s*\)'
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

foreach ($sourceRoot in $sourceBoundaryRules.Keys) {
    $fullSourceRoot = Join-Path $ProjectRoot $sourceRoot
    if (-not (Test-Path -LiteralPath $fullSourceRoot -PathType Container)) {
        $violations.Add("Pure Kotlin source root is missing: $sourceRoot")
        continue
    }
    foreach ($file in Get-ChildItem -LiteralPath $fullSourceRoot -Recurse -File -Filter "*.kt") {
        $rootPrefix = $ProjectRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
        $relativeFile = $file.FullName.Substring($rootPrefix.Length)
        foreach ($match in Select-String -LiteralPath $file.FullName -SimpleMatch -Pattern $sourceBoundaryRules[$sourceRoot]) {
            $violations.Add("Forbidden pure-module reference: ${relativeFile}:$($match.LineNumber): $($match.Line.Trim())")
        }
    }
}

$androidRoot = Join-Path $ProjectRoot "android-app"
$gradleFiles = Get-ChildItem -LiteralPath $androidRoot -Recurse -File -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -in @("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts") -and
    $_.FullName -notmatch '[\\/]build[\\/]' -and
    $_.FullName -notmatch '[\\/]\.gradle[\\/]'
}
foreach ($file in $gradleFiles) {
    $relativeFile = $file.FullName.Substring(($ProjectRoot.TrimEnd('\') + '\').Length)
    $normalizedRelativeFile = $relativeFile.Replace('\', '/')
    foreach ($match in Select-String -LiteralPath $file.FullName -SimpleMatch -Pattern @("../", "..\", "parentFile")) {
        $violations.Add("Gradle input may escape android-app: ${relativeFile}:$($match.LineNumber): $($match.Line.Trim())")
    }
    foreach ($match in Select-String -LiteralPath $file.FullName -Pattern '(?i)(from|file|files|includeBuild)\s*\(\s*["''](?:[A-Za-z]:[\\/]|[\\/]{2})') {
        $violations.Add("Absolute local Gradle input is forbidden: ${relativeFile}:$($match.LineNumber): $($match.Line.Trim())")
    }
    foreach ($match in Select-String -LiteralPath $file.FullName -Pattern '\b(from|file|files|includeBuild)\s*\(') {
        $line = $match.Line.Trim()
        $allowed = switch ($normalizedRelativeFile) {
            "android-app/settings.gradle" {
                $line -match '^includeBuild\(\s*["'']build-logic["'']\s*\)$'
                break
            }
            "android-app/app/build.gradle" {
                $line -match '^from\(\s*["'']\$rootDir/starter-content["'']\s*\)\s*\{?$' -or
                    $line -match '^def starterAssetsDir = file\(\s*["'']\$buildDir/generated/starter-assets["'']\s*\)$' -or
                    $line -eq 'implementation files('
                break
            }
            default { $false }
        }
        if (-not $allowed) {
            $violations.Add("Unapproved local Gradle input: ${relativeFile}:$($match.LineNumber): $line")
        }
    }
}

Test-LegacyFileSize "android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt" 38000
Test-LegacyFileSize "android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt" 30000

if ($violations.Count -gt 0) {
    throw ("Android architecture verification failed:`n - " + ($violations -join "`n - "))
}

Write-Host "Android architecture verification passed. Standalone modular Assistant slice detected."

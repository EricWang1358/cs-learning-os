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

$forbiddenDomainImports = @(
    "import android.",
    "import androidx.",
    "import com.cslearningos.mobile.ui.",
    "import com.cslearningos.mobile.data.",
    "import org.json."
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

$assistantImplBuild = Get-ModuleBuildText "android-app/feature/assistant/impl"
if ($null -ne $assistantImplBuild -and $assistantImplBuild -notmatch 'project\s*\(\s*["'']?:feature:assistant:api["'']\s*\)') {
    $violations.Add("Assistant implementation must depend on :feature:assistant:api")
}

$modelAdapterBuild = Get-ModuleBuildText "android-app/adapter/model-openai"
if ($null -ne $modelAdapterBuild -and $modelAdapterBuild -notmatch 'project\s*\(\s*["'']?:domain:assistant["'']\s*\)') {
    $violations.Add("OpenAI model adapter must depend on :domain:assistant")
}

$domainSourceRoot = Join-Path $ProjectRoot "android-app/domain/assistant/src/main"
if (Test-Path -LiteralPath $domainSourceRoot -PathType Container) {
    foreach ($file in Get-ChildItem -LiteralPath $domainSourceRoot -Recurse -File -Filter "*.kt") {
        $rootPrefix = $ProjectRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
        $relativeFile = $file.FullName.Substring($rootPrefix.Length)
        foreach ($match in Select-String -LiteralPath $file.FullName -SimpleMatch -Pattern $forbiddenDomainImports) {
            $violations.Add("Forbidden domain import: ${relativeFile}:$($match.LineNumber): $($match.Line.Trim())")
        }
    }
} else {
    $violations.Add("Assistant domain source root is missing: android-app/domain/assistant/src/main")
}

Test-LegacyFileSize "android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt" 38000
Test-LegacyFileSize "android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt" 30000

if ($violations.Count -gt 0) {
    throw ("Android architecture verification failed:`n - " + ($violations -join "`n - "))
}

Write-Host "Android architecture verification passed. Standalone modular Assistant slice detected."

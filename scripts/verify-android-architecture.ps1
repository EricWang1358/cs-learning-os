param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$bootstrapRequiredPaths = @(
    "android-app/app/src/main/java/com/cslearningos/mobile/data",
    "android-app/app/src/main/java/com/cslearningos/mobile/domain",
    "android-app/app/src/main/java/com/cslearningos/mobile/ui",
    "android-app/app/src/main/java/com/cslearningos/mobile/core/common/AndroidArchitectureConstants.kt"
)

$featureRootRelativePath = "android-app/app/src/main/java/com/cslearningos/mobile/feature"
$featureStructureRequiredPaths = @(
    "android-app/app/src/main/java/com/cslearningos/mobile/appshell",
    "android-app/app/src/main/java/com/cslearningos/mobile/core",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/settings",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/backup",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/library",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/capture",
    "android-app/app/src/main/java/com/cslearningos/mobile/feature/review"
)

function Get-MissingRelativePaths {
    param(
        [string]$Root,
        [string[]]$RelativePaths
    )

    return @(
        $RelativePaths | Where-Object {
            -not (Test-Path (Join-Path $Root $_))
        }
    )
}

function Test-LegacyFileSize {
    param(
        [string]$Root,
        [string]$RelativePath,
        [int]$MaxBytes
    )

    $fullPath = Join-Path $Root $RelativePath
    if (-not (Test-Path $fullPath)) {
        throw ("Expected legacy file is missing: " + $RelativePath)
    }

    $size = (Get-Item $fullPath).Length
    if ($size -gt $MaxBytes) {
        throw ("Legacy architecture file still too large: {0} ({1} bytes > {2} bytes)" -f $RelativePath, $size, $MaxBytes)
    }
}

$missingBootstrapPaths = Get-MissingRelativePaths -Root $ProjectRoot -RelativePaths $bootstrapRequiredPaths
if ($missingBootstrapPaths.Count -gt 0) {
    throw ("Android architecture baseline missing: " + ($missingBootstrapPaths -join ", "))
}

$featureRootPath = Join-Path $ProjectRoot $featureRootRelativePath
if (Test-Path $featureRootPath) {
    $missingFeaturePaths = Get-MissingRelativePaths -Root $ProjectRoot -RelativePaths $featureStructureRequiredPaths
    if ($missingFeaturePaths.Count -gt 0) {
        throw ("Android feature structure missing: " + ($missingFeaturePaths -join ", "))
    }

    Test-LegacyFileSize -Root $ProjectRoot -RelativePath "android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt" -MaxBytes 38000
    Test-LegacyFileSize -Root $ProjectRoot -RelativePath "android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt" -MaxBytes 30000

    Write-Host "Android architecture verification passed. Feature-first layout detected."
    exit 0
}

Write-Host "Android architecture verification passed. Bootstrap layout active until feature/ is introduced."
exit 0

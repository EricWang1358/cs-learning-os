param(
    [string]$VerifierPath = (Join-Path $PSScriptRoot "verify-android-architecture.ps1")
)

$ErrorActionPreference = "Stop"

function Set-FixtureFile {
    param(
        [string]$Root,
        [string]$RelativePath,
        [string]$Content = ""
    )

    $path = Join-Path $Root $RelativePath
    New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
    Set-Content -LiteralPath $path -Value $Content -Encoding UTF8
}

function New-ArchitectureFixture {
    param([string]$Root)

    $directories = @(
        "android-app/app/src/main/java/com/cslearningos/mobile/data",
        "android-app/app/src/main/java/com/cslearningos/mobile/domain",
        "android-app/app/src/main/java/com/cslearningos/mobile/ui",
        "android-app/app/src/main/java/com/cslearningos/mobile/appshell",
        "android-app/app/src/main/java/com/cslearningos/mobile/core",
        "android-app/app/src/main/java/com/cslearningos/mobile/feature/settings",
        "android-app/app/src/main/java/com/cslearningos/mobile/feature/backup",
        "android-app/app/src/main/java/com/cslearningos/mobile/feature/library",
        "android-app/app/src/main/java/com/cslearningos/mobile/feature/capture",
        "android-app/app/src/main/java/com/cslearningos/mobile/feature/review",
        "android-app/core/kernel",
        "android-app/domain/assistant/src/main/kotlin/example",
        "android-app/feature/assistant/api",
        "android-app/feature/assistant/impl",
        "android-app/adapter/model-openai"
    )
    foreach ($directory in $directories) {
        New-Item -ItemType Directory -Force -Path (Join-Path $Root $directory) | Out-Null
    }

    Set-FixtureFile $Root "android-app/app/src/main/java/com/cslearningos/mobile/core/common/AndroidArchitectureConstants.kt"
    Set-FixtureFile $Root "android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt"
    Set-FixtureFile $Root "android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt"
    Set-FixtureFile $Root "android-app/app/build.gradle" 'from("$rootDir/starter-content")'
    Set-FixtureFile $Root "android-app/settings.gradle" @'
include ":core:kernel"
include ":domain:assistant"
include ":feature:assistant:api"
include ":feature:assistant:impl"
include ":adapter:model-openai"
'@
    Set-FixtureFile $Root "android-app/feature/assistant/impl/build.gradle" 'implementation project(":feature:assistant:api")'
    Set-FixtureFile $Root "android-app/adapter/model-openai/build.gradle" 'implementation project(":domain:assistant")'
    Set-FixtureFile $Root "android-app/domain/assistant/src/main/kotlin/example/Assistant.kt" "package example"
}

function Invoke-ArchitectureVerifier {
    param([string]$Root)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $VerifierPath -ProjectRoot $Root 2>&1 | Out-String
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    return @{ ExitCode = $exitCode; Output = $output }
}

function Assert-ExitCode {
    param(
        [hashtable]$Result,
        [int]$Expected,
        [string]$Case
    )

    if ($Result.ExitCode -ne $Expected) {
        throw "$Case expected exit code $Expected but got $($Result.ExitCode). Output: $($Result.Output)"
    }
}

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("cslearningos-architecture-" + [guid]::NewGuid())
try {
    New-ArchitectureFixture $fixtureRoot
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 0 "valid modular fixture"

    Remove-Item -Recurse -Force (Join-Path $fixtureRoot "android-app/core/kernel")
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "missing required module"
    New-Item -ItemType Directory -Force -Path (Join-Path $fixtureRoot "android-app/core/kernel") | Out-Null

    Set-FixtureFile $fixtureRoot "android-app/domain/assistant/src/main/kotlin/example/Assistant.kt" "package example`nimport android.content.Context"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "forbidden domain import"
    Set-FixtureFile $fixtureRoot "android-app/domain/assistant/src/main/kotlin/example/Assistant.kt" "package example"

    Set-FixtureFile $fixtureRoot "android-app/app/build.gradle" 'from("../content-demo")'
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "parent starter-content dependency"
    Set-FixtureFile $fixtureRoot "android-app/app/build.gradle" 'from("$rootDir/starter-content")'

    Set-FixtureFile $fixtureRoot "android-app/feature/assistant/impl/build.gradle" "dependencies {}"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "missing assistant API dependency"
    Set-FixtureFile $fixtureRoot "android-app/feature/assistant/impl/build.gradle" 'implementation project(":feature:assistant:api")'

    Set-FixtureFile $fixtureRoot "android-app/adapter/model-openai/build.gradle" "dependencies {}"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "missing assistant domain dependency"

    Write-Host "Android architecture verifier regression tests passed."
} finally {
    if (Test-Path $fixtureRoot) {
        Remove-Item -Recurse -Force $fixtureRoot
    }
}

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
        "android-app/core/kernel/src/main/kotlin/example",
        "android-app/core/database/src/main/kotlin/example",
        "android-app/domain/assistant/src/main/kotlin/example",
        "android-app/domain/content/src/main/kotlin/example",
        "android-app/application/content/src/main/kotlin/example",
        "android-app/data/content-room/src/main/kotlin/example",
        "android-app/feature/assistant/api/src/main/kotlin/example",
        "android-app/feature/assistant/impl",
        "android-app/adapter/model-openai"
    )
    foreach ($directory in $directories) {
        New-Item -ItemType Directory -Force -Path (Join-Path $Root $directory) | Out-Null
    }

    Set-FixtureFile $Root "android-app/app/src/main/java/com/cslearningos/mobile/core/common/AndroidArchitectureConstants.kt"
    Set-FixtureFile $Root "android-app/app/src/main/java/com/cslearningos/mobile/ui/LearningViewModel.kt"
    Set-FixtureFile $Root "android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt"
    Set-FixtureFile $Root "android-app/app/build.gradle" @'
from("$rootDir/starter-content")
implementation project(":core:kernel")
implementation project(":core:database")
implementation project(":domain:content")
implementation project(":application:content")
implementation project(":data:content-room")
implementation project(":domain:assistant")
implementation project(":feature:assistant:api")
implementation project(":feature:assistant:impl")
implementation project(":adapter:model-openai")
'@
    Set-FixtureFile $Root "android-app/core/kernel/build.gradle" "dependencies {}"
    Set-FixtureFile $Root "android-app/core/database/build.gradle" "dependencies {}"
    Set-FixtureFile $Root "android-app/domain/assistant/build.gradle" 'implementation project(":core:kernel")'
    Set-FixtureFile $Root "android-app/domain/content/build.gradle" 'implementation project(":core:kernel")'
    Set-FixtureFile $Root "android-app/application/content/build.gradle" @'
implementation project(":core:kernel")
implementation project(":domain:content")
'@
    Set-FixtureFile $Root "android-app/data/content-room/build.gradle" @'
implementation project(":core:kernel")
implementation project(":core:database")
implementation project(":domain:content")
implementation project(":application:content")
'@
    Set-FixtureFile $Root "android-app/feature/assistant/api/build.gradle" "dependencies {}"
    Set-FixtureFile $Root "android-app/settings.gradle" @'
include ":app"
include ":core:kernel"
include ":core:database"
include ":domain:assistant"
include ":domain:content"
include ":application:content"
include ":data:content-room"
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

    Set-FixtureFile $fixtureRoot "android-app/settings.gradle" @'
include ":core:kernel"
include ":core:database"
include ":domain:assistant"
include ":domain:content"
include ":application:content"
include ":data:content-room"
include ":feature:assistant:api"
include ":feature:assistant:impl"
include ":adapter:model-openai"
'@
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "missing app composition root"
    Set-FixtureFile $fixtureRoot "android-app/settings.gradle" @'
include ":app"
include ":core:kernel"
include ":core:database"
include ":domain:assistant"
include ":domain:content"
include ":application:content"
include ":data:content-room"
include ":feature:assistant:api"
include ":feature:assistant:impl"
include ":adapter:model-openai"
'@

    Remove-Item -Recurse -Force (Join-Path $fixtureRoot "android-app/core/kernel")
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "missing required module"
    New-Item -ItemType Directory -Force -Path (Join-Path $fixtureRoot "android-app/core/kernel/src/main/kotlin/example") | Out-Null
    Set-FixtureFile $fixtureRoot "android-app/core/kernel/build.gradle" "dependencies {}"

    Set-FixtureFile $fixtureRoot "android-app/domain/assistant/src/main/kotlin/example/Assistant.kt" "package example`nimport android.content.Context"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "forbidden domain import"
    Set-FixtureFile $fixtureRoot "android-app/domain/assistant/src/main/kotlin/example/Assistant.kt" "package example"

    # Phase 2A boundaries must reject a reverse database dependency. The Phase 1-only
    # verifier initially misses this fixture, proving the regression test is meaningful.
    Set-FixtureFile $fixtureRoot "android-app/core/database/build.gradle" 'implementation project(":app")'
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "reverse database dependency"
    Set-FixtureFile $fixtureRoot "android-app/core/database/build.gradle" "dependencies {}"

    Set-FixtureFile $fixtureRoot "android-app/core/database/build.gradle" "implementation project(path: ':app', configuration: 'default')"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "configured reverse database dependency"
    Set-FixtureFile $fixtureRoot "android-app/core/database/build.gradle" "dependencies {}"

    Set-FixtureFile $fixtureRoot "android-app/domain/content/src/main/kotlin/example/Content.kt" "package example`nimport androidx.room.Entity"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "Room reference in content domain"
    Remove-Item -Force (Join-Path $fixtureRoot "android-app/domain/content/src/main/kotlin/example/Content.kt")

    Set-FixtureFile $fixtureRoot "android-app/application/content/src/main/kotlin/example/Command.kt" "package example`nimport com.cslearningos.mobile.data.LearningDao"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "legacy app-data reference in content application"
    Remove-Item -Force (Join-Path $fixtureRoot "android-app/application/content/src/main/kotlin/example/Command.kt")

    Set-FixtureFile $fixtureRoot "android-app/application/content/src/main/kotlin/example/Command.kt" "package example`nimport android.content.Context"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "Android reference in content application"
    Remove-Item -Force (Join-Path $fixtureRoot "android-app/application/content/src/main/kotlin/example/Command.kt")

    Set-FixtureFile $fixtureRoot "android-app/application/content/src/main/kotlin/example/Command.kt" "package example`nimport androidx.room.Dao"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "Room reference in content application"
    Remove-Item -Force (Join-Path $fixtureRoot "android-app/application/content/src/main/kotlin/example/Command.kt")

    Set-FixtureFile $fixtureRoot "android-app/application/content/src/main/kotlin/example/Command.kt" "package example`nimport org.json.JSONObject"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "JSON reference in content application"
    Remove-Item -Force (Join-Path $fixtureRoot "android-app/application/content/src/main/kotlin/example/Command.kt")

    Set-FixtureFile $fixtureRoot "android-app/data/content-room/src/main/kotlin/example/Adapter.kt" "package example`nimport com.cslearningos.mobile.ui.LibraryScreen"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "UI reference in Room content adapter"
    Remove-Item -Force (Join-Path $fixtureRoot "android-app/data/content-room/src/main/kotlin/example/Adapter.kt")

    Set-FixtureFile $fixtureRoot "android-app/app/build.gradle" 'from("$rootDir/../private-content")'
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "parent starter-content dependency"
    Set-FixtureFile $fixtureRoot "android-app/app/build.gradle" @'
from("$rootDir/starter-content")
implementation project(":core:kernel")
implementation project(":core:database")
implementation project(":domain:content")
implementation project(":application:content")
implementation project(":data:content-room")
implementation project(":domain:assistant")
implementation project(":feature:assistant:api")
implementation project(":feature:assistant:impl")
implementation project(":adapter:model-openai")
'@

    Set-FixtureFile $fixtureRoot "android-app/app/build.gradle" @'
def externalDir = rootDir.toPath().resolveSibling("private-content").toFile()
from(externalDir)
implementation project(":domain:assistant")
implementation project(":feature:assistant:api")
implementation project(":feature:assistant:impl")
implementation project(":adapter:model-openai")
'@
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "indirect parent build input"
    Set-FixtureFile $fixtureRoot "android-app/app/build.gradle" @'
from("$rootDir/starter-content")
implementation project(":core:kernel")
implementation project(":core:database")
implementation project(":domain:content")
implementation project(":application:content")
implementation project(":data:content-room")
implementation project(":domain:assistant")
implementation project(":feature:assistant:api")
implementation project(":feature:assistant:impl")
implementation project(":adapter:model-openai")
'@

    Set-FixtureFile $fixtureRoot "android-app/core/kernel/build.gradle" "implementation project(path: ':domain:assistant')"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "reverse kernel dependency"
    Set-FixtureFile $fixtureRoot "android-app/core/kernel/build.gradle" "dependencies {}"

    Set-FixtureFile $fixtureRoot "android-app/core/kernel/src/main/kotlin/example/Kernel.kt" "package example`nval type = android.content.Context::class"
    Assert-ExitCode (Invoke-ArchitectureVerifier $fixtureRoot) 1 "forbidden fully-qualified kernel reference"
    Remove-Item -Force (Join-Path $fixtureRoot "android-app/core/kernel/src/main/kotlin/example/Kernel.kt")

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

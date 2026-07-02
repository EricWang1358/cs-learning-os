param(
    [string]$SourceVerifyScript = (Join-Path $PSScriptRoot "verify-android-beta.ps1")
)

$ErrorActionPreference = "Stop"

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function New-TestProject {
    param(
        [string]$Root
    )

    $androidApp = Join-Path $Root "android-app\app"
    $androidSourceRoot = Join-Path $androidApp "src\main\java\com\cslearningos\mobile"
    $releaseNotes = Join-Path $Root "docs\release-notes.md"
    $doctorScript = Join-Path $Root "scripts\android-doctor.ps1"

    New-Item -ItemType Directory -Force -Path (Join-Path $androidSourceRoot "data") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $androidSourceRoot "domain") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $androidSourceRoot "ui") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $androidSourceRoot "core\common") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $androidApp "src\main\java\com\example") | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path $releaseNotes) | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path $doctorScript) | Out-Null

    @'
android {
    defaultConfig {
        applicationId "com.example.app"
        versionCode 9
        versionName "0.1.8"
    }
}
'@ | Set-Content -Encoding UTF8 (Join-Path $androidApp "build.gradle")

    @'
# Release Notes

## Android Beta 0.1.8

Date: 2026-07-01

Git tag: `android-v0.1.8-beta`

Android version:

- `versionCode`: 9
- `versionName`: `0.1.8`

Status: implementation beta.

Highlights:

- Baseline beta build.
'@ | Set-Content -Encoding UTF8 $releaseNotes

    'param(); @{ ok = $true; checks = @() } | ConvertTo-Json' | Set-Content -Encoding UTF8 $doctorScript

    'class Sample {}' | Set-Content -Encoding UTF8 (Join-Path $androidApp "src\main\java\com\example\Sample.kt")
    @'
package com.cslearningos.mobile.ui

class LearningViewModel
'@ | Set-Content -Encoding UTF8 (Join-Path $androidSourceRoot "ui\LearningViewModel.kt")
    @'
package com.cslearningos.mobile.data

class LearningRepository
'@ | Set-Content -Encoding UTF8 (Join-Path $androidSourceRoot "data\LearningRepository.kt")
    @'
package com.cslearningos.mobile.core.common

object AndroidArchitectureConstants {
    const val DueReviewRefreshIntervalMillis: Long = 60_000L
}
'@ | Set-Content -Encoding UTF8 (Join-Path $androidSourceRoot "core\common\AndroidArchitectureConstants.kt")
}

function Initialize-TestGitRepo {
    param(
        [string]$Root
    )

    & git -C $Root init | Out-Null
    & git -C $Root config user.name "Codex Test" | Out-Null
    & git -C $Root config user.email "codex-test@example.com" | Out-Null
    & git -C $Root add . | Out-Null
    & git -C $Root commit -m "baseline android beta" | Out-Null
    & git -C $Root tag android-v0.1.8-beta | Out-Null
}

function Invoke-Verify {
    param(
        [string]$VerifyScript,
        [string]$ProjectRoot
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $VerifyScript -ProjectRoot $ProjectRoot -SkipDoctor -SkipUnitTests -SkipAssemble 2>&1 | Out-String
    $ErrorActionPreference = $previousErrorActionPreference
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = $output
    }
}

function Invoke-ArchitectureVerify {
    param(
        [string]$VerifyScript,
        [string]$ProjectRoot
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $VerifyScript -ProjectRoot $ProjectRoot 2>&1 | Out-String
    $ErrorActionPreference = $previousErrorActionPreference
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = $output
    }
}

function Test-ArchitectureHarnessRequiresFeatureFolders {
    param(
        [string]$VerifyScript
    )

    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("verify-android-architecture-" + [System.Guid]::NewGuid().ToString("N"))
    $architectureScript = Join-Path (Split-Path $VerifyScript -Parent) "verify-android-architecture.ps1"

    try {
        New-TestProject -Root $root
        New-Item -ItemType Directory -Force -Path (Join-Path $root "android-app\app\src\main\java\com\cslearningos\mobile\feature\settings\ui") | Out-Null

        $result = Invoke-ArchitectureVerify -VerifyScript $architectureScript -ProjectRoot $root
        Assert-True ($result.ExitCode -ne 0) "Expected architecture harness to fail when required feature folders are missing, but it passed.`n$($result.Output)"
        Assert-True ($result.Output -match "feature structure") "Expected architecture harness to report missing feature structure.`n$($result.Output)"
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Test-DirtyAndroidChangesRequireVersionBump {
    param(
        [string]$VerifyScript
    )

    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("verify-android-beta-" + [System.Guid]::NewGuid().ToString("N"))

    try {
        New-TestProject -Root $root
        Initialize-TestGitRepo -Root $root

        'class Sample { fun changed() = true }' | Set-Content -Encoding UTF8 (Join-Path $root "android-app\app\src\main\java\com\example\Sample.kt")
        $result = Invoke-Verify -VerifyScript $VerifyScript -ProjectRoot $root

        Assert-True ($result.ExitCode -ne 0) "Expected verify-android-beta to fail for dirty Android changes without a version bump, but it passed.`n$result"
        Assert-True ($result.Output -match "working tree") "Expected output to mention Android working tree/version gate failure.`n$($result.Output)"
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Test-BumpedTaggedReleasePasses {
    param(
        [string]$VerifyScript
    )

    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("verify-android-beta-" + [System.Guid]::NewGuid().ToString("N"))

    try {
        New-TestProject -Root $root
        Initialize-TestGitRepo -Root $root

        @'
android {
    defaultConfig {
        applicationId "com.example.app"
        versionCode 10
        versionName "0.1.9"
    }
}
'@ | Set-Content -Encoding UTF8 (Join-Path $root "android-app\app\build.gradle")

        @'
# Release Notes

## Android Beta 0.1.9

Date: 2026-07-02

Git tag: `android-v0.1.9-beta`

Android version:

- `versionCode`: 10
- `versionName`: `0.1.9`

Status: implementation beta.

Highlights:

- Version bump for the next Android beta.

## Android Beta 0.1.8

Date: 2026-07-01

Git tag: `android-v0.1.8-beta`

Android version:

- `versionCode`: 9
- `versionName`: `0.1.8`

Status: implementation beta.

Highlights:

- Baseline beta build.
'@ | Set-Content -Encoding UTF8 (Join-Path $root "docs\release-notes.md")

        New-Item -ItemType Directory -Force -Path (Join-Path $root "android-app\app\src\main\java\com\cslearningos\mobile\feature\settings") | Out-Null
        New-Item -ItemType Directory -Force -Path (Join-Path $root "android-app\app\src\main\java\com\cslearningos\mobile\feature\backup") | Out-Null
        New-Item -ItemType Directory -Force -Path (Join-Path $root "android-app\app\src\main\java\com\cslearningos\mobile\feature\library") | Out-Null
        New-Item -ItemType Directory -Force -Path (Join-Path $root "android-app\app\src\main\java\com\cslearningos\mobile\feature\capture") | Out-Null
        New-Item -ItemType Directory -Force -Path (Join-Path $root "android-app\app\src\main\java\com\cslearningos\mobile\feature\review") | Out-Null
        New-Item -ItemType Directory -Force -Path (Join-Path $root "android-app\app\src\main\java\com\cslearningos\mobile\appshell") | Out-Null

        & git -C $root add . | Out-Null
        & git -C $root commit -m "bump android beta version" | Out-Null
        & git -C $root tag android-v0.1.9-beta | Out-Null

        $result = Invoke-Verify -VerifyScript $VerifyScript -ProjectRoot $root
        Assert-True ($result.ExitCode -eq 0) "Expected verify-android-beta to pass for a bumped and tagged Android beta.`n$($result.Output)"
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Test-ArchitectureHarnessRequiresFeatureFolders -VerifyScript $SourceVerifyScript
Test-DirtyAndroidChangesRequireVersionBump -VerifyScript $SourceVerifyScript
Test-BumpedTaggedReleasePasses -VerifyScript $SourceVerifyScript

Write-Host "verify-android-beta regression tests passed."

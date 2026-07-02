param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [switch]$Json,
    [switch]$SkipArchitecture,
    [switch]$SkipDoctor,
    [switch]$SkipUnitTests,
    [switch]$SkipAssemble,
    [switch]$AllowTagMismatchWithHead
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

function Invoke-ExternalCommand {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = ""
    )

    $output = ""
    $exitCode = 1

    try {
        if ($WorkingDirectory) {
            Push-Location $WorkingDirectory
        }

        $output = (& $FilePath @Arguments 2>&1 | Out-String).Trim()
        $exitCode = $LASTEXITCODE
        if ($null -eq $exitCode) {
            $exitCode = 0
        }
    } catch {
        $output = ($_ | Out-String).Trim()
        $exitCode = 1
    } finally {
        if ($WorkingDirectory) {
            Pop-Location
        }
    }

    return [pscustomobject]@{
        exitCode = $exitCode
        output = $output
    }
}

function Get-GitStatusLines {
    param(
        [string]$WorkingDirectory,
        [string[]]$Pathspecs = @()
    )

    $statusRun = Invoke-ExternalCommand -FilePath "git" -Arguments @("status", "--porcelain", "--untracked-files=all", "--") + $Pathspecs -WorkingDirectory $WorkingDirectory
    if ($statusRun.exitCode -ne 0) {
        return [pscustomobject]@{
            ok = $false
            lines = @()
            detail = $statusRun.output
        }
    }

    $lines = @($statusRun.output -split "\r?\n" | Where-Object { $_.Trim() })
    return [pscustomobject]@{
        ok = $true
        lines = $lines
        detail = $statusRun.output
    }
}

function Get-BuildGradleVersion {
    param([string]$BuildGradlePath)

    $result = [ordered]@{
        versionCode = $null
        versionName = $null
    }

    if (-not (Test-Path $BuildGradlePath)) {
        return [pscustomobject]$result
    }

    $buildGradleText = Get-Content -Raw $BuildGradlePath
    $versionCodeMatch = [regex]::Match($buildGradleText, '(?m)^\s*versionCode\s+(?<value>\d+)\s*$')
    $versionNameMatch = [regex]::Match($buildGradleText, '(?m)^\s*versionName\s+"(?<value>[^"]+)"\s*$')

    if ($versionCodeMatch.Success) {
        $result.versionCode = [int]$versionCodeMatch.Groups["value"].Value
    }

    if ($versionNameMatch.Success) {
        $result.versionName = $versionNameMatch.Groups["value"].Value
    }

    return [pscustomobject]$result
}

function Get-LatestAndroidReleaseNotes {
    param([string]$ReleaseNotesPath)

    $result = [ordered]@{
        heading = $null
        headingVersion = $null
        date = $null
        gitTag = $null
        versionCode = $null
        versionName = $null
        status = $null
        highlightsCount = 0
    }

    if (-not (Test-Path $ReleaseNotesPath)) {
        return [pscustomobject]$result
    }

    $releaseNotesText = Get-Content -Raw $ReleaseNotesPath
    $sectionMatches = [regex]::Matches(
        $releaseNotesText,
        '(?ms)^##\s+(?<heading>Android Beta [^\r\n]+)\r?\n(?<body>.*?)(?=^##\s+|\z)'
    )

    if ($sectionMatches.Count -eq 0) {
        return [pscustomobject]$result
    }

    $latestSection = $sectionMatches[0]
    $result.heading = $latestSection.Groups["heading"].Value.Trim()

    $headingVersionMatch = [regex]::Match($result.heading, '^Android Beta\s+(?<value>\S.+)$')
    if ($headingVersionMatch.Success) {
        $result.headingVersion = $headingVersionMatch.Groups["value"].Value.Trim()
    }

    $sectionBody = $latestSection.Groups["body"].Value

    $dateMatch = [regex]::Match($sectionBody, '(?m)^Date:\s*(?<value>\S.*)\s*$')
    if ($dateMatch.Success) {
        $result.date = $dateMatch.Groups["value"].Value.Trim()
    }

    $gitTagMatch = [regex]::Match($sectionBody, '(?m)^Git tag:\s*`(?<value>[^`]+)`\s*$')
    if ($gitTagMatch.Success) {
        $result.gitTag = $gitTagMatch.Groups["value"].Value.Trim()
    }

    $versionCodeMatch = [regex]::Match($sectionBody, '(?m)^-\s+`versionCode`:\s*(?<value>\d+)\s*$')
    if ($versionCodeMatch.Success) {
        $result.versionCode = [int]$versionCodeMatch.Groups["value"].Value
    }

    $versionNameMatch = [regex]::Match($sectionBody, '(?m)^-\s+`versionName`:\s*`(?<value>[^`]+)`\s*$')
    if ($versionNameMatch.Success) {
        $result.versionName = $versionNameMatch.Groups["value"].Value.Trim()
    }

    $statusMatch = [regex]::Match($sectionBody, '(?m)^Status:\s*(?<value>\S.*)\s*$')
    if ($statusMatch.Success) {
        $result.status = $statusMatch.Groups["value"].Value.Trim()
    }

    $lines = $sectionBody -split "\r?\n"
    $highlightsIndex = -1
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match '^Highlights:\s*$') {
            $highlightsIndex = $i
            break
        }
    }

    if ($highlightsIndex -ge 0) {
        $bulletCount = 0
        $started = $false

        for ($i = $highlightsIndex + 1; $i -lt $lines.Length; $i++) {
            $line = $lines[$i]
            if ($line -match '^\s*$') {
                if ($started) {
                    continue
                }

                continue
            }

            if ($line -match '^\-\s+') {
                $started = $true
                $bulletCount++
                continue
            }

            if ($started) {
                break
            }

            break
        }

        $result.highlightsCount = $bulletCount
    }

    return [pscustomobject]$result
}

$buildGradlePath = Join-Path $ProjectRoot "android-app\app\build.gradle"
$releaseNotesPath = Join-Path $ProjectRoot "docs\release-notes.md"
$androidRoot = Join-Path $ProjectRoot "android-app"
$gradleWrapper = Join-Path $androidRoot "gradlew.bat"
$architectureScript = Join-Path $PSScriptRoot "verify-android-architecture.ps1"
$doctorScript = Join-Path $ProjectRoot "scripts\android-doctor.ps1"
$buildGradleExists = Test-Path $buildGradlePath
$releaseNotesExists = Test-Path $releaseNotesPath

Add-Check "build.gradle exists" $buildGradleExists $buildGradlePath | Out-Null
Add-Check "release notes exists" $releaseNotesExists $releaseNotesPath | Out-Null

$androidVersion = Get-BuildGradleVersion -BuildGradlePath $buildGradlePath
$expectedTag = if ($androidVersion.versionName) { "android-v$($androidVersion.versionName)-beta" } else { $null }
$latestAndroidReleaseNotes = Get-LatestAndroidReleaseNotes -ReleaseNotesPath $releaseNotesPath

$latestAndroidSectionExists = [bool]$latestAndroidReleaseNotes.heading
Add-Check "latest Android section exists" $latestAndroidSectionExists $(if ($latestAndroidSectionExists) { $latestAndroidReleaseNotes.heading } else { "No Android Beta section found in $releaseNotesPath" }) | Out-Null

$headingMatchesVersion = [bool]($latestAndroidReleaseNotes.headingVersion -and $androidVersion.versionName -and $latestAndroidReleaseNotes.headingVersion -eq $androidVersion.versionName)
Add-Check "release heading matches versionName" $headingMatchesVersion "Heading version '$($latestAndroidReleaseNotes.headingVersion)' vs Gradle versionName '$($androidVersion.versionName)'." | Out-Null

$dateHasExpectedFormat = [bool]($latestAndroidReleaseNotes.date -and $latestAndroidReleaseNotes.date -match '^\d{4}-\d{2}-\d{2}$')
Add-Check "release date uses YYYY-MM-DD" $dateHasExpectedFormat "Date: '$($latestAndroidReleaseNotes.date)'." | Out-Null

$versionCodeMatches = [bool]($null -ne $latestAndroidReleaseNotes.versionCode -and $null -ne $androidVersion.versionCode -and $latestAndroidReleaseNotes.versionCode -eq $androidVersion.versionCode)
Add-Check "release versionCode matches Gradle" $versionCodeMatches "Release notes versionCode '$($latestAndroidReleaseNotes.versionCode)' vs Gradle '$($androidVersion.versionCode)'." | Out-Null

$versionNameMatches = [bool]($latestAndroidReleaseNotes.versionName -and $androidVersion.versionName -and $latestAndroidReleaseNotes.versionName -eq $androidVersion.versionName)
Add-Check "release versionName matches Gradle" $versionNameMatches "Release notes versionName '$($latestAndroidReleaseNotes.versionName)' vs Gradle '$($androidVersion.versionName)'." | Out-Null

$tagMatchesExpected = [bool]($latestAndroidReleaseNotes.gitTag -and $expectedTag -and $latestAndroidReleaseNotes.gitTag -eq $expectedTag)
Add-Check "release tag matches expected tag" $tagMatchesExpected "Release notes tag '$($latestAndroidReleaseNotes.gitTag)' vs expected '$expectedTag'." | Out-Null

$statusExists = [bool]($latestAndroidReleaseNotes.status -and $latestAndroidReleaseNotes.status.Trim())
Add-Check "release status exists" $statusExists "Status: '$($latestAndroidReleaseNotes.status)'." | Out-Null

$highlightsExist = $latestAndroidReleaseNotes.highlightsCount -ge 1
Add-Check "release highlights exist" $highlightsExist "Highlights bullets counted: $($latestAndroidReleaseNotes.highlightsCount)." | Out-Null

$tagExists = $false
$tagCommit = $null
$headCommit = $null

if ($expectedTag) {
    $tagCheck = Invoke-ExternalCommand -FilePath "git" -Arguments @("rev-parse", "-q", "--verify", "refs/tags/$expectedTag") -WorkingDirectory $ProjectRoot
    $tagExists = $tagCheck.exitCode -eq 0
    Add-Check "expected tag exists in git" $tagExists $(if ($tagExists) { "Git tag '$expectedTag' exists." } else { "Git tag '$expectedTag' not found. $($tagCheck.output)" }) | Out-Null

    if ($tagExists) {
        $tagCommitCheck = Invoke-ExternalCommand -FilePath "git" -Arguments @("rev-list", "-n", "1", $expectedTag) -WorkingDirectory $ProjectRoot
        if ($tagCommitCheck.exitCode -eq 0 -and $tagCommitCheck.output) {
            $tagCommit = ($tagCommitCheck.output -split "\r?\n" | Select-Object -First 1).Trim()
        }
    }

    $headCommitCheck = Invoke-ExternalCommand -FilePath "git" -Arguments @("rev-parse", "HEAD") -WorkingDirectory $ProjectRoot
    if ($headCommitCheck.exitCode -eq 0 -and $headCommitCheck.output) {
        $headCommit = ($headCommitCheck.output -split "\r?\n" | Select-Object -First 1).Trim()
    }
} else {
    Add-Check "expected tag exists in git" $false "Expected tag could not be derived because Gradle versionName was not found." | Out-Null
}

$tagPointsToHead = [bool]($tagExists -and $tagCommit -and $headCommit -and $tagCommit -eq $headCommit)
if ($AllowTagMismatchWithHead) {
    $tagPointsToHeadDetail = if ($tagExists -and $tagCommit -and $headCommit) {
        "AllowTagMismatchWithHead set. Tag commit '$tagCommit' vs HEAD '$headCommit'."
    } else {
        "AllowTagMismatchWithHead set. Tag/HEAD comparison skipped because commit resolution was incomplete."
    }
    Add-Check "expected tag points to HEAD" $true $tagPointsToHeadDetail | Out-Null
} else {
    Add-Check "expected tag points to HEAD" $tagPointsToHead "Tag commit '$tagCommit' vs HEAD '$headCommit'." | Out-Null
}

$releasePathspecs = @(
    "android-app",
    "docs/release-notes.md",
    "scripts/verify-android-beta.ps1",
    "scripts/verify-android-architecture.ps1"
)
$releaseStatus = Get-GitStatusLines -WorkingDirectory $ProjectRoot -Pathspecs $releasePathspecs
$releaseWorkingTreeClean = [bool]($releaseStatus.ok -and $releaseStatus.lines.Count -eq 0)
$releaseWorkingTreeDetail = if (-not $releaseStatus.ok) {
    "Could not read git status for Android release paths. $($releaseStatus.detail)"
} elseif ($releaseStatus.lines.Count -eq 0) {
    "Android release paths are clean."
} else {
    "Working tree has uncommitted Android release changes: $($releaseStatus.lines -join '; '). Bump version metadata in committed release state before verifying."
}
Add-Check "android release working tree clean" $releaseWorkingTreeClean $releaseWorkingTreeDetail | Out-Null

if ($SkipDoctor) {
    Add-Check "android doctor" $true "Skipped by -SkipDoctor." | Out-Null
} else {
    $shellPath = (Get-Process -Id $PID).Path
    $doctorRun = Invoke-ExternalCommand -FilePath $shellPath -Arguments @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $doctorScript,
        "-ProjectRoot",
        $ProjectRoot,
        "-Json"
    ) -WorkingDirectory $ProjectRoot

    $doctorOk = $false
    $doctorDetail = $doctorRun.output

    if ($doctorRun.exitCode -in @(0, 1) -and $doctorRun.output) {
        try {
            $doctorPayload = $doctorRun.output | ConvertFrom-Json
            $doctorOk = [bool]$doctorPayload.ok
            $failedDoctorChecks = @($doctorPayload.checks | Where-Object { -not $_.ok } | ForEach-Object { $_.name })
            if ($failedDoctorChecks.Count -gt 0) {
                $doctorDetail = "Android doctor failed checks: $($failedDoctorChecks -join ', ')."
            } else {
                $doctorDetail = "Android doctor passed."
            }
        } catch {
            $doctorDetail = "android-doctor output was not valid JSON. $doctorDetail"
        }
    }

    Add-Check "android doctor" $doctorOk $doctorDetail | Out-Null
}

if ($SkipArchitecture) {
    Add-Check "android architecture harness" $true "Skipped by -SkipArchitecture." | Out-Null
} else {
    $shellPath = (Get-Process -Id $PID).Path
    $architectureRun = Invoke-ExternalCommand -FilePath $shellPath -Arguments @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $architectureScript,
        "-ProjectRoot",
        $ProjectRoot
    ) -WorkingDirectory $ProjectRoot

    Add-Check "android architecture harness" ($architectureRun.exitCode -eq 0) $(if ($architectureRun.output) { $architectureRun.output } else { "verify-android-architecture exited with code $($architectureRun.exitCode)." }) | Out-Null
}

if ($SkipUnitTests) {
    Add-Check "testDebugUnitTest" $true "Skipped by -SkipUnitTests." | Out-Null
} else {
    $unitTestRun = Invoke-ExternalCommand -FilePath $gradleWrapper -Arguments @("testDebugUnitTest") -WorkingDirectory $androidRoot
    Add-Check "testDebugUnitTest" ($unitTestRun.exitCode -eq 0) $(if ($unitTestRun.output) { $unitTestRun.output } else { "testDebugUnitTest exited with code $($unitTestRun.exitCode)." }) | Out-Null
}

if ($SkipAssemble) {
    Add-Check "assembleDebug" $true "Skipped by -SkipAssemble." | Out-Null
} else {
    $assembleRun = Invoke-ExternalCommand -FilePath $gradleWrapper -Arguments @("assembleDebug") -WorkingDirectory $androidRoot
    Add-Check "assembleDebug" ($assembleRun.exitCode -eq 0) $(if ($assembleRun.output) { $assembleRun.output } else { "assembleDebug exited with code $($assembleRun.exitCode)." }) | Out-Null
}

$allRequiredOk = -not ($checks | Where-Object { $_.severity -eq "required" -and -not $_.ok })

$payload = [pscustomobject]@{
    ok = $allRequiredOk
    projectRoot = $ProjectRoot
    androidVersion = [pscustomobject]@{
        versionCode = $androidVersion.versionCode
        versionName = $androidVersion.versionName
        expectedTag = $expectedTag
    }
    releaseNotes = [pscustomobject]@{
        heading = $latestAndroidReleaseNotes.heading
        date = $latestAndroidReleaseNotes.date
        gitTag = $latestAndroidReleaseNotes.gitTag
        versionCode = $latestAndroidReleaseNotes.versionCode
        versionName = $latestAndroidReleaseNotes.versionName
        status = $latestAndroidReleaseNotes.status
        highlightsCount = $latestAndroidReleaseNotes.highlightsCount
    }
    checks = $checks
}

if ($Json) {
    $payload | ConvertTo-Json -Depth 6
} else {
    Write-Host "Android beta verification"
    Write-Host "  Project root: $ProjectRoot"
    Write-Host "  Gradle:       versionCode=$($androidVersion.versionCode) versionName=$($androidVersion.versionName)"
    Write-Host "  Notes:        $($latestAndroidReleaseNotes.heading)"
    Write-Host ""

    foreach ($check in $checks) {
        $status = if ($check.ok) { "OK" } else { "FAIL" }
        Write-Host "[$status] $($check.name) - $($check.detail)"
    }

    Write-Host ""
    if ($allRequiredOk) {
        Write-Host "Android beta verification passed."
    } else {
        Write-Host "Android beta verification found required failures."
    }
}

if ($allRequiredOk) {
    exit 0
}

exit 1

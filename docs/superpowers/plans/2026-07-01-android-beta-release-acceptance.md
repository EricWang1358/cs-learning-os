# Android Beta Release Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic Android beta acceptance script that verifies version metadata, release notes, Git tag state, and required Android verification commands for each phone-testable APK build.

**Architecture:** Keep Android release acceptance separate from `android-doctor.ps1`. Add a focused `scripts/verify-android-beta.ps1` that parses Gradle and release-notes metadata, checks the expected Git tag, then runs doctor, unit tests, and APK build by default. Update Android workflow docs so this becomes the standard beta handoff gate.

**Tech Stack:** PowerShell, Git CLI, Gradle wrapper, existing Android doctor script, Markdown docs.

---

## Acceptance Checklist

- [ ] `scripts/verify-android-beta.ps1` reads `versionCode` and `versionName` from `android-app/app/build.gradle`.
- [ ] The script parses the latest Android section in `docs/release-notes.md`.
- [ ] The script derives the expected tag as `android-v<versionName>-beta`.
- [ ] The script fails when Gradle version fields and release-notes fields disagree.
- [ ] The script fails when the expected tag is missing.
- [ ] The script fails when the expected tag does not point to `HEAD`, unless explicitly told to allow that mismatch.
- [ ] The script runs `.\scripts\android-doctor.ps1`, `cd android-app; .\gradlew.bat testDebugUnitTest`, and `cd android-app; .\gradlew.bat assembleDebug` by default.
- [ ] The script supports JSON output.
- [ ] `docs/android-workflow.md` documents the new Android beta acceptance loop.
- [ ] `android-app/README.md` documents the new Android beta acceptance command.
- [ ] Final verification includes running the new acceptance script in both text and JSON modes.

## File Structure

- Create: `scripts/verify-android-beta.ps1`
  - Android beta acceptance gate for version metadata, release-notes metadata, Git tag, and required Android verification commands.
- Modify: `docs/android-workflow.md`
  - Add the Android beta acceptance command to the required loop and verification matrix.
- Modify: `android-app/README.md`
  - Document how to run the Android beta acceptance gate before handoff/distribution.

## Task 1: Write The Acceptance Script First Through Red Checks

**Files:**
- Create: `scripts/verify-android-beta.ps1`

- [ ] **Step 1: Write the script skeleton with explicit required checks**

Create `scripts/verify-android-beta.ps1` with:

- parameters:
  - `ProjectRoot`
  - `Json`
  - `SkipDoctor`
  - `SkipUnitTests`
  - `SkipAssemble`
  - `AllowTagMismatchWithHead`
- a shared `Add-Check` helper
- placeholders for:
  - reading Gradle version metadata
  - reading the latest Android release-notes block
  - Git tag checks
  - Android command execution

Use this structure:

```powershell
param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [switch]$Json,
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
```

- [ ] **Step 2: Run the new script to verify RED**

Run:

```powershell
.\scripts\verify-android-beta.ps1
```

Expected: FAIL because the metadata parsing and command execution logic is not implemented yet.

## Task 2: Implement Metadata And Git Checks

**Files:**
- Modify: `scripts/verify-android-beta.ps1`

- [ ] **Step 1: Parse Gradle Android version fields**

Add logic to read:

```powershell
$buildGradlePath = Join-Path $ProjectRoot "android-app\app\build.gradle"
$buildGradleText = Get-Content -Raw $buildGradlePath

$versionCodeMatch = [regex]::Match($buildGradleText, '(?m)^\s*versionCode\s+(\d+)\s*$')
$versionNameMatch = [regex]::Match($buildGradleText, '(?m)^\s*versionName\s+"([^"]+)"\s*$')
```

Store:

- integer `versionCode`
- string `versionName`
- derived `expectedTag = "android-v$versionName-beta"`

- [ ] **Step 2: Parse the latest Android release-notes section**

Parse the first section matching:

- `## Android Beta ...`
- `Git tag:`
- `versionCode`
- `versionName`

The parser may assume the current `docs/release-notes.md` format, but it must fail clearly if any field is missing.

- [ ] **Step 3: Add metadata consistency checks**

Add required checks for:

- Gradle file exists
- release-notes file exists
- release-notes latest Android section exists
- release-notes `versionCode` equals Gradle `versionCode`
- release-notes `versionName` equals Gradle `versionName`
- release-notes tag equals derived expected tag

- [ ] **Step 4: Add Git tag checks**

Use `git rev-parse` to verify:

- expected tag exists
- `HEAD` resolves
- expected tag commit equals `HEAD`, unless `-AllowTagMismatchWithHead` is set

Use command shape like:

```powershell
$headSha = (& git -C $ProjectRoot rev-parse HEAD).Trim()
$tagSha = (& git -C $ProjectRoot rev-parse $expectedTag 2>$null).Trim()
```

- [ ] **Step 5: Run metadata-only verification to verify GREEN so far**

Run:

```powershell
.\scripts\verify-android-beta.ps1 -SkipDoctor -SkipUnitTests -SkipAssemble
```

Expected: PASS on metadata and Git checks for the current `0.1.8` state.

## Task 3: Implement Android Verification Commands And JSON Output

**Files:**
- Modify: `scripts/verify-android-beta.ps1`

- [ ] **Step 1: Add a reusable command runner**

Add a helper that executes a command, captures exit code, and records a check result.

The helper should support:

- repo-root command:
  - `powershell.exe -ExecutionPolicy Bypass -File scripts/android-doctor.ps1`
- Android root commands:
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat assembleDebug`

- [ ] **Step 2: Wire default verification behavior**

Implement default behavior:

- run doctor unless `-SkipDoctor`
- run unit tests unless `-SkipUnitTests`
- run assemble unless `-SkipAssemble`

Failures must mark the overall script result as failed.

- [ ] **Step 3: Add text and JSON output**

Emit:

- text mode:
  - one line per check
  - summary success/failure line
- JSON mode:

```json
{
  "ok": true,
  "projectRoot": "...",
  "androidVersion": {
    "versionCode": 9,
    "versionName": "0.1.8",
    "expectedTag": "android-v0.1.8-beta"
  },
  "releaseNotes": {
    "heading": "Android Beta 0.1.8",
    "tag": "android-v0.1.8-beta",
    "versionCode": 9,
    "versionName": "0.1.8"
  },
  "checks": [...]
}
```

- [ ] **Step 4: Run the full acceptance gate**

Run:

```powershell
.\scripts\verify-android-beta.ps1
```

Expected: PASS with doctor, unit tests, and assemble all succeeding.

- [ ] **Step 5: Run JSON mode**

Run:

```powershell
.\scripts\verify-android-beta.ps1 -Json
```

Expected: valid JSON with `ok`, `androidVersion`, `releaseNotes`, and `checks`.

## Task 4: Update Android Workflow Docs

**Files:**
- Modify: `docs/android-workflow.md`
- Modify: `android-app/README.md`

- [ ] **Step 1: Add Android beta acceptance to the required loop**

Update `docs/android-workflow.md` so Android release acceptance for phone-testable builds includes:

1. bump Gradle Android version
2. update `docs/release-notes.md`
3. create/move the matching Git tag
4. run `.\scripts\verify-android-beta.ps1`

- [ ] **Step 2: Add the new command to the verification matrix**

Add a row like:

```markdown
| Android beta release acceptance | `.\scripts\verify-android-beta.ps1` |
```

- [ ] **Step 3: Update Android README release instructions**

Document that after bumping version metadata and release notes, workers should run:

```powershell
.\scripts\verify-android-beta.ps1
```

before sharing or handing off the APK.

- [ ] **Step 4: Verify docs stay consistent with existing version-discipline language**

Re-read the updated docs and keep the rule aligned with:

- every phone-testable Android implementation commit bumps version metadata
- docs-only commits do not require APK version bumps unless a new APK is distributed

## Task 5: Strict Final Verification

**Files:**
- Modify: `scripts/verify-android-beta.ps1`
- Modify: `docs/android-workflow.md`
- Modify: `android-app/README.md`

- [ ] **Step 1: Run metadata-only acceptance**

```powershell
.\scripts\verify-android-beta.ps1 -SkipDoctor -SkipUnitTests -SkipAssemble
```

Expected: PASS.

- [ ] **Step 2: Run full acceptance**

```powershell
.\scripts\verify-android-beta.ps1
```

Expected: PASS.

- [ ] **Step 3: Run JSON acceptance and parse it**

```powershell
.\scripts\verify-android-beta.ps1 -Json | ConvertFrom-Json
```

Expected: object parses successfully and `.ok` is true.

- [ ] **Step 4: Final checklist**

Report every acceptance checklist item as pass/fail/untested with no hand-waving.

- [ ] **Step 5: Commit**

```bash
git add scripts/verify-android-beta.ps1 docs/android-workflow.md android-app/README.md docs/superpowers/specs/2026-07-01-android-beta-release-acceptance-design.md docs/superpowers/plans/2026-07-01-android-beta-release-acceptance.md
git commit -m "chore: add android beta release acceptance gate"
```

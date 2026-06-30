# Android Migration Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the A0 Android migration shell workflow so the project has a deterministic doctor check, clear documentation, and a safe next path toward mobile readability.

**Architecture:** Keep Android as a WebView shell over the existing React/FastAPI app for A0/A1, then extract domain/storage contracts in A2. The first implementation slice stays in release tooling and docs, avoiding React state churn and private data packaging.

**Tech Stack:** PowerShell, Gradle/Android project metadata, Markdown docs.

---

## File Structure

- Modify `scripts/android-doctor.ps1`: add structured check results, optional JSON output, Gradle wrapper/project checks, and a private-data packaging guard.
- Modify `docs/android-workflow.md`: document the machine-readable doctor mode and first A0 verification loop.
- Modify `android-app/README.md`: add first-run and verification commands.
- Modify `README.md`: ensure the Superpowers spec/plan navigation is discoverable without becoming the main user path.

## Task 1: Make Android Doctor Deterministic

**Files:**

- Modify: `scripts/android-doctor.ps1`
- Test: `scripts/android-doctor.ps1`

- [ ] **Step 1: Replace ad hoc output with collected check objects**

Use this structure in `scripts/android-doctor.ps1`:

```powershell
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
```

- [ ] **Step 2: Add project structure and private-data guards**

Check these paths:

```powershell
$androidRoot = Join-Path $ProjectRoot "android-app"
$assetRoot = Join-Path $androidRoot "app\src\main\assets"

Add-Check "android-app directory" (Test-Path $androidRoot) $androidRoot | Out-Null
Add-Check "Android manifest" (Test-Path (Join-Path $androidRoot "app\src\main\AndroidManifest.xml")) "app/src/main/AndroidManifest.xml" | Out-Null
Add-Check "MainActivity" (Test-Path (Join-Path $androidRoot "app\src\main\java\com\cslearningos\mobile\MainActivity.java")) "WebView shell entry" | Out-Null
Add-Check "Fallback asset" (Test-Path (Join-Path $assetRoot "www\index.html")) "app/src/main/assets/www/index.html" | Out-Null
Add-Check "No private content in Android assets" (-not (Test-Path (Join-Path $assetRoot "data")) -and -not (Test-Path (Join-Path $assetRoot "knowledge.db"))) "assets must not contain data/content or knowledge.db" | Out-Null
```

- [ ] **Step 3: Add Java, Gradle, and Android SDK checks**

Keep missing toolchain as a clear failure, not a crash:

```powershell
try {
    $javaOutput = (& java -version 2>&1 | Out-String).Trim()
    $javaFirstLine = ($javaOutput -split "`r?`n" | Select-Object -First 1)
    $javaOk = $javaOutput -match 'version "((17|18|19|20|21|22|23|24|25|26)\.|[2-9][0-9]\.)'
    Add-Check "JDK 17+" $javaOk $javaFirstLine | Out-Null
} catch {
    Add-Check "JDK 17+" $false "java not found" | Out-Null
}

$gradleCmd = Get-Command gradle -ErrorAction SilentlyContinue
$gradleWrapper = Join-Path $androidRoot "gradlew.bat"
$gradleOk = $null -ne $gradleCmd -or (Test-Path $gradleWrapper)
$gradleDetail = if ($gradleCmd) { $gradleCmd.Source } elseif (Test-Path $gradleWrapper) { $gradleWrapper } else { "gradle or gradlew.bat not found" }
Add-Check "Gradle" $gradleOk $gradleDetail | Out-Null

$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) {
    $sdkRoot = $env:ANDROID_SDK_ROOT
}
$sdkOk = [bool]($sdkRoot -and (Test-Path $sdkRoot))
Add-Check "Android SDK" $sdkOk ($(if ($sdkRoot) { $sdkRoot } else { "ANDROID_HOME/ANDROID_SDK_ROOT not set" })) | Out-Null
```

- [ ] **Step 4: Emit text or JSON summaries**

Use this final output:

```powershell
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
```

- [ ] **Step 5: Run text doctor**

Run:

```powershell
.\scripts\android-doctor.ps1
```

Expected: command exits `0` if JDK, Gradle, and SDK are present; otherwise exits `1` with exact missing prerequisites. Structure checks should be `OK`.

- [ ] **Step 6: Run JSON doctor**

Run:

```powershell
.\scripts\android-doctor.ps1 -Json
```

Expected: valid JSON with `ok`, `projectRoot`, `androidRoot`, and `checks`.

## Task 2: Document The A0 Verification Contract

**Files:**

- Modify: `docs/android-workflow.md`
- Modify: `android-app/README.md`

- [ ] **Step 1: Update `docs/android-workflow.md` verification matrix**

Add a row for JSON doctor:

```markdown
| Machine-readable Android doctor | `.\scripts\android-doctor.ps1 -Json` |
```

- [ ] **Step 2: Add an A0 local runbook to `docs/android-workflow.md`**

Add:

```markdown
## A0 Local Runbook

1. From the repo root, run `.\scripts\android-doctor.ps1`.
2. If toolchain prerequisites are missing, install JDK 17+, Android Studio SDK, and Gradle or a Gradle wrapper before claiming Android build success.
3. Start the existing web app with `.\scripts\dev.ps1 -Detached -NoBrowser`.
4. Open `android-app/` in Android Studio and run the `app` configuration.
5. Confirm the emulator loads `http://10.0.2.2:5173`.
6. Stop the web server and confirm the fallback asset page appears on reload.
```

- [ ] **Step 3: Update `android-app/README.md` doctor instructions**

Add:

```markdown
For worker handoffs or release scripts, use JSON mode:

```powershell
.\scripts\android-doctor.ps1 -Json
```
```

## Task 3: Link Superpowers Design Artifacts

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Add Superpowers spec/plan docs to explanatory navigation**

Add these rows near the Android docs:

```markdown
| [docs/superpowers/specs/2026-07-01-android-migration-workflow-design.md](docs/superpowers/specs/2026-07-01-android-migration-workflow-design.md) | Superpowers design spec for the Android migration workflow and first safe implementation slice. | Current Android migration spec. |
| [docs/superpowers/plans/2026-07-01-android-migration-workflow.md](docs/superpowers/plans/2026-07-01-android-migration-workflow.md) | Step-by-step implementation plan for hardening Android A0 verification. | Current Android migration plan. |
```

- [ ] **Step 2: Re-read the table for stale or duplicate wording**

Expected: the new rows point to workflow artifacts without replacing `docs/android-migration.md` or `docs/android-workflow.md` as the main Android guides.

## Task 4: Verify And Commit

**Files:**

- Verify only.

- [ ] **Step 1: Run Android doctor text mode**

Run:

```powershell
.\scripts\android-doctor.ps1
```

Expected: structure checks pass; missing toolchain is explicitly reported if the machine lacks Gradle or Android SDK.

- [ ] **Step 2: Run Android doctor JSON mode**

Run:

```powershell
.\scripts\android-doctor.ps1 -Json
```

Expected: valid JSON parseable by PowerShell:

```powershell
.\scripts\android-doctor.ps1 -Json | ConvertFrom-Json
```

- [ ] **Step 3: Check git diff**

Run:

```powershell
git diff -- README.md android-app/README.md docs/android-workflow.md scripts/android-doctor.ps1 docs/superpowers/specs/2026-07-01-android-migration-workflow-design.md docs/superpowers/plans/2026-07-01-android-migration-workflow.md
```

Expected: only Android workflow/docs/doctor/Superpowers artifacts changed.

- [ ] **Step 4: Commit if verification is acceptable**

Run:

```powershell
git add README.md android-app/README.md docs/android-workflow.md scripts/android-doctor.ps1 docs/superpowers/specs/2026-07-01-android-migration-workflow-design.md docs/superpowers/plans/2026-07-01-android-migration-workflow.md
git commit -m "chore: harden android migration workflow"
```

Expected: one focused commit. Do not push unless separately requested.

## Self-Review

- Spec coverage: the plan implements the first safe implementation slice described in the design spec.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation steps remain.
- Type consistency: PowerShell parameters, field names, and file paths are consistent across tasks.
- Scope check: changes are limited to Android workflow tooling and docs; no React or backend behavior changes are included.


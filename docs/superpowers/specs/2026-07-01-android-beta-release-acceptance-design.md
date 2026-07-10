# Android Beta Release Acceptance Design

## Purpose

The Android app now ships as a native offline beta with repeated testable APK drops. The project already documents beta version discipline, but the release contract is still spread across Gradle metadata, release notes, Git tags, and manual verification commands. We already hit a real drift case where the code, tag, and release ledger temporarily disagreed.

This design defines a small, deterministic Android beta release acceptance layer so every phone-testable Android build can be checked the same way before handoff or distribution.

## Problem

Today the Android beta release state lives in multiple places:

- `android-app/app/build.gradle`
- `docs/release-notes.md`
- Git tags such as `android-v0.1.8-beta`
- manual verification runs:
  - `.\scripts\android-doctor.ps1`
  - `cd android-app; .\gradlew.bat testDebugUnitTest`
  - `cd android-app; .\gradlew.bat assembleDebug`

Those pieces are documented, but not enforced together by one Android-specific acceptance script. That leaves three avoidable failure modes:

1. version metadata is bumped in Gradle but not reflected in release notes
2. release notes mention a tag that does not exist or does not point at the intended commit
3. metadata looks correct but required Android verification was not rerun on the final state

## Product Position

This is not a general release framework and not a new packaging system. It is a narrow Android beta gate for the current milestone path:

```text
native offline beta
  -> deterministic beta acceptance
  -> later signed release/productization
```

The goal is to make Android beta handoff and release discipline machine-checkable without changing the app's learning behavior.

## Recommended Approach

Add a new script:

```text
scripts/verify-android-beta.ps1
```

This script should be the single Android beta acceptance entry point. It will:

1. read `versionCode` and `versionName` from `android-app/app/build.gradle`
2. parse the latest Android section in `docs/release-notes.md`
3. derive the expected tag name as `android-v<versionName>-beta`
4. verify the release-notes section matches the Gradle version fields
5. verify the expected tag exists
6. optionally verify the expected tag points to `HEAD`
7. run Android verification commands unless the caller explicitly skips them
8. emit human-readable text output and machine-readable JSON output

This should mirror the role that `scripts/verify-beta.ps1` plays for the desktop beta, but remain Android-specific because the acceptance contract is different.

## Alternatives Considered

### Extend `scripts/android-doctor.ps1`

This would fold release metadata checks into the existing Android structure checker.

Trade-off: it keeps one fewer script, but it mixes environment/toolchain checks with release bookkeeping. `android-doctor.ps1` is currently about prerequisites, structure, and safety boundaries; version/tag/release-note validation is a separate concern.

Decision: reject for now.

### Reuse `scripts/verify-beta.ps1`

This would expand the desktop beta verifier into a shared cross-surface release tool.

Trade-off: it sounds DRY, but the desktop beta and Android beta have different artifacts, dependencies, and release contracts. Forcing them into one script now would add branching and make both harder to reason about.

Decision: reject for now.

### New Android-Specific Acceptance Script

This keeps Android beta acceptance explicit and small, while still reusing the existing Android doctor and Gradle commands.

Trade-off: one more script to maintain, but the ownership boundary is clean and the behavior is easy to explain.

Decision: use this path.

## Scope

### In Scope

- new `scripts/verify-android-beta.ps1`
- parsing Gradle Android version metadata
- parsing the latest Android release-notes entry
- checking expected tag naming and presence
- checking whether the tag points to `HEAD`
- running:
  - `.\scripts\android-doctor.ps1`
  - `cd android-app; .\gradlew.bat testDebugUnitTest`
  - `cd android-app; .\gradlew.bat assembleDebug`
- text and JSON output
- workflow docs updates telling Android workers to use this script for beta acceptance

### Out Of Scope

- signing configs
- Play Store or store packaging
- changelog generation from commits
- automatic tag creation
- automatic version bumping
- release-note authoring assistance
- desktop beta verification refactor

## Acceptance Contract

The script should treat these as required checks:

### Metadata checks

- `android-app/app/build.gradle` contains:
  - `versionCode <integer>`
  - `versionName "<semver-like beta string>"`
- `docs/release-notes.md` latest Android section contains:
  - header like `## Android Beta 0.1.8`
  - `Git tag: \`android-v0.1.8-beta\``
  - `versionCode`
  - `versionName`
- `versionName` in release notes matches Gradle `versionName`
- `versionCode` in release notes matches Gradle `versionCode`
- release-notes tag matches derived expected tag

### Git checks

- expected tag exists
- expected tag resolves to a commit
- by default, expected tag must point to `HEAD`

The `tag == HEAD` rule is the safe default for shipping a testable Android build. A looser mode can exist later if the project needs historical validation, but the first version should optimize for preventing accidental release drift on the current commit.

### Verification checks

- Android doctor exits successfully
- Android unit tests exit successfully
- Android debug APK build exits successfully

## CLI Shape

Recommended parameters:

- `-ProjectRoot`
- `-Json`
- `-SkipDoctor`
- `-SkipUnitTests`
- `-SkipAssemble`
- `-AllowTagMismatchWithHead`

Default behavior should be strict:

- do not skip verification unless explicitly told
- fail if expected tag does not point to `HEAD`

## Output Model

Follow the structure style already used by `scripts/android-doctor.ps1` and `scripts/verify-beta.ps1`.

Each check should produce a record like:

```text
name
ok
severity
detail
```

Suggested top-level JSON shape:

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

## Documentation Changes

Update:

- `docs/android-workflow.md`
- `android-app/README.md`

Both should describe `.\scripts\verify-android-beta.ps1` as the standard Android beta acceptance command after version bump and release-notes update.

`docs/android-workflow.md` should treat it as the preferred release acceptance loop for phone-testable Android builds. The existing doctor/test/build commands should remain documented individually because they are still useful during development, but the beta acceptance script becomes the handoff gate.

## Acceptance Criteria

- A single command verifies Android beta metadata and build readiness.
- The command fails if `build.gradle`, `docs/release-notes.md`, and the expected tag disagree.
- The command fails if the expected Android beta tag is not on `HEAD`, unless the caller explicitly opts out.
- The command runs doctor, unit tests, and APK build by default.
- The command supports JSON output for future tooling or worker handoff.
- `docs/android-workflow.md` and `android-app/README.md` both point Android beta workers to the new acceptance script.

## Risks

- Parsing `docs/release-notes.md` by naive regex can become brittle if the note format drifts.
- Running all verification commands by default makes the script slower than metadata-only checks, but that is acceptable for a release gate.
- Over-validating Git state could make the script annoying for local experiments, so strict release behavior should be optional only where clearly justified.

## Decision

Proceed with a dedicated `scripts/verify-android-beta.ps1` release-acceptance script. Keep it narrow, strict by default, and aligned with the existing Android doctor and beta verification script conventions. The first version should prevent metadata drift and missing verification, not automate the entire release process.

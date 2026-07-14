# Android Open Source First Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the Android `v0.1.39-beta` GitHub prerelease with a public-repository baseline and reproducible release assets.

**Architecture:** Documentation and governance files establish contributor and security contracts. A local release script builds the APK, creates its SHA-256 checksum, and produces release notes; GitHub Release publishes only those verified artifacts from an annotated version tag.

**Tech Stack:** Git, GitHub CLI, Gradle, PowerShell, Jetpack Compose Android app.

---

### Task 1: Add Public Repository Contracts

**Files:**
- Create: `LICENSE`
- Create: `CONTRIBUTING.md`
- Create: `SECURITY.md`
- Create: `CODE_OF_CONDUCT.md`
- Create: `CHANGELOG.md`
- Modify: `README.md`

- [ ] Write Apache-2.0 license text, contributor setup and verification instructions, private vulnerability reporting policy, Contributor Covenant 2.1, and a Keep-a-Changelog entry for `0.1.39-beta`.
- [ ] Update README Android section with the beta APK channel, optional-AI privacy boundary, source build command, and links to all public contracts.
- [ ] Run `git diff --check` and `rg -n "TODO|TBD" LICENSE CONTRIBUTING.md SECURITY.md CODE_OF_CONDUCT.md CHANGELOG.md README.md`.
- [ ] Commit `docs: prepare repository for Android public beta`.

### Task 2: Add Repeatable Release Preparation

**Files:**
- Create: `scripts/prepare-android-release.ps1`
- Create: `docs/android-release.md`

- [ ] Write a failing PowerShell invocation test using `-WhatIf` that asserts the expected APK, checksum, and notes paths without publishing.
- [ ] Implement a script that verifies the requested tag equals the Gradle `versionName`, builds `:app:assembleDebug`, copies the APK to `generated/releases/`, writes `SHA256SUMS.txt`, and writes release notes from `CHANGELOG.md`.
- [ ] Run `powershell -ExecutionPolicy Bypass -File scripts/prepare-android-release.ps1 -Version 0.1.39-beta -WhatIf` and then the non-WhatIf command.
- [ ] Commit `build: add Android release preparation`.

### Task 3: Verify Public Release Inputs

**Files:**
- Modify: `docs/android-release.md`

- [ ] Scan tracked files and reachable release history with `git grep` and `git log -S` for credentials, key stores, PEM markers, and common provider key prefixes.
- [ ] Run `cd android-app; .\\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --rerun-tasks`.
- [ ] Run `powershell -ExecutionPolicy Bypass -File scripts/verify-android-architecture.ps1`.
- [ ] Install the generated APK with `adb install -r` and launch its package.
- [ ] Record exact commands, artifact filenames, checksum, and verification result in `docs/android-release.md`.

### Task 4: Create The Public Prerelease

**Files:**
- Modify: `CHANGELOG.md`

- [ ] Create annotated tag `android-v0.1.39-beta` at the verified release commit and push `codex/android-polish-pass` plus the tag to `origin`.
- [ ] Create GitHub prerelease `android-v0.1.39-beta` with the generated APK and `SHA256SUMS.txt`, using the fixed release-note contract in the design spec.
- [ ] Query `gh release view android-v0.1.39-beta` and download/check the published checksum.
- [ ] Commit any release-record documentation update with `docs: record Android beta release`.

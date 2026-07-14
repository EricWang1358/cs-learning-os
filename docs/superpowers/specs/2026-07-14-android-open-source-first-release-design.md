# Android Open Source First Release Design

## Goal

Publish CS Learning OS Android `v0.1.39-beta` as the project's first public Android GitHub Release and prepare the repository for outside users and contributors.

## Release Channel

The initial distribution channel is a GitHub Release in `EricWang1358/cs-learning-os`, not Google Play. The release has one installable debug-independent APK asset, a SHA-256 checksum, concise installation steps, known limitations, and a clear statement that AI providers are optional and configured by the user.

The first tag is `android-v0.1.39-beta`, matching the Android module `versionName`. The APK asset uses a stable, platform-specific name: `cs-learning-os-android-v0.1.39-beta.apk`.

## Public Repository Baseline

The root repository adds:

- `LICENSE` with Apache License 2.0.
- `CONTRIBUTING.md` with local setup, focused verification commands, and contribution boundaries.
- `SECURITY.md` with private vulnerability reporting guidance and explicit instructions never to report or commit API keys.
- `CODE_OF_CONDUCT.md` based on the Contributor Covenant 2.1.
- `CHANGELOG.md` using Keep a Changelog sections, seeded with the first Android beta.
- A root release guide that documents the repeatable build, checksum, GitHub Release, and post-release verification sequence.

The README becomes the public entry point: it explains the Android beta, its local-first model, optional external AI providers, supported installation method, source build command, and links to all governance files. It does not claim Google Play availability, cloud sync, automatic model writes, or production-grade end-to-end encryption.

## Sensitive Data Rules

Before publication, scan tracked files and Git history reachable from the release tag for provider credentials, PEM/private-key material, signed keystores, `.env` files, and common API-key patterns. Any detected secret blocks publication until rotated and removed from reachable history. User-entered API keys remain device-local and are not included in APK assets or source defaults.

The release workflow builds with the public debug signing configuration. It must not publish local keystores, Android Studio configuration, generated caches, emulator data, or `.playwright-cli` / `.superpowers` local state.

## Release Notes Contract

The first release notes contain these fixed sections:

1. Highlights: local-first Nodes, Capture, Review, Markdown reader/editor, optional assistant, GFM table support.
2. Installation: download APK, permit the installer when prompted, open app, configure AI only if desired.
3. Privacy and AI: local data remains on device; requests go only to the provider the user configures; do not enter secrets into notes/chat.
4. Known limitations: no account sync, no Play Store delivery, beta-quality migration compatibility, assistant actions require confirmation.
5. Verification: APK SHA-256 and source tag/commit.
6. Feedback: link to GitHub Issues and security policy.

## Build And Verification

Release acceptance requires:

- Clean tracked-file secret scan.
- `android-app\\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --rerun-tasks` succeeds.
- Architecture verification script succeeds if present.
- APK can install and launch on an Android emulator.
- SHA-256 matches the uploaded APK.
- GitHub Release is created as a prerelease and links the exact tag and release assets.

## Non-Goals

This release does not create a Play Console listing, user accounts, server synchronization, telemetry, crash reporting service, or automatic updater. A signed production release, SBOM, reproducible release build, and CI release automation are follow-up milestones once the public beta feedback loop exists.

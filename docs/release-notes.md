# Release Notes

## Android Beta 0.1.1

Date: 2026-07-01

Git tag: `android-v0.1.1-beta`

Android version:

- `versionCode`: 2
- `versionName`: `0.1.1`

Status: prerelease beta metadata update.

Highlights:

- Added the Android beta version discipline: every phone-testable Android implementation commit must bump `versionCode` and `versionName`.
- Added starter-content and Capture-to-Node design rules so Android demo content stays compatible with desktop `content-demo` Markdown.
- Documented that APK/AAB build outputs stay ignored and version identity belongs in Gradle plus release notes.

Notes:

- This release metadata update does not commit generated APK files.
- Existing local Android implementation files are still uncommitted and will be handled separately.
- The next Android implementation APK should continue from `0.1.1` and bump again when distributed for testing.

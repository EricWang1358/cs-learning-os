# Release Notes

## Android Beta 0.1.4

Date: 2026-07-01

Git tag: `android-v0.1.4-beta`

Android version:

- `versionCode`: 5
- `versionName`: `0.1.4`

Status: implementation beta.

Highlights:

- Made AI service settings visibly auto-save and added an explicit Save settings confirmation.
- Wired Validate and Pull models to real OpenAI-compatible `/models` requests with inline loading, success, and failure states.
- Added pulled model chips so a returned model can be selected without retyping.
- Completed the Capture Slip AI chain: save a slip, tap `AI draft node`, call `/chat/completions`, and open an editable Markdown node draft before saving.

## Android Beta 0.1.3

Date: 2026-07-01

Git tag: `android-v0.1.3-beta`

Android version:

- `versionCode`: 4
- `versionName`: `0.1.3`

Status: implementation beta.

Highlights:

- Collapsed the Android `More` area into an expandable settings list with one active section at a time.
- Added local system language preference with `Follow system`, `English`, and `Chinese` options.
- Added app display mode preference with `Follow system`, `Day`, and `Night`; the app palette and system bars update together.
- Replaced the AI thinking default switch with custom workbench buttons to keep the native UI visually consistent.

## Android Beta 0.1.2

Date: 2026-07-01

Git tag: `android-v0.1.2-beta`

Android version:

- `versionCode`: 3
- `versionName`: `0.1.2`

Status: implementation beta.

Highlights:

- Added Android starter content import from the shared desktop-compatible `content-demo` Markdown assets.
- Added Markdown front matter parsing and standalone quiz import for first-launch demo nodes and due review cards.
- Upgraded Capture Slip promotion into a structured editable node draft and marks slips converted after saving.
- Kept APK/AAB outputs ignored; `.gitignore` already excludes local Android build artifacts and launcher scripts.

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

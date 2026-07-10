# Release Notes

## Android Beta 0.1.12

Date: 2026-07-10

Git tag: `android-v0.1.12-beta`

Android version:

- `versionCode`: 13
- `versionName`: `0.1.12`

Status: implementation beta.

Highlights:

- Added the Home-logo Knowledge Assistant: a dedicated phone chat workspace with local-source citations, keyboard send, stop/new-chat controls, and an OpenAI-compatible streaming reply adapter.
- Added a constrained mobile MCP-style tool boundary: the assistant can search local knowledge, open a cited node or quiz, prepare an editable note draft, or save a reply to Capture only after a distinct user tap. It cannot delete, move, archive, mutate Areas, or restore backups.
- Extracted assistant coordination from `LearningViewModel` into `feature/assistant`, added SSE framing/cancellation coverage, restored the architecture size harness, and moved existing AI draft networking onto the IO dispatcher.

## Android Beta 0.1.11

Date: 2026-07-02

Git tag: `android-v0.1.11-beta`

Android version:

- `versionCode`: 12
- `versionName`: `0.1.11`

Status: implementation beta.

Highlights:

- Added a shared Android motion policy and Compose animation layer for card emphasis, collapsible sections, status banners, bottom navigation, review answer reveal, reader question capture, and Capture AI preflight.
- Tightened phone top chrome on non-home screens so the route title, node count, and due count stay in one compact row with less visual weight.
- Improved narrow-screen density in Backup and Library by making file actions and folder/node toolbars wrap more naturally.

## Android Beta 0.1.10

Date: 2026-07-02

Git tag: `android-v0.1.10-beta`

Android version:

- `versionCode`: 11
- `versionName`: `0.1.10`

Status: implementation beta.

Highlights:

- Split Android app wiring into `appshell`, `feature/settings`, `feature/backup`, `feature/library`, `feature/capture`, and `feature/review` boundaries with an architecture harness that now also guards legacy file size growth.
- Reduced `LearningViewModel` into a smaller compatibility shell by moving UI models out, extracting OpenAI-compatible settings/network behavior, and clearing stale restore state after backup import.
- Turned `LearningRepository` into a compatibility facade over feature repositories and added focused navigation/settings/repository regression coverage for the modularization pass.

## Android Beta 0.1.9

Date: 2026-07-02

Git tag: `android-v0.1.9-beta`

Android version:

- `versionCode`: 10
- `versionName`: `0.1.9`

Status: implementation beta.

Highlights:

- Compacted non-home Android screens so Capture, Library, Backup, and More reach primary actions faster on phones.
- Split screen-specific Android UI out of the overloaded app shell and added a shared compact chrome policy for follow-on cleanup.
- Finished tappable Markdown links plus clearer backup import/export feedback for the file-based Android backup flow.

## Android Beta 0.1.8

Date: 2026-07-01

Git tag: `android-v0.1.8-beta`

Android version:

- `versionCode`: 9
- `versionName`: `0.1.8`

Status: implementation beta.

Highlights:

- Added review-state cleanup when a node is permanently deleted or starter demo content is removed, preventing orphaned review state/attempt data in backups.
- Kept mobile bottom navigation context selected for Reader, Editor, Search, Quiz Editor, Backup, and More subflows.
- Restored AI/task notification visibility in landscape workbench layouts.

## Android Beta 0.1.7

Date: 2026-07-01

Git tag: `android-v0.1.7-beta`

Android version:

- `versionCode`: 8
- `versionName`: `0.1.7`

Status: implementation beta.

Highlights:

- Added visible Capture Slip AI workflow states: queued, drafting, draft ready, failure, and retry-ready.
- Added an in-app notification tray plus a More -> Notifications inbox for Capture/AI task feedback.
- Expanded AI preflight to show the exact slip text, type, topic/source, provider, base URL, and model before Generate.
- Added a phone-friendly Library area map with collapse/expand behavior before node cards.
- Expanded demo content with Virtual Memory, Capture Slip Workflow, and a second committed review card; starter seed version now upgrades to add new demo content without overwriting existing starter edits.

## Android Beta 0.1.6

Date: 2026-07-01

Git tag: `android-v0.1.6-beta`

Android version:

- `versionCode`: 7
- `versionName`: `0.1.6`

Status: implementation beta.

Highlights:

- Hardened the Android UX logic chain against the desktop navigation contract: Library now starts with `area -> track -> order` structure instead of a flat feed.
- Tightened secondary headers and shared buttons so phone labels stay one-line with ellipsis instead of awkward wrapping.
- Shortened Home, Capture, More, and Data tool labels while moving explanations into nearby status/help text.
- Clarified Capture AI flow, Reader question persistence, Backup/export roles, and Review `Again` behavior.

## Android Beta 0.1.5

Date: 2026-07-01

Git tag: `android-v0.1.5-beta`

Android version:

- `versionCode`: 6
- `versionName`: `0.1.5`

Status: implementation beta.

Highlights:

- Reworked the Android bottom navigation from letter placeholders into icon-based mobile tabs with stable accessibility descriptions and a Review due badge.
- Added a Capture Slip AI preflight panel: users can review what will be sent, validate the configured provider, then generate an editable Markdown node draft.
- Added real readable Markdown/TXT export for active nodes, open reader questions, capture slips, and quiz cards.
- Tightened Trash behavior so linked quiz cards leave review/search while the node is trashed, then return when restored.
- Updated Data tools to expose Markdown/TXT export and kept JSON backup/restore as the full recovery path.

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

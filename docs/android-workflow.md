# Android Native Workflow

This workflow is the operating contract for Android work. The current Android milestone is a native, local-first, offline minimum product. Future SaaS or sync modes must be additive adapters, not assumptions baked into the phone app.

## Prime Directive

Keep a small shippable Android product working at all times:

- Native Compose UI owns the phone surface.
- Room/SQLite owns durable local state.
- Domain services stay deterministic and testable.
- Core reading, editing, quiz, review, search, export, and restore work without backend, account, or AI.
- Network permission exists for future user-configured AI/sync adapters, but no content should leave the device unless the user explicitly triggers that action.
- Private desktop content and generated indexes are never packaged into the APK by accident.

## Required Loop

Every Android task must follow this loop:

1. State the milestone.
2. State the write scope.
3. State the domain invariant being protected.
4. Implement the smallest vertical slice.
5. Run `scripts/android-doctor.ps1`.
6. Run `cd android-app; .\gradlew.bat testDebugUnitTest`.
7. Run `cd android-app; .\gradlew.bat assembleDebug`.
8. Record what could not be verified.

If a step cannot run locally, do not fake success. Write down the missing toolchain and continue with static checks only.

## Milestones

| Milestone | Goal | Exit Criteria |
| --- | --- | --- |
| A0 Native Offline MVP | Compose app supports local Markdown nodes, quiz/review, search, and explicit backup. | Doctor, unit tests, and debug APK build pass. |
| A1 Mobile UX Hardening | Reading, editing, review, search, and backup flows are comfortable on a phone. | Manual phone/emulator smoke covers create, edit, read, quiz, review, search, export, restore. |
| A2 Sync Boundary | Local entities expose stable ids, revisions, tombstones, and conflict hooks. | Sync can be added as a repository adapter without replacing Room/domain models. |
| A3 Desktop Sync | Phone can exchange user-approved data with the desktop app. | Local transport/import-export story exists before hosted sync. |
| A4 Productization | Signed build, release checklist, privacy policy, backup UX, and diagnostics exist. | Release candidate can be installed and tested by a non-developer. |

## Architecture Rules

- The Android product must not require the Python backend.
- The Android product must not require the React app.
- The manifest may request `INTERNET` for user-configured AI/sync adapters, but must not enable cleartext traffic or custom network security bypasses without a documented feature need.
- AI is optional. Users may later provide API keys or use a hosted provider, but core study flows must work without AI.
- Domain models must not depend on a hosted account.
- Sync is an adapter, not the source of truth.
- Private `data/content`, `data/knowledge.db`, generated indexes, and local backups must never be copied into Android assets.
- Any destructive write path needs an explicit backup, snapshot, or repair story before product release.

## Mobile Product Rules

- Home, Capture, Library, Review, and More are first-class mobile tabs, not web-page sections stacked behind one scroll.
- Home may carry the largest brand treatment; secondary tabs must use compact headers so content appears without excessive thumb scrolling.
- Capture is the phone-native input path: short slips first, then optional promotion into Markdown nodes, quiz cards, or AI drafts.
- AI drafting must be explicit: show what will be sent, allow provider validation, generate only an editable Markdown draft, and require the user to save before it becomes a node.
- Library should preserve desktop-compatible organization through `area`, `track`, `order`, summary, and Markdown body rather than exposing internal revision jargon.
- Review `Again` means same-session retry and should not disappear from the user's current practice loop.
- Trash is a recovery state: active search/review should hide trashed nodes and linked quizzes, while restore should put them back into the study loop.
- Markdown/TXT export is the phone-to-desktop fallback for users who do not want hosted sync; JSON backup remains the full recovery format.
- Secondary mobile pages must use compact headers and short button labels. If a label can wrap awkwardly on a 360dp phone, shorten it first and rely on nearby status/help text for explanation.
- Every AI-facing action needs a visible chain: configure provider, validate if desired, send only the selected slip, open an editable Markdown draft, then require explicit `Save Markdown`.
- Library must show desktop-compatible structure (`area -> track -> order`) before raw node cards so the Android product does not become a flat feed.

## Strict Write Scopes

| Task Type | Allowed Files |
| --- | --- |
| Native Android product | `android-app/**`, `scripts/android-*.ps1`, `docs/android-*.md`, `.gitignore` |
| Mobile Web UI | `app/src/**`, `app/scripts/**`, Android smoke docs |
| Sync contract | `docs/android-migration.md`, `android-app/app/src/main/java/**/data/**`, future shared domain files |
| Backend adapter | `backend/**`, API docs, smoke tests |
| Release tooling | `scripts/android-*.ps1`, `android-app/**`, README |

Do not mix unrelated scopes in one commit unless the coordinator explicitly says it is a milestone commit.

## Verification Matrix

| Check | Command |
| --- | --- |
| Android beta release acceptance | `.\scripts\verify-android-beta.ps1` |
| Toolchain and offline-product doctor | `.\scripts\android-doctor.ps1` |
| Machine-readable Android doctor | `.\scripts\android-doctor.ps1 -Json` |
| Android unit tests | `cd android-app; .\gradlew.bat testDebugUnitTest` |
| Android debug APK | `cd android-app; .\gradlew.bat assembleDebug` |
| Frontend build, only when web app touched | `cd app; npm run build` |
| Backend smoke, only when backend touched | `.\.venv\Scripts\python.exe backend\smoke_test.py` |

The Gradle wrapper is checked in. The command still requires JDK 17+ and Android SDK 35 to be visible to the CLI through `JAVA_HOME`, `ANDROID_HOME`/`ANDROID_SDK_ROOT`, or ignored `android-app/local.properties`.

## Local Runbook

1. From the repo root, run `.\scripts\android-doctor.ps1`.
2. If toolchain prerequisites are missing, install JDK 17+ and Android SDK 35, then expose them through `JAVA_HOME` and `ANDROID_HOME` or ignored `android-app/local.properties`.
3. Run `cd android-app; .\gradlew.bat testDebugUnitTest`.
4. Run `cd android-app; .\gradlew.bat assembleDebug`.
5. Install over the existing app with `.\scripts\android-install-debug.ps1`; it uses `adb install -r` and preserves local data when `applicationId` and signing key stay the same.
6. Manually smoke: create Markdown node, read it, edit it, add quiz, review it, search it, export backup, restore backup.

## Beta Update Rules

Android keeps app-local Room data across APK updates when all of these stay true:

- The package name remains `com.cslearningos.mobile`.
- The new APK is signed by the same key as the installed APK.
- The APK is installed as an update, not after uninstalling.
- Room database versions only move forward and every schema change has a migration.

Debug testing can use `.\scripts\android-install-debug.ps1`. Public beta should use a stable release signing key and increment `versionCode` for each distributed build.

Every Android implementation commit that produces a testable APK must update `android-app/app/build.gradle` before commit:

- Increment `versionCode` by 1.
- Increment `versionName` in the current beta line, for example `0.1.0` -> `0.1.1`.
- Update `docs/release-notes.md` with the matching Android beta entry.
- Ensure the matching Git tag exists and points at the handoff commit, for example `android-v0.1.1-beta`.
- Run `.\scripts\verify-android-beta.ps1` as the release acceptance gate before handoff or distribution.
- Mention the APK version in the handoff/final message.
- Do not bump the APK version for docs-only/spec-only commits unless a new APK is actually distributed.

`.gitignore` must keep generated APK/AAB files, `android-app/local.properties`, build folders, private data, and local scratch material out of Git. Version identity belongs in Gradle, not in committed APK filenames.

## Decision Gates

Before A2, answer:

- What is the stable content id strategy across phone and desktop?
- Are conflicts last-writer-wins, field-level merges, or user-mediated?
- What needs tombstones versus hard deletes?

Before A4, answer:

- Is sync file-based, event-based, or server-authoritative?
- Does hosted SaaS store content, metadata only, or encrypted blobs?
- What is the explicit user-visible backup and restore policy?

## Current Path

```text
A0 native offline MVP
  -> A1 phone UX hardening
  -> A2 sync-ready repository boundary
  -> A3 desktop sync
  -> A4 productization
```

This path preserves speed while keeping the SaaS door open.

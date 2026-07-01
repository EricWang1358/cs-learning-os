# Android Native Workflow

This workflow is the operating contract for Android work. The current Android milestone is a native, local-first, offline minimum product. Future SaaS or sync modes must be additive adapters, not assumptions baked into the phone app.

## Prime Directive

Keep a small shippable Android product working at all times:

- Native Compose UI owns the phone surface.
- Room/SQLite owns durable local state.
- Domain services stay deterministic and testable.
- Core reading, editing, quiz, review, search, export, and restore work without backend, account, network, or AI.
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
- The manifest must not request network permissions until a specific sync or provider feature needs them.
- AI is optional. Users may later provide API keys or use a hosted provider, but core study flows must work without AI.
- Domain models must not depend on a hosted account.
- Sync is an adapter, not the source of truth.
- Private `data/content`, `data/knowledge.db`, generated indexes, and local backups must never be copied into Android assets.
- Any destructive write path needs an explicit backup, snapshot, or repair story before product release.

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
5. Install `android-app/app/build/outputs/apk/debug/app-debug.apk` on an emulator or phone.
6. Manually smoke: create Markdown node, read it, edit it, add quiz, review it, search it, export backup, restore backup.

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

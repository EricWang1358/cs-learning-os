# Android Migration Workflow

This workflow is the operating contract for Android work. It keeps the project local-first, SaaS-compatible, and strict enough that migration does not become another stone pile.

## Prime Directive

Start with the smallest shippable Android surface, then replace internals behind stable contracts.

Do not rewrite the whole app before the Android shell can run. Do not hardcode a SaaS assumption into the domain model. Do not package private content into the APK by accident.

## Required Loop

Every Android task must follow this loop:

1. State the milestone.
2. State the write scope.
3. State the domain invariant being protected.
4. Implement the smallest vertical slice.
5. Run `scripts/android-doctor.ps1`.
6. Run any available Gradle/Android Studio build check.
7. Record what could not be verified.

If a step cannot run locally, do not fake success. Write down the missing toolchain and continue with static checks.

## Milestones

| Milestone | Goal | Exit Criteria |
| --- | --- | --- |
| A0 Shell | Android project imports and shows either WebView app or fallback page. | Android Studio sync succeeds; debug install opens. |
| A1 Mobile Readability | Existing UI is usable on a phone viewport. | Node read, quiz read, focus mode, Back, and fallback screen pass manual smoke. |
| A2 Local Data Contract | Android has explicit interfaces for content, search, questions, and reading activity. | Domain interfaces exist without committing to sync or SaaS. |
| A3 Offline Demo | APK can ship demo content without private data. | Demo content loads without desktop backend. |
| A4 Review Engine | Quiz attempts and review scheduling are durable. | Attempt table and scheduling policy exist. |
| A5 Sync Ready | Optional sync can be added without rewriting local-first flows. | Stable ids, revisions, tombstones, conflict policy, export/import exist. |

## Architecture Rules

- The Android app may start as a WebView shell.
- The Python backend must not be embedded into Android without an explicit ADR.
- AI is optional. Users may later provide API keys or use a hosted provider, but core study flows must work without AI.
- Domain models must not depend on a hosted account.
- Sync is an adapter, not the source of truth.
- Private `data/content` and `data/knowledge.db` must never be copied into Android assets.
- Demo content is allowed only if it is already safe to commit.
- Any destructive write path needs a backup, snapshot, or repair story before product release.

## Strict Write Scopes

| Task Type | Allowed Files |
| --- | --- |
| Android shell | `android-app/**`, `docs/android-*.md`, `.gitignore` |
| Mobile Web UI | `app/src/**`, `app/scripts/**`, Android smoke docs |
| Data contract | `docs/android-migration.md`, future shared domain files |
| Backend adapter | `backend/**`, API docs, smoke tests |
| Release tooling | `scripts/android-*.ps1`, `android-app/**`, README |

Do not mix unrelated scopes in one commit unless the coordinator explicitly says it is a milestone commit.

## Verification Matrix

| Check | Command |
| --- | --- |
| Toolchain doctor | `.\scripts\android-doctor.ps1` |
| Machine-readable Android doctor | `.\scripts\android-doctor.ps1 -Json` |
| Frontend build | `cd app; npm run build` |
| Backend smoke | `.\.venv\Scripts\python.exe backend\smoke_test.py` |
| Android Gradle build | `cd android-app; .\gradlew.bat assembleDebug` |
| Manual emulator route | Start web app, open Android app, confirm `http://10.0.2.2:5173` loads. |

The Gradle wrapper is checked in. The command still requires JDK 17+ and Android SDK 35 to be visible to the CLI through `JAVA_HOME`, `ANDROID_HOME`/`ANDROID_SDK_ROOT`, or ignored `android-app/local.properties`.

## A0 Local Runbook

1. From the repo root, run `.\scripts\android-doctor.ps1`.
2. If toolchain prerequisites are missing, install JDK 17+ and Android SDK 35, then expose them through `JAVA_HOME` and `ANDROID_HOME` or ignored `android-app/local.properties`; Gradle is provided by the checked-in wrapper.
3. Start the existing web app with `.\scripts\dev.ps1 -Detached -NoBrowser`.
4. Open `android-app/` in Android Studio and run the `app` configuration.
5. Confirm the emulator loads `http://10.0.2.2:5173`.
6. Stop the web server and confirm the fallback asset page appears on reload.

## Decision Gates

Before A2, answer:

- Is Android a shell over the existing web app, or a native UI rewrite?
- Is local storage Markdown + SQLite, Room, or a generated read model?
- What content can be packaged as demo content?

Before A5, answer:

- Is sync file-based, event-based, or server-authoritative?
- What is the conflict policy?
- Does hosted SaaS store content, metadata only, or encrypted blobs?

## First Recommended Path

The current path is:

```text
A0 Android shell
  -> A1 mobile readability
  -> A2 explicit domain/storage contracts
  -> A3 offline demo content
```

This path preserves speed while keeping the SaaS door open.

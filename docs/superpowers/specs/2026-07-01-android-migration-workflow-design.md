# Android Migration Workflow Design

## Purpose

CS Learning OS is moving toward a local-first personal learning OS with an Android product track. The Android project should create a runnable mobile surface quickly while preserving the architecture needed for future sync, hosted accounts, and optional AI providers.

This design turns the existing Android shell into a disciplined migration workflow. It does not replace the current React/FastAPI/SQLite app. It wraps it first, exposes mobile constraints, then extracts stable contracts behind the existing product behavior.

## Current Context

The repository already contains:

- `android-app/`: a Gradle Android application shell.
- `android-app/app/src/main/java/com/cslearningos/mobile/MainActivity.java`: a WebView entry point that loads `BuildConfig.CS_LEARNING_WEB_URL`.
- `android-app/app/src/main/assets/www/index.html`: an offline fallback page.
- `docs/android-migration.md`: the local-first and SaaS-compatible architecture stance.
- `docs/android-workflow.md`: milestone gates and write-scope rules.
- `scripts/android-doctor.ps1`: a prerequisite and structure checker.
- `docs/state-machine.md`: the canonical state and product-readiness guardrail.
- `docs/worker-protocol.md`: the collaboration protocol for scoped workers.

The project root is `D:\A\1CMU\26Summer\cs-learning-os`. The parent directory is not the Git repository.

## Recommended Approach

Use a strangler migration:

1. Keep Android as a WebView shell for A0.
2. Use the shell to find mobile-specific UI and state bugs in A1.
3. Extract explicit local domain and storage contracts in A2.
4. Add offline demo content only after the data boundary is explicit.
5. Add sync readiness as an adapter path, not as the core data model.

This is the fastest route that still protects product architecture. A native rewrite now would spend most of its time rediscovering state rules already encoded in the web app.

## Alternatives Considered

### Full Native Rewrite First

This would create Android-native screens for reading, search, quizzes, and review immediately.

Trade-off: it might produce a more native app later, but it duplicates unsettled domain behavior now. It also risks diverging from the web state machine before focus reading, Q Queue, edit recovery, and AI draft review are fully stabilized.

Decision: reject for the first migration slice.

### Package The Current Web App As Static Assets

This would build the React app and load it from Android assets.

Trade-off: it reduces dependency on the desktop dev server, but it does not solve the backend, SQLite, Markdown, AI-job, and write-compensation boundaries. It is useful later for offline demo mode, not as the first architecture milestone.

Decision: defer to A3 Offline Demo.

### WebView Shell With Local Development Backend

This loads the existing Vite app through `http://10.0.2.2:5173` on emulator and falls back to a packaged offline page when unreachable.

Trade-off: it is not final product architecture, but it exposes phone viewport, WebView navigation, cleartext, asset, and fallback behavior quickly.

Decision: use this for A0 and A1.

## Architecture

```text
Android shell
  -> WebView host
  -> existing React app during A0/A1
  -> existing FastAPI/SQLite backend during development
  -> explicit domain/storage contracts during A2
  -> offline demo adapter during A3
  -> optional sync/export/import adapters after A5
```

Core study flows must not require AI or hosted accounts. A future SaaS profile may add identity, sync, hosted APIs, or managed AI provider configuration, but those must remain adapters over local-first domain concepts.

## Component Boundaries

### Android Shell

Responsible for:

- Loading the configured local development URL.
- Showing a useful fallback page if the web app is unavailable.
- Handling Android Back through WebView history first.
- Restricting local cleartext development access.
- Providing future Android-only product gates such as permissions, backup, and release build configuration.

Not responsible for:

- Embedding the Python backend.
- Owning Markdown writes.
- Resolving AI jobs.
- Packaging private content.

### Existing Web App

Responsible for:

- Current reading, focus mode, search, quiz, Q Queue, graph, and health behavior.
- Route and hash semantics.
- Edit and AI review UX during A0/A1.

The Android track must not add more long-lived state into `App.tsx` without a split plan. Mobile changes that touch UI state should prefer smaller route shells, hooks, or components.

### Backend And Data

Responsible for:

- Markdown and SQLite ownership.
- Write compensation and stale-hash protection.
- Reader questions and AI jobs.
- Search and graph APIs.

Android must treat backend/data migration as A2+ contract work, not as an A0 shell concern.

## Data Policy

Private user content must never enter the APK by accident.

Allowed to package:

- `android-app/app/src/main/assets/www/index.html`.
- Future tiny demo content already safe under `content-demo/`.

Not allowed to package:

- `data/content/`.
- `data/knowledge.db`.
- `generated/`.
- `.venv/`.
- local raw captures, private screenshots, or provider auth files.

## State Invariants

The Android shell must preserve the web app's canonical state rules:

- URL route remains canonical for selected node, quiz, queue, graph, health, search query, focus mode, and hash.
- Android Back first navigates WebView history, then exits the activity.
- Focus reading remains a reading mode, not just a viewport style.
- Manual edits and AI drafts remain human-gated.
- Reader questions remain open until a backend-confirmed apply succeeds.
- Health and graph routes must not inherit stale node hashes.

## Security And Productization

Cleartext traffic is acceptable for the development emulator route only. Release-oriented work should move toward debug-only local cleartext or a manifest/resource split that prevents accidental broad cleartext permission in production.

The Android workflow must record what could not be verified. Missing Android Studio, SDK, Gradle wrapper, or emulator access is a known local-toolchain limitation, not a reason to pretend the build passed.

## Milestones

### A0 Android Shell

Goal: Android project imports and opens either the web app or fallback page.

Exit criteria:

- `scripts/android-doctor.ps1` reports structure and toolchain status.
- Android Studio can sync the project, or missing toolchain is explicitly reported.
- WebView loads `http://10.0.2.2:5173` in emulator when the dev server is running.
- Fallback page appears when unreachable.

### A1 Mobile Readability

Goal: the existing web UI is usable on a phone viewport.

Exit criteria:

- Node reading, quiz reading, focus mode, Q Queue, Back, and fallback behavior pass manual smoke.
- Mobile fixes avoid growing `App.tsx` further unless the touched state is intentionally extracted.

### A2 Local Data Contract

Goal: Android has explicit interfaces for content, search, questions, reading activity, and quiz attempts.

Exit criteria:

- Contracts are documented before native/offline storage work.
- Sync remains an adapter, not the source of truth.
- AI remains optional.

## First Implementation Slice

The first safe implementation slice is not a native rewrite. It should harden the A0 migration shell and verification contract:

- Make the Android doctor more useful and deterministic.
- Add a machine-readable verification summary so future workers and release scripts can consume it.
- Document the exact first-run path from repo root to Android emulator.
- Keep changes scoped to `scripts/android-doctor.ps1`, Android docs, and Superpowers workflow docs.

This slice advances the migration without risking the existing web app state machine.

## Risks

- Android toolchain may be absent on the current machine. The workflow must report this clearly.
- WebView behavior can mask browser-specific bugs. A1 must include mobile viewport and emulator checks.
- Broad cleartext configuration is acceptable for early development but must be narrowed before release.
- Static asset packaging could accidentally include private data if future copy tasks are careless.
- A native rewrite too early would duplicate unstable state logic and slow the project down.

## Verification

Required checks for A0 work:

- `.\scripts\android-doctor.ps1`
- `cd app; npm run build` if web UI files are touched.
- Android Studio sync or `cd android-app; .\gradlew.bat assembleDebug` if a wrapper/toolchain is available.

If a check cannot run, the result must say why and whether the changed files were still statically inspected.


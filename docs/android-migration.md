# Android Migration Plan

CS Learning OS should become a local-first personal learning OS that can later support sync or SaaS deployment profiles. The Android project starts as a shell, not a rewrite.

## Non-Negotiables

- Local-first: reading, quiz review, and personal study state must not require a hosted account.
- SaaS-compatible: storage and identity boundaries must not block future sync, web accounts, or hosted providers.
- AI-optional: core study flows must work without AI. Provider keys should be user/device/account configuration.
- Data separation: app code, demo content, private content, generated indexes, and backups stay separable.
- Recoverability: Android writes need the same compensation and repair discipline as desktop writes.

## Target Architecture

```text
Presentation
  Android WebView shell now
  Native/React mobile surfaces later

Application services
  Reading session
  Quiz/review session
  Question queue
  AI draft orchestration
  Health/preflight

Domain model
  Node
  Quiz
  ReaderQuestion
  AIJob
  ReadingActivity
  ReviewAttempt

Storage adapters
  Markdown + SQLite local
  Android packaged demo content
  Future sync transport
  Future hosted API
```

## Migration Strategy

Use a strangler pattern:

1. Wrap the existing UI in Android to expose mobile constraints quickly.
2. Extract stable API and domain contracts from the web app.
3. Move read-only browsing/search flows behind mobile-safe service interfaces.
4. Add offline demo content and local state.
5. Add quiz attempt/review scheduling tables.
6. Add optional sync/export/import.
7. Decide whether any screen deserves native UI after the domain contracts are stable.

## Algorithm And Scale Track

- Search: keep SQLite FTS5/BM25 first; add semantic ranking only after stable content ids and query logs exist.
- Ingest: move from full rebuild to content-hash incremental ingest, O(changed files) instead of O(all files).
- Review scheduling: add FSRS or SM-2 style scheduling after quiz attempts are durable.
- Graph: keep paginated graph API; consider cached layout coordinates for large graphs.
- Sync: use stable ids, revision ids, tombstones, and last-writer conflict policy before remote sync.
- AI: store prompt version, policy version, validator version, and provider metadata for reproducible jobs.

## Productization Gates

- Android project imports in Android Studio.
- Debug build reaches the local web server from emulator.
- Offline fallback screen is available.
- Private content is never packaged by accident.
- Release build disables cleartext dev hosts unless explicitly configured.
- Health screen reports content root, DB path, AI readiness, and sync status.
- Backup/export path exists before any destructive edit flow ships.

## First Grill Decision

The recommended first milestone is:

```text
Android WebView shell + existing local desktop backend
```

This is not the final architecture. It is the cheapest way to expose mobile product constraints without rewriting the domain layer prematurely.

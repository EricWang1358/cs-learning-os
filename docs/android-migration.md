# Android Migration Plan

CS Learning OS should become a local-first personal learning OS that can later support desktop sync, hosted sync, or SaaS deployment profiles. The Android project has moved from shell exploration to a native offline product slice.

## Non-Negotiables

- Local-first: Markdown reading/editing, quiz review, search, and personal study state must not require a hosted account.
- SaaS-compatible: storage and identity boundaries must not block future sync, web accounts, or hosted providers.
- AI-optional: core study flows must work without AI. Provider keys should be user/device/account configuration.
- Data separation: app code, demo content, private content, generated indexes, and backups stay separable.
- Recoverability: Android writes need explicit export/restore or repair paths before destructive flows ship.
- Offline default: no Android network permission until a concrete sync or provider feature needs it.

## Target Architecture

```text
Presentation
  Native Android Compose screens
  Future desktop/mobile surfaces

Application services
  Node editing and reading
  Quiz authoring and Markdown quiz sync
  Review session
  Search
  Backup and restore
  Future AI draft orchestration

Domain model
  Node
  Quiz
  ReviewState
  ReviewAttempt
  BackupEnvelope
  Future ReaderQuestion and AIJob

Storage adapters
  Room/SQLite local
  Room FTS local search
  Explicit JSON backup/restore
  Future desktop sync transport
  Future hosted API
```

## Migration Strategy

Use a strangler pattern, but the phone product owns its local core now:

1. Keep native Android build green with doctor, unit tests, and debug APK build.
2. Harden Markdown node CRUD, reading, search, quiz authoring, review, and backup UX.
3. Stabilize entity ids, revisions, tombstones, and conflict metadata before sync.
4. Add desktop sync as an optional repository adapter.
5. Add hosted sync or account mode as another adapter, not as the domain source of truth.
6. Add AI provider configuration only after offline study flows are reliable.

## Algorithm And Scale Track

- Search: keep Room FTS/BM25 first; add semantic ranking only after stable content ids and query logs exist.
- Ingest/sync: move from full restore to content-hash incremental sync, O(changed nodes) instead of O(all nodes).
- Review scheduling: current deterministic scheduler is a seed; evaluate FSRS or SM-2 after attempts are durable and observable.
- Graph: keep graph as optional derived view; avoid putting graph layout into the core edit/review path.
- Sync: use stable ids, revision ids, tombstones, and explicit conflict policy before remote sync.
- AI: store prompt version, policy version, validator version, and provider metadata for reproducible jobs.

## Productization Gates

- Android doctor passes.
- Unit tests pass.
- Debug APK builds.
- Phone/emulator smoke covers create, edit, read, quiz, review, search, export, and restore.
- Manifest has no network permission unless the feature branch explicitly adds sync/provider networking.
- Automatic system backup of local learning data is disabled unless a user-facing policy exists.
- Private content is never packaged by accident.
- Explicit backup/export path exists before any destructive edit flow ships.
- Release build, signing, privacy policy, and diagnostics are documented.

## First Grill Decision

The recommended current milestone is:

```text
Native offline MVP
```

The next hard question is not “can Android load the desktop app?” anymore. It is “which local data and UX contracts must stay stable when desktop sync or hosted sync arrives?”

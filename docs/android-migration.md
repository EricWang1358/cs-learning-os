# Android Migration Plan

CS Learning OS is a local-first personal learning app that can later support desktop sync, hosted sync, or SaaS deployment profiles. Android now owns a native offline product slice rather than embedding the desktop app.

## Current Documentation

- [Android architecture](../android-app/docs/architecture.md) describes the native Compose, domain, and Room boundaries.
- [Recovery and backup contract](../android-app/docs/data-recovery.md) defines export, restore, and input limits.
- [Android workflow](android-workflow.md) records engineering and release verification gates.
- [Android user guide](../android-app/docs/android-app-usage.md) explains the current product flows, including optional AI guidance.
- [Client Android-parity plan](client-android-parity-plan.md) tracks the planned React client and API work without representing it as shipped functionality.

Cross-device synchronization, accounts, remote transport, conflict resolution, and a computer-client sync path are deferred. The local outbox is preparation for a future adapter, not a working synchronization feature.

## Non-negotiables

- Local-first: Markdown editing, reading, search, review, and study state do not require an account.
- SaaS-compatible: storage and identity boundaries leave room for optional sync and hosted providers.
- AI-optional: core study flows work without AI; provider configuration belongs to the user and device.
- Data separation: app code, demo content, private content, indexes, and backups remain separate.
- Recoverability: export and restore paths exist before destructive data flows ship.
- No blanket network permission: `INTERNET` is used only by the explicit, optional AI provider; all core study and recovery flows remain offline.

## Target architecture

```text
Native Compose UI
  -> feature and application services
  -> domain models and deterministic policies
  -> Room/SQLite local storage
  -> optional sync or provider adapters
```

Current domain work covers nodes, quizzes, review state, attempts, search, Trash, and backup envelopes. Future sync requires stable ids, revision ids, tombstones, and an explicit conflict policy before any remote transport is added.

## Migration sequence

1. Keep Android doctor, unit tests, architecture checks, and debug builds green.
2. Harden local Markdown, search, quiz, review, Trash, and backup behavior.
3. Stabilize revisions, deletion records, and conflict rules.
4. Add desktop sync as an optional repository adapter.
5. Add hosted sync or account mode as another adapter.
6. Keep AI providers optional and outside the core study contract.

## Scale policy

- Search stays on Room FTS/BM25 until stable content ids and measured needs justify semantic ranking.
- Sync should eventually process changed content rather than replacing every item.
- Review scheduling stays deterministic and observable before adopting a more complex scheduler.
- Graph layout remains a derived view, outside editing and review paths.
- AI jobs record enough provider and policy metadata to explain generated results.

## Productization gates

- Android doctor passes.
- Full unit tests pass.
- Architecture verification passes.
- A debug APK assembles.
- Phone smoke covers create, edit, read, search, quiz, review, export, and restore.
- Manifest networking is limited to explicit provider functionality; analytics, automatic sync, and a mandatory backend are prohibited.
- Automatic Android system backup stays disabled unless a user-facing policy explicitly changes it.
- Private content is never packaged.
- Destructive actions explain recovery limits and direct users to export first.
- Signed releases include version, release-note, privacy, and diagnostics checks.

The open migration question is which local data and UX contracts must stay stable when desktop or hosted sync arrives, not whether Android can load the desktop app.

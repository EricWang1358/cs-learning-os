# Android Migration Plan

CS Learning OS is a local-first personal learning app with an optional personal
desktop Study Sync adapter. Android owns a native offline product slice rather
than embedding the desktop app; hosted sync and SaaS deployment profiles remain
future adapters.

## Current Documentation

- [Android architecture](../android-app/docs/architecture.md) describes the native Compose, domain, and Room boundaries.
- [Recovery and backup contract](../android-app/docs/data-recovery.md) defines export, restore, and input limits.
- [Android workflow](android-workflow.md) records engineering and release verification gates.
- [Android user guide](../android-app/docs/android-app-usage.md) explains the current product flows, including optional AI guidance.
- [Client Android-parity plan](client-android-parity-plan.md) tracks the planned React client and API work without representing it as shipped functionality.

Personal desktop Study Sync now exists for a phone-friendly study subset. It is
not a full desktop database mirror: desktop remains authoritative for complex
KnowledgeGraph/frontmatter/index work, while Android sync focuses on light
reading Nodes, Quiz records, Daily Bite cards, Capture/Reader Question records,
and review events. Hosted accounts, public remote transport, and full conflict
resolution across arbitrary clients remain deferred.

> Status update (2026-07-19): the personal desktop sync adapter exists — see
> `docs/superpowers/plans/2026-07-11-personal-desktop-mobile-sync.md`. The
> replication outbox drains through it for scoped study data and revision-gated
> content pushes; hosted/accounts sync remains deferred.

## Non-negotiables

- Local-first: Markdown editing, reading, search, review, and study state do not require an account.
- SaaS-compatible: storage and identity boundaries leave room for optional sync and hosted providers.
- AI-optional: core study flows work without AI; provider configuration belongs to the user and device.
- Data separation: app code, demo content, private content, indexes, and backups remain separate.
- Recoverability: export and restore paths exist before destructive data flows ship.
- No blanket network assumption: `INTERNET` is used only by explicit, optional
  user-triggered adapters such as AI providers or Study Sync; all core study and
  recovery flows remain offline.

## Target architecture

```text
Native Compose UI
  -> feature and application services
  -> domain models and deterministic policies
  -> Room/SQLite local storage
  -> optional sync or provider adapters
```

Current domain work covers nodes, quizzes, Daily Bite cards, review state,
attempts, search, Trash, scoped Study Sync, and backup envelopes. Future hosted
sync still requires identity, broader conflict policy, and deployment security
before any public remote transport is added.

## Migration sequence

1. Keep Android doctor, unit tests, architecture checks, and debug builds green.
2. Harden local Markdown, search, quiz, review, Trash, and backup behavior.
3. Stabilize revisions, deletion records, and conflict rules.
4. Keep personal desktop Study Sync scoped to mobile review data.
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

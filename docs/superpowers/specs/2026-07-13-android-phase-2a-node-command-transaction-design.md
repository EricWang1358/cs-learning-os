# Android Phase 2A Node Command Transaction Design

## Goal

Prove the domain/Room separation and atomic command boundary with one production Node create/edit path. The slice introduces a Room v7 additive migration, idempotent commands, processed-command storage, and a local transactional outbox while preserving current UI behavior, existing user data, derived quiz behavior, and backup schema v1.

## Confirmed Decisions

- Phase 2 is decomposed. Phase 2A migrates only Node create/edit as a real vertical slice; later Phase 2 slices migrate the remaining writes and contexts.
- Room remains the local source of truth.
- Database v6 migrates additively to v7. Existing tables and `learning_nodes.sync_status` remain readable and writable during compatibility.
- New domain models do not expose `syncStatus`.
- Backup schema v1 remains unchanged.
- The same `CommandId` and request fingerprint returns the first committed result without a second revision, projection update, or outbox record.
- Reusing a `CommandId` with a different request fingerprint is a command conflict.
- The Phase 2A outbox stores a versioned local domain change. Network envelopes, peers, causal vectors, and transport states remain Phase 5 work.
- The existing `LibraryRepository` remains a compatibility facade and delegates only `saveNode` to the new command port.

## Scope

### Included

- Node creation and editing through the current editor save action.
- Expected-revision validation, missing/deleted target handling, and Area validation.
- Existing Node FTS projection and deterministic Markdown quiz/review derivation.
- Real Room transaction coverage for canonical rows, projections, processed command, and outbox.
- Mechanical ownership move of Room schema sources into `:core:database` without changing the database class package or database filename.
- Explicit Node domain/Room mapping.
- Architecture enforcement for new modules and pure Kotlin boundaries.

### Excluded

- Trash, restore, permanent delete, move Area, check/uncheck, read traces, starter import, backup restore, Quiz editor writes, Capture, Review, and Assistant persistence migration.
- Removal of legacy `sync_status` columns or backup fields.
- Network synchronization, replica identity, causal versions, inbox, conflicts, acknowledgements, or wire protocol.
- Replacement of all `LibraryRepository`, `LearningRepository`, `LearningDao`, or `LearningViewModel` responsibilities.

## Module Graph

```text
:app
  |-- :application:content
  `-- :data:content-room
        |-- :application:content
        |-- :domain:content
        `-- :core:database

:application:content --> :domain:content --> :core:kernel
:core:database       --> Android Room only
```

### `:domain:content`

Pure Kotlin ownership:

- `Node`, `NodeId`, and content fields.
- Node create/edit transition rules.
- Revision, missing/deleted target, Area, and validation outcomes.
- Markdown quiz derivation policy currently implemented by `MarkdownQuizParser`.
- Domain events used to build the local outbox payload.

It must not import Android, Room, JSON, `com.cslearningos.mobile.data`, UI, replication transport, or `SyncStatus`.

The content Node excludes `lastReadAt`, which belongs to Learning, and excludes `syncStatus`, which belongs to legacy persistence/replication compatibility. The mapper preserves those old columns when updating an existing row.

### `:application:content`

Pure Kotlin ownership:

- `SaveNodeCommand` with `CommandId`, stable Node ID, create/update mode, expected revision, Area ID, editable fields, timestamp, and request fingerprint.
- `ContentCommandPort` and typed command result/failure contracts.
- Compatibility-independent result types.
- Stable canonical request fingerprint policy.

### `:core:database`

Android Room ownership:

- `LearningDatabase`, Room entities, DAO, converters, migrations, and tracked schema JSON.
- New processed-command and outbox entities/DAO operations.

The database remains named `learning-os.db`. `LearningDatabase` retains its current package and Room identity so v1-v6 schema assets and installed databases remain compatible. Moving files changes Gradle ownership, not persisted names.

### `:data:content-room`

Android data-adapter ownership:

- `LearningNodeEntity`/`Node` mapper.
- Room implementation of `ContentCommandPort`.
- Transaction orchestration, processed-command lookup, and result replay.
- Node/FTS/derived quiz writes and outbox insertion.
- Compatibility mapping from typed failures to the current facade behavior.

### `:app`

The existing UI, ViewModel, and repository facade remain. `LibraryRepository.saveNode` delegates to `ContentCommandPort`; all other methods remain on the legacy DAO path.

## Command Contract

`SaveNodeCommand` always contains a stable Node ID:

- Create allocates the Node ID when the pending save command is created, before entering the transaction.
- Update uses the existing Node ID and requires the editor's expected revision.
- The editor retains the pending command ID, Node ID, and fingerprint after a failed save.
- Retrying unchanged fields reuses the command.
- Changing title, Markdown, Area, target, expected revision, or mode invalidates the pending command and creates a new one.
- A successful save clears the pending command.

The request fingerprint is SHA-256 over a canonical UTF-8 representation of command type, Node ID, expected revision, Area ID, title, and exact Markdown body. `CommandId` and timestamp are not part of the fingerprint. Canonicalization uses a structured encoder with stable field order, not ad hoc concatenation.

## Room v7 Schema

### `processed_commands`

| Column | Type | Rule |
| --- | --- | --- |
| `command_id` | TEXT | primary key |
| `command_type` | TEXT | non-null |
| `request_fingerprint` | TEXT | non-null |
| `result_type` | TEXT | non-null |
| `result_payload_json` | TEXT | non-null, local replay snapshot |
| `processed_at` | INTEGER | non-null |

### `replication_outbox`

| Column | Type | Rule |
| --- | --- | --- |
| `change_id` | TEXT | primary key |
| `command_id` | TEXT | non-null, unique for this slice |
| `aggregate_type` | TEXT | `content.node` |
| `aggregate_id` | TEXT | Node ID |
| `operation` | TEXT | `create` or `update` |
| `base_revision` | INTEGER | null for create |
| `new_revision` | INTEGER | non-null |
| `domain_schema_version` | INTEGER | starts at 1 |
| `payload_json` | TEXT | canonical domain Node snapshot |
| `payload_hash` | TEXT | SHA-256 of payload bytes |
| `state` | TEXT | starts at `pending` |
| `created_at` | INTEGER | non-null |

An index on `(state, created_at)` supports later draining. Phase 2A emits one Node change per Node save. Markdown-derived Quiz and Review rows are deterministic local projections of the Node Markdown and do not emit independent outbox changes in this slice.

The outbox payload excludes `syncStatus`, reading traces, provider data, peer data, and transport metadata. Payloads are local application data and are never logged.

## Transaction Flow

```text
Editor save action
  -> pending SaveNodeCommand(CommandId, fingerprint)
  -> LibraryRepository compatibility facade
  -> ContentCommandPort
  -> Room withTransaction
       1. lookup processed_commands by CommandId
       2. replay matching result or reject fingerprint mismatch
       3. load Node and Area persistence rows
       4. map rows to domain values
       5. apply pure create/edit transition and expected revision check
       6. map and write learning_nodes (legacy sync_status = dirty)
       7. update Node FTS and Markdown-derived Quiz/Review rows
       8. insert one pending replication_outbox row
       9. insert processed_commands result snapshot
  -> return saved Node through the compatibility facade
```

Every listed write is in the same Room transaction. Any exception or typed failure before commit leaves canonical rows, FTS, derived rows, processed commands, and outbox unchanged.

## Idempotency

- Matching ID and matching fingerprint returns the stored first result.
- Replay does not load and mutate the Node again.
- Replay does not increment revision or timestamp.
- Replay does not rerun projections or insert another outbox item.
- Matching ID with a different fingerprint returns `CommandReuseConflict` and makes no write.
- Processed command rows are not included in backup v1 and are local operational records.

## Error Model

The new port returns typed outcomes:

| Failure | Meaning | Compatibility disposition |
| --- | --- | --- |
| `Validation` | blank/invalid content or Area input | current validation rejection |
| `Missing` | target Node or selected Area disappeared | current illegal-argument behavior |
| `Deleted` | target is tombstoned | current illegal-state behavior |
| `StaleRevision` | expected revision differs | current save-conflict behavior |
| `CommandReuseConflict` | same ID, different fingerprint | visible retry conflict; no write |
| `Storage` | Room/encoding/disk failure | preserve editor input and show current save failure |

Domain and application modules contain stable error codes, not localized strings. Adapter exceptions retain implementation causes only for local diagnostics and never include Node bodies or outbox payloads.

## File Ownership Target

```text
android-app/
|-- core/database/
|   |-- src/main/.../LearningDatabase.kt
|   |-- src/main/.../LearningEntities.kt
|   |-- src/main/.../LearningDao.kt
|   |-- src/main/.../RoomConverters.kt
|   `-- schemas/.../1.json ... 7.json
|-- domain/content/
|   `-- src/main/.../Node.kt, NodeEditor.kt, MarkdownQuizDeriver.kt
|-- application/content/
|   `-- src/main/.../SaveNodeCommand.kt, ContentCommandPort.kt
|-- data/content-room/
|   `-- src/main/.../NodeRoomMapper.kt, RoomContentCommandAdapter.kt
`-- app/
    `-- compatibility composition and LibraryRepository delegation
```

Exact files may be split further in the implementation plan when one responsibility would otherwise produce an oversized source file.

## Verification

### Pure Tests

- Create and edit transitions.
- Missing, deleted, invalid Area, and stale revision outcomes.
- Revision advancement and preserved compatibility fields.
- Markdown quiz derivation.
- Command fingerprint determinism and field sensitivity.
- Node mapper round trip without `syncStatus` in domain types.

### Real Room JVM Tests

Robolectric plus Room testing executes:

- v6 database with existing Node data migrates to v7 with data intact and empty operational tables.
- Successful create/edit commits Node, FTS, derived rows, processed command, and one outbox row.
- Same-command replay returns the first result with unchanged row counts and revision.
- Command ID/fingerprint mismatch writes nothing.
- Stale revision writes nothing.
- Injected projection failure after canonical mutation rolls the entire transaction back.

The projection writer is an internal data-adapter collaborator so tests can inject a failure without adding test-only methods to production classes.

### Compatibility And Architecture Gates

- Existing editor navigation and save policy tests remain green.
- Backup schema v1 round trips remain byte/behavior compatible.
- Required modules and dependency allowlists are enforced.
- Domain/application imports of Android, Room, JSON, UI, and old app data fail architecture verification.
- Full Android unit tests, migration tests, `assembleDebug`, Gradle project graph, and repository architecture scripts pass.

## Rollout And Next Slice

Phase 2A ships behind the existing save entry point; there is no runtime feature flag or dual write. Rollback is a code rollback only: the additive v7 tables may remain unused without affecting old data, but app downgrade from v7 is not supported unless the prior APK already knows schema v7.

After Phase 2A is stable, Phase 2B migrates remaining Content writes and queries. Phase 2C then separates Learning and Capture persistence, removes domain exposure of `syncStatus`, and prepares all canonical write paths for replication without implementing network synchronization.

# Personal Desktop-Mobile Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` only after the design gates in this document are approved.

**Goal:** Add an optional, local-first personal sync system where the desktop owns the full learning corpus and Android pulls a configured study subset while preserving offline use and portable backup.

**Architecture:** Keep SQLite/domain stores authoritative on each device. Add a versioned change-envelope protocol above existing repositories, then provide transports in priority order: trusted private network, optional internet relay, and explicit file handoff. Do not make a long JSON backup the normal synchronization protocol.

**Tech Stack:** Existing FastAPI desktop backend, desktop SQLite/content writer, Android Room/Kotlin repositories, HTTPS over Tailscale or a user-selected endpoint, OkHttp + org.json on Android, Android Storage Access Framework.

**Status:** Revised 2026-07-17 after a full codebase audit. The original draft assumed desktop schema capabilities that do not exist yet; this revision adds Phase 0 (desktop syncable schema) and corrects the entity model, progress-sync design, and network/auth prerequisites.

---

## Codebase Audit (2026-07-17)

The audit that shaped this revision. Every task below traces back to one of these findings.

### Desktop (`backend/`)

| Finding | Evidence | Consequence |
|---|---|---|
| Nodes use `slug` as PK; **no revision counter**, only `updated_at`; trash is `visibility='trash'` | `backend/db.py:15-30`, `backend/node_lifecycle_service.py:339-398` | Revisions must be added before deltas are meaningful |
| **No `areas` table** — area is a plain string column on `nodes`/`quizzes` | `backend/db.py:18`, `backend/db.py:65` | v1 treats Area as a label attribute, not a synced entity |
| Quizzes have `id` + `updated_at`, **no revision, no tombstone** | `backend/db.py:63-80` | Same schema work as nodes |
| `quiz_attempts` is append-only but keyed by `INTEGER AUTOINCREMENT` | `backend/db.py:192-199` | Cannot dedupe client uploads; needs a client-attempt-ID column |
| **Capture slips do not exist on desktop** — no table, service, or router | (Android-only concept today) | Desktop needs a `capture_slips` table to receive uploads |
| `reader_questions` keyed by `INTEGER AUTOINCREMENT`, hard-DELETE endpoint exists | `backend/db.py:106-115`, `backend/reader_question_router.py:70-74` | Needs `client_id` for idempotent create-only upload |
| Review scheduling state (`review_queue`) is latest-state; attempts are the durable log | `backend/db.py:180-199`, `backend/learning_service.py:70-119` | Sync the attempt log; **derive scheduling state on each side** |
| **No change-log / cursor capability** anywhere | schema inspection | Phase 0 adds a `sync_changes` envelope table |
| **No auth**; CORS is localhost-only; uvicorn binds `127.0.0.1` | `backend/api.py:73-84`, `scripts/dev.ps1:37,106-113` | Phone cannot even reach the desktop until bind + auth land |
| Desktop export = `content_manifest` / `llmwiki_pack` (no bodies, no `.txt` backup, no ZIP) | `backend/maintenance_service.py:31-229` | The `.txt` backup format is Android-side (`BackupCodec` v1); desktop package export is new work |

### Android (`android-app/`)

| Finding | Evidence | Consequence |
|---|---|---|
| All learning entities have stable string IDs, `revision`, `deletedAt`; `SyncStatus(clean/dirty/deleted/conflicted)` is written on every mutation but **never consumed** | `core/database/.../LearningEntities.kt:7-133` | v1 upload can key off `sync_status` flags immediately |
| `ReviewAttemptEntity` has client-generated string IDs; `ReviewStateEntity` is derived scheduling state | `LearningEntities.kt:115-133`, `ReviewRepository.kt:80-113` | Same "sync attempts, derive state" model as desktop |
| **No HTTP client dependency** (no OkHttp/Retrofit/kotlinx.serialization); AI adapter uses raw `HttpURLConnection` | `feature/settings/.../OpenAiModelGateway.kt` | Add OkHttp; reuse `org.json` (already used by `BackupCodec`) |
| `replication_outbox` / `processed_commands` tables exist (Room v7) but production code never writes them | `CommandPersistenceEntities.kt:8-38` | Keep reserved for future command-level replication; **v1 does not depend on them** |
| Backup import is **replace, not merge** (`clearAll()` then insert) | `LearningRepository.kt:338-369` | Restoring a backup must invalidate pairings/cursors — document the recovery path |
| Assistant conversations are Room-local, excluded from backup | `AssistantConversationEntity.kt`, `LearningBackup.kt` | Explicit sync non-goal for v1 |
| Room DB is at version 7 with full migration chain | `LearningDatabase.kt:12-185` | Sync lineage columns require a v7→v8 migration |

## Product Decision

### Recommended Baseline

Use the desktop as the personal authority and Android as an offline replica with an explicit subset policy.

```text
Desktop total corpus + desktop sync service
        |
        | incremental pull (scoped) / append-only push (events)
        v
Android study subset + local Room review state
```

The recommended connection path is Tailscale or an equivalent private mesh VPN. It works when the personal server moves between Windows and Linux, avoids exposing a home port, provides encrypted device-to-device access, and does not require CS Learning OS to operate its own account or relay service. A manually entered HTTPS URL remains supported for LAN, Cloudflare Tunnel, reverse proxy, or a future VPS.

### Sync Entity Matrix (v1)

Area handling deserves special attention: the desktop has no Area entity, only a label column. v1 therefore syncs **labels, not Area entities**. Android keeps its rich `AreaEntity` locally and matches pulled labels by `areaId`/slug; desktop-initiated renames arrive as ordinary content updates.

| Entity | Desktop → Phone | Phone → Desktop | Conflict policy |
|---|---|---|---|
| Areas (labels) | as node/quiz attributes | — | desktop label wins; phone remaps local references |
| Nodes (Markdown) | pull, scope-filtered | **no auto-upload in v1** | both changed → keep desktop version, park phone text as conflict draft |
| Quizzes (content) | pull, scope-filtered | **no auto-upload in v1** | same as nodes |
| Review attempts | pull (dedupe by attempt ID) | push, append-only, client IDs | none — log union; each side derives scheduling state |
| Review scheduling state | **never synced** | **never synced** | derived from the attempt log on each side |
| Capture slips | (desktop display only) | push, create-only, client ID as PK | none — phone is the only creator in v1 |
| Reader questions | status pulled back | push, create-only with `client_id` | status is desktop-owned |
| Assistant conversations | — | — | **non-goal v1** (device-local) |
| Media/assets | — | — | **non-goal v1** (text only; lazy-fetch later) |
| Deletions | tombstones in manifest | **not propagated in v1** | never silently delete phone-local changes |

### Non-Goals For The First Release

- No multi-user accounts, team sharing, billing, or hosted SaaS.
- No CRDT editor or automatic last-writer-wins Markdown overwrite.
- No background polling that drains battery or assumes the desktop is always online.
- No deletion propagation from phone to desktop until restore, conflict, and audit reports are proven.
- No media/asset transfer; Markdown text only.
- No assistant-conversation sync.
- No dependence on the `replication_outbox` table; it stays reserved for future command-level replication.

## Design Gates Before Coding

### Gate 1: Authority And Scope

Confirm these defaults with real desktop/phone use:

- Desktop is authoritative for nodes, quizzes, and their area labels.
- Android pulls only selected area labels, pinned nodes, and nodes with due reviews. A manual "download this area" action can extend the subset.
- Android remains authoritative for unsynced capture slips, reader questions, and review attempts until they are successfully uploaded and receipted.
- Deletion is represented by a tombstone and is never silently applied to a mobile-only change.
- Review scheduling state (`review_queue` / `ReviewStateEntity`) is **derived, never transferred**: the attempt log is the only shared progress record.

### Gate 2: Private Connectivity

Test three environments before choosing a default transport:

1. Same Wi-Fi: desktop service bound to the LAN interface with token auth; pairing QR carries endpoint, one-time token, and TLS fingerprint.
2. Different networks: Tailscale hostname reaches the same service without port forwarding; Tailscale ACLs are the network boundary.
3. Desktop offline: Android reports "last sync" and stays fully usable; it never blocks capture, reading, or review.

The sync endpoints must **refuse to start on a non-loopback bind without pairing credentials configured** — an unauthenticated LAN sync server is a hard fail, not a warning.

### Gate 3: Conflict Policy

Adopt field-specific behavior before implementing automatic upload:

- Markdown node changed on both devices: keep the desktop version and create a clearly named Android conflict draft containing the mobile text (`SyncStatus.conflicted` exists for exactly this). Never discard either text.
- Review attempts: append-only and idempotent by stable client attempt ID; scheduling is recomputed, never merged.
- Capture slips and reader questions: create-only upload; server assigns receipt metadata but preserves client ID.
- Area rename: arrives as ordinary content updates; Android remaps `areaId` references by label and shows a sync report.

## Implementation Phases

### Phase 0: Make The Desktop Schema Syncable

**Status: implemented 2026-07-17** (`backend/sync_envelope.py`, `backend/db.py` schema v5, hooks in all write paths, `backend/test_sync_envelope.py` 8 tests).

**Why this phase exists:** the original plan assumed stable IDs, revisions, tombstones, and a change cursor on the desktop. None of these exist (see audit). Everything else in this plan depends on them.

**Files:** `backend/db.py`, `backend/sync_envelope.py`, `backend/content_write_service.py`, `backend/learning_service.py`, `backend/reader_question_service.py`, `backend/node_lifecycle_service.py`, `backend/ai_job_service.py`, `backend/ingest.py`

Schema migration (schema_meta version 5, idempotent ALTERs for legacy DBs):

```sql
ALTER TABLE nodes   ADD COLUMN revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE quizzes ADD COLUMN revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE quizzes ADD COLUMN deleted_at TEXT;
-- revision 0 = pre-sync baseline; the write-path hook increments to 1 on
-- the first recorded change, and full ingests assign 1 at creation.

CREATE TABLE capture_slips (
  id TEXT PRIMARY KEY,              -- client-generated UUID
  body TEXT NOT NULL,
  type TEXT NOT NULL,
  topic_hint TEXT NOT NULL DEFAULT '', source_label TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'inbox',
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 0,
  deleted_at TEXT
);

ALTER TABLE quiz_attempts    ADD COLUMN client_attempt_id TEXT;  -- partial UNIQUE index, NULLs allowed
ALTER TABLE reader_questions ADD COLUMN client_id TEXT;          -- partial UNIQUE index, NULLs allowed

CREATE TABLE sync_changes (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,   -- the opaque cursor
  entity_type TEXT NOT NULL,               -- node | quiz | capture_slip | reader_question | review_attempt
  entity_id TEXT NOT NULL,
  revision INTEGER,
  content_hash TEXT,
  tombstone INTEGER NOT NULL DEFAULT 0,
  changed_at TEXT NOT NULL
);
CREATE INDEX idx_sync_changes_entity ON sync_changes(entity_type, entity_id);
```

Write-path hooks (all inside `ContentWriteService` / domain services — per `docs/data-policy.md` the write rule), each in the **same SQLite transaction** as the mutation:

- Node body edits, node file upserts (create/trash/restore/archive), quiz body edits: `revision += 1`, recompute `content_hash`, append one `sync_changes` row. Trash visibility maps to `tombstone = 1`.
- Permanent node delete: tombstone row with `revision = last + 1`.
- Attempt recording: desktop generates its own `client_attempt_id` (UUID hex) so desktop-originated attempts are pushable too; appends a `review_attempt` change row.
- Reader-question create (including AI-job queued questions), status change, delete: `client_id` assigned at creation; each transition appends a change row (delete = tombstone). Legacy rows without `client_id` log as `db-<pk>`.

Full-ingest behavior (the subtle part):

- Preserves `{id: revision}` and body hashes across the rebuild (same pattern as `node_activity`); unchanged content keeps its revision and logs nothing, changed content bumps and logs, vanished entities log tombstones.
- **Fixes a pre-existing data-loss bug:** `DELETE FROM quizzes` used to cascade-wipe `quiz_attempts` on every re-ingest; the attempt log is now preserved and re-inserted (filtered to surviving quizzes).

Acceptance criteria (all proven by `backend/test_sync_envelope.py`):

- Every mutation = exactly one change row; revisions increment; `sync_changes.seq` strictly increases.
- Trash → restore produces tombstone-then-live rows; permanent delete logs a tombstone with bumped revision.
- Legacy v4 databases migrate idempotently; re-ingesting unchanged content logs zero new rows and preserves revisions and attempts.

### Phase 1: Pairing, Bind Policy, And Auth

**Desktop files:** new `backend/sync_router.py` + `backend/sync_auth.py`; `backend/api.py` registers; `scripts/dev.ps1` gains `-ApiHost`; `backend/runtime_config.py` reads `CS_LEARNING_HOST`.

Endpoints:

```text
GET  /api/sync/v1/health      -- protocol version, server ID, no auth required
POST /api/sync/v1/pair        -- one-time pairing token -> device_id + scoped bearer credential
```

- Default bind stays `127.0.0.1`. LAN/Tailscale serving requires explicit `-ApiHost` / `CS_LEARNING_HOST` **and** refuses to expose `/api/sync/v1/*` without pairing credentials configured.
- Pairing token: desktop-generated, single-use, expires in 10 minutes, shown in the desktop UI as text + QR payload (`endpoint`, `token`, `server cert fingerprint`).
- Credential: random 256-bit bearer, stored **SHA-256 hashed** on the desktop, scoped (`sync:read`, `sync:push`), revocable from a desktop device list.
- All other desktop routers stay localhost-only; the sync router enforces its own bearer middleware.

Tests must prove: expired/reused pairing tokens rejected; credential hash never returned by any endpoint; unpaired devices get 401 on every sync route; loopback-only mode unchanged by default.

### Phase 2: Pull (Desktop Manifest + Android Merge)

**Desktop:** add to `sync_router.py`:

```text
POST /api/sync/v1/manifest    -- { cursor, scope } -> { protocolVersion, serverId, cursor, changes[] }
POST /api/sync/v1/pull        -- { entityType, ids[] } -> typed records
```

- `scope`: `{ "areas": ["algorithms", ...], "includeDueReviews": true, "pinnedNodeIds": [...] }` — `areas` are **labels** (audit finding).
- Manifest rows carry `{ type, id, revision, hash, tombstone }` only — never bodies or credentials.
- Cursor = last seen `sync_changes.seq`. `serverId` or `protocolVersion` mismatch → `cursor_reset` response; the phone re-baselines instead of applying partial deltas.

**Android:** new `feature/sync/` package (no UI yet):

```text
SyncTransport       OkHttp calls, bearer credential, TLS fingerprint pinning from pairing
SyncRepository      cursor + scope persistence (DataStore), pull orchestration
SyncManifest        org.json DTOs (project already uses org.json in BackupCodec)
SyncMergePolicy     typed conflict decisions + import report
```

Room v7→v8 migration adds sync lineage to `nodes` and `quizzes`: `baseRevision` (desktop revision at last merge) — conflict detection = remote revision advanced **and** local `syncStatus == dirty`.

Apply rules:

- Pull applied in one Room transaction; failure leaves DB and cursor untouched.
- Clean local record + remote change → accept remote, `syncStatus = clean`, `baseRevision = remote revision`.
- Dirty local record + remote change → accept remote into the canonical row **and** park the local text as a conflict draft (new capture slip of a `conflict` type, or a sibling node marked `syncStatus = conflicted`), surfaced in the sync report.
- Remote tombstone + clean local → soft-delete locally. Remote tombstone + dirty local → conflict report, never silent delete.
- Replaying the same pull is idempotent (test).

### Phase 3: Push Append-Only Learning Events

Only after pull is stable. Add batched upload endpoints:

```text
POST /api/sync/v1/push/attempts          -- [{ clientAttemptId, quizId, grade, answeredAt, elapsedMs, note }]
POST /api/sync/v1/push/captures          -- [{ id, body, type, topicHint, sourceLabel, createdAt }]
POST /api/sync/v1/push/reader-questions  -- [{ clientId, nodeId, question, createdAt }]
```

- Dedupe by client IDs (Phase 0 `UNIQUE` columns); response returns per-record receipts `{ id, status: accepted | duplicate | rejected }` plus a high-water cursor.
- Android upload source: rows with `syncStatus = dirty` (already maintained by every repository write). On `accepted` receipts only, reset to `clean` — one DAO update per entity.
- **Review progress convergence:** desktop processes accepted attempts through its existing scheduler (`learning_service.py:70-119`) so `review_queue` catches up; desktop-originated attempts flow back down in the next pull (manifest type `review_attempt`), and Android applies them through `ReviewScheduler` idempotently by attempt ID. Neither side ever sends scheduling state.
- Mobile Markdown/quiz content upload stays **off** in this phase; conflict drafts wait for the desktop review workflow.

### Phase 4: Explicit Sync UI And Network Policy

Add a compact More section showing:

- paired desktop name and trust fingerprint, with unpair/revoke
- selected areas and estimated mobile subset size
- last successful sync time and per-entity report (pulled, updated, conflicts, uploaded)
- manual `Pull now` and `Upload learning events` actions
- connection mode: local URL, Tailscale hostname, or advanced custom URL
- conflict drafts inbox entry (review phone-side text vs accepted desktop version)

Network behavior:

- Never sync automatically on cellular in the first release.
- Permit optional Wi-Fi-only periodic sync only after manual sync succeeds.
- Use foreground user action for large pulls and show cancellation/progress.
- Treat unreachable desktop as normal offline state, not an error dialog loop.
- Backup restore wipes sync lineage: after `restoreBackup`, require re-pair or explicit full re-baseline (import already clears operational tables — see audit).

### Phase 5: Portable File Handoff

File import remains the zero-server fallback and must become phone-friendly.

**Package instead of bare long text.** Desktop export gains `cs-learning-os-package-<timestamp>.zip` (manifest, JSON records, Markdown projections, optional media with hashes) alongside the existing manifest exports. The package record schema **is** the sync manifest record schema — an offline transport, not a second data model. Android `.txt` backup (`BackupCodec` v1) stays the phone-native format; cross-import documents the field mapping (desktop `slug` ↔ Android `id`, area label ↔ `AreaEntity`).

**Receive shared files directly.** Android `ACTION_SEND` handler for `text/plain`, `application/json`, and package ZIP MIME types. Receiving a file from QQ, WeChat, or Files opens an import confirmation screen, validates it, shows added/updated/conflicted counts, and requires explicit apply.

Tests must prove malformed, oversized, wrong-version, and duplicate packages leave user data untouched.

## Deployment Profiles

| Profile | Intended use | Required infrastructure | Recommendation |
|---|---|---|---|
| LAN URL | Same Wi-Fi, temporary desktop availability | Desktop FastAPI bound to LAN with token auth | First development profile |
| Tailscale | Moving Windows/Linux desktop, private remote access | Tailscale on phone and desktop | Recommended personal default |
| Cloudflare Tunnel/reverse proxy | Remote access without mesh client | Stable domain, desktop tunnel, valid TLS | Optional advanced profile |
| WebDAV/S3 package relay | Store encrypted packages, no live desktop API | User-owned storage | Future transport adapter |
| Manual TXT/ZIP | No network or emergency recovery | File manager/share target | Permanent fallback |

## Verification And Release Gates

- Desktop backend contract tests cover schema hooks, pairing, cursor progression, scopes, cursor-reset recovery, and auth rejection.
- Android unit tests cover manifest parsing, idempotency, merge policy, transaction rollback, conflict drafts, and import reports.
- A two-device manual matrix covers Windows desktop, Linux desktop, same Wi-Fi, Tailscale remote, desktop offline, and phone reinstall/restore.
- Sync telemetry stays local and inspectable: last cursor, report counts, endpoint mode, and failure reason — never note content or credentials.
- Release notes document no automatic deletion behavior and the exact recovery path using backup packages.

## Research Questions For The Design Review

1. Is Tailscale acceptable as a dependency for the personal default, or must the app provide a no-external-client remote route first?
2. Which areas should seed the phone subset automatically, and should due review override the subset boundary?
3. Should mobile capture slips upload immediately when connected, or only with an explicit sync button in the first release?
4. Is end-to-end encryption for package relay required before considering WebDAV/S3, or is Tailscale-only sufficient for the first remote phase?
5. Conflict draft representation: capture slip with a new `conflict` type (reuses the existing inbox UI) or a sibling node row with `syncStatus = conflicted`? Prototype both against the existing conflict-review UX before Phase 2 lands.
6. Should desktop-originated review attempts flow back to the phone in v1 (full bidirectional attempt log), or is phone→desktop upload enough for the first release? Bidirectional is more correct for multi-device reviewing but doubles merge-policy test surface.

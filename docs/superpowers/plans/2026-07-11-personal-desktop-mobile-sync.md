# Personal Desktop-Mobile Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` only after the design gates in this document are approved.

**Goal:** Add an optional, local-first personal sync system where the desktop owns the full learning corpus and Android pulls a configured study subset while preserving offline use and portable backup.

**Architecture:** Keep SQLite/domain stores authoritative on each device. Add a versioned change-manifest protocol above existing repositories, then provide transports in priority order: trusted private network, optional internet relay, and explicit file handoff. Do not make a long JSON backup the normal synchronization protocol.

**Tech Stack:** Existing FastAPI desktop backend, desktop SQLite/content writer, Android Room/Kotlin repositories, HTTPS over Tailscale or a user-selected endpoint, Android Storage Access Framework.

---

## Product Decision

### Recommended Baseline

Use the desktop as the personal authority and Android as an offline replica with an explicit subset policy.

```text
Desktop total corpus + desktop sync service
        |
        | incremental pull / selected mobile changes
        v
Android study subset + local Room review state
```

The recommended connection path is Tailscale or an equivalent private mesh VPN. It works when the personal server moves between Windows and Linux, avoids exposing a home port, provides encrypted device-to-device access, and does not require CS Learning OS to operate its own account or relay service. A manually entered HTTPS URL remains supported for LAN, Cloudflare Tunnel, reverse proxy, or a future VPS.

### Non-Goals For The First Release

- No multi-user accounts, team sharing, billing, or hosted SaaS.
- No CRDT editor or automatic last-writer-wins Markdown overwrite.
- No background polling that drains battery or assumes the desktop is always online.
- No deletion propagation until restore, conflict, and audit reports are proven.

## Design Gates Before Coding

### Gate 1: Authority And Scope

Confirm these defaults with real desktop/phone use:

- Desktop is authoritative for nodes, Areas, Markdown content, media metadata, and starter-independent quizzes.
- Android pulls only selected Areas, pinned nodes, and nodes with due reviews. A manual "download this Area" action can extend the subset.
- Android remains authoritative for unsynced capture slips, reader questions, review attempts, and local UI preferences until they are successfully uploaded.
- Deletion is represented by a tombstone and is never silently applied to a mobile-only change.

### Gate 2: Private Connectivity

Test three environments before choosing a default transport:

1. Same Wi-Fi: desktop service address is entered or discovered and a pairing QR code carries the endpoint plus device public key fingerprint.
2. Different networks: Tailscale hostname reaches the same service without port forwarding.
3. Desktop offline: Android reports "last sync" and stays fully usable; it never blocks capture, reading, or review.

### Gate 3: Conflict Policy

Adopt field-specific behavior before implementing automatic upload:

- Markdown node changed on both devices: keep the desktop version and create a clearly named Android conflict draft containing the mobile text. Never discard either text.
- Review attempts: append-only and idempotent by stable attempt ID.
- Capture slips and reader questions: create-only upload; server assigns receipt metadata but preserves client ID.
- Area rename/move: desktop wins during the first release; Android shows a sync report and remaps local references.

## Data And Protocol Design

### Task 1: Define A Versioned Sync Manifest

**Desktop files to inspect:**
- `backend/api_models.py`
- `backend/content_write_service.py`
- `backend/node_router.py`
- `backend/quiz_router.py`
- `backend/reader_question_router.py`

**Android files to inspect:**
- `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningEntities.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningDao.kt`
- `android-app/app/src/main/java/com/cslearningos/mobile/data/LearningRepository.kt`

Define a JSON manifest with stable entity IDs, entity type, revision, content hash, tombstone state, and change cursor. Keep payloads separate from the manifest so a phone downloads only changed, in-scope records.

```json
{
  "protocolVersion": 1,
  "serverId": "desktop-installation-id",
  "cursor": "opaque-change-cursor",
  "scope": { "areaIds": ["algorithms"], "includeDueReviews": true },
  "changes": [
    { "type": "node", "id": "uuid", "revision": 12, "hash": "sha256", "deleted": false }
  ]
}
```

Acceptance criteria:

- A manifest does not contain full Markdown bodies or API keys.
- Cursors are opaque to clients and safe to invalidate after a server reset.
- Every mutation has a stable ID and revision/tombstone data before it is syncable.

### Task 2: Add Desktop Pull Endpoints First

Create a dedicated `backend/sync_router.py`, registered by `backend/api.py`, with these read-only endpoints:

```text
POST /api/sync/v1/pair
POST /api/sync/v1/manifest
POST /api/sync/v1/pull
GET  /api/sync/v1/health
```

`pair` exchanges a one-time desktop-generated pairing token for a device ID and scoped credential. `manifest` takes a cursor and subset scope. `pull` takes requested change IDs and returns typed records. `health` reports protocol version, database availability, and the current desktop identity only.

Tests must prove:

- Expired or reused pairing tokens are rejected.
- A scope cannot request an unpaired Area.
- A second manifest with the returned cursor is empty when nothing changed.
- A desktop service reset returns a recoverable cursor-reset response, not partial data.

### Task 3: Add Android Sync Boundaries Without UI

Create feature-local Android contracts under `feature/sync/`:

```text
SyncTransport          network/file transport boundary
SyncRepository         cursor, scope, transaction orchestration
SyncManifest           protocol DTOs
SyncMergePolicy        typed conflict decisions and import report
```

The first Android transport calls the desktop pull endpoints only when the user requests sync. Apply a pull in one Room transaction and use existing repository/content paths for indexing and review-state preservation.

Tests must prove:

- A pull only creates/updates records inside the selected scope.
- Replaying the same pull is idempotent.
- A local Markdown edit conflicting with a remote revision creates a conflict draft rather than overwriting data.
- Failed pulls leave the prior database and cursor unchanged.

### Task 4: Add Mobile Upload For Append-Only Learning Events

Only after pull is stable, add batched upload endpoints for review attempts, captures, and reader questions. Use client-generated IDs and an upload receipt cursor so retries cannot duplicate events.

```text
POST /api/sync/v1/push/events
POST /api/sync/v1/push/captures
POST /api/sync/v1/push/reader-questions
```

Do not permit automatic mobile Markdown node replacement in this phase. Expose conflict drafts to the desktop review workflow first.

### Task 5: Add Explicit Sync UI And Network Policy

Add a compact More section showing:

- paired desktop name and trust fingerprint
- selected Areas and estimated mobile subset size
- last successful sync time and per-entity report
- manual `Pull now` and `Upload learning events` actions
- connection mode: local URL, Tailscale hostname, or advanced custom URL

Network behavior:

- Never sync automatically on cellular in the first release.
- Permit optional Wi-Fi-only periodic sync only after manual sync succeeds.
- Use foreground user action for large pulls and show cancellation/progress.
- Treat unreachable desktop as normal offline state, not an error dialog loop.

## Portable File Handoff Plan

File import remains the zero-server fallback and must become phone-friendly.

### Task 6: Package Instead Of Bare Long Text

Desktop export should produce both:

- `cs-learning-os-backup-<timestamp>.txt`: current human-shareable JSON compatibility file.
- `cs-learning-os-package-<timestamp>.zip`: manifest, JSON records, Markdown projections, and optional media with hashes.

Android import keeps `.txt` and `.json` support, then adds package validation and an import report. The package format shares the sync manifest record schema so it is an offline transport, not a second data model.

### Task 7: Make Android Receive Shared Files Directly

Add an Android `ACTION_SEND` intent handler for `text/plain`, `application/json`, and package ZIP MIME types. Receiving a file from QQ, WeChat, or Files should open an import confirmation screen, validate it, show added/updated/conflicted counts, and require explicit apply.

Tests must prove malformed, oversized, wrong-version, and duplicate packages leave user data untouched.

## Deployment Profiles

| Profile | Intended use | Required infrastructure | Recommendation |
|---|---|---|---|
| LAN URL | Same Wi-Fi, temporary desktop availability | Desktop FastAPI service | First development profile |
| Tailscale | Moving Windows/Linux desktop, private remote access | Tailscale on phone and desktop | Recommended personal default |
| Cloudflare Tunnel/reverse proxy | Remote access without mesh client | Stable domain and desktop tunnel | Optional advanced profile |
| WebDAV/S3 package relay | Store encrypted packages, no live desktop API | User-owned storage | Future transport adapter |
| Manual TXT/ZIP | No network or emergency recovery | File manager/share target | Permanent fallback |

## Verification And Release Gates

- Desktop backend contract tests cover pairing, cursor progression, scopes, reset recovery, and auth rejection.
- Android unit tests cover manifest parsing, idempotency, merge policy, transaction rollback, and import reports.
- A two-device manual matrix covers Windows desktop, Linux desktop, same Wi-Fi, Tailscale remote, desktop offline, and phone reinstall/restore.
- Sync telemetry stays local and inspectable: last cursor, report counts, endpoint mode, and failure reason, never note content or API keys.
- Release notes document no automatic deletion behavior and the exact recovery path using backup packages.

## Research Questions For The Design Review

1. Is Tailscale acceptable as a dependency for the personal default, or must the app provide a no-external-client remote route first?
2. Which Areas or tracks should seed the phone subset automatically, and should due review override the subset boundary?
3. Should mobile capture slips upload immediately when connected, or only with an explicit sync button in the first release?
4. Is end-to-end encryption for package relay required before considering WebDAV/S3, or is Tailscale-only sufficient for the first remote phase?
5. Should desktop conflict drafts live beside the original node, in a dedicated Inbox Area, or require immediate merge review?

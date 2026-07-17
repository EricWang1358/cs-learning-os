# Quiz Upstream Sync Design

## Scope

Add a versioned Android-to-desktop sync path for manually created and edited
Quiz records. This is a narrow extension of the existing node outbox flow.

Included:

- Android manual Quiz create and edit commands write the canonical Room row
  and a `content.quiz` replication outbox item in one transaction.
- The outbox payload is a versioned Quiz DTO that can deterministically render
  desktop `## Prompt`, `## Answer`, and `## Explanation` Markdown.
- Android batches only the earliest pending change for each Quiz ID.
- Quiz title, summary, and difficulty are desktop-owned because the Android
  Quiz entity has no corresponding fields. Android omits them; existing
  desktop Quizzes retain them and phone-created Quizzes derive a title from
  the Prompt and start at the desktop difficulty default of `medium`.
- Desktop validates `baseRevision`, idempotency `changeId`, and the next
  revision before writing the existing Markdown source, SQLite row, FTS index,
  and sync change envelope.
- An accepted receipt carries the desktop revision. Android removes exactly
  that outbox item and marks a Quiz clean only when its current local revision
  still equals the sent revision.

Excluded:

- Phone-to-desktop Quiz deletion/tombstones.
- Automatic text merge or last-writer-wins behavior for concurrent edits.
- Changes derived from node moves, area changes, starter-content maintenance,
  or review scheduling.

## Data Flow

```text
Quiz editor save
  -> Quiz save command validates expected revision
  -> Room transaction: quiz + FTS + processed command + content.quiz outbox
  -> SyncRepository selects oldest pending change per Quiz
  -> POST /api/sync/v1/push/quizzes
  -> Desktop revision check + Markdown/SQLite/FTS/change-log transaction
  -> { id, status, revision } receipt
  -> Android conditional clean acknowledgement + outbox deletion
```

The outbox payload, not a later database snapshot, is pushed. Therefore a
second local edit cannot be overwritten or acknowledged by an earlier receipt.

## Conflict And Failure Rules

- A stale desktop revision returns `stale_revision`; Android leaves the outbox
  and local Quiz dirty for the existing pull/conflict workflow.
- Invalid payloads, unsupported tombstones, or malformed desktop Markdown
  return a per-record rejection; one bad Quiz cannot fail the batch.
- Missing or revision-less success receipts do not acknowledge local state.
- Replaying the same `changeId` returns the persisted receipt without writing
  the Markdown document a second time.

## Verification

- Backend contract tests cover accepted update, idempotent replay, stale
  revision, metadata preservation, malformed source rejection, and HTTP auth.
- Android tests cover transactional outbox creation, payload mapping, accepted
  acknowledgement, rejected receipt retention, and protection against an old
  receipt clearing a newer local edit.
- Full backend tests and Android Debug unit tests plus APK assembly run before
  this slice is reported as complete.

# Android Offline Native Product Design

## Purpose

The Android product must become a self-contained local-first learning OS, not a phone shell that depends on the desktop FastAPI backend, a development server, cloud sync, or AI. The first product slice should run fully on the phone while preserving a clean path to later desktop sync and cloud sync.

This design replaces the WebView-first product direction with a native Android offline core built inside the existing `android-app` Gradle project.

## Product Position

The first offline Android product is a complete local learning loop:

1. Open the app without network.
2. Create a Markdown learning node.
3. Edit and save that node locally.
4. Read the rendered Markdown.
5. Search local content.
6. Create quiz cards manually or parse them from Markdown.
7. Review due cards with spaced repetition.
8. Persist reading, quiz, and review state.
9. Export a backup.
10. Restore from a backup.

The product must not require:

- A desktop backend.
- A Vite dev server.
- A hosted account.
- AI generation.
- Internet access.

## Recommended Approach

Build a real native Android app using the existing `android-app` project:

- Kotlin for product code.
- Jetpack Compose for UI.
- Room/SQLite as the local source of truth.
- SQLite FTS for offline search.
- Markdown as the user-facing content format.
- Sync-ready data fields from the first schema.

The current WebView shell should no longer be treated as the product surface. It may remain temporarily only as legacy scaffolding during migration, but the implementation target is native screens over local Room repositories.

## Alternatives Considered

### Keep WebView And Add Native Bridges

This would keep the React UI and expose Android Room through a JavaScript bridge.

Trade-off: it reuses existing UI, but it creates a three-state system: JavaScript state, WebView lifecycle state, and Room state. Editing, review scheduling, search indexing, backup, and future sync would all need bridge contracts before the product is stable.

Decision: reject for the offline product core.

### Package The Existing Web App As Static Assets

This would load a built React bundle from `android_asset`.

Trade-off: it can be useful for demos, but it does not solve native persistence, Room migrations, Android backup policy, local file exports, or future mobile sync contracts.

Decision: reject as the main path. Static assets can still be used for documentation or fallback screens.

### Native Android Offline Core

This builds local persistence, UI, search, quiz, review, and backup as Android-native product layers.

Trade-off: it requires a larger initial migration than a WebView shell, but it avoids carrying desktop assumptions into the phone product.

Decision: use this path.

## Architecture

```text
android-app/
  app/
    data/
      room entities
      dao interfaces
      database migrations
      repositories
      backup import/export
    domain/
      node model
      quiz model
      review scheduler
      markdown quiz parser
      sync-ready revision helpers
    ui/
      Compose navigation
      node list
      reader
      editor
      search
      quiz editor
      review queue
      settings/backup
```

The data layer owns durable truth. UI state should be derived from repositories and ViewModels, not duplicated into long-lived global UI state.

## Data Model

### Node

Required fields:

- `id`: stable UUID.
- `title`: user-visible title.
- `markdown_body`: raw Markdown source.
- `created_at`: local creation timestamp.
- `updated_at`: local update timestamp.
- `last_read_at`: nullable reading timestamp.
- `revision`: monotonically increasing local revision.
- `sync_status`: `clean`, `dirty`, `deleted`, or `conflicted`.
- `deleted_at`: nullable tombstone timestamp.

### Quiz Item

Required fields:

- `id`: stable UUID.
- `node_id`: nullable owning node id.
- `prompt`: question text.
- `answer`: answer text.
- `explanation`: optional explanation.
- `source`: `manual` or `markdown`.
- `source_anchor`: nullable Markdown block anchor for parsed cards.
- `created_at`.
- `updated_at`.
- `revision`.
- `sync_status`.
- `deleted_at`.

### Review State

Required fields:

- `quiz_id`.
- `ease`.
- `interval_days`.
- `due_at`.
- `last_result`: `again`, `hard`, or `good`.
- `attempt_count`.
- `updated_at`.

### Review Attempt

Required fields:

- `id`.
- `quiz_id`.
- `result`: `again`, `hard`, or `good`.
- `answered_at`.
- `scheduled_due_at`.

## Markdown

Editing is raw Markdown in a native text editor. Reading is rendered Markdown.

First version rendering must support:

- Headings.
- Paragraphs.
- Lists.
- Block quotes.
- Inline code.
- Fenced code blocks.
- Links as text or external intents.

The first version does not need a WYSIWYG editor.

## Quiz Sources

The product supports both manual and Markdown-derived quiz cards.

Manual quiz flow:

1. User opens a node.
2. User creates a quiz card.
3. Card is linked to the node.
4. Card enters the review queue.

Markdown-derived quiz flow:

1. User writes quiz syntax in Markdown.
2. Save parses quiz blocks.
3. Parsed quiz cards are upserted by `node_id + source_anchor`.
4. Removed quiz blocks tombstone their parsed cards.

The exact Markdown quiz syntax should be simple and deterministic. Recommended initial form:

```markdown
:::quiz id=optional-stable-anchor
question: Why avoid `(l + r) / 2` in binary search?
answer: It can overflow; use `l + (r - l) / 2`.
explanation: Overflow can occur before division.
:::
```

AI-generated quiz cards are out of scope for the offline first version.

## Review Scheduling

Use a lightweight Anki-like scheduler with three outcomes:

- `again`.
- `hard`.
- `good`.

The scheduler updates `ease`, `interval_days`, `due_at`, and writes a `review_attempt` record. The algorithm should be deterministic and small enough to test directly.

The first version does not need FSRS, deck options, or user-tunable scheduling parameters.

## Search

Use SQLite FTS for local search.

Search must support:

- Node title.
- Node Markdown body.
- Quiz prompt.
- Quiz answer.
- Result snippets.
- Jumping from result to reader.
- Jumping from quiz result to quiz detail or owning node.
- Reindexing after node save.
- Reindexing after quiz save or Markdown quiz parse.
- Removing tombstoned nodes and quiz items from active results.

## Backup And Restore

The first version must support JSON export and full restore.

Export includes:

- Nodes.
- Quiz items.
- Review states.
- Review attempts.
- Schema version.
- Export timestamp.

Restore rules:

- Restore is full overwrite, not merge.
- User must confirm overwrite.
- Schema version must be supported.
- Import validates before changing the active database.
- FTS is rebuilt after restore.
- Failed import leaves existing data unchanged.

This is a safety feature, not a sync protocol.

## Sync Readiness

The first version does not implement desktop sync or cloud sync, but it must preserve the future path.

Every syncable entity must have:

- Stable id.
- `created_at`.
- `updated_at`.
- `revision`.
- `sync_status`.
- `deleted_at` tombstone where deletion applies.

Future sync transports should be adapters over the local model:

- Manual export/import.
- Desktop sync.
- Cloud sync.

The local database remains the phone source of truth.

## Privacy And Backup Policy

The first version should avoid accidental leakage:

- Store data in the app private directory.
- Do not request broad external storage permissions.
- Do not package private desktop content into the APK.
- Keep export user-initiated.
- Disable or tightly restrict automatic Android backup until a product backup policy is explicit.

Custom encryption and App Lock are out of scope for the first version.

## Screens

First implementation should include:

- Home / node list.
- Node reader.
- Node editor.
- Search.
- Quiz editor.
- Review queue.
- Review card.
- Settings / backup and restore.

Screens may be visually simple. The important requirement is that the offline logic loop is complete.

## Out Of Scope For First Offline Version

- Desktop sync.
- Cloud sync.
- Hosted accounts.
- AI generation.
- WYSIWYG Markdown editing.
- Conflict resolution UI.
- File folder import.
- External storage content library.
- Full Anki/FSRS compatibility.
- Release signing and store distribution.

## Implementation Slices

### Slice 1: Native Foundation

- Add Kotlin, Compose, Room, and Navigation dependencies.
- Convert `MainActivity` to a Compose entry point.
- Keep package id stable.
- Preserve a buildable debug APK.

### Slice 2: Local Data Core

- Add Room database, entities, DAOs, migrations, and repositories.
- Add seed/demo data only through safe committed fixtures.
- Add unit tests for persistence and review scheduling where possible.

### Slice 3: Node Loop

- Node list.
- Node create/edit.
- Markdown reader.
- Last read persistence.

### Slice 4: Search

- SQLite FTS.
- Search screen.
- Reindex on save and restore.

### Slice 5: Quiz And Review

- Manual quiz editor.
- Markdown quiz parser.
- Review queue and card screen.
- Attempt recording and scheduler.

### Slice 6: Backup And Restore

- JSON export.
- Full overwrite import.
- Validation and FTS rebuild.

## Verification

Required checks for each slice:

- `.\scripts\android-doctor.ps1`
- `cd android-app; .\gradlew.bat assembleDebug`
- Unit tests when data/domain code is touched.

Manual smoke for the completed offline version:

1. Install APK on a phone or emulator.
2. Turn off network.
3. Create a node.
4. Edit Markdown and save.
5. Reopen app and confirm content persists.
6. Read the node and confirm `last_read_at` changes.
7. Search for title and body text.
8. Create a manual quiz.
9. Add a Markdown quiz block and confirm parsed card exists.
10. Review due cards and confirm scheduling changes.
11. Export JSON backup.
12. Delete or alter local data.
13. Restore backup and confirm data returns.

## Risks

- Native migration is larger than a WebView shell and should be sliced carefully.
- Markdown rendering libraries can constrain Compose styling.
- Room schema mistakes early can make migrations painful.
- Review scheduling must remain deterministic to avoid corrupting learning state.
- Backup restore must validate before overwrite.
- Debug build may still contain WebView-era dev URL config until the shell is removed.

## Decision

Proceed with a native Android offline product core in the existing `android-app` project. The first product version should be independent on the phone, complete across content/edit/search/quiz/review/backup, and designed for later sync without implementing sync in the first version.

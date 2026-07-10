# Android Markdown, Backup, and Compact UI Design

## Purpose

The Android app needs three coordinated improvements:

1. replace the partial hand-written Markdown renderer with a standard parser plus a small app adapter
2. replace phone-side JSON text editing with file-based backup export, share, save, and import
3. reduce top-of-screen visual weight on non-home pages and use collapsible sections more aggressively to save space

The goal is better correctness, better mobile UX, and cleaner architecture without piling more logic into `LearningOsApp.kt`.

## Confirmed Product Decisions

- Full backup export filename uses `cs-learning-os-backup-<timestamp>.txt`
- File content remains JSON
- Import accepts `.txt` or `.json` by parsing file contents as JSON
- The backup screen should not expose a large editable JSON text box
- Primary backup action is export and share
- Secondary backup action is save to local
- Restore action is import from file
- Markdown support should cover headings, emphasis, inline code, code blocks, blockquotes, ordered and unordered lists, links, horizontal rules, and tables
- Custom `:::quiz` blocks remain supported
- The home screen keeps its stronger top presentation; non-home pages should become more compact

## Problems In The Current Code

- `MarkdownRenderer.kt` manually parses only a subset of Markdown and will keep drifting from standard behavior
- `BackupScreen` still depends on `backupText`, which assumes users manually inspect or edit raw JSON on mobile
- `LearningOsApp.kt` is already large, so adding more parsing and Android file-transfer logic there would worsen maintenance
- non-home pages repeat prominent header cards even though the app shell already shows the brand/context above them

## Recommended Approach

Use a standard Markdown parser library as the source of truth, but keep rendering native in Compose through a small intermediate model owned by the app.

Split responsibilities into focused units:

- `StandardMarkdownDocument`
  - parse CommonMark plus table extension into an app-owned block/span model
- `QuizAwareMarkdownAdapter`
  - split `:::quiz` sections from regular Markdown and preserve quiz cards as custom blocks
- `BackupTransferCoordinator`
  - create backup documents, shareable temp files, and import payload reading helpers
- `BackupScreen`
  - present action tiles and collapsible help instead of a raw JSON editor
- shared compact-header helpers
  - reduce top-card prominence and move descriptive copy lower in the screen hierarchy

## Architecture

```text
node markdown
  -> QuizAwareMarkdownAdapter
  -> StandardMarkdownDocument (commonmark parser + table extension)
  -> Compose renderer blocks/spans

LearningRepository
  -> exportBackup(): JSON string
  -> restoreBackup(rawJson)

BackupTransferCoordinator
  -> buildBackupDocument(now, json)
  -> writeShareFile(cacheDir)
  -> writeToPickedUri(contentResolver, uri)
  -> readImportedText(contentResolver, uri)

Compose backup UI
  -> share export
  -> save export
  -> import restore
```

## Scope

### In Scope

- add standard Markdown parsing support for the approved syntax set
- keep `:::quiz` rendering
- export full backup as JSON-in-`.txt`
- share backup through Android sharesheet
- save backup to a user-chosen local document
- import backup from a picked file
- remove the large backup text field
- add compact/collapsible treatment to selected non-home screens
- reduce top header card prominence on non-home screens

### Out Of Scope

- translating node content or quiz content
- rich Markdown editing
- arbitrary raw HTML rendering
- replacing the Room backup schema
- redesigning the home screen hero treatment

## UI Direction

### Markdown

Rendered Markdown should stay visually consistent with the current app theme, but behavior should follow standard Markdown parsing rather than line-by-line heuristics.

### Backup

The backup page becomes a lightweight action panel:

- share full backup
- save full backup locally
- import backup file
- optional low-priority explanatory copy in a collapsible block

No raw backup editor remains on screen.

### Compact Non-Home Pages

For non-home screens:

- section headers become lower-contrast and smaller
- descriptive copy moves below primary content or into collapsible help blocks
- space-heavy overview cards become collapsible where practical, starting with backup/help and library overview/map surfaces

## Acceptance Criteria

### Phase 1

- Markdown now correctly renders the approved syntax set, including links and tables
- `:::quiz` blocks still render as custom quiz cards
- backup export can be shared as `cs-learning-os-backup-<timestamp>.txt`
- backup export can be saved locally through the system document picker
- backup restore imports from a picked `.txt` or `.json` file by parsing JSON content
- the raw backup text box is removed
- touched code is split into smaller purpose-specific units instead of growing existing stone-pile files

### Phase 2

- non-home header cards are visibly less dominant
- at least the library and backup flows use more collapsible space-saving sections
- explanatory copy is lower priority and no longer competes with the main content at the top of the page

## Risks And Mitigations

- Standard Markdown libraries do not understand `:::quiz` natively.
  - Mitigation: isolate quiz parsing in a wrapper layer and hand normal segments to the library.
- Android sharing requires file URIs that other apps can access.
  - Mitigation: use `FileProvider` plus cache-backed export files.
- UI compaction can accidentally hide useful information.
  - Mitigation: keep the home screen unchanged and use collapsible disclosures instead of deleting context.

## Decision

Proceed with a standard Markdown parser plus app-owned adapter, a file-based backup workflow, and targeted non-home compaction improvements that reduce UI noise without changing core learning content behavior.

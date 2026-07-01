# Android Mobile Learning Workflow Design

## Goal

Reframe the Android app from a scroll-heavy note list into a local-first mobile learning companion. The phone product should make it easy to capture small learning fragments, organize them into structured notes, review them as quiz cards, and export readable study materials without requiring cloud sync, a backend, or AI.

## Product Position

The Android app is not a miniature desktop app and not a news-feed-style content browser. It is the mobile capture and review surface for CS Learning OS.

The product should optimize for moments like:

- The user is watching a video and notices an unclear concept.
- The user hears a term such as "virtual memory" and wants to start a learning thread quickly.
- The user remembers a common mistake and wants to record it before forgetting.
- The user wants to review due cards during fragmented phone time.
- The user wants to export readable Markdown or TXT to a computer for longer editing, printing, or exam prep.

## Pain Analysis

### Mobile Input Is Expensive

Typing long Markdown on a phone is slow. A raw Markdown editor should exist, but it must not be the primary capture workflow.

The first mobile action should be a low-friction capture slip:

```text
I do not understand why TLB miss triggers a page table walk.
```

The user can later fold this slip into an outline, a node, or a quiz card.

### Feed-Like Layout Feels Wrong

A long vertical list makes the app feel like a content feed instead of a software tool. The mobile home should behave like a command center with stable destinations, visible system state, and a fixed bottom navigation bar.

### Backup JSON Is Not User-Facing Export

JSON backup is necessary for full app restore, but it is not a readable learning artifact. Users need a separate export mode that produces Markdown or TXT bundles they can inspect, send to a computer, print, or convert into exam materials.

### Local-First Must Not Block Future AI Or Sync

Core study flows must work offline. AI provider configuration and sync should be optional adapters. The domain model must support them later without making the first product depend on a server.

### Phone And Computer Roles Differ

The phone is best for capture, review, and light outline editing. The computer is better for bulk import, deep editing, PDF generation, printing, and final exam packet assembly. The product should make this division explicit instead of pretending the phone should do everything.

## Recommended Information Architecture

Use a fixed bottom navigation model:

```text
Home | Capture | Library | Review | More
```

### Home

Home is the command center. It should show:

- Continue reading.
- Quick capture prompt.
- Due review count.
- Recent capture slips.
- Recent nodes.
- Export or backup reminder when useful.

Home should not be a raw node list.

### Capture

Capture is the fastest path for mobile input.

Supported capture types:

- Unclear point.
- Mistake or trap.
- Video note.
- Concept seed.
- Question to solve later.

Each capture slip has:

- Body text.
- Optional topic hint.
- Optional source label.
- Capture type.
- Created timestamp.
- Status: inbox, linked, converted, archived.

### Library

Library owns organized knowledge:

- Nodes.
- Topics.
- Search.
- Recent notes.
- Outline navigation.

It should support both list and outline views, but the default should emphasize structured learning threads rather than a flat feed.

### Review

Review owns spaced repetition:

- Due cards.
- New cards from captures or nodes.
- Mistake cards.
- Review history.

The review flow stays reveal-first, then rating.

### More

More owns system capabilities:

- Settings.
- AI provider configuration.
- Backup and restore.
- Markdown/TXT export.
- Import.
- Desktop sync entry marked as future local transport.
- Support and diagnostics.

This mirrors mature mobile apps: core learning lives in bottom tabs; system tools live behind More.

## Core Workflow

### First-Time User

```text
Open app
-> Home explains local-first learning loop
-> Tap Capture
-> Write "I want to learn virtual memory"
-> Save as capture slip
-> Convert to outline node
-> Add first subheadings
-> Review or export later
```

### Everyday Capture

```text
Watching video
-> Capture unclear point
-> Assign topic "Virtual Memory" if obvious
-> Leave in Inbox if not obvious
-> Later process Inbox into node sections or quiz cards
```

### Outline To Markdown

The user edits structure first:

```text
Virtual Memory
  Address translation
    TLB
    Page table walk
  Common mistakes
    Virtual address is not physical address
```

The app projects it to Markdown:

```markdown
# Virtual Memory

## Address translation

### TLB

### Page table walk

## Common mistakes

### Virtual address is not physical address
```

Raw Markdown editing remains available for advanced users, but the mobile-first editor is outline-based.

### Capture To Node

Capture slips can be:

- Linked to an existing node section.
- Converted into a new node.
- Converted into a quiz card.
- Archived after processing.

This avoids forcing users to decide the final structure at capture time.

### Capture To AI Expansion

AI is optional and later-stage.

When AI is available, a capture slip can become:

- Draft explanation.
- Expanded node section.
- Quiz cards.
- Mistake checklist.

The local app should store AI jobs as explicit tasks with provider metadata, not hidden magic writes.

## Data Model

### CaptureSlip

```text
id
body
type: unclear | mistake | video_note | concept_seed | question
topic_hint
source_label
linked_node_id
linked_outline_block_id
status: inbox | linked | converted | archived
created_at
updated_at
revision
sync_status
deleted_at
```

### OutlineBlock

```text
id
node_id
parent_id
order
level
title
body
created_at
updated_at
revision
sync_status
deleted_at
```

### LearningNode

The existing node remains the durable learning unit. It should support both:

- Raw Markdown body.
- Derived Markdown projection from outline blocks.

The app should choose one source of truth per node at a time:

- `markdown` mode for raw editing.
- `outline` mode for mobile structured editing.

### ExportPackage

Readable export should be separate from backup:

```text
format: markdown_bundle | txt_bundle | quiz_sheet | full_json_backup
included_nodes
included_quizzes
created_at
export_report
```

## Import And Export Strategy

### Backup JSON

Purpose: full restore for the app.

Includes:

- Nodes.
- Captures.
- Outline blocks.
- Quizzes.
- Review state.
- Attempts.
- Reader questions.
- Version metadata.

This is not the primary user-facing study material.

### Markdown/TXT Export

Purpose: readable study material.

The first mobile export should support:

- Export one node as Markdown text.
- Export selected nodes as a plain-text Markdown bundle.
- Share through Android share sheet.

PDF generation is out of scope for the first mobile slice. Computer-side PDF or print workflows are better for exam packets.

### Quiz Sheet Export

Purpose: printable practice.

Initial format can be Markdown/TXT:

```markdown
# Practice Sheet: Virtual Memory

## Questions

1. What happens on a TLB miss?

## Answers

1. The processor walks the page table or invokes OS handling depending on architecture and state.
```

## AI Provider Strategy

AI is important, but it should not be required for the offline product.

Recommended staged approach:

1. Add an AI provider settings screen that explains the future optional-provider model.
2. Store provider configuration locally and explicitly.
3. Do not send content anywhere unless the user triggers an AI action.
4. Add networking only when the AI feature is implemented and documented.
5. Track AI-generated changes as draft suggestions before applying to nodes.

This preserves local-first trust while keeping the door open for user-provided API keys.

## Desktop Sync Strategy

The project should not require a hosted cloud service.

Recommended sequence:

1. Manual Markdown/TXT export.
2. Full JSON backup and restore.
3. Desktop import of mobile export.
4. Later local desktop sync adapter.
5. Hosted sync only if product direction changes.

The mobile app should remain useful for pure phone users.

## Visual Direction

The app should learn from mature mobile apps such as Chess.com:

- Fixed bottom navigation.
- Clear home command center.
- Large primary actions.
- Horizontal cards where useful.
- System tools collected under More.
- Strong app identity instead of a generic content feed.

The visual language should still match CS Learning OS:

- Dark workbench background.
- Yellow operational accent.
- Bordered cards.
- Dense but touch-safe controls.
- Clear distinction between learning actions and system settings.

## Implementation Phases

### A1: Mobile Workflow Shell

- Add bottom navigation: Home, Capture, Library, Review, More.
- Add More screen with Settings, AI Provider, Backup, Export, Import, Support.
- Rework Home into a command center, not a scroll feed.
- Keep existing node, review, search, and backup behavior working.

### A2: Capture Slip

- Add CaptureSlip entity, DAO, repository methods, and UI.
- Add Inbox view.
- Allow capture slips to link to nodes.
- Add tests for create, archive, and export preservation.

### A3: Outline Editor

- Add OutlineBlock model.
- Add outline editor UI for headings and section bodies.
- Add Markdown projection.
- Preserve raw Markdown editor as advanced mode.

### A4: Export UX

- Separate JSON backup from readable Markdown/TXT export.
- Add one-node Markdown export.
- Add selected-nodes TXT bundle export.
- Add quiz sheet Markdown export.

### A5: AI Provider Boundary

- Add AI settings screen.
- Add local provider config storage.
- Add AI draft job model.
- Add user-triggered "expand capture" and "generate quiz draft" flows after privacy and network policy are explicit.

## Acceptance Criteria

- The first mobile screen feels like an app command center, not a note feed.
- Bottom navigation is always available in portrait mode.
- A user can capture an unclear point in fewer than three taps from launch.
- A capture slip can survive app restart.
- A capture slip can be converted or linked instead of remaining dead text.
- A user can edit a learning node as an outline on the phone.
- Markdown remains the portable projection format.
- JSON backup is clearly labeled as restore data, not normal study export.
- Markdown/TXT export is available for readable study material.
- AI settings are explicit and optional.
- No cloud account is required.
- Pure phone users and phone-plus-desktop users are both supported.

## Open Decision

The recommended next implementation target is A1 plus the first half of A2:

```text
Bottom navigation + More screen + Capture Slip inbox
```

This gives the product a real mobile workflow before investing in deeper outline editing or AI.

## Self-Review

- No requirement depends on hosted sync.
- AI is designed as an optional adapter, not a hidden dependency.
- Markdown export and JSON backup are intentionally separate.
- The workflow addresses mobile typing friction directly.
- The design keeps the existing local Room/SQLite architecture and adds new domain objects instead of replacing it.

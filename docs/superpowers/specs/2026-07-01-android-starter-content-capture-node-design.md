# Android Starter Content And Capture-To-Node Design

## Goal

Make the Android APK useful on first launch and make Capture-to-Node feel like a learning workflow instead of a thin Markdown form.

The Android app must stay compatible with the desktop CS Learning OS content model. Demo Markdown, front matter, quiz conventions, track metadata, and future import/export behavior should not fork into an Android-only format.

## Current Pain

Clean Android installs are empty because Room is never seeded from `content-demo`. This makes Home, Library, Search, Review, and Capture hard to evaluate from a freshly installed APK.

Capture promotion is also too shallow. Today `promoteCaptureSlipToNode` only opens the Markdown editor with a title guessed from `topicHint` or the first line and a body shaped like:

```markdown
# <topic>

> Captured from phone / <source>

<slip body>
```

That loses the bigger product idea: the user wants a small phone note to become a draft learning node, possibly linked to an existing track/node, then later expanded by AI or desktop editing.

## Design Principles

- Use the same demo Markdown files as desktop whenever possible.
- Do not hardcode a second Android-only demo corpus in Kotlin.
- Seed only on first app data lifetime, not every app open.
- If the user deletes starter content, do not silently recreate it.
- Keep JSON backup/restore as the source of full app recovery.
- Keep readable Markdown/TXT export separate from backup.
- Capture-to-Node should create an inspectable draft before saving durable knowledge.
- AI routing is optional; local rule-based routing must be useful without network access.

## Starter Content Strategy

Android should package the repo-level `content-demo` directory as app assets through Gradle.

Recommended asset mapping:

```text
content-demo/
  nodes/**/*.md
  quizzes/**/*.md
```

The app imports these files on first launch only when starter content has not been seeded before. The seed marker should live in local preferences, for example:

```text
starter_content_seeded_version = 1
```

This means:

- Fresh install or cleared app data gets starter nodes/quizzes.
- Normal app upgrade does not duplicate starter content.
- User-deleted starter nodes stay deleted.
- A future seed version can intentionally add new demo content with migration rules.

## Markdown Compatibility Contract

The Android importer should preserve the Markdown body exactly after front matter extraction. It should parse only the minimal metadata needed for Room:

```text
title
area
track
order
status
visibility
tags
summary
related
linked_nodes
```

For the current Room schema, only `title` and `markdownBody` are required. Other metadata can remain embedded in the Markdown/front matter until Android has first-class fields for `area`, `track`, `order`, and tags.

Important compatibility rule: Android must not rewrite desktop Markdown into an Android-specific dialect during seed import. It can derive Room rows from Markdown, but the source text should remain portable.

## Demo Quiz Strategy

Desktop has standalone quiz Markdown under `content-demo/quizzes`. Android should import these into `QuizItemEntity` when possible by parsing conventional sections:

```text
## Prompt
## Answer
## Explanation
```

If a quiz file cannot be parsed safely, keep it out of Room rather than importing malformed review cards. This keeps startup predictable and avoids bad demo data.

## Capture-To-Node Workflow

Capture promotion should become a draft workflow:

```text
Capture slip
-> Generate node draft
-> User reviews title, target, outline, Markdown body
-> Save as new node or link/append to existing node
-> Slip becomes converted or linked
```

The first implementation should support:

- New-node draft from slip.
- Title derived from topic hint or first meaningful sentence.
- Body with an outline scaffold, not just a quoted block.
- Source and capture type preserved in Markdown.
- Slip status updated after save.

Recommended draft Markdown:

```markdown
# <title>

## Captured Question

<slip body>

## What I Need To Understand

- ...

## Notes

> Source: <source label>
> Type: <capture type>
```

## Local Routing Before AI

Before network AI exists, Android should provide deterministic routing hints:

- If `topicHint` matches a node title, use that node as the suggested target.
- If `topicHint` matches existing Markdown/front matter track text, show that track as context.
- If no target is obvious, create a new node draft.

This gives the user immediate value and creates the same interaction slot that future AI can upgrade.

## AI Extension Boundary

Future AI should attach to the draft step, not directly mutate saved nodes.

When API settings are configured, Capture can offer:

- Suggest target node/track.
- Expand slip into explanation.
- Generate quiz drafts.
- Append to a section.

AI output should be stored as a draft suggestion until the user applies it.

## Data Model Direction

Current data model can support the first slice without schema changes:

- Starter nodes use `LearningNodeEntity`.
- Starter quizzes use `QuizItemEntity` and `ReviewStateEntity`.
- Capture slips already have `status`, `linkedNodeId`, timestamps, revision, and sync status.

Likely next schema additions:

- `LearningNodeEntity.area`
- `LearningNodeEntity.track`
- `LearningNodeEntity.order`
- `CaptureSlipEntity.linkedOutlineBlockId`
- A durable `NodeDraftEntity` if drafts need to survive app restarts before save.

For this slice, editor state can hold the draft if we explicitly keep scope to "review then save now."

## Implementation Shape

Recommended files:

- `android-app/app/build.gradle`: include `../content-demo` as Android assets.
- `data/StarterContentImporter.kt`: read asset Markdown and convert to seed rows.
- `data/MarkdownFrontMatter.kt`: minimal parser for front matter and body preservation.
- `data/DemoQuizImporter.kt`: parse standalone demo quiz sections.
- `LearningRepository.kt`: `seedStarterContentIfNeeded`.
- `LearningViewModel.kt`: call seeding during init before users judge the empty app.
- `CaptureScreen.kt`: make promotion language say draft, not final conversion.
- Tests for first-launch seed, no duplicate seed, user deletion not recreated, front matter title extraction, quiz parsing, and capture draft shape.

## Acceptance Criteria

- A fresh APK install shows starter nodes without importing from desktop manually.
- Starter Markdown comes from `content-demo` assets, not duplicated Kotlin strings.
- Seed import is idempotent and does not recreate deleted starter content on every launch.
- Search can find seeded nodes.
- Review can show seeded quiz cards when quiz files parse.
- Capture slip promotion opens an editable node draft with useful outline scaffolding.
- Saving a promoted draft marks the slip converted or linked.
- Markdown remains compatible with desktop export/import expectations.
- Existing backup/restore still preserves user-created nodes, quizzes, reader questions, and capture slips.

## Self-Review

- The design does not introduce a cloud dependency.
- The design keeps Android content compatible with desktop Markdown.
- The design avoids Android-only demo drift.
- The design handles the user's empty-APK complaint directly.
- The first slice remains implementable without committing to full AI or sync.

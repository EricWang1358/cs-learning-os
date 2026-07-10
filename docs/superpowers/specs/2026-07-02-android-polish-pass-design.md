# Android Polish Pass Design

## Purpose

This polish pass refines the Android app after the first Markdown and backup upgrade. The priority order is:

1. compress non-home page hierarchy so actions appear before explanation
2. continue breaking large UI files into smaller, cleaner screen units
3. tighten interaction correctness around Markdown rendering and backup flows

The goal is to make the Android app feel lighter and more deliberate on phone screens while reducing stone-pile code in the current UI layer.

## Confirmed Direction

- Use the medium-compaction approach, not a minimal touch-up and not a heavy tool-panel redesign
- Apply the compaction mainly to non-home pages
- Keep the home screen's stronger presentation style
- Continue splitting responsibilities out of `LearningOsApp.kt`
- Finish the existing Markdown and backup behaviors so they feel complete rather than half-upgraded

## Problems To Solve

### Visual Density

Several non-home screens still lead with explanation or large card framing before the user reaches the primary task. On mobile this makes the app feel taller and noisier than it needs to.

### Architecture Pressure

`LearningOsApp.kt` still owns too much screen-specific structure even after the previous pass. This makes future UI changes riskier and slows down iteration.

### Interaction Gaps

The new Markdown layer and backup flow work, but they still need polish:

- links are styled but should behave like links
- table and list rendering can be steadier
- backup actions need sharper success/failure feedback
- old JSON-editing mental models should be fully removed from nearby copy and entry points

## Recommended Approach

Make a focused polish pass with three coordinated tracks:

### Track 1: Non-Home Compaction

Shift non-home screens to an action-first layout:

- keep a small heading block at the top
- move explanatory copy lower or behind disclosure
- ensure the primary input, actions, or list content appears earlier

### Track 2: Targeted UI Decomposition

Split the largest remaining screen chunks into smaller files with clear ownership:

- backup screen pieces
- library screen pieces
- any shared compact header/help surfaces that emerge naturally

This is not a broad refactor. It is a targeted cleanup that serves the current polish goals.

### Track 3: Interaction Finishing

Polish the current feature set without expanding scope:

- clickable Markdown links
- steadier Markdown tables, lists, and quote spacing
- clearer backup error handling
- more accurate nearby wording for backup/share/import actions

## Page-Level Design

### Capture

- keep only a small top heading
- move the composer to the top of the main stack
- move the AI chain explanation into a collapsible help area
- keep AI preflight important, but render it as a tighter status-focused card

### More

- reduce the page introduction to a short line
- keep settings sections collapsible, but visually closer to settings rows than hero cards
- keep support and notification explanation lower priority than the actual controls

### Backup

- keep a small title area
- lead with the three primary actions: share, save locally, import
- keep help text inside a collapsible section
- place risk or overwrite guidance near import-related actions instead of in a large intro block

### Library

- keep area-level collapsing
- keep overview and map, but default them to collapsed
- let the node list appear earlier on first view

### Search And Review

- keep a compact title row
- shorten explanatory text to one line where possible
- prioritize search field, buttons, cards, and due-review actions

### Reader, Editor, And Quiz Editor

- keep `DetailHeading`
- reduce its visual dominance further
- keep controls close to the heading so the page feels immediate rather than ceremonial

## Architecture Changes

### In Scope

- move backup-related UI out of `LearningOsApp.kt`
- move library-related UI out of `LearningOsApp.kt`
- keep using shared compact header and collapsible helper components
- avoid creating a second parallel component system

### Out Of Scope

- broad project-wide refactoring unrelated to the touched screens
- redesigning the home screen
- replacing the Markdown adapter architecture introduced in the previous pass

## Interaction Polish

### Markdown

- links should open when tapped
- table cells should wrap long text more gracefully
- table row and cell framing should remain readable on narrow screens
- nested list and quote spacing should be more consistent

### Backup

- sharing, saving, and importing each need clear success and failure messages
- import failures should prefer human-readable reasons such as invalid JSON, unreadable file, or permission issue
- nearby entry copy in `More` and `Backup` should reflect the new file-based workflow rather than old raw-JSON editing concepts

## Acceptance Criteria

- On non-home pages, the primary task appears before most explanatory copy
- `Capture`, `More`, `Backup`, `Library`, `Search`, and `Review` feel visibly more compact than before
- `Library` reaches node content faster on first entry
- `Backup` fully presents as a file/share/import workflow rather than a JSON editor workflow
- Markdown links are tappable
- Markdown tables and lists render more reliably on narrow screens
- `LearningOsApp.kt` becomes meaningfully smaller and more focused
- `testDebugUnitTest` passes
- `assembleDebug` passes

## Risks And Mitigations

- Over-compressing pages can remove useful context
  - Mitigation: keep the home screen unchanged and move context into collapsible sections rather than deleting it

- Refactoring screen code can create regressions in navigation or state wiring
  - Mitigation: keep decomposition local to screen boundaries and avoid business-logic movement unless necessary

- Markdown interaction polish can drift into a larger renderer rewrite
  - Mitigation: keep the parser architecture intact and only finish the missing interaction layer and layout details

## Decision

Proceed with a medium-compaction Android polish pass that first improves non-home hierarchy, then reduces `LearningOsApp.kt` pressure through targeted screen extraction, and finally finishes the Markdown and backup interactions already introduced.

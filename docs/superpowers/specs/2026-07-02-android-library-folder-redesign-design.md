# Android Library Folder Redesign Design

## Purpose

Redesign the Android `Library` screen from a stacked content-card view into a mobile file-browser view that feels closer to a folder manager.

The goal is to make the library faster to scan, easier to navigate on a phone, and structurally cleaner in code.

## Confirmed Direction

- `Area` becomes the top-level folder concept
- The library always starts at the full `Area` folder list
- Entering an `Area` opens a node file list directly
- `track` remains lightweight metadata, not a second navigation layer
- node check state is persistent and lightweight, not bulk-action mode
- overview and map stay available, but demoted behind collapsible secondary sections

## Problems To Solve

### Interaction Mismatch

The current library behaves like a reading dashboard: large cards, nested tracks, and delayed access to the actual node list. This does not match the user's desired file-manager mental model.

### Incomplete Folder Logic

`Area` is currently derived from `LearningNodeEntity.area`, so Android has no real create, rename, or delete flow for folders. Empty folders cannot exist, and folder lifecycle is not explicit.

### Architecture Pressure

The new behavior needs clearer boundaries than "derive all library structure from nodes in one screen file." We need isolated models for folder data, folder navigation state, and the folder-oriented screen UI.

## Recommended Approach

Implement the redesign in three coordinated layers:

### Track 1: Real Area Entity

Promote `Area` to a first-class Room entity with its own id, slug, display name, order, and lifecycle timestamps.

Nodes reference an `areaId`, while legacy `area` strings are retained only for migration and backup compatibility.

### Track 2: File-Browser Library UI

Replace the current "area card with embedded tracks and nodes" layout with:

- a root `Area` folder list
- an `Area` detail view that shows node rows directly
- lightweight metadata chips and small labels instead of large explanatory cards

### Track 3: Persistent Light Check State

Add a persistent check marker to nodes so users can lightly mark important or pending items. This is not selection mode and does not introduce batch actions in this pass.

## Page-Level Design

### Library Root

- show `Area` rows first, not overview cards
- each row looks like a compact folder item
- show the folder name prominently
- show compact metadata in the same row, such as node count and due count
- provide a lightweight entry to create an `Area`
- keep overview and map at the bottom in collapsed sections

### Area Detail

- show a compact top bar with back-to-all-areas action
- show the current `Area` name as the page title
- show node rows immediately
- show `track` only as a small tag or small section label
- keep row interactions simple:
  - tapping the row opens the node
  - tapping the checkbox toggles the persistent marked state

### Area Management

- support create `Area`
- support rename `Area`
- allow deleting an empty `Area`
- reject deleting a non-empty `Area` until the user has moved or removed its nodes
- support moving a node to another `Area`

## Data Design

### Area Entity

`Area` should have:

- `id`
- `slug`
- `name`
- `order`
- `createdAt`
- `updatedAt`
- `deletedAt`

### Node Additions

Nodes gain:

- `areaId`
- `isChecked`

The existing `area` string remains for migration and backup compatibility during this pass.

### View State

The library UI state should explicitly track:

- whether the user is at the root list or inside an `Area`
- the selected `Area`
- the current node-mark filter: `all` or `checked`
- transient dialogs for create, rename, move, and delete actions

## Migration And Compatibility

- migrate existing nodes by matching their current `area` slug to created `Area` rows
- create missing `Area` rows automatically during migration or backup import
- keep backup import/export compatible with existing payloads while extending them to include `areas` and checked state
- accept older backups that only contain node `area` strings

## Acceptance Criteria

- the Android library root opens to a folder-style `Area` list
- entering an `Area` opens a direct node file list
- `track` is visually weak metadata, not a second navigation layer
- Android can create, rename, and delete empty `Area` folders
- Android can move a node to another `Area`
- empty `Area` folders can exist and render correctly
- node checked state persists across app restarts
- the library supports filtering between `all` and `checked`
- overview and map remain available but do not dominate the first screen
- focused unit tests pass
- `:app:compileDebugKotlin` passes
- `assembleDebug` passes

## Risks And Mitigations

- Schema changes may break backup compatibility
  - Mitigation: keep backward-compatible decode behavior and add migration tests

- The library screen could become another oversized file
  - Mitigation: split folder models, area-management UI, and node-list UI into focused files

- Adding folder management could accidentally become a full desktop file-manager project
  - Mitigation: keep this pass limited to create, rename, delete-empty, move-node, and checked-state persistence

## Decision

Proceed with a focused Android Library redesign that upgrades `Area` into a real entity, shifts the UI to a folder-browser model, and adds persistent lightweight node marking without introducing bulk-action complexity.

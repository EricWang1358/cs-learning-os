# Android Library Tree Design

## Goal

Replace the Android Library's card-and-toolbar layout with a compact file-browser tree. Areas behave like folders, nodes behave like files, and the default screen prioritizes scanning and navigation over visible management controls.

## Confirmed Interaction Model

- The Library starts with one vertical list of Area rows.
- Tapping an Area row expands or collapses that Area inline. There is no dedicated expand button.
- An expanded Area shows lightweight, frameless text actions for creating a node, editing the Area, and opening secondary Area actions.
- Nodes are visible only within an expanded Area.
- Tapping a Node row immediately opens the existing Reader route. Nodes never add a second inline disclosure level.
- Node operations remain reachable from the Reader and its existing overflow or action surfaces. Editing, moving, check state, trash, and permanent-delete behavior are retained.
- The Trash row is always last. It is a system view, not an `AreaEntity`, and cannot be renamed, moved, or deleted.
- Expanding Trash shows trashed nodes and their original Area. Restore returns a node to its stored `areaId`; permanent deletion stays explicit and confirmed.

## Layout

### Area Row

Each row contains a folder indicator, Area name, compact track preview, and item count. The whole row is the disclosure target. Due and checked counts remain available as secondary metadata without turning the row into a card.

Expanded content uses the same inline disclosure treatment as More: a quiet surface, a short action strip with no outlined buttons, and direct node rows. Area deletion is not a primary command. The existing empty-Area restriction remains; any destructive Area action is placed in secondary actions and retains its confirmation or rejection feedback.

### Node Row

Each node row shows title, track, check state, due-review summary, and recency. Tapping the row opens the Reader. This preserves the current reading workflow while removing duplicate Read buttons and the proposed node-disclosure layer.

### Trash Row

Trash is anchored after ordinary Areas even when empty. Its count is the number of recoverable trashed nodes. Expanded rows show original Area and deletion recency. Available commands are Restore and a separately-confirmed Delete forever. Restore uses the current repository behavior and returns the node to the original Area; it must not create a new Area or silently change metadata.

## Preserved Behavior

- Area creation, rename, empty-Area deletion, and node relocation.
- Persistent node check state and checked filtering where already supported.
- Track labels, due-review counts, summaries, timestamps, and node ordering.
- Reader, editor, quiz/review linkage, search exclusion for trashed nodes, and backup serialization.
- Existing confirmation and data-recovery rules: moving to Trash is reversible; Delete forever is recoverable only through a backup.

## State And Accessibility

- Expanded Area state is held in Library UI state and restores consistently across recomposition.
- Only one Area is expanded at a time; expanding another Area collapses the prior one.
- Area rows expose button role plus localized expanded/collapsed semantics. Node rows expose the Reader navigation action.
- All row text wraps or truncates safely at 320dp. Text actions use minimum touch targets despite their frameless appearance.

## Testing

- Library model tests verify Area ordering, the final system Trash row, and that ordinary Area actions do not include direct destructive deletion.
- UI policy tests verify one expanded Area at a time, a Node row maps to Reader navigation, and Trash exposes restore metadata rather than Area controls.
- Repository tests continue to verify Trash restore preserves the original Area and permanent deletion is only reachable from Trash.
- Run targeted Library tests, full unit tests, Android architecture verification, and `assembleDebug`.

## Scope

This redesign changes the Android Library presentation and interaction routing only. It does not alter the Room schema, backup format, Reader content model, review scheduling, or Trash persistence contract.

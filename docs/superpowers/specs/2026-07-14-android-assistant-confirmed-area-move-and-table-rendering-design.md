# Android Assistant Confirmed Area Move And Table Rendering Design

## Goal

Let the assistant recommend moving the currently targeted node to one existing Area, require explicit user confirmation before an atomic move, and render assistant-produced GFM tables correctly on phone screens.

## Scope

### Confirmed Area Move

- The assistant proposal references only the current node and an existing Area ID.
- The UI shows the proposed Area and a short reason, then exposes one confirm action.
- The user must tap the action before any write occurs.
- A dedicated move command validates node existence, target Area existence, and node revision in one Room transaction.
- A successful command updates the canonical node, dependent projections, processed-command record, and local replication outbox atomically.
- Repeating the same command is idempotent. A stale revision, deleted node, missing Area, or mismatched command reuse returns a typed failure and does not partially write.

### GFM Tables

- Keep CommonMark plus `TablesExtension` as the Markdown AST parser.
- Extend assistant Markdown normalization only for an unambiguous table: a header row, a valid delimiter row, and at least one data row.
- Normalization adds required table boundaries and missing leading/trailing pipes without rewriting fenced code, quiz blocks, or ordinary text containing pipes.
- Render tables in a horizontally scrollable viewport. Columns retain readable minimum widths rather than being compressed to the phone width.

## User Flow

1. The assistant responds to a current-node request with a validated existing Area ID and reason.
2. The application parses this as a move proposal, not an immediate mutation.
3. The assistant message presents `Move to <Area>` and the reason.
4. Confirming submits the move command. Success refreshes the node and message state; failure presents a typed, non-sensitive explanation.
5. Rejecting or leaving the proposal untouched changes no content.

## Boundaries

- The model cannot create, rename, or delete Areas.
- The model cannot batch-move nodes in this release.
- The assistant/UI layers do not mutate Room directly.
- The data adapter owns the transactional move and outbox record.
- Existing Node save behavior remains unchanged.

## Tests And Visual Verification

- Pure proposal parsing tests cover known/unknown Area IDs and malformed directives.
- Command tests cover confirmed success, rejection without write, stale revision, missing/deleted targets, same-command replay, and command reuse conflict.
- Markdown tests cover screenshot-style tables without a leading pipe, tables after prose/list content, and protected code/quiz blocks.
- Compose tests verify the confirmation action is absent for invalid proposals and invokes only the confirmation path for valid proposals.
- Emulator screenshots verify the assistant table is rendered as a scrollable table and that the move confirmation updates the node Area.

## Deferred Work

- Code rendering is deferred. A later design will keep up to three code lines inline and open longer source in a dedicated monospaced code reader with scrolling, selection, and language metadata.
- Math and chemistry rendering are deferred. A later design will introduce explicit formula blocks and choose a safe dedicated renderer or image pipeline for LaTex and chemistry notation.
- AI prompt changes for batch classification and formula/code-specific structured output are deferred until those renderers and actions have typed contracts.

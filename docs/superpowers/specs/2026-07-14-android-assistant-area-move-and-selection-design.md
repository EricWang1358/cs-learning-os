# Android Assistant Area Move And Text Selection Design

## Scope

This change restores the Android localization test baseline, adds a single-node Area move proposed by the assistant and confirmed by the user, and makes completed assistant Markdown bodies selectable and copyable. It does not add batch writes, link rewriting, code/formula readers, or automatic model writes.

## Area Move

Nodes have stable IDs; Capture references use `linkedNodeId`; Markdown links are not authoritative Area paths. A move therefore updates only the target node's `areaId` and `area` slug plus the existing related projections. It must reuse `LearningRepository.moveNodeToArea`, never rewrite Markdown.

The model may emit one typed move proposal containing the current node ID, its expected revision, an existing target Area ID, and a concise reason. The UI renders a confirmation card only after validating that the target Area exists. Confirmation calls a new assistant bridge action, rechecks the node revision, and delegates to the existing repository move operation. A changed, missing, deleted, or already-moved node produces a non-destructive error.

`AssistantRunMachine` becomes the state authority for the new proposal request only: start, context-ready, streaming, parsing, completion, cancellation, supersession, and failure all carry the request run ID. Existing assistant chat remains compatible while this path proves the boundary.

## Text Selection

Completed assistant Markdown is rendered through an `AssistantMessageBody` boundary. Native Compose selection wraps only the rendered prose body. Citation controls, link destinations, confirmation cards, and draft/retry buttons remain outside the selectable region so their tap behavior is retained. Streaming and user messages remain non-selectable.

## Verification

Unit tests cover current localization copy, stale/invalid move proposals, revision rejection, and run-ID stale events. Compose tests verify assistant prose exposes selectable text while action cards remain independently actionable. The Android debug unit suite, connected test, architecture verifier, and an emulator smoke validate the result.

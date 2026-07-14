# Client Android-Parity Documentation Design

## Goal

Make the React Web client roadmap and root documentation follow the verified Android product baseline, so contributors can distinguish shipped behavior, planned parity work, and historical migration material.

## Scope

The client in scope is `app/`, the React Web client served by the existing local FastAPI backend. This work does not create an Electron shell, a new desktop-native application, cloud synchronization, or account infrastructure.

The documentation refresh covers:

1. A root `docs/client-android-parity-plan.md` execution plan.
2. Current Android information in `README.md`.
3. Cleanup of active Android execution documents that still direct contributors to the retired WebView and cleartext emulator route.
4. A clear status convention for current, planned, and historical Android documents.

## Source Of Truth

Android behavior is accepted only when it has a current implementation and automated/device verification. The current baseline includes native Compose screens, Room-backed local data, backup/restore with bounded imports, optional user-configured HTTPS-only AI providers, an editable AI-draft flow, a knowledge assistant, Markdown table rendering, and security limits for untrusted content.

The plan must not claim desktop/mobile sync, replication transport, semantic search, formula rendering, code-reader mode, batch assistant writes, cloud accounts, or a desktop-native shell as shipped.

## Documentation Model

`README.md` is the concise project entry point. It will state the product topology, current Android properties, security boundary, build entry points, and links to deeper documentation. It must not contain historical implementation instructions.

`docs/client-android-parity-plan.md` is the actionable Web-client parity roadmap. It will contain a capability matrix with Android status, Web status, backend dependency, priority, and acceptance evidence. Work is ordered by shared safety and local-data contracts before UI equivalence, then optional AI workflows, and finally deferred cross-device capabilities.

Date-stamped specifications and completed plans remain historical decision records unless they contain retired instructions that someone could still execute by mistake. Historical architecture and migration documents should stay in place for traceability, but they must not be presented as the current operating guide. If a document contains direct WebView, `http://10.0.2.2:5173`, or cleartext emulator instructions, that executable guidance must be removed, deleted, or explicitly relabeled as retired.

## Client Parity Stages

### Stage 0: Contract And Safety Alignment

The Web client must consume the current local API without triggering side-effecting GETs, use explicit guarded model-preflight POST semantics, present AI drafts as reviewable proposals, and retain bounded import/link/Markdown handling equivalent to Android policy.

### Stage 1: Local Learning Workflow Alignment

The Web client must make Android's current local workflows easy to find: areas, node editing, Capture-to-draft flow, review queue, Trash, backup/restore, and a clear offline/local-data explanation. Existing API routes should be reused before backend expansion.

### Stage 2: Optional AI Alignment

The Web client must make provider configuration, provider validation, draft generation state, review/apply/reject, and failure recovery as explicit as Android. It must state what content is sent and never imply autonomous persistence.

### Stage 3: Deferred Cross-Client Capability

Replication/sync, shared identity, conflict resolution, semantic retrieval, rich code/formula readers, and batch assistant write operations remain separately designed work. The plan lists the required contracts but assigns no implementation date.

## Quality Rules

- Current docs use verified present-tense statements and link to current source/test evidence.
- Plans label unimplemented work as planned or deferred, never as a user-facing capability.
- Every client-parity slice names its API contract, UI ownership, tests, and Android comparison target.
- README and usage guidance are UTF-8, readable Chinese/English where applicable, and avoid stale screenshots or obsolete version claims.

## Verification

Documentation changes are checked with repository searches for retired WebView/cleartext execution instructions, link/path validation, Markdown rendering, and `git diff --check`. The client roadmap itself is reviewed against current Android source, backend routes, and the Android architecture/security documentation.

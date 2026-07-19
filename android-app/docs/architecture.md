# Android Architecture

The Android product is local-first and independently buildable. Room-backed local data is the application read source of truth. UI sends typed actions to screen state holders; application commands validate domain rules and commit canonical rows, projections, and future replication outbox records atomically.

No required build input resolves outside this Android repository root. Starter content, recovery guidance, module build logic, and tests are Android-owned. In the monorepo, the broader target contract is available at `../../docs/superpowers/specs/2026-07-13-android-layered-modular-architecture-design.md`; this document remains the self-contained, enforceable Android boundary when Android is published separately.

## Phase 2A Module Graph

```text
:app (compatibility shell)
  |-- :core:kernel
  |-- :core:database -------> :data:graph-room
  |-- :domain:content -------> :core:kernel
  |-- :application:content --> :core:kernel, :domain:content
  |-- :data:content-room ---> :core:kernel, :core:database,
  |                             :domain:content, :application:content
  |-- :data:graph-room
  |-- :feature:assistant:api
  |-- :feature:assistant:impl --> :feature:assistant:api
  |-- :domain:assistant -------> :core:kernel
  `-- :adapter:model-openai ---> :domain:assistant
```

Dependency direction points toward contracts and domain policy. `:domain:assistant` and `:domain:content` are pure Kotlin. `:application:content` owns typed commands and idempotency policy. `:core:database` owns the Room schema and migrations and composes the local `:data:graph-room` KG tables into the same database. `:data:content-room` is the only Node command adapter allowed to combine application commands with Room. `:adapter:model-openai` owns endpoint normalization, request JSON, HTTP, SSE parsing, provider error mapping, and credential redaction. `:app` composes these modules while the legacy UI and compatibility facade are migrated.

## Port Interaction

| Caller | Port or event | Callee | Canonical write allowed | Phase |
| --- | --- | --- | --- | --- |
| User/UI | `AssistantEntryRequest` | Assistant entry policy | No | Active |
| `:app` compatibility facade | `ModelRequest` through `ModelGateway` | model adapter | No | Active |
| Model adapter | `ModelEvent` carrying `runId` | `:app` compatibility facade | No | Active |
| Target Assistant runtime | `ModelEvent` through `AssistantRunMachine` | Session/Run aggregates | No | Next |
| Assistant parser | reviewed proposal | application command port | Only after acceptance | Next |
| Node editor / `LibraryRepository` facade | `SaveNodeCommand` through `ContentCommandPort` | `RoomContentCommandAdapter` | Yes, create/edit slice | Active |
| Room command adapter | one Room transaction | canonical Node, projections, command receipt, outbox | Yes | Active |
| Replication worker | committed local outbox record | device-neutral peer | Already committed data only | Future |

The local `:data:graph-room` module is Android-owned persistence for KG-shaped
data. It does not imply that Study Sync ingests the desktop's complete
KnowledgeGraph or generated index state.

Model output is always untrusted proposal data. It cannot directly write Nodes, Quizzes, Captures, review state, Room rows, backup data, or synchronization records. The current coordinator retains its response-message correlation while `AssistantRunMachine` defines, but does not yet drive, the target `runId` cancellation/supersession/stale-callback guarantee. Proposal application will additionally compare the target base revision/hash.

## Study Sync Boundary

Desktop sync is a mobile study projection, not a full desktop database mirror.
The desktop remains authoritative for complex authoring, Markdown frontmatter,
KnowledgeGraph structure, generated indexes, and graph repair. Android consumes
only the phone-friendly subset needed for practical use on the go:

- light reading Node fields that Room can render safely;
- Quiz records for review;
- Daily Bite `bite_card` records for short recall drills;
- Capture slips and reader questions when explicitly synced;
- Review attempts and other learning events that can be pushed back.

The Android Room schema must not grow just to match desktop-only tables. When a
desktop feature needs to reach the phone, add or reuse a projection that maps it
into the above study data instead of teaching Android to ingest the whole
desktop knowledge graph.

## Source Tree

```text
android-app/
|-- app/                         compatibility composition, UI, legacy facade and assistant
|-- core/kernel/                 stable IDs, revisions, domain results
|-- core/database/               Room schema, DAO, v1-v7 migrations and entities
|-- domain/assistant/            ModelGateway and pure AssistantRunMachine
|-- domain/content/              pure Node model, editor policy and quiz parser
|-- application/content/         idempotent SaveNodeCommand port and fingerprint
|-- data/content-room/           Room mapper, command transaction and projections
|-- data/graph-room/             Android-owned local KnowledgeGraph Room adapter
|-- feature/assistant/api/       typed feature entry contract
|-- feature/assistant/impl/      entry policy implementation
|-- adapter/model-openai/        OpenAI-compatible HTTP/SSE adapter
|-- build-logic/                 shared pure-Kotlin module convention
|-- starter-content/             repository-local bundled learning content
`-- docs/                        Android-owned architecture and recovery contracts
```

Future replication modules belong beside these roots rather than under desktop code. They consume local outbox records only after a canonical local commit.

## Enforcement

From the repository root run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-android-architecture.ps1
```

The verifier requires the Phase 2A module graph and exact dependency edges, rejects parent `content-demo` build inputs, and reports every forbidden source reference with its file and line. It keeps pure-domain rules, prevents `:core:database` from depending on `:app`, prevents legacy app-data imports in `:application:content`, and prevents UI imports in `:data:content-room`. Legacy `LearningViewModel` and `LearningRepository` size targets remain visible warnings during migration.

## Phase 2A Node Transaction

Phase 2A adds a Node create/edit vertical slice. Room migration v6 to v7 adds `processed_commands` and `replication_outbox` while preserving the existing `sync_status` column and backup schema v1. The domain `ContentNode` deliberately excludes `syncStatus` and `lastReadAt`; the Room mapper preserves read activity and writes legacy dirty status for a local Node change.

`SaveNodeCommand` has a stable content fingerprint. The adapter stores the first outcome for a command ID, replays that outcome only when the fingerprint matches, and rejects reuse of the same ID with different content. Its Room transaction validates the command, writes the canonical Node, refreshes derived quiz/FTS/review projections, records the command receipt, and appends one local outbox payload. A failure in any projection rolls the whole transaction back.

The outbox is local durable intent for a future replication worker. Network transport, peer discovery, remote inbox application, replica identity, causal envelopes, conflict resolution, acknowledgements, and sync UI are not implemented in Phase 2A. Assistant runtime wiring remains a separate migration; model output still cannot write Room directly.

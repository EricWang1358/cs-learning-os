# Android Architecture

The Android product is local-first and independently buildable. Room-backed local data is the application read source of truth. UI sends typed actions to screen state holders; application commands validate domain rules and commit canonical rows, projections, and future replication outbox records atomically.

No required build input resolves outside this Android repository root. Starter content, recovery guidance, module build logic, and tests are Android-owned. In the monorepo, the broader target contract is available at `../../docs/superpowers/specs/2026-07-13-android-layered-modular-architecture-design.md`; this document remains the self-contained, enforceable Android boundary when Android is published separately.

## Phase 1 Module Graph

```text
:app (compatibility shell)
  |-- :feature:assistant:api
  |-- :feature:assistant:impl --> :feature:assistant:api
  |-- :domain:assistant -------> :core:kernel
  `-- :adapter:model-openai ---> :domain:assistant
```

Dependency direction points toward contracts and domain policy. `:domain:assistant` is pure Kotlin and cannot import Android, UI/data packages, JSON, HTTP, or provider types. `:adapter:model-openai` owns endpoint normalization, request JSON, HTTP, SSE parsing, provider error mapping, and credential redaction. `:app` temporarily composes these modules while legacy Compose, coordinator, repository, and Room code are migrated.

## Port Interaction

| Caller | Port or event | Callee | Canonical write allowed | Phase |
| --- | --- | --- | --- | --- |
| User/UI | `AssistantEntryRequest` | Assistant entry policy | No | Active |
| Assistant runtime | `ModelRequest` through `ModelGateway` | model adapter | No | Active |
| Model adapter | `ModelEvent` correlated by `runId` | Assistant run machine | No | Active |
| Assistant parser | reviewed proposal | application command port | Only after acceptance | Next |
| Application command | validated transaction | Room repositories | Yes | Next |
| Room transaction | committed outbox record | replication worker | Already committed data only | Future |
| Replication worker | versioned envelope/checkpoint | device-neutral peer | No direct UI/model mutation | Future |

Model output is always untrusted proposal data. It cannot directly write Nodes, Quizzes, Captures, review state, Room rows, backup data, or synchronization records. Cancellation, supersession, and stale callbacks are isolated by `runId`; proposal application will additionally compare the target base revision/hash.

## Source Tree

```text
android-app/
|-- app/                         compatibility composition, UI, Room, legacy assistant
|-- core/kernel/                 stable IDs, revisions, domain results
|-- domain/assistant/            ModelGateway and pure AssistantRunMachine
|-- feature/assistant/api/       typed feature entry contract
|-- feature/assistant/impl/      entry policy implementation
|-- adapter/model-openai/        OpenAI-compatible HTTP/SSE adapter
|-- build-logic/                 shared pure-Kotlin module convention
|-- starter-content/             repository-local bundled learning content
`-- docs/                        Android-owned architecture and recovery contracts
```

Future child projects add application command ports, separated Room adapters, and replication modules beside these roots rather than nesting them under desktop code.

## Enforcement

From the repository root run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-android-architecture.ps1
```

The verifier requires the module graph and dependency edges, rejects parent `content-demo` build inputs, and reports every forbidden domain import. Legacy `LearningViewModel` and `LearningRepository` size targets remain visible warnings during migration.

## Phase 1 Handoff

Phase 1 activates `:core:kernel`, `:domain:assistant`, `:feature:assistant:api`, `:feature:assistant:impl`, and `:adapter:model-openai`. Existing navigation behavior, Room data, backup schema v1, assistant prompts, streaming, cancellation, and draft review remain behind the `:app` compatibility shell.

Replication is not implemented in Phase 1. The next child project is Phase 2: separate domain/application models from Room entities and repositories while preserving Room as the local source of truth. Outbox/inbox replication begins only after that command and transaction boundary exists.

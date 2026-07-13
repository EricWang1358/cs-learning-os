# Android Layered Modular Architecture Design

## Status

Approved in staged design review on 2026-07-13. This specification supersedes the target architecture in `2026-07-02-android-feature-modularization-design.md`; that earlier package-first refactor remains historical migration context.

## Purpose

Refactor the Android application into an independently buildable, independently open-sourceable local-first product. The design must isolate user interaction, assistant orchestration, domain data, persistence, and network transports so a change can be traced through a bounded dependency subtree instead of the current app-wide logic chain.

The desktop application is an optional synchronization peer. Android must build and remain useful without the desktop source tree, desktop backend, or a reachable network.

## Confirmed Decisions

- Android becomes a self-contained product and future standalone repository.
- The current Room schemas, backup format, user data, and major interaction behavior remain backward compatible during migration.
- Synchronization is device-neutral. A desktop-preferred policy may be configured, but desktop authority is not encoded in domain or wire contracts.
- Gradle modules enforce dependency direction. Kotlin package names alone are insufficient.
- The user layer means UI, user intent, approval, and navigation. Accounts, organizations, and cloud identity are out of scope.
- Model output is never a persistence operation. Canonical writes require an explicit user-triggered application command.
- Implementation is incremental. The target architecture is complete, while each migration slice remains buildable and releasable.

## Industry Baseline

The design uses the following benchmarks as evidence, not as templates to copy blindly:

- Google Android architecture recommendations: repository-backed data layer, local source of truth, UDF, Flow, screen-level state holders, and no direct UI-to-data-source access.
- Now in Android: feature `api`/`impl` separation, core dependency rules, generated module graphs, offline-first repositories, and explicit attention to build time and public APIs.
- Slack Circuit and Spotify Mobius: state/event-only UI boundaries and model-event-effect loops with explicit effect concurrency.
- Android offline-first guidance: lazy and queued writes, WorkManager, pull/push/hybrid synchronization, versioning, and conflict handling.
- CouchDB replication protocol: changes feed, revision ancestry, peer replication logs, checkpoints, recovery, and batch difference calculation.
- Local-first software principles: offline availability, data ownership, portability, and user-visible conflict handling.

The project does not adopt Circuit, Mobius, CouchDB, or a general CRDT library as a required runtime dependency. Their proven boundaries inform project-native Kotlin contracts.

## Architecture Views

The architecture is documented through separate views. They must not be collapsed into a single linear `UI -> assistant -> data -> network` source dependency diagram.

1. The bounded-context map defines ownership of business rules and records.
2. The Gradle graph defines compile-time dependencies and public APIs.
3. The runtime UDF view defines command, query, and asynchronous flows.
4. The replication view defines peer synchronization, conflicts, and transport.
5. The assistant runtime view defines sessions, runs, interactions, proposals, tools, and provider adapters.

## Bounded Contexts

### Content

Owns:

- `Area`
- `Node`
- `QuizDefinition`
- content visibility and lifecycle
- optimistic entity revision checks
- Markdown quiz derivation rules
- search projection definitions

FTS rows are derived projections, not canonical content.

### Learning

Owns:

- `ReviewSchedule`
- append-only `ReviewAttempt`
- append-only `ReadingEvent` and the derived reading trace
- `ReaderQuestion`
- scheduling policy and policy version

Review schedules and latest-reading summaries are local projections rebuilt from events where practical. They are not primary replication records.

### Capture

Owns:

- `CaptureSlip`
- capture lifecycle transitions
- archive and restore rules
- the source relationship created when a capture is promoted to content

### Assistant Runtime

Owns:

- `AssistantSession`
- `AssistantRun`
- `AssistantInteraction`
- immutable `AssistantProposal`
- prompt, model, tool, validator, and policy version references
- assistant run state transitions

It does not own Node, Quiz, Capture, Review, or sync writes.

### Replication

Owns:

- `ReplicaIdentity`
- `ChangeEnvelope`
- outbox and inbox items
- replication sessions and logs
- pair/scope checkpoints
- conflicts and resolution records
- tombstone acknowledgements
- scope definitions and peer capability records

`syncStatus` is removed from domain models during migration. Replication state belongs here.

### Configuration And Trust

Owns:

- provider profiles
- endpoint profiles
- pairing records
- secret references
- user network policy
- trust fingerprints and credential metadata

Secrets stay in Android Keystore-backed storage and never enter backups, model prompts, replication records, logs, or telemetry.

### Backup And Recovery

Owns:

- backup snapshot manifests
- portable package validation
- restore previews and reports
- full replacement and explicit merge workflows

Backup restore and incremental replication are separate state machines. A file package may implement the replication transport schema, but disaster recovery semantics cannot silently become synchronization semantics.

## Cross-Context Contracts

| Producer | Contract | Consumer | Consistency |
| --- | --- | --- | --- |
| Content | `QuizCatalogChanged` | Learning | Same local unit of work where required; otherwise durable local event |
| Content/Learning/Capture | `ChangeEnvelope` | Replication | Same transaction as canonical local mutation |
| Assistant | `ProposalReady` | Authoring UI/application | Immutable handoff; no write |
| Capture application workflow | `PromoteCaptureCommand` | Content + Capture | One local unit of work |
| Replication | validated `MergePlan` | Domain import commands | Atomic local apply plus checkpoint |
| Domain query APIs | observable read models | Feature state holders | Kotlin Flow |

Cross-context calls use application commands or integration events. Repositories do not write another context's tables directly.

## Port Interaction Matrix

| Port or public contract | Owner | Called by | Implemented by | Input and output | Failure boundary |
| --- | --- | --- | --- | --- | --- |
| `FeatureRoute` / `FeatureResult` | Feature `api` | App navigator and other feature `impl` modules | Feature `impl` destination registration | Typed route input and typed result | Invalid route input is rejected before destination state is created |
| `UiActionSink` / `UiState` | Feature `impl` | Compose UI | Screen Presenter/ViewModel | User action down; immutable observable state up | UI never receives implementation exceptions or data-source objects |
| `ContentCommandPort` | Content domain | Authoring application workflow | Content data repository | Versioned create/edit/move/trash/restore command and domain result/events | Validation, missing entity, stale revision, or storage mapping |
| `ContentQueryPort` | Content domain | Library, assistant context, authoring UI | Content data repository | Flow/read model for Areas, Nodes, QuizDefinitions, and search | Local storage/query failure; no network error in query API |
| `LearningCommandPort` | Learning domain | Review and authoring workflows | Learning data repository | Record attempt/event, resolve question, activate quiz definition | Invalid transition, duplicate command, or storage failure |
| `LearningQueryPort` | Learning domain | Review, home, assistant context | Learning data repository | Due queue, attempts, traces, questions as observable read models | Local storage/query failure |
| `CaptureCommandPort` / `CaptureQueryPort` | Capture domain | Capture and promotion workflows | Capture data repository | Versioned capture lifecycle commands and observable inbox/archive models | Invalid transition, stale revision, or storage failure |
| `AssistantSessionStore` | Assistant domain | Assistant application executor | Assistant data repository | Sessions, stable messages, run links | Storage failure preserves active in-memory input for retry |
| `AssistantRunJournal` | Assistant domain | Assistant effect runner | Assistant data repository | Durable run/interaction/proposal transitions and snapshots | Interrupted or failed writes produce a terminal diagnostic state |
| `AssistantContextQuery` | Assistant application | Assistant request preparation | Adapter over Content/Learning/Capture query ports | Scoped, budgeted, hashed context snapshot | Missing context may degrade; policy denial must stop the run |
| `ModelGateway` | Assistant domain | Assistant effect runner | OpenAI-compatible or future local/desktop model adapter | Provider-neutral request, capabilities, and `ModelEvent` stream | Auth, rate, timeout, cancellation, protocol, or capability failure |
| `ProposalHandoff` | Assistant feature API | Assistant Presenter | App navigator/target feature | Immutable proposal ID and target/base revision | Missing/stale target opens review conflict, never writes |
| `UnitOfWork` | Application/data boundary | Application command handlers | Room transaction adapter | Idempotent command block and collected events | Full rollback; no partial canonical/projection/outbox write |
| `ChangeStore` | Replication domain | Replication engine and command transaction hook | Replication data repository | Outbox, inbox, logs, checkpoints, conflicts, acknowledgements | Storage failure blocks checkpoint advancement |
| `DomainExporter` / `DomainImporter` | Replication domain | Replication engine | Per-context replication adapters | Domain record to wire record; merge plan to domain command | Schema/policy/validation failures become rejected inbox records |
| `MergePlanner` | Replication domain | Replication engine | Pure domain implementation | Causal versions plus base/local/remote metadata to merge plan | Unsupported version/policy produces reviewable protocol/conflict result |
| `ReplicaTransport` | Replication domain | Replication engine | HTTPS, file package, or future relay adapter | Handshake, pull, push, acknowledgement batches | Offline/auth/protocol/remote failures cannot mutate canonical data |
| `ReplicationScheduler` | Replication application | User sync action and app lifecycle | WorkManager/platform adapter | Manual or constrained scheduled request | Retry/backoff/cancel is explicit and locally inspectable |
| `TrustStore` | Config and trust domain | Model and replication adapters | Keystore-backed platform adapter | Secret references, credentials, and fingerprints | Secrets never cross the port; unavailable credentials require reconfiguration |
| `RecoveryPackagePort` | Recovery domain | Backup/restore workflow | JSON v1 and signed package adapters | Export stream or staged import manifest/report | Invalid input is rejected before canonical data changes |

The matrix is a runtime interaction map, not permission to add one generic service interface per row. A port is created only when it protects a stable domain boundary or replaceable implementation.

## Compile-Time Module Model

### Dependency Bands

```text
app / app-demo / benchmark
        |
        +--> feature:<name>:impl --> feature:<other>:api
        |             |
        |             +--> application + domain contracts + core UI
        |
        +--> data:<context> ---------> domain:<context>
        +--> adapter:<type> ---------> domain ports
        +--> Hilt composition bindings

application --> domain contexts --> core:kernel
core modules -X-> domain / feature / app
domain contexts -X-> one another
feature impl -X-> other feature impl
```

### Target Repository Tree

```text
learning-os-android/
  app/
  app-demo/
  benchmark/
  build-logic/
  core/
    kernel/
    database/
    designsystem/
    ui/
    network/
    testing/
  domain/
    content/
    learning/
    capture/
    assistant/
    replication/
    config/
    recovery/
  application/
    src/main/kotlin/.../
      authoring/
      capturepromotion/
      assistant/
      replication/
      recovery/
  data/
    content/
    learning/
    capture/
    assistant/
    replication/
    config/
    recovery/
  adapter/
    model-openai/
    sync-http/
    sync-package/
    platform-android/
  feature/
    home/{api,impl}/
    library/{api,impl}/
    capture/{api,impl}/
    review/{api,impl}/
    assistant/{api,impl}/
    sync/{api,impl}/
    settings/{api,impl}/
    backup/{api,impl}/
  protocol/sync/v1/
  starter-content/
  docs/
    architecture/
    protocol/
    decisions/
  scripts/
  CONTRIBUTING.md
  CODE_OF_CONDUCT.md
  SECURITY.md
```

The legal license is intentionally not selected by this technical specification. Public release is blocked until the repository owner explicitly chooses a license; implementation must not add a license by assumption.

### Module Rules

- Feature `api` contains navigation keys, route inputs, and cross-feature results only.
- Feature `impl` contains Compose, screen state holders, and UI mapping. It may depend on another feature's `api`, never another `impl`.
- Domain modules are pure Kotlin and depend only on `core:kernel`.
- Data and external adapters implement domain contracts. They do not import Compose, UI state, string resources, or navigation types.
- `core:database` owns Room entities, DAOs, migrations, and transaction primitives. Feature modules cannot access it.
- `core:kernel` contains only stable values such as typed IDs, time values, `DomainResult`, entity revisions, and page tokens. It is not a general model bucket.
- Gradle dependencies default to `implementation`. Every `api` exposure requires review.
- Explicit API mode and Kotlin Binary Compatibility Validator protect domain and feature API surfaces.
- CI generates module graphs and rejects cycles, forbidden imports, undeclared public APIs, unused dependencies, and source paths outside the Android repository.
- Every mature module documents owner, purpose, public API, dependency graph, test command, and known consumers.

Modules are created when they establish a real public API, test boundary, or replaceable implementation. The migration must not generate the complete target as empty modules.

## Runtime Data Model

### Command Path

```text
Compose UiAction
  -> screen Presenter/ViewModel
  -> application command(CommandId, expectedRevision)
  -> domain aggregate validation and events
  -> UnitOfWork
       - canonical domain rows
       - required local projections
       - transactional replication outbox
       - processed command record
```

Stable command IDs make retried imports, assistant accepts, sync applies, and repeated taps idempotent.

### Query Path

```text
Room-backed read model
  -> context query API (DB model to domain/read model)
  -> Flow
  -> screen state holder stateIn(...)
  -> immutable UiState
  -> lifecycle-aware Compose rendering
```

Room-backed local data is the canonical source for application reads. An editor may own an unsaved draft, but it cannot present the draft as a saved Node. After a command succeeds, UI observes the resulting query flow instead of manually mutating an app-wide entity copy.

### Asynchronous Work

Assistant runs, replication sessions, imports, and long exports first create durable work records. An effect handler or WorkManager runner executes the operation according to its foreground, network, retry, and concurrency policy, then writes a typed result to local storage. UI observes the durable result.

The system does not claim a transaction across a model provider, peer, filesystem, and Room. External work uses at-least-once delivery, stable idempotency keys, checkpoints, and visible compensation or reports.

## Assistant Runtime

### Aggregates

#### Session

```text
Active -> Archived -> Deleted
```

Owns the stable message log, target reference, and run references. Restoring history restores stable records, not an in-progress HTTP stream.

#### Run

```text
Created
  -> ContextBuilding
  -> PolicyChecked
  -> Streaming
  -> Parsing
  -> Completed

Streaming -> AwaitingInteraction -> Streaming
any active state -> Failed | Cancelled | Superseded | Interrupted
```

Every callback is correlated by `runId` and provider request ID. The default policy is cancel-previous within one session and allow concurrency across sessions.

#### Interaction

```text
Pending -> Answered | Dismissed | Expired
```

Selections, confirmations, and sensitive-read approvals are durable interruptions linked to a run segment.

#### Proposal

```text
Ready -> Reviewing -> Accepted -> Applied
Ready/Reviewing -> Rejected
Ready/Reviewing/Accepted -> Stale
Accepted -> ApplyFailed -> Reviewing
```

A proposal contains target ID, base entity revision/hash, source run ID, structured proposed fields, prompt version, model profile/version, policy version, and validator version. Opening and applying both recheck the base revision.

### Run Pipeline

```text
AssistantCommand
  -> scoped and budgeted ContextSnapshot
  -> provider/tool/privacy PolicyGate
  -> provider-neutral ModelGateway
  -> token/tool/structured ModelEvents
  -> protocol and proposal validators
  -> Answer | Interaction | Proposal
```

Stable run transitions and result snapshots are journaled. Tokens are buffered and throttled for UI; they are not written to Room one token at a time. Process death marks an active stream interrupted. Retry creates an explicit continuation from the original request snapshot rather than pretending to resume the same HTTP stream.

### Tool Policy

| Level | Examples | Policy |
| --- | --- | --- |
| Read | search knowledge, get node, list due reviews | Allowlisted, scoped, size-limited, audited by hash/metadata |
| Propose | propose node/quiz/capture edit | Schema and policy validated; produces immutable proposal only |
| Sensitive read | broad cross-area search, attachments | Requires per-run user interaction and may be narrowed |
| Write | save, delete, sync, restore | Not registered for model use; only user-triggered application commands |

`ModelGateway.capabilities()` negotiates streaming, structured output, tool calling, and context limits. Provider adapters map SSE, HTTP, and provider JSON into provider-neutral `ModelEvent` values.

## Replication Engine

### Session Lifecycle

```text
Idle
  -> Handshake
  -> FindCheckpoint
  -> PullChanges
  -> StageInbox
  -> PlanMerge
  -> AtomicApply
  -> PushOutbox
  -> AwaitAck
  -> Completed

active state -> Offline | AuthFailed | ProtocolFailed | Cancelled
checkpoint mismatch/peer epoch change -> RebaseRequired
```

Cancellation before apply is safe. Apply is one Room transaction covering domain imports, conflict records, inbox state, projections, and checkpoint. A peer reset triggers rebase/full-manifest recovery while preserving unacknowledged local changes.

### Replication Records

| Record | Purpose |
| --- | --- |
| `ReplicaIdentity` | Stable replica ID, epoch, key fingerprint, capabilities |
| `ChangeEnvelope` | Change ID, entity reference, operation, causal versions, schemas, payload hash |
| `ReplicationLog` | Peer/scope/session history and checkpoints |
| `OutboxItem` | Pending, sent, acknowledged, or dead-letter local changes |
| `InboxItem` | Received, validated, planned, applied, or rejected remote changes |
| `ConflictRecord` | Base/local/remote references, policy version, resolution audit |
| `TombstoneAck` | Peer acknowledgements required before deletion metadata can be collected |
| `ScopeDefinition` | Area/pin/due filters, version, and filter hash |

The initial causal representation is a bounded dotted version vector with a compaction frontier. It detects incomparable concurrent edits without relying on device wall-clock last-write-wins. General CRDT text merging is out of scope; concurrent Markdown edits produce a reviewable conflict.

### Merge Policy

- Node, QuizDefinition, and Area changes apply automatically only when causally ordered. Concurrent versions create a conflict with base/local/remote preservation.
- ReviewAttempt and ReadingEvent are append-only unions deduplicated by stable event ID.
- ReviewSchedule and reading summaries are derived locally and are not replicated as canonical records.
- Capture and ReaderQuestion creates union safely; concurrent edits use field/state-specific rules or explicit conflicts.
- Assistant sessions, runs, raw prompts, and provider responses are local-only by default.
- Provider secrets, pair credentials, and network policies are never replicated.
- Leaving a sync scope is not a user deletion. Locally changed or user-owned content is not evicted automatically.
- Tombstones are collected only after all known peers acknowledge them or the user explicitly forgets a peer.

### Replication Ports

| Port | Responsibility |
| --- | --- |
| `ChangeStore` | Outbox, inbox, logs, checkpoints, conflicts, tombstone acknowledgements |
| `DomainExporter` | Domain record to versioned wire record |
| `DomainImporter` | Validated merge plan to idempotent domain command |
| `MergePlanner` | Pure causal comparison and domain policy selection |
| `ReplicaTransport` | Handshake, pull, push, and acknowledgement only |
| `BlobStore` | Content-addressed media, hashes, chunks, and resume |
| `TrustStore` | Pair credential, fingerprint, secret reference |
| `ReplicationScheduler` | Manual triggers and WorkManager constraints/backoff |

### Wire Protocol

- `protocolVersion` and `entitySchemaVersion` are independent.
- Handshake negotiates protocol range, schema capability, replica identity/epoch, batch size, compression, blob support, and scope capability.
- Manifest records and full payloads are separate so scoped clients download only required changes.
- HTTP and signed file packages transport the same versioned batches.
- JSON Schema golden fixtures are language-neutral contract evidence for future desktop, NAS, and relay implementations.
- Media uses content-addressed blobs and is not embedded in long JSON records.

## Error Model

Ports return typed outcomes. Implementation exceptions are mapped at adapter boundaries and retain their cause only for local diagnostics.

| Category | Examples | Required disposition |
| --- | --- | --- |
| Domain | validation, stale revision, missing target, illegal transition | User correction or conflict review; no retry loop |
| Data | transaction failure, migration failure, corruption, no space | Roll back, preserve input, recovery action |
| Assistant | auth, rate limit, timeout, interrupted, invalid tool, invalid proposal, policy denied | Explicit retry/configure/review disposition |
| Replication | offline, peer auth, protocol mismatch, rebase, conflict, dead letter | Continue offline; report and recover without partial apply |
| Recovery | unsupported schema, hash mismatch, oversized package, invalid signature | Stage rejection; canonical data unchanged |

Domain code never contains localized strings. UI maps typed failures to localized state and available actions. Diagnostics record operation ID, stage, error code, timing, and implementation metadata, never note bodies, prompts, API keys, or sync payloads.

## Verification Strategy

### Characterization And Compatibility

- Preserve current assistant target, review, save, conflict, cancellation, and history behaviors before moving code.
- Run Room migrations from every exported schema version 1 through 6 to the new schema.
- Keep backup schema v1 import and its missing-Area compatibility behavior.
- Verify starter content import completeness without a repository-external source path.

### Unit And Contract Tests

- Exhaustive reducer transition tests for assistant session/run/interaction/proposal and replication session states.
- Stale run IDs, cancellation, process interruption, retry, and single-claim command tests.
- Shared contract suites for fake and real repository/adapter implementations.
- Pure merge-policy tests for ordered, concurrent, duplicate, reset, tombstone, and scope cases.
- Scheduler policy version tests and deterministic replay from append-only events.
- Domain/entity/wire mapping tests at each boundary.

### Integration Tests

- Room transaction rollback includes canonical rows, projections, outbox, processed command, inbox, and checkpoint.
- WorkManager tests cover constraints, exponential backoff, cancellation, and process restart.
- Model adapter tests cover streaming, throttling, invalid structured output, timeout, cancellation, and capability fallback.
- HTTP and package transports run the same protocol golden corpus.
- Backup staging proves malformed or unsupported input cannot change canonical data.

### Architecture And API Gates

- Gradle module graph has no cycles.
- Domain modules contain no Android, Room, Compose, HTTP, or resource imports.
- Feature implementation modules do not depend on other feature implementation modules.
- Feature modules do not access Room DAOs or provider adapters.
- Public API dumps change only through reviewed updates.
- No Gradle source, asset, document test, or script resolves a required file outside the Android repository root.
- Module READMEs and dependency graphs are generated/checked in CI.

### Release And Performance Gates

- Unit tests, lint, architecture checks, `assembleDebug`, and demo flavor build pass from a fresh clone.
- Offline launch, capture, reading, review, and backup remain functional without network permission use.
- Macrobenchmark covers startup, library rendering, assistant stream rendering, and large local datasets.
- Baseline profile and R8 release build are validated before public release.
- Security checks cover secret leakage, exported components, network security config, dependency scanning, and package verification.

## Migration Strategy

This document is the umbrella architecture. Phase 0 and Phase 1 form the next implementation project and receive the next detailed plan. Each later phase requires a focused child design/spec and plan before code begins, using this document as its non-negotiable dependency and data-ownership baseline.

### Phase 0: Lock Current Behavior

- Add missing characterization tests around current state machines and cross-entity writes.
- Record the current module/import graph and database/backup compatibility matrix.
- Update `docs/state-machine.md` to distinguish historical desktop state machines from the new Android architecture contracts.

### Phase 1: Make Android Self-Contained And Prove One Vertical Slice

- Keep `android-app/` in the current monorepo while making every build input live below it.
- Move/copy the public starter package to `android-app/starter-content/` and remove `../content-demo` from Gradle.
- Replace documentation tests that require desktop `app/` or backend paths.
- Add convention build logic, version catalog, module graph checks, and the minimum real modules for the assistant vertical slice.
- Introduce `core:kernel`, `domain:assistant`, `feature:assistant:api`, `feature:assistant:impl`, and `adapter:model-openai` only as responsibilities move into them.
- Keep compatibility adapters from the old app shell and repository so behavior remains releasable.

This phase proves the dependency pattern. It does not create all target modules empty and does not physically delete or move the desktop application.

### Phase 2: Separate Domain And Room Models

- Establish `core:database` and context data adapters.
- Add explicit mappers and Room migration coverage.
- Introduce the command UnitOfWork, processed commands, domain events, and transactional outbox.
- Remove `syncStatus` from new domain models while keeping compatibility mapping for old rows and backups.

### Phase 3: Complete Assistant Runtime Migration

- Replace `AssistantCoordinator`, `KnowledgeAssistantSession`, and `AssistantAppBridge` responsibilities with session/run/interaction/proposal state, application execution, model adapter, and feature navigation contracts.
- Preserve existing visible behavior through compatibility entry points until screen tests pass.
- Remove domain-to-UI and domain-to-data imports.

### Phase 4: Migrate Product Contexts And Features

- Move Content, Learning, Capture, Config, and Recovery in bounded slices.
- Replace `LearningRepository`, `LearningViewModel`, and `LearningOsApp` compatibility paths as each consumer moves.
- Convert manual FTS and review synchronization into transaction/projector rules.

### Phase 5: Introduce Replication Without Automatic Writes

- Add wire schemas, replica identity, outbox/inbox, logs, checkpoints, and pure merge planning.
- Add read-only manifest/pull and dry-run reports first.
- Add atomic apply, conflict UI, and append-only event push only after replay/rollback tests pass.
- Add HTTP pairing and file package transports after the engine is transport-independent.

### Phase 6: Extract And Publish

- Verify `android-app/` is a complete repository root with no parent dependencies.
- Preserve history using an explicit repository extraction process.
- Add the owner-selected license and public release governance.
- Publish protocol fixtures, contributor documentation, security policy, release process, and compatibility guarantees.

## Current-To-Target Ownership Map

| Current file or concern | Target ownership |
| --- | --- |
| `ui/LearningOsApp.kt` | app composition/navigation plus feature implementations |
| `ui/LearningViewModel.kt` | feature state holders and application commands |
| `feature/assistant/ui/AssistantCoordinator.kt` | assistant state transitions, application executor, and feature presenter |
| `feature/assistant/domain/KnowledgeAssistantSession.kt` | assistant application context snapshot and run preparation |
| `feature/assistant/data/KnowledgeAssistantService.kt` | `adapter:model-openai` |
| `feature/assistant/ui/AssistantAppBridge.kt` | feature API navigation/results and app navigator bindings |
| `data/LearningEntities.kt` | bounded domain models plus `core:database` entities/mappers |
| `data/LearningDao.kt` | context DAOs inside `core:database` |
| `data/LearningRepository.kt` | context query/command repositories and compatibility facade removal |
| `feature/library/data/LibraryRepository.kt` | Content application, Learning integration handlers, search projector, data adapters |
| `data/BackupCodec.kt` | Recovery schema/codec and package adapter |
| `../content-demo` Gradle input | repository-local `starter-content/` |
| byte-size architecture script | module graph, forbidden dependency, explicit API, and compatibility gates |

## Acceptance Criteria

- A fresh Android checkout builds without desktop directories or network access to private infrastructure.
- Existing user databases and backup v1 files remain readable.
- The app stays offline-capable for reading, capture, review, editing, and recovery.
- Model providers cannot directly persist canonical data.
- Assistant callbacks cannot mutate a superseded run or conversation.
- Canonical local mutation and replication outbox insertion are atomic.
- Remote data cannot bypass validation, merge planning, and domain import commands.
- Concurrent content edits preserve all versions and create a reviewable conflict.
- Append-only learning events replay idempotently.
- Feature API/implementation, domain, data, and adapter dependency rules are enforced in CI.
- Architecture documentation includes the context map, module graph, port interaction tables, state transitions, error dispositions, and protocol compatibility policy.
- The first migration slice moves real assistant responsibility and proves the graph without empty-module scaffolding.

## References

- https://developer.android.com/topic/architecture/recommendations
- https://developer.android.com/topic/modularization
- https://developer.android.com/topic/architecture/data-layer/offline-first
- https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md
- https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md
- https://slackhq.github.io/circuit/
- https://spotify.github.io/mobius/
- https://docs.couchdb.org/en/stable/replication/protocol.html
- https://www.inkandswitch.com/essay/local-first/
- https://github.com/Kotlin/binary-compatibility-validator

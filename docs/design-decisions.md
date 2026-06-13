# Design Decisions

## 2026-05-28: Unified Project

Decision: keep the learning system in one project directory.

```text
cs-learning-os/
  skill/     LLM rules and workflow instructions
  content-demo/  small demo Markdown knowledge nodes
  app/       React knowledge website
  scripts/   indexing, validation, and generation tools
  docs/      design decisions and project notes
```

Reasoning:
- The user wants one integrated learning system, not separate skill, notes, and website folders.
- The website should be the main reading and navigation surface.
- Markdown should remain the maintainable source format for LLM-assisted updates.
- React should be used so the project also becomes frontend practice.

Current open decision:
- Choose the first React implementation style for `app/`.

## 2026-05-28: First React Scope

Decision: build the first React version around five core features.

- Global search
- Area filtering
- Node cards
- Node detail panel
- Archive entry for low-frequency content

Reasoning:
- These features validate the knowledge system without making the first frontend iteration too large.
- The React app should become real practice while still serving the learning workflow.

## 2026-05-28: Three-Layer Data Storage

Decision: use a three-layer storage policy.

- GitHub stores code, demo Markdown nodes, skill rules, scripts, docs, and lightweight metadata.
- Generated files are recreated when possible and committed only when deployment or sharing needs them.
- Private content and large source material stay local by default.

Reasoning:
- GitHub has practical and documented limits around large files and repository size.
- The project should remain easy to clone, review, and publish.
- Large tutorials, PDFs, screenshots, mirrors, and datasets can be preserved locally without bloating Git history.

Trace requirement:
- If a local-only file informs a node, record enough metadata in Git-tracked files to reconstruct what happened.
- See `docs/data-policy.md`.

## 2026-05-28: Backend Architecture

Decision: use `SQLite + FastAPI` for the first backend.

Reasoning:
- SQLite is lightweight, local-first, fast enough for a large personal knowledge base, and easy to back up.
- FastAPI fits Python-based Markdown ingestion, text processing, search, and future AI retrieval workflows.
- React remains the main user interface, while the API handles indexing, search, node details, and later recommendation logic.

Initial direction:
- Keep Markdown as an important source format.
- Ingest Markdown metadata and content into SQLite.
- Serve React through API endpoints rather than reading generated JSON directly.

## 2026-05-28: Source of Truth

Decision: Markdown remains the source of truth; SQLite is the index, query, and acceleration layer.

Status: superseded for long-term runtime architecture by the 2026-06-12 data policy. Markdown remains a first-class portable learning package/projection, while SQLite/domain store becomes the operational authority for product state and write coordination.

Reasoning:
- Markdown is readable, Git-friendly, and easy for LLM-assisted maintenance.
- SQLite provides faster search, filtering, detail lookup, and future recommendation support.
- This avoids making the first app depend on database editing workflows before the knowledge format has stabilized.

Data flow:

```text
content-demo/**/*.md
or an external content root selected by CS_LEARNING_CONTENT
  -> ingest script
  -> var/knowledge.db or a database selected by CS_LEARNING_DB
  -> FastAPI
  -> React
```

## 2026-05-28: Initial SQLite Schema

Decision: use a relational schema with FTS5 reserved for full-text search.

Initial tables:
- `nodes`: knowledge node metadata and body content.
- `tags`: normalized tag names.
- `node_tags`: many-to-many node/tag mapping.
- `links`: relationships such as prerequisites, related nodes, and suggested next.
- `sources`: source URLs, local trace hints, and provenance metadata.
- `node_fts`: SQLite FTS5 table for high-performance full-text search.

Reasoning:
- This supports search, filtering, node details, recommendations, provenance, and archive visibility from the first backend version.
- FTS5 keeps the project local-first while still enabling strong full-text search performance.
- Normalizing tags and links avoids turning the database into one large JSON blob too early.

## 2026-05-28: Content Standard A

Decision: define `Standard A` for bilingual practical exam notes.

Use it for GDB, C, systems, coursework, and exam-style material.

Requirements:
- English and Chinese explanations.
- Concrete command or code examples.
- Plain-language explanation.
- Common mistakes.
- Quick recall.
- Frontmatter links to prerequisites and related nodes.

Workflow rule:
- Before adding future content, ask which content standard to use unless the user explicitly names one.

## 2026-05-28: Implementation Order

Decision: build the backend data loop before the React app.

First backend loop:
- Ingest Markdown nodes from a configured content root.
- Store searchable data in SQLite.
- Serve `GET /api/nodes`.
- Serve `GET /api/search`.
- Serve `GET /api/nodes/{slug}`.

Reasoning:
- React should consume real API data rather than hard-coded mock data.
- The backend defines the data contract for the first frontend.
- This keeps the project aligned with the selected `SQLite + FastAPI` architecture.

## 2026-05-28: Python Dependency Isolation

Decision: use a project-level virtual environment at `.venv/`.

Reasoning:
- Avoid installing FastAPI and Uvicorn globally.
- Keep backend dependencies isolated from other coursework and projects.
- Keep `.venv/` local-only and excluded from Git.

## 2026-05-28: Frontend Stack

Decision: use `Vite + React + TypeScript` for the frontend.

Reasoning:
- Vite keeps local development fast and simple.
- React provides the main website interaction surface for the knowledge system.
- TypeScript makes API data contracts, component props, and UI state easier to maintain as the project grows.

Reference skills:
- `react-best-practices` from Vercel Labs for React performance and implementation rules.
- `web-design-guidelines` from Vercel Labs for UI quality review.
- `playwright-interactive` for persistent browser QA.

## 2026-05-28: Frontend Package Manager

Decision: use `npm` for the first frontend.

Reasoning:
- Node and npm are already available in the current environment.
- Avoid introducing another package manager before the project needs it.
- Vite's React TypeScript template works cleanly with npm.

## 2026-05-28: Node Version

Decision: use `nvm` to upgrade global Node to Node 22 LTS.

Observed result:
- Installed Node `22.22.3`.
- Switched current environment to Node `v22.22.3`.

Reasoning:
- Latest Vite templates require a newer Node version than `v20.17.0`.
- `nvm` keeps version switching possible instead of manually overwriting Node.

## 2026-06-12: Product Positioning

Decision: upgrade the product direction to a `Local-first personal learning OS`.

Reasoning:
- The primary user is one learner operating a personal knowledge, review, search, and planning system.
- Cloud/SaaS is not a current use case.
- Keep architecture habits that are not SaaS-hostile: clear API boundaries, portable data, explicit contracts, and replaceable adapters.
- Do not optimize the core workflow around accounts, tenancy, billing, hosted sync, or team administration.

## 2026-06-12: Architecture Core

Decision: the current goal is to extract a presentable `Local-first Learning OS Architecture Core`, not just clean up a large messy codebase.

Reasoning:
- Refactoring should make the product idea easier to explain, demo, and extend.
- The architecture core should foreground local data ownership, learning workflows, search, review, provenance, and workflow-level state.
- Cleanup work is valuable only when it clarifies the core model, reduces coupling, or makes the local-first learning loop more demonstrable.

## 2026-06-12: Dependency Strategy

Decision: use mature libraries for mature general problems, while keeping core product semantics under project control.

Reasoning:
- Use libraries for solved infrastructure problems such as routing, HTTP serving, SQLite access, parsing, testing, and build tooling.
- Own the domain language around nodes, reviews, learning state, provenance, source traces, and workflow orchestration.
- Avoid building fragile custom tools only to stay lightweight.
- Avoid piling on frameworks when typed local code and a small library are enough.

## 2026-06-12: Frontend State Architecture

Decision: split frontend state by workflow and state owner, not by incidental visual component boundaries.

Initial direction:
- Use React built-in typed reducers, context, and custom hooks first.
- Keep state close to the workflow that owns it: search, node detail, review, planning, ingestion status, and settings.
- Introduce a heavier state library only after repeated cross-workflow coordination makes the need concrete.

Reasoning:
- Visual component splits do not reliably match product state ownership.
- Typed reducers and hooks are enough for the current app scale and keep the state model inspectable while the architecture core stabilizes.

## 2026-06-12: Backend Evolution

Decision: keep the backend as `thin api.py + domain services`.

Initial direction:
- `api.py` should expose routes, validate request/response boundaries, and delegate real work.
- Domain services should own ingestion, search, node lookup, scheduling, and recommendation logic.
- Do not change frameworks or start a large backend rewrite in the current phase.

Reasoning:
- The existing FastAPI + SQLite direction still fits the local-first product.
- Thin routes plus explicit services make the architecture easier to test, explain, and split without a big-bang rewrite.

## 2026-06-12: Learning Algorithm Scope

Decision: an Anki-like scheduler is sufficient for the main learning loop.

Reasoning:
- The core product needs predictable spaced repetition, review state, and lightweight scheduling before advanced optimization.
- FSRS, embeddings, and adaptive ML features are not part of the mainline architecture now.
- Advanced algorithms can be explored later behind clear interfaces if the product loop proves the need.

## 2026-06-12: Search Architecture

Decision: use SQLite FTS/BM25 as the core search capability.

Initial direction:
- Keep full-text search local, inspectable, and dependency-light through SQLite FTS.
- Treat semantic search and embeddings as optional future adapters.
- Do not make embeddings a core dependency for search, ranking, or basic navigation.

Reasoning:
- FTS/BM25 is strong enough for the first searchable personal learning OS.
- Adapter boundaries preserve future semantic search options without forcing model infrastructure into the core.

## 2026-06-12: Refactoring Strategy

Decision: use a strangler split strategy instead of a big-bang rewrite.

Initial direction:
- Extract stable domain services and typed contracts one workflow at a time.
- Keep the app runnable while each slice moves behind clearer boundaries.
- Prefer small, testable migrations that preserve behavior over broad rewrites.

Reasoning:
- The project is evolving from a working local learning system into a clearer architecture core.
- Strangler split keeps learning data, demos, and workflows usable while reducing coupling.

## 2026-06-13: LLMwiki Integration Boundary

Decision: treat LLMwiki as a future optional knowledge projection or adapter, not as the runtime authority.

Recommended answer:
- Yes, consider LLMwiki.
- No, do not let it replace the local-first core, SQLite/domain state, Markdown package format, or Anki-like review scheduler.

Target role:
- Export a clean LLM-readable package from the current knowledge base.
- Import or repair content through the same package and `ContentWriteService` path.
- Provide an optional retrieval/index layer for AI-assisted reading, Q Queue work, or content audits.
- Stay detachable so the app remains usable when LLMwiki is absent.

Non-goals for the current phase:
- Do not introduce a permanent background indexing daemon.
- Do not require embeddings or semantic search for normal navigation.
- Do not move private user data into a hosted LLMwiki service by default.
- Do not bypass Markdown package hashes, manifest checks, or SQLite review state.

Architecture guardrails:
- The runtime authority remains the SQLite/domain store.
- Markdown plus assets remain the portable learning package.
- `ContentWriteService` remains the only write path for content mutations.
- LLMwiki import/export should produce explicit reports: added, updated, skipped, failed, stale, and repaired.
- Large media should stay as asset references, not inline LLM context blobs.

Memory and local PC policy:
- LLMwiki support must be lazy and on-demand.
- Indexes should be incremental, hash-aware, and rebuildable.
- The app should load summaries, manifests, or selected chunks instead of resident full-corpus ASTs.

Future interface sketch:

```text
Learning OS domain store
  -> package/export manifest
  -> LLMwiki adapter
  -> optional retrieval/audit/report output
  -> ContentWriteService for accepted imports or repairs
```

Open decision:
- If LLMwiki means a specific external project or file format, evaluate its schema and license before binding to it.
- If LLMwiki means a general LLM-friendly wiki layer, implement it first as a local export/import adapter.

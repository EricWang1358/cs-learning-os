# Design Decisions

## 2026-05-28: Unified Project

Decision: keep the learning system in one project directory.

```text
cs-learning-os/
  skill/     LLM rules and workflow instructions
  content/   Markdown knowledge nodes and source material
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

- GitHub stores code, Markdown nodes, skill rules, scripts, docs, and lightweight metadata.
- Generated files are recreated when possible and committed only when deployment or sharing needs them.
- Large source material stays local by default.

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

Reasoning:
- Markdown is readable, Git-friendly, and easy for LLM-assisted maintenance.
- SQLite provides faster search, filtering, detail lookup, and future recommendation support.
- This avoids making the first app depend on database editing workflows before the knowledge format has stabilized.

Data flow:

```text
content/**/*.md
  -> ingest script
  -> var/knowledge.db
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
- Ingest Markdown nodes from `content/`.
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

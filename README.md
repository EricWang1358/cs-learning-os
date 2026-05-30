# CS Learning OS

A personal computer science learning system built around searchable knowledge nodes, a quiz bank, and future review workflows.

This project is not just a notes folder. It is a small local knowledge app:

- Markdown is the source of truth.
- SQLite provides indexing and full-text search.
- FastAPI serves the data.
- React provides the reading, navigation, and practice UI.
- Local runtime data stays local so the GitHub repository remains lightweight.
- The app shell and the personal knowledge database are designed to be separable.

## Current Features

- Knowledge nodes for algorithms, projects, abilities, and CS fundamentals.
- Global search backed by SQLite FTS5 with safe fallback search.
- React reading UI with Markdown rendering, code blocks, links, and focus mode.
- Separate quiz-bank layer for exam-style practice questions.
- Linked review: quiz items can point back to the knowledge nodes they test.
- `Current loop` cockpit with a full-width knowledge navigator and program health route.
- Program health reads real local metrics: content size, SQLite size, generated artifacts, queue counts, and AI readiness.
- Content standards for future additions:
  - `Standard A`: bilingual practical exam note.
  - `Standard Q`: quiz-bank item.

## Project Structure

```text
app/                 React + Vite frontend
backend/             FastAPI API, SQLite schema, Markdown ingest
data/                Private runtime data root, ignored by Git
data/content/        Current private Markdown knowledge base
data/knowledge.db    Current private SQLite index
content-demo/        Small demo Markdown content tracked by Git
content/             Legacy ignored local content copy; do not use for new notes
docs/                Design notes, implementation log, standards
scripts/             Utility scripts
skill/               Local skill instructions and references
var/                 Local SQLite database, ignored by Git
generated/           QA screenshots and generated artifacts, ignored by Git
.venv/               Project Python virtual environment, ignored by Git
```

## Local Setup

Use a project-level Python virtual environment.

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r backend\requirements.txt
```

Install frontend dependencies from the `app` folder.

```powershell
cd app
npm install
```

## Rebuild The Database

Markdown files are ingested into SQLite.

The default private runtime pair is:

```text
data\content
data\knowledge.db
```

`content-demo/` is only the tiny Git-tracked demo content for clean installs and smoke tests.

```powershell
cd <path-to-cs-learning-os>
.\.venv\Scripts\python.exe -m backend.ingest --content content-demo --db var\knowledge.db
```

For private user data, use the repo-local ignored data directory:

```powershell
.\.venv\Scripts\python.exe -m backend.ingest --content data\content --db data\knowledge.db
```

Expected output looks like:

```text
Ingested 2 nodes and 1 quizzes into ...\var\knowledge.db
```

## Run Locally

Recommended Windows one-command workflow:

```powershell
cd <path-to-cs-learning-os>
.\scripts\dev.ps1
```

This script:

- stops existing listeners on ports `8000` and `5173`
- rebuilds the SQLite index
- starts FastAPI on `127.0.0.1:8000`
- starts Vite on `127.0.0.1:5173`
- writes logs under `generated/dev/`
- opens the frontend in the browser
- keeps a foreground supervisor running so `Ctrl+C` stops both dev servers

`dev.ps1` will automatically prefer the repo-local private `data/` directory, then `CS_LEARNING_DATA_ROOT`, then a legacy sibling data directory if present:

```text
data\content
data\knowledge.db
```

Directory roles:

- `data/content/` is the active private learning library for normal local use.
- `data/knowledge.db` is the generated SQLite index for that private library.
- `data/nodes/` or `data/quizzes/` at the data-root level are invalid orphan locations. They are not the normal runtime content root and should be migrated or quarantined before ingesting.
- `content-demo/` is a small tracked demo library for clean checkouts and tests.
- `content/` is an ignored legacy copy from earlier migrations. Do not add new notes there; migrate or delete it only after verifying it is not your active `CS_LEARNING_CONTENT`.

To run the same app shell against another user's content and database explicitly:

```powershell
.\scripts\dev.ps1 -ContentDir <path-to-content> -DbPath <path-to-knowledge.db>
```

To start servers in the old background style, for smoke tests or automation:

```powershell
.\scripts\dev.ps1 -Detached -NoBrowser
```

To only stop the local dev servers:

```powershell
.\scripts\stop-dev.ps1
```

Manual startup is also supported.

Start the backend:

```powershell
cd <path-to-cs-learning-os>
.\.venv\Scripts\python.exe -m uvicorn backend.api:app --host 127.0.0.1 --port 8000
```

Start the frontend in another terminal:

```powershell
cd app
npm run dev
```

Open the Vite URL, usually:

```text
http://127.0.0.1:5173
```

If port `8000` is already occupied, find and stop the old Python process before restarting the API.

## Verification

Backend smoke test:

```powershell
cd <path-to-cs-learning-os>
.\.venv\Scripts\python.exe backend\smoke_test.py
```

Frontend checks:

```powershell
cd app
npm run lint
npm run build
npm run test:smoke
```

The smoke test captures QA screenshots under `generated/qa/`, which is intentionally ignored by Git.

## Content Workflow

When adding content, choose a standard first.

Use `Standard A` for concept notes, command explanations, GDB/C topics, and bilingual tutorial-style exam notes.

Use `Standard Q` for fixed practice questions, exam screenshots, and future daily review candidates.

Quality bar:

- New explanations should match the depth of `Shark Tank Passcode: process_code and is_valid_code`.
- Do not skip prerequisite vocabulary, state changes, command effects, operand roles, or arithmetic steps.
- If a quiz exposes missing prerequisite knowledge, update/create the linked Standard A node before treating the quiz as complete.

Placement rule:

- `CS fundamentals` stays broad, but new nodes there must be intro-level prerequisites or foundational bridges.
- Good fits: intro C, GDB, x86-64, binary representation, memory, CSAPP/Bomb Lab basics.
- Bad fits: advanced OS/compiler/security topics, project-specific notes, tool-only workflows, or rare tricks.
- If unsure, choose a more specific area/track or mark the item `archive`; do not dump it into `CS fundamentals`.

Knowledge nodes and quiz items should stay separate:

- Knowledge nodes teach concepts.
- Quiz items test whether the learner can apply concepts.
- Quiz items link back to knowledge nodes through `linked_nodes`.

The browser UI also supports local Markdown body edits:

- Open a node or quiz.
- Click `Edit mode`.
- Edit the Markdown body.
- Click `Save Markdown` and confirm.

The first version preserves frontmatter and only edits the body text. Use files directly when you need to change metadata such as `track`, `order`, `tags`, or `linked_nodes`.

Optional AI-assisted revision is available through the local FastAPI backend.

By default, the app uses the local Codex CLI so it can reuse your Codex configuration:

- Install Codex CLI with `npm install -g @openai/codex`.
- The backend looks for `codex.cmd` / `codex` on `PATH`.
- Override with `CS_LEARNING_CODEX_CLI` if your executable is not on `PATH`.
- The Codex CLI model defaults to `gpt-5.4-mini`; override with `CS_LEARNING_CODEX_MODEL`.
- Third-party Codex providers are supported through a generated project-local Codex config at `generated/codex-home`.
- By default, the backend reads provider settings from `%USERPROFILE%\.codex\config.toml` and copies `%USERPROFILE%\.codex\auth.json`; override with `CS_LEARNING_CODEX_SOURCE_HOME`, `CS_LEARNING_CODEX_BASE_URL`, `CS_LEARNING_CODEX_MODEL_PROVIDER`, `CS_LEARNING_CODEX_AUTH_FILE`, or `CS_LEARNING_CODEX_HOME`.
- Check `/api/ai/preflight` for a lightweight Codex readiness check; use `/api/ai/preflight?run_model=true` when you explicitly want a real JSON-schema model call.
- In focus reading, add or select `Q to be solved`, then click `Draft with AI`.
- The request is persisted as an AI job in `Q Queue`; no page refresh is needed.
- The AI draft waits in `Q Queue` until you click `Review draft`; it does not save automatically.
- Review the Markdown, then click `Save Markdown` to write the local source file.
- Questions stay open while the AI job runs and are marked resolved only after you apply the draft.
- Drafts can be rejected from `Q Queue`; rejected drafts leave linked questions open.
- Failed jobs keep a short readable error summary and can be retried from `Q Queue`.
- AI drafts prefer compact `patch_ops` when possible, then the backend composes the final Markdown for review.
- If the target Markdown changed after a draft was created, apply is blocked and the UI shows a draft conflict instead of overwriting newer content.

Example:

```powershell
$env:CS_LEARNING_AI_PROVIDER="codex-cli"
# Optional when codex is not on PATH:
# $env:CS_LEARNING_CODEX_CLI="<path-to-codex.cmd>"
$env:CS_LEARNING_CODEX_MODEL="gpt-5.4-mini"
.\scripts\dev.ps1
```

To use the OpenAI API directly instead:

- Set `OPENAI_API_KEY` in the terminal that starts `.\scripts\dev.ps1`.
- Set `CS_LEARNING_AI_PROVIDER` to `openai-api`.
- Optionally set `CS_LEARNING_OPENAI_MODEL`; default is `gpt-5.4-mini`.

Example:

```powershell
$env:OPENAI_API_KEY="sk-..."
$env:CS_LEARNING_AI_PROVIDER="openai-api"
$env:CS_LEARNING_OPENAI_MODEL="gpt-5.4-mini"
.\scripts\dev.ps1
```

Reader questions are tracked through the `Q Queue`:

- In focus reading, use `Q to be solved` to save unclear points.
- Use the sidebar `Q Queue` entry to see open questions and persisted AI jobs in one list.
- The queue reads from `reader_questions`, so agents do not need to scan all nodes or tags.
- AI jobs are separate durable records; queued, solving, draft-ready, and failed states are visible.
- Job event logs record stage changes for debugging local Codex CLI failures.
- Draft-ready jobs are still human-controlled: review the generated Markdown, inspect the line diff, then apply, reject, or retry.
- Test-created questions should be resolved by smoke tests and should not remain in the open queue.

## Navigation Cockpit

The sidebar `Current loop` section has two expansion routes:

- `Knowledge navigator` opens `/graph`, a full-width 2.5D navigator backed by graph endpoints and `graph_cache`.
- `System health` opens `/health`, a local observability page backed by `/api/system/metrics`.
- `/api/system/metrics` keeps heavyweight storage checks behind a cached snapshot. Refreshing the page should not rescan `.venv`, `node_modules`, or `generated/` synchronously.
- On API startup, the backend loads the last health snapshot and refreshes heavy storage metrics in a single background thread. The Health page shows the snapshot timestamp in Beijing time.
- GitHub upload size defaults to the local tracked-file estimate to avoid rate limits and network stalls. Set `CS_LEARNING_GITHUB_REMOTE_SIZE=1` only when you explicitly want a remote GitHub API check.

The graph navigator is intentionally closer to an advanced mind map than a decorative star field:

- `/graph` shows `Workbench -> area`.
- `/graph/area/<area>` shows area -> tracks.
- `/graph/track/<area>/<track>` shows track -> nodes.
- `/graph/node/<slug>` shows node -> Markdown headings.
- Node and heading layers page at 12 visible cards.
- Heading cards jump to `focus=1` reading URLs with section hashes.

Future 3D graph work should keep this route and backend contract stable, then lazy-load heavier rendering only after the user opens `/graph`.

## Git And Data Policy

The repository should stay small and portable.

Architecture principle:

- Frontend and backend code are the app shell.
- `content-demo/` is tiny synthetic demo data, not a real knowledge base.
- `data/content/` and `data/knowledge.db` are the default private runtime user data.
- Root-level `data/nodes/` and `data/quizzes/` are not valid app data layout; real content belongs under `data/content/nodes/` and `data/content/quizzes/`.
- `content/` is an ignored legacy local copy, not the normal write target.
- Different users should be able to run the same app against different content directories and databases.
- FastAPI reads `CS_LEARNING_CONTENT` and `CS_LEARNING_DB` when provided.
- Removing private content from the current Git tree does not erase it from old commits. Keep the repository private or rewrite history before treating earlier personal content as fully removed.

Tracked:

- Source code.
- Demo or intentionally shared Markdown content.
- Documentation.
- Package lockfiles.
- Skill/reference files.

Ignored:

- `.venv/`
- `app/node_modules/`
- `app/dist/`
- `var/`
- `generated/`
- `__pycache__/`
- local raw captures and downloads
- private or bulky content libraries

This keeps GitHub clean while allowing large local databases, screenshots, downloaded references, and generated artifacts to live on the machine.

## Roadmap

- Add more Standard Q quiz items from exam screenshots.
- Add hide/show answer interaction.
- Add quiz attempts and correctness history.
- Add daily review queue with weights and due dates.
- Improve related-knowledge recommendations.
- Add richer search filters across nodes and quizzes.
- Upgrade `/graph` into an interactive 3D knowledge map with lazy-loaded rendering.
- Expand `/health` into diagnostics for storage growth, failed jobs, stale indexes, backups, and provider readiness.

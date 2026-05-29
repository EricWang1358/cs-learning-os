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
- Content standards for future additions:
  - `Standard A`: bilingual practical exam note.
  - `Standard Q`: quiz-bank item.

## Project Structure

```text
app/                 React + Vite frontend
backend/             FastAPI API, SQLite schema, Markdown ingest
content-demo/        Small demo Markdown content tracked by Git
content/             Local/private Markdown content, ignored by Git when present
content/nodes/       Local/private knowledge nodes
content/quizzes/     Local/private quiz-bank items
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

```powershell
cd D:\A\1CMU\26Summer\cs-learning-os
.\.venv\Scripts\python.exe -m backend.ingest --content content-demo --db var\knowledge.db
```

For private user data, point ingest at the external content directory:

```powershell
.\.venv\Scripts\python.exe -m backend.ingest --content ..\cs-learning-data\content --db ..\cs-learning-data\knowledge.db
```

Expected output looks like:

```text
Ingested 2 nodes and 1 quizzes into ...\var\knowledge.db
```

## Run Locally

Recommended Windows one-command workflow:

```powershell
cd D:\A\1CMU\26Summer\cs-learning-os
.\scripts\dev.ps1
```

This script:

- stops existing listeners on ports `8000` and `5173`
- rebuilds the SQLite index
- starts FastAPI on `127.0.0.1:8000`
- starts Vite on `127.0.0.1:5173`
- writes logs under `generated/dev/`
- opens the frontend in the browser

On this machine, `dev.ps1` will automatically prefer a sibling private data directory if it exists:

```text
..\cs-learning-data\content
..\cs-learning-data\knowledge.db
```

To run the same app shell against another user's content and database explicitly:

```powershell
.\scripts\dev.ps1 -ContentDir D:\path\to\their-content -DbPath D:\path\to\their-knowledge.db
```

To only stop the local dev servers:

```powershell
.\scripts\stop-dev.ps1
```

Manual startup is also supported.

Start the backend:

```powershell
cd D:\A\1CMU\26Summer\cs-learning-os
.\.venv\Scripts\python.exe -m uvicorn backend.api:app --host 127.0.0.1 --port 8000
```

Start the frontend in another terminal:

```powershell
cd D:\A\1CMU\26Summer\cs-learning-os\app
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
cd D:\A\1CMU\26Summer\cs-learning-os
.\.venv\Scripts\python.exe backend\smoke_test.py
```

Frontend checks:

```powershell
cd D:\A\1CMU\26Summer\cs-learning-os\app
npm run lint
npm run build
npm run test:smoke
```

The smoke test captures QA screenshots under `generated/qa/`, which is intentionally ignored by Git.

## Content Workflow

When adding content, choose a standard first.

Use `Standard A` for concept notes, command explanations, GDB/C topics, and bilingual tutorial-style exam notes.

Use `Standard Q` for fixed practice questions, exam screenshots, and future daily review candidates.

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
- The backend looks for `D:\Program Files\nodejs\node_global\codex.cmd` first.
- Override with `CS_LEARNING_CODEX_CLI` if your path is different.
- The Codex CLI model defaults to `gpt-5.4-mini`; override with `CS_LEARNING_CODEX_MODEL`.
- In focus reading, add or select `Q to be solved`, then click `Resolve with AI`.
- The request is persisted as an AI job in `Q Queue`; no page refresh is needed.
- The AI draft waits in `Q Queue` until you click `Review draft`; it does not save automatically.
- Review the Markdown, then click `Save Markdown` to write the local source file.
- Questions that the AI draft directly answers are marked resolved only after you save.
- Failed jobs keep a short readable error summary and can be retried from `Q Queue`.

Example:

```powershell
$env:CS_LEARNING_AI_PROVIDER="codex-cli"
$env:CS_LEARNING_CODEX_CLI="D:\Program Files\nodejs\node_global\codex.cmd"
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
- Test-created questions should be resolved by smoke tests and should not remain in the open queue.

## Git And Data Policy

The repository should stay small and portable.

Architecture principle:

- Frontend and backend code are the app shell.
- `content-demo/` is tiny synthetic demo data, not a real knowledge base.
- `content/` and SQLite are user data.
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

# CS Learning OS — Setup Guide

When you clone this project onto a new machine, here is what to expect and how
to get it running.

## Quick Start (Windows)

```powershell
# 1. Create Python virtual environment
python3 -m venv .venv
.\.venv\Scripts\pip install -r backend/requirements.txt

# 2. Install frontend dependencies
cd app
npm ci
cd ..

# 3. Launch (dev mode)
.\启动 Learning OS.cmd
```

The launcher:
- Runs `python3 -m backend.ingest` to populate the database from Markdown files.
- Starts the API server (`uvicorn`) on `http://127.0.0.1:8000`.
- Starts the frontend dev server (`vite`) on `http://localhost:5173`.

## Quick Start (macOS / Linux)

There is no native launcher script yet. Run each process manually:

```bash
# 1. venv and dependencies
python3 -m venv .venv
source .venv/bin/activate
pip install -r backend/requirements.txt

# 2. Frontend
cd app
npm ci
cd ..

# 3. Ingest content into the database
.venv/bin/python3 -m backend.ingest --content data/content --db data/knowledge.db

# 4. Start backend API (Terminal 1)
.venv/bin/python3 -m uvicorn backend.api:app --host 127.0.0.1 --port 8000

# 5. Start frontend dev server (Terminal 2)
cd app && npm run dev
```

On macOS you may need to install `python3` and `node` via `brew` first.

## Prerequisites

| Tool | Minimum version | Check with |
|------|----------------|------------|
| Python | 3.11+ | `python3 --version` |
| Node.js | 18+ | `node --version` |
| npm | 9+ | `npm --version` |

## AI Configuration

The app ships with **Codex CLI** as the default AI provider. If you don't have
Codex CLI installed, AI features (assistant, draft generation, review) are
unavailable until you configure a provider.

You have two options:

### Option A: Install Codex CLI

```
npm install -g @anthropic-ai/codex
```

### Option B: Use an OpenAI-compatible API

Open `http://localhost:5173/more` in the app, scroll to the **AI Provider**
section, and click **+ Add OpenAI-compatible provider**. Fill in:

| Field | Example |
|-------|---------|
| Label | DeepSeek |
| API Key | `sk-...` |
| Base URL | `https://api.deepseek.com/v1` |
| Model | `deepseek-v4-flash` or `deepseek-v4-pro` |

Supported services include DeepSeek, Kimi, OpenAI, Groq, Ollama (local), and
anything exposing a `/v1/chat/completions` endpoint. API keys are stored
locally in `data/.ai-providers.json` and never leave your machine.

After saving, click **Activate** on the new provider, then click the **Restart**
button on the Home Dashboard.

## Known Issues on Non-Windows Machines

1. **No launcher script** — macOS/Linux need manual startup (see above). A
   cross-platform launcher is planned.

2. **CRLF line endings** — Git may warn about line-ending changes. Run
   `git config core.autocrlf true` on Windows, `false` on macOS/Linux.

3. **Windows path references** — Some node source links reference absolute
   Windows paths (e.g., `D:\A\1CMU\26Summer\`). These are ignored on other
   platforms but may show as broken links in the UI.

4. **Port conflicts** — If ports 5173/8000/8001 are in use, edit the
   commands to use different ports. The frontend Vite config in `app/vite.config.ts`
   proxies `/api` to `localhost:8000`.

5. **Database not included** — The database file (`data/knowledge.db`) is in
   `.gitignore`. You must run `python3 -m backend.ingest` after clone to
   create it from the Markdown content files.

## Android App

The `android-app/` directory contains an Android companion app. It is optional
— all features work in the browser alone. Skip it unless you want to browse
your knowledge map on a phone.

## Syncing

The Sync feature lets you pair two devices (laptop + Android) on the same
local network. See the **Sync** page in the app (`/sync`) for setup.

## Troubleshooting

**"Node source file has no frontmatter"** — A `.md` file in `data/content/nodes/`
is missing a trailing newline after the closing `---`. See the
Frontmatter Integrity Rule in the skill documentation.

**"AI features are disabled"** — Set `CS_LEARNING_AI_ENABLED=true` in your
environment, or use the More page to configure a provider and enable AI.

**"No nodes found"** — Run `python3 -m backend.ingest --content data/content --db data/knowledge.db`
then restart the API server.

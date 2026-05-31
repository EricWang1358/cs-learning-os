# Workspace Layout Notes

This project can live inside a broader school workspace, but it should not depend on that workspace name.

Recommended layout:

```text
<workspace>/
  cs-learning-os/
    app/                 # Git-tracked app shell
    backend/
    data/                # Private Markdown, SQLite DB, screenshots, ignored by Git
      content/           # Active local notes and quiz bank
        assets/          # Private tutorial images served by /content-assets
      knowledge.db       # Generated local SQLite index
      nodes/             # Invalid if present: orphaned root-level content
      quizzes/           # Invalid if present: orphaned root-level content
    content-demo/        # Tiny tracked demo content for clean checkout
    content/             # Ignored legacy local copy, migration-only
  coursework/            # Summer course assignments and notes, optional
```

Current app rules:

- `cs-learning-os/` is the reusable app shell.
- `cs-learning-os/data/` is private user data and is ignored by Git.
- `cs-learning-os/data/content/` is the default active content root for your real nodes and quizzes.
- `cs-learning-os/data/content/assets/` is the default private media root for tutorial images.
- `cs-learning-os/data/nodes/` and `cs-learning-os/data/quizzes/` should not exist in normal operation. They mean some run used `data/` itself as `CS_LEARNING_CONTENT` instead of `data/content/`.
- `cs-learning-os/content-demo/` is intentionally small and tracked so another machine can boot the app without your private notes.
- `cs-learning-os/content/` may still exist locally as an ignored legacy copy. Treat it as migration residue unless an environment variable explicitly selects it.
- The data root can still be moved by setting `CS_LEARNING_DATA_ROOT`.
- `CS_LEARNING_CONTENT` and `CS_LEARNING_DB` still override the exact content and database paths.
- `scripts/dev.ps1` prefers repo-local `data/`, then `CS_LEARNING_DATA_ROOT`, then legacy sibling `../cs-learning-data`, then demo data inside the repo.
- Codex CLI discovery uses `PATH` by default. Set `CS_LEARNING_CODEX_CLI` only when the executable is not on `PATH`.

Do not move coursework folders automatically from an agent run. They may contain assignment-relative paths, hidden grading files, or local build artifacts.

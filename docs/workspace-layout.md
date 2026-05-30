# Workspace Layout Notes

This project can live inside a broader school workspace, but it should not depend on that workspace name.

Recommended layout:

```text
<workspace>/
  cs-learning-os/
    app/                 # Git-tracked app shell
    backend/
    data/                # Private Markdown, SQLite DB, screenshots, ignored by Git
  coursework/            # Summer course assignments and notes, optional
```

Current app rules:

- `cs-learning-os/` is the reusable app shell.
- `cs-learning-os/data/` is private user data and is ignored by Git.
- The data root can still be moved by setting `CS_LEARNING_DATA_ROOT`.
- `CS_LEARNING_CONTENT` and `CS_LEARNING_DB` still override the exact content and database paths.
- `scripts/dev.ps1` prefers repo-local `data/`, then `CS_LEARNING_DATA_ROOT`, then legacy sibling `../cs-learning-data`, then demo data inside the repo.
- Codex CLI discovery uses `PATH` by default. Set `CS_LEARNING_CODEX_CLI` only when the executable is not on `PATH`.

Do not move coursework folders automatically from an agent run. They may contain assignment-relative paths, hidden grading files, or local build artifacts.

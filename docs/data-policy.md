# Data Policy

## Storage Layers

### GitHub layer

Commit code, skill rules, design docs, lightweight metadata, demo content, and small files needed to understand or rebuild the project.

Examples:
- `app/`
- demo `content-demo/`
- `skill/`
- `scripts/`
- `docs/`
- small JSON files used by the app

Personal knowledge content is user data. Do not assume it belongs in the public app repository.

`content-demo/` should stay minimal and synthetic. It is for proving the app boots, not for storing real study material.

### Generated layer

Regenerate these files when possible. Commit only when they are needed for deployment or sharing.

Examples:
- generated search indexes
- generated HTML
- build output
- temporary summaries
- `var/knowledge.db`

### Local-only layer

Keep bulky source material local. Do not commit it by default.

Examples:
- PDFs
- videos
- screenshots
- webpage mirrors
- downloaded datasets
- raw tutorial archives
- private Markdown knowledge bases
- personal quiz banks
- local SQLite databases

## App Shell vs User Data

The frontend and backend should be reusable across different users.

## Content Directory Roles

Use these names consistently:

- `data/content/`: the current private Markdown knowledge base for normal local runs.
- `data/content/assets/`: private tutorial media, served by the backend through `/content-assets/...`.
- `data/knowledge.db`: the generated SQLite index for `data/content/`.
- `data/nodes/` and `data/quizzes/`: invalid root-level orphan locations. Do not write there; inspect, migrate, or quarantine if they appear.
- `content-demo/`: tiny Git-tracked synthetic demo data for clean installs, smoke tests, and fallback behavior.
- `content/`: ignored legacy local copy from earlier iterations. It should not receive new notes unless `CS_LEARNING_CONTENT` explicitly points there for a one-off migration.
- `var/knowledge.db`: ignored demo or fallback SQLite index, usually regenerated from `content-demo/`.

New notes, quizzes, sources, assets, trash entries, and reader-question workflows should target the active content root, not a hard-coded folder name.

Runtime data is selected by:

```text
CS_LEARNING_CONTENT
CS_LEARNING_DB
```

If those environment variables are absent, the app uses:

```text
content-demo/
var/knowledge.db
```

This means one app shell can serve many different knowledge bases.

Private data is kept in the repo-local ignored data directory by default:

```text
data/content
data/knowledge.db
```

The one-command dev script prefers this repo-local `data/` root when it exists. `CS_LEARNING_DATA_ROOT` can move the whole private data root, while `CS_LEARNING_CONTENT` and `CS_LEARNING_DB` override the exact paths.

Important privacy note:

Removing private content from the current Git tree prevents future commits from carrying it, but it does not erase old commits. If the repository ever becomes public or shared broadly, either keep it private, rewrite Git history, or create a fresh clean repository.

## Trace Rule

When a large local file informs a note, leave a trace in Git-tracked metadata:
- original URL or source name
- local path hint, if useful
- access date
- short summary
- which node used it

The project should remain understandable from GitHub even when local files are absent.

## Practical Rule

If a file is large, binary, generated, or easy to recreate, keep it out of Git unless there is a clear reason to version it.

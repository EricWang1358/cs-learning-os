# Data Policy

## Storage Layers

### GitHub layer

Commit code, skill rules, design docs, lightweight metadata, demo content, and small files needed to understand or rebuild the project.

Examples:
- `app/`
- demo `content/` or `content-demo/`
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

On Eric's local machine, private data is kept outside the app repository:

```text
../cs-learning-data/content
../cs-learning-data/knowledge.db
```

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

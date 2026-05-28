# Data Policy

## Storage Layers

### GitHub layer

Commit code, skill rules, Markdown knowledge nodes, design docs, lightweight metadata, and small data files needed to rebuild the project.

Examples:
- `app/`
- `content/nodes/`
- `skill/`
- `scripts/`
- `docs/`
- small JSON files used by the app

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

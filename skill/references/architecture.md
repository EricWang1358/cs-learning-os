# Architecture

The map has two jobs:

1. Preserve a large amount of learning material without becoming a junk drawer.
2. Help the learner rediscover the right idea at the right moment.

## Recommended Layout

```text
map-root/
  index.md
  index.html
  index.json
  nodes/
    algorithms/
    projects/
    abilities/
    cs-fundamentals/
    tools/
    questions/
  sources/
  inbox/
```

## Deterministic Parts

These should be handled by files and scripts:
- folder structure
- file naming
- frontmatter fields
- HTML index generation
- JSON index generation
- exact tag filtering
- exact title or slug lookup

## LLM-Judged Parts

These should be decided by the assistant each time:
- whether an item deserves `core`, `support`, or `archive`
- where a cross-domain idea belongs
- which related nodes are actually useful
- what should be suggested next
- whether a tutorial is worth summarizing
- whether a note is too broad and should be split

## Why Markdown First

Markdown is easy to diff, search, edit, and transform. HTML should be generated from Markdown metadata rather than treated as the source of truth.

## Why Small Nodes

Small nodes make retrieval better. A good node usually answers one durable question, explains one concept, or captures one reusable pattern.

## Growth Rule

Start with simple folders and indexes. Add complexity only after the map has enough content to expose real friction.

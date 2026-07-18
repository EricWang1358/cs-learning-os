# Architecture

The map has two jobs:

1. Preserve a large amount of learning material without becoming a junk drawer.
2. Help the learner rediscover the right idea at the right moment.

## Recommended Layout

This layout is relative to the active content root, not necessarily the repository root. In `cs-learning-os`, the normal private content root is:

```text
data/content/
```

Do not confuse this with the data root itself. `data/nodes/` and `data/quizzes/` are invalid orphan locations.

```text
<active-content-root>/
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

Git-tracked `content-demo/` may use the same internal layout, but only for tiny synthetic demo data.

## Deterministic Parts

These should be handled by files and scripts:
- folder structure
- file naming
- frontmatter fields
- HTML index generation
- JSON index generation
- exact tag filtering
- exact title or slug lookup

## Two Graph Layers

The project deliberately has two related graphs:

1. **Content navigation graph**: ingest reads Markdown `prerequisites` and
   `related` into the ordinary `links` table. It powers backlinks and note
   navigation. It must use real slugs and must not contain self-links or
   prerequisite cycles.
2. **KnowledgeGraph DAG**: `kg_question` identifies a problem/root,
   `kg_edge(parent_node_id, child_node_id)` records that the parent depends on
   the child, and `kg_mastery` records learner state. This graph is explicit;
   `related` metadata does not create a KG edge automatically.

KnowledgeGraph nodes must be real Markdown-backed nodes because ingest can
rebuild the ordinary node table. Shared prerequisites are valid DAG diamonds;
cycles and orphaned endpoints are not.

## LLM-Judged Parts

These should be decided by the assistant each time:
- whether an item deserves `core`, `support`, or `archive`
- where a cross-domain idea belongs
- which related nodes are actually useful
- what should be suggested next
- whether a tutorial is worth summarizing
- whether a note is too broad and should be split

The LLM may propose links or a prerequisite tree, but it does not decide that
the graph is valid. Slug resolution, endpoint existence, cycle checks, scope,
and proposal confirmation remain deterministic application responsibilities.

## Why Markdown First

Markdown is easy to diff, search, edit, and transform. HTML should be generated from Markdown metadata rather than treated as the source of truth.

## Why Small Nodes

Small nodes make retrieval better. A good node usually answers one durable question, explains one concept, or captures one reusable pattern.

## Growth Rule

Start with simple folders and indexes. Add complexity only after the map has enough content to expose real friction.

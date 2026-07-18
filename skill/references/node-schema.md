# Node Schema

Every node should start with YAML frontmatter.

```yaml
---
title: "Dijkstra's Algorithm"
slug: dijkstra
area: algorithms
track: graph-algorithms
level: intermediate
status: seed
visibility: core
tags: [graph, shortest-path, greedy]
prerequisites: [graph-representation, priority-queue]
related: [bellman-ford, bfs, a-star]
sources:
  - https://cp-algorithms.com/graph/dijkstra.html
summary: "Find shortest paths from one source in graphs with nonnegative edge weights."
---
```

## Required Fields

- `title`: Human-readable title.
- `slug`: Stable lowercase identifier. Recommended for every node; it is the
  identity used by frontmatter links, API responses, and graph edges.
- `area`: One top-level area.
- `track`: Narrow learning path within an area.
- `level`: Optional learning level such as `intro`, `intermediate`, or `advanced`.
- `status`: Use `seed`, `growing`, `solid`, or `review`.
- `visibility`: Use `core`, `support`, or `archive`.
- `tags`: Searchable concept tags.
- `summary`: One-sentence retrieval summary.

## Optional Fields

- `prerequisites`: Existing node slugs that this node depends on. Direction is
  current node -> prerequisite target; keep the list small and acyclic.
- `related`: Existing lateral node slugs. This is not a parent/child edge.
- `sources`: authoritative HTTPS URLs or repository-relative source files. Do
  not commit developer-local absolute paths or references to ignored private
  coursework files.
- `review_after`: Date or rough trigger for future review.

Do not add `mastery`, `score`, `attempts`, or `fail_streak` to frontmatter.
Those values belong to the KnowledgeGraph mastery store and change after quiz
verification events.

## Body Template

Use this order by default:

```markdown
# Title

## Why It Matters

## Core Idea

## When To Use It

## Details

## Practice / Application

## Common Confusions

## Suggested Next
```

## Slug Rule

Use lowercase words separated by hyphens. Prefer stable concept names:
- `dijkstra`
- `union-find`
- `cache-locality`
- `project-auth-flow`

## Reader Links

Use a visible Markdown hyperlink whenever a reader should continue to another
concept:

```markdown
[Process lifecycles](fork-process-creation-and-waitpid.md)
```

Relative `.md` links must resolve to a real node in the active content root.
Use absolute HTTPS links only for authoritative external references; the app
opens those links separately from internal node navigation.

## Path Rule

For real local tutorials in this project, write nodes under the active private content root:

```text
data/content/nodes/<area>/<slug>.md
```

If `CS_LEARNING_CONTENT` is set, replace `data/content` with that configured content root. Never write real nodes to root-level `data/nodes/`, and never use `content-demo/` except for demo fixtures or smoke tests.

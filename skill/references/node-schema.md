# Node Schema

Every node should start with YAML frontmatter.

```yaml
---
title: "Dijkstra's Algorithm"
area: algorithms
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
- `area`: One top-level area.
- `status`: Use `seed`, `growing`, `solid`, or `review`.
- `visibility`: Use `core`, `support`, or `archive`.
- `tags`: Searchable concept tags.
- `summary`: One-sentence retrieval summary.

## Optional Fields

- `prerequisites`: Slugs that should be learned before this.
- `related`: Slugs that should be suggested nearby.
- `sources`: URLs or local source files.
- `review_after`: Date or rough trigger for future review.

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

## Path Rule

For real local tutorials in this project, write nodes under the active private content root:

```text
data/content/nodes/<area>/<slug>.md
```

If `CS_LEARNING_CONTENT` is set, replace `data/content` with that configured content root. Never write real nodes to root-level `data/nodes/`, and never use `content-demo/` except for demo fixtures or smoke tests.
